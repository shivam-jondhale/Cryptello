package com.cryptonex.config;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.domain.PlanType;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.repository.SubscriptionPlanRepository;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private SubscriptionPlanRepository planRepository;

    @Override
    public void run(String... args) throws Exception {
        if (planRepository.count() == 0) {
            // Platform Plan (INR, Cashfree)
            SubscriptionPlan platformPlanInr = new SubscriptionPlan();
            platformPlanInr.setName("CRYPTELLO_PRO_30D_INR");
            platformPlanInr.setDescription("Platform Pro Access (Monthly)");
            platformPlanInr.setPrice(new BigDecimal("999.00")); // 999.00 INR
            platformPlanInr.setCurrency("INR");
            platformPlanInr.setDurationMonths(1);
            platformPlanInr.setPlanType(PlanType.PLATFORM);
            platformPlanInr.setProviderType(PaymentProvider.CASHFREE);
            planRepository.save(platformPlanInr);

            // Platform Plan (USD, Stripe)
            SubscriptionPlan platformPlanUsd = new SubscriptionPlan();
            platformPlanUsd.setName("CRYPTELLO_PRO_30D_USD");
            platformPlanUsd.setDescription("Platform Pro Access (Monthly)");
            platformPlanUsd.setPrice(new BigDecimal("15.00")); // 15.00 USD
            platformPlanUsd.setCurrency("USD");
            platformPlanUsd.setDurationMonths(1);
            platformPlanUsd.setPlanType(PlanType.PLATFORM);
            platformPlanUsd.setProviderType(PaymentProvider.STRIPE);
            planRepository.save(platformPlanUsd);

            // Trader Plan (INR, Cashfree)
            SubscriptionPlan traderPlanInr = new SubscriptionPlan();
            traderPlanInr.setName("TRADER_PREMIUM_30D_INR");
            traderPlanInr.setDescription("Trader Premium Access (Monthly)");
            traderPlanInr.setPrice(new BigDecimal("1999.00")); // 1999.00 INR
            traderPlanInr.setCurrency("INR");
            traderPlanInr.setDurationMonths(1);
            traderPlanInr.setPlanType(PlanType.TRADER);
            traderPlanInr.setProviderType(PaymentProvider.CASHFREE);
            planRepository.save(traderPlanInr);
        }
    }
}
