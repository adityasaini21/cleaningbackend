package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeliveryPerformanceDTO {

    private Long totalDeliveredOrders;
    private Double averageDeliveryTimeMinutes;
    private Long deliveredToday;
    private Long pendingDeliveries;
}