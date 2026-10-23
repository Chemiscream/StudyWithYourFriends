package com.studylog.web;

import com.studylog.model.Notification;
import com.studylog.repo.NotificationRepository;
import com.studylog.sec.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 站内通知：别人评论你的记录或回复你的评论时产生。 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    /** 列表最多返回的条数，防止长期堆积拖慢页面。 */
    private static final int MAX_LIST = 50;

    private final NotificationRepository notifications;
    private final Tokens tokens;

    public NotificationController(NotificationRepository notifications, Tokens tokens) {
        this.notifications = notifications;
        this.tokens = tokens;
    }

    @GetMapping
    Map<String, Object> list(HttpServletRequest req) {
        String me = requireLogin(req);
        long unread = notifications.countByRecipientAndReadFalse(me);
        List<Notification> all = notifications.findByRecipientOrderByCreatedAtDesc(me);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Notification n : all) {
            if (items.size() >= MAX_LIST) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("actor", n.getActor());
            m.put("record_id", n.getRecordId());
            m.put("board_id", n.getBoardId());
            m.put("preview", n.getPreview());
            m.put("read", n.isRead());
            m.put("created_at", n.getCreatedAt());
            items.add(m);
        }
        return Map.of("ok", true, "unread", unread, "items", items);
    }

    /** 单条标记已读（点击通知跳转时调用）。 */
    @PostMapping("/{id}/read")
    Map<String, Object> markRead(HttpServletRequest req, @PathVariable Long id) {
        String me = requireLogin(req);
        Notification n = notifications.findByIdAndRecipient(id, me)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "通知不存在"));
        if (!n.isRead()) {
            n.setRead(true);
            notifications.save(n);
        }
        return Map.of("ok", true);
    }

    /** 一键清除：删除我的全部通知。 */
    @PostMapping("/clear")
    Map<String, Object> clear(HttpServletRequest req) {
        String me = requireLogin(req);
        notifications.deleteByRecipient(me);
        return Map.of("ok", true, "message", "已清除");
    }

    private String requireLogin(HttpServletRequest req) {
        String u = tokens.currentUser(req);
        if (u == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return u;
    }
}
