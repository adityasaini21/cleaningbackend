package com.premchemicals.cleaningbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginResponseDTO {
    private boolean isNewUser;
    private String token;
    private String email;
    private String fullName;
}
