package com.premchemicals.cleaningbackend.dto;

import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponseDTO {

    private Long orderId;

    // 🔥 MAIN DATE FIELD (USE THIS ONLY)
    private LocalDateTime createdAt;

    private Double totalAmount;

    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;

    private String shippingAddress;
    private String phoneNumber;

    private List<OrderItemResponse> items;

    @Data
    public static class OrderItemResponse {
        private String productName;
        private Integer quantity;
        private Double price;
    }
}