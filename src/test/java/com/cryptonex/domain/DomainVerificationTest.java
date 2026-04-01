package com.cryptonex.domain;

import com.cryptonex.model.Subscription;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import com.cryptonex.repository.SubscriptionPlanRepository;
import com.cryptonex.repository.SubscriptionRepository;
import com.cryptonex.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DomainVerificationTest {

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testSeedPlans() {
        // Create Platform Plan
        SubscriptionPlan platformPlan = new SubscriptionPlan();
        platformPlan.setName("Cryptello Pro Monthly");
        platformPlan.setPrice(new java.math.BigDecimal("499")); // 499 INR
        platformPlan.setCurrency("INR");
        platformPlan.setDurationMonths(1);
        platformPlan.setPlanType(PlanType.PLATFORM);
        platformPlan.setProviderType(PaymentProvider.CASHFREE);

        planRepository.save(platformPlan);

        // Create Trader Plan
        SubscriptionPlan traderPlan = new SubscriptionPlan();
        traderPlan.setName("Trader X Premium");
        traderPlan.setPrice(new java.math.BigDecimal("199"));
        traderPlan.setCurrency("INR");
        traderPlan.setDurationMonths(1);
        traderPlan.setPlanType(PlanType.TRADER);
        traderPlan.setProviderType(PaymentProvider.CASHFREE);

        planRepository.save(traderPlan);

        // Verify
        assertEquals(2, planRepository.count());
        assertNotNull(platformPlan.getId());
        assertNotNull(traderPlan.getId());
    }

    @Test
    public void testSubscriptionBehavior() {
        // Setup User and Plan
        User user = new User();
        user.setFullName("Sub Test User");
        user.setEmail("subtest" + System.currentTimeMillis() + "@example.com");
        user.setPassword("pass");
        userRepository.save(user);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("Test Plan");
        plan.setDurationMonths(1);
        planRepository.save(plan);

        // Create Subscription (Simulate Payment Success)
        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(java.time.LocalDateTime.now());
        sub.setCurrentPeriodEnd(java.time.LocalDateTime.now().plusMonths(plan.getDurationMonths()));

        subscriptionRepository.save(sub);

        // Verify Initial State
        assertNotNull(sub.getId());
        assertEquals(java.time.LocalDate.now().plusDays(30), sub.getCurrentPeriodEnd().toLocalDate());

        // Simulate Renewal (Extend by 1 month)
        sub.setCurrentPeriodEnd(sub.getCurrentPeriodEnd().plusMonths(plan.getDurationMonths()));
        subscriptionRepository.save(sub);

        // Verify Renewal
        Subscription reloaded = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(java.time.LocalDate.now().plusDays(60), reloaded.getCurrentPeriodEnd().toLocalDate());
    }
}
