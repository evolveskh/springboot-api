package com.example.springbootapi.filter;

import com.example.springbootapi.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rate-limit.login.max-requests:5}")
    private int loginMaxRequests;

    @Value("${rate-limit.login.window-seconds:60}")
    private int loginWindowSeconds;

    @Value("${rate-limit.register.max-requests:10}")
    private int registerMaxRequests;

    @Value("${rate-limit.register.window-seconds:3600}")
    private int registerWindowSeconds;

    @Value("${rate-limit.transactions.max-requests:20}")
    private int transactionsMaxRequests;

    @Value("${rate-limit.transactions.window-seconds:60}")
    private int transactionsWindowSeconds;

    @Value("${rate-limit.default.max-requests:100}")
    private int defaultMaxRequests;

    @Value("${rate-limit.default.window-seconds:60}")
    private int defaultWindowSeconds;

    public RateLimitingFilter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        int[] limit = resolveLimit(method, path);
        int maxRequests = limit[0];
        int windowSeconds = limit[1];

        String key = "rate_limit:" + method + ":" + path + ":" + ip;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        long currentCount = count != null ? count : 0;

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - currentCount)));

        if (currentCount > maxRequests) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            response.setHeader("X-RateLimit-Retry-After", String.valueOf(ttl != null && ttl > 0 ? ttl : windowSeconds));
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            ErrorResponse errorResponse = new ErrorResponse(
                    LocalDateTime.now(),
                    429,
                    "Too Many Requests",
                    "Rate limit exceeded. Please try again later.",
                    path
            );
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private int[] resolveLimit(String method, String path) {
        if ("POST".equalsIgnoreCase(method)) {
            if (path.equals("/api/auth/login")) {
                return new int[]{loginMaxRequests, loginWindowSeconds};
            }
            if (path.equals("/api/auth/register")) {
                return new int[]{registerMaxRequests, registerWindowSeconds};
            }
            if (path.startsWith("/api/transactions")) {
                return new int[]{transactionsMaxRequests, transactionsWindowSeconds};
            }
        }
        return new int[]{defaultMaxRequests, defaultWindowSeconds};
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
