package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class TokenBlacklistServiceTest {

    private TokenBlacklistService blacklistService;

    @BeforeEach
    void setUp() {
        blacklistService = new TokenBlacklistService();
    }

    @Test
    @DisplayName("Verify valid non-expired token remains blacklisted")
    void testBlacklistValidTokenRemainsBlocked() {
        String token = "valid_jwt_token_123";
        long futureExpiration = System.currentTimeMillis() + 600_000L; // 10 mins in future

        blacklistService.blacklistToken(token, futureExpiration);

        assertTrue(blacklistService.isBlacklisted(token), "Non-expired token must be reported as blacklisted");
        assertEquals(1, blacklistService.getBlacklistSize(), "Blacklist size must be 1");
    }

    @Test
    @DisplayName("Verify expired token queried via isBlacklisted returns false and is lazily evicted")
    void testExpiredTokenOnAccessReturnsFalseAndIsRemoved() {
        String token = "expired_jwt_token_456";
        long pastExpiration = System.currentTimeMillis() - 5_000L; // 5 seconds in past

        blacklistService.blacklistToken(token, pastExpiration);
        assertEquals(1, blacklistService.getBlacklistSize(), "Blacklist initially contains the expired token");

        boolean isBlocked = blacklistService.isBlacklisted(token);

        assertFalse(isBlocked, "Expired token must not be considered blacklisted");
        assertEquals(0, blacklistService.getBlacklistSize(), "Expired token must be lazily evicted upon query");
    }

    @Test
    @DisplayName("Verify cleanupExpiredTokens removes orphaned expired entries while preserving valid tokens")
    void testCleanupExpiredTokensPurgesOrphanedExpiredEntries() {
        long now = System.currentTimeMillis();

        // 3 expired tokens that will NEVER be queried directly by clients
        blacklistService.blacklistToken("orphaned_expired_1", now - 30_000L);
        blacklistService.blacklistToken("orphaned_expired_2", now - 20_000L);
        blacklistService.blacklistToken("orphaned_expired_3", now - 10_000L);

        // 2 valid tokens that must remain blacklisted
        blacklistService.blacklistToken("active_blocked_1", now + 300_000L);
        blacklistService.blacklistToken("active_blocked_2", now + 600_000L);

        assertEquals(5, blacklistService.getBlacklistSize(), "Blacklist must initially hold 5 entries");

        int purgedCount = blacklistService.cleanupExpiredTokens();

        assertEquals(3, purgedCount, "Exactly 3 expired tokens must be purged");
        assertEquals(2, blacklistService.getBlacklistSize(), "Exactly 2 valid blacklisted tokens must remain");

        assertTrue(blacklistService.isBlacklisted("active_blocked_1"), "First valid token must still be blocked");
        assertTrue(blacklistService.isBlacklisted("active_blocked_2"), "Second valid token must still be blocked");
        assertFalse(blacklistService.isBlacklisted("orphaned_expired_1"), "Expired token must not be blocked");
    }

    @Test
    @DisplayName("Verify opportunistic cleanup runs during blacklistToken insertion when interval has elapsed")
    void testOpportunisticCleanupTriggeredWhenDue() {
        long now = System.currentTimeMillis();

        // Populate with expired tokens
        blacklistService.blacklistToken("stale_token_1", now - 50_000L);
        blacklistService.blacklistToken("stale_token_2", now - 40_000L);

        // Simulate that 15 minutes have passed since last cleanup
        AtomicLong lastCleanupTime = (AtomicLong) ReflectionTestUtils.getField(blacklistService, "lastCleanupTime");
        assertNotNull(lastCleanupTime);
        lastCleanupTime.set(now - (15 * 60 * 1000L));

        // Blacklisting a new token triggers cleanupIfDue()
        blacklistService.blacklistToken("fresh_token", now + 600_000L);

        // Stale tokens should have been purged during insertion, leaving only fresh_token
        assertEquals(1, blacklistService.getBlacklistSize(), "Only the fresh token should remain after opportunistic cleanup");
        assertTrue(blacklistService.isBlacklisted("fresh_token"), "Fresh token must be blacklisted");
    }

    @Test
    @DisplayName("Verify null and blank tokens are handled safely without errors")
    void testNullOrBlankTokenHandling() {
        assertDoesNotThrow(() -> blacklistService.blacklistToken(null, System.currentTimeMillis() + 10_000L));
        assertDoesNotThrow(() -> blacklistService.blacklistToken("", System.currentTimeMillis() + 10_000L));
        assertDoesNotThrow(() -> blacklistService.blacklistToken("   ", System.currentTimeMillis() + 10_000L));

        assertEquals(0, blacklistService.getBlacklistSize(), "Null or blank tokens must not be inserted into the blacklist");
        assertFalse(blacklistService.isBlacklisted(null), "Null token must return false");
        assertFalse(blacklistService.isBlacklisted(""), "Empty token must return false");
        assertFalse(blacklistService.isBlacklisted("   "), "Blank token must return false");
    }

    @Test
    @DisplayName("Verify concurrent insertions, queries, and cleanups execute without race conditions")
    void testConcurrentAccessSafety() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulInserts = new AtomicInteger(0);

        long now = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String validToken = "valid_" + threadId + "_" + j;
                        String expiredToken = "expired_" + threadId + "_" + j;

                        blacklistService.blacklistToken(validToken, now + 500_000L);
                        blacklistService.blacklistToken(expiredToken, now - 100_000L);
                        successfulInserts.incrementAndGet();

                        blacklistService.isBlacklisted(validToken);
                        if (j % 50 == 0) {
                            blacklistService.cleanupExpiredTokens();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent operations must complete within timeout");
        executor.shutdown();

        // Final cleanup must cleanly remove all expired tokens
        blacklistService.cleanupExpiredTokens();

        // Every thread added operationsPerThread valid tokens
        int expectedValidTokens = threadCount * operationsPerThread;
        assertEquals(expectedValidTokens, blacklistService.getBlacklistSize(),
                "All valid tokens must be safely preserved after concurrent insertions and cleanups");
    }
}
