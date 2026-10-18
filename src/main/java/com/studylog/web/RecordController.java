package com.studylog.web;

import com.studylog.model.Comment;
import com.studylog.model.CommentLike;
import com.studylog.model.Notification;
import com.studylog.model.Record;
import com.studylog.model.RecordLike;
import com.studylog.model.User;
import com.studylog.repo.CommentLikeRepository;
import com.studylog.repo.CommentRepository;
import com.studylog.repo.NotificationRepository;
import com.studylog.repo.RecordLikeRepository;
import com.studylog.repo.RecordRepository;
import com.studylog.repo.UserRepository;
import com.studylog.sec.HtmlSanitize;
import com.studylog.sec.Tokens;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api")
public class RecordController {

    // 图文/视频混排 base64 内嵌：40MB 视频 base64 约 53MB，加若干图片留余量
    private static final int MAX_CONTENT = 70_000_000;

    private final RecordRepository records;
    private final CommentRepository comments;
    private final UserRepository users;
    private final RecordLikeRepository likes;
    private final CommentLikeRepository commentLikes;
    private final NotificationRepository notifications;
    private final Tokens tokens;
    private final EntityManager em;

    public RecordController(RecordRepository records, CommentRepository comments,
                            UserRepository users, RecordLikeRepository likes,
                            CommentLikeRepository commentLikes,
                            NotificationRepository notifications,
                            Tokens tokens, EntityManager em) {
        this.records = records;
        this.comments = comments;
        this.users = users;
        this.likes = likes;
        this.commentLikes = commentLikes;
        this.notifications = notifications;
        this.tokens = tokens;
        this.em = em;
    }

    // ---------- 列表 + 筛选 ----------
    @GetMapping("/records")
    Map<String, Object> list(HttpServletRequest req,
                             @RequestParam(name = "q", defaultValue = "") String q,
                             @RequestParam(name = "username", defaultValue = "") String username,
                             @RequestParam(name = "from", defaultValue = "") String dateFrom,
                             @RequestParam(name = "to", defaultValue = "") String dateTo) {
        q = q.trim();
        username = username.trim();
        dateFrom = dateFrom.trim();
        dateTo = dateTo.trim();

        StringBuilder sql = new StringBuilder(
                "SELECT r.id, r.username, r.content, r.created_at, r.updated_at, COALESCE(u.color, '4a90d9') " +
                "FROM records r LEFT JOIN users u ON u.username = r.username WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        // q：角色名或记录内容模糊搜索（不区分大小写，参数化绑定，通配符转义）
        String kw = null;
        if (!q.isEmpty()) {
            kw = "%" + escapeLike(q) + "%";
            sql.append(" AND (LOWER(r.username) LIKE LOWER(:q) ESCAPE '\\\\' OR LOWER(r.content) LIKE LOWER(:q) ESCAPE '\\\\')");
            params.put("q", kw);
        }
        // 兼容旧参数 username（仅按角色）
        if (!username.isEmpty()) {
            sql.append(" AND LOWER(r.username) LIKE LOWER(:uname) ESCAPE '\\\\'");
            params.put("uname", "%" + escapeLike(username) + "%");
        }
        if (!dateFrom.isEmpty()) {
            sql.append(" AND DATE(r.created_at) >= :from");
            params.put("from", LocalDate.parse(dateFrom));
        }
        if (!dateTo.isEmpty()) {
            sql.append(" AND DATE(r.created_at) <= :to");
            params.put("to", LocalDate.parse(dateTo));
        }
        sql.append(" ORDER BY r.created_at DESC LIMIT 500");

        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        String me = tokens.currentUser(req);
        List<Long> ids = rows.stream().map(r -> ((Number) r[0]).longValue()).toList();

        // 留言（含每条留言的点赞数与当前用户是否已赞）
        Map<Long, List<Map<String, Object>>> commentsMap = new HashMap<>();
        if (!ids.isEmpty()) {
            List<Comment> cs = comments.findByRecordIdInOrderByCreatedAtAsc(ids);
            List<Long> cids = cs.stream().map(Comment::getId).toList();
            Map<Long, Long> cLikeCounts = new HashMap<>();
            Set<Long> cLikedIds = new HashSet<>();
            if (!cids.isEmpty()) {
                for (Object[] row : commentLikes.countByCommentIds(cids)) {
                    cLikeCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
                }
                if (me != null) cLikedIds.addAll(commentLikes.findLikedCommentIds(cids, me));
            }
            for (Comment c : cs) {
                commentsMap.computeIfAbsent(c.getRecordId(), k -> new ArrayList<>())
                        .add(commentMap(c, me, cLikeCounts, cLikedIds));
            }
        }

        // 点赞数 + 当前用户是否已赞
        Map<Long, Long> likeCounts = new HashMap<>();
        Set<Long> likedIds = new HashSet<>();
        if (!ids.isEmpty()) {
            for (Object[] row : likes.countByRecordIds(ids)) {
                likeCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
            if (me != null) likedIds.addAll(likes.findLikedRecordIds(ids, me));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            long rid = ((Number) r[0]).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rid);
            m.put("username", r[1]);
            m.put("content", r[2]);
            String color = r[5] == null ? AuthController.DEFAULT_COLOR : (String) r[5];
            m.put("color", color);
            m.put("created_at", toOffset(r[3]));
            m.put("updated_at", r[4] == null ? null : toOffset(r[4]));
            m.put("mine", r[1].equals(me));
            m.put("comments", commentsMap.getOrDefault(rid, List.of()));
            m.put("like_count", likeCounts.getOrDefault(rid, 0L));
            m.put("liked", likedIds.contains(rid));
            out.add(m);
        }

        return Map.of("ok", true, "records", out);
    }

    // ---------- 单条详情（详情页） ----------
    @GetMapping("/records/{rid}")
    Map<String, Object> getOne(HttpServletRequest req, @PathVariable Long rid) {
        String me = tokens.currentUser(req);
        Record r = records.findById(rid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "记录不存在或已被删除"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("username", r.getUsername());
        m.put("content", r.getContent());
        m.put("color", colorOf(r.getUsername()));
        m.put("created_at", r.getCreatedAt());
        m.put("updated_at", r.getUpdatedAt());
        m.put("mine", r.getUsername().equals(me));
        List<Comment> cs = comments.findByRecordIdInOrderByCreatedAtAsc(List.of(rid));
        List<Long> cids = cs.stream().map(Comment::getId).toList();
        Map<Long, Long> cLikeCounts = new HashMap<>();
        Set<Long> cLikedIds = new HashSet<>();
        if (!cids.isEmpty()) {
            for (Object[] row : commentLikes.countByCommentIds(cids)) {
                cLikeCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
            if (me != null) cLikedIds.addAll(commentLikes.findLikedCommentIds(cids, me));
        }
        List<Map<String, Object>> cms = new ArrayList<>();
        for (Comment c : cs) {
            cms.add(commentMap(c, me, cLikeCounts, cLikedIds));
        }
        m.put("comments", cms);
        m.put("like_count", likes.countByRecordId(rid));
        m.put("liked", me != null && likes.findByRecordIdAndUsername(rid, me).isPresent());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("record", m);
        return res;
    }

    // ---------- 点赞（切换） ----------
    @PostMapping("/records/{rid}/like")
    Map<String, Object> toggleLike(HttpServletRequest req, @PathVariable Long rid) {
        String me = requireLogin(req);
        Record r = records.findById(rid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "记录不存在"));
        boolean liked;
        var existing = likes.findByRecordIdAndUsername(rid, me);
        if (existing.isPresent()) {
            likes.delete(existing.get());
            liked = false;
            // 取消点赞时撤回未处理的点赞通知，避免反复点赞刷通知
            notifications.deleteByRecipientAndActorAndTypeAndRecordId(r.getUsername(), me, "like_record", rid);
        } else {
            RecordLike l = new RecordLike();
            l.setRecordId(rid);
            l.setUsername(me);
            l.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            likes.save(l);
            liked = true;
            if (!r.getUsername().equals(me)) {
                saveNotification(r.getUsername(), me, "like_record", rid, null, plainText(r.getContent()));
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("liked", liked);
        m.put("like_count", likes.countByRecordId(rid));
        return m;
    }

    // ---------- 留言点赞（切换） ----------
    @PostMapping("/comments/{cid}/like")
    Map<String, Object> toggleCommentLike(HttpServletRequest req, @PathVariable Long cid) {
        String me = requireLogin(req);
        Comment c = comments.findById(cid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "留言不存在"));
        boolean liked;
        var existing = commentLikes.findByCommentIdAndUsername(cid, me);
        if (existing.isPresent()) {
            commentLikes.delete(existing.get());
            liked = false;
            notifications.deleteByRecipientAndActorAndTypeAndCommentId(c.getUsername(), me, "like_comment", cid);
        } else {
            CommentLike l = new CommentLike();
            l.setCommentId(cid);
            l.setUsername(me);
            l.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            commentLikes.save(l);
            liked = true;
            if (!c.getUsername().equals(me)) {
                String preview = c.getContent().length() > 60 ? c.getContent().substring(0, 60) + "…" : c.getContent();
                saveNotification(c.getUsername(), me, "like_comment", c.getRecordId(), cid, preview);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("liked", liked);
        m.put("like_count", commentLikes.countByCommentId(cid));
        return m;
    }

    // ---------- 新增 ----------
    @PostMapping("/records")
    Map<String, Object> add(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String me = requireLogin(req);
        String content = HtmlSanitize.clean(body.getOrDefault("content", ""));
        if (content.isBlank()) throw new BizException(HttpStatus.BAD_REQUEST, "内容不能为空");
        if (content.length() > MAX_CONTENT) throw new BizException(HttpStatus.BAD_REQUEST, "内容过长");
        User u = users.findByUsername(me)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        Record r = new Record();
        r.setUserId(u.getId());
        r.setUsername(me);
        r.setContent(content);
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        records.save(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("id", r.getId());
        m.put("message", "已添加");
        return m;
    }

    // ---------- 删除（作者本人或管理员） ----------
    @org.springframework.transaction.annotation.Transactional
    @DeleteMapping("/records/{rid}")
    Map<String, Object> delete(HttpServletRequest req, @PathVariable Long rid) {
        String me = requireLogin(req);
        Record r = records.findById(rid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "记录不存在"));
        if (!r.getUsername().equals(me) && !isAdmin(me)) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能删除自己的记录");
        }
        List<Long> cids = comments.findByRecordIdInOrderByCreatedAtAsc(List.of(rid))
                .stream().map(Comment::getId).toList();
        if (!cids.isEmpty()) commentLikes.deleteByCommentIdIn(cids);
        comments.deleteByRecordId(rid);
        likes.deleteByRecordId(rid);
        notifications.deleteByRecordId(rid);
        records.delete(r);
        return Map.of("ok", true, "message", "已删除");
    }

    // ---------- 编辑 ----------
    @PutMapping("/records/{rid}")
    Map<String, Object> edit(HttpServletRequest req, @PathVariable Long rid,
                             @RequestBody Map<String, String> body) {
        String me = requireLogin(req);
        Record r = records.findById(rid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "记录不存在"));
        if (!r.getUsername().equals(me)) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能编辑自己的记录");
        }
        String content = HtmlSanitize.clean(body.getOrDefault("content", ""));
        if (content.isBlank()) throw new BizException(HttpStatus.BAD_REQUEST, "内容不能为空");
        if (content.length() > MAX_CONTENT) throw new BizException(HttpStatus.BAD_REQUEST, "内容过长");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        r.setContent(content);
        r.setUpdatedAt(now);
        records.save(r);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("updated_at", now);
        m.put("message", "已更新");
        return m;
    }

    // ---------- 留言 ----------
    @PostMapping("/records/{rid}/comments")
    Map<String, Object> addComment(HttpServletRequest req, @PathVariable Long rid,
                                   @RequestBody Map<String, String> body) {
        String me = requireLogin(req);
        String content = body.getOrDefault("content", "").trim();
        if (content.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, "留言不能为空");
        if (content.length() > 2000) throw new BizException(HttpStatus.BAD_REQUEST, "留言过长");
        Record record = records.findById(rid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "记录不存在"));
        String replyTo = body.getOrDefault("reply_to", "").trim();
        if (!replyTo.isEmpty()) {
            if (replyTo.length() > 32) throw new BizException(HttpStatus.BAD_REQUEST, "被回复用户名过长");
            if (!users.existsByUsername(replyTo)) throw new BizException(HttpStatus.BAD_REQUEST, "被回复用户不存在");
        } else {
            replyTo = null;
        }
        Comment c = new Comment();
        c.setRecordId(rid);
        c.setUsername(me);
        c.setContent(content);
        c.setReplyTo(replyTo);
        c.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        comments.save(c);
        createNotifications(record, c, me);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("id", c.getId());
        m.put("message", "已留言");
        return m;
    }

    @DeleteMapping("/comments/{cid}")
    Map<String, Object> deleteComment(HttpServletRequest req, @PathVariable Long cid) {
        String me = requireLogin(req);
        Comment c = comments.findById(cid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "留言不存在"));
        if (!c.getUsername().equals(me) && !isAdmin(me)) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能删除自己的留言");
        }
        commentLikes.deleteByCommentId(cid);
        notifications.deleteByCommentId(cid);
        comments.delete(c);
        return Map.of("ok", true, "message", "已删除");
    }

    // ---------- 工具 ----------
    private Map<String, Object> commentMap(Comment c, String me, Map<Long, Long> likeCounts, Set<Long> likedIds) {
        Map<String, Object> cm = new LinkedHashMap<>();
        cm.put("id", c.getId());
        cm.put("username", c.getUsername());
        cm.put("content", c.getContent());
        cm.put("reply_to", c.getReplyTo());
        cm.put("color", colorOf(c.getUsername()));
        cm.put("created_at", c.getCreatedAt());
        cm.put("mine", c.getUsername().equals(me));
        cm.put("like_count", likeCounts.getOrDefault(c.getId(), 0L));
        cm.put("liked", likedIds.contains(c.getId()));
        return cm;
    }

    /**
     * 留言产生两类通知：回复了你的评论（reply）、评论了你的记录（comment）。
     * 自己操作自己不通知；同一接收人只发一条（回复记录作者自己的评论时按 reply 通知一次）。
     */
    private void createNotifications(Record record, Comment c, String me) {
        String preview = c.getContent().length() > 60 ? c.getContent().substring(0, 60) + "…" : c.getContent();
        Set<String> notified = new HashSet<>();
        if (c.getReplyTo() != null && !c.getReplyTo().equals(me)) {
            saveNotification(c.getReplyTo(), me, "reply", record.getId(), c.getId(), preview);
            notified.add(c.getReplyTo());
        }
        String owner = record.getUsername();
        if (!owner.equals(me) && !notified.contains(owner)) {
            saveNotification(owner, me, "comment", record.getId(), c.getId(), preview);
        }
    }

    private void saveNotification(String recipient, String actor, String type, Long recordId, Long commentId, String preview) {
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setActor(actor);
        n.setType(type);
        n.setRecordId(recordId);
        n.setCommentId(commentId);
        n.setPreview(preview);
        n.setRead(false);
        n.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        notifications.save(n);
    }

    private String requireLogin(HttpServletRequest req) {
        String u = tokens.currentUser(req);
        if (u == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return u;
    }

    private boolean isAdmin(String username) {
        return users.findByUsername(username).map(User::isAdmin).orElse(false);
    }

    /** 记录 HTML 内容转纯文本摘要（用于点赞通知预览）。 */
    private static String plainText(String html) {
        if (html == null) return "";
        String t = html.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    /** 转义 LIKE 通配符（配合 SQL 中的 ESCAPE '\\'）。 */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String colorOf(String username) {
        return users.findByUsername(username).map(User::getColor).orElse(AuthController.DEFAULT_COLOR);
    }

    private static OffsetDateTime toOffset(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime o) return o;
        if (v instanceof LocalDateTime l) return l.atOffset(ZoneOffset.UTC);
        if (v instanceof java.sql.Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
        if (v instanceof java.util.Date d) return d.toInstant().atOffset(ZoneOffset.UTC);
        throw new IllegalArgumentException("未知时间类型: " + v.getClass());
    }
}
