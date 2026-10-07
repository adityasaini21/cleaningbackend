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
        if (adminPhone == null || adminPhone.isBlank()) return;

        User admin = userRepository.findByPhoneNumber(adminPhone).orElse(null);
        if (admin != null) {
            admin.setActive(true);
            admin.setRole(Role.ROLE_ADMIN);
            userRepository.save(admin);
            System.out.println("======================================");
            System.out.println("ADMIN ACCOUNT VERIFIED & ACTIVATED");
            System.out.println("======================================");
            return;
        }

        admin = User.builder()
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