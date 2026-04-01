package com.cryptonex.payment.service;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.response.PaymentResponse;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripePaymentProvider implements PaymentProviderStrategy {

    @Value("${stripe.api.key}")
    private String apiKey;

    @Override
    public PaymentProvider providerType() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public PaymentResponse createOrder(PaymentOrder order) {
        try {
            Stripe.apiKey = apiKey;

            SessionCreateParams params = SessionCreateParams.builder()
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl("http://localhost:3000/payment/success?order_id=" + order.getId()) // TODO:
                                                                                                      // Configurable
                    .setCancelUrl("http://localhost:3000/payment/cancel")
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(order.getCurrency())
                                    .setUnitAmount(
                                            order.getAmount().multiply(new java.math.BigDecimal("100")).longValue())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(order.getPlan() != null ? order.getPlan().getName()
                                                    : "Ad-Hoc Payment")
                                            .setDescription(order.getPlan() != null ? order.getPlan().getDescription()
                                                    : "One-time payment")
                                            .build())
                                    .build())
                            .build())
                    .setCustomerEmail(order.getUser().getEmail())
                    .build();

            Session session = Session.create(params);

            order.setProviderOrderId(session.getId());
            order.setProviderPaymentLinkUrl(session.getUrl());

            PaymentResponse res = new PaymentResponse();
            res.setPaymentLinkUrl(session.getUrl());
            res.setProviderOrderId(session.getId());
            return res;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create Stripe session", e);
        }
    }
}
