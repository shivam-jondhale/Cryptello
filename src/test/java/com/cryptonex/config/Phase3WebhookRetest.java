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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Formatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
                "stripe.webhook.secret=whsec_test_secret",
                "razorpay.api.secret=rzp_test_secret"
})
public class Phase3WebhookRetest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private WebhookEventRepository webhookEventRepository;

        @Autowired
        private PaymentOrderRepository paymentOrderRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Value("${stripe.webhook.secret}")
        private String stripeSecret;

        @Value("${razorpay.api.secret}")
        private String razorpaySecret;

        @BeforeEach
        void setUp() {
                webhookEventRepository.deleteAll();
                paymentOrderRepository.deleteAll();
        }

        // --- Helper Methods for Signatures ---

        private String computeHmacSha256(String data, String secret)
                        throws NoSuchAlgorithmException, InvalidKeyException {
                Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
                SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                sha256_HMAC.init(secret_key);
                byte[] bytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
                return toHexString(bytes);
        }

        private String toHexString(byte[] bytes) {
                Formatter formatter = new Formatter();
                for (byte b : bytes) {
                        formatter.format("%02x", b);
                }
                return formatter.toString();
        }

        private String generateStripeSignature(String payload) throws Exception {
                long timestamp = Instant.now().getEpochSecond();
                String signedPayload = timestamp + "." + payload;
                String signature = computeHmacSha256(signedPayload, stripeSecret);
                return "t=" + timestamp + ",v1=" + signature;
        }

        // --- 1. Signature & Authenticity Tests ---

        @Test
        void stripeValidSignatureShouldSucceed() throws Exception {
                String payload = "{\"id\":\"evt_test_123\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_123\"}}}";
                String sigHeader = generateStripeSignature(payload);

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Received"));

                // Verify DB
                Optional<WebhookEvent> event = webhookEventRepository.findByProviderAndEventId(PaymentProvider.STRIPE,
                                "evt_test_123");
                assertTrue(event.isPresent());
                assertEquals("FAILED", event.get().getStatus());
                assertEquals("Order not found", event.get().getErrorMessage());
        }

        @Test
        void stripeMissingSignatureShouldFail() throws Exception {
                String payload = "{\"id\":\"evt_test_missing_sig\"}";

                mockMvc.perform(post("/api/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest()); // 400

                // Verify No DB Change
                Optional<WebhookEvent> event = webhookEventRepository.findByProviderAndEventId(PaymentProvider.STRIPE,
                                "evt_test_missing_sig");
                assertFalse(event.isPresent());
        }

        @Test
        void stripeWrongSecretShouldFail() throws Exception {
                String payload = "{\"id\":\"evt_test_wrong_secret\"}";
                // Sign with WRONG secret
                long timestamp = Instant.now().getEpochSecond();
                String signedPayload = timestamp + "." + payload;
                String signature = computeHmacSha256(signedPayload, "wrong_secret");
                String sigHeader = "t=" + timestamp + ",v1=" + signature;

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isBadRequest());

                assertFalse(webhookEventRepository
                                .findByProviderAndEventId(PaymentProvider.STRIPE, "evt_test_wrong_secret")
                                .isPresent());
        }

        @Test
        void razorpayValidSignatureShouldSucceed() throws Exception {
                String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\"}}}}";
                String signature = computeHmacSha256(payload, razorpaySecret);

                mockMvc.perform(post("/api/webhooks/razorpay")
                                .header("X-Razorpay-Signature", signature)
                                .header("X-Razorpay-Event-Id", "rp_evt_123")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                Optional<WebhookEvent> event = webhookEventRepository.findByProviderAndEventId(PaymentProvider.RAZORPAY,
                                "rp_evt_123");
                assertTrue(event.isPresent());
        }

        // --- 2. Idempotency & Event Ordering ---

        @Test
        void stripeDuplicateEventShouldBeIdempotent() throws Exception {
                String payload = "{\"id\":\"evt_test_dup\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_123\"}}}";
                String sigHeader = generateStripeSignature(payload);

                // First Request
                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                assertEquals(1, webhookEventRepository.count());

                // Second Request (Same Event ID)
                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Duplicate event"));

                assertEquals(1, webhookEventRepository.count()); // Should still be 1
        }

        // --- 3. PaymentOrder & Data Integrity ---

        @Test
        void stripeOrderSuccessUpdate() throws Exception {
                // Create Order
                PaymentOrder order = new PaymentOrder();
                order.setProviderOrderId("cs_test_success");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setAmount(new java.math.BigDecimal("10.00"));
                paymentOrderRepository.save(order);

                String payload = "{\"id\":\"evt_test_success\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_success\"}}}";
                String sigHeader = generateStripeSignature(payload);

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                // Verify Order Updated
                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.SUCCESS, updatedOrder.getStatus());
                assertEquals("cs_test_success", updatedOrder.getProviderPaymentId());

                // Verify Webhook Status
                WebhookEvent event = webhookEventRepository
                                .findByProviderAndEventId(PaymentProvider.STRIPE, "evt_test_success")
                                .orElseThrow();
                assertEquals("SUCCESS", event.getStatus());
        }

        @Test
        void razorpayOrderSuccessUpdate() throws Exception {
                // Create Order with Payment Link ID (simulated)
                PaymentOrder order = new PaymentOrder();
                order.setProviderOrderId("plink_123");
                order.setProvider(PaymentProvider.RAZORPAY);
                order.setStatus(PaymentOrderStatus.PENDING);
                paymentOrderRepository.save(order);

                // Payload with payment_link.id
                String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_rzp_123\",\"amount\":1000,\"currency\":\"INR\"}},\"payment_link\":{\"entity\":{\"id\":\"plink_123\"}}}}";
                String signature = computeHmacSha256(payload, razorpaySecret);

                mockMvc.perform(post("/api/webhooks/razorpay")
                                .header("X-Razorpay-Signature", signature)
                                .header("X-Razorpay-Event-Id", "rp_evt_success")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                // Verify Order Updated
                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.SUCCESS, updatedOrder.getStatus());
                assertEquals("pay_rzp_123", updatedOrder.getProviderPaymentId());
        }

        @Test
        void stripeAmountMismatchShouldFail() throws Exception {
                // Create Order with Amount 1000
                PaymentOrder order = new PaymentOrder();
                order.setProviderOrderId("cs_test_amount_fail");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setAmount(new java.math.BigDecimal("10.00"));
                paymentOrderRepository.save(order);

                // Payload with Amount 2000
                String payload = "{\"id\":\"evt_test_amount_fail\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_amount_fail\",\"amount_total\":2000,\"currency\":\"usd\"}}}";
                String sigHeader = generateStripeSignature(payload);

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Amount mismatch"));

                // Verify Order NOT Updated
                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.PENDING, updatedOrder.getStatus());

                // Verify Webhook Status
                WebhookEvent event = webhookEventRepository
                                .findByProviderAndEventId(PaymentProvider.STRIPE, "evt_test_amount_fail").orElseThrow();
                assertEquals("FAILED", event.getStatus());
                assertTrue(event.getErrorMessage().contains("Amount mismatch"));
        }

        @Test
        void stripeCurrencyMismatchShouldFail() throws Exception {
                // Create Order with USD
                PaymentOrder order = new PaymentOrder();
                order.setProviderOrderId("cs_test_curr_fail");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setAmount(new java.math.BigDecimal("10.00"));
                order.setCurrency("USD");
                paymentOrderRepository.save(order);

                // Payload with EUR
                String payload = "{\"id\":\"evt_test_curr_fail\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_curr_fail\",\"amount_total\":1000,\"currency\":\"eur\"}}}";
                String sigHeader = generateStripeSignature(payload);

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Currency mismatch"));

                // Verify Order NOT Updated
                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.PENDING, updatedOrder.getStatus());

                // Verify Webhook Status
                WebhookEvent event = webhookEventRepository
                                .findByProviderAndEventId(PaymentProvider.STRIPE, "evt_test_curr_fail").orElseThrow();
                assertEquals("FAILED", event.getStatus());
                assertTrue(event.getErrorMessage().contains("Currency mismatch"));
        }

        @Test
        void razorpayAmountMismatchShouldFail() throws Exception {
                // Create Order with Amount 1000
                PaymentOrder order = new PaymentOrder();
                order.setProviderOrderId("plink_amount_fail");
                order.setProvider(PaymentProvider.RAZORPAY);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setAmount(new java.math.BigDecimal("1000"));
                paymentOrderRepository.save(order);

                // Payload with Amount 2000
                String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_rzp_amount_fail\",\"amount\":2000,\"currency\":\"INR\"}},\"payment_link\":{\"entity\":{\"id\":\"plink_amount_fail\"}}}}";
                String signature = computeHmacSha256(payload, razorpaySecret);

                mockMvc.perform(post("/api/webhooks/razorpay")
                                .header("X-Razorpay-Signature", signature)
                                .header("X-Razorpay-Event-Id", "rp_evt_amount_fail")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Amount mismatch"));

                // Verify Order NOT Updated
                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.PENDING, updatedOrder.getStatus());

                // Verify Webhook Status
                WebhookEvent event = webhookEventRepository
                                .findByProviderAndEventId(PaymentProvider.RAZORPAY, "rp_evt_amount_fail").orElseThrow();
                assertEquals("FAILED", event.getStatus());
                assertTrue(event.getErrorMessage().contains("Amount mismatch"));
        }

        @Test
        void razorpayCurrencyMismatchShouldFail() throws Exception {
                // Create Order with INR
                PaymentOrder order = new PaymentOrder();
                order.setProviderOrderId("plink_curr_fail");
                order.setProvider(PaymentProvider.RAZORPAY);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setAmount(new java.math.BigDecimal("1000"));
                order.setCurrency("INR");
                paymentOrderRepository.save(order);

                // Payload with USD
                String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_rzp_curr_fail\",\"amount\":1000,\"currency\":\"USD\"}},\"payment_link\":{\"entity\":{\"id\":\"plink_curr_fail\"}}}}";
                String signature = computeHmacSha256(payload, razorpaySecret);

                mockMvc.perform(post("/api/webhooks/razorpay")
                                .header("X-Razorpay-Signature", signature)
                                .header("X-Razorpay-Event-Id", "rp_evt_curr_fail")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Currency mismatch"));

                // Verify Order NOT Updated
                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.PENDING, updatedOrder.getStatus());

                // Verify Webhook Status
                WebhookEvent event = webhookEventRepository
                                .findByProviderAndEventId(PaymentProvider.RAZORPAY, "rp_evt_curr_fail").orElseThrow();
                assertEquals("FAILED", event.getStatus());
                assertTrue(event.getErrorMessage().contains("Currency mismatch"));
        }

        // --- 4. Performance & Flood Scenarios ---

        @Test
        void webhookBurstTest() throws Exception {
                int threads = 50;
                java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
                                .newFixedThreadPool(threads);
                java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(
                                0);
                java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(
                                0);

                for (int i = 0; i < threads; i++) {
                        final int index = i;
                        executor.submit(() -> {
                                try {
                                        String payload = "{\"id\":\"evt_burst_" + index
                                                        + "\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_burst_"
                                                        + index + "\"}}}";
                                        String sigHeader = generateStripeSignature(payload);

                                        mockMvc.perform(post("/api/webhooks/stripe")
                                                        .header("Stripe-Signature", sigHeader)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(payload))
                                                        .andExpect(status().isOk());
                                        successCount.incrementAndGet();
                                } catch (Exception e) {
                                        failureCount.incrementAndGet();
                                }
                        });
                }

                executor.shutdown();
                executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

                assertEquals(threads, successCount.get());
                assertEquals(0, failureCount.get());
        }

        @Test
        void largePayloadShouldBeHandled() throws Exception {
                StringBuilder largeData = new StringBuilder();
                for (int i = 0; i < 10000; i++) {
                        largeData.append("a");
                }
                String payload = "{\"id\":\"evt_large\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_large\",\"metadata\":{\"key\":\""
                                + largeData.toString() + "\"}}}}";
                String sigHeader = generateStripeSignature(payload);

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", sigHeader)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                Optional<WebhookEvent> event = webhookEventRepository.findByProviderAndEventId(PaymentProvider.STRIPE,
                                "evt_large");
                assertTrue(event.isPresent());
        }
}
