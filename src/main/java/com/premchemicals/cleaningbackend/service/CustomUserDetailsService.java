package com.premchemicals.cleaningbackend.service;

import com.premchemicals.cleaningbackend.model.User;
import com.premchemicals.cleaningbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String phoneNumber)
            throws UsernameNotFoundException {

        String tenDigitPhone = phoneNumber != null ? phoneNumber.replaceAll("[^0-9]", "") : "";
        if (tenDigitPhone.length() >= 10) {
            tenDigitPhone = tenDigitPhone.substring(tenDigitPhone.length() - 10);
        }

        String searchPhone = tenDigitPhone;
        User user = userRepository.findByPhoneNumber(searchPhone)
                .orElseGet(() -> userRepository.findByPhoneNumber(phoneNumber)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found")));

        return org.springframework.security.core.userdetails.User
                .builder()
                .username(user.getPhoneNumber())
                .password(user.getPassword())
                .roles(
                        user.getRole()
                                .name()
                                .replace("ROLE_", "")
                )
                .disabled(!user.isActive())
                .build();
    }
}