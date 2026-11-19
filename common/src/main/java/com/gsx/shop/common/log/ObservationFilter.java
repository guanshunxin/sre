package com.gsx.shop.common.log;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** 记录实际状态和耗时，路由模板避免高基数/隐私；请求 ID 传给下游运费服务串联日志。 */
@Component
@Order(2)
public class ObservationFilter extends OncePerRequestFilter {
    private final RequestObservations observations;

    public ObservationFilter(RequestObservations observations) {
        this.observations = observations;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-ID"),
                id =
                        supplied != null && supplied.matches("[a-zA-Z0-9-]{1,64}")
                                ? supplied
                                : UUID.randomUUID().toString();
        long start = System.nanoTime();
        Instant at = Instant.now();
        MDC.put("requestId", id);
        response.setHeader("X-Request-ID", id);
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith("/api/")) {
                String route = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
                if (route.equals("null")) route = "UNMATCHED";
                long ms = (System.nanoTime() - start) / 1000000;
                observations.append(new RequestObservations.Entry(at, id, route, response.getStatus(), ms));
                LoggerFactory.getLogger(getClass())
                        .info("Business request; route={}, status={}, durationMs={}", route, response.getStatus(), ms);
            }
            MDC.remove("requestId");
        }
    }
}
