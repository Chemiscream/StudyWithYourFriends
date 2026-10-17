package com.studylog.web;

import com.studylog.model.User;
import com.studylog.repo.UserRepository;
import com.studylog.sec.Passwords;
import com.studylog.sec.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    public AuthController(UserRepository users, Tokens tokens) {
        this.users = users;
        this.tokens = tokens;
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
