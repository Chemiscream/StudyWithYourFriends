package com.studylog.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studylog.model.AiMessage;
import com.studylog.repo.AiMessageRepository;
import com.studylog.sec.Tokens;
import com.studylog.web.BizException;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 看板娘「库莉姆」问答：默认代理到服务端配置的本地模型（LM Studio，OpenAI 兼容接口），
 * 用户可在「设置 → AI 设置」中切换为任意 OpenAI 兼容 API（设置仅存于用户浏览器，提问时随请求带来）。
 * - 上下文：全站学习记录剥离 HTML/base64 后按时间倒序组装，受字符预算限制；
 * - 记忆：按账号存服务端（每账号最多保留最近 MAX_STORED 条），可随时清空；
 * - 排队：信号量限制并发（默认 1），超出进入 FIFO 等待，等待人数有上限。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Pattern TAG_RE = Pattern.compile("<[^>]+>");
    private static final Pattern B64_RE = Pattern.compile("data:(?:image|video)/[a-zA-Z0-9.+]+;base64,[A-Za-z0-9+/=]+");

    private static final int MAX_QUESTION = 2000;    // 单次提问长度上限
    private static final int MAX_STORED = 20;        // 每账号最多保留 20 条（约 10 轮）
    private static final int HISTORY_SENT = 6;       // 发给模型的最近历史条数（约 3 轮）
    private static final int MAX_RECORD_CHARS = 500; // 单条记录截断长度
    private static final int MAX_RECORD_ROWS = 3000; // 读取记录行数安全上限
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final AiMessageRepository aiMessages;
    private final Tokens tokens;
    private final EntityManager em;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private final String cfgBaseUrl;
    private final String cfgApiKey;
    private final String cfgModel;
    private final int timeoutSeconds;
    private final int maxContextChars;
    private final int maxQueue;
    private final int maxConcurrent;
    private final Semaphore slots;
    private final AtomicInteger waiting = new AtomicInteger();
    private final Executor taskExecutor;

    public AiController(AiMessageRepository aiMessages, Tokens tokens, EntityManager em,
                        @Value("${app.ai.base-url}") String baseUrl,
                        @Value("${app.ai.api-key:}") String apiKey,
                        @Value("${app.ai.model:}") String model,
                        @Value("${app.ai.max-concurrent:1}") int maxConcurrent,
                        @Value("${app.ai.max-queue:10}") int maxQueue,
                        @Value("${app.ai.timeout-seconds:180}") int timeoutSeconds,
                        @Value("${app.ai.max-context-chars:16000}") int maxContextChars,
                        Executor taskExecutor) {
        this.aiMessages = aiMessages;
        this.tokens = tokens;
        this.em = em;
        this.taskExecutor = taskExecutor;
        this.cfgBaseUrl = stripSlash(baseUrl);
        this.cfgApiKey = apiKey == null ? "" : apiKey.trim();
        this.cfgModel = model == null ? "" : model.trim();
        this.slots = new Semaphore(Math.max(1, maxConcurrent));
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.maxQueue = Math.max(0, maxQueue);
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
        this.maxContextChars = Math.max(1000, maxContextChars);
    }

    // ---------- 队列状态（前端等待时轮询） ----------
    @GetMapping("/status")
    Map<String, Object> status(HttpServletRequest req) {
        requireLogin(req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("running", maxConcurrent - slots.availablePermits());
        m.put("waiting", waiting.get());
        return m;
    }

    // ---------- 对话历史 ----------
    @GetMapping("/history")
    Map<String, Object> history(HttpServletRequest req) {
        String me = requireLogin(req);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiMessage msg : aiMessages.findByUsernameOrderByCreatedAtAscIdAsc(me)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            m.put("created_at", msg.getCreatedAt());
            out.add(m);
        }
        return Map.of("ok", true, "messages", out);
    }

    @DeleteMapping("/history")
    Map<String, Object> clearHistory(HttpServletRequest req) {
        String me = requireLogin(req);
        aiMessages.deleteByUsername(me);
        return Map.of("ok", true, "message", "已清空对话");
    }

    // ---------- 模型列表（AI 设置面板下拉选择用） ----------
    @PostMapping("/models")
    Map<String, Object> models(HttpServletRequest req, @RequestBody(required = false) Map<String, Object> body) {
        requireLogin(req);
        Provider p = resolveProvider(settingsOf(body));
        return Map.of("ok", true, "models", fetchModels(p));
    }

    // ---------- 问答（SSE 流式） ----------
    /**
     * 流式协议（text/event-stream，每条消息 data 为 JSON）：
     *   {"model":"..."}      元信息（首个事件）
     *   {"delta":"..."}      增量文本（可多条）
     *   {"error":"..."}      错误（终态，本轮不写入记忆）
     *   {"done":true}        正常结束（本轮已写入记忆）
     * 登录校验、模型解析、排队都在流开始前完成——确定性错误仍以 JSON 返回，
     * 前端按 Content-Type 区分两种响应。
     */
    @PostMapping("/chat")
    SseEmitter chat(HttpServletRequest req, @RequestBody Map<String, Object> body) {
        String me = requireLogin(req);
        Object q = body == null ? null : body.get("question");
        String question = q instanceof String s ? s.trim() : "";
        if (question.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, "问题不能为空");
        if (question.length() > MAX_QUESTION) throw new BizException(HttpStatus.BAD_REQUEST, "问题过长（最多 " + MAX_QUESTION + " 字）");

        // 默认服务端本地模型；用户在「AI 设置」中提供了覆盖配置时使用用户自己的（安全规则见 resolveProvider）
        Provider p = resolveProvider(settingsOf(body));
        if (p.model().isEmpty()) {
            List<String> models = fetchModels(p);
            if (models.isEmpty()) {
                throw new BizException(HttpStatus.BAD_GATEWAY, "库莉姆的脑袋还没醒（模型服务未加载模型），请稍后再来看看吧～");
            }
            p = p.withModel(models.get(0));
        }

        // 排队：超出并发上限的请求 FIFO 等待，等待人数满员则直接拒绝。
        // 排队发生在返回 emitter 之前，因此响应头到达时排队已结束，前端可据此停止轮询。
        if (waiting.get() >= maxQueue) {
            throw new BizException(HttpStatus.TOO_MANY_REQUESTS, "找库莉姆聊天的人太多啦，先去逛逛学习记录，稍后再来吧～");
        }
        waiting.incrementAndGet();
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "请求被中断，请重试");
        } finally {
            waiting.decrementAndGet();
        }

        // 已获得槽位；异步任务结束后释放。emitter 超时兜底覆盖整段生成时间。
        SseEmitter emitter = new SseEmitter(Duration.ofSeconds(timeoutSeconds + 30L).toMillis());
        final Provider fp = p;
        try {
            taskExecutor.execute(() -> streamAnswer(emitter, fp, me, question));
        } catch (Exception e) {
            slots.release();
            emitter.complete();
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, "服务繁忙，请稍后再试");
        }
        return emitter;
    }

    private void streamAnswer(SseEmitter emitter, Provider p, String me, String question) {
        StringBuilder full = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean(false);          // 成功写入记忆后错误不再重复上报
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<java.io.InputStream> upstreamRef = new AtomicReference<>();
        // 客户端断开 / emitter 超时时关闭上游流，唤醒阻塞中的 read()
        emitter.onTimeout(() -> { cancelled.set(true); closeQuietly(upstreamRef.get()); });
        emitter.onError(t -> cancelled.set(true));
        try {
            sendEvent(emitter, Map.of("model", p.model()));

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
            // 只把最近几轮历史发给模型，控制上下文体积
            List<AiMessage> history = aiMessages.findByUsernameOrderByCreatedAtAscIdAsc(me);
            for (AiMessage msg : history.subList(Math.max(0, history.size() - HISTORY_SENT), history.size())) {
                messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
            messages.add(Map.of("role", "user", "content", question));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", p.model());
            payload.put("messages", messages);
            payload.put("stream", true);

            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(p.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30)) // 到响应头为止；正文耗时由 emitter 超时兜底
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(payload)));
            if (!p.apiKey().isEmpty()) b.header("Authorization", "Bearer " + p.apiKey());

            HttpResponse<java.io.InputStream> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String bodyText;
                try (java.io.InputStream errIn = resp.body()) {
                    bodyText = new String(errIn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                throw gatewayError("调用模型", resp.statusCode(), bodyText);
            }
            upstreamRef.set(resp.body());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    String l = line.trim();
                    if (!l.startsWith("data:")) continue;
                    String data = l.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    JsonNode delta = om.readTree(data).path("choices").path(0).path("delta").path("content");
                    if (delta.isValueNode() && !delta.asText().isEmpty()) {
                        full.append(delta.asText());
                        sendEvent(emitter, Map.of("delta", delta.asText()));
                    }
                }
            }
            if (cancelled.get()) return; // 客户端已断开/超时，不写入记忆
            if (full.isEmpty()) throw new BizException(HttpStatus.BAD_GATEWAY, "库莉姆一时语塞了，换个问法再试试吧～");
            saveExchange(me, question, full.toString());
            saved.set(true);
            sendEvent(emitter, Map.of("done", true));
            emitter.complete();
        } catch (Exception e) {
            if (!saved.get()) {
                String msg = e instanceof BizException bz ? bz.getMessage()
                        : e instanceof HttpTimeoutException ? "库莉姆想得入迷超时啦，请稍后再试～"
                        : (cancelled.get() ? null : connectErrorMessage());
                if (msg != null) {
                    try {
                        sendEvent(emitter, Map.of("error", msg));
                        emitter.complete();
                    } catch (Exception ignore) { /* 客户端已断开 */ }
                }
            } else {
                try { emitter.complete(); } catch (Exception ignore) { /* 已完成 */ }
            }
        } finally {
            slots.release();
        }
    }

    /** 用 Jackson（UTF-8）发送一条 SSE data 事件，避免 StringHttpMessageConverter 编码歧义。 */
    private void sendEvent(SseEmitter emitter, Map<String, Object> data) throws Exception {
        emitter.send(SseEmitter.event().data(data, MediaType.APPLICATION_JSON));
    }

    private static void closeQuietly(java.io.InputStream in) {
        if (in != null) { try { in.close(); } catch (Exception ignore) {} }
    }

    private List<String> fetchModels(Provider p) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(p.baseUrl() + "/models"))
                .timeout(Duration.ofSeconds(30)).GET();
        if (!p.apiKey().isEmpty()) b.header("Authorization", "Bearer " + p.apiKey());
        JsonNode root;
        try {
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw gatewayError("拉取模型列表", resp.statusCode(), resp.body());
            }
            root = om.readTree(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_GATEWAY, connectErrorMessage());
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            String id = n.path("id").asText("");
            if (!id.isBlank()) ids.add(id);
        }
        return ids;
    }

    // ---------- 内部：上下文与记忆 ----------
    private String buildSystemPrompt() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT username, content, created_at FROM records ORDER BY created_at DESC LIMIT " + MAX_RECORD_ROWS)
                .getResultList();
        StringBuilder sb = new StringBuilder();
        int used = 0, included = 0;
        for (Object[] r : rows) {
            String text = stripContent((String) r[1]);
            if (text.isBlank()) continue;
            if (text.length() > MAX_RECORD_CHARS) text = text.substring(0, MAX_RECORD_CHARS) + "…";
            String line = "[" + dateOf(r[2]) + "] " + r[0] + "：" + text + "\n";
            if (used + line.length() > maxContextChars) break;
            sb.append(line);
            used += line.length();
            included++;
        }
        String note = included < rows.size()
                ? "（记录较多，仅包含最新 " + included + " 条，更早的未包含）"
                : "（共 " + included + " 条）";
        String now = ZonedDateTime.now(DISPLAY_ZONE)
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINA));
        return "你是「库莉姆」，学习记录网站的看板娘，一个活泼可爱的女孩子，身边总跟着一只黑猫。\n" +
                "现在是 " + now + "（北京时间）。用户说「今天」「昨天」时以此为准。\n" +
                "以下是站内用户发布的学习记录" + note + "，按时间倒序，格式为 [日期] 用户名：内容：\n---\n" +
                (sb.length() == 0 ? "（暂无记录）\n" : sb.toString()) +
                "---\n" +
                "回答规则：\n" +
                "1. 以库莉姆的身份说话，语气活泼亲切，像朋友闲聊一样自然，不要用客服腔；\n" +
                "2. 不要自称「AI助手」，也不要说自己是人工智能或语言模型，你就是库莉姆；\n" +
                "3. 这些学习记录是站内用户们发布的内容，不是你的经历；\n" +
                "4. 只依据以上学习记录和对话历史作答，不要编造记录中不存在的内容；找不到相关信息时坦率说不知道；\n" +
                "5. 你不能替用户执行任何操作（发记录、生成日报等都做不到），不要承诺自己做不到的事；\n" +
                "6. 用简洁的中文回答，涉及统计时说明依据。";
    }

    private void saveExchange(String me, String question, String answer) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AiMessage u = new AiMessage();
        u.setUsername(me);
        u.setRole("user");
        u.setContent(question);
        u.setCreatedAt(now);
        AiMessage a = new AiMessage();
        a.setUsername(me);
        a.setRole("assistant");
        a.setContent(answer.length() > 60000 ? answer.substring(0, 60000) : answer);
        a.setCreatedAt(now);
        aiMessages.saveAll(List.of(u, a));
        // 每账号只保留最近 MAX_STORED 条，防止无限增长
        List<AiMessage> all = aiMessages.findByUsernameOrderByCreatedAtAscIdAsc(me);
        if (all.size() > MAX_STORED) {
            aiMessages.deleteAll(all.subList(0, all.size() - MAX_STORED));
        }
    }

    // ---------- 工具 ----------
    private record Provider(String baseUrl, String apiKey, String model) {
        Provider withModel(String m) { return new Provider(baseUrl, apiKey, m); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> settingsOf(Map<String, Object> body) {
        Object s = body == null ? null : body.get("settings");
        return s instanceof Map ? (Map<String, Object>) s : Map.of();
    }

    /**
     * 解析模型提供方。安全要点：用户提供 base_url 时，只使用用户自己的 api_key，
     * 绝不与服务端配置的默认 key 混用，防止默认 key 被转发到用户指定的地址。
     */
    private Provider resolveProvider(Map<String, Object> s) {
        String baseUrl = strOf(s, "base_url", 300);
        String apiKey = strOf(s, "api_key", 300);
        String model = strOf(s, "model", 200);
        if (baseUrl.isEmpty()) {
            baseUrl = cfgBaseUrl;
            if (apiKey.isEmpty()) apiKey = cfgApiKey;
        } else if (!(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new BizException(HttpStatus.BAD_REQUEST, "Base URL 必须以 http:// 或 https:// 开头");
        }
        if (model.isEmpty()) model = cfgModel;
        return new Provider(stripSlash(baseUrl), apiKey, model);
    }

    private static String strOf(Map<String, Object> m, String key, int maxLen) {
        Object v = m.get(key);
        String s = v instanceof String str ? str.trim() : "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private static String stripSlash(String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String stripContent(String content) {
        if (content == null) return "";
        String text = B64_RE.matcher(content).replaceAll(" ");
        text = TAG_RE.matcher(text).replaceAll(" ");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String dateOf(Object v) {
        OffsetDateTime odt;
        if (v instanceof OffsetDateTime o) odt = o;
        else if (v instanceof LocalDateTime l) odt = l.atOffset(ZoneOffset.UTC);
        else if (v instanceof java.sql.Timestamp t) odt = t.toInstant().atOffset(ZoneOffset.UTC);
        else if (v instanceof java.util.Date d) odt = d.toInstant().atOffset(ZoneOffset.UTC);
        else return String.valueOf(v);
        return odt.atZoneSameInstant(DISPLAY_ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private BizException gatewayError(String action, int statusCode, String body) {
        String s = body == null ? "" : body.replaceAll("\\s+", " ");
        if (s.length() > 200) s = s.substring(0, 200) + "…";
        return new BizException(HttpStatus.BAD_GATEWAY,
                action + "失败（HTTP " + statusCode + "）" + (s.isBlank() ? "" : "：" + s));
    }

    private String connectErrorMessage() {
        return "库莉姆暂时联络不上她的小脑袋啦，请稍后再来试试吧～";
    }

    private String requireLogin(HttpServletRequest req) {
        String u = tokens.currentUser(req);
        if (u == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return u;
    }
}
