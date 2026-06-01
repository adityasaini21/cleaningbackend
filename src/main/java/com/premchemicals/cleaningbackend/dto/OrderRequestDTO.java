package com.premchemicals.cleaningbackend.dto;

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



    @NotEmpty
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {

        @NotNull
        private Long productId;

        @NotNull
        private Integer quantity;
    }
}