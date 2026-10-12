package com.premchemicals.cleaningbackend;

import com.premchemicals.cleaningbackend.security.RateLimitingFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
    }

    @Test
    @DisplayName("Verify /auth/otp/verify Enforces Strict 10 Requests Per Minute Limit Per IP")
    void testOtpVerifyRateLimit10PerMinute() throws Exception {
        String testIp = "192.168.1.100";

        // First 10 requests must succeed (HTTP 200 via mock filter chain)
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/otp/verify");
            request.setRemoteAddr(testIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), "Request " + i + " must pass rate limiter");
        }

        // 11th request must be blocked with HTTP 429
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/otp/verify");
        request.setRemoteAddr(testIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus(), "11th request to /auth/otp/verify must be rate limited with HTTP 429");
        assertTrue(response.getContentAsString().contains("Too many OTP verification attempts"),
                "Response body must contain specific OTP verification error message");
    }

    @Test
    @DisplayName("Verify /auth/otp/login Enforces Strict 10 Requests Per Minute Limit Per IP")
    void testOtpLoginRateLimit10PerMinute() throws Exception {
        String testIp = "192.168.1.101";

        // First 10 requests pass
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/otp/login");
            request.setRemoteAddr(testIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus());
        }

        // 11th request blocked
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/otp/login");
        request.setRemoteAddr(testIp);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus(), "11th request to /auth/otp/login must be rate limited with HTTP 429");
        assertTrue(response.getContentAsString().contains("Too many OTP verification attempts"));
    }

    @Test
    @DisplayName("Verify /auth/otp/send Uses General 60 Requests Per Minute Bucket and Is Not Blocked by 10-Request Verify Limit")
    void testOtpSendUsesGeneralRateLimitBucket() throws Exception {
        String testIp = "192.168.1.102";

        // Send 15 requests to /auth/otp/send — all must pass because limit is 60 req/min
        for (int i = 1; i <= 15; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/otp/send");
            request.setRemoteAddr(testIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), "Request " + i + " to /auth/otp/send must pass general rate limiter");
        }
    }

    @Test
    @DisplayName("Verify Different IPs Have Independent Rate Limiting Buckets")
    void testDifferentIpsHaveIndependentBuckets() throws Exception {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        // Exhaust limit for IP1 on /auth/otp/verify
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/otp/verify");
            request.setRemoteAddr(ip1);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        // 11th request for IP1 is blocked
        MockHttpServletRequest requestBlocked = new MockHttpServletRequest("POST", "/auth/otp/verify");
        requestBlocked.setRemoteAddr(ip1);
        MockHttpServletResponse responseBlocked = new MockHttpServletResponse();
        filter.doFilter(requestBlocked, responseBlocked, new MockFilterChain());
        assertEquals(429, responseBlocked.getStatus());

        // First request for IP2 must pass without being affected by IP1
        MockHttpServletRequest requestAllowed = new MockHttpServletRequest("POST", "/auth/otp/verify");
        requestAllowed.setRemoteAddr(ip2);
        MockHttpServletResponse responseAllowed = new MockHttpServletResponse();
        filter.doFilter(requestAllowed, responseAllowed, new MockFilterChain());
        assertEquals(200, responseAllowed.getStatus(), "Independent IP must not be blocked");
    }

    @Test
    @DisplayName("Verify /auth/check-phone Enforces Rate Limiting Bucket (30 Requests/min)")
    void testCheckPhoneRateLimiting() throws Exception {
        String testIp = "192.168.1.150";

        // First 30 requests to /auth/check-phone must succeed
        for (int i = 1; i <= 30; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/check-phone");
            request.setParameter("phoneNumber", "9876543210");
            request.setRemoteAddr(testIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), "Request " + i + " to /auth/check-phone must pass");
        }

        // 31st request must be rate limited with HTTP 429
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("GET", "/auth/check-phone");
        blockedRequest.setParameter("phoneNumber", "9876543210");
        blockedRequest.setRemoteAddr(testIp);
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(blockedRequest, blockedResponse, chain);

        assertEquals(429, blockedResponse.getStatus(), "31st request to /auth/check-phone must return 429");
        assertTrue(blockedResponse.getContentAsString().contains("Too many account check attempts"));
    }
}
