package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopProductDTO {

    private String productName;
    private Long totalQuantitySold;
    private Double totalRevenue;
}