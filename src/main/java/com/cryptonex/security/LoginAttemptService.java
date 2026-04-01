package com.cryptonex.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class LoginAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final String ATTEMPT_PREFIX = "login_attempt:";
    private static final String LOCK_PREFIX = "login_lock:";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 15;
    private static final long ATTEMPT_TTL_MINUTES = 15;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean isBlocked(String email, String ip) {
        String key = getLockKey(email, ip);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void loginFailed(String email, String ip) {
        String attemptKey = getAttemptKey(email, ip);
        String lockKey = getLockKey(email, ip);

        Long attempts = redisTemplate.opsForValue().increment(attemptKey);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptKey, ATTEMPT_TTL_MINUTES, TimeUnit.MINUTES);
        }

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            logger.warn("Blocking user {} at IP {} due to too many failed login attempts", email, ip);
            redisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofMinutes(LOCK_DURATION_MINUTES));
            // Optional: reset attempts after locking, or keep them to prevent immediate
            // re-lock logic issues?
            // Keeping them might be fine, but cleaner to expire them or let them expire
            // naturally.
            // Let's leave them to expire naturally.
        }
    }

    public void loginSucceeded(String email, String ip) {
        redisTemplate.delete(getAttemptKey(email, ip));
        // We do NOT delete the lock key here because if they managed to login, they
        // weren't locked.
        // But if they were locked, they wouldn't reach here.
        // If there was a lingering lock key (somehow?), we could delete it, but better
        // to be strict.
    }

    private String getAttemptKey(String email, String ip) {
        return ATTEMPT_PREFIX + email + ":" + ip;
    }

    private String getLockKey(String email, String ip) {
        return LOCK_PREFIX + email + ":" + ip;
    }
}
