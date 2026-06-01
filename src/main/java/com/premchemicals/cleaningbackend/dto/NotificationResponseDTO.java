package com.premchemicals.cleaningbackend.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDTO {

    private Long id;

    private String title;

    private String message;

    private boolean read;

    private LocalDateTime createdAt;
}