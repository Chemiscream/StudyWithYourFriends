package com.studylog.web;

import com.studylog.model.Comment;
import com.studylog.model.Record;
import com.studylog.model.User;
import com.studylog.repo.CommentRepository;
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

    private static final int MAX_CONTENT = 15_000_000;

    private final RecordRepository records;
    private final CommentRepository comments;
    private final UserRepository users;
    private final Tokens tokens;
    private final EntityManager em;

    public RecordController(RecordRepository records, CommentRepository comments,
                            UserRepository users, Tokens tokens, EntityManager em) {
        this.records = records;
        this.comments = comments;
        this.users = users;
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

        // 留言
        Map<Long, List<Map<String, Object>>> commentsMap = new HashMap<>();
        if (!ids.isEmpty()) {
            List<Comment> cs = comments.findByRecordIdInOrderByCreatedAtAsc(ids);
            for (Comment c : cs) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", c.getId());
                cm.put("username", c.getUsername());
                cm.put("content", c.getContent());
                cm.put("reply_to", c.getReplyTo());
                cm.put("color", colorOf(c.getUsername()));
                cm.put("created_at", c.getCreatedAt());
                cm.put("mine", c.getUsername().equals(me));
                commentsMap.computeIfAbsent(c.getRecordId(), k -> new ArrayList<>()).add(cm);
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("username", r[1]);
            m.put("content", r[2]);
            String color = r[5] == null ? AuthController.DEFAULT_COLOR : (String) r[5];
            m.put("color", color);
            m.put("created_at", toOffset(r[3]));
            m.put("updated_at", r[4] == null ? null : toOffset(r[4]));
            m.put("mine", r[1].equals(me));
            m.put("comments", commentsMap.getOrDefault(((Number) r[0]).longValue(), List.of()));
            out.add(m);
        }

        return Map.of("ok", true, "records", out);
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
    @DeleteMapping("/records/{rid}")
    Map<String, Object> delete(HttpServletRequest req, @PathVariable Long rid) {
        String me = requireLogin(req);
        Record r = records.findById(rid)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "记录不存在"));
        if (!r.getUsername().equals(me) && !isAdmin(me)) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能删除自己的记录");
        }
        comments.deleteByRecordId(rid);
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
        if (!records.existsById(rid)) throw new BizException(HttpStatus.NOT_FOUND, "记录不存在");
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
        comments.delete(c);
        return Map.of("ok", true, "message", "已删除");
    }

    // ---------- 工具 ----------
    private String requireLogin(HttpServletRequest req) {
        String u = tokens.currentUser(req);
        if (u == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return u;
    }

    private boolean isAdmin(String username) {
        return users.findByUsername(username).map(User::isAdmin).orElse(false);
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
