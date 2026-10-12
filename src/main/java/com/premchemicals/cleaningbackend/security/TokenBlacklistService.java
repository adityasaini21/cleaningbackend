package com.premchemicals.cleaningbackend.security;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TokenBlacklistService {

    private final ConcurrentHashMap<String, Long> blacklistedTokens = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupTime = new AtomicLong(System.currentTimeMillis());

    // Minimum interval between opportunistic batch sweeps (10 minutes)
    private static final long CLEANUP_INTERVAL_MS = 10 * 60 * 1000L;

    public void blacklistToken(String token, long expirationTimeMs) {
        if (token == null || token.isBlank()) {
            return;
        }
        blacklistedTokens.put(token, expirationTimeMs);
        cleanupIfDue();
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
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

    /**
     * Removes all expired blacklist entries from the in-memory store.
     * Thread-safe and non-blocking for concurrent readers/writers.
     *
     * @return the number of expired tokens purged
     */
    public int cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        int beforeSize = blacklistedTokens.size();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
        lastCleanupTime.set(now);
        return beforeSize - blacklistedTokens.size();
    }

    /**
     * Opportunistically triggers cleanup during token insertion if the cleanup interval has elapsed.
     * Uses atomic compareAndSet to ensure only one thread performs cleanup concurrently.
     */
    private void cleanupIfDue() {
        long now = System.currentTimeMillis();
        long last = lastCleanupTime.get();
        if (now - last >= CLEANUP_INTERVAL_MS) {
            if (lastCleanupTime.compareAndSet(last, now)) {
                blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() <= now);
            }
        }
    }

    /**
     * Returns the current number of blacklisted tokens (useful for testing and monitoring).
     */
    public int getBlacklistSize() {
        return blacklistedTokens.size();
    }
}
