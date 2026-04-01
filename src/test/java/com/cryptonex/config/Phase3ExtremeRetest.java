package com.cryptonex.config;

import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.WebhookEvent;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "stripe.webhook.secret=whsec_test_secret",
        "razorpay.api.secret=rzp_test_secret"
})
public class Phase3ExtremeRetest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Value("${stripe.webhook.secret}")
    private String stripeSecret;

    @BeforeEach
    void setUp() {
        webhookEventRepository.deleteAll();
        paymentOrderRepository.deleteAll();
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
    void jsonDepthAttackShouldFail() throws Exception {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            payload.append("{\"a\":");
        }
        payload.append("1");
        for (int i = 0; i < 500; i++) {
            payload.append("}");
        }

        // We wrap it in a valid Stripe structure so it reaches the parser
        String fullPayload = "{\"id\":\"evt_depth\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_depth\",\"metadata\":"
                + payload.toString() + "}}}";
        String sigHeader = generateStripeSignature(fullPayload);

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", sigHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullPayload))
                .andExpect(status().isOk()); // Controller catches exception and returns 200 usually, or 400

        // Verify it didn't crash the app (500)
    }

    @Test
    void concurrentDuplicateProcessingShouldBeIdempotent() throws Exception {
        String payload = "{\"id\":\"evt_concurrent\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_concurrent\"}}}";
        String sigHeader = generateStripeSignature(payload);

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    mockMvc.perform(post("/api/webhooks/stripe")
                            .header("Stripe-Signature", sigHeader)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                            .andExpect(status().isOk());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(threads, successCount.get());
        assertEquals(1, webhookEventRepository.count());
    }

    @Test
    void typeConfusionShouldNotCrash() throws Exception {
        // Amount as String "1000" instead of number
        String payload = "{\"id\":\"evt_type_conf\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_type_conf\",\"amount_total\":\"1000\",\"currency\":\"usd\"}}}";
        String sigHeader = generateStripeSignature(payload);

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", sigHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        // Check if it was saved as FAILED or SUCCESS (depending on Jackson leniency)
        Optional<WebhookEvent> event = webhookEventRepository.findByProviderAndEventId(PaymentProvider.STRIPE,
                "evt_type_conf");
        assertTrue(event.isPresent());
    }

    @Test
    void numericOverflowShouldFail() throws Exception {
        // Amount > Long.MAX_VALUE
        String payload = "{\"id\":\"evt_overflow\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_overflow\",\"amount_total\":9223372036854775808,\"currency\":\"usd\"}}}";
        String sigHeader = generateStripeSignature(payload);

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", sigHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk());

        Optional<WebhookEvent> event = webhookEventRepository.findByProviderAndEventId(PaymentProvider.STRIPE,
                "evt_overflow");
        assertTrue(event.isPresent());
        assertEquals("FAILED", event.get().getStatus());
    }
}
