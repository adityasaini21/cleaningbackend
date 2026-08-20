package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OtpLoginResponseDTO {
    private String token;
    private boolean isNewUser;
    private String fullName;
}
