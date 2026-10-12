package com.premchemicals.cleaningbackend.config;

import com.premchemicals.cleaningbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class DatabaseMigrationInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        // 1. Widen users.phone_number to VARCHAR(50) for account-deletion placeholders
        try {
            userRepository.widenPhoneNumberColumn();
            System.out.println("Database migration: users.phone_number successfully widened to VARCHAR(50)");
        } catch (Exception e) {
            System.err.println("Database migration note (widen phone_number): " + e.getMessage());
        }

        // 2. Add deleted_by_user column if not exists
        try {
            userRepository.addDeletedByUserColumnIfNotExists();
        } catch (Exception e) {
            System.err.println("Database migration note (deleted_by_user column): " + e.getMessage());
        }

        // 3. Add password_changed_at column if not exists
        try {
            userRepository.addPasswordChangedAtColumnIfNotExists();
        } catch (Exception e) {
            System.err.println("Database migration note (password_changed_at column): " + e.getMessage());
        }
    }
}
