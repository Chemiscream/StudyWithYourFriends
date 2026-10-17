package com.studylog.web;

import org.springframework.http.HttpStatus;

/** 业务异常：带 HTTP 状态码和提示信息，对应 Python 版 jsonify + 状态码。 */
public class BizException extends RuntimeException {
    final HttpStatus status;

    public BizException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
