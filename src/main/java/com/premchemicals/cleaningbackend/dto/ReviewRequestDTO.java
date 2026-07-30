package com.premchemicals.cleaningbackend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReviewRequestDTO {

    private Long productId;

    private Integer rating;

    private String comment;

    // ======================================
    // CLOUDINARY IMAGE URLS
    // ======================================

    private List<String> imageUrls;
}