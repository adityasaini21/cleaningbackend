package com.premchemicals.cleaningbackend.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {
    private String username;
    private String password;
}