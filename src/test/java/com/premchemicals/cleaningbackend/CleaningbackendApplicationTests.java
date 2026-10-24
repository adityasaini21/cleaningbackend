package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.controller.OrderController;
import com.premchemicals.cleaningbackend.dto.*;
import com.premchemicals.cleaningbackend.model.*;
import com.premchemicals.cleaningbackend.model.enums.*;
import com.premchemicals.cleaningbackend.repository.*;
import com.premchemicals.cleaningbackend.security.JwtUtil;
import com.premchemicals.cleaningbackend.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CleaningbackendApplicationTests {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private PaymentTransactionRepository paymentTransactionRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private ProductService productService;
    private DeliveryPincodeRepository deliveryPincodeRepository;
    private NotificationService notificationService;
    private GoogleMapsService googleMapsService;
    private DeliveryPincodeService deliveryPincodeService;
    private PaymentService paymentService;

    private OrderService orderService;
    private OrderController orderController;
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        paymentTransactionRepository = mock(PaymentTransactionRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        productService = mock(ProductService.class);
        deliveryPincodeRepository = mock(DeliveryPincodeRepository.class);
        notificationService = mock(NotificationService.class);
        googleMapsService = mock(GoogleMapsService.class);
        deliveryPincodeService = mock(DeliveryPincodeService.class);
        paymentService = mock(PaymentService.class);

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

        orderController = new OrderController(orderService, paymentService);

        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "cleaning_backend_super_secret_key_2026_secure_application_987654321");
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 15552000000L);
        jwtUtil.init();

        // Default: delivery pincode deliverable
        when(deliveryPincodeService.isDeliverable(anyString())).thenReturn(true);

        // Authenticate mock user in SecurityContext
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("9795328275", null, List.of(() -> "ROLE_USER"))
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Verify Local Checkout Flow: Order Placement & Amount Calculation")
    void testLocalCheckoutOrderPlacement() {
        User user = User.builder()
                .id(1L)
                .phoneNumber("9795328275")
                .fullName("Test Customer")
                .role(Role.ROLE_USER)
                .active(true)
                .build();
        when(userRepository.findByPhoneNumber("9795328275")).thenReturn(Optional.of(user));

        Product product = new Product();
        product.setId(1L);
        product.setName("Floor Cleaner 5L");
        product.setPrice(100.0);
        product.setActive(true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        when(googleMapsService.getCoordinatesFromAddress(any(), any())).thenReturn(new double[]{26.45, 80.29});
        when(googleMapsService.getRoadDistanceInMeters(anyDouble(), anyDouble())).thenReturn(2500.0); // <= 5km -> ₹49 delivery

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 101L);
            return o;
        });

        OrderRequestDTO request = new OrderRequestDTO();
        request.setShippingAddress("123 Swarup Nagar, Kanpur");
        request.setPhoneNumber("9795328275");
        request.setPincode("208002");
        request.setPaymentMethod("COD");

        OrderRequestDTO.OrderItemRequest itemDto = new OrderRequestDTO.OrderItemRequest();
        itemDto.setProductId(1L);
        itemDto.setQuantity(3); // 3 * 100 = 300 >= 200 min order
        request.setItems(List.of(itemDto));

        OrderResponseDTO response = orderController.placeOrder(request);

        assertNotNull(response);
        assertEquals(101L, response.getOrderId());
        assertEquals(349.0, response.getTotalAmount(), "Total amount must be item total (₹300) + delivery charge (₹49)");
        assertEquals(OrderStatus.CONFIRMED, response.getOrderStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Verify Checkout Rejects Inactive Products")
    void testCheckoutRejectsInactiveProduct() {
        User user = User.builder().id(1L).phoneNumber("9795328275").active(true).build();
        when(userRepository.findByPhoneNumber("9795328275")).thenReturn(Optional.of(user));

        Product inactiveProduct = new Product();
        inactiveProduct.setId(2L);
        inactiveProduct.setName("Discontinued Soap");
        inactiveProduct.setPrice(150.0);
        inactiveProduct.setActive(false);
        when(productRepository.findById(2L)).thenReturn(Optional.of(inactiveProduct));

        OrderRequestDTO request = new OrderRequestDTO();
        request.setShippingAddress("123 Street");
        request.setPhoneNumber("9795328275");
        request.setPincode("208002");
        request.setPaymentMethod("COD");

        OrderRequestDTO.OrderItemRequest itemDto = new OrderRequestDTO.OrderItemRequest();
        itemDto.setProductId(2L);
        itemDto.setQuantity(2);
        request.setItems(List.of(itemDto));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.placeOrder(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("currently unavailable"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Verify Checkout Rejects Orders Below ₹200 Minimum Limit")
    void testCheckoutRejectsSubMinimumOrder() {
        User user = User.builder().id(1L).phoneNumber("9795328275").active(true).build();
        when(userRepository.findByPhoneNumber("9795328275")).thenReturn(Optional.of(user));

        Product product = new Product();
        product.setId(3L);
        product.setName("Small Brush");
        product.setPrice(50.0);
        product.setActive(true);
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));

        OrderRequestDTO request = new OrderRequestDTO();
        request.setShippingAddress("123 Street");
        request.setPhoneNumber("9795328275");
        request.setPincode("208002");
        request.setPaymentMethod("COD");

        OrderRequestDTO.OrderItemRequest itemDto = new OrderRequestDTO.OrderItemRequest();
        itemDto.setProductId(3L);
        itemDto.setQuantity(1); // 1 * 50 = 50 < 200
        request.setItems(List.of(itemDto));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.placeOrder(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Minimum order amount is ₹199.00"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Verify Admin Purge All Orders: deletes transactions, items, and orders in batch")
    void testAdminPurgeAllOrders() {
        java.util.Map<String, String> response = orderController.deleteAllOrders();
        assertNotNull(response);
        assertEquals("All orders have been successfully deleted.", response.get("message"));

        verify(paymentTransactionRepository, times(1)).deleteAllInBatch();
        verify(orderItemRepository, times(1)).deleteAllInBatch();
        verify(orderRepository, times(1)).deleteAllInBatch();
    }

    @Test
    @DisplayName("Verify GeocodingController reverseGeocode returns 200 with address")
    void testReverseGeocodeController() {
        com.premchemicals.cleaningbackend.controller.GeocodingController geocodingController =
                new com.premchemicals.cleaningbackend.controller.GeocodingController(googleMapsService);

        Map<String, Object> mockResult = Map.of(
                "address", "123 Govind Nagar",
                "pincode", "208006",
                "city", "Kanpur",
                "state", "Uttar Pradesh"
        );
        when(googleMapsService.reverseGeocode(26.45, 80.30)).thenReturn(mockResult);

        org.springframework.http.ResponseEntity<?> response = geocodingController.reverseGeocode(26.45, 80.30);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("123 Govind Nagar", body.get("address"));
        assertEquals("208006", body.get("pincode"));
    }
}
