package com.premchemicals.cleaningbackend.security;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();

        if (path.equals("/auth/login") || path.equals("/auth/register") || path.startsWith("/auth/google/") || path.startsWith("/auth/otp/") || path.equals("/auth/check-phone")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {

            String jwt = authHeader.substring(7);

            if (tokenBlacklistService.isBlacklisted(jwt)) {
                sendUnauthorizedError(response, "Token has been invalidated (logged out).");
                return;
            }

            String phoneNumber = jwtUtil.extractUsername(jwt);

            if (phoneNumber != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                Optional<User> userOptional = userRepository.findByPhoneNumber(phoneNumber);

                if (userOptional.isEmpty()) {
                    sendUnauthorizedError(response, "User account not found.");
                    return;
                }

                User user = userOptional.get();

                if (!user.isActive()) {
                    sendUnauthorizedError(response, "Account is deactivated.");
                    return;
                }

                if (user.getRole() == null) {
                    sendUnauthorizedError(response, "User role is invalid or unassigned.");
                    return;
                }

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority(user.getRole().name())
                );

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                phoneNumber,
                                null,
                                authorities
                        );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authToken);
            }

        } catch (ExpiredJwtException e) {
            log.warn("JWT authentication failed: Token has expired.");
            sendUnauthorizedError(response, "JWT token has expired. Please login again.");
            return;
        } catch (JwtException e) {
            log.warn("JWT authentication failed: Token signature or format is invalid.");
            sendUnauthorizedError(response, "Invalid JWT token signature or format.");
            return;
        } catch (Exception e) {
            log.error("JWT authentication failed due to processing error.");
            sendUnauthorizedError(response, "Authentication failed.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}