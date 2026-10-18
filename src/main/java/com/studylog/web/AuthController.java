package com.studylog.web;

import com.studylog.model.User;
import com.studylog.repo.UserRepository;
import com.studylog.sec.Passwords;
import com.studylog.sec.Tokens;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    static final String DEFAULT_COLOR = "4a90d9";
    static final List<String> PALETTE = List.of(
            "4a90d9", "e74c3c", "27ae60", "f39c12", "9b59b6",
            "1abc9c", "e67e22", "34495e", "d35400", "8e44ad",
            "16a085", "c0392b", "2980b9", "f1c40f", "7f8c8d");

    private final UserRepository users;
    private final Tokens tokens;
    private final EntityManager em;

    public AuthController(UserRepository users, Tokens tokens, EntityManager em) {
        this.users = users;
        this.tokens = tokens;
        this.em = em;
    }

    static String pickColor(String username) {
        int sum = 0;
        for (int i = 0; i < username.length(); i++) sum += username.charAt(i);
        return PALETTE.get(Math.floorMod(sum, PALETTE.size()));
    }

    @PostMapping("/register")
    Map<String, Object> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        if (username.isEmpty() || password.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        if (username.length() > 32 || password.length() > 128) {
            throw new BizException(HttpStatus.BAD_REQUEST, "用户名或密码过长");
        }
        if (users.existsByUsername(username)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        String color = pickColor(username);
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(Passwords.hash(password));
        u.setColor(color);
        u.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        users.save(u);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("message", "注册成功");
        m.put("token", tokens.make(username));
        m.put("username", username);
        m.put("color", color);
        m.put("is_admin", false);
        return m;
    }

    @PostMapping("/login")
    Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "");
        if (username.isEmpty() || password.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        User u = users.findByUsername(username)
                .filter(x -> Passwords.verify(password, x.getPasswordHash()))
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("token", tokens.make(username));
        m.put("username", username);
        m.put("color", u.getColor() == null ? DEFAULT_COLOR : u.getColor());
        m.put("is_admin", u.isAdmin());
        return m;
    }

    @GetMapping("/me")
    Map<String, Object> me(HttpServletRequest req) {
        String u = tokens.currentUser(req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        if (u == null) {
            m.put("username", null);
            m.put("color", null);
            m.put("is_admin", false);
        } else {
            User user = users.findByUsername(u).orElse(null);
            m.put("username", u);
            m.put("color", user == null || user.getColor() == null ? DEFAULT_COLOR : user.getColor());
            m.put("is_admin", user != null && user.isAdmin());
        }
        return m;
    }

    /** 用户列表（登录用户可见，供留言 @提及自动补全；管理员账号对普通用户隐藏）。 */
    @GetMapping("/users")
    Map<String, Object> listUsers(HttpServletRequest req) {
        if (tokens.currentUser(req) == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        List<Map<String, Object>> items = new ArrayList<>();
        for (User u : users.findAllByAdminFalseOrderByUsername()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.getUsername());
            m.put("color", u.getColor() == null ? DEFAULT_COLOR : u.getColor());
            items.add(m);
        }
        return Map.of("ok", true, "users", items);
    }

    /**
     * 改名：站点所有关联都以 username 字符串快照存储（无数据库外键），
     * 因此改名 = 在一个事务里级联更新所有含 username 的表。
     * 注意：新增含 username 的表时，必须在这里同步加一条 UPDATE。
     */
    @PutMapping("/users/rename")
    @Transactional
    Map<String, Object> rename(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String oldName = tokens.currentUser(req);
        if (oldName == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        String newName = body.getOrDefault("new_username", "").trim();
        if (newName.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, "新用户名不能为空");
        if (newName.length() > 32) throw new BizException(HttpStatus.BAD_REQUEST, "用户名过长（最多 32 字）");
        if (newName.equals(oldName)) throw new BizException(HttpStatus.BAD_REQUEST, "新用户名与当前用户名相同");
        if (users.existsByUsername(newName)) throw new BizException(HttpStatus.BAD_REQUEST, "用户名已存在");

        User u = users.findByUsername(oldName)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        u.setUsername(newName);
        users.save(u); // username 不是主键（主键是自增 id），save 按 id 更新，安全
        em.createNativeQuery("UPDATE records SET username = :n WHERE username = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE comments SET username = :n WHERE username = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE comments SET reply_to = :n WHERE reply_to = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE ai_messages SET username = :n WHERE username = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE record_likes SET username = :n WHERE username = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE comment_likes SET username = :n WHERE username = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE record_favorites SET username = :n WHERE username = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE notifications SET recipient = :n WHERE recipient = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();
        em.createNativeQuery("UPDATE notifications SET actor = :n WHERE actor = :o")
                .setParameter("n", newName).setParameter("o", oldName).executeUpdate();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("message", "改名成功");
        m.put("username", newName);
        m.put("token", tokens.make(newName)); // 旧 token 里的名字已失效，重签新 token
        m.put("color", u.getColor() == null ? DEFAULT_COLOR : u.getColor());
        m.put("is_admin", u.isAdmin());
        return m;
    }

    /** 修改密码：校验原密码后更新；token 与密码无关，无需重签。 */
    @PutMapping("/users/password")
    Map<String, Object> changePassword(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String username = tokens.currentUser(req);
        if (username == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        String oldPwd = body.getOrDefault("old_password", "");
        String newPwd = body.getOrDefault("new_password", "");
        if (oldPwd.isEmpty() || newPwd.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        if (newPwd.length() > 128) {
            throw new BizException(HttpStatus.BAD_REQUEST, "新密码过长（最多 128 位）");
        }
        User u = users.findByUsername(username)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (!Passwords.verify(oldPwd, u.getPasswordHash())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "原密码错误");
        }
        u.setPasswordHash(Passwords.hash(newPwd));
        users.save(u);
        return Map.of("ok", true, "message", "密码已修改");
    }

    @PutMapping("/users/color")
    Map<String, Object> setColor(HttpServletRequest req, @RequestBody Map<String, String> body) {
        String username = tokens.currentUser(req);
        if (username == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        String color = body.getOrDefault("color", "").trim().replaceFirst("^#", "").toLowerCase();
        if (color.length() != 6) {
            throw new BizException(HttpStatus.BAD_REQUEST, "颜色格式错误（需 6 位 hex）");
        }
        try {
            Long.parseLong(color, 16);
        } catch (NumberFormatException e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "颜色格式错误");
        }
        User u = users.findByUsername(username)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));
        u.setColor(color);
        users.save(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("color", color);
        m.put("message", "颜色已更新");
        return m;
    }
}
