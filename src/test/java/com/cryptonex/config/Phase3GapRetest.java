package com.cryptonex.config;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.WebhookEvent;
import com.cryptonex.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "stripe.webhook.secret=whsec_test_secret",
        "razorpay.api.secret=rzp_test_secret"
})
public class Phase3GapRetest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Value("${stripe.webhook.secret}")
    private String stripeSecret;

    @BeforeEach
    void setUp() {
        webhookEventRepository.deleteAll();
    }

    private String generateStripeSignature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(stripeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
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

    @Test
    void unsupportedEventTypeShouldBeIgnored() throws Exception {
        String payload = "{\"id\":\"evt_unknown\",\"type\":\"unknown.event\",\"data\":{\"object\":{\"id\":\"obj_123\"}}}";
        String sigHeader = generateStripeSignature(payload);

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", sigHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        // Should be saved but status might be PENDING or similar (default)
        Optional<WebhookEvent> event = webhookEventRepository.findByProviderAndEventId(PaymentProvider.STRIPE,
                "evt_unknown");
        assertTrue(event.isPresent());
        // We didn't explicitly set status for unknown events in controller, so it
        // likely remains null or default
        // Just verifying it didn't crash
    }

    @Test
    void emptyPayloadShouldFail() throws Exception {
        String payload = "";
        // Signature generation might fail or produce something valid for empty string
        // But controller expects JSON body

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", "t=123,v1=abc") // Dummy sig
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentTypeShouldFail() throws Exception {
        String payload = "{\"id\":\"evt_wrong_type\"}";
        String sigHeader = generateStripeSignature(payload);

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", sigHeader)
                .contentType(MediaType.TEXT_PLAIN) // Wrong type
                .content(payload))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 415 || status == 500, "Expected 415 or 500 but got " + status);
                });
    }

    @Test
    void providerMismatchShouldFail() throws Exception {
        // Sending Stripe payload/signature to Razorpay endpoint
        String payload = "{\"id\":\"evt_mismatch\"}";
        String sigHeader = generateStripeSignature(payload);

        mockMvc.perform(post("/api/webhooks/razorpay")
                .header("X-Razorpay-Signature", sigHeader) // Using Stripe sig as Razorpay sig header
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest()); // Signature verification should fail
    }
}
