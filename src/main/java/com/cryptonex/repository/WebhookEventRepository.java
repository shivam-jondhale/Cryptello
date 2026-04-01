package com.cryptonex.repository;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByProviderAndEventId(PaymentProvider provider, String eventId);

    boolean existsByProviderAndEventId(PaymentProvider provider, String eventId);
}
