package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.OrderRequestDTO;
import com.premchemicals.cleaningbackend.dto.OrderResponseDTO;
import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.service.OrderService;
import com.premchemicals.cleaningbackend.service.PaymentService;
import com.razorpay.RazorpayException;
import com.premchemicals.cleaningbackend.dto.AssignDeliveryBoyDTO;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    private final PaymentService paymentService;

    // =====================================================
    // PLACE ORDER
    // =====================================================

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public OrderResponseDTO placeOrder(
            @Valid @RequestBody OrderRequestDTO request) {

        return orderService.placeOrder(request);
    }

    @PutMapping("/{orderId}/assign-delivery")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public OrderResponseDTO assignDeliveryBoy(

            @PathVariable Long orderId,

            @RequestBody AssignDeliveryBoyDTO request
    ) {

        return orderService.assignDeliveryBoy(
                orderId,
                request
        );
    }

    // =====================================================
    // CREATE RAZORPAY PAYMENT
    // =====================================================

    @PostMapping("/{orderId}/pay")
    @PreAuthorize("isAuthenticated()")
    public String createPayment(
            @PathVariable Long orderId)
            throws RazorpayException {

        return paymentService
                .createRazorpayOrder(orderId);
    }

    // =====================================================
    // GET MY ORDERS
    // =====================================================

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<OrderResponseDTO> getMyOrders() {

        return orderService.getMyOrders();
    }

    // =====================================================
    // GET ORDER BY ID
    // =====================================================

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public OrderResponseDTO getOrderById(
            @PathVariable Long orderId) {

        return orderService
                .getOrderById(orderId);
    }

    // =====================================================
    // ADMIN: GET ALL ORDERS
    // =====================================================

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<OrderResponseDTO> getAllOrders() {

        return orderService.getAllOrders();
    }

    // =====================================================
    // ADMIN: UPDATE STATUS
    // =====================================================

    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public OrderResponseDTO updateOrderStatus(

            @PathVariable Long orderId,

            @RequestParam String status) {

        // 🔥 IMPORTANT FIX
        OrderStatus orderStatus =
                OrderStatus.valueOf(
                        status.toUpperCase()
                );

        return orderService.updateOrderStatus(
                orderId,
                orderStatus
        );
    }

    // =====================================================
    // CANCEL ORDER
    // =====================================================

    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public OrderResponseDTO cancelOrder(
            @PathVariable Long orderId) {

        return orderService.cancelOrder(orderId);
    }
}