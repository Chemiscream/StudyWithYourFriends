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
 * 媒体改为上传接口后，JSON 只承载正文（几百 KB），multipart 才需要放宽到视频上限。
 */
@Component
@Order(1)
public class BodySizeFilter implements Filter {

    private static final long MAX_JSON_BODY = 8L * 1024 * 1024;       // 8MB
    private static final long MAX_MULTIPART_BODY = 64L * 1024 * 1024; // 64MB（视频 40MB + 表单开销）

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String ct = req.getContentType();
        boolean multipart = ct != null && ct.toLowerCase().startsWith("multipart/");
        long max = multipart ? MAX_MULTIPART_BODY : MAX_JSON_BODY;
        long len = req.getContentLengthLong();
        if (len > max) {
            ((HttpServletResponse) response).sendError(413, "请求内容过大");
            return;
        }
        chain.doFilter(request, response);
    }
}
