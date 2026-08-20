package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.LoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.LoginResponseDTO;
import com.premchemicals.cleaningbackend.dto.SaveFcmTokenRequest;
import com.premchemicals.cleaningbackend.dto.UserProfileDTO;
import com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO;
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
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.premchemicals.cleaningbackend.dto.GoogleLoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.GoogleRegisterRequestDTO;
import com.premchemicals.cleaningbackend.dto.GoogleLoginResponseDTO;
import com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO;
import com.premchemicals.cleaningbackend.service.OtpService;
import java.util.UUID;
import java.util.Map;
import com.premchemicals.cleaningbackend.service.GoogleAuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final JwtUtil jwtUtil;

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final GoogleAuthService googleAuthService;

    private final OtpService otpService;

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
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Phone number already registered");
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
    // GOOGLE SIGN-IN LOGIN
    // =========================================
    @PostMapping("/google/login")
    public GoogleLoginResponseDTO loginWithGoogle(@Valid @RequestBody GoogleLoginRequestDTO request) {
        return googleAuthService.loginWithGoogle(request.getIdToken());
    }

    // =========================================
    // GOOGLE SIGN-IN REGISTER
    // =========================================
    @PostMapping("/google/register")
    public GoogleLoginResponseDTO registerWithGoogle(@Valid @RequestBody GoogleRegisterRequestDTO request) {
        return googleAuthService.registerWithGoogle(request.getIdToken(), request.getPhoneNumber());
    }

    // =========================================
    // OTP PASSWORDLESS LOGIN & REGISTER
    // =========================================
    @PostMapping("/otp/login")
    public ResponseEntity<?> loginOrRegisterWithOtp(@Valid @RequestBody OtpLoginRequestDTO request) {
        String phone = request.getPhoneNumber().trim();
        String otp = request.getOtp().trim();

        // 1. Verify OTP first
        boolean isOtpValid = otpService.verifyOtp(phone, otp);
        if (!isOtpValid) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
        }

        // 2. Check if user exists by phone
        User user = userRepository.findByPhoneNumber(phone).orElse(null);
        boolean isNewUser = false;

        if (user == null) {
            // User does not exist, so register them!
            String name = request.getFullName();
            if (name == null || name.trim().isEmpty()) {
                name = "Customer"; // Fallback name
            } else {
                name = name.trim();
            }

            user = User.builder()
                    .phoneNumber(phone)
                    .fullName(name)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.ROLE_USER)
                    .active(true)
                    .build();

            userRepository.save(user);
            isNewUser = true;
        }

        // 3. Generate JWT Token
        UserDetails userDetails = org.springframework.security.core.userdetails.User.withUsername(user.getPhoneNumber())
                .password(user.getPassword())
                .authorities(user.getRole().name())
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new OtpLoginResponseDTO(token, isNewUser, user.getFullName()));
    }

    // =========================================
    // CHECK IF PHONE NUMBER EXISTS
    // =========================================
    @GetMapping("/check-phone")
    public ResponseEntity<?> checkPhoneExists(@RequestParam String phoneNumber) {
        boolean exists = userRepository.existsByPhoneNumber(phoneNumber.trim());
        return ResponseEntity.ok(Map.of("exists", exists));
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

    // =========================================
    // GET PROFILE
    // =========================================
    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public UserProfileDTO getProfile(Authentication authentication) {
        String phoneNumber = authentication.getName();
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));

        UserProfileDTO dto = new UserProfileDTO();
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setAddress(user.getAddress());
        dto.setCity(user.getCity());
        dto.setState(user.getState());
        dto.setPincode(user.getPincode());
        dto.setLandmark(user.getLandmark());
        return dto;
    }

    // =========================================
    // UPDATE PROFILE
    // =========================================
    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public UserProfileDTO updateProfile(
            @RequestBody UserProfileDTO request,
            Authentication authentication
    ) {
        String phoneNumber = authentication.getName();
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));

        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        user.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);
        user.setCity(request.getCity() != null ? request.getCity().trim() : null);
        user.setState(request.getState() != null ? request.getState().trim() : null);
        user.setPincode(request.getPincode() != null ? request.getPincode().trim() : null);
        user.setLandmark(request.getLandmark() != null ? request.getLandmark().trim() : null);

        userRepository.save(user);
        return request;
    }

    // =========================================
    // CHANGE PASSWORD
    // =========================================
    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public String changePassword(
            @RequestBody ChangePasswordRequestDTO request,
            Authentication authentication
    ) {
        String phoneNumber = authentication.getName();
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Incorrect old password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return "Password changed successfully";
    }
}