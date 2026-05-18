package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RestockRecommendationDTO {

    private String productName;
    private Integer currentStock;
    private Double forecastNext7Days;
    private Integer recommendedProduction;
}