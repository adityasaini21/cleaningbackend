package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class JwtSecretValidationTest {

    @Test
    @DisplayName("Fail startup when JWT secret is null or empty")
    void testFailStartupWhenSecretIsEmpty() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "");
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                jwtUtil::init
        );
        assertTrue(exception.getMessage().contains("FATAL CONFIGURATION ERROR"), "Exception message must indicate fatal configuration error");
    }

    @Test
    @DisplayName("Fail startup when JWT secret is shorter than 32 characters")
    void testFailStartupWhenSecretIsTooShort() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "short_secret_12345");
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                jwtUtil::init
        );
        assertTrue(exception.getMessage().contains("shorter than 32 characters"), "Exception message must state length requirement");
    }

    @Test
    @DisplayName("Succeed startup when JWT secret is 32 characters or longer")
    void testSucceedStartupWhenSecretIsValid() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "a_very_secure_secret_key_that_is_at_least_32_characters_long");
        assertDoesNotThrow(jwtUtil::init, "Valid 32+ char secret must init without throwing exceptions");
    }
}
