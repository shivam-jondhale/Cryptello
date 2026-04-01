package com.cryptonex.payment.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Component;

@Component
public class StripeClientWrapper {
    public Session createSession(SessionCreateParams params) throws StripeException {
        return Session.create(params);
    }
}
