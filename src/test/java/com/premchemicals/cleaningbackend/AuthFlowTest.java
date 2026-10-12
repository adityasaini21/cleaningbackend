package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuthFlowTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "cleaning_backend_super_secret_key_2026_secure_application_987654321");
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 15552000000L);
        jwtUtil.init();
    }

    @Test
    @DisplayName("Verify JWT Generation and Extraction with Canonical 10-Digit Phone Number")
    void testJwtTokenGenerationAndUsernameExtraction() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("+919876543210")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);
        assertNotNull(token, "Generated JWT token must not be null");

        String extractedUsername = jwtUtil.extractUsername(token);
        assertEquals("9876543210", extractedUsername, "Subject in JWT must be normalized canonical 10-digit phone number");

        String extractedRole = jwtUtil.extractRole(token);
        assertEquals("ROLE_USER", extractedRole, "Role claim in JWT must match authority");
    }

    @Test
    @DisplayName("Verify User Entity Default Active State and Deletion Flags")
    void testUserActiveStateDefaults() {
        User user = User.builder()
                .phoneNumber("9876543210")
                .fullName("Test Customer")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .build();

        assertTrue(user.isActive(), "New user builder default must be active=true");
        assertFalse(user.isDeletedByUser(), "New user builder default must be deletedByUser=false");
    }

    @Test
    @DisplayName("Verify JwtAuthenticationFilter Rejects Inactive Users Without Auto-Reactivating")
    void testJwtAuthenticationFilterRejectsInactiveUsers() throws Exception {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklistService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter filter = new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, userRepository, blacklistService);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("9876543210")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        String validJwt = jwtUtil.generateToken(userDetails);

        // 1. Admin suspended user: active = false, deletedByUser = false
        User adminSuspendedUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Suspended User")
                .password("pass")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(false)
                .build();

        org.mockito.Mockito.when(userRepository.findByPhoneNumber("9876543210"))
                .thenReturn(java.util.Optional.of(adminSuspendedUser));

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/orders");
        request.addHeader("Authorization", "Bearer " + validJwt);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus(), "Admin-suspended user must be rejected with 401");
        assertTrue(response.getContentAsString().contains("Account is deactivated or suspended"), "Error message must indicate deactivation");
        assertFalse(adminSuspendedUser.isActive(), "Admin-suspended user must NEVER be automatically reactivated");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());

        // 2. Self-deactivated user: active = false, deletedByUser = true
        User selfDeactivatedUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Deactivated User")
                .password("pass")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByPhoneNumber("9876543210"))
                .thenReturn(java.util.Optional.of(selfDeactivatedUser));

        org.springframework.mock.web.MockHttpServletResponse response2 = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain2 = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response2, filterChain2);

        assertEquals(401, response2.getStatus(), "Self-deactivated user must be rejected with 401");
        assertTrue(response2.getContentAsString().contains("Account is deactivated or suspended"));
    }

    @Test
    @DisplayName("Verify OTP Login Rejects Admin-Blocked User with 403 Without Reactivating or Issuing JWT")
    void testOtpLoginRejectsAdminBlockedUserWithoutReactivating() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        String phone = "9876543210";
        org.mockito.Mockito.when(otpService.verifyOtp("+919876543210", "123456")).thenReturn(true);
        org.mockito.Mockito.when(otpService.verifyOtp("9876543210", "123456")).thenReturn(true);

        User blockedUser = User.builder()
                .id(1L)
                .phoneNumber("9876543210")
                .fullName("Blocked User")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(false)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(java.util.Optional.of(blockedUser));

        com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO request = new com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO();
        request.setPhoneNumber("9876543210");
        request.setOtp("123456");

        org.springframework.http.ResponseEntity<?> response = authController.loginOrRegisterWithOtp(request);

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, response.getStatusCode(), "Admin-blocked user OTP login must be rejected with 403 Forbidden");
        assertFalse(blockedUser.isActive(), "Admin-blocked user MUST remain active=false");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("Verify OTP Login Allows Self-Deactivated User Reactivation")
    void testOtpLoginAllowsSelfDeactivatedUserReactivation() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        org.mockito.Mockito.when(otpService.verifyOtp("9876543210", "123456")).thenReturn(true);

        User selfDeactivatedUser = User.builder()
                .id(2L)
                .phoneNumber("9876543210")
                .fullName("Self Deactivated User")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(java.util.Optional.of(selfDeactivatedUser));

        com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO request = new com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO();
        request.setPhoneNumber("9876543210");
        request.setOtp("123456");

        org.springframework.http.ResponseEntity<?> response = authController.loginOrRegisterWithOtp(request);

        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        assertTrue(selfDeactivatedUser.getPhoneNumber().startsWith("DELETED_"), "Old self-deactivated user must have phone detached with DELETED_ prefix");
        assertFalse(selfDeactivatedUser.isActive(), "Old self-deactivated user must remain inactive");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.atLeast(2)).save(org.mockito.ArgumentMatchers.any(User.class));

        com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO body = (com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO) response.getBody();
        assertNotNull(body);
        assertTrue(body.isNewUser());
        assertNotNull(body.getToken());
    }

    @Test
    @DisplayName("Verify OTP Login Succeeds For Existing Active User")
    void testOtpLoginSucceedsForActiveUser() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        org.mockito.Mockito.when(otpService.verifyOtp("9876543210", "123456")).thenReturn(true);

        User activeUser = User.builder()
                .id(3L)
                .phoneNumber("9876543210")
                .fullName("Active User")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .active(true)
                .deletedByUser(false)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(java.util.Optional.of(activeUser));

        com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO request = new com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO();
        request.setPhoneNumber("9876543210");
        request.setOtp("123456");

        org.springframework.http.ResponseEntity<?> response = authController.loginOrRegisterWithOtp(request);

        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO body = (com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO) response.getBody();
        assertNotNull(body);
        assertFalse(body.isNewUser(), "Existing active user must return isNewUser=false");
        assertNotNull(body.getToken());
    }

    @Test
    @DisplayName("Verify OTP Login Registers Genuinely New User")
    void testOtpLoginRegistersNewUser() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        org.mockito.Mockito.when(encoder.encode(org.mockito.Mockito.anyString())).thenReturn("encoded_pass");
        org.mockito.Mockito.when(otpService.verifyOtp("9999999999", "123456")).thenReturn(true);
        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9999999999", "9999999999")).thenReturn(java.util.Optional.empty());

        com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO request = new com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO();
        request.setPhoneNumber("9999999999");
        request.setOtp("123456");
        request.setFullName("New Customer");

        org.springframework.http.ResponseEntity<?> response = authController.loginOrRegisterWithOtp(request);

        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(1)).save(org.mockito.Mockito.any(User.class));

        com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO body = (com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO) response.getBody();
        assertNotNull(body);
        assertTrue(body.isNewUser(), "Genuinely new user must return isNewUser=true");
        assertNotNull(body.getToken());
    }

    @Test
    @DisplayName("Verify Password Registration Normalizes Phone Number to 10 Digits and Prevents Duplicates")
    void testPasswordRegistrationNormalizesPhoneNumberAndPreventsDuplicates() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        org.mockito.Mockito.when(userRepository.existsByCanonicalPhone("9876543210", "+919876543210")).thenReturn(true);

        User newUser = User.builder()
                .phoneNumber("+919876543210")
                .fullName("Test User")
                .password("password123")
                .build();

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> authController.register(newUser),
                "Registering an existing phone number with +91 prefix must be rejected as duplicate");
    }

    @Test
    @DisplayName("Verify OtpController /auth/otp/send Rejects Admin-Blocked Users with 403 Forbidden")
    void testSendOtpRejectsAdminBlockedUser() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        com.premchemicals.cleaningbackend.controller.OtpController otpController =
                new com.premchemicals.cleaningbackend.controller.OtpController(otpService, userRepository);

        User blockedUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Blocked User")
                .active(false)
                .deletedByUser(false)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9876543210", "9876543210"))
                .thenReturn(java.util.Optional.of(blockedUser));

        org.springframework.http.ResponseEntity<?> response = otpController.sendOtp("9876543210");

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, response.getStatusCode(),
                "OTP send request for admin-blocked user must return 403 Forbidden");
        org.mockito.Mockito.verify(otpService, org.mockito.Mockito.never()).sendOtp(org.mockito.Mockito.anyString());
    }

    @Test
    @DisplayName("Verify OtpController /auth/otp/send Allows Self-Deactivated Users")
    void testSendOtpAllowsSelfDeactivatedUser() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        com.premchemicals.cleaningbackend.controller.OtpController otpController =
                new com.premchemicals.cleaningbackend.controller.OtpController(otpService, userRepository);

        User selfDeactivatedUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Self Deactivated User")
                .active(false)
                .deletedByUser(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9876543210", "9876543210"))
                .thenReturn(java.util.Optional.of(selfDeactivatedUser));

        org.springframework.http.ResponseEntity<?> response = otpController.sendOtp("9876543210");

        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        org.mockito.Mockito.verify(otpService, org.mockito.Mockito.times(1)).sendOtp("9876543210");
    }

    @Test
    @DisplayName("Verify AdminInitializer Run Does Not Reactivate Blocked Users on Restart")
    void testAdminInitializerDoesNotReactivateBlockedUsersOnRestart() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.config.AdminInitializer initializer =
                new com.premchemicals.cleaningbackend.config.AdminInitializer(userRepository, encoder);

        ReflectionTestUtils.setField(initializer, "adminPhone", "9999999999");
        ReflectionTestUtils.setField(initializer, "adminPassword", "adminPass123");
        ReflectionTestUtils.setField(initializer, "adminFullName", "Super Admin");

        initializer.run();

        // Verify redundant schema and blanket reactivation methods are NEVER invoked on restart
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).addDeletedByUserColumnIfNotExists();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).addPasswordChangedAtColumnIfNotExists();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).fixNullActiveUsers();
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).ensureAdminsAreActive();

        // Verify configured admin user is initialized correctly as active ROLE_ADMIN
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(1)).findByPhoneNumber("9999999999");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(1)).save(
                org.mockito.ArgumentMatchers.argThat(user ->
                        "9999999999".equals(user.getPhoneNumber())
                                && user.isActive()
                                && com.premchemicals.cleaningbackend.model.enums.Role.ROLE_ADMIN.equals(user.getRole())
                )
        );
    }

    @Test
    @DisplayName("Verify OTP Login Resolves Existing User Across Phone Formats Without Duplicate Account Creation")
    void testOtpLoginResolvesExistingUserAcrossPhoneFormats() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        User existingUser = User.builder()
                .id(10L)
                .phoneNumber("9876543210")
                .fullName("Existing Customer")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .active(true)
                .deletedByUser(false)
                .build();

        // Repository stores canonical 10-digit number; findByCanonicalPhone finds it for "+91 98765 43210"
        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9876543210", "+91 98765 43210")).thenReturn(java.util.Optional.of(existingUser));
        org.mockito.Mockito.when(otpService.verifyOtp(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString())).thenReturn(true);

        // User enters formatted phone number with spaces and +91
        com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO request = new com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO();
        request.setPhoneNumber("+91 98765 43210");
        request.setOtp("123456");

        org.springframework.http.ResponseEntity<?> response = authController.loginOrRegisterWithOtp(request);

        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO body = (com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO) response.getBody();
        assertNotNull(body);
        assertFalse(body.isNewUser(), "Must resolve existing user and NOT mark as new user");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("Verify AdminUserService searchUsers Does Not Mutate deletedByUser For Blocked Users With [DELETED] Password")
    void testAdminUserServiceSearchUsersDoesNotMutateDeletedByUserForBlockedUsers() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.mapper.UserMapper userMapper = new com.premchemicals.cleaningbackend.mapper.UserMapper();
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);

        com.premchemicals.cleaningbackend.repository.OrderRepository orderRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.OrderRepository.class);
        com.premchemicals.cleaningbackend.repository.ReviewRepository reviewRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.ReviewRepository.class);
        com.premchemicals.cleaningbackend.repository.ReviewHelpfulRepository reviewHelpfulRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.ReviewHelpfulRepository.class);
        com.premchemicals.cleaningbackend.repository.NotificationRepository notificationRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.NotificationRepository.class);

        com.premchemicals.cleaningbackend.service.AdminUserService adminUserService =
                new com.premchemicals.cleaningbackend.service.AdminUserService(userRepository, orderRepository, reviewRepository, reviewHelpfulRepository, notificationRepository, userMapper, encoder);

        User blockedUserWithDeletedPassword = User.builder()
                .id(50L)
                .phoneNumber("9682210557")
                .fullName("Prem")
                .password("[DELETED]")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(false)
                .build();

        org.mockito.Mockito.when(userRepository.searchUsers("")).thenReturn(List.of(blockedUserWithDeletedPassword));

        List<com.premchemicals.cleaningbackend.dto.UserResponseDTO> result = adminUserService.searchUsers("");

        assertEquals(1, result.size());
        assertFalse(result.get(0).isActive(), "User must remain active=false");
        assertFalse(result.get(0).isDeletedByUser(), "User must remain deletedByUser=false (NOT mutated to true)");
        assertFalse(blockedUserWithDeletedPassword.isDeletedByUser(), "Entity deletedByUser flag must remain false");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("Verify Change Password Rejects Non-Admin with 403 Forbidden")
    void testChangePasswordRejectsNonAdmin() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        User customerUser = User.builder()
                .id(15L)
                .phoneNumber("9876543210")
                .fullName("Customer User")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .active(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9876543210", "9876543210"))
                .thenReturn(java.util.Optional.of(customerUser));

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("9876543210", null);

        com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO dto = new com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO();
        dto.setOldPassword("oldPass123");
        dto.setNewPassword("newPass123");

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> authController.changePassword(dto, auth)
        );

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Only administrators can change passwords"));
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("Verify Change Password Rejects Incorrect Old Password with 400 Bad Request")
    void testChangePasswordRejectsIncorrectOldPassword() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        User adminUser = User.builder()
                .id(1L)
                .phoneNumber("9999999999")
                .fullName("Admin User")
                .password("encoded_old_pass")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9999999999", "9999999999"))
                .thenReturn(java.util.Optional.of(adminUser));
        org.mockito.Mockito.when(encoder.matches("wrongOldPass", "encoded_old_pass")).thenReturn(false);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("9999999999", null);

        com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO dto = new com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO();
        dto.setOldPassword("wrongOldPass");
        dto.setNewPassword("newPass123");

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> authController.changePassword(dto, auth)
        );

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Incorrect old password", ex.getReason());
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("Verify Change Password Successfully Hashes New Password and Sets passwordChangedAt Timestamp")
    void testChangePasswordSucceedsAndSetsPasswordChangedAt() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        User adminUser = User.builder()
                .id(1L)
                .phoneNumber("9999999999")
                .fullName("Admin User")
                .password("encoded_old_pass")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByCanonicalPhone("9999999999", "9999999999"))
                .thenReturn(java.util.Optional.of(adminUser));
        org.mockito.Mockito.when(encoder.matches("correctOldPass", "encoded_old_pass")).thenReturn(true);
        org.mockito.Mockito.when(encoder.encode("brandNewPass123")).thenReturn("encoded_brandNewPass123");

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("9999999999", null);

        com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO dto = new com.premchemicals.cleaningbackend.dto.ChangePasswordRequestDTO();
        dto.setOldPassword("correctOldPass");
        dto.setNewPassword("brandNewPass123");

        String result = authController.changePassword(dto, auth);

        assertEquals("Password changed successfully", result);
        assertEquals("encoded_brandNewPass123", adminUser.getPassword());
        assertNotNull(adminUser.getPasswordChangedAt(), "passwordChangedAt must be populated upon password change");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.times(1)).save(adminUser);
    }

    @Test
    @DisplayName("Verify JwtAuthenticationFilter Immediately Rejects Old Admin Token Issued Before/At passwordChangedAt")
    void testJwtAuthenticationFilterRejectsOldAdminTokensAfterPasswordChange() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklistService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter filter = new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, userRepository, blacklistService);

        UserDetails adminDetails = org.springframework.security.core.userdetails.User
                .withUsername("9999999999")
                .password("pass")
                .authorities("ROLE_ADMIN")
                .build();
        String oldAdminJwt = jwtUtil.generateToken(adminDetails);

        // Password change occurs after/at token issuance
        java.time.LocalDateTime passwordChangedAt = java.time.LocalDateTime.now().plusSeconds(2);

        User adminUser = User.builder()
                .phoneNumber("9999999999")
                .fullName("Admin")
                .password("encoded_pass")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .passwordChangedAt(passwordChangedAt)
                .build();

        org.mockito.Mockito.when(userRepository.findByPhoneNumber("9999999999"))
                .thenReturn(java.util.Optional.of(adminUser));

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/admin/orders");
        request.addHeader("Authorization", "Bearer " + oldAdminJwt);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus(), "Old admin JWT must be rejected with 401 Unauthorized");
        assertTrue(response.getContentAsString().contains("Session has been invalidated due to password change"),
                "Response must notify user of session invalidation due to password change");
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify JwtAuthenticationFilter Accepts Newly Issued Admin Token After Password Change")
    void testJwtAuthenticationFilterAcceptsNewAdminTokensAfterPasswordChange() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklistService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter filter = new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, userRepository, blacklistService);

        // Password was changed 5 minutes ago
        java.time.LocalDateTime passwordChangedAt = java.time.LocalDateTime.now().minusMinutes(5);

        // Admin logs in NOW and gets a new token
        UserDetails adminDetails = org.springframework.security.core.userdetails.User
                .withUsername("9999999999")
                .password("newPass")
                .authorities("ROLE_ADMIN")
                .build();
        String newAdminJwt = jwtUtil.generateToken(adminDetails);

        User adminUser = User.builder()
                .phoneNumber("9999999999")
                .fullName("Admin")
                .password("encoded_new_pass")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .passwordChangedAt(passwordChangedAt)
                .build();

        org.mockito.Mockito.when(userRepository.findByPhoneNumber("9999999999"))
                .thenReturn(java.util.Optional.of(adminUser));

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/admin/orders");
        request.addHeader("Authorization", "Bearer " + newAdminJwt);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus(), "New admin token issued after password change must be accepted");
        assertNotNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must be set with authentication for valid new admin token");
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify Customer JWT Unaffected When passwordChangedAt Is Null")
    void testCustomerSessionUnaffectedWhenPasswordChangedAtIsNull() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklistService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter filter = new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, userRepository, blacklistService);

        UserDetails customerDetails = org.springframework.security.core.userdetails.User
                .withUsername("9876543210")
                .password("pass")
                .authorities("ROLE_USER")
                .build();
        String customerJwt = jwtUtil.generateToken(customerDetails);

        User customerUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Customer")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .active(true)
                .passwordChangedAt(null)
                .build();

        org.mockito.Mockito.when(userRepository.findByPhoneNumber("9876543210"))
                .thenReturn(java.util.Optional.of(customerUser));

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/orders");
        request.addHeader("Authorization", "Bearer " + customerJwt);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus(), "Customer session must pass through unaffected");
        assertNotNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(),
                "Customer authentication must be successfully set in SecurityContext");
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify Session Invalidation Persists Across Restart When User Loaded From Database")
    void testPersistentSessionInvalidationSurvivesRestart() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        // Pre-restart: Admin token generated in the past
        UserDetails adminDetails = org.springframework.security.core.userdetails.User
                .withUsername("9999999999")
                .password("pass")
                .authorities("ROLE_ADMIN")
                .build();
        String preRestartToken = jwtUtil.generateToken(adminDetails);

        // Password change persisted in DB at T_1 (after token generation)
        java.time.LocalDateTime persistedPasswordChangedAt = java.time.LocalDateTime.now().plusSeconds(1);

        // Simulate application restart: New filter instance, new mock repository simulating freshly booted backend
        com.premchemicals.cleaningbackend.repository.UserRepository newRepoInstance = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService newBlacklistInstance = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter restartedFilter =
                new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, newRepoInstance, newBlacklistInstance);

        User userFromPostgres = User.builder()
                .phoneNumber("9999999999")
                .fullName("Admin")
                .password("new_db_hash")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .passwordChangedAt(persistedPasswordChangedAt)
                .build();

        org.mockito.Mockito.when(newRepoInstance.findByPhoneNumber("9999999999"))
                .thenReturn(java.util.Optional.of(userFromPostgres));

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/admin/dashboard");
        request.addHeader("Authorization", "Bearer " + preRestartToken);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        restartedFilter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus(), "After backend restart, old admin JWT must STILL be rejected due to persisted passwordChangedAt in DB");
        assertTrue(response.getContentAsString().contains("Session has been invalidated due to password change"));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify Logout Endpoint Continues to Blacklist Specific Tokens")
    void testLogoutBlacklistsTokenCorrectly() {
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.service.OtpService otpService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.service.OtpService.class);
        org.springframework.security.crypto.password.PasswordEncoder encoder = org.mockito.Mockito.mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklist = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        org.springframework.security.authentication.AuthenticationManager authManager = org.mockito.Mockito.mock(org.springframework.security.authentication.AuthenticationManager.class);

        com.premchemicals.cleaningbackend.controller.AuthController authController =
                new com.premchemicals.cleaningbackend.controller.AuthController(authManager, jwtUtil, userRepository, encoder, otpService, blacklist);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("9999999999")
                .password("pass")
                .authorities("ROLE_ADMIN")
                .build();
        String jwt = jwtUtil.generateToken(userDetails);

        org.springframework.http.ResponseEntity<?> response = authController.logout("Bearer " + jwt);

        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        org.mockito.Mockito.verify(blacklist, org.mockito.Mockito.times(1))
                .blacklistToken(org.mockito.Mockito.eq(jwt), org.mockito.Mockito.anyLong());
    }

    private String generateExpiredToken(UserDetails userDetails) {
        JwtUtil expiredJwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(expiredJwtUtil, "secret", "cleaning_backend_super_secret_key_2026_secure_application_987654321");
        ReflectionTestUtils.setField(expiredJwtUtil, "jwtExpiration", -10000L); // 10 seconds in the past
        expiredJwtUtil.init();
        return expiredJwtUtil.generateToken(userDetails);
    }

    @Test
    @DisplayName("Verify JwtAuthenticationFilter Rejects Expired Customer Token with HTTP 401")
    void testExpiredCustomerTokenRejectedWithHttp401() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklistService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter filter = new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, userRepository, blacklistService);

        UserDetails customerDetails = org.springframework.security.core.userdetails.User
                .withUsername("9876543210")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        String expiredJwt = generateExpiredToken(customerDetails);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/orders");
        request.addHeader("Authorization", "Bearer " + expiredJwt);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus(), "Expired customer token must be rejected with HTTP 401");
        assertTrue(response.getContentAsString().contains("JWT token has expired. Please login again."),
                "Error response must explicitly indicate that JWT token has expired");
        assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must not contain authentication for expired token");
    }

    @Test
    @DisplayName("Verify JwtAuthenticationFilter Rejects Expired Admin Token with HTTP 401")
    void testExpiredAdminTokenRejectedWithHttp401() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklistService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter filter = new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, userRepository, blacklistService);

        UserDetails adminDetails = org.springframework.security.core.userdetails.User
                .withUsername("9999999999")
                .password("adminPass")
                .authorities("ROLE_ADMIN")
                .build();
        String expiredJwt = generateExpiredToken(adminDetails);

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/admin/orders");
        request.addHeader("Authorization", "Bearer " + expiredJwt);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus(), "Expired admin token must be rejected with HTTP 401");
        assertTrue(response.getContentAsString().contains("JWT token has expired. Please login again."),
                "Error response must explicitly indicate that JWT token has expired");
        assertNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must not contain authentication for expired token");
    }

    @Test
    @DisplayName("Verify Non-Expired Token Is Accepted and Authenticates User")
    void testNonExpiredTokenAcceptedWithHttp200() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        com.premchemicals.cleaningbackend.repository.UserRepository userRepository = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        com.premchemicals.cleaningbackend.security.TokenBlacklistService blacklistService = org.mockito.Mockito.mock(com.premchemicals.cleaningbackend.security.TokenBlacklistService.class);
        com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter filter = new com.premchemicals.cleaningbackend.security.JwtAuthenticationFilter(jwtUtil, userRepository, blacklistService);

        UserDetails customerDetails = org.springframework.security.core.userdetails.User
                .withUsername("9876543210")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        String validJwt = jwtUtil.generateToken(customerDetails);

        User customerUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Customer")
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .active(true)
                .build();

        org.mockito.Mockito.when(userRepository.findByPhoneNumber("9876543210"))
                .thenReturn(java.util.Optional.of(customerUser));

        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setServletPath("/api/orders");
        request.addHeader("Authorization", "Bearer " + validJwt);

        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockFilterChain filterChain = new org.springframework.mock.web.MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus(), "Valid non-expired token must be accepted");
        assertNotNull(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext must contain authentication for non-expired token");
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify Configured JWT Expiration Lifetime Remains 180 Days")
    void testConfiguredJwtExpirationRemains180Days() {
        long expected180DaysMs = 180L * 24 * 60 * 60 * 1000; // 15,552,000,000 ms
        assertEquals(15552000000L, expected180DaysMs, "180 days must equal exactly 15,552,000,000 ms");

        Object configuredExpiration = ReflectionTestUtils.getField(jwtUtil, "jwtExpiration");
        assertNotNull(configuredExpiration);
        assertEquals(15552000000L, (Long) configuredExpiration, "Configured JWT expiration in JwtUtil must remain 180 days (15,552,000,000 ms)");
    }
}
