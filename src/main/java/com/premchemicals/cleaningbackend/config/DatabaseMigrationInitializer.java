package com.premchemicals.cleaningbackend.config;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.model.enums.Role;
import com.premchemicals.cleaningbackend.repository.OrderRepository;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
@RequiredArgsConstructor
public class DatabaseMigrationInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

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

        // 4. Standardize phone numbers to clean 10-digit Indian mobile format and resolve duplicates
        try {
            standardizePhoneNumbers();
        } catch (Exception e) {
            System.err.println("Database migration note (phone standardization): " + e.getMessage());
        }
    }

    private void standardizePhoneNumbers() {
        List<User> allUsers = userRepository.findAll();
        Map<String, List<User>> phoneMap = new HashMap<>();

        for (User u : allUsers) {
            String p = u.getPhoneNumber();
            if (p != null && !p.startsWith("DELETED_") && !p.startsWith("MERGED_")) {
                String clean = p.replaceAll("[^0-9]", "");
                if (clean.length() >= 10) {
                    clean = clean.substring(clean.length() - 10);
                }
                if (clean.matches("^[6-9]\\d{9}$")) {
                    phoneMap.computeIfAbsent(clean, k -> new ArrayList<>()).add(u);
                }
            }
        }

        int updatedCount = 0;
        int duplicateMergedCount = 0;

        for (Map.Entry<String, List<User>> entry : phoneMap.entrySet()) {
            String standardPhone = entry.getKey();
            List<User> list = entry.getValue();

            if (list.size() == 1) {
                User u = list.get(0);
                if (!standardPhone.equals(u.getPhoneNumber())) {
                    u.setPhoneNumber(standardPhone);
                    userRepository.save(u);
                    updatedCount++;
                }
            } else if (list.size() > 1) {
                // Priority: Admin > User with orders > Older ID
                User primary = list.stream()
                        .filter(u -> u.getRole() == Role.ROLE_ADMIN)
                        .findFirst()
                        .orElse(null);

                if (primary == null) {
                    primary = list.stream()
                            .filter(u -> orderRepository.existsByUser(u))
                            .findFirst()
                            .orElse(list.get(0));
                }

                // Standardize primary
                primary.setPhoneNumber(standardPhone);
                userRepository.save(primary);

                // Safely detach secondary duplicate users
                for (User dupe : list) {
                    if (!dupe.getId().equals(primary.getId())) {
                        dupe.setActive(false);
                        dupe.setDeletedByUser(true);
                        dupe.setPhoneNumber("DELETED_DUPE_" + dupe.getId() + "_" + System.currentTimeMillis());
                        userRepository.save(dupe);
                        duplicateMergedCount++;
                    }
                }
            }
        }

        if (updatedCount > 0 || duplicateMergedCount > 0) {
            System.out.println("Database migration: Phone numbers standardized. " +
                    updatedCount + " updated to 10 digits, " +
                    duplicateMergedCount + " duplicates resolved.");
        }
    }
}
