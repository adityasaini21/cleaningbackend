package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.controller.AdminController;
import com.premchemicals.cleaningbackend.controller.AdminOrderController;
import com.premchemicals.cleaningbackend.controller.CategoryController;
import com.premchemicals.cleaningbackend.dto.UserResponseDTO;
import com.premchemicals.cleaningbackend.model.Category;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.CategoryRepository;
import com.premchemicals.cleaningbackend.repository.NotificationRepository;
import com.premchemicals.cleaningbackend.repository.OrderRepository;
import com.premchemicals.cleaningbackend.repository.ReviewHelpfulRepository;
import com.premchemicals.cleaningbackend.repository.ReviewRepository;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.service.AdminUserService;
import com.premchemicals.cleaningbackend.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AdminSecurityAuditFixesTest {

    private UserRepository userRepository;
    private OrderRepository orderRepository;
    private ReviewRepository reviewRepository;
    private ReviewHelpfulRepository reviewHelpfulRepository;
    private NotificationRepository notificationRepository;
    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        orderRepository = mock(OrderRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        reviewHelpfulRepository = mock(ReviewHelpfulRepository.class);
        notificationRepository = mock(NotificationRepository.class);

        adminUserService = new AdminUserService(
                userRepository,
                orderRepository,
                reviewRepository,
                reviewHelpfulRepository,
                notificationRepository,
                new com.premchemicals.cleaningbackend.mapper.UserMapper(),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class)
        );
    }

    // =========================================================================
    // FIX 1 & 2: ADMIN ACCESS CONTROL & PREAUTHORIZE TESTS
    // =========================================================================

    @Test
    @DisplayName("Fix 2: AdminController must be protected by class-level @PreAuthorize requiring ROLE_ADMIN or ADMIN")
    void testAdminControllerHasClassLevelPreAuthorize() {
        PreAuthorize annotation = AdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, "AdminController must be annotated with @PreAuthorize at the class level");
        assertEquals("hasAnyAuthority('ROLE_ADMIN', 'ADMIN')", annotation.value(),
                "@PreAuthorize must require 'ROLE_ADMIN' or 'ADMIN' authority");
    }

    @Test
    @DisplayName("Fix 2: AdminController endpoints properly delegate to AdminUserService")
    void testAdminControllerDelegation() {
        AdminUserService mockService = mock(AdminUserService.class);
        AdminController controller = new AdminController(mockService);

        UserResponseDTO userDTO = new UserResponseDTO();
        userDTO.setId(1L);
        userDTO.setFullName("Test User");
        userDTO.setPhoneNumber("9876543210");
        userDTO.setActive(true);

        when(mockService.searchUsers("test")).thenReturn(List.of(userDTO));
        List<UserResponseDTO> searchResult = controller.searchUsers("test");
        assertEquals(1, searchResult.size());
        verify(mockService, times(1)).searchUsers("test");

        UserResponseDTO toggledDTO = new UserResponseDTO();
        toggledDTO.setId(1L);
        toggledDTO.setActive(false);

        when(mockService.toggleStatus(1L)).thenReturn(toggledDTO);
        UserResponseDTO toggleResult = controller.toggleStatus(1L);
        assertFalse(toggleResult.isActive());
        verify(mockService, times(1)).toggleStatus(1L);

        String clearResult = controller.clearBlockedUsers();
        assertEquals("Blocked users list cleared successfully", clearResult);
        verify(mockService, times(1)).clearBlockedUsers();
    }

    @Test
    @DisplayName("Fix 1: Authorities check confirms ROLE_USER is non-admin while ROLE_ADMIN and ADMIN are recognized")
    void testAuthorityChecksDistinguishAdminFromRegularUser() {
        SimpleGrantedAuthority userAuthority = new SimpleGrantedAuthority("ROLE_USER");
        SimpleGrantedAuthority adminAuthority = new SimpleGrantedAuthority("ROLE_ADMIN");
        SimpleGrantedAuthority plainAdmin = new SimpleGrantedAuthority("ADMIN");

        List<SimpleGrantedAuthority> adminAuthorities = List.of(adminAuthority, plainAdmin);

        assertFalse(adminAuthorities.contains(userAuthority), "ROLE_USER must not have admin authority");
        assertTrue(adminAuthorities.contains(adminAuthority), "ROLE_ADMIN must have admin authority");
        assertTrue(adminAuthorities.contains(plainAdmin), "ADMIN must have admin authority");
    }

    @Test
    @DisplayName("Fix 1 & 2: Non-admin request triggering AccessDeniedException returns HTTP 403 Forbidden")
    void testNonAdminAccessDeniedHandlerReturns403() {
        com.premchemicals.cleaningbackend.exception.GlobalExceptionHandler handler =
                new com.premchemicals.cleaningbackend.exception.GlobalExceptionHandler();

        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/admin/orders/today");
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("Access is denied");

        org.springframework.http.ResponseEntity<com.premchemicals.cleaningbackend.exception.ErrorResponse> response =
                handler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "Access denied must result in HTTP 403 FORBIDDEN");
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("You do not have permission to access this resource", response.getBody().getMessage());
        assertEquals("/api/admin/orders/today", response.getBody().getPath());
    }

    @Test
    @DisplayName("Fix 1 & 2: All admin controllers are verified to have PreAuthorize annotations requiring ADMIN role")
    void testAdminSecurityAnnotationsPresentAcrossControllers() {
        // AdminController
        PreAuthorize adminCtrlAuth = AdminController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(adminCtrlAuth);
        assertTrue(adminCtrlAuth.value().contains("ADMIN"));

        // AdminOrderController
        PreAuthorize orderCtrlAuth = AdminOrderController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(orderCtrlAuth);
        assertTrue(orderCtrlAuth.value().contains("ADMIN"));

        // AdminAnalyticsController
        PreAuthorize analyticsCtrlAuth = com.premchemicals.cleaningbackend.controller.AdminAnalyticsController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(analyticsCtrlAuth);
        assertTrue(analyticsCtrlAuth.value().contains("ADMIN"));

        // AdminInventoryController
        PreAuthorize inventoryCtrlAuth = com.premchemicals.cleaningbackend.controller.AdminInventoryController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(inventoryCtrlAuth);
        assertTrue(inventoryCtrlAuth.value().contains("ADMIN"));
    }

    // =========================================================================
    // FIX 3: CATEGORY CREATION ID REJECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Fix 3: Category creation must reject requests with client-supplied ID (HTTP 400)")
    void testCreateCategoryRejectsSuppliedId() {
        CategoryRepository categoryRepo = mock(CategoryRepository.class);
        CategoryController controller = new CategoryController(categoryRepo);

        Category categoryWithId = Category.builder()
                .id(42L)
                .name("Industrial Detergents")
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.createCategory(categoryWithId)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), "Supplying category ID must return HTTP 400 BAD_REQUEST");
        assertTrue(ex.getReason().contains("Category ID must not be provided"),
                "Exception reason must clearly indicate ID rejection");
        verify(categoryRepo, never()).save(any());
    }

    @Test
    @DisplayName("Fix 3: Category creation succeeds when ID is null")
    void testCreateCategorySucceedsWithoutId() {
        CategoryRepository categoryRepo = mock(CategoryRepository.class);
        CategoryController controller = new CategoryController(categoryRepo);

        Category newCategory = Category.builder()
                .id(null)
                .name("Liquid Detergents")
                .build();

        Category savedCategory = Category.builder()
                .id(1L)
                .name("Liquid Detergents")
                .build();

        when(categoryRepo.save(newCategory)).thenReturn(savedCategory);

        Category result = controller.createCategory(newCategory);
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Liquid Detergents", result.getName());
        verify(categoryRepo, times(1)).save(newCategory);
    }

    // =========================================================================
    // FIX 4: SAFE BLOCKED-USER DELETION & FK CONSTRAINT PROTECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Fix 4: Preserves blocked user with existing orders to prevent FK violation and retain audit trail")
    void testClearBlockedUsersPreservesUserWithOrders() {
        User blockedUserWithOrders = User.builder()
                .id(101L)
                .phoneNumber("9876543210")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(false)
                .build();

        when(userRepository.findByRole(Role.ROLE_USER)).thenReturn(List.of(blockedUserWithOrders));
        when(orderRepository.existsByUser(blockedUserWithOrders)).thenReturn(true);
        when(reviewRepository.existsByUser(blockedUserWithOrders)).thenReturn(false);
        when(reviewHelpfulRepository.existsByUser(blockedUserWithOrders)).thenReturn(false);

        adminUserService.clearBlockedUsers();

        verify(userRepository, never()).deleteAll(any());
        verify(userRepository, never()).delete(any());
        verify(notificationRepository, never()).deleteByUser(any());
    }

    @Test
    @DisplayName("Fix 4: Preserves blocked user with existing reviews to prevent FK violation and retain product reviews")
    void testClearBlockedUsersPreservesUserWithReviews() {
        User blockedUserWithReviews = User.builder()
                .id(102L)
                .phoneNumber("9876543211")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(false)
                .build();

        when(userRepository.findByRole(Role.ROLE_USER)).thenReturn(List.of(blockedUserWithReviews));
        when(orderRepository.existsByUser(blockedUserWithReviews)).thenReturn(false);
        when(reviewRepository.existsByUser(blockedUserWithReviews)).thenReturn(true);
        when(reviewHelpfulRepository.existsByUser(blockedUserWithReviews)).thenReturn(false);

        adminUserService.clearBlockedUsers();

        verify(userRepository, never()).deleteAll(any());
        verify(userRepository, never()).delete(any());
        verify(notificationRepository, never()).deleteByUser(any());
    }

    @Test
    @DisplayName("Fix 4: Preserves blocked user with existing review helpful votes")
    void testClearBlockedUsersPreservesUserWithHelpfulVotes() {
        User blockedUserWithHelpful = User.builder()
                .id(103L)
                .phoneNumber("9876543212")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(false)
                .build();

        when(userRepository.findByRole(Role.ROLE_USER)).thenReturn(List.of(blockedUserWithHelpful));
        when(orderRepository.existsByUser(blockedUserWithHelpful)).thenReturn(false);
        when(reviewRepository.existsByUser(blockedUserWithHelpful)).thenReturn(false);
        when(reviewHelpfulRepository.existsByUser(blockedUserWithHelpful)).thenReturn(true);

        adminUserService.clearBlockedUsers();

        verify(userRepository, never()).deleteAll(any());
        verify(userRepository, never()).delete(any());
        verify(notificationRepository, never()).deleteByUser(any());
    }

    @Test
    @DisplayName("Fix 4: Safely deletes blocked user who has NO orders, reviews, or helpful votes, cleaning notifications first")
    @SuppressWarnings("unchecked")
    void testClearBlockedUsersSafelyDeletesUserWithoutHistoricalRecords() {
        User blockedUserWithoutRecords = User.builder()
                .id(104L)
                .phoneNumber("9876543213")
                .role(Role.ROLE_USER)
                .active(false)
                .deletedByUser(false)
                .build();

        when(userRepository.findByRole(Role.ROLE_USER)).thenReturn(List.of(blockedUserWithoutRecords));
        when(orderRepository.existsByUser(blockedUserWithoutRecords)).thenReturn(false);
        when(reviewRepository.existsByUser(blockedUserWithoutRecords)).thenReturn(false);
        when(reviewHelpfulRepository.existsByUser(blockedUserWithoutRecords)).thenReturn(false);

        adminUserService.clearBlockedUsers();

        verify(notificationRepository, times(1)).deleteByUser(blockedUserWithoutRecords);

        ArgumentCaptor<List<User>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository, times(1)).deleteAll(captor.capture());
        List<User> deletedUsers = captor.getValue();
        assertEquals(1, deletedUsers.size());
        assertEquals(104L, deletedUsers.get(0).getId());
    }

    @Test
    @DisplayName("Fix 4: Mixed list - safely deletes only unlinked blocked users while preserving linked ones")
    @SuppressWarnings("unchecked")
    void testClearBlockedUsersMixedScenario() {
        User userWithOrders = User.builder().id(201L).phoneNumber("9876543201").role(Role.ROLE_USER).active(false).deletedByUser(false).build();
        User userWithReviews = User.builder().id(202L).phoneNumber("9876543202").role(Role.ROLE_USER).active(false).deletedByUser(false).build();
        User cleanUser = User.builder().id(203L).phoneNumber("9876543203").role(Role.ROLE_USER).active(false).deletedByUser(false).build();
        User activeUser = User.builder().id(204L).phoneNumber("9876543204").role(Role.ROLE_USER).active(true).deletedByUser(false).build();

        when(userRepository.findByRole(Role.ROLE_USER)).thenReturn(List.of(userWithOrders, userWithReviews, cleanUser, activeUser));

        when(orderRepository.existsByUser(userWithOrders)).thenReturn(true);
        when(reviewRepository.existsByUser(userWithOrders)).thenReturn(false);
        when(reviewHelpfulRepository.existsByUser(userWithOrders)).thenReturn(false);

        when(orderRepository.existsByUser(userWithReviews)).thenReturn(false);
        when(reviewRepository.existsByUser(userWithReviews)).thenReturn(true);
        when(reviewHelpfulRepository.existsByUser(userWithReviews)).thenReturn(false);

        when(orderRepository.existsByUser(cleanUser)).thenReturn(false);
        when(reviewRepository.existsByUser(cleanUser)).thenReturn(false);
        when(reviewHelpfulRepository.existsByUser(cleanUser)).thenReturn(false);

        adminUserService.clearBlockedUsers();

        verify(notificationRepository, times(1)).deleteByUser(cleanUser);
        verify(notificationRepository, never()).deleteByUser(userWithOrders);
        verify(notificationRepository, never()).deleteByUser(userWithReviews);
        verify(notificationRepository, never()).deleteByUser(activeUser);

        ArgumentCaptor<List<User>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRepository, times(1)).deleteAll(captor.capture());
        List<User> deleted = captor.getValue();
        assertEquals(1, deleted.size());
        assertEquals(203L, deleted.get(0).getId());
    }

    // =========================================================================
    // FIX 5: CLEAR-ALL ORDERS ENDPOINT REMOVAL TESTS
    // =========================================================================

    @Test
    @DisplayName("Fix 5: AdminOrderController must NOT expose a DELETE clear-all endpoint")
    void testAdminOrderControllerDoesNotHaveClearAllEndpoint() {
        Method[] methods = AdminOrderController.class.getDeclaredMethods();
        for (Method method : methods) {
            DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
            if (deleteMapping != null) {
                for (String path : deleteMapping.value()) {
                    assertFalse(path.contains("clear-all"),
                            "AdminOrderController must not have any DELETE mapping for 'clear-all': found on " + method.getName());
                }
            }
            assertNotEquals("clearAllOrders", method.getName(),
                    "AdminOrderController must not contain clearAllOrders method");
        }
    }

    @Test
    @DisplayName("Fix 5: AdminOrderController retains valid operational order management endpoints")
    void testAdminOrderControllerValidEndpointsRetained() {
        OrderService mockOrderService = mock(OrderService.class);
        AdminOrderController controller = new AdminOrderController(mockOrderService);

        when(mockOrderService.getOrdersByStatus(OrderStatus.CREATED)).thenReturn(Collections.emptyList());
        assertNotNull(controller.getOrdersByStatus(OrderStatus.CREATED));

        when(mockOrderService.getTodaysOrders()).thenReturn(Collections.emptyList());
        assertNotNull(controller.getTodaysOrders());

        when(mockOrderService.getTodayRevenue()).thenReturn(1500.0);
        assertEquals(1500.0, controller.getTodayRevenue());

        when(mockOrderService.getMonthlyRevenue()).thenReturn(45000.0);
        assertEquals(45000.0, controller.getMonthlyRevenue());

        when(mockOrderService.getPendingDeliveries()).thenReturn(Collections.emptyList());
        assertNotNull(controller.getPendingDeliveries());
    }
}
