package com.studylog.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** HTML 页面禁止缓存，保证发版后用户刷新即拿到新版（与 Python 版行为一致）。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();
        if (uri.equals("/") || uri.endsWith(".html")) {
            var res = (jakarta.servlet.http.HttpServletResponse) response;
            res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            res.setHeader("Pragma", "no-cache");
            res.setHeader("Expires", "0");
        }
        chain.doFilter(request, response);
    }
}
