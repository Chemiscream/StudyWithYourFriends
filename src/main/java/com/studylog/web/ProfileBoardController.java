package com.studylog.web;

import com.studylog.ai.AiAssistantService;
import com.studylog.model.Notification;
import com.studylog.model.ProfileMessage;
import com.studylog.model.User;
import com.studylog.persona.PersonaService;
import com.studylog.repo.NotificationRepository;
import com.studylog.repo.ProfileMessageRepository;
import com.studylog.repo.UserRepository;
import com.studylog.sec.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 个人档案留言板：访客在他人档案页留一句话。
 * 可见性与档案页一致——管理员与 AI 助手的留言板对普通访客隐藏（本人仍可查看自己的）。
 */
@RestController
@RequestMapping("/api")
public class ProfileBoardController {

    /** 单条留言长度上限（纯文本，前端按纯文本渲染）。 */
    private static final int MAX_CONTENT = 500;
    private static final int DEFAULT_PAGE_SIZE = 8;
    private static final int MAX_PAGE_SIZE = 30;

    private final ProfileMessageRepository board;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final Tokens tokens;
    private final PersonaService persona;

    public ProfileBoardController(ProfileMessageRepository board, UserRepository users,
                                  NotificationRepository notifications, Tokens tokens,
                                  PersonaService persona) {
        this.board = board;
        this.users = users;
        this.notifications = notifications;
        this.tokens = tokens;
        this.persona = persona;
    }

    /** 留言列表：公开可读；before 为上一页最后一条的 id（游标分页，避免新留言插队导致重复）。 */
    @GetMapping("/persona/{username}/board")
    Map<String, Object> list(HttpServletRequest req,
                             @PathVariable String username,
                             @RequestParam(name = "before", required = false) Long before,
                             @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        String name = username == null ? "" : username.trim();
        String me = tokens.currentUser(req);
        visibleTarget(name, me);

        int limit = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        List<ProfileMessage> rows = before == null || before <= 0
                ? board.findByProfileUsernameOrderByIdDesc(name, PageRequest.of(0, limit + 1))
                : board.findByProfileUsernameAndIdLessThanOrderByIdDesc(name, before, PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        if (hasMore) rows = rows.subList(0, limit);

        boolean admin = me != null && isAdmin(me);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ProfileMessage m : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("username", m.getUsername());
            item.put("color", colorOf(m.getUsername()));
            item.put("content", m.getContent());
            item.put("created_at", m.getCreatedAt());
            item.put("mine", m.getUsername().equals(me));
            // 作者本人、留言板主人、管理员都可以删
            item.put("can_delete", m.getUsername().equals(me) || name.equals(me) || admin);
            items.add(item);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("total", board.countByProfileUsername(name));
        out.put("items", items);
        out.put("has_more", hasMore);
        return out;
    }

    /** 发布留言（登录后可用）。 */
    @PostMapping("/persona/{username}/board")
    Map<String, Object> add(HttpServletRequest req, @PathVariable String username,
                            @RequestBody Map<String, String> body) {
        String me = requireLogin(req);
        String name = username == null ? "" : username.trim();
        visibleTarget(name, me);

        String content = body.getOrDefault("content", "").trim();
        if (content.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, "留言不能为空");
        if (content.length() > MAX_CONTENT) {
            throw new BizException(HttpStatus.BAD_REQUEST, "留言过长（最多 " + MAX_CONTENT + " 字）");
        }

        ProfileMessage m = new ProfileMessage();
        m.setProfileUsername(name);
        m.setUsername(me);
        m.setContent(content);
        m.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        board.save(m);

        // 五维人格：留言者体贴加分（即时播放），板主魅力加分（留作未读）
        Map<String, Object> personaResult = persona.onBoardMessage(name, m);
        // 通知板主（自己给自己留言不通知）
        if (!name.equals(me)) {
            String preview = content.length() > 60 ? content.substring(0, 60) + "…" : content;
            saveNotification(name, me, "board", m.getId(), preview);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("id", m.getId());
        out.put("message", "已留言");
        out.putAll(personaResult);
        return out;
    }

    /** 删除留言：作者、留言板主人、管理员均可。 */
    @DeleteMapping("/persona/board/{id}")
    Map<String, Object> delete(HttpServletRequest req, @PathVariable Long id) {
        String me = requireLogin(req);
        ProfileMessage m = board.findById(id)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "留言不存在"));
        boolean allowed = m.getUsername().equals(me) || m.getProfileUsername().equals(me) || isAdmin(me);
        if (!allowed) throw new BizException(HttpStatus.FORBIDDEN, "只能删除自己的留言");
        notifications.deleteByBoardId(id);
        board.delete(m);
        return Map.of("ok", true, "message", "已删除");
    }

    // ---------- 工具 ----------

    /**
     * 档案可见性：管理员与 AI 助手的档案对外隐藏，统一按用户不存在处理；
     * 本人（含管理员本人）查看自己的留言板不受限制。
     */
    private User visibleTarget(String name, String me) {
        User u = users.findByUsername(name)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        boolean self = me != null && me.equals(name);
        if (!self && (u.isAdmin() || AiAssistantService.AI_NAME.equals(name))) {
            throw new BizException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return u;
    }

    /** 留言板通知：board 类型不带 record_id，只带 board_id，前端点击后打开自己的档案页。 */
    private void saveNotification(String recipient, String actor, String type, Long boardId, String preview) {
        if (AiAssistantService.AI_NAME.equals(recipient)) return;
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setActor(actor);
        n.setType(type);
        n.setRecordId(null);
        n.setBoardId(boardId);
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

    private String colorOf(String username) {
        return users.findByUsername(username).map(User::getColor).orElse(AuthController.DEFAULT_COLOR);
    }
}
