package com.premchemicals.cleaningbackend.dto;

import lombok.Data;

@Data
public class ReviewRequestDTO {

    private Long productId;

    private Integer rating;

    private String comment;
}