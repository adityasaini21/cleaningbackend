package com.premchemicals.cleaningbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequestDTO {

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100)
    private String newPassword;
}