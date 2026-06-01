package com.premchemicals.cleaningbackend.dto;

import com.premchemicals.cleaningbackend.model.enums.OrderStatus;
import com.premchemicals.cleaningbackend.model.enums.PaymentStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponseDTO {

    private Long orderId;

    private LocalDateTime createdAt;

    private String pincode;

    private Double totalAmount;

    private OrderStatus orderStatus;
    private PaymentStatus paymentStatus;

    private String shippingAddress;
    private String phoneNumber;

    private String deliveryBoyName;

    private String deliveryBoyPhone;

    private List<OrderItemResponse> items;

    @Data
    public static class OrderItemResponse {

        // 🔥 REQUIRED FOR REORDER FEATURE
        private Long productId;

        private String productName;

        private Integer quantity;

        private Double price;
    }
}