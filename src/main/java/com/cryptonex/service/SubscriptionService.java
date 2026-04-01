package com.cryptonex.service;

import com.cryptonex.domain.USER_ROLE;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import com.cryptonex.repository.SubscriptionPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class SubscriptionService {

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional
    public SubscriptionPlan createTraderPlan(User authUser, String name, String description, BigDecimal price,
            int durationMonths) throws Exception {
        // Role Check
        // Role Check
        boolean isVerifiedTrader = authUser.getRoles().contains(USER_ROLE.ROLE_VERIFIED_TRADER);
        boolean isAdmin = authUser.getRoles().contains(USER_ROLE.ROLE_ADMIN);

        if (!isVerifiedTrader && !isAdmin) {
            throw new RuntimeException("Access Denied: Only Verified Traders or Admins can create trader plans.");
        }

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName(name);
        plan.setDescription(description);
        plan.setPrice(price);
        plan.setDurationMonths(durationMonths);
        plan.setPlanType(com.cryptonex.domain.PlanType.TRADER);

        // Link to Trader (Self)
        plan.setTrader(authUser);

        return subscriptionPlanRepository.save(plan);
    }

    public java.util.List<SubscriptionPlan> getTraderPlans(Long traderId) {
        return subscriptionPlanRepository.findByTraderId(traderId);
    }

    public java.util.List<SubscriptionPlan> getAllPlans() {
        return subscriptionPlanRepository.findAll();
    }

    public SubscriptionPlan getPlan(Long planId) throws Exception {
        return subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new Exception("Plan not found"));
    }
}
