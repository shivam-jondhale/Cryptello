package com.cryptonex.security;

import com.cryptonex.service.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimitingService rateLimitingService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.mail.javamail.JavaMailSender javaMailSender;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.cryptonex.service.EmailService emailService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.cryptonex.service.WalletService walletService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    public void setup() {
        // In a real integration test, buckets are stateful.
        // We use unique IPs/Users to isolate tests.
    }

    @Test
    public void testT1_Webhook_Differentiated() throws Exception {
        // 1. Untrusted (No Signature) -> Strict Limit (10/min)
        String untrustedIp = "192.168.1.100";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/webhooks/cashfree")
                    .with(request -> {
                        request.setRemoteAddr(untrustedIp);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest()); // Likely 400 due to bad request, but NOT 429 yet
        }
        // 11th request should be 429
        mockMvc.perform(post("/api/webhooks/cashfree")
                .with(request -> {
                    request.setRemoteAddr(untrustedIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isTooManyRequests());

        // 2. Trusted (With Signature) -> High Limit (200/min)
        String trustedIp = "192.168.1.101";
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/webhooks/cashfree")
                    .header("x-webhook-signature", "some_sig")
                    .with(request -> {
                        request.setRemoteAddr(trustedIp);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest()); // 400 (Invalid Sig) but NOT 429
        }
        // Should still be allowed (not 429)
        mockMvc.perform(post("/api/webhooks/cashfree")
                .header("x-webhook-signature", "some_sig")
                .with(request -> {
                    request.setRemoteAddr(trustedIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testT2_Payment_Abuse_User() throws Exception {
        // Generate valid token
        String token = io.jsonwebtoken.Jwts.builder()
                .setSubject("abuser@example.com")
                .claim("roles", "ROLE_USER")
                .setIssuedAt(new java.util.Date())
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 86400000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();

        // User Limit: 10/min
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/payments/subscribe/999")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().is4xxClientError()); // 404 (User/Plan not found)
        }

        // 11th request -> 429
        mockMvc.perform(post("/api/payments/subscribe/999")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    public void testT3_BruteForce_Login() throws Exception {
        // Signin IP Limit: 30/min
        String attackerIp = "10.0.0.50";

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/auth/signin")
                    .with(request -> {
                        request.setRemoteAddr(attackerIp);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"victim@example.com\", \"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized()); // 401 (Wrong creds)
        }

        // 31st request -> 429
        mockMvc.perform(post("/auth/signin")
                .with(request -> {
                    request.setRemoteAddr(attackerIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"victim@example.com\", \"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    public void testT4_Mixed_Attack() throws Exception {
        // IP A (Attacker) -> Webhook Spam
        String attackerIp = "10.0.0.66";
        // IP B (Normal) -> Webhook Normal
        String normalIp = "10.0.0.77";

        // Exhaust Attacker
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(post("/api/webhooks/stripe")
                    .with(request -> {
                        request.setRemoteAddr(attackerIp);
                        return request;
                    })
                    .content("{}"))
                    .andReturn();
        }
        // Attacker is blocked
        mockMvc.perform(post("/api/webhooks/stripe")
                .with(request -> {
                    request.setRemoteAddr(attackerIp);
                    return request;
                })
                .content("{}"))
                .andExpect(status().isTooManyRequests());

        // Normal User is NOT blocked
        mockMvc.perform(post("/api/webhooks/stripe")
                .with(request -> {
                    request.setRemoteAddr(normalIp);
                    return request;
                })
                .content("{}"))
                .andExpect(status().isBadRequest()); // 400 (No sig)
    }
}
