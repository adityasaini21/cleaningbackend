package com.premchemicals.cleaningbackend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryDTO {

    // =========================================
    // TODAY
    // =========================================

    private long todayOrders;

    private long completedOrders;

    private long pendingDeliveries;

    private double todayRevenue;

    private double todayProfit;

    // =========================================
    // WEEKLY
    // =========================================

    private long weeklyOrders;

    private double weeklyRevenue;

    private double weeklyProfit;

    // =========================================
    // MONTHLY
    // =========================================

    private long monthlyOrders;

    private double monthlyRevenue;

    private double monthlyProfit;

    // =========================================
    // INVENTORY
    // =========================================

    private long lowStockCount;
}