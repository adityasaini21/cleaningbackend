package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.*;
import com.premchemicals.cleaningbackend.model.*;
import com.premchemicals.cleaningbackend.model.enums.*;
import com.premchemicals.cleaningbackend.repository.*;
import com.premchemicals.cleaningbackend.service.NotificationService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.premchemicals.cleaningbackend.model.DeliveryPincode;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final DeliveryPincodeRepository
            deliveryPincodeRepository;
    private final NotificationService notificationService;
    private final GoogleMapsService googleMapsService;
    private final DeliveryPincodeService deliveryPincodeService;

    // =========================================================
    // CREATE ORDER
    // =========================================================

    @Transactional
    public OrderResponseDTO placeOrder(OrderRequestDTO request) {

        String phoneNumber = getLoggedInPhoneNumber();

        User user = userRepository
                .findByPhoneNumber(phoneNumber)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Order order = new Order();

        order.setUser(user);

        order.setOrderDate(LocalDateTime.now());

        order.setCreatedAt(LocalDateTime.now());

        order.setShippingAddress(
                request.getShippingAddress()
        );

        order.setPhoneNumber(
                request.getPhoneNumber()
        );

// ========================================
// PINCODE VALIDATION
// ========================================

        if (!deliveryPincodeService.isDeliverable(request.getPincode())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Delivery not available for this pincode"
            );
        }

        order.setPincode(request.getPincode().trim());

// ========================================

        PaymentMethod paymentMethod =
                PaymentMethod.valueOf(
                        request.getPaymentMethod()
                                .toUpperCase()
                );

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

        if (totalAmount < 200.0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum order amount is ₹200.00"
            );
        }

        order.setOrderItems(orderItems);

        // 🚗 CALCULATE DELIVERY CHARGE BY ROAD DISTANCE
        double deliveryCharge = 30.0;
        double lat = 26.4764; // Default store lat
        double lng = 80.3124; // Default store lng

        double[] coords = googleMapsService.getCoordinatesFromAddress(
                request.getShippingAddress(),
                request.getPincode()
        );
        if (coords != null) {
            lat = coords[0];
            lng = coords[1];
        }

        double distanceInMeters = googleMapsService.getRoadDistanceInMeters(lat, lng);
        System.out.println("🚗 Calculated road distance for order: " + distanceInMeters + " meters.");
        if (distanceInMeters > 5000.0) {
            deliveryCharge = 50.0;
        }

        order.setDeliveryCharge(deliveryCharge);
        order.setTotalAmount(totalAmount + deliveryCharge);

        if (paymentMethod == PaymentMethod.ONLINE) {
            order.setOrderStatus(OrderStatus.CREATED);
            order.setPaymentStatus(PaymentStatus.PENDING);
        } else {
            order.setOrderStatus(OrderStatus.CONFIRMED);
            order.setPaymentStatus(PaymentStatus.PENDING);
        }

        orderRepository.save(order);

// ========================================
// CREATE NOTIFICATION
// ========================================

        if (paymentMethod == PaymentMethod.COD) {
            notificationService.createNotification(
                    user,
                    "Order Placed",
                    "Your order #" + order.getId() +
                            " has been placed successfully."
            );

            List<User> admins =
                    userRepository.findAll()
                            .stream()
                            .filter(u -> u.getRole() == Role.ROLE_ADMIN)
                            .toList();

            for (User admin : admins) {
                notificationService.createNotification(
                        admin,
                        "New Order Received",
                        "New order #" + order.getId() +
                                " placed by " + user.getFullName()
                );
            }
        }

// ========================================

        return mapToResponse(order);
    }

    public Map<String, Object> getDeliveryChargeAndDistance(String address, String pincode) {
        double lat = 26.4764; // Default store coordinates as fallback origin
        double lng = 80.3124;

        double[] coords = googleMapsService.getCoordinatesFromAddress(address, pincode);
        if (coords != null) {
            lat = coords[0];
            lng = coords[1];
        }

        double distanceInMeters = googleMapsService.getRoadDistanceInMeters(lat, lng);
        double deliveryCharge = 30.0;
        if (distanceInMeters > 5000.0) {
            deliveryCharge = 50.0;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("distanceMeters", distanceInMeters);
        result.put("deliveryCharge", deliveryCharge);
        return result;
    }

    // =========================================================
    // GET MY ORDERS
    // =========================================================

    public List<OrderResponseDTO> getMyOrders() {

        User user = userRepository.findByPhoneNumber(getLoggedInPhoneNumber())
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
                .filter(order -> order.getPaymentMethod() != PaymentMethod.ONLINE
                        || order.getPaymentStatus() == PaymentStatus.COMPLETED)
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

    @Transactional
    public OrderResponseDTO updateOrderStatus(
            Long orderId,
            OrderStatus newStatus
    ) {

        Order order = getOrderOrThrow(orderId);

        order.setOrderStatus(newStatus);

// =========================================
// PAYMENT STATUS SYNC
// =========================================

        if (order.getPaymentMethod() == PaymentMethod.COD) {

            if (newStatus == OrderStatus.DELIVERED) {

                order.setDeliveredAt(LocalDateTime.now());

                order.setPaymentStatus(
                        PaymentStatus.COMPLETED
                );

            } else {

                order.setPaymentStatus(
                        PaymentStatus.PENDING
                );
            }
        }

        // =========================================
        // NOTIFICATION MESSAGE
        // =========================================

        String title = "Order Update";

        String message =
                "Your order #" + order.getId() +
                        " status changed to " +
                        newStatus.name().replace("_", " ");

        // =========================================
        // CUSTOM MESSAGES
        // =========================================

        switch (newStatus) {

            case CONFIRMED:

                message =
                        "Your order #" + order.getId() +
                                " has been confirmed ✅";
                break;


            case OUT_FOR_DELIVERY:

                message =
                        "Your order #" + order.getId() +
                                " is out for delivery 📦";
                break;

            case DELIVERED:

                message =
                        "Your order #" + order.getId() +
                                " has been delivered 🎉";
                break;

            case CANCELLED:

                message =
                        "Your order #" + order.getId() +
                                " has been cancelled ❌";
                break;
        }

        // =========================================
        // SEND NOTIFICATION
        // =========================================

        notificationService.createNotification(

                order.getUser(),

                title,

                message
        );

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

    private String getLoggedInPhoneNumber() {

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

        dto.setCreatedAt(
                order.getCreatedAt() != null
                        ? order.getCreatedAt()
                        : order.getOrderDate()
        );

        dto.setTotalAmount(order.getTotalAmount());
        dto.setDeliveryCharge(order.getDeliveryCharge() != null ? order.getDeliveryCharge() : 0.0);

        dto.setOrderStatus(order.getOrderStatus());

        dto.setPaymentStatus(order.getPaymentStatus());

        dto.setShippingAddress(order.getShippingAddress());

        dto.setPhoneNumber(order.getPhoneNumber());
        dto.setDeliveryBoyName(
                order.getDeliveryBoyName()
        );

        dto.setDeliveryBoyPhone(
                order.getDeliveryBoyPhone()
        );

        dto.setPincode(order.getPincode());

        dto.setItems(

                order.getOrderItems()

                        .stream()

                        .map(item -> {

                            OrderResponseDTO.OrderItemResponse r =
                                    new OrderResponseDTO.OrderItemResponse();

                            r.setProductId(
                                    item.getProduct().getId()
                            );

                            r.setProductName(
                                    item.getProduct().getName()
                            );

                            r.setQuantity(
                                    item.getQuantity()
                            );

                            r.setPrice(
                                    item.getPrice()
                            );

                            return r;

                        })

                        .toList()
        );

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
                .filter(order -> order.getPaymentMethod() != PaymentMethod.ONLINE
                        || order.getPaymentStatus() == PaymentStatus.COMPLETED)
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
                .filter(order -> order.getPaymentMethod() != PaymentMethod.ONLINE
                        || order.getPaymentStatus() == PaymentStatus.COMPLETED)
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

        // =========================================
        // TODAY
        // =========================================

        LocalDateTime startOfDay =
                today.atStartOfDay();

        LocalDateTime endOfDay =
                today.atTime(23,59,59);

        // =========================================
        // WEEK
        // =========================================

        LocalDate startWeek =
                today.minusDays(6);

        LocalDateTime startOfWeek =
                startWeek.atStartOfDay();

        LocalDateTime endOfWeek =
                endOfDay;

        // =========================================
        // MONTH
        // =========================================

        YearMonth month = YearMonth.now();

        LocalDateTime startOfMonth =
                month.atDay(1).atStartOfDay();

        LocalDateTime endOfMonth =
                month.atEndOfMonth()
                        .atTime(23,59,59);

        // =========================================
        // TODAY STATS
        // =========================================

        long todayOrders =
                orderRepository.countConfirmedOrdersBetween(
                        startOfDay,
                        endOfDay
                );

        Double todayRevenue =
                orderRepository.calculateRevenueBetweenDates(
                        PaymentStatus.COMPLETED,
                        startOfDay,
                        endOfDay
                );

        // =========================================
        // WEEKLY STATS
        // =========================================

        long weeklyOrders =
                orderRepository.countConfirmedOrdersBetween(
                        startOfWeek,
                        endOfWeek
                );

        Double weeklyRevenue =
                orderRepository.calculateRevenueBetweenDates(
                        PaymentStatus.COMPLETED,
                        startOfWeek,
                        endOfWeek
                );

        // =========================================
        // MONTHLY STATS
        // =========================================

        long monthlyOrders =
                orderRepository.countConfirmedOrdersBetween(
                        startOfMonth,
                        endOfMonth
                );

        Double monthlyRevenue =
                orderRepository.calculateRevenueBetweenDates(
                        PaymentStatus.COMPLETED,
                        startOfMonth,
                        endOfMonth
                );

        // =========================================
        // PROFIT CALCULATION
        // =========================================

        double todayProfit =
                (todayRevenue != null
                        ? todayRevenue : 0) * 0.30;

        double weeklyProfit =
                (weeklyRevenue != null
                        ? weeklyRevenue : 0) * 0.30;

        double monthlyProfit =
                (monthlyRevenue != null
                        ? monthlyRevenue : 0) * 0.30;

        // =========================================
        // OTHER STATS
        // =========================================

        long completedOrders =
                orderRepository.countByPaymentStatus(
                        PaymentStatus.COMPLETED
                );

        long pendingDeliveries =
                orderRepository.countByOrderStatus(
                        OrderStatus.OUT_FOR_DELIVERY
                );

        long lowStockCount =
                productService.getLowStockCount();

        // =========================================
        // RETURN DTO
        // =========================================

        return DashboardSummaryDTO.builder()

                // TODAY
                .todayOrders(todayOrders)
                .todayRevenue(todayRevenue != null
                        ? todayRevenue : 0)
                .todayProfit(todayProfit)

                // WEEKLY
                .weeklyOrders(weeklyOrders)
                .weeklyRevenue(weeklyRevenue != null
                        ? weeklyRevenue : 0)
                .weeklyProfit(weeklyProfit)

                // MONTHLY
                .monthlyOrders(monthlyOrders)
                .monthlyRevenue(monthlyRevenue != null
                        ? monthlyRevenue : 0)
                .monthlyProfit(monthlyProfit)

                // OTHER
                .completedOrders(completedOrders)
                .pendingDeliveries(pendingDeliveries)
                .lowStockCount(lowStockCount)

                .build();
    }

    @Transactional
    public OrderResponseDTO assignDeliveryBoy(

            Long orderId,

            AssignDeliveryBoyDTO request
    ) {

        Order order =
                getOrderOrThrow(orderId);

        order.setDeliveryBoyName(
                request.getDeliveryBoyName()
        );

        order.setDeliveryBoyPhone(
                request.getDeliveryBoyPhone()
        );

        notificationService.createNotification(

                order.getUser(),

                "Delivery Partner Assigned",

                "Your delivery partner "
                        + request.getDeliveryBoyName()
                        + " has been assigned."
        );

        return mapToResponse(order);
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