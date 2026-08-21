package com.premchemicals.cleaningbackend.security;

import com.premchemicals.cleaningbackend.service.ServiceStatusService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ServiceSuspensionFilter implements Filter {

    private final ServiceStatusService serviceStatusService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (serviceStatusService.isServiceSuspended()) {
            boolean isAllowed = path.startsWith("/admin/") || 
                                path.startsWith("/auth/") || 
                                path.equals("/api/service-status") || 
                                path.startsWith("/uploads/");

            if (!isAllowed) {
                httpResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                httpResponse.getWriter().write(
                        "{\"error\":\"SERVICE_SUSPENDED\",\"message\":\"Service is temporarily suspended for maintenance.\"}"
                );
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
