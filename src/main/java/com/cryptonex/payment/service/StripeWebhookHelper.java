package com.cryptonex.payment.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.stereotype.Component;

@Component
public class StripeWebhookHelper {
    public Event constructEvent(String payload, String sigHeader, String secret) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, secret);
    }
}
