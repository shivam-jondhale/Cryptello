package com.cryptonex.service;

import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.User;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "stripe.webhook.secret=whsec_test_secret"
})
class PaymentOrderLifecycleH2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Test
    void shouldUpdateOrderStatusOnWebhook() throws Exception {
        // 1. Create User
        User user = new User();
        user.setFullName("Payment H2 User");
        user.setEmail("payment_h2@example.com");
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

        // Debug: check webhook event
        var events = webhookEventRepository.findAll();
        if (events.isEmpty()) {
            System.out.println("No webhook events found!");
        } else {
            events.forEach(e -> System.out.println(
                    "Event: " + e.getEventId() + ", Status: " + e.getStatus() + ", Error: " + e.getErrorMessage()));
        }

        assertEquals(PaymentOrderStatus.SUCCESS, updatedOrder.getStatus(),
                "Order status should be SUCCESS. Events: " + events);
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
