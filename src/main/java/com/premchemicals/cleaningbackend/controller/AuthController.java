package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.LoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.LoginResponseDTO;
import com.premchemicals.cleaningbackend.dto.SaveFcmTokenRequest;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.security.JwtUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final JwtUtil jwtUtil;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    // =========================================
    // REGISTER
    // =========================================

    @PostMapping("/register")
    public String register(
            @RequestBody User request) {

        if (userRepository.findByUsername(
                request.getUsername()
        ).isPresent()) {

            return "Username already exists";
        }

        User user = new User();

        user.setUsername(
                request.getUsername()
        );

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setRole(
                "ROLE_" + request.getRole()
        );

        userRepository.save(user);

        return "User registered successfully";
    }

    // =========================================
    // LOGIN
    // =========================================

    @PostMapping("/login")
    public LoginResponseDTO login(
            @RequestBody LoginRequestDTO request) {

        Authentication authentication =

                authenticationManager.authenticate(

                        new UsernamePasswordAuthenticationToken(

                                request.getUsername(),

                                request.getPassword()
                        )
                );

        UserDetails userDetails =
                (UserDetails)
                        authentication.getPrincipal();

        String token =
                jwtUtil.generateToken(userDetails);

        return new LoginResponseDTO(token);
    }

    // =========================================
    // SAVE FCM TOKEN
    // =========================================

    @PostMapping("/save-fcm-token")
    public String saveFcmToken(
            @RequestBody SaveFcmTokenRequest request,
            Authentication authentication) {

        String username =
                authentication.getName();

        System.out.println(
                "FCM SAVE USER: " + username
        );

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        user.setFcmToken(
                request.getFcmToken()
        );

        userRepository.save(user);

        return "FCM token saved successfully";
    }
}