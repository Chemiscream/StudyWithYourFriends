package com.studylog.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** 业务异常：带 HTTP 状态码和提示信息，对应 Python 版 jsonify + 状态码。 */
class BizException extends RuntimeException {
    final HttpStatus status;

    BizException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}

@RestControllerAdvice
class BizExceptionHandler {
    @ExceptionHandler(BizException.class)
    ResponseEntity<Map<String, Object>> handle(BizException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("message", e.getMessage());
        return ResponseEntity.status(e.status).body(body);
    }
}

final class Resp {
    private Resp() {}

    static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    static Map<String, Object> ok(String key, Object value) {
        Map<String, Object> m = ok();
        m.put(key, value);
        return m;
    }

    static Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("message", message);
        return m;
    }
}
