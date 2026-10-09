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
        try {
            userRepository.addDeletedByUserColumnIfNotExists();
            userRepository.fixNullActiveUsers();
            userRepository.fixInactiveNonDeletedUsers();
            userRepository.ensureAdminsAreActive();
            System.out.println("======================================");
            System.out.println("DATABASE USER ACTIVE STATES MIGRATED & ACTIVATED");
            System.out.println("======================================");
        } catch (Exception e) {
            System.err.println("Column migration note: " + e.getMessage());
        }

        if (adminPhone == null || adminPhone.trim().isEmpty() || !adminPhone.trim().matches("^[6-9]\\d{9}$")) {
            System.out.println("======================================");
            System.out.println("ADMIN_PHONE NOT CONFIGURED OR INVALID - SKIPPING ADMIN ACCOUNT CREATION");
            System.out.println("======================================");
            return;
        }

        String cleanPhone = adminPhone.trim();
        String pass = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword.trim() : "admin123";
        String name = (adminFullName != null && !adminFullName.isBlank()) ? adminFullName.trim() : "Admin User";

        try {
            User admin = userRepository.findByPhoneNumber(cleanPhone).orElse(null);
            if (admin != null) {
                admin.setActive(true);
                admin.setRole(Role.ROLE_ADMIN);
                userRepository.save(admin);
                System.out.println("======================================");
                System.out.println("ADMIN ACCOUNT VERIFIED & ACTIVATED: " + cleanPhone);
                System.out.println("======================================");
                return;
            }

            admin = User.builder()
                    .fullName(name)
                    .phoneNumber(cleanPhone)
                    .password(passwordEncoder.encode(pass))
                    .role(Role.ROLE_ADMIN)
                    .active(true)
                    .build();

            userRepository.save(admin);

            System.out.println("======================================");
            System.out.println("ADMIN ACCOUNT CREATED SUCCESSFULLY: " + cleanPhone);
            System.out.println("======================================");
        } catch (Exception e) {
            System.err.println("AdminInitializer Warning: " + e.getMessage());
        }
    }
}