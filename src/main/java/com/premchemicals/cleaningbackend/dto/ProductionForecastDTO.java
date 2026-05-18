package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProductionForecastDTO {

    private String productName;
    private Long last30DaysSold;
    private Double dailyAverage;
    private Double forecastNext7Days;
}