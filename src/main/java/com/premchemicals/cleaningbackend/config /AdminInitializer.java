package com.premchemicals.cleaningbackend.config;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.phone}")
    private String adminPhone;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.full-name}")
    private String adminFullName;

    @Override
    public void run(String... args) {

        if (userRepository.existsByPhoneNumber(adminPhone)) {
            return;
        }

        User admin = User.builder()
                .fullName(adminFullName)
                .phoneNumber(adminPhone)
                .password(passwordEncoder.encode(adminPassword))
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build();

        userRepository.save(admin);

        System.out.println("======================================");
        System.out.println("ADMIN ACCOUNT CREATED SUCCESSFULLY");
        System.out.println("======================================");
    }
}