package com.cryptonex.service;

import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.User;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.payment.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class PaymentOrderLifecycleTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("stripe.webhook.secret", () -> "whsec_test_secret");
    }

    @Test
    void shouldUpdateOrderStatusOnWebhook() throws Exception {
        // 1. Create User
        User user = new User();
        user.setFullName("Payment User");
        user.setEmail("payment@example.com");
        user.setPassword("password");
        user = userRepository.save(user);

        // 2. Create Order
        PaymentOrder order = new PaymentOrder();
        order.setUser(user);
        order.setAmount(new java.math.BigDecimal("100"));
        // order.setAmountInPaise(10000L); // Removed
        order.setStatus(PaymentOrderStatus.PENDING);
        order.setPaymentMethod(PaymentMethod.STRIPE);
        order.setProvider(com.cryptonex.domain.PaymentProvider.STRIPE);
        order.setProviderOrderId("pi_123"); // Match the inner ID

        paymentOrderRepository.save(order);

        // 3. Simulate Webhook
        String payload = "{\"id\": \"evt_pi_123\", \"object\": \"event\", \"type\": \"payment_intent.succeeded\", \"data\": {\"object\": {\"id\": \"pi_123\", \"object\": \"payment_intent\"}}}";

        String secret = "whsec_test_secret";
        long timestamp = Instant.now().getEpochSecond();
        String signature = generateStripeSignature(payload, secret, timestamp);

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", signature)
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // 4. Verify Status
        PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
        assertEquals(PaymentOrderStatus.SUCCESS, updatedOrder.getStatus());
    }

    private String generateStripeSignature(String payload, String secret, long timestamp) throws Exception {
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
