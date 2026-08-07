package com.premchemicals.cleaningbackend.dto;

import lombok.Data;

@Data
public class UserResponseDTO {

    private Long id;

    private String fullName;

    private String phoneNumber;

    private String email;

    private boolean active;
}