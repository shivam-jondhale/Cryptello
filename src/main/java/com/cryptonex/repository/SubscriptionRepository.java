package com.cryptonex.repository;

import com.cryptonex.model.Subscription;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Subscription findByUserAndPlan(User user, SubscriptionPlan plan);

    Subscription findByUserAndTraderId(User user, Long traderId);

    java.util.List<Subscription> findByUser(User user);
}
