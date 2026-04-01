package com.cryptonex.payment.webhook;

import com.cryptonex.event.WebhookReceivedEvent;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.WebhookEvent;
import com.cryptonex.payment.PaymentService;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.WebhookEventRepository;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WebhookEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebhookEventListener.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Async("webhookTaskExecutor")
    @EventListener
    @Transactional
    public void handleWebhookReceived(WebhookReceivedEvent event) {
        WebhookEvent webhookEvent = event.getWebhookEvent();
        logger.info("Processing webhook event: {}", webhookEvent.getEventId());

        try {
            // Need to parse the payload again or store structured data.
            // For simplicity in this phase, we assume the payload is raw JSON and we
            // construct Event from it if needed,
            // OR simpler: we trust the initial validation and just re-parse to get the ID.

            // In a real optimized system, we might pass the 'Event' object or parsed fields
            // in the ApplicationEvent
            // to avoid re-parsing, but passing entity is safer for transaction boundaries.

            // Let's re-construct the necessary Stripe object for processing.
            // CAUTION: constructEvent checks signature which needs headers.
            // We should have stored what we need.

            // Actually, StripeWebhookController already validated signature.
            // We just need to parse JSON.
            // For now, let's assume we can map the payload to the domain action.

            // Re-parsing logic (simplified)
            Event stripeEvent = Event.GSON.fromJson(webhookEvent.getPayload(), Event.class);

            if ("checkout.session.completed".equals(stripeEvent.getType())) {
                Session session = (Session) stripeEvent.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null) {
                    String providerOrderId = session.getId();
                    PaymentOrder paymentOrder = paymentOrderRepository.findByProviderAndProviderOrderId(
                            com.cryptonex.domain.PaymentProvider.STRIPE, providerOrderId);

                    if (paymentOrder != null) {
                        paymentService.processPaymentSuccess(paymentOrder);
                    } else {
                        // This might be a legitimate case (e.g. order expired/deleted), but log it.
                        logger.warn("PaymentOrder not found for Stripe Session: {}", providerOrderId);
                    }
                }
            }

            webhookEvent.setStatus("PROCESSED");
            webhookEventRepository.save(webhookEvent);

        } catch (Exception e) {
            logger.error("Failed to process webhook event: {}", webhookEvent.getEventId(), e);
            webhookEvent.setStatus("FAILED");
            webhookEventRepository.save(webhookEvent);
        }
    }
}
