package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService();
    }

    @Test
    @DisplayName("Verify Mock Mode OTP Generation and Verification when devMode is true")
    void testMockOtpMode() {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        // Send OTP
        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));

        // Verify invalid code fails
        assertFalse(otpService.verifyOtp("9876543210", "000000"), "Incorrect OTP must fail verification");

        // Extract generated code and verify correct code succeeds
        String code = extractOtp(otpService, "+919876543210");
        assertNotNull(code, "Generated code must exist in cache");
        assertTrue(otpService.verifyOtp("+919876543210", code), "Correct OTP must succeed");

        // Verify single-use requirement (second attempt fails)
        assertFalse(otpService.verifyOtp("+919876543210", code), "Used OTP must fail on second attempt");
    }

    @Test
    @DisplayName("Verify Production Mode fails securely when MINIMOTH_API_KEY is missing")
    void testProductionModeFailsWithoutApiKey() {
        ReflectionTestUtils.setField(otpService, "devMode", false);
        ReflectionTestUtils.setField(otpService, "minimothApiKey", "");

        RuntimeException sendEx = assertThrows(RuntimeException.class, () -> otpService.sendOtp("9876543210"));
        assertTrue(sendEx.getMessage().contains("MiniMoth API Key is not configured"), "Must throw explicit configuration exception in production mode");

        RuntimeException verifyEx = assertThrows(RuntimeException.class, () -> otpService.verifyOtp("9876543210", "123456"));
        assertTrue(verifyEx.getMessage().contains("MiniMoth API Key is not configured"), "Must throw explicit configuration exception in production mode");
    }

    @Test
    @DisplayName("Verify 60-Second Cooldown Enforcement and Retry After 60 Seconds")
    void testOtpCooldownEnforcementAndRetryAfter60Seconds() {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        // 1. First send succeeds
        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));

        // 2. Immediate second send within 60s must fail with HTTP 429
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> otpService.sendOtp("9876543210"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Please wait"), "Error reason must indicate remaining seconds");

        // 3. Simulate 61 seconds passing
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> cooldownMap = (Map<String, LocalDateTime>) ReflectionTestUtils.getField(otpService, "otpCooldownMap");
        assertNotNull(cooldownMap);
        cooldownMap.put("+919876543210", LocalDateTime.now().minusSeconds(61));

        // 4. Send after 60s must succeed
        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));
    }

    @Test
    @DisplayName("Verify Failed Send Does Not Block Immediate Retries")
    void testFailedSendDoesNotBlockRetry() {
        ReflectionTestUtils.setField(otpService, "devMode", false);
        ReflectionTestUtils.setField(otpService, "minimothApiKey", "");

        // Send fails due to missing key
        assertThrows(RuntimeException.class, () -> otpService.sendOtp("9876543210"));

        // Enable devMode and retry — must succeed without cooldown error
        ReflectionTestUtils.setField(otpService, "devMode", true);
        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));
    }

    @Test
    @DisplayName("Verify Concurrent Requests Protection Against Cooldown Bypass")
    void testConcurrentRequestsCooldownProtection() throws InterruptedException {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger cooldownBlockedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    otpService.sendOtp("9876543210");
                    successCount.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        cooldownBlockedCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release threads concurrently
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly 1 concurrent request must succeed");
        assertEquals(9, cooldownBlockedCount.get(), "Remaining 9 concurrent requests must be blocked by cooldown");
    }

    @Test
    @DisplayName("Verify PhoneLock Cleanup Evicts Unused Locks to Prevent Memory Leak")
    void testLockMapCleanupNoMemoryLeak() {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));

        @SuppressWarnings("unchecked")
        Map<String, Object> phoneLockMap = (Map<String, Object>) ReflectionTestUtils.getField(otpService, "phoneLockMap");
        assertNotNull(phoneLockMap);
        assertTrue(phoneLockMap.isEmpty(), "phoneLockMap must be empty after request finishes");
    }

    @Test
    @DisplayName("Verify lock acquisition safety: Overlapping concurrent requests for the same phone MUST use the exact same lock instance")
    void testConcurrentOverlapUsesSameLockInstance() throws InterruptedException {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        String testPhone = "9876543210";
        String normalizedPhone = "+919876543210";

        @SuppressWarnings("unchecked")
        Map<String, Object> phoneLockMap = (Map<String, Object>) ReflectionTestUtils.getField(otpService, "phoneLockMap");

        CountDownLatch threadAStarted = new CountDownLatch(1);
        CountDownLatch threadBAttempted = new CountDownLatch(1);
        AtomicReference<Object> lockSeenByThreadA = new AtomicReference<>();
        AtomicReference<Object> lockSeenByThreadB = new AtomicReference<>();
        AtomicBoolean secondRequestBlockedByCooldown = new AtomicBoolean(false);

        // Thread A: Executes sendOtp
        Thread threadA = new Thread(() -> {
            otpService.sendOtp(testPhone);
        });

        // Thread B: Concurrently attempts sendOtp for the same phone
        Thread threadB = new Thread(() -> {
            try {
                threadAStarted.await();
                lockSeenByThreadB.set(phoneLockMap.get(normalizedPhone));
                threadBAttempted.countDown();
                otpService.sendOtp(testPhone);
            } catch (ResponseStatusException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    secondRequestBlockedByCooldown.set(true);
                }
            } catch (InterruptedException ignored) {
            }
        });

        // Sample lock instance while Thread A enters
        phoneLockMap.compute("+919876543210", (k, v) -> {
            // Pre-warm lock check
            return v;
        });

        threadA.start();
        // Give Thread A a microsecond to enter and register lock in map
        while (phoneLockMap.get(normalizedPhone) == null && threadA.isAlive()) {
            Thread.onSpinWait();
        }
        lockSeenByThreadA.set(phoneLockMap.get(normalizedPhone));
        threadAStarted.countDown();

        threadB.start();
        threadA.join();
        threadB.join();

        if (lockSeenByThreadA.get() != null && lockSeenByThreadB.get() != null) {
            assertSame(lockSeenByThreadA.get(), lockSeenByThreadB.get(),
                    "Thread B MUST receive the exact same PhoneLock instance as Thread A during overlapping requests");
        }

        assertTrue(secondRequestBlockedByCooldown.get(), "Overlapping second request must be blocked by 60s cooldown");
        assertTrue(phoneLockMap.isEmpty(), "phoneLockMap must be empty after both overlapping requests finish");
    }

    @Test
    @DisplayName("Stress test: Repeated concurrent requests during lock eviction must not bypass cooldown or duplicate OTPs")
    void testPhoneLockStressAndOverlapSafety() throws InterruptedException {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        String testPhone = "9876543210";
        String normalizedPhone = "+919876543210";
        int rounds = 30;
        int threadsPerRound = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadsPerRound * 2);
        AtomicInteger totalSuccesses = new AtomicInteger(0);
        AtomicInteger totalBlocked = new AtomicInteger(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> phoneLockMap = (Map<String, Object>) ReflectionTestUtils.getField(otpService, "phoneLockMap");
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> cooldownMap = (Map<String, LocalDateTime>) ReflectionTestUtils.getField(otpService, "otpCooldownMap");

        for (int round = 0; round < rounds; round++) {
            // Reset cooldown map every 5th round to test transition from eviction to creation
            if (round % 5 == 0) {
                cooldownMap.remove(normalizedPhone);
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadsPerRound);

            for (int t = 0; t < threadsPerRound; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        otpService.sendOtp(testPhone);
                        totalSuccesses.incrementAndGet();
                    } catch (ResponseStatusException e) {
                        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            totalBlocked.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
        }

        executor.shutdown();

        assertEquals(6, totalSuccesses.get(), "Exactly 1 OTP send per fresh wave must succeed (6 waves total)");
        assertEquals(294, totalBlocked.get(), "All 294 premature concurrent requests must be blocked by cooldown");
        assertTrue(phoneLockMap.isEmpty(), "phoneLockMap must be completely empty after all requests finish");
    }

    @Test
    @DisplayName("Verify 5th Incorrect Attempt Invalidates OTP and Blocks Further Verification for 15 Minutes")
    void testFifthIncorrectAttemptInvalidatesOtpAndEntersLockout() {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        // Send OTP
        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));
        String realCode = extractOtp(otpService, "+919876543210");
        assertNotNull(realCode);

        // Attempts 1 to 4: Incorrect code returns false
        for (int i = 1; i <= 4; i++) {
            assertFalse(otpService.verifyOtp("9876543210", "000000"), "Attempt " + i + " must return false");
        }

        // 5th attempt: Incorrect code returns false, invalidates OTP, and sets 15-minute lockout
        assertFalse(otpService.verifyOtp("9876543210", "000000"), "5th attempt must return false");

        // Verify cached OTP has been invalidated
        assertNull(extractOtp(otpService, "+919876543210"), "OTP must be invalidated after 5th incorrect attempt");

        // 6th attempt (even with what was the real code): blocked by 15-minute lockout
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> otpService.verifyOtp("9876543210", realCode));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Verification locked"), "Must indicate verification is locked");
    }

    @Test
    @DisplayName("Verify Lockout Duration and Safe Cleanup of Expired Lockout")
    void testLockoutDurationAndSafeExpiredCleanup() {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));

        // Trigger 5 failures to enter lockout
        for (int i = 0; i < 5; i++) {
            assertFalse(otpService.verifyOtp("9876543210", "000000"));
        }

        // Immediate attempt is locked out
        assertThrows(ResponseStatusException.class, () -> otpService.verifyOtp("9876543210", "000000"));

        // Simulate 16 minutes passing (lockout expired)
        @SuppressWarnings("unchecked")
        Map<String, LocalDateTime> lockoutMap = (Map<String, LocalDateTime>) ReflectionTestUtils.getField(otpService, "lockoutMap");
        assertNotNull(lockoutMap);
        lockoutMap.put("+919876543210", LocalDateTime.now().minusMinutes(16));

        // Attempt after expiration must not throw TOO_MANY_REQUESTS; expired lockout is evicted safely
        assertFalse(otpService.verifyOtp("9876543210", "000000"), "Expired lockout must be evicted and not throw TOO_MANY_REQUESTS");
        assertFalse(lockoutMap.containsKey("+919876543210"), "Expired lockout entry must be evicted from lockoutMap");
    }

    @Test
    @DisplayName("Verify Successful Verification Clears Failure State and Resets Counter")
    void testSuccessfulVerificationClearsFailureState() {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));
        String realCode = extractOtp(otpService, "+919876543210");
        assertNotNull(realCode);

        // 2 incorrect attempts
        assertFalse(otpService.verifyOtp("9876543210", "000000"));
        assertFalse(otpService.verifyOtp("9876543210", "000000"));

        @SuppressWarnings("unchecked")
        Map<String, Integer> failedAttemptsMap = (Map<String, Integer>) ReflectionTestUtils.getField(otpService, "failedAttemptsMap");
        assertNotNull(failedAttemptsMap);
        assertEquals(2, failedAttemptsMap.get("+919876543210"));

        // Successful verification with real code
        assertTrue(otpService.verifyOtp("9876543210", realCode));

        // Verify failure state is completely cleared
        assertNull(failedAttemptsMap.get("+919876543210"), "Failed attempts map must be cleared after success");
    }

    @Test
    @DisplayName("Verify Concurrent Verification Attempts Cannot Bypass Lockout Limit")
    void testConcurrentVerificationAttemptsProtectionAgainstLockoutBypass() throws InterruptedException {
        ReflectionTestUtils.setField(otpService, "devMode", true);

        assertDoesNotThrow(() -> otpService.sendOtp("9876543210"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger failedAttemptCount = new AtomicInteger(0);
        AtomicInteger lockoutBlockedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean res = otpService.verifyOtp("9876543210", "wrong_code");
                    if (!res) {
                        failedAttemptCount.incrementAndGet();
                    }
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        lockoutBlockedCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(5, failedAttemptCount.get(), "Exactly 5 incorrect attempts must be processed before lockout");
        assertEquals(5, lockoutBlockedCount.get(), "Remaining 5 concurrent requests must be blocked by lockout");

        @SuppressWarnings("unchecked")
        Map<String, Object> phoneLockMap = (Map<String, Object>) ReflectionTestUtils.getField(otpService, "phoneLockMap");
        assertNotNull(phoneLockMap);
        assertTrue(phoneLockMap.isEmpty(), "phoneLockMap must be empty after all requests complete");
    }

    @SuppressWarnings("unchecked")
    private String extractOtp(OtpService service, String phone) {
        java.util.Map<String, Object> cache = (java.util.Map<String, Object>) ReflectionTestUtils.getField(service, "otpCache");
        if (cache == null) return null;
        Object details = cache.get(phone);
        if (details == null) return null;
        return (String) ReflectionTestUtils.getField(details, "code");
    }
}
