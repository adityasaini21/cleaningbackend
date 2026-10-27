package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.controller.AuthController;
import com.premchemicals.cleaningbackend.dto.OrderResponseDTO;
import com.premchemicals.cleaningbackend.dto.OrderResponseDTO.OrderItemResponse;
import com.premchemicals.cleaningbackend.dto.OtpLoginRequestDTO;
import com.premchemicals.cleaningbackend.dto.OtpLoginResponseDTO;
import com.premchemicals.cleaningbackend.model.Order;
import com.premchemicals.cleaningbackend.model.OrderItem;
import com.premchemicals.cleaningbackend.model.Product;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentMethod;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.NotificationRepository;
import com.premchemicals.cleaningbackend.repository.OrderRepository;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.security.JwtUtil;
import com.premchemicals.cleaningbackend.security.TokenBlacklistService;
import com.premchemicals.cleaningbackend.service.DeliveryPincodeService;
import com.premchemicals.cleaningbackend.service.GoogleMapsService;
import com.premchemicals.cleaningbackend.service.NotificationService;
import com.premchemicals.cleaningbackend.service.OrderService;
import com.premchemicals.cleaningbackend.service.OtpService;
import com.premchemicals.cleaningbackend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AppBugFixesTest {

    // --- OrderService & dependencies ---
    private OrderRepository orderRepository;
    private UserRepository userRepository;
    private OrderService orderService;

    // --- AuthController & dependencies ---
    private AuthenticationManager authenticationManager;
    private JwtUtil jwtUtil;
    private PasswordEncoder passwordEncoder;
    private OtpService otpService;
    private TokenBlacklistService tokenBlacklistService;
    private NotificationRepository notificationRepository;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        userRepository = mock(UserRepository.class);
        var orderItemRepository = mock(com.premchemicals.cleaningbackend.repository.OrderItemRepository.class);
        var paymentTransactionRepository = mock(com.premchemicals.cleaningbackend.repository.PaymentTransactionRepository.class);
        var productRepository = mock(com.premchemicals.cleaningbackend.repository.ProductRepository.class);
        var productService = mock(ProductService.class);
        var deliveryPincodeRepository = mock(com.premchemicals.cleaningbackend.repository.DeliveryPincodeRepository.class);
        var notificationService = mock(NotificationService.class);
        var googleMapsService = mock(GoogleMapsService.class);
        var deliveryPincodeService = mock(DeliveryPincodeService.class);

        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                paymentTransactionRepository,
                productRepository,
                userRepository,
                productService,
                deliveryPincodeRepository,
                notificationService,
                googleMapsService,
                deliveryPincodeService
        );

        authenticationManager = mock(AuthenticationManager.class);
        jwtUtil = mock(JwtUtil.class);
        passwordEncoder = mock(PasswordEncoder.class);
        otpService = mock(OtpService.class);
        tokenBlacklistService = mock(TokenBlacklistService.class);
        notificationRepository = mock(NotificationRepository.class);

        authController = new AuthController(
                authenticationManager,
                jwtUtil,
                userRepository,
                passwordEncoder,
                otpService,
                tokenBlacklistService
        );
        ReflectionTestUtils.setField(authController, "notificationRepository", notificationRepository);
    }

    private Authentication mockSecurityUser(String phoneNumber, Role role) {
        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                phoneNumber,
                null,
                Collections.singletonList(new SimpleGrantedAuthority(role.name()))
        );
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        return authentication;
    }

    // =========================================================================
    // BUG 1: REORDER IMAGE URL IN ORDER ITEM DTO
    // =========================================================================

    @Test
    @DisplayName("Bug 1 Fix: OrderResponseDTO Item includes imageUrl correctly")
    void testOrderResponseDTO_IncludesImageUrl() {
        OrderItemResponse itemResponse = new OrderItemResponse();
        itemResponse.setProductName("NuKlean Floor Cleaner");
        itemResponse.setImageUrl("https://res.cloudinary.com/demo/image/upload/sample.jpg");
        itemResponse.setPrice(199.0);
        itemResponse.setQuantity(2);

        assertEquals("https://res.cloudinary.com/demo/image/upload/sample.jpg", itemResponse.getImageUrl());
        assertEquals("NuKlean Floor Cleaner", itemResponse.getProductName());
    }

    @Test
    @DisplayName("Bug 1 Fix: OrderService mapToResponse preserves product imageUrl")
    void testOrderService_MapToResponse_IncludesProductImageUrl() {
        Product product = Product.builder()
                .name("Glass Cleaner")
                .imageUrl("https://res.cloudinary.com/demo/image/upload/glass.png")
                .build();
        product.setId(10L);

        OrderItem orderItem = new OrderItem();
        orderItem.setId(101L);
        orderItem.setProduct(product);
        orderItem.setQuantity(3);
        orderItem.setPrice(150.0);

        User customer = User.builder()
                .phoneNumber("9876543210")
                .role(Role.ROLE_USER)
                .build();
        customer.setId(1L);

        Order order = new Order();
        order.setId(500L);
        order.setUser(customer);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.COD);
        order.setOrderDate(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setOrderItems(List.of(orderItem));

        OrderResponseDTO response = ReflectionTestUtils.invokeMethod(orderService, "mapToResponse", order);
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("https://res.cloudinary.com/demo/image/upload/glass.png", response.getItems().get(0).getImageUrl());
    }

    // =========================================================================
    // BUG 2: ACCOUNT DELETION GIVES A GENUINELY FRESH START
    // =========================================================================

    @Test
    @DisplayName("Bug 2 Fix: deleteAccount anonymizes user, detaches phone with DELETED_ prefix, blacklists token, and clears notifications")
    void testDeleteAccount_AnonymizesAndFreesPhoneNumber() {
        Authentication auth = mockSecurityUser("9876543210", Role.ROLE_USER);

        User user = User.builder()
                .phoneNumber("9876543210")
                .fullName("John Doe")
                .email("john@example.com")
                .address("123 Street")
                .role(Role.ROLE_USER)
                .active(true)
                .deletedByUser(false)
                .build();
        user.setId(42L);

        when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(Optional.of(user));
        when(jwtUtil.extractExpiration("test-token-123")).thenReturn(new Date(System.currentTimeMillis() + 3600000));

        String result = authController.deleteAccount("Bearer test-token-123", auth);
        assertNotNull(result);

        // Verify token blacklisting
        verify(tokenBlacklistService).blacklistToken(eq("test-token-123"), anyLong());

        // Verify notifications cleared
        verify(notificationRepository).deleteByUser(user);

        // Verify user entity state
        assertTrue(user.isDeletedByUser());
        assertFalse(user.isActive());
        assertEquals("Deleted User", user.getFullName());
        assertNull(user.getEmail());
        assertNull(user.getAddress());
        assertTrue(user.getPhoneNumber().startsWith("DELETED_42_"), "Phone number must be freed with DELETED_ prefix");
        assertTrue(user.getPhoneNumber().length() > 10, "Pseudonymized phone placeholder must be longer than 10 chars");
        assertTrue(user.getPhoneNumber().length() <= 50, "Pseudonymized phone placeholder must fit within VARCHAR(50)");
        assertTrue(user.getPhoneNumber().matches("^([6-9]\\d{9}|DELETED_.*)$"), "Phone placeholder must pass User entity bean validation pattern");
        assertNotNull(user.getPasswordChangedAt(), "passwordChangedAt must be updated to invalidate active sessions");

        // Verify saved to DB
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Migration Fix: DatabaseMigrationInitializer executes all additive schema migrations")
    void testDatabaseMigrationInitializer_ExecutesAdditiveMigrations() {
        com.premchemicals.cleaningbackend.config.DatabaseMigrationInitializer initializer =
                new com.premchemicals.cleaningbackend.config.DatabaseMigrationInitializer(userRepository);

        initializer.run();

        verify(userRepository, times(1)).widenPhoneNumberColumn();
        verify(userRepository, times(1)).addDeletedByUserColumnIfNotExists();
        verify(userRepository, times(1)).addPasswordChangedAtColumnIfNotExists();
    }

    @Test
    @DisplayName("Migration Fix: DatabaseMigrationInitializer handles database exceptions gracefully")
    void testDatabaseMigrationInitializer_HandlesExceptionsGracefully() {
        com.premchemicals.cleaningbackend.repository.UserRepository failingRepo =
                mock(com.premchemicals.cleaningbackend.repository.UserRepository.class);
        doThrow(new RuntimeException("Table does not exist yet")).when(failingRepo).widenPhoneNumberColumn();

        com.premchemicals.cleaningbackend.config.DatabaseMigrationInitializer initializer =
                new com.premchemicals.cleaningbackend.config.DatabaseMigrationInitializer(failingRepo);

        assertDoesNotThrow(() -> initializer.run(), "Database migration runner must not crash the application context on exception");
    }

    @Test
    @DisplayName("Bug 2 Fix: Re-registering/login after deletion creates a fresh User account (isNewUser=true)")
    void testVerifyOtpLogin_DeletedUserReRegisteringGetsBrandNewAccount() {
        // User with deletedByUser=true exists under old phone
        User oldDeletedUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Old User")
                .active(false)
                .deletedByUser(true)
                .role(Role.ROLE_USER)
                .build();
        oldDeletedUser.setId(77L);

        when(otpService.verifyOtp("9876543210", "123456")).thenReturn(true);
        when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(Optional.of(oldDeletedUser));
        when(jwtUtil.generateToken(any())).thenReturn("new-jwt-token-abc");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        OtpLoginRequestDTO request = new OtpLoginRequestDTO();
        request.setPhoneNumber("9876543210");
        request.setOtp("123456");

        ResponseEntity<?> response = authController.loginOrRegisterWithOtp(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        OtpLoginResponseDTO body = (OtpLoginResponseDTO) response.getBody();
        assertNotNull(body);
        assertTrue(body.isNewUser(), "Returning user with previously deleted account must be treated as new user");
        assertEquals("new-jwt-token-abc", body.getToken());

        // Verify that old user's phone was detached and a new User entity was saved
        verify(userRepository, atLeast(2)).save(userCaptor.capture());
        List<User> savedUsers = userCaptor.getAllValues();
        User detachedUser = savedUsers.get(0);
        assertTrue(detachedUser.getPhoneNumber().startsWith("DELETED_77_"));

        User newlyCreatedUser = savedUsers.get(1);
        assertEquals("9876543210", newlyCreatedUser.getPhoneNumber());
        assertTrue(newlyCreatedUser.isActive());
        assertFalse(newlyCreatedUser.isDeletedByUser());
    }

    @Test
    @DisplayName("Bug 2 Fix: Admin-blocked user cannot login with OTP and gets 403 Forbidden")
    void testVerifyOtpLogin_AdminBlockedUserRejectedWith403() {
        User blockedUser = User.builder()
                .phoneNumber("9876543210")
                .fullName("Blocked Bad Actor")
                .active(false)
                .deletedByUser(false) // Admin blocked, NOT self-deleted
                .role(Role.ROLE_USER)
                .build();
        blockedUser.setId(99L);

        when(otpService.verifyOtp("9876543210", "123456")).thenReturn(true);
        when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(Optional.of(blockedUser));

        OtpLoginRequestDTO request = new OtpLoginRequestDTO();
        request.setPhoneNumber("9876543210");
        request.setOtp("123456");

        ResponseEntity<?> response = authController.loginOrRegisterWithOtp(request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("error").contains("suspended by administration"));
    }

    @Test
    @DisplayName("Bug 2 Fix: checkPhoneExists returns false for deleted user and true for active user")
    void testCheckPhoneExists_IgnoresDeletedUsers() {
        User deletedUser = User.builder()
                .phoneNumber("9876543210")
                .active(false)
                .deletedByUser(true)
                .build();
        when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(Optional.of(deletedUser));

        ResponseEntity<?> respDeleted = authController.checkPhoneExists("9876543210");
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyDeleted = (Map<String, Object>) respDeleted.getBody();
        assertFalse((Boolean) bodyDeleted.get("exists"));

        User activeUser = User.builder()
                .phoneNumber("9876543210")
                .active(true)
                .deletedByUser(false)
                .build();
        when(userRepository.findByCanonicalPhone("9876543210", "9876543210")).thenReturn(Optional.of(activeUser));

        ResponseEntity<?> respActive = authController.checkPhoneExists("9876543210");
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyActive = (Map<String, Object>) respActive.getBody();
        assertTrue((Boolean) bodyActive.get("exists"));
    }

    // =========================================================================
    // BUG 3: 60-SECOND CANCELLATION WINDOW & CONCURRENCY
    // =========================================================================

    @Test
    @DisplayName("Bug 3 Fix: Order cancellation within 60 seconds succeeds")
    void testCancelOrder_Within60Seconds_Succeeds() {
        mockSecurityUser("9876543210", Role.ROLE_USER);

        User user = User.builder().phoneNumber("9876543210").role(Role.ROLE_USER).build();
        user.setId(1L);
        when(userRepository.findByPhoneNumber("9876543210")).thenReturn(Optional.of(user));

        Order order = new Order();
        order.setId(10L);
        order.setUser(user);
        order.setOrderStatus(OrderStatus.CREATED);
        order.setPaymentMethod(PaymentMethod.COD);
        // Created 20 seconds ago
        order.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusSeconds(20));
        order.setOrderDate(order.getCreatedAt());

        when(orderRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(order));

        OrderResponseDTO cancelledDto = orderService.cancelOrder(10L);

        assertEquals(OrderStatus.CANCELLED, order.getOrderStatus());
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Bug 3 Fix: Order cancellation after 60 seconds throws 400 Bad Request")
    void testCancelOrder_After60Seconds_ThrowsBadRequest() {
        mockSecurityUser("9876543210", Role.ROLE_USER);

        User user = User.builder().phoneNumber("9876543210").role(Role.ROLE_USER).build();
        user.setId(1L);
        when(userRepository.findByPhoneNumber("9876543210")).thenReturn(Optional.of(user));

        Order order = new Order();
        order.setId(11L);
        order.setUser(user);
        order.setOrderStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.COD);
        // Created 65 seconds ago
        order.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusSeconds(65));
        order.setOrderDate(order.getCreatedAt());

        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orderService.cancelOrder(11L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Cancellation window expired"));
        verify(orderRepository, never()).save(order);
    }

    @Test
    @DisplayName("Bug 3 Fix: Order cancellation rejects ineligible statuses (OUT_FOR_DELIVERY, DELIVERED, CANCELLED)")
    void testCancelOrder_IneligibleStatuses_ThrowsBadRequest() {
        mockSecurityUser("9876543210", Role.ROLE_USER);

        User user = User.builder().phoneNumber("9876543210").role(Role.ROLE_USER).build();
        user.setId(1L);
        when(userRepository.findByPhoneNumber("9876543210")).thenReturn(Optional.of(user));

        // Test OUT_FOR_DELIVERY
        Order orderOut = new Order();
        orderOut.setId(12L);
        orderOut.setUser(user);
        orderOut.setOrderStatus(OrderStatus.OUT_FOR_DELIVERY);
        orderOut.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusSeconds(10));
        when(orderRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(orderOut));

        ResponseStatusException exOut = assertThrows(ResponseStatusException.class, () ->
                orderService.cancelOrder(12L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exOut.getStatusCode());
        assertTrue(exOut.getReason().contains("Orders out for delivery cannot be cancelled"));

        // Test DELIVERED
        Order orderDelivered = new Order();
        orderDelivered.setId(13L);
        orderDelivered.setUser(user);
        orderDelivered.setOrderStatus(OrderStatus.DELIVERED);
        orderDelivered.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusSeconds(10));
        when(orderRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(orderDelivered));

        ResponseStatusException exDelivered = assertThrows(ResponseStatusException.class, () ->
                orderService.cancelOrder(13L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exDelivered.getStatusCode());
        assertTrue(exDelivered.getReason().contains("Delivered order cannot be cancelled"));

        // Test already CANCELLED
        Order orderCancelled = new Order();
        orderCancelled.setId(14L);
        orderCancelled.setUser(user);
        orderCancelled.setOrderStatus(OrderStatus.CANCELLED);
        orderCancelled.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusSeconds(10));
        when(orderRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(orderCancelled));

        ResponseStatusException exCancelled = assertThrows(ResponseStatusException.class, () ->
                orderService.cancelOrder(14L)
        );
        assertEquals(HttpStatus.BAD_REQUEST, exCancelled.getStatusCode());
        assertTrue(exCancelled.getReason().contains("Order already cancelled"));
    }

    @Test
    @DisplayName("Bug 3 Fix: Order cancellation rejects non-owner non-admin with 403 Forbidden")
    void testCancelOrder_NonOwner_ThrowsForbidden() {
        mockSecurityUser("9876543210", Role.ROLE_USER);

        User attacker = User.builder().phoneNumber("9876543210").role(Role.ROLE_USER).build();
        attacker.setId(999L);
        when(userRepository.findByPhoneNumber("9876543210")).thenReturn(Optional.of(attacker));

        User victim = User.builder().phoneNumber("9123456789").role(Role.ROLE_USER).build();
        victim.setId(1L);

        Order victimOrder = new Order();
        victimOrder.setId(15L);
        victimOrder.setUser(victim);
        victimOrder.setOrderStatus(OrderStatus.CREATED);
        victimOrder.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusSeconds(10));
        when(orderRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(victimOrder));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                orderService.cancelOrder(15L)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Access Denied: You do not own this order"));
    }
}
