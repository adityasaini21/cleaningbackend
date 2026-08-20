package com.premchemicals.cleaningbackend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequestDTO {
    @NotBlank(message = "ID Token is required")
    private String idToken;
}
