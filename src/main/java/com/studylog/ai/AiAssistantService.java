package com.studylog.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studylog.model.Comment;
import com.studylog.model.Notification;
import com.studylog.model.Record;
import com.studylog.model.User;
import com.studylog.repo.CommentRepository;
import com.studylog.repo.NotificationRepository;
import com.studylog.repo.RecordRepository;
import com.studylog.repo.UserRepository;
import com.studylog.sec.Passwords;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 助手虚拟用户（用户名：AI助手）。
 * 触发方式：留言或记录正文中出现 @AI助手 时，读取记录全文与留言后以 AI助手 身份留言回应；
 * 每日日报生成成功后，以 AI助手 身份发一条播报记录。生成走本机模型（OpenAI 兼容接口），
 * 异步执行不阻塞用户请求；模型离线或超时则静默放弃，不产生留言。
 */
@Service
public class AiAssistantService {

    public static final String AI_NAME = "AI助手";
    /** AI 助手展示主题色（紫色，与普通用户区分）。 */
    public static final String AI_COLOR = "9b59f0";

    private static final int MAX_RECORD_CHARS = 4000;
    private static final int MAX_COMMENTS_SHOWN = 30;
    private static final int MAX_REPLY_CHARS = 2000;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(120);
    private static final DateTimeFormatter CN_DATE = DateTimeFormatter.ofPattern("M月d日");

    private final UserRepository users;
    private final RecordRepository records;
    private final CommentRepository comments;
    private final NotificationRepository notifications;
    private final String baseUrl;
    private final String apiKey;
    private final String cfgModel;
    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** 单线程串行执行：本地模型并发能力有限，助手回复排队处理。 */
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-assistant");
        t.setDaemon(true);
        return t;
    });

    public AiAssistantService(UserRepository users, RecordRepository records,
                              CommentRepository comments, NotificationRepository notifications,
                              @Value("${app.ai.base-url}") String baseUrl,
                              @Value("${app.ai.api-key:}") String apiKey,
                              @Value("${app.ai.model:}") String model) {
        this.users = users;
        this.records = records;
        this.comments = comments;
        this.notifications = notifications;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.cfgModel = model == null ? "" : model.trim();
    }

    /** 启动时确保 AI助手 用户存在；密码 hash 来自随机 UUID，无人可登录。 */
    @PostConstruct
    void ensureUser() {
        if (users.existsByUsername(AI_NAME)) return;
        User u = new User();
        u.setUsername(AI_NAME);
        u.setPasswordHash(Passwords.hash(UUID.randomUUID().toString()));
        u.setColor(AI_COLOR);
        u.setAdmin(false);
        u.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        users.save(u);
        System.out.println("[AI助手] 已创建虚拟用户: " + AI_NAME);
    }

    // ---------- 触发入口（RecordController 调用，均为用户操作后触发） ----------

    /** 记录创建/编辑后调用：正文（剥离 HTML）包含 @AI助手 时触发回应留言，编辑历史不影响触发。 */
    public void replyOnRecord(Record r, String author) {
        if (!plainOf(r.getContent()).contains("@" + AI_NAME)) return;
        long rid = r.getId();
        pool.submit(() -> {
            try {
                String reply = chatOnce(
                        SYSTEM_PROMPT,
                        "站内用户「" + author + "」发布了一条学习记录并在正文中 @ 了你，请阅读后以留言的形式回应（1-3 句话，可以点评、鼓励或提问）。\n" +
                                "【学习记录】\n" + truncate(plainOf(r.getContent()), MAX_RECORD_CHARS));
                if (reply == null) return;
                insertComment(rid, null, reply, author, null);
            } catch (Exception e) {
                System.err.println("[AI助手] 回应记录 " + rid + " 失败: " + e.getMessage());
            }
        });
    }

    /**
     * 留言发布后调用。触发条件（满足其一）：
     * 1) 留言内容包含 @AI助手（reply_to 指向该留言）；
     * 2) 该留言直接回复了 AI助手的留言（reply_to = AI助手，无需 @）。
     */
    public void replyToComment(Record r, Comment c) {
        if (AI_NAME.equals(c.getUsername())) return; // 防自触发兜底
        boolean mentioned = c.getContent().contains("@" + AI_NAME);
        boolean replyToAi = AI_NAME.equals(c.getReplyTo());
        if (!mentioned && !replyToAi) return;
        long rid = r.getId();
        long cid = c.getId();
        String asker = c.getUsername();
        String question = c.getContent();
        boolean repliedDirectly = replyToAi; // 直接回复 AI 留言（非 @）
        pool.submit(() -> {
            try {
                StringBuilder ctx = new StringBuilder();
                ctx.append(repliedDirectly
                        ? "站内用户「" + asker + "」刚刚直接回复了你的留言（没有@你），请阅读记录与留言后回复他。\n"
                        : "站内用户「" + asker + "」在学习记录下的留言中 @ 了你，请阅读记录与已有留言后直接回复他。\n");
                ctx.append("【学习记录】\n").append(truncate(plainOf(r.getContent()), MAX_RECORD_CHARS)).append("\n");
                ctx.append("【该记录下的留言（旧→新）】\n");
                List<Comment> list = comments.findByRecordIdInOrderByCreatedAtAsc(List.of(rid));
                // 标注被回复者的最近一条留言，帮助 AI 理解「他」「这句话」等指代；AI 自己的留言标「你之前发的」
                String replyTarget = c.getReplyTo();
                Long targetCommentId = null;
                if (replyTarget != null && !replyTarget.isEmpty()) {
                    for (Comment o : list) {
                        if (replyTarget.equals(o.getUsername())) targetCommentId = o.getId();
                    }
                }
                int shown = 0;
                for (Comment o : list) {
                    if (o.getId().equals(cid)) continue; // 触发留言单独列出
                    if (shown++ >= MAX_COMMENTS_SHOWN) break;
                    String tag;
                    if (AI_NAME.equals(o.getUsername())) tag = "（你之前发的）";
                    else if (o.getId().equals(targetCommentId)) tag = "（" + asker + " 回复的对象）";
                    else tag = "";
                    ctx.append("- ").append(o.getUsername()).append(tag).append("：").append(oneLine(o.getContent())).append("\n");
                }
                ctx.append("【").append(asker).append(" 刚刚留言");
                ctx.append(repliedDirectly ? "回复了你" : " @ 了你").append("】");
                if (replyTarget != null && !replyTarget.isEmpty() && !repliedDirectly) {
                    ctx.append("（这条留言是在回复「").append(replyTarget).append("」的留言）");
                }
                ctx.append("\n").append(oneLine(question)).append("\n");
                ctx.append("请直接回复这条留言。如果用户提到「他」「这句话」「这条记录」等，请结合上文判断具体指代。");
                String reply = chatOnce(SYSTEM_PROMPT, ctx.toString());
                if (reply == null) return;
                insertComment(rid, asker, reply, asker, cid);
            } catch (Exception e) {
                System.err.println("[AI助手] 回复留言 " + cid + " 失败: " + e.getMessage());
            }
        });
    }

    /** 日报生成成功后调用：以 AI助手 身份发一条播报记录，告知大家该日期的日报已发布。 */
    public void announceReport(LocalDate reportDate) {
        try {
            User ai = users.findByUsername(AI_NAME).orElse(null);
            if (ai == null) return;
            Record r = new Record();
            r.setUserId(ai.getId());
            r.setUsername(AI_NAME);
            r.setContent("<p>📊 " + CN_DATE.format(reportDate) + " 的学习日报已发布，点击首页顶部的「日报」卡片即可查看～</p>");
            r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            records.save(r);
            System.out.println("[AI助手] 已播报 " + reportDate + " 日报");
        } catch (Exception e) {
            System.err.println("[AI助手] 日报播报失败: " + e.getMessage());
        }
    }

    // ---------- 留言落库 + 通知 ----------

    /** 以 AI助手 身份插入一条留言；replyToUser 为被回复者用户名（可空）；并通知指定用户。 */
    private void insertComment(long recordId, String replyToUser, String reply, String notifyUser, Long replyToCommentId) {
        Record r = records.findById(recordId).orElse(null);
        if (r == null) return; // 记录已被删除
        Comment c = new Comment();
        c.setRecordId(recordId);
        c.setUsername(AI_NAME);
        c.setContent(reply);
        c.setReplyTo(replyToUser);
        c.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        comments.save(c);
        // 通知触发者（留言 @ 触发=reply；记录 @ 触发=comment）；自己触发自己不发
        if (notifyUser != null && !notifyUser.equals(AI_NAME)) {
            String preview = oneLine(reply);
            if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
            Notification n = new Notification();
            n.setRecipient(notifyUser);
            n.setActor(AI_NAME);
            n.setType(replyToCommentId != null ? "reply" : "comment");
            n.setRecordId(recordId);
            n.setCommentId(c.getId());
            n.setPreview(preview);
            n.setRead(false);
            n.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            notifications.save(n);
        }
        System.out.println("[AI助手] 已在记录 " + recordId + " 下留言 #" + c.getId());
    }

    // ---------- 模型调用 ----------

    private static final String SYSTEM_PROMPT =
            "你是「学习记录」网站（一个好友间分享学习进展的小站）的 AI 助手，用户名是 AI助手。" +
                    "语气友善自然、简洁，回复 1-3 句话，用中文。直接输出留言正文本身，" +
                    "不要出现「AI助手：」之类的前缀，不要使用 Markdown 标题或列表。";

    /**
     * 非流式调用本机模型，返回纯文本回复；模型离线/无可用模型时返回 null（静默放弃）。
     */
    private String chatOnce(String system, String user) throws Exception {
        String model = resolveModel();
        if (model == null) {
            System.err.println("[AI助手] 模型不可用，跳过本次回应");
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        payload.put("stream", false);
        payload.put("max_tokens", 300);
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(CHAT_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(payload)));
        if (!apiKey.isEmpty()) rb.header("Authorization", "Bearer " + apiKey);
        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            System.err.println("[AI助手] 模型返回 " + resp.statusCode() + ": " + truncate(resp.body(), 200));
            return null;
        }
        String text = om.readTree(resp.body()).path("choices").path(0).path("message").path("content").asText("");
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) return null;
        // 去掉模型可能自行加的前缀/引用标记，保证留言干净
        text = text.replaceAll("^(?:AI\\s*助手|AI助手)\\s*[:：]\\s*", "")
                .replaceAll("^@\\s*" + AI_NAME + "\\s*[:：]?\\s*", "");
        text = oneLine(text);
        if (text.isEmpty()) return null;
        return truncate(text, MAX_REPLY_CHARS);
    }

    /** 配置未指定模型名时，从 /models 取第一个；服务不可达返回 null。 */
    private String resolveModel() {
        if (!cfgModel.isEmpty()) return cfgModel;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(15)).GET();
            if (!apiKey.isEmpty()) b.header("Authorization", "Bearer " + apiKey);
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return null;
            var arr = om.readTree(resp.body()).path("data");
            return arr.isArray() && arr.size() > 0 ? arr.get(0).path("id").asText(null) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 文本工具 ----------

    /** 富文本 HTML 转纯文本：标签替换为空格，解码常见 HTML 实体，压缩空白。 */
    private static String plainOf(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{2,}", "\n").trim();
    }

    /** 压缩为单行（AI 回复与通知预览均单行展示）。 */
    private static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s*\\n+\\s*", " ").trim();
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
