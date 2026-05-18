package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.DashboardSummaryDTO;
import com.premchemicals.cleaningbackend.dto.OrderResponseDTO;
import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    // =========================================
    // 📦 GET ORDERS BY STATUS
    // =========================================
    @GetMapping("/status/{status}")
    public List<OrderResponseDTO> getOrdersByStatus(
            @PathVariable OrderStatus status) {

        return orderService.getOrdersByStatus(status);
    }

    // =========================================
    // 📅 GET TODAY'S ORDERS
    // =========================================
    @GetMapping("/today")
    public List<OrderResponseDTO> getTodaysOrders() {
        return orderService.getTodaysOrders();
    }

    // =========================================
    // 💰 GET TODAY REVENUE
    // =========================================
    @GetMapping("/revenue/today")
    public Double getTodayRevenue() {
        return orderService.getTodayRevenue();
    }

    // =========================================
    // 💰 GET MONTHLY REVENUE
    // =========================================
    @GetMapping("/revenue/month")
    public Double getMonthlyRevenue() {
        return orderService.getMonthlyRevenue();
    }

    // =========================================
    // 🚚 GET PENDING DELIVERIES
    // =========================================
    @GetMapping("/pending-deliveries")
    public List<OrderResponseDTO> getPendingDeliveries() {
        return orderService.getPendingDeliveries();
    }

    // =========================================
// 📊 ADMIN DASHBOARD SUMMARY
// =========================================
    @GetMapping("/dashboard")
    public DashboardSummaryDTO getDashboardSummary() {
        return orderService.getDashboardSummary();
    }

    // =========================================
// 🔄 UPDATE ORDER STATUS
// =========================================
    @PutMapping("/{orderId}/status")
    public OrderResponseDTO updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status
    ) {
        return orderService.updateOrderStatus(orderId, status);
    }
}