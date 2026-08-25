package com.premchemicals.cleaningbackend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private final ConcurrentHashMap<String, List<Long>> loginAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Long>> passwordResetAttempts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        String ip = getClientIP(httpRequest);

        if (path.startsWith("/auth/login") || path.startsWith("/auth/register") || path.startsWith("/auth/otp")) {
            if (isRateLimited(ip, loginAttempts, 10, 60000)) { // 10 attempts per minute
                httpResponse.setStatus(429); // Too Many Requests
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\": \"Too many login attempts. Please try again in a minute.\"}");
                return;
            }
        } else if (path.startsWith("/auth/change-password") || path.startsWith("/auth/delete-account")) {
            if (isRateLimited(ip, passwordResetAttempts, 5, 3600000)) { // 5 attempts per hour
                httpResponse.setStatus(429);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\": \"Too many password actions. Please try again in an hour.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip, ConcurrentHashMap<String, List<Long>> attemptsMap, int maxAttempts, long windowMs) {
        long now = System.currentTimeMillis();
        
        attemptsMap.putIfAbsent(ip, Collections.synchronizedList(new ArrayList<>()));
        List<Long> attempts = attemptsMap.get(ip);
        
        synchronized (attempts) {
            attempts.removeIf(timestamp -> (now - timestamp) > windowMs);
            if (attempts.size() >= maxAttempts) {
                return true;
            }
            attempts.add(now);
        }
        return false;
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
