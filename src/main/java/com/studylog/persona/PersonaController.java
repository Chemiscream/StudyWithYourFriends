package com.studylog.persona;

import com.studylog.sec.Tokens;
import com.studylog.web.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 五维人格指数：/persona/me 需登录，/persona/{username} 公开（管理员与 AI 助手不展示）。 */
@RestController
@RequestMapping("/api")
public class PersonaController {

    private final Tokens tokens;
    private final PersonaService persona;

    public PersonaController(Tokens tokens, PersonaService persona) {
        this.tokens = tokens;
        this.persona = persona;
    }

    @GetMapping("/persona/me")
    Map<String, Object> me(HttpServletRequest req) {
        String me = tokens.currentUser(req);
        if (me == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return persona.snapshot(me);
    }

    @GetMapping("/persona/{username}")
    Map<String, Object> byUsername(@PathVariable String username) {
        return persona.publicSnapshot(username == null ? "" : username.trim());
    }

    /** 登录后拉取别人带来的未读加分（合并成一份，供前端播放一次 POINT UP）。 */
    @GetMapping("/persona/pending")
    Map<String, Object> pending(HttpServletRequest req) {
        String me = tokens.currentUser(req);
        if (me == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        return persona.pending(me);
    }

    /** 动画播放完毕，标记未读加分已见，避免下次登录重复播放。 */
    @PostMapping("/persona/pending/seen")
    Map<String, Object> pendingSeen(HttpServletRequest req) {
        String me = tokens.currentUser(req);
        if (me == null) throw new BizException(HttpStatus.UNAUTHORIZED, "未登录");
        persona.markPendingSeen(me);
        return Map.of("ok", true);
    }
}
