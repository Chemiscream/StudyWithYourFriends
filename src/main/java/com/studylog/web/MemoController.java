package com.studylog.web;

import com.studylog.model.Memo;
import com.studylog.repo.MemoRepository;
import com.studylog.sec.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 个人备忘录：登录后每个账号只能看到、操作自己的条目。 */
@RestController
@RequestMapping("/api/memos")
public class MemoController {

    /** 单个账号最多保存的条目数。 */
    private static final int MAX_ITEMS = 200;
    /** 单条内容最长字数。 */
    private static final int MAX_LEN = 300;

    private final MemoRepository memos;
    private final Tokens tokens;

    public MemoController(MemoRepository memos, Tokens tokens) {
        this.memos = memos;
        this.tokens = tokens;
    }

    @GetMapping
    Map<String, Object> list(HttpServletRequest req) {
        String me = requireLogin(req);
        List<Memo> all = memos.findByUsernameOrderByDoneAscCreatedAtDesc(me);
        List<Map<String, Object>> items = new ArrayList<>();
        long pending = 0;
        for (Memo m : all) {
            if (!m.isDone()) pending++;
            items.add(view(m));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("items", items);
        r.put("pending", pending);
        return r;
    }

    @PostMapping
    Map<String, Object> add(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String me = requireLogin(req);
        String content = clean(body.get("content"));
        if (content.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, "内容不能为空");
        if (content.length() > MAX_LEN) throw new BizException(HttpStatus.BAD_REQUEST, "内容最多 " + MAX_LEN + " 字");
        if (memos.countByUsername(me) >= MAX_ITEMS) {
            throw new BizException(HttpStatus.BAD_REQUEST, "备忘录最多保存 " + MAX_ITEMS + " 条，请先清理一些");
        }
        Memo m = new Memo();
        m.setUsername(me);
        m.setContent(content);
        m.setCreatedAt(OffsetDateTime.now());
        memos.save(m);
        return Map.of("ok", true, "item", view(m));
    }

    /** 勾选 / 取消勾选，或修改内容。 */
    @PutMapping("/{id}")
    Map<String, Object> update(HttpServletRequest req, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String me = requireLogin(req);
        Memo m = memos.findByIdAndUsername(id, me)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "条目不存在"));
        if (body.containsKey("content")) {
            String content = clean((String) body.get("content"));
            if (content.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, "内容不能为空");
            if (content.length() > MAX_LEN) throw new BizException(HttpStatus.BAD_REQUEST, "内容最多 " + MAX_LEN + " 字");
            m.setContent(content);
        }
        Object done = body.get("done");
        if (done instanceof Boolean b) m.setDone(b);
        m.setUpdatedAt(OffsetDateTime.now());
        memos.save(m);
        return Map.of("ok", true, "item", view(m));
    }

    @DeleteMapping("/{id}")
    Map<String, Object> delete(HttpServletRequest req, @PathVariable Long id) {
        String me = requireLogin(req);
        Memo m = memos.findByIdAndUsername(id, me)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "条目不存在"));
        memos.delete(m);
        return Map.of("ok", true, "message", "已删除");
    }

    private Map<String, Object> view(Memo m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("content", m.getContent());
        r.put("done", m.isDone());
        r.put("created_at", m.getCreatedAt());
        return r;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private String requireLogin(HttpServletRequest req) {
        String u = tokens.currentUser(req);
        if (u == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return u;
    }
}
