package com.premchemicals.cleaningbackend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewResponseDTO {

    private Long id;

    private String username;

    private Integer rating;

    private String comment;

    private LocalDateTime createdAt;
}