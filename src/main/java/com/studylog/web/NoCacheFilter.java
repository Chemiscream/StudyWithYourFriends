package com.studylog.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** HTML 页面禁止缓存并强制 UTF-8，保证发版后用户刷新即拿到新版（与 Python 版行为一致）。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoCacheFilter implements Filter {

    private static final String HTML_UTF8 = "text/html;charset=UTF-8";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();
        boolean html = uri.equals("/") || uri.endsWith(".html");
        HttpServletResponse res = (HttpServletResponse) response;
        if (html) {
            res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            res.setHeader("Pragma", "no-cache");
            res.setHeader("Expires", "0");
            chain.doFilter(request, new Utf8HtmlWrapper(res));
        } else {
            chain.doFilter(request, response);
        }
    }

    /** 把静态 HTML 的 Content-Type 统一成 text/html;charset=UTF-8。 */
    private static class Utf8HtmlWrapper extends HttpServletResponseWrapper {
        Utf8HtmlWrapper(HttpServletResponse response) { super(response); }

        @Override
        public void setContentType(String type) {
            super.setContentType(isHtml(type) ? HTML_UTF8 : type);
        }

        @Override
        public void setHeader(String name, String value) {
            if ("Content-Type".equalsIgnoreCase(name) && isHtml(value)) value = HTML_UTF8;
            super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if ("Content-Type".equalsIgnoreCase(name) && isHtml(value)) value = HTML_UTF8;
            super.addHeader(name, value);
        }

        private static boolean isHtml(String type) {
            return type != null && type.toLowerCase().startsWith("text/html");
        }
    }
}
