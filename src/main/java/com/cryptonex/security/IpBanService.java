package com.cryptonex.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class IpBanService {

    private static final String BAN_PREFIX = "ip_ban:";
    private static final String VIOLATION_PREFIX = "ip_violation:";
    private static final int MAX_VIOLATIONS = 5;
    private static final long BAN_DURATION_HOURS = 1;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean isBanned(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BAN_PREFIX + ip));
    }

    public void recordViolation(String ip) {
        String violationKey = VIOLATION_PREFIX + ip;
        Long violations = redisTemplate.opsForValue().increment(violationKey);

        if (violations != null && violations == 1) {
            redisTemplate.expire(violationKey, 10, TimeUnit.MINUTES);
        }

        if (violations != null && violations >= MAX_VIOLATIONS) {
            banIp(ip);
        }
    }

    private void banIp(String ip) {
        redisTemplate.opsForValue().set(BAN_PREFIX + ip, "BANNED", Duration.ofHours(BAN_DURATION_HOURS));
        redisTemplate.delete(VIOLATION_PREFIX + ip); // Clear violations
        System.out.println("IP BANNED: " + ip);
    }
}
