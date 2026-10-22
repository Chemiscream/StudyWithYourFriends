package com.studylog.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studylog.model.Comment;
import com.studylog.model.Notification;
import com.studylog.model.Record;
import com.studylog.model.User;
import com.studylog.repo.CommentRepository;
import com.studylog.web.VersionController;
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
    private static final int MAX_SELF_RECORDS = 2;
    private static final int MAX_SELF_RECORD_CHARS = 240;
    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(120);
    private static final DateTimeFormatter CN_DATE = DateTimeFormatter.ofPattern("M月d日");
    private static final java.time.ZoneId CN_ZONE = java.time.ZoneId.of("Asia/Shanghai");

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
                                "【当前情境（以此为准）】\n" +
                                "- 发布并 @ 你的人：「" + author + "」。他说的「我」指他自己，「你」指你（AI助手）。\n" +
                                "- 记录正文是「" + author + "」本人的学习成果；正文里提到的其他名字都是第三方，不是作者本人。\n" +
                                "【学习记录（" + author + " 发布的学习成果，不是你的经历）】\n" + truncate(plainOf(r.getContent()), MAX_RECORD_CHARS));
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
        // 「求夸奖」且留言者不是记录作者：只围绕留言者本人组织上下文，避免把记录作者的成果安到他头上
        boolean praiseMe = !asker.equals(r.getUsername())
                && question.matches(".*(夸|表扬|赞美).{0,6}我.*");
        pool.submit(() -> {
            try {
                StringBuilder ctx = new StringBuilder();
                if (praiseMe) {
                    ctx.append("站内用户「").append(asker).append("」在留言中 @ 了你，他在向你求夸奖。\n");
                } else {
                    ctx.append(repliedDirectly
                            ? "站内用户「" + asker + "」刚刚直接回复了你的留言（没有@你），请阅读记录与留言后回复他。\n"
                            : "站内用户「" + asker + "」在学习记录下的留言中 @ 了你，请阅读记录与已有留言后直接回复他。\n");
                }
                ctx.append("【对话对象（最重要）】\n")
                   .append("- 现在和你说话的人叫「").append(asker).append("」，你要回复的人就是他。\n")
                   .append("- 他留言里的「我 / 俺 / 本人」都指 ").append(asker).append(" 本人，不是别人，也不是你（AI助手）。\n")
                   .append("- 这条记录的作者是「").append(r.getUsername()).append("」，记录里的学习成果属于 ")
                   .append(r.getUsername()).append("，不属于 ").append(asker).append("，也不属于你。\n");
                if (!asker.equals(r.getUsername())) {
                    ctx.append("- 不要把 ").append(r.getUsername()).append(" 的成果说成 ").append(asker).append(" 的成果；")
                       .append("要夸 ").append(asker).append("，只能依据他的留言或下面【").append(asker).append(" 自己的学习记录】。\n");
                }
                ctx.append("- 记录或留言里出现的其他名字（如同学、朋友）都是与你们两个无关的第三方，不要写他们做了什么，也不要把记录内容安到他们身上。\n")
                   .append("- 如果他说「夸我 / 表扬我 / 夸夸我」，就是要你夸「").append(asker)
                   .append("」本人：不要夸记录作者，不要夸别人，也不要夸你自己。\n");
                if (!asker.equals(r.getUsername())) {
                    ctx.append("【").append(asker).append(" 自己的学习记录（属于 ").append(asker)
                       .append("，不是你的经历）】\n").append(selfRecordsContext(asker));
                }
                if (praiseMe) {
                    ctx.append("【").append(asker).append(" 刚刚留言】\n").append(oneLine(question)).append("\n");
                    ctx.append("他在求夸奖：请用 1-2 句话直接夸「").append(asker).append("」本人。")
                       .append("只能使用上面属于他自己的学习记录作为素材；如果他没有学习记录，就夸他的学习积极性并请他分享。")
                       .append("不要提这条记录的作者、记录内容或其中的其他人名，也不要夸你自己。");
                } else {
                    ctx.append("【学习记录（").append(r.getUsername()).append(" 发布的学习成果，不是你的经历）】\n")
                       .append(truncate(plainOf(r.getContent()), MAX_RECORD_CHARS)).append("\n");
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
                        if (AI_NAME.equals(o.getUsername())) tag = "（你之前发的，不要照抄，也不要沿用其中的错误说法）";
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
                    ctx.append("请直接回复这条留言。如果用户提到「他」「这句话」「这条记录」等，请结合上文判断具体指代；" +
                            "人物身份一律以【对话对象】为准，不要照抄你之前回复里用过的话。");
                }
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
            r.setContent("<p>" + CN_DATE.format(reportDate) + " 的学习日报已发布，点击首页顶部的「日报」卡片即可查看～</p>");
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

    /**
     * 系统提示：人设 + 平台真实功能知识（含全部版本更新日志）。
     * 注入真实资料避免小模型对「最近更新了什么」之类问题凭空编造（幻觉）。
     */
    private static final String SYSTEM_PROMPT = buildSystemPrompt();

    private static String buildSystemPrompt() {
        return "你是「学习记录」网站（好友间分享学习进展的小站）的官方 AI 助手，用户名是 AI助手。" +
                "语气友善自然、简洁，回复 1-3 句话，用中文，像朋友闲聊而不是客服话术。直接输出留言正文本身，" +
                "不要出现「AI助手：」之类的前缀，不要使用 Markdown 标题或列表。\n" +
                "【身份与能力边界（必须遵守）】\n" +
                "1. 学习记录和留言都是站内用户发布的——记录正文是发布者本人的学习成果，绝不是你的经历。" +
                "你（AI助手）没有自己的学习记录和生活经历，不要把任何人的学习内容说成你的。" +
                "永远不要用第一人称描述用户的学习内容（不要说「我今天做了实验」这类话），你只是来围观和回应的朋友。\n" +
                "2. 你只能通过留言/回复和大家对话，不能替用户执行任何操作（发记录、生成日报、收藏、点赞等）。" +
                "不要主动承诺「我帮你做某事」，可以建议用户自己去操作。\n" +
                "3. 你没有主动发起对话的能力，只有被 @ 或被回复时才会出现。\n" +
                "4. 分清人物身份：当前向你说话的人说的「我」指他自己，「你」指你；记录作者、记录里提到的同学朋友都是不同的人。" +
                "不要因为某个名字在记录或历史留言里出现过，就把说话的人当成那个人；也不要沿用你之前回复里说错的说法。\n" +
                "【平台功能速览】学习记录（富文本，支持图片/视频，图片点击可放大）、留言与回复、点赞、" +
                "收藏、@提及通知（留言和记录里都可以@）、转发引用、AI 学习日报（每天 0 点由系统自动生成前一天的日报，不是你生成的）、" +
                "设置面板（修改个人信息、查看全部历史更新日志）、主题色切换、暗色模式。\n" +
                "【本站版本更新日志（回答「最近更新/新功能/怎么用」类问题时必须以此为准）】\n" +
                VersionController.notesText() +
                "【回答规则】与平台功能/版本相关的问题必须严格依据上面的真实信息回答；" +
                "上面没有的信息如果不确定，就诚实说不清楚，禁止编造不存在的功能或版本。";
    }

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

    /** 留言者本人最近 2 条学习记录的摘要，为「夸我」类请求提供真实、属于他本人的素材。 */
    private String selfRecordsContext(String asker) {
        List<Record> own = records.findTop2ByUsernameOrderByCreatedAtDesc(asker);
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Record r : own) {
            if (n >= MAX_SELF_RECORDS) break;
            String text = truncate(plainOf(r.getContent()), MAX_SELF_RECORD_CHARS);
            if (text.isEmpty()) continue;
            sb.append("- ").append(asker).append(" 在")
              .append(CN_DATE.format(r.getCreatedAt().atZoneSameInstant(CN_ZONE).toLocalDate()))
              .append("发布：").append(text).append("\n");
            n++;
        }
        return sb.length() == 0
                ? "（他最近没有发布学习记录，缺少可夸的具体素材，可以请他分享最近在学什么）\n"
                : sb.toString();
    }

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
