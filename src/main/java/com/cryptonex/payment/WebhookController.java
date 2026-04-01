package com.cryptonex.payment;

import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.WebhookEvent;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.WebhookEventRepository;
import com.cryptonex.service.AlertService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    @Value("${stripe.webhook.secret:whsec_test}") // Default for dev
    private String stripeWebhookSecret;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertService alertService;

    @Autowired
    private com.cryptonex.payment.service.StripeWebhookHelper stripeWebhookHelper;

    @Autowired
    private PaymentService paymentService;

    @PostMapping(value = "/stripe", consumes = "application/json")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload) {

        Event event;
        try {
            event = stripeWebhookHelper.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Stripe signature verification failed: {}", e.getMessage());
            alertService.logAlert("Stripe signature verification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            logger.error("Stripe webhook error: {}", e.getMessage());
            alertService.sendCriticalAlert("Stripe Webhook Error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook error");
        }

        // Persist Event (Idempotency)
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setProvider(PaymentProvider.STRIPE);
        webhookEvent.setEventId(event.getId());
        webhookEvent.setEventType(event.getType());
        webhookEvent.setPayload(payload);

        try {
            webhookEventRepository.save(webhookEvent);
        } catch (DataIntegrityViolationException e) {
            logger.info("Duplicate Stripe event received: {}", event.getId());
            return ResponseEntity.ok("Duplicate event");
        }

        // Process Event
        try {
            if ("checkout.session.completed".equals(event.getType())
                    || "payment_intent.succeeded".equals(event.getType())) {

                // For checkout.session.completed, the object is a Session
                // For payment_intent.succeeded, the object is a PaymentIntent
                // We need to handle both or focus on one.
                // Our StripePaymentProvider creates a Session, so checkout.session.completed is
                // primary.

                JsonNode root = objectMapper.readTree(payload);
                JsonNode dataObject = root.path("data").path("object");
                String id = dataObject.path("id").asText();

                // For Session, client_reference_id might be our Order ID if we set it.
                // Or we use providerOrderId which we stored as session.getId().

                if (id != null && !id.isEmpty()) {
                    PaymentOrder order = paymentOrderRepository.findByProviderAndProviderOrderId(
                            com.cryptonex.domain.PaymentProvider.STRIPE, id);

                    // Fallback: try client_reference_id if order not found by providerOrderId
                    if (order == null && dataObject.has("client_reference_id")) {
                        String clientRef = dataObject.path("client_reference_id").asText();
                        if (clientRef != null && !clientRef.isEmpty()) {
                            try {
                                Long orderId = Long.parseLong(clientRef);
                                order = paymentOrderRepository.findById(orderId).orElse(null);
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                    }

                    if (order != null) {

                        // Validate Amount and Currency
                        long amountReceived = dataObject.path("amount_total").asLong();
                        String currencyReceived = dataObject.path("currency").asText();

                        // Note: amount_total is available on Session.
                        // On PaymentIntent, it is 'amount'.
                        if (amountReceived == 0 && dataObject.has("amount")) {
                            amountReceived = dataObject.path("amount").asLong();
                        }

                        if (order.getAmount() != null && order.getAmount().multiply(new java.math.BigDecimal(100))
                                .longValue() != amountReceived) {
                            logger.warn("Amount mismatch for order {}: expected {}, got {}", order.getId(),
                                    order.getAmount(), amountReceived);
                            webhookEvent.setStatus("FAILED");
                            webhookEvent.setErrorMessage("Amount mismatch: expected " + order.getAmount()
                                    + ", got " + amountReceived);
                            webhookEventRepository.save(webhookEvent);
                            return ResponseEntity.ok("Amount mismatch");
                        }

                        // Stripe sends lowercase currency usually
                        if (order.getCurrency() != null && !order.getCurrency().equalsIgnoreCase(currencyReceived)) {
                            logger.warn("Currency mismatch for order {}: expected {}, got {}", order.getId(),
                                    order.getCurrency(), currencyReceived);
                            webhookEvent.setStatus("FAILED");
                            webhookEvent.setErrorMessage(
                                    "Currency mismatch: expected " + order.getCurrency() + ", got " + currencyReceived);
                            webhookEventRepository.save(webhookEvent);
                            return ResponseEntity.ok("Currency mismatch");
                        }

                        // CRITICAL FIX: Call PaymentService to handle subscription logic
                        paymentService.processPaymentSuccess(order);

                        webhookEvent.setStatus("SUCCESS");
                        webhookEvent.setProcessedAt(LocalDateTime.now());
                        webhookEventRepository.save(webhookEvent);
                    } else {
                        logger.warn("Payment order not found for Stripe event: {}", id);
                        webhookEvent.setStatus("FAILED");
                        webhookEvent.setErrorMessage("Order not found");
                        webhookEventRepository.save(webhookEvent);
                    }
                }
            } else if ("checkout.session.expired".equals(event.getType())
                    || "payment_intent.payment_failed".equals(event.getType())) {

                JsonNode root = objectMapper.readTree(payload);
                JsonNode dataObject = root.path("data").path("object");
                String id = dataObject.path("id").asText();

                if (id != null && !id.isEmpty()) {
                    PaymentOrder order = paymentOrderRepository.findByProviderAndProviderOrderId(
                            com.cryptonex.domain.PaymentProvider.STRIPE, id);
                    if (order != null) {
                        order.setStatus(PaymentOrderStatus.FAILED);
                        paymentOrderRepository.save(order);

                        webhookEvent.setStatus("PROCESSED_FAILURE");
                        webhookEventRepository.save(webhookEvent);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing Stripe event: {}", e.getMessage());
            webhookEvent.setStatus("FAILED");
            webhookEvent.setErrorMessage(e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Processing error");
        }
        return ResponseEntity.ok("Received");
    }

    /*
     * @PostMapping(value = "/razorpay", consumes = "application/json")
     * public ResponseEntity<String> handleRazorpayWebhook(
     * 
     * @RequestHeader("X-Razorpay-Signature") String signature,
     * 
     * @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String
     * eventIdHeader,
     * 
     * @RequestBody String payload) {
     * 
     * try {
     * Utils.verifyWebhookSignature(payload, signature, razorpayApiSecret);
     * } catch (Exception e) {
     * logger.error("Razorpay signature verification failed: {}", e.getMessage());
     * return
     * ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
     * }
     * 
     * // Parse basic info for idempotency
     * String eventId;
     * String eventType;
     * try {
     * JSONObject json = new JSONObject(payload);
     * eventId = (eventIdHeader != null) ? eventIdHeader : "RP_" +
     * System.currentTimeMillis();
     * eventType = json.getString("event");
     * } catch (Exception e) {
     * return ResponseEntity.badRequest().body("Invalid JSON");
     * }
     * 
     * // Persist Event
     * WebhookEvent webhookEvent = new WebhookEvent();
     * webhookEvent.setProvider(PaymentProvider.RAZORPAY);
     * webhookEvent.setEventId(eventId);
     * webhookEvent.setEventType(eventType);
     * webhookEvent.setPayload(payload);
     * 
     * try {
     * webhookEventRepository.save(webhookEvent);
     * } catch (DataIntegrityViolationException e) {
     * logger.info("Duplicate Razorpay event received: {}", eventId);
     * return ResponseEntity.ok("Duplicate event");
     * }
     * 
     * // Process Event
     * try {
     * if ("payment.captured".equals(eventType)) {
     * JSONObject json = new JSONObject(payload);
     * JSONObject payment =
     * json.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity"
     * );
     * 
     * String paymentLinkId = null;
     * if (json.has("payload") && json.getJSONObject("payload").has("payment_link"))
     * {
     * paymentLinkId =
     * json.getJSONObject("payload").getJSONObject("payment_link").getJSONObject(
     * "entity")
     * .getString("id");
     * }
     * 
     * if (paymentLinkId != null) {
     * Optional<PaymentOrder> orderOpt =
     * paymentOrderRepository.findByPaymentLinkId(paymentLinkId);
     * if (orderOpt.isPresent()) {
     * PaymentOrder order = orderOpt.get();
     * 
     * // Validate Amount and Currency
     * long amountReceived = payment.getLong("amount");
     * String currencyReceived = payment.getString("currency");
     * 
     * if (order.getAmountInPaise() != null && order.getAmountInPaise() !=
     * amountReceived) {
     * logger.warn("Amount mismatch for order {}: expected {}, got {}",
     * order.getId(),
     * order.getAmountInPaise(), amountReceived);
     * webhookEvent.setStatus("FAILED");
     * webhookEvent.setErrorMessage("Amount mismatch: expected " +
     * order.getAmountInPaise()
     * + ", got " + amountReceived);
     * webhookEventRepository.save(webhookEvent);
     * return ResponseEntity.ok("Amount mismatch");
     * }
     * 
     * if (order.getCurrency() != null &&
     * !order.getCurrency().equalsIgnoreCase(currencyReceived)) {
     * logger.warn("Currency mismatch for order {}: expected {}, got {}",
     * order.getId(),
     * order.getCurrency(), currencyReceived);
     * webhookEvent.setStatus("FAILED");
     * webhookEvent.setErrorMessage(
     * "Currency mismatch: expected " + order.getCurrency() + ", got " +
     * currencyReceived);
     * webhookEventRepository.save(webhookEvent);
     * return ResponseEntity.ok("Currency mismatch");
     * }
     * 
     * order.setStatus(PaymentOrderStatus.SUCCESS);
     * order.setProviderPaymentId(payment.getString("id"));
     * paymentOrderRepository.save(order);
     * 
     * webhookEvent.setStatus("SUCCESS");
     * webhookEvent.setProcessedAt(LocalDateTime.now());
     * webhookEventRepository.save(webhookEvent);
     * return ResponseEntity.ok("Processed");
     * }
     * }
     * 
     * // Fallback or other logic
     * webhookEvent.setStatus("MANUAL_REVIEW"); // If we can't auto-match
     * webhookEventRepository.save(webhookEvent);
     * }
     * } catch (Exception e) {
     * logger.error("Error processing Razorpay event: {}", e.getMessage());
     * webhookEvent.setStatus("FAILED");
     * webhookEvent.setErrorMessage(e.getMessage());
     * webhookEventRepository.save(webhookEvent);
     * }
     * 
     * return ResponseEntity.ok("Received");
     * }
     */
}
