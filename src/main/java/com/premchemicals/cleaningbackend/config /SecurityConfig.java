package com.premchemicals.cleaningbackend.config;

import com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter;
import com.premchemicals.cleaningbackend.service.CustomUserDetailsService;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                .cors(cors -> {})

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->

                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authenticationProvider(
                        authenticationProvider()
                )

                .authorizeHttpRequests(auth -> auth

                        // =====================================
                        // AUTH PUBLIC APIs
                        // =====================================

                        .requestMatchers(

                                "/auth/login",

                                "/auth/register",

                                "/api/notifications/test"

                        ).permitAll()

                        // =====================================
                        // FCM TOKEN API
                        // =====================================

                        .requestMatchers(
                                "/api/users/save-fcm-token"
                        ).authenticated()

                        // =====================================
                        // UPLOADS
                        // =====================================

                        .requestMatchers(
                                "/uploads/**"
                        ).permitAll()

                        // =====================================
                        // PUBLIC PRODUCT APIs
                        // =====================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/products/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/categories/**"
                        ).permitAll()

                        // =====================================
                        // PINCODE
                        // =====================================

                        .requestMatchers(
                                "/api/pincode/**"
                        ).permitAll()

                        // =====================================
                        // REVIEWS (PUBLIC READ)
                        // =====================================

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/reviews/**"
                        ).permitAll()

                        // =====================================
                        // ORDERS
                        // =====================================

                        .requestMatchers(
                                "/api/orders/**"
                        ).authenticated()

                        // =====================================
                        // PAYMENTS
                        // =====================================

                        .requestMatchers(
                                "/api/payments/**"
                        ).authenticated()

                        // =====================================
                        // WEBHOOKS
                        // =====================================

                        .requestMatchers(
                                "/api/webhooks/**"
                        ).permitAll()

                        // =====================================
                        // EVERYTHING ELSE
                        // =====================================

                        .anyRequest().authenticated()
                )

                .addFilterBefore(

                        jwtAuthenticationFilter,

                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(

                List.of(
                        "http://localhost:5173"
                )
        );

        configuration.setAllowedMethods(

                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider();

        provider.setUserDetailsService(
                userDetailsService
        );

        provider.setPasswordEncoder(
                passwordEncoder()
        );

        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {

        return config.getAuthenticationManager();
    }
}