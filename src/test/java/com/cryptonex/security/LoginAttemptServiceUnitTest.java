package com.cryptonex.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoginAttemptServiceUnitTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    @BeforeEach
    public void setup() {
        lenient().when(redisTemplate.hasKey(anyString())).thenReturn(false);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testLoginFailed_IncrementsAttempts() {
        String email = "test@example.com";
        String ip = "127.0.0.1";

        when(valueOperations.increment(anyString())).thenReturn(1L);

        loginAttemptService.loginFailed(email, ip);

        verify(valueOperations).increment("login_attempt:" + email + ":" + ip);
    }

    @Test
    public void testLoginFailed_LocksAccount_AfterMaxAttempts() {
        String email = "test@example.com";
        String ip = "127.0.0.1";

        // return 5
        when(valueOperations.increment(anyString())).thenReturn(5L);

        loginAttemptService.loginFailed(email, ip);

        verify(valueOperations).set(eq("login_lock:" + email + ":" + ip), eq("LOCKED"), any(Duration.class));
    }

    @Test
    public void testIsBlocked_ReturnsTrue_WhenLockExists() {
        String email = "test@example.com";
        String ip = "127.0.0.1";

        when(redisTemplate.hasKey("login_lock:" + email + ":" + ip)).thenReturn(true);

        assertTrue(loginAttemptService.isBlocked(email, ip));
    }

    @Test
    public void testIsBlocked_ReturnsFalse_WhenNoLock() {
        String email = "test@example.com";
        String ip = "127.0.0.1";

        lenient().when(redisTemplate.hasKey("login_lock:" + email + ":" + ip)).thenReturn(false);

        assertFalse(loginAttemptService.isBlocked(email, ip));
    }

    @Test
    public void testLoginSucceeded_ClearsAttempts() {
        String email = "test@example.com";
        String ip = "127.0.0.1";

        loginAttemptService.loginSucceeded(email, ip);

        verify(redisTemplate).delete("login_attempt:" + email + ":" + ip);
    }
}
