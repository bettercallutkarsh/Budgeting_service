package com.budgetingapp.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String REQUEST_LOGGER_NAME = "com.budgetingapp.request";

    private static final Logger requestLog = LoggerFactory.getLogger(REQUEST_LOGGER_NAME);
    private static final List<String> EXCLUDED_PATH_PREFIXES = List.of(
            "/swagger-ui", "/v3/api-docs", "/webjars", "/actuator", "/favicon.ico", "/error");

    private final String serviceName;
    private final String environment;
    private final long slowRequestThresholdMs;

    public RequestCorrelationFilter(
            @Value("${spring.application.name:budgeting-backend}") String serviceName,
            @Value("${app.environment:local}") String environment,
            @Value("${app.logging.slow-request-threshold-ms:1000}") long slowRequestThresholdMs) {
        this.serviceName = serviceName;
        this.environment = environment;
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = RequestIds.newId();
        RequestIds.set(requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
            String method = LogSanitizer.sanitize(request.getMethod());
            String route = resolveRoute(request);
            int status = response.getStatus();
            if (isExcluded(request)) {
                requestLog.debug("request.completed requestId={} service={} env={} method={} route={} status={} durationMs={}",
                        requestId, serviceName, environment, method, route, status, durationMs);
            } else {
                requestLog.info("request.completed requestId={} service={} env={} method={} route={} status={} durationMs={}",
                        requestId, serviceName, environment, method, route, status, durationMs);
                if (slowRequestThresholdMs > 0 && durationMs >= slowRequestThresholdMs) {
                    requestLog.warn("request.slow requestId={} method={} route={} status={} durationMs={} thresholdMs={}",
                            requestId, method, route, status, durationMs, slowRequestThresholdMs);
                }
            }
            RequestIds.clear();
        }
    }

    private String resolveRoute(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern != null ? pattern.toString() : request.getRequestURI();
        return LogSanitizer.sanitize(route);
    }

    private boolean isExcluded(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && EXCLUDED_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
