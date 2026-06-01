package com.premchemicals.cleaningbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(

            HttpServletRequest request,

            HttpServletResponse response,

            FilterChain filterChain

    ) throws ServletException, IOException {

        String path = request.getServletPath();

        // =====================================
        // PUBLIC ENDPOINTS ONLY
        // =====================================

        if (

                path.equals("/auth/login")

                        ||

                        path.equals("/auth/register")

        ) {

            filterChain.doFilter(request, response);

            return;
        }

        // =====================================
        // AUTH HEADER
        // =====================================

        final String authHeader =
                request.getHeader("Authorization");

        if (

                authHeader == null

                        ||

                        !authHeader.startsWith("Bearer ")

        ) {

            filterChain.doFilter(request, response);

            return;
        }

        try {

            String jwt =
                    authHeader.substring(7);

            String username =
                    jwtUtil.extractUsername(jwt);

            // =====================================
            // SAFE ROLE EXTRACTION
            // =====================================

            String role = "ROLE_USER";

            try {

                String extractedRole =
                        jwtUtil.extractRole(jwt);

                if (

                        extractedRole != null

                                &&

                                !extractedRole.isBlank()

                ) {

                    role = extractedRole.toUpperCase();

                    if (!role.startsWith("ROLE_")) {

                        role = "ROLE_" + role;
                    }
                }

            } catch (Exception ignored) {
            }

            System.out.println(
                    "JWT USERNAME: " + username
            );

            System.out.println(
                    "JWT ROLE: " + role
            );

            // =====================================
            // SET AUTHENTICATION
            // =====================================

            if (

                    username != null

                            &&

                            SecurityContextHolder
                                    .getContext()
                                    .getAuthentication() == null

            ) {

                UsernamePasswordAuthenticationToken authToken =

                        new UsernamePasswordAuthenticationToken(

                                username,

                                null,

                                Collections.singletonList(
                                        new SimpleGrantedAuthority(role)
                                )
                        );

                authToken.setDetails(

                        new WebAuthenticationDetailsSource()

                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authToken);
            }

        } catch (Exception e) {

            System.out.println(
                    "JWT FILTER ERROR: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }

        filterChain.doFilter(request, response);
    }
}