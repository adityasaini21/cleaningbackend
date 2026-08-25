package com.premchemicals.cleaningbackend.security;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    private final ConcurrentHashMap<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    public void blacklistToken(String token, long expirationTimeMs) {
        blacklistedTokens.put(token, expirationTimeMs);
    }

    public boolean isBlacklisted(String token) {
        Long expiration = blacklistedTokens.get(token);
        if (expiration == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiration) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }
}
