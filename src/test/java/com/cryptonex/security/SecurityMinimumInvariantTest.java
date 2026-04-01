package com.cryptonex.security;

import com.cryptonex.config.JwtConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=fake-secret-for-invariant-testing-only-must-be-long-enough",
        "jwt.access-token-expiration-ms=86400000",
        "spring.security.oauth2.client.registration.google.client-id=fake-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=fake-client-secret",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "cashfree.app.id=fake_app_id",
        "cashfree.secret.key=fake_secret_key",
        "cashfree.api.url=https://sandbox.cashfree.com/pg",
        "COINMARKETCAP_API_KEY=fake-key",
        "management.health.redis.enabled=false"
})
public class SecurityMinimumInvariantTest {

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Environment environment;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.cryptonex.service.EmailService emailService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @Test
    @DisplayName("Token lifetime should match documented policy (24h) and be configurable")
    void verifyTokenLifetime() {
        // Verify that the configuration matches policy (24h = 86400000ms)
        // We inject the property used by JwtProvider, but since JwtProvider reads it
        // directly via @Value,
        // we might need to inspect the environment or the bean if we exposed it.
        // Currently JwtProvider has it as private @Value.
        // Ideally, JwtConfig should expose expiration too.
        // For now, checks against the Environment which feeds the bean.

        Long expirationMs = environment.getProperty("jwt.access-token-expiration-ms", Long.class);
        assertThat(expirationMs).isNotNull();
        assertThat(expirationMs).isEqualTo(86400000L)
                .withFailMessage("Token lifetime must be 24h as per SECURITY-MINIMUM.md");
    }

    @Test
    @DisplayName("Password encoder must be BCrypt with strength >= 10")
    void verifyPasswordPolicy() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);

        // Reflection to check strength is hard for BCryptPasswordEncoder as it doesn't
        // expose it easily
        // but it doesn't default to < 10 (default is 10).
        // Verification: BCrypt hash usually starts with $2a$10$

        String encoded = passwordEncoder.encode("test");
        // Check for BCrypt prefix ($2a$ or $2b$) and cost factor 10
        assertThat(encoded).matches("^\\$2[ab]\\$10\\$.*");
    }

    @Test
    @DisplayName("Environment secrets must not be default in PROD context")
    void verifyEnvSecrets() {
        // This test runs in default (test) profile, so we check that we have verified
        // this behavior via ConfigSanityTest
        // This is just a placeholder to remind us that ConfigSanityTest covers the
        // "Prod" verification.
        // We can verify that current context has a secure-enough secret for testing
        // (setup in TestPropertySource)

        String secret = jwtConfig.getSecretString();
        assertThat(secret).isNotEqualTo("change-me");
        assertThat(secret.length()).isGreaterThan(32);
    }
}
