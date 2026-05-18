package com.premchemicals.cleaningbackend.controller;

import com.premchemicals.cleaningbackend.dto.*;
import com.premchemicals.cleaningbackend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final OrderService orderService;

    // =========================================
    // 📈 TOP SELLING PRODUCTS
    // =========================================
    @GetMapping("/top-products")
    public List<TopProductDTO> getTopSellingProducts() {
        return orderService.getTopSellingProducts();
    }

    // =========================================
// 📉 WORST SELLING PRODUCTS
// =========================================
    @GetMapping("/worst-products")
    public List<TopProductDTO> getWorstSellingProducts() {
        return orderService.getWorstSellingProducts();
    }

    // =========================================
// ☠ DEAD STOCK PRODUCTS
// =========================================
    @GetMapping("/dead-stock")
    public List<DeadStockDTO> getDeadStockProducts() {
        return orderService.getDeadStockProducts();
    }
//    @GetMapping("/margin-ranking")
//    public List<MarginRankingDTO> getMarginRanking() {
//        return orderService.getMarginRanking();
//    }

    @GetMapping("/production-forecast")
    public List<ProductionForecastDTO> getProductionForecast() {
        return orderService.getProductionForecast();
    }

    @GetMapping("/restock-recommendation")
    public List<RestockRecommendationDTO> getRestockRecommendation() {
        return orderService.getRestockRecommendation();
    }
    @GetMapping("/customer-repeat")
    public CustomerRepeatDTO getCustomerRepeatAnalytics() {
        return orderService.getCustomerRepeatAnalytics();
    }

    @GetMapping("/delivery-performance")
    public DeliveryPerformanceDTO getDeliveryPerformance() {
        return orderService.getDeliveryPerformance();
    }


}