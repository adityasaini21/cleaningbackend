package com.premchemicals.cleaningbackend.dto;

import lombok.Data;

@Data
public class LoginRequestDTO {

    private String phoneNumber;

    private String password;
}