package com.cryptonex.domain;

import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.SubscriptionPlanRepository;
import com.cryptonex.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PaymentOrderConsistencyTest {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testPaymentOrderCreationFromPlan() {
        // Setup
        User user = new User();
        user.setFullName("Consistency User");
        user.setEmail("consist" + System.currentTimeMillis() + "@example.com");
        user.setPassword("pass");
        userRepository.save(user);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("Consistency Plan");
        plan.setPrice(new java.math.BigDecimal("999"));
        plan.setCurrency("INR");
        plan.setProviderType(PaymentProvider.CASHFREE);
        plan.setDurationMonths(1);
        planRepository.save(plan);

        // Simulate Payment Order Creation Logic
        PaymentOrder order = new PaymentOrder();
        order.setUser(user);
        order.setPlan(plan);
        order.setAmount(plan.getPrice());
        order.setCurrency(plan.getCurrency());
        order.setProvider(plan.getProviderType());
        order.setPurpose("PLATFORM_SUBSCRIPTION");
        order.setStatus(PaymentOrderStatus.CREATED);

        paymentOrderRepository.save(order);

        // Verify Consistency
        PaymentOrder savedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();

        assertEquals(plan.getPrice(), savedOrder.getAmount());
        assertEquals(plan.getCurrency(), savedOrder.getCurrency());
        assertEquals(plan.getProviderType(), savedOrder.getProvider());
        assertEquals("PLATFORM_SUBSCRIPTION", savedOrder.getPurpose());
        assertNotNull(savedOrder.getPlan());
    }
}
