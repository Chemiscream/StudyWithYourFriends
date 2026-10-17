package com.studylog.daily;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studylog.model.DailyReport;
import com.studylog.repo.DailyReportRepository;
import com.studylog.stats.WordExtractor;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 日报服务：统计指定上海日期的全站学习记录，调用本地模型生成趣味摘要并持久化。
 * 日报与用户无关（全站统一一份），只在当天有学习记录时才生成。
 */
@Service
public class DailyReportService {

    private static final Pattern TAG_RE = Pattern.compile("<[^>]+>");
    private static final Pattern B64_RE = Pattern.compile("data:(?:image|video)/[a-zA-Z0-9.+]+;base64,[A-Za-z0-9+/=]+");

    private final DailyReportRepository repo;
    private final EntityManager em;
    private final WordExtractor wordExtractor = new WordExtractor();
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** 生成中的互斥锁：防止补生成任务与手动请求并发打模型，也防止慢生成被重复请求打断。 */
    private final AtomicBoolean generating = new AtomicBoolean(false);

    private final String cfgBaseUrl;
    private final String cfgApiKey;
    private final String cfgModel;
    private final int maxContextChars;

    public DailyReportService(DailyReportRepository repo, EntityManager em,
                              @Value("${app.ai.base-url}") String baseUrl,
                              @Value("${app.ai.api-key:}") String apiKey,
                              @Value("${app.ai.model:}") String model,
                              @Value("${app.ai.max-context-chars:16000}") int maxContextChars) {
        this.repo = repo;
        this.em = em;
        this.cfgBaseUrl = stripSlash(baseUrl);
        this.cfgApiKey = apiKey == null ? "" : apiKey.trim();
        this.cfgModel = model == null ? "" : model.trim();
        this.maxContextChars = Math.max(1000, maxContextChars);
    }

    /**
     * 生成指定上海日期的日报。
     * 同一时间只允许一个生成任务在执行（互斥锁）：模型生成慢时不会被新请求打断重跑。
     * @return true=成功保存；false=当天无记录；null=模型离线或正在生成中（未保存，等待补生成重试）
     */
    @Transactional
    public Boolean generate(LocalDate date) {
        if (!generating.compareAndSet(false, true)) {
            // 已有生成任务在跑，不重复请求
            return null;
        }
        try {
            List<RecordRow> rows = fetchRecordsOfDate(date);
            if (rows.isEmpty()) return false;

            Map<String, Object> stat = buildStat(rows);
            AiResult ai = buildAiSummary(date, rows);
            // 模型离线：不保存，留给补生成任务重试
            if (ai == null) return null;

            DailyReport r = repo.findByReportDate(date).orElseGet(DailyReport::new);
            r.setReportDate(date);
            r.setContent(toJson(stat));
            r.setSummary(ai.summary());
            r.setSubtitle(ai.subtitle());
            r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            repo.save(r);
            return true;
        } finally {
            generating.set(false);
        }
    }

    /**
     * 补生成缺失日报中最早的一天。
     * 每次只补最早的一天，保证按日期顺序排队——模型离线多日后恢复时，
     * 会从最早的缺失日期开始一天一天补，不会让后面的覆盖前面的。
     */
    @SuppressWarnings("unchecked")
    public void backfillOne() {
        // 有学习记录的日期（上海时区，升序）
        List<java.sql.Date> sqlDates = em.createNativeQuery(
                "SELECT DISTINCT DATE(created_at + INTERVAL 8 HOUR) AS d FROM records ORDER BY d ASC")
                .getResultList();
        if (sqlDates.isEmpty()) return;
        List<LocalDate> recordDates = sqlDates.stream().map(java.sql.Date::toLocalDate).toList();

        // 已有日报的日期
        Set<LocalDate> done = repo.findAllReportDates().stream().collect(Collectors.toSet());

        for (LocalDate d : recordDates) {
            if (!done.contains(d)) {
                try {
                    generate(d);
                } catch (Exception e) {
                    System.err.println("[DailyReport] 补生成 " + d + " 失败: " + e.getMessage());
                }
                break; // 一次只补最早的一天，下一轮再补下一个
            }
        }
    }

    // ---------- 统计 ----------

    private Map<String, Object> buildStat(List<RecordRow> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());

        // 各人提交条数排行
        Map<String, Integer> cnt = new LinkedHashMap<>();
        Map<String, String> colorOf = new LinkedHashMap<>();
        for (RecordRow r : rows) {
            cnt.merge(r.username, 1, Integer::sum);
            colorOf.putIfAbsent(r.username, r.color);
        }
        List<Map<String, Object>> ranking = cnt.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", e.getKey());
                    m.put("count", e.getValue());
                    m.put("color", colorOf.get(e.getKey()));
                    return m;
                })
                .toList();
        out.put("ranking", ranking);

        // 当日关键词 top10
        Map<String, Long> counter = new HashMap<>();
        for (RecordRow r : rows) {
            for (String w : wordExtractor.extract(r.content)) {
                counter.merge(w, 1L, Long::sum);
            }
        }
        List<Map<String, Object>> words = counter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("text", e.getKey());
                    m.put("weight", e.getValue());
                    return m;
                })
                .toList();
        out.put("words", words);
        return out;
    }

    /** 取指定上海日期的全站记录（created_at 存 UTC，+8h 转上海日期）。 */
    @SuppressWarnings("unchecked")
    private List<RecordRow> fetchRecordsOfDate(LocalDate date) {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT r.username, r.content, COALESCE(u.color, '4a90d9') " +
                "FROM records r LEFT JOIN users u ON u.username = r.username " +
                "WHERE DATE(r.created_at + INTERVAL 8 HOUR) = :date " +
                "ORDER BY r.created_at ASC")
                .setParameter("date", date)
                .getResultList();
        List<RecordRow> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new RecordRow((String) r[0], (String) r[1], (String) r[2]));
        }
        return out;
    }

    private record RecordRow(String username, String content, String color) {}
    private record AiResult(String subtitle, String summary) {}

    // ---------- AI 趣味摘要 ----------

    /**
     * 分两次请求模型：先生成正文日报，再根据正文提炼一句短副标题。
     * 副标题不混入正文，独立生成更稳定。
     */
    private AiResult buildAiSummary(LocalDate date, List<RecordRow> rows) {
        // 组装上下文（剥离 HTML/base64，截断单条，控制总预算）
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (RecordRow r : rows) {
            String text = stripContent(r.content);
            if (text.isBlank()) continue;
            if (text.length() > 300) text = text.substring(0, 300) + "…";
            String line = "[" + r.username + "] " + text + "\n";
            if (used + line.length() > maxContextChars) break;
            sb.append(line);
            used += line.length();
        }
        String recordsText = sb.length() == 0 ? "（暂无文本内容）" : sb.toString();

        try {
            String model = cfgModel;
            if (model.isEmpty()) {
                List<String> models = fetchModels();
                if (models.isEmpty()) return null;
                model = models.get(0);
            }

            // 第一次：生成正文日报
            String system = "你是「学习记录」网站的日报小编，要把大家一天的学习情况写成一篇活泼有趣的日报。\n" +
                    "风格要求：像朋友在群里聊天，语气轻快带点梗，适当用 emoji，不要太官方太严肃。\n" +
                    "内容要求：基于以下当日记录，总结大家学了什么、谁最拼、有什么亮点。可以适度调侃，但绝对不要编造记录里没有的内容。\n" +
                    "格式要求：150-300 字，可以用 1-2 个小标题或分点，让阅读轻松。\n" +
                    "今天的日期是 " + date + "。";
            String summary = callChat(model, system, "以下是今天大家的学习记录：\n" + recordsText + "\n请写日报。");
            if (summary == null) return null;

            // 第二次：根据正文提炼一句短副标题
            String subtitle = buildSubtitle(model, date, summary);
            if (subtitle == null || subtitle.isBlank()) {
                // 兜底：正文前 20 字
                String one = summary.replaceAll("\\s+", " ").trim();
                subtitle = one.length() > 20 ? one.substring(0, 20) + "…" : one;
            }
            return new AiResult(subtitle, summary);
        } catch (Exception e) {
            return null;
        }
    }

    /** 第二次请求：让模型根据正文日报生成一句不超过 10 字的副标题。 */
    private String buildSubtitle(String model, LocalDate date, String summary) {
        String system = "你是标题提炼助手。请根据下面的日报正文，用一句不超过 10 个字的话高度概括当天的整体情况。" +
                "只输出这一句话，不要加引号、书名号或任何前后缀。今天的日期是 " + date + "。";
        try {
            String res = callChat(model, system, "日报正文：\n" + summary + "\n\n请输出不超过 10 个字的副标题。");
            if (res == null) return null;
            res = res.trim();
            // 去掉模型可能加的引号/书名号/句号
            res = res.replaceAll("^[「\"'《]+", "").replaceAll("[」\"'》。！？]+$", "").trim();
            if (res.length() > 30) res = res.substring(0, 30);
            return res;
        } catch (Exception e) {
            return null;
        }
    }

    private String callChat(String model, String system, String user) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));
        payload.put("stream", false);
        payload.put("temperature", 0.8); // 稍微高一点，让风格更活泼

        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfgBaseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(600))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(payload)));
        if (!cfgApiKey.isEmpty()) b.header("Authorization", "Bearer " + cfgApiKey);

        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) throw new RuntimeException("HTTP " + resp.statusCode());
        JsonNode root = om.readTree(resp.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        return content.isBlank() ? null : content;
    }

    private List<String> fetchModels() throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfgBaseUrl + "/models"))
                .timeout(Duration.ofSeconds(15)).GET();
        if (!cfgApiKey.isEmpty()) b.header("Authorization", "Bearer " + cfgApiKey);
        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) return List.of();
        JsonNode root = om.readTree(resp.body());
        List<String> ids = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            String id = n.path("id").asText("");
            if (!id.isBlank()) ids.add(id);
        }
        return ids;
    }

    // ---------- 工具 ----------

    private static String stripContent(String content) {
        if (content == null) return "";
        String text = B64_RE.matcher(content).replaceAll(" ");
        text = TAG_RE.matcher(text).replaceAll(" ");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String stripSlash(String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private String toJson(Object o) {
        try { return om.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
