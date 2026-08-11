package com.premchemicals.cleaningbackend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserProfileDTO {
    private String fullName;
    private String email;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String landmark;
}
