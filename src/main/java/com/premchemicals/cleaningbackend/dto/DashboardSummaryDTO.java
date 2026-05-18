package com.premchemicals.cleaningbackend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardSummaryDTO {

    private long todayOrders;
    private long completedOrders;
    private long pendingDeliveries;

    private double todayRevenue;
    private double monthlyRevenue;

    // 🔥 NEW
    private long lowStockCount;
}