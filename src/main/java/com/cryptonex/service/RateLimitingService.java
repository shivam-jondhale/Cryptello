package com.cryptonex.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, this::newBucket);
    }

    private Bucket newBucket(String key) {
        if (key.startsWith("payment_user_")) {
            // Payment (User): 10/min + 100/day
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                    .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofDays(1))))
                    .build();
        } else if (key.startsWith("payment_ip_")) {
            // Payment (IP - Unauth): 20/min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1))))
                    .build();
        } else if (key.startsWith("webhook_trusted_")) {
            // Webhook (Trusted - Signed): 200/min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(200, Refill.greedy(200, Duration.ofMinutes(1))))
                    .build();
        } else if (key.startsWith("webhook_untrusted_")) {
            // Webhook (Untrusted - No/Invalid Signature): 10/min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                    .build();
        } else if (key.startsWith("auth_signin_ip_")) {
            // Auth Signin (IP): 30/min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1))))
                    .build();
        } else if (key.startsWith("auth_signin_email_")) {
            // Auth Signin (Email): 10 per 15 min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(15))))
                    .build();
        } else if (key.startsWith("auth_signup_ip_")) {
            // Auth Signup (IP): 5/min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
                    .build();
        } else if (key.startsWith("global_ip_")) {
            // Global IP Limit: 100/min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
                    .build();
        } else {
            // Default fallback (should not be hit if logic is correct): 50/min
            return Bucket4j.builder()
                    .addLimit(Bandwidth.classic(50, Refill.greedy(50, Duration.ofMinutes(1))))
                    .build();
        }
    }
}
