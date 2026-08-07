package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.dto.ResetPasswordRequestDTO;
import com.premchemicals.cleaningbackend.dto.UserResponseDTO;
import com.premchemicals.cleaningbackend.mapper.UserMapper;
import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    public List<UserResponseDTO> searchUsers(String query) {

        List<User> users =
                userRepository.searchUsers(
                        Role.ROLE_USER,
                        query
                );

        return users.stream()
                .map(userMapper::toDTO)
                .toList();
    }

    public void resetPassword(
            Long userId,
            ResetPasswordRequestDTO request
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        userRepository.save(user);
    }
}