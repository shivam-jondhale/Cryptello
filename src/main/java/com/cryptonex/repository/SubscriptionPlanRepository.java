package com.cryptonex.repository;

import com.cryptonex.domain.PlanType;
import com.cryptonex.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    List<SubscriptionPlan> findByPlanType(PlanType planType);

    List<SubscriptionPlan> findByTraderId(Long traderId);

    SubscriptionPlan findByName(String name);
}
