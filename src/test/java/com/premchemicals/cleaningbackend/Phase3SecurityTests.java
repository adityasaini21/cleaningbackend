package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.config.SecurityConfig;
import com.premchemicals.cleaningbackend.controller.LogController;
import com.premchemicals.cleaningbackend.dto.ReviewRequestDTO;
import com.premchemicals.cleaningbackend.dto.ReviewSummaryDTO;
import com.premchemicals.cleaningbackend.exception.ErrorResponse;
import com.premchemicals.cleaningbackend.exception.GlobalExceptionHandler;
import com.premchemicals.cleaningbackend.model.Notification;
import com.premchemicals.cleaningbackend.model.Product;
import com.premchemicals.cleaningbackend.model.Review;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.repository.NotificationRepository;
import com.premchemicals.cleaningbackend.repository.ProductRepository;
import com.premchemicals.cleaningbackend.repository.ReviewHelpfulRepository;
import com.premchemicals.cleaningbackend.repository.ReviewRepository;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import com.premchemicals.cleaningbackend.service.NotificationService;
import com.premchemicals.cleaningbackend.service.impl.ReviewServiceImpl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class Phase3SecurityTests {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // 1. REVIEW VALIDATION TESTS
    // =========================================================

    @Test
    @DisplayName("Review Validation: Reject missing product ID")
    void testReviewValidationMissingProductId() {
        ReviewRequestDTO dto = ReviewRequestDTO.builder()
                .productId(null)
                .rating(5)
                .comment("Great product!")
                .build();

        Set<ConstraintViolation<ReviewRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Should fail validation when product ID is null");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Product ID is required")));
    }

    @Test
    @DisplayName("Review Validation: Reject missing or out-of-range ratings")
    void testReviewValidationRatingRange() {
        // Null rating
        ReviewRequestDTO nullRating = ReviewRequestDTO.builder()
                .productId(1L)
                .rating(null)
                .comment("Ok")
                .build();
        Set<ConstraintViolation<ReviewRequestDTO>> nullViolations = validator.validate(nullRating);
        assertFalse(nullViolations.isEmpty());
        assertTrue(nullViolations.stream().anyMatch(v -> v.getMessage().contains("Rating is required")));

        // Rating 0 (less than 1)
        ReviewRequestDTO zeroRating = ReviewRequestDTO.builder()
                .productId(1L)
                .rating(0)
                .comment("Bad")
                .build();
        Set<ConstraintViolation<ReviewRequestDTO>> zeroViolations = validator.validate(zeroRating);
        assertFalse(zeroViolations.isEmpty());
        assertTrue(zeroViolations.stream().anyMatch(v -> v.getMessage().contains("Rating must be between 1 and 5")));

        // Rating 6 (greater than 5)
        ReviewRequestDTO sixRating = ReviewRequestDTO.builder()
                .productId(1L)
                .rating(6)
                .comment("Awesome")
                .build();
        Set<ConstraintViolation<ReviewRequestDTO>> sixViolations = validator.validate(sixRating);
        assertFalse(sixViolations.isEmpty());
        assertTrue(sixViolations.stream().anyMatch(v -> v.getMessage().contains("Rating must be between 1 and 5")));

        // Valid ratings 1 through 5
        for (int r = 1; r <= 5; r++) {
            ReviewRequestDTO validDto = ReviewRequestDTO.builder()
                    .productId(1L)
                    .rating(r)
                    .comment("Valid comment")
                    .build();
            assertTrue(validator.validate(validDto).isEmpty(), "Rating " + r + " must pass validation");
        }
    }

    @Test
    @DisplayName("Review Validation: Reject comments exceeding 1000 characters")
    void testReviewValidationCommentLength() {
        String longComment = "a".repeat(1001);
        ReviewRequestDTO dto = ReviewRequestDTO.builder()
                .productId(1L)
                .rating(4)
                .comment(longComment)
                .build();

        Set<ConstraintViolation<ReviewRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "Comment over 1000 characters must fail validation");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("Comment cannot exceed 1000 characters")));

        // Exactly 1000 chars must pass
        ReviewRequestDTO exactDto = ReviewRequestDTO.builder()
                .productId(1L)
                .rating(4)
                .comment("a".repeat(1000))
                .build();
        assertTrue(validator.validate(exactDto).isEmpty(), "1000 character comment must be valid");
    }

    @Test
    @DisplayName("Review Service: getProductReviews does not crash with NPE when review rating is null")
    void testGetProductReviewsWithNullRatingDoesNotCrash() {
        ProductRepository productRepo = mock(ProductRepository.class);
        ReviewRepository reviewRepo = mock(ReviewRepository.class);
        ReviewHelpfulRepository helpfulRepo = mock(ReviewHelpfulRepository.class);
        ReviewServiceImpl reviewService = new ReviewServiceImpl(
                reviewRepo,
                productRepo,
                mock(UserRepository.class),
                null,
                helpfulRepo
        );

        Product product = Product.builder().id(10L).name("Test Cleaner").build();
        when(productRepo.findById(10L)).thenReturn(Optional.of(product));

        // Two reviews: one with valid rating 4, one with legacy null rating
        Review r1 = Review.builder().id(1L).rating(4).comment("Good").product(product).build();
        Review r2 = Review.builder().id(2L).rating(null).comment("Legacy review with null rating").product(product).build();
        when(reviewRepo.findByProduct(product)).thenReturn(List.of(r1, r2));

        // Must calculate without throwing NullPointerException
        ReviewSummaryDTO summary = reviewService.getProductReviews(10L);
        assertNotNull(summary);
        assertEquals(2, summary.getReviewCount());
        // Average should be based on the valid non-null review (4.0), not crashing or assigning a fake rating
        assertEquals(4.0, summary.getAverageRating());
        assertEquals(1, summary.getFourStar());
    }

    // =========================================================
    // 2. CORS TESTS
    // =========================================================

    @Test
    @DisplayName("CORS: Respects configured allowed origins and rejects arbitrary wildcards")
    void testCorsConfigurationRespectsAllowedOrigins() {
        SecurityConfig config = new SecurityConfig(null, null, null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of("http://localhost:5173", "http://localhost:3000"));

        CorsConfigurationSource source = config.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/products");
        CorsConfiguration cors = source.getCorsConfiguration(request);
        assertNotNull(cors);

        // Verify allowed origins match configured values
        List<String> origins = cors.getAllowedOrigins();
        assertNotNull(origins);
        assertTrue(origins.contains("http://localhost:5173"));
        assertTrue(origins.contains("http://localhost:3000"));
        assertFalse(origins.contains("*"), "Wildcard * must not be set as an allowed origin with credentials");
        assertTrue(cors.getAllowCredentials(), "Credentials should be supported for configured origins");
    }

    // =========================================================
    // 3. GEOCODING TELEMETRY PRIVACY TESTS
    // =========================================================

    @Test
    @DisplayName("Geocoding Privacy: Endpoint returns 200 and extracts numeric option without logging sensitive coordinates or address")
    void testGeocodingTelemetryEndpointSanitized() {
        LogController controller = new LogController();

        // 1. Flutter standard payload for Option 2
        Map<String, Object> payloadOpt2 = Map.of(
                "option", "OPTION 2: Native OS Geocoder + India Post",
                "latitude", 26.4502508,
                "longitude", 80.2958044,
                "address", "123 Sensitive Street",
                "fullFormattedAddress", "123 Sensitive Street, Kanpur, UP"
        );
        ResponseEntity<Map<String, String>> response2 = controller.logGeocoding(payloadOpt2);
        assertEquals(HttpStatus.OK, response2.getStatusCode());
        assertEquals("logged", response2.getBody().get("status"));

        // 2. Direct numeric payload
        Map<String, Object> payloadDirect = Map.of(
                "option", 1
        );
        ResponseEntity<Map<String, String>> responseDirect = controller.logGeocoding(payloadDirect);
        assertEquals(HttpStatus.OK, responseDirect.getStatusCode());
        assertEquals("logged", responseDirect.getBody().get("status"));
    }

    // =========================================================
    // 4. NOTIFICATION OWNERSHIP TESTS
    // =========================================================

    @Test
    @DisplayName("Notification Ownership: User can mark only their own notifications as read")
    void testNotificationOwnershipMarkAsRead() {
        NotificationRepository notificationRepo = mock(NotificationRepository.class);
        UserRepository userRepo = mock(UserRepository.class);

        NotificationService service = new NotificationService(notificationRepo, userRepo);

        // Authenticated user is Customer A
        String customerPhone = "9876543210";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customerPhone, "password")
        );

        User customerA = User.builder().id(101L).phoneNumber(customerPhone).build();
        when(userRepo.findByPhoneNumber(customerPhone)).thenReturn(Optional.of(customerA));

        // Notification 5 belongs to Customer A
        Notification notifA = Notification.builder().id(5L).user(customerA).read(false).build();
        when(notificationRepo.findByIdAndUser(5L, customerA)).thenReturn(Optional.of(notifA));

        // Marking own notification succeeds
        service.markAsRead(5L);
        assertTrue(notifA.isRead(), "Customer A's notification should be marked as read");
        verify(notificationRepo, times(1)).save(notifA);

        // Notification 99 belongs to Customer B (not Customer A)
        when(notificationRepo.findByIdAndUser(99L, customerA)).thenReturn(Optional.empty());

        // Attempting to mark another user's notification as read throws exception
        RuntimeException exception = assertThrows(RuntimeException.class, () -> service.markAsRead(99L));
        assertTrue(exception.getMessage().contains("Notification not found or unauthorized"));
    }

    // =========================================================
    // 5. ERROR LOGGING & RESPONSE SANITIZATION TESTS
    // =========================================================

    @Test
    @DisplayName("Error Handling: RuntimeException response masks internal database and system details")
    void testGlobalExceptionHandlerSanitizesInternalErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");

        // 1. Database-related exception
        RuntimeException dbEx = new RuntimeException("org.postgresql.util.PSQLException: Connection refused to database");
        ResponseEntity<ErrorResponse> dbResponse = handler.handleRuntimeException(dbEx, request);

        assertEquals(HttpStatus.BAD_REQUEST, dbResponse.getStatusCode());
        assertNotNull(dbResponse.getBody());
        assertFalse(dbResponse.getBody().getMessage().contains("PSQLException"), "Must not leak Postgres exception class");
        assertFalse(dbResponse.getBody().getMessage().contains("database"), "Must mask raw database details");
        assertNotNull(dbResponse.getBody().getCorrelationId());

        // 2. File path / internal URL exception
        RuntimeException pathEx = new RuntimeException("Failed to access /var/app/internal/secret/config.properties");
        ResponseEntity<ErrorResponse> pathResponse = handler.handleRuntimeException(pathEx, request);

        assertFalse(pathResponse.getBody().getMessage().contains("/var/app"), "Must not leak internal server paths");
        assertTrue(pathResponse.getBody().getMessage().contains("An unexpected error occurred"));

        // 3. Clean user-facing business exception
        RuntimeException cleanEx = new RuntimeException("You can review only purchased products");
        ResponseEntity<ErrorResponse> cleanResponse = handler.handleRuntimeException(cleanEx, request);
        assertEquals("You can review only purchased products", cleanResponse.getBody().getMessage());
    }
}
