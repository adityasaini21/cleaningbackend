package com.premchemicals.cleaningbackend.mapper;

import com.premchemicals.cleaningbackend.dto.UserResponseDTO;
import com.premchemicals.cleaningbackend.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponseDTO toDTO(User user) {

        UserResponseDTO dto = new UserResponseDTO();

        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setEmail(user.getEmail());
        dto.setActive(user.isActive());

        return dto;
    }
}