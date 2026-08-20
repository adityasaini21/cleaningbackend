package com.premchemicals.cleaningbackend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.premchemicals.cleaningbackend.dto.GoogleLoginResponseDTO;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Value("${google.client.id:}")
    private String googleClientId;

    /**
     * Verifies the Google ID Token and checks if user exists.
     */
    public GoogleLoginResponseDTO loginWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = verifyGoogleToken(idTokenString);

        String email = payload.getEmail();
        String name = (String) payload.get("name");

        // Check if user already exists by email
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            // User exists! Generate our custom JWT token and return login details
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getPhoneNumber());
            String token = jwtUtil.generateToken(userDetails);
            return new GoogleLoginResponseDTO(false, token, email, user.getFullName());
        } else {
            // User is new! Signal that they need to complete registration (add phone number)
            return new GoogleLoginResponseDTO(true, null, email, name);
        }
    }

    /**
     * Registers a new user using Google profile data and user-provided phone number.
     */
    @Transactional
    public GoogleLoginResponseDTO registerWithGoogle(String idTokenString, String phoneNumber) {
        GoogleIdToken.Payload payload = verifyGoogleToken(idTokenString);

        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String cleanPhone = phoneNumber.trim();

        // 1. Double check phone unique constraint
        if (userRepository.existsByPhoneNumber(cleanPhone)) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        // 2. Double check email unique constraint
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered with another account");
        }

        // 3. Create and save new user
        User user = User.builder()
                .fullName(name)
                .email(email)
                .phoneNumber(cleanPhone)
                // Google users don't use passwords directly, so we write a random secure UUID hash
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role(Role.ROLE_USER)
                .active(true)
                .build();

        userRepository.save(user);

        // 4. Generate JWT
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getPhoneNumber());
        String token = jwtUtil.generateToken(userDetails);

        return new GoogleLoginResponseDTO(false, token, email, user.getFullName());
    }

    /**
     * Helper to verify ID Token with Google's public certificates.
     */
    private GoogleIdToken.Payload verifyGoogleToken(String idTokenString) {
        try {
            NetHttpTransport transport = new NetHttpTransport();
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

            GoogleIdTokenVerifier.Builder verifierBuilder = new GoogleIdTokenVerifier.Builder(transport, jsonFactory);

            // Add client ID restriction if configured in application.properties
            if (googleClientId != null && !googleClientId.trim().isEmpty()) {
                verifierBuilder.setAudience(Collections.singletonList(googleClientId.trim()));
            }

            GoogleIdTokenVerifier verifier = verifierBuilder.build();
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken != null) {
                return idToken.getPayload();
            }
        } catch (Exception e) {
            throw new RuntimeException("Google ID Token verification failed: " + e.getMessage(), e);
        }
        throw new IllegalArgumentException("Invalid Google ID Token");
    }
}
