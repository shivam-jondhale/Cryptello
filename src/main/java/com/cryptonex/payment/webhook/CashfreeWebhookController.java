package com.cryptonex.payment.webhook;

import com.cryptonex.model.PaymentOrder;
import com.cryptonex.payment.PaymentService;
import com.cryptonex.repository.PaymentOrderRepository;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/cashfree")
public class CashfreeWebhookController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Value("${cashfree.secret.key}")
    private String secretKey;

    private final Gson gson = new Gson();

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestHeader Map<String, String> headers,
            @RequestBody String payload) {

        try {
            // 1. Verify Signature
            // Cashfree sends x-webhook-signature, x-webhook-timestamp
            String signature = headers.get("x-webhook-signature");
            String timestamp = headers.get("x-webhook-timestamp");

            if (signature == null || timestamp == null) {
                // Try case-insensitive headers if needed, but Spring usually handles it.
                // Let's assume standard headers.
                return new ResponseEntity<>("Missing headers", HttpStatus.BAD_REQUEST);
            }

            if (!verifySignature(payload, timestamp, signature)) {
                return new ResponseEntity<>("Invalid Signature", HttpStatus.FORBIDDEN);
            }

            // 2. Parse Payload
            JsonObject json = gson.fromJson(payload, JsonObject.class);
            String type = json.get("type").getAsString();

            if ("PAYMENT_SUCCESS_WEBHOOK".equals(type)) {
                JsonObject data = json.getAsJsonObject("data");
                JsonObject order = data.getAsJsonObject("order");
                JsonObject payment = data.getAsJsonObject("payment");

                String orderId = order.get("order_id").getAsString();
                String paymentStatus = payment.get("payment_status").getAsString();

                if ("SUCCESS".equals(paymentStatus)) {
                    // 3. Process Payment
                    // Our orderId might be "ORDER_123_..." or just "123" depending on what we sent.
                    // In CashfreePaymentProvider, we sent "ORDER_" + id + "_" + timestamp
                    // We need to find the order by providerOrderId (which is the Cashfree order_id)

                    PaymentOrder paymentOrder = paymentOrderRepository.findByProviderAndProviderOrderId(
                            com.cryptonex.domain.PaymentProvider.CASHFREE, orderId);

                    if (paymentOrder != null) {
                        paymentService.processPaymentSuccess(paymentOrder);
                    } else {
                        System.err.println("Order not found for webhook: " + orderId);
                    }
                }
            }

            return new ResponseEntity<>("OK", HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean verifySignature(String payload, String timestamp, String signature) {
        try {
            String data = timestamp + payload;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            String computedSignature = Base64.getEncoder()
                    .encodeToString(sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8)));
            return computedSignature.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}
