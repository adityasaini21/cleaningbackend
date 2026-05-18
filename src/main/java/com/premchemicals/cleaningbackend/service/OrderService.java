package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.*;
import com.premchemicals.cleaningbackend.model.*;
import com.premchemicals.cleaningbackend.model.enums.*;
import com.premchemicals.cleaningbackend.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;

    // =========================================================
    // CREATE ORDER
    // =========================================================

    @Transactional
    public OrderResponseDTO placeOrder(OrderRequestDTO request) {

        String username = getLoggedInUsername();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Order order = new Order();

        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setShippingAddress(request.getShippingAddress());
        order.setPhoneNumber(request.getPhoneNumber());

        PaymentMethod paymentMethod =
                PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase());

        order.setPaymentMethod(paymentMethod);

        double totalAmount = 0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderRequestDTO.OrderItemRequest item : request.getItems()) {

            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (product.getStock() < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for " + product.getName());
            }

            OrderItem orderItem = new OrderItem();

            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPrice(product.getPrice());

            totalAmount += product.getPrice() * item.getQuantity();

            orderItems.add(orderItem);

            if (paymentMethod == PaymentMethod.COD) {
                product.setStock(product.getStock() - item.getQuantity());
            }
        }

        order.setOrderItems(orderItems);
        order.setTotalAmount(totalAmount);

        if (paymentMethod == PaymentMethod.ONLINE) {
            order.setOrderStatus(OrderStatus.CREATED);
            order.setPaymentStatus(PaymentStatus.PENDING);
        } else {
            order.setOrderStatus(OrderStatus.CONFIRMED);
            order.setPaymentStatus(PaymentStatus.PENDING);
        }

        orderRepository.save(order);

        return mapToResponse(order);
    }

    // =========================================================
    // GET MY ORDERS
    // =========================================================

    public List<OrderResponseDTO> getMyOrders() {

        User user = userRepository.findByUsername(getLoggedInUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return orderRepository.findByUser(user)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =========================================================
    // GET ALL ORDERS
    // =========================================================

    public List<OrderResponseDTO> getAllOrders() {

        return orderRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =========================================================
    // GET ORDER BY ID
    // =========================================================

    public OrderResponseDTO getOrderById(Long id) {

        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        return mapToResponse(order);
    }

    // =========================================================
    // UPDATE ORDER STATUS
    // =========================================================

    @Transactional
    public OrderResponseDTO updateOrderStatus(Long orderId, OrderStatus newStatus) {

        Order order = getOrderOrThrow(orderId);

        order.setOrderStatus(newStatus);

        if (newStatus == OrderStatus.DELIVERED) {

            order.setDeliveredAt(LocalDateTime.now());

            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.setPaymentStatus(PaymentStatus.COMPLETED);
            }
        }

        return mapToResponse(order);
    }

    // =========================================================
// CANCEL ORDER
// =========================================================

    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId) {

        Order order = getOrderOrThrow(orderId);

        // 🔥 Already cancelled check
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order already cancelled"
            );
        }

        // 🔥 Delivered check
        if (order.getOrderStatus() == OrderStatus.DELIVERED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Delivered order cannot be cancelled"
            );
        }

        // 🔥 1-MINUTE RULE (CRITICAL FIX)
        LocalDateTime createdAt = order.getCreatedAt() != null
                ? order.getCreatedAt()
                : order.getOrderDate(); // fallback safety

        if (createdAt == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order timestamp missing"
            );
        }

        Duration diff = Duration.between(createdAt, LocalDateTime.now());

        if (diff.getSeconds() >= 60) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cancel time expired (only allowed within 1 minute)"
            );
        }

        // 🔥 CANCEL ORDER
        order.setOrderStatus(OrderStatus.CANCELLED);

        // 🔄 RESTORE STOCK
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
        }

        return mapToResponse(order);
    }

    // =========================================================
    // PAYMENT SUCCESS
    // =========================================================

    @Transactional
    public void markPaymentSuccess(Long orderId) {

        Order order = getOrderOrThrow(orderId);

        for (OrderItem item : order.getOrderItems()) {

            Product product = item.getProduct();

            product.setStock(product.getStock() - item.getQuantity());
        }

        order.setPaymentStatus(PaymentStatus.COMPLETED);
        order.setOrderStatus(OrderStatus.CONFIRMED);
    }

    // =========================================================
    // ANALYTICS
    // =========================================================

    public List<TopProductDTO> getTopSellingProducts() {

        return orderRepository.findTopSellingProducts()
                .stream()
                .map(o -> new TopProductDTO(
                        (String) o[0],
                        (Long) o[1],
                        (Double) o[2]))
                .toList();
    }

    public List<TopProductDTO> getWorstSellingProducts() {

        return orderRepository.findWorstSellingProducts()
                .stream()
                .map(o -> new TopProductDTO(
                        (String) o[0],
                        (Long) o[1],
                        (Double) o[2]))
                .toList();
    }

    public List<DeadStockDTO> getDeadStockProducts() {

        LocalDateTime date = LocalDateTime.now().minusDays(30);

        return orderRepository.findDeadStockProducts(date)
                .stream()
                .map(p -> new DeadStockDTO(
                        p.getName(),
                        p.getStock()))
                .toList();
    }

    public List<ProductionForecastDTO> getProductionForecast() {

        LocalDateTime date = LocalDateTime.now().minusDays(30);

        return orderRepository.getLast30DaysSales(date)
                .stream()
                .map(o -> {

                    String name = (String) o[0];
                    Long sold = (Long) o[1];

                    double avg = sold / 30.0;
                    double forecast = avg * 7;

                    return new ProductionForecastDTO(
                            name,
                            sold,
                            avg,
                            forecast
                    );
                })
                .toList();
    }

    public CustomerRepeatDTO getCustomerRepeatAnalytics() {

        List<Object[]> data = orderRepository.getCustomerOrderCounts();

        long total = data.size();

        long repeat = data.stream()
                .filter(o -> (Long) o[1] >= 2)
                .count();

        double rate = total > 0 ? (repeat * 100.0) / total : 0;

        return new CustomerRepeatDTO(total, repeat, rate);
    }

    public DeliveryPerformanceDTO getDeliveryPerformance() {

        long delivered =
                orderRepository.countByOrderStatus(OrderStatus.DELIVERED);

        Double avg =
                orderRepository.getAverageDeliveryTimeInMinutes();

        if (avg == null) avg = 0.0;

        long pending =
                orderRepository.countByOrderStatus(OrderStatus.OUT_FOR_DELIVERY);

        return new DeliveryPerformanceDTO(
                delivered,
                avg,
                0L,
                pending
        );
    }

    // =========================================================
    // PRIVATE HELPERS
    // =========================================================

    private String getLoggedInUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Order getOrderOrThrow(Long id) {

        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private OrderResponseDTO mapToResponse(Order order) {

        OrderResponseDTO dto = new OrderResponseDTO();

        dto.setOrderId(order.getId());

        // 🔥 FIX: Use createdAt properly
        dto.setCreatedAt(
                order.getCreatedAt() != null
                        ? order.getCreatedAt()
                        : order.getOrderDate()   // fallback (important)
        );

        dto.setTotalAmount(order.getTotalAmount());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setShippingAddress(order.getShippingAddress());
        dto.setPhoneNumber(order.getPhoneNumber());

        dto.setItems(order.getOrderItems().stream().map(item -> {

            OrderResponseDTO.OrderItemResponse r =
                    new OrderResponseDTO.OrderItemResponse();

            r.setProductName(item.getProduct().getName());
            r.setQuantity(item.getQuantity());
            r.setPrice(item.getPrice());

            return r;

        }).toList());

        return dto;
    }
    // =========================================================
// RESTOCK RECOMMENDATION
// =========================================================
    public List<RestockRecommendationDTO> getRestockRecommendation() {

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        List<Object[]> salesData =
                orderRepository.getLast30DaysSales(thirtyDaysAgo);

        Map<String, Long> salesMap = new HashMap<>();

        for (Object[] obj : salesData) {
            salesMap.put((String) obj[0], (Long) obj[1]);
        }

        return productRepository.findAllActiveWithCategory()
                .stream()
                .map(product -> {

                    Long sold = salesMap.getOrDefault(product.getName(), 0L);

                    double dailyAverage = sold / 30.0;
                    double forecast7 = dailyAverage * 7;

                    int recommended = 0;

                    if (product.getStock() < forecast7) {
                        recommended = (int) Math.ceil(forecast7 - product.getStock());
                    }

                    return new RestockRecommendationDTO(
                            product.getName(),
                            product.getStock(),
                            forecast7,
                            recommended
                    );
                })
                .toList();
    }

    // =========================================================
// GET ORDERS BY STATUS
// =========================================================
    public List<OrderResponseDTO> getOrdersByStatus(OrderStatus status) {

        return orderRepository.findByOrderStatus(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =========================================================
// TODAY ORDERS
// =========================================================
    public List<OrderResponseDTO> getTodaysOrders() {

        LocalDate today = LocalDate.now();

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(23,59,59);

        return orderRepository.findByOrderDateBetween(start,end)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =========================================================
// TODAY REVENUE
// =========================================================
    public Double getTodayRevenue() {

        LocalDate today = LocalDate.now();

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(23,59,59);

        Double revenue =
                orderRepository.calculateRevenueBetweenDates(
                        PaymentStatus.COMPLETED,
                        start,
                        end
                );

        return revenue != null ? revenue : 0.0;
    }

    // =========================================================
// PENDING DELIVERIES
// =========================================================
    public List<OrderResponseDTO> getPendingDeliveries() {

        return orderRepository.findByOrderStatus(OrderStatus.OUT_FOR_DELIVERY)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }
    // =========================================================
// DASHBOARD SUMMARY
// =========================================================
    public DashboardSummaryDTO getDashboardSummary() {

        LocalDate today = LocalDate.now();

        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(23,59,59);

        YearMonth month = YearMonth.now();

        LocalDateTime startOfMonth = month.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = month.atEndOfMonth().atTime(23,59,59);

        long todayOrders =
                orderRepository.countByOrderDateBetween(startOfDay,endOfDay);

        long completedOrders =
                orderRepository.countByPaymentStatus(PaymentStatus.COMPLETED);

        long pendingDeliveries =
                orderRepository.countByOrderStatus(OrderStatus.OUT_FOR_DELIVERY);

        Double todayRevenue =
                orderRepository.calculateRevenueBetweenDates(
                        PaymentStatus.COMPLETED,
                        startOfDay,
                        endOfDay
                );

        Double monthlyRevenue =
                orderRepository.calculateRevenueBetweenDates(
                        PaymentStatus.COMPLETED,
                        startOfMonth,
                        endOfMonth
                );

        long lowStockCount =
                productService.getLowStockCount();

        return DashboardSummaryDTO.builder()
                .todayOrders(todayOrders)
                .completedOrders(completedOrders)
                .pendingDeliveries(pendingDeliveries)
                .todayRevenue(todayRevenue != null ? todayRevenue : 0)
                .monthlyRevenue(monthlyRevenue != null ? monthlyRevenue : 0)
                .lowStockCount(lowStockCount)
                .build();
    }

    // =========================================================
// MONTHLY REVENUE
// =========================================================
    public Double getMonthlyRevenue() {

        YearMonth currentMonth = YearMonth.now();

        LocalDateTime start =
                currentMonth.atDay(1).atStartOfDay();

        LocalDateTime end =
                currentMonth.atEndOfMonth().atTime(23, 59, 59);

        Double revenue =
                orderRepository.calculateRevenueBetweenDates(
                        PaymentStatus.COMPLETED,
                        start,
                        end
                );

        return revenue != null ? revenue : 0.0;
    }

}