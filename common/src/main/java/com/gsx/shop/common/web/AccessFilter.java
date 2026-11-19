package com.gsx.shop.common.web;

import com.gsx.shop.common.config.AccessProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 取证与业务口令隔离；健康存活不依赖外部系统，故障入口还需独立演练口令。 */
@Component
@Order(1)
public class AccessFilter extends OncePerRequestFilter {
    private final AccessProperties p;

    public AccessFilter(AccessProperties p) {
        this.p = p;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator/health")) {
            chain.doFilter(request, response);
            return;
        }
        boolean observation = path.startsWith("/actuator/") || path.startsWith("/internal/observations/");
        boolean valid =
                same(request.getHeader("Authorization"), "Bearer " + (observation ? p.metricsToken() : p.apiToken()));
        if (path.startsWith("/internal/faults") || path.startsWith("/internal/lab"))
            valid &= same(request.getHeader("X-Lab-Token"), p.labToken());
        if (!valid) {
            response.setStatus(401);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"需要有效访问口令\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean same(String actual, String expected) {
        return actual != null
                && MessageDigest.isEqual(
                        actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
