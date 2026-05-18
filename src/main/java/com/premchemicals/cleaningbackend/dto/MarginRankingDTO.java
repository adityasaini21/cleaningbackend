package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MarginRankingDTO {

    private String productName;
    private Double profitPerUnit;
    private Double totalProfit;
    private Long totalQuantitySold;
}