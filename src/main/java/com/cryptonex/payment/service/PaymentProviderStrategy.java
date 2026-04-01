package com.cryptonex.payment.service;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.response.PaymentResponse;

public interface PaymentProviderStrategy {
    PaymentProvider providerType();

    PaymentResponse createOrder(PaymentOrder order);
}
