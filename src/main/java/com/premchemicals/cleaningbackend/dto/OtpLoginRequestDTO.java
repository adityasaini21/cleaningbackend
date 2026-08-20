package com.premchemicals.cleaningbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpLoginRequestDTO {

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Enter a valid 10-digit Indian mobile number"
    )
    private String phoneNumber;

    @NotBlank(message = "OTP is required")
    private String otp;

    private String fullName; // Optional, only sent when registering a new user
}
