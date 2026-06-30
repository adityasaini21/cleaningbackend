package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryDTO {

    private Double averageRating;

    private Integer reviewCount;

    private Integer fiveStar;

    private Integer fourStar;

    private Integer threeStar;

    private Integer twoStar;

    private Integer oneStar;

    private List<ReviewResponseDTO> reviews;
}
