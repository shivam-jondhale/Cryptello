package com.cryptonex.payment.service;

import com.cryptonex.domain.PaymentProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentProviderFactory {

    private final Map<PaymentProvider, PaymentProviderStrategy> strategies;

    @Autowired
    public PaymentProviderFactory(List<PaymentProviderStrategy> strategyList) {
        strategies = strategyList.stream()
                .collect(Collectors.toMap(PaymentProviderStrategy::providerType, Function.identity()));
    }

    public PaymentProviderStrategy getProvider(PaymentProvider provider) {
        PaymentProviderStrategy strategy = strategies.get(provider);
        if (strategy == null) {
            throw new IllegalArgumentException("No provider found for type: " + provider);
        }
        return strategy;
    }
}
