package com.premchemicals.cleaningbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequestDTO {

    @NotBlank
    private String shippingAddress;

    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String pincode;

    @NotBlank  // ✅ NEW FIELD (MANDATORY)
    private String paymentMethod;  // "ONLINE" or "COD"

    private Double latitude;
    private Double longitude;

    @NotEmpty
    private List<@Valid OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {

        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;
    }
}