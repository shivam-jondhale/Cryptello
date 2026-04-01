package com.cryptonex.controller;

import com.cryptonex.model.WebhookEvent;
import com.cryptonex.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", // Disable flyway for H2 or ensure scripts are compatible
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "stripe.webhook.secret=whsec_test_secret"
})
class WebhookPersistenceH2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Test
    void shouldPersistStripeWebhookEvent() throws Exception {
        String payload = "{\"id\": \"evt_h2_123\", \"object\": \"event\"}";
        String secret = "whsec_test_secret";
        long timestamp = Instant.now().getEpochSecond();
        String signature = generateStripeSignature(payload, secret, timestamp);

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", signature)
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        List<WebhookEvent> events = webhookEventRepository.findAll();
        boolean exists = events.stream().anyMatch(e -> "evt_h2_123".equals(e.getEventId()));
        assertEquals(true, exists);
    }

    private String generateStripeSignature(String payload, String secret, long timestamp)
            throws NoSuchAlgorithmException, InvalidKeyException {
        String signedPayload = timestamp + "." + payload;
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1)
                hexString.append('0');
            hexString.append(hex);
        }

        return "t=" + timestamp + ",v1=" + hexString.toString();
    }
}
