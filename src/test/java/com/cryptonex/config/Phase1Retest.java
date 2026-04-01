package com.cryptonex.config;

import com.cryptonex.repository.UserRepository;
import com.cryptonex.security.JwtConstant;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpMethod;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("prod")
@org.springframework.test.context.TestPropertySource(properties = {
                "app.cors.allowed-origins=http://localhost:3000,http://example.com",
                "DB_URL=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "DB_USER=sa",
                "DB_PASSWORD=",
                "JWT_SECRET=fake-secret-for-phase1-retest-only-must-be-long-enough-32-chars",
                "STRIPE_API_KEY=sk_test_fake",
                "CASHFREE_APP_ID=fake_app_id",
                // Properties required by ProdValidationConfig and App
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "jwt.secret=fake-secret-for-phase1-retest-only-must-be-long-enough-32-chars",
                "stripe.api.key=sk_test_fake",
                "cashfree.app.id=fake_app_id",
                // Mail mocks if needed (avoiding context failure if mail starter present)
                "spring.mail.host=localhost",
                "spring.mail.port=1025",
                // OAuth2
                "spring.security.oauth2.client.registration.google.client-id=fake-client-id",
                "spring.security.oauth2.client.registration.google.client-secret=fake-client-secret",
                // Schema init for tests
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "cashfree.app.id=fake_app_id",
                "cashfree.secret.key=fake_secret_key",
                "cashfree.api.url=https://sandbox.cashfree.com/pg",
                "COINMARKETCAP_API_KEY=fake-key",
                "STRIPE_WEBHOOK_SECRET=fake-secret",
                "management.health.redis.enabled=false",
                // Schema init for tests
                "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class Phase1Retest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @org.springframework.boot.test.mock.mockito.MockBean
        private com.cryptonex.service.AlertService alertService;

        @Autowired
        private com.cryptonex.config.JwtConfig jwtConfig;

        @org.springframework.boot.test.mock.mockito.MockBean
        private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

        @org.springframework.boot.test.mock.mockito.MockBean
        private org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

        @org.springframework.boot.test.mock.mockito.MockBean
        private com.cryptonex.service.ChatBotService chatBotService;

        @org.springframework.boot.test.mock.mockito.MockBean
        private com.cryptonex.payment.PaymentService paymentService;

        @org.springframework.boot.test.mock.mockito.MockBean
        private com.cryptonex.service.CoinService coinService;

        // -------------------------------------------------------------------
        // 1. CORS / HTTP config
        // -------------------------------------------------------------------

        @Test
        void corsAllowedOriginHappyPath() throws Exception {
                mockMvc.perform(get("https://localhost/api/users/profile") // HTTPS
                                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token_placeholder"))
                                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                                "http://localhost:3000"));
        }

        @Test
        void corsPreflightAllowedOrigin() throws Exception {
                mockMvc.perform(options("https://localhost/api/users/profile") // HTTPS
                                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                                .andExpect(status().isOk())
                                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                                org.hamcrest.Matchers.containsString("POST")))
                                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                                                org.hamcrest.Matchers.containsString("Authorization, Content-Type")));
        }

        @Test
        void corsBlockedOrigin() throws Exception {
                mockMvc.perform(get("https://localhost/api/users/profile") // HTTPS
                                .header(HttpHeaders.ORIGIN, "https://evil-attacker.com"))
                                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        // -------------------------------------------------------------------
        // 2. HTTPS, HSTS & Security headers
        // -------------------------------------------------------------------

        @Test
        void httpToHttpsRedirect() throws Exception {
                mockMvc.perform(get("http://localhost/api/health"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://")));
        }

        @Test
        void securityHeadersPresent() throws Exception {
                mockMvc.perform(get("https://localhost/auth/signup")) // HTTPS
                                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                                .andExpect(header().string("X-Frame-Options", "DENY"))
                                .andExpect(
                                                header().string("Strict-Transport-Security",
                                                                org.hamcrest.Matchers.containsString("max-age")));
        }

        // -------------------------------------------------------------------
        // 3. Actuator & admin exposure
        // -------------------------------------------------------------------

        @Test
        void actuatorHealthIsPublic() throws Exception {
                mockMvc.perform(get("https://localhost/actuator/health")) // HTTPS
                                .andExpect(status().isOk());
        }

        @Test
        void sensitiveActuatorsRequireAuth() throws Exception {
                mockMvc.perform(get("https://localhost/actuator/env")) // HTTPS
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void webhookEndpointRequiresSignature() throws Exception {
                mockMvc.perform(post("https://localhost/api/webhooks/stripe") // HTTPS
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
        }

        // -------------------------------------------------------------------
        // 4. JwtTokenValidator & config edge cases
        // -------------------------------------------------------------------

        @Test
        void expiredTokenShouldFail() throws Exception {
                SecretKey key = jwtConfig.getSecretKey();
                String expiredToken = Jwts.builder()
                                .setSubject("test@example.com")
                                .setIssuedAt(new Date(System.currentTimeMillis() - 10000))
                                .setExpiration(new Date(System.currentTimeMillis() - 5000))
                                .signWith(key)
                                .compact();

                mockMvc.perform(get("https://localhost/api/users/profile") // HTTPS
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void tamperedSignatureShouldFail() throws Exception {
                SecretKey key = jwtConfig.getSecretKey();
                String validToken = Jwts.builder()
                                .setSubject("test@example.com")
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + 10000))
                                .signWith(key)
                                .compact();

                String tamperedToken = validToken.substring(0, validToken.length() - 1) + "X";

                mockMvc.perform(get("https://localhost/api/users/profile") // HTTPS
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------
        // 5. Enhanced Production Checks
        // -------------------------------------------------------------------

        @Test
        void securityHeadersOnError() throws Exception {
                // Trigger a 400 error (Missing Header)
                mockMvc.perform(post("https://localhost/api/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest())
                                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                                .andExpect(header().string("X-Frame-Options", "DENY"))
                                .andExpect(
                                                header().string("Strict-Transport-Security",
                                                                org.hamcrest.Matchers.containsString("max-age")));
        }

        @Test
        void jwtWithNoneAlgorithmShouldFail() throws Exception {
                // Create a token with "none" algorithm (unsecured)
                String noneToken = Jwts.builder()
                                .setSubject("test@example.com")
                                .setIssuedAt(new Date())
                                .setExpiration(new Date(System.currentTimeMillis() + 10000))
                                .compact(); // No signWith = none alg

                mockMvc.perform(get("https://localhost/api/users/profile")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + noneToken))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void corsSecondAllowedOrigin() throws Exception {
                mockMvc.perform(get("https://localhost/api/users/profile")
                                .header(HttpHeaders.ORIGIN, "http://example.com")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token_placeholder"))
                                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                                "http://example.com"));
        }

        // -------------------------------------------------------------------
        // 6. Extreme Security Checks
        // -------------------------------------------------------------------

        @Test
        void httpVerbTamperingTraceShouldFail() throws Exception {
                // TRACE method is often used for XST attacks and should be disabled
                // Accepting any 4xx error (400, 403, 405) as long as it's not 200 or 500
                mockMvc.perform(request(HttpMethod.TRACE, "https://localhost/api/users/profile")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token_placeholder"))
                                .andExpect(status().is4xxClientError());
        }

        @Test
        void pathTraversalShouldFail() throws Exception {
                // Spring Security Firewall usually blocks this before it reaches the dispatcher
                // It might throw RequestRejectedException which results in 400 or 500 depending
                // on config
                // We expect it to NOT return 200 OK
                try {
                        mockMvc.perform(get("https://localhost/api/users/../../actuator/env"))
                                        .andExpect(status().is4xxClientError());
                } catch (Exception e) {
                        // RequestRejectedException is acceptable here
                }
        }

        @Test
        void malformedJsonShouldReturn400() throws Exception {
                mockMvc.perform(post("https://localhost/api/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"invalid\": }")) // Syntax error
                                .andExpect(status().isBadRequest());
        }

        @Test
        void largePayloadShouldFail() throws Exception {
                // Create a 11MB string (default limit is usually 2MB or 10MB)
                String largeData = "a".repeat(11 * 1024 * 1024);

                // Accepting any 4xx error (400, 413)
                mockMvc.perform(post("https://localhost/api/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"data\": \"" + largeData + "\"}"))
                                .andExpect(status().is4xxClientError());
        }

        @Test
        void sqlInjectionInHeaderShouldNotCrash() throws Exception {
                mockMvc.perform(get("https://localhost/api/users/profile")
                                .header("X-Custom-Header", "' OR '1'='1")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token_placeholder"))
                                .andExpect(status().isUnauthorized()); // Should still fail auth, but not 500
        }
}
