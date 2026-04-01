package com.cryptonex.payment.webhook;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cryptonex.event.WebhookReceivedEvent;
import com.cryptonex.model.WebhookEvent;
import com.cryptonex.repository.WebhookEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload) {

        meterRegistry.counter("cryptello.webhook.received", "provider", "stripe").increment();

        if (endpointSecret == null || endpointSecret.isEmpty()) {
            return new ResponseEntity<>("Webhook secret not configured", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (Exception e) {
            meterRegistry.counter("cryptello.webhook.error", "provider", "stripe", "reason", "invalid_signature")
                    .increment();
            return new ResponseEntity<>("Invalid Signature", HttpStatus.BAD_REQUEST);
        }

        if ("checkout.session.completed".equals(event.getType())) {

            // Idempotency check
            String eventId = event.getId();
            if (webhookEventRepository.existsByProviderAndEventId(com.cryptonex.domain.PaymentProvider.STRIPE,
                    eventId)) {
                return new ResponseEntity<>("Event already received", HttpStatus.OK);
            }

            // Save Webhook Event (Pending)
            WebhookEvent webhookEvent = new WebhookEvent();
            webhookEvent.setProvider(com.cryptonex.domain.PaymentProvider.STRIPE);
            webhookEvent.setEventId(eventId);
            webhookEvent.setPayload(payload);
            webhookEvent.setStatus("PENDING");
            webhookEventRepository.save(webhookEvent);

            // Publish Async Event
            eventPublisher.publishEvent(new WebhookReceivedEvent(this, webhookEvent));

        }

        return new ResponseEntity<>("OK", HttpStatus.OK);
    }
}
