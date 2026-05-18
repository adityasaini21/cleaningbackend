package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomerRepeatDTO {

    private Long totalCustomers;
    private Long repeatCustomers;
    private Double repeatRate;
}