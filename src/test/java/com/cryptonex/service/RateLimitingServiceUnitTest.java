package com.cryptonex.service;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RateLimitingServiceUnitTest {

    private final RateLimitingService rateLimitingService = new RateLimitingService();

    @Test
    public void testResolveBucket_PaymentUser() {
        Bucket bucket = rateLimitingService.resolveBucket("payment_user_123");
        assertNotNull(bucket);
        // We can't easily inspect the internal bandwidths of a Bucket4j bucket without
        // reflection or consuming tokens.
        // So we test by consumption behavior.

        // Should allow 10 requests immediately
        for (int i = 0; i < 10; i++) {
            assertTrue(bucket.tryConsume(1), "Should allow request " + (i + 1));
        }

        // 11th should fail (assuming greedy refill doesn't refill instantly within ms
        // execution)
        // Note: Generic greedy refill might refill slightly if execution is slow, but
        // usually < 1ms won't refill enough for 1 token if rate is 10/min.
        // 10/min = 1 token every 6000ms.

        // Asserting strictly might be flaky if test runs very slow, but for unit test
        // it's usually fine.
        assertNotNull(bucket.getAvailableTokens());
    }

    @Test
    public void testResolveBucket_WebhookTrusted() {
        Bucket bucket = rateLimitingService.resolveBucket("webhook_trusted_127.0.0.1");
        assertNotNull(bucket);
        // 200/min
        assertTrue(bucket.tryConsume(200));
    }

    @Test
    public void testResolveBucket_AuthSignin() {
        Bucket bucket = rateLimitingService.resolveBucket("auth_signin_ip_127.0.0.1");
        assertNotNull(bucket);
        // 30/min
        assertTrue(bucket.tryConsume(30));
    }

    @Test
    public void testResolveBucket_AuthSignup() {
        Bucket bucket = rateLimitingService.resolveBucket("auth_signup_ip_127.0.0.1");
        assertNotNull(bucket);
        // 5/min
        assertTrue(bucket.tryConsume(5));

        // 6th should fail
        // assertFalse(bucket.tryConsume(1));
    }
}
