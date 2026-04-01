package com.cryptonex.auth;

import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.request.LoginRequest;
import com.cryptonex.security.LoginAttemptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=dummy-id",
        "spring.security.oauth2.client.registration.google.client-secret=dummy-secret",
        "management.health.redis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.redis.RedisReactiveHealthContributorAutoConfiguration,org.springframework.boot.actuate.autoconfigure.redis.RedisHealthContributorAutoConfiguration"
})
@org.junit.jupiter.api.Disabled("Blocked by environment configuration issues with Actuator/Redis mocks")
public class AccountLockoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.cryptonex.service.EmailService emailService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.cryptonex.service.AlertService alertService;

    // Mock Redis container to prevent connection attempts
    @org.springframework.boot.test.mock.mockito.MockBean(name = "container")
    private org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    // Mock RedisTemplate with in-memory store
    @org.springframework.boot.test.mock.mockito.MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private ObjectMapper objectMapper;

    private java.util.Map<String, Object> redisStore = new java.util.HashMap<>();

    @BeforeEach
    public void setup() {
        redisStore.clear();

        // Setup user
        if (userRepository.findByEmail("lockout_test@example.com") == null) {
            User user = new User();
            user.setEmail("lockout_test@example.com");
            user.setPassword(passwordEncoder.encode("password"));
            user.setFullName("Lockout Test");
            userRepository.save(user);
        }

        // Configure Redis Mock
        org.springframework.data.redis.core.ValueOperations<String, Object> valueOps = org.mockito.Mockito
                .mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // hasKey
        org.mockito.Mockito.when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    return redisStore.containsKey(key);
                });

        // delete (single key)
        org.mockito.Mockito.when(redisTemplate.delete(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    redisStore.remove(key);
                    return true;
                });

        // delete (collection)
        org.mockito.Mockito.when(redisTemplate.delete(org.mockito.ArgumentMatchers.any(java.util.Collection.class)))
                .thenAnswer(invocation -> {
                    java.util.Collection<String> keys = invocation.getArgument(0);
                    if (keys != null)
                        keys.forEach(redisStore::remove);
                    return (long) (keys == null ? 0 : keys.size());
                });

        // opsForValue.increment
        org.mockito.Mockito.when(valueOps.increment(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    Long val = (Long) redisStore.getOrDefault(key, 0L);
                    val++;
                    redisStore.put(key, val);
                    return val;
                });

        // opsForValue.set
        org.mockito.Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            redisStore.put(key, value);
            return null;
        }).when(valueOps).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(java.time.Duration.class));
    }

    @Test
    public void testAccountLockout() throws Exception {
        String email = "lockout_test@example.com";
        String ip = "127.0.0.1";

        LoginRequest badRequest = new LoginRequest();
        badRequest.setEmail(email);
        badRequest.setPassword("wrongpassword");

        // 1. Fail 5 times
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(badRequest))
                    .header("X-Forwarded-For", ip))
                    .andExpect(status().isUnauthorized()); // 401
        }

        // 2. 6th attempt should be blocked (even with correct password would be
        // blocked, but we test blocking logic)
        // We expect isBlocked to be true
        assert (loginAttemptService.isBlocked(email, ip));

        // 3. Attempt with correct password should still fail now
        LoginRequest goodRequest = new LoginRequest();
        goodRequest.setEmail(email);
        goodRequest.setPassword("password");

        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goodRequest))
                .header("X-Forwarded-For", ip))
                .andExpect(status().isUnauthorized()); // Still 401 because locked

        // 4. Attempt from different IP should work
        mockMvc.perform(post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goodRequest))
                .header("X-Forwarded-For", "192.168.1.1"))
                .andExpect(status().isOk()); // 200 OK
    }
}
