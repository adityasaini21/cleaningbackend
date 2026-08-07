package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.LoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.LoginResponseDTO;
import com.premchemicals.cleaningbackend.dto.SaveFcmTokenRequest;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
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
    // REGISTER CUSTOMER
    // =========================================

    @PostMapping("/register")
    public String register(
            @RequestBody User request
    ) {

        String phoneNumber =
                request.getPhoneNumber().trim();

        if (userRepository.existsByPhoneNumber(phoneNumber)) {

            return "Phone number already registered";
        }

        User user = new User();

        user.setPhoneNumber(phoneNumber);

        user.setPassword(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setRole(Role.ROLE_USER);

        user.setActive(true);

        user.setFullName(
                request.getFullName().trim()
        );

        if (request.getEmail() != null) {
            user.setEmail(
                    request.getEmail().trim()
            );
        }

        if (request.getAddress() != null) {
            user.setAddress(
                    request.getAddress().trim()
            );
        }

        if (request.getCity() != null) {
            user.setCity(
                    request.getCity().trim()
            );
        }

        if (request.getState() != null) {
            user.setState(
                    request.getState().trim()
            );
        }

        if (request.getPincode() != null) {
            user.setPincode(
                    request.getPincode().trim()
            );
        }

        if (request.getLandmark() != null) {
            user.setLandmark(
                    request.getLandmark().trim()
            );
        }

        userRepository.save(user);

        return "User registered successfully";
    }

    // =========================================
    // LOGIN
    // =========================================

    @PostMapping("/login")
    public LoginResponseDTO login(
            @RequestBody LoginRequestDTO request
    ) {

        Authentication authentication =
                authenticationManager.authenticate(

                        new UsernamePasswordAuthenticationToken(

                                request.getPhoneNumber().trim(),

                                request.getPassword()
                        )
                );

        UserDetails userDetails =
                (UserDetails) authentication.getPrincipal();

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

            Authentication authentication
    ) {

        String phoneNumber =
                authentication.getName();

        User user =
                userRepository
                        .findByPhoneNumber(phoneNumber)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        if (request.getFcmToken() != null) {

            user.setFcmToken(
                    request.getFcmToken().trim()
            );

            userRepository.save(user);
        }

        return "FCM token saved successfully";
    }
}