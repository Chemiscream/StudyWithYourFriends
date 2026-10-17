package com.studylog.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 限制 API 请求体大小，防止超大 body 打爆内存。
 * Spring Boot/Tomcat 默认只限制表单 POST，application/json 不限制，这里统一兜底。
 */
@Component
@Order(1)
public class BodySizeFilter implements Filter {

    private static final long MAX_BODY = 20L * 1024 * 1024; // 20MB（图片 base64 图文上限 15MB）

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        long len = req.getContentLengthLong();
        if (len > MAX_BODY) {
            ((HttpServletResponse) response).sendError(413, "请求内容过大");
            return;
        }
        chain.doFilter(request, response);
    }
}
