package com.cryptonex.repository;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    PaymentOrder findByProviderAndProviderOrderId(PaymentProvider provider, String providerOrderId);
}
