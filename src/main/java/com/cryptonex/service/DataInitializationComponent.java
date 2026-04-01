package com.cryptonex.service;

import com.cryptonex.domain.USER_ROLE;
import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializationComponent implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final com.cryptonex.repository.SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    public DataInitializationComponent(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            com.cryptonex.repository.SubscriptionPlanRepository subscriptionPlanRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Override
    public void run(String... args) {
        initializeDefaultUsers();
        initializeDefaultPlans();
    }

    private void initializeDefaultUsers() {
        createUserIfNotExists(
                "shivamsss123@gmail.com",
                "Shivam Jondhale",
                "Shivam123",
                USER_ROLE.ROLE_ADMIN);

        createUserIfNotExists(
                "vaibhavikahar11@gmail.com",
                "Vaibhavi Kahar",
                "vaibhavi123",
                USER_ROLE.ROLE_ADMIN);
    }

    private void createUserIfNotExists(String email, String fullName, String rawPassword, USER_ROLE role) {
        if (userRepository.findByEmail(email) == null) {
            User user = new User();
            user.setEmail(email);
            user.setFullName(fullName);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.getRoles().add(role);
            userRepository.save(user);
        }
    }

    private void initializeDefaultPlans() {
        // 1. Platform Plan - INR (Cashfree)
        createPlanIfNotExists(
                "CRYPTELLO_PRO_MONTHLY_INR",
                "Monthly Platform Subscription (INR)",
                new java.math.BigDecimal("1000"),
                "INR",
                1,
                com.cryptonex.domain.PlanType.PLATFORM,
                com.cryptonex.domain.PaymentProvider.CASHFREE);

        // 2. Platform Plan - USD (Stripe)
        createPlanIfNotExists(
                "CRYPTELLO_PRO_MONTHLY_USD",
                "Monthly Platform Subscription (USD)",
                new java.math.BigDecimal("15"), // $15
                "USD",
                1,
                com.cryptonex.domain.PlanType.PLATFORM,
                com.cryptonex.domain.PaymentProvider.STRIPE);

        // 3. Trader Plan - INR (Cashfree)
        createPlanIfNotExists(
                "TRADER_SUB_MONTHLY_INR",
                "Monthly Trader Subscription (INR)",
                new java.math.BigDecimal("500"),
                "INR",
                1,
                com.cryptonex.domain.PlanType.TRADER,
                com.cryptonex.domain.PaymentProvider.CASHFREE);
    }

    private void createPlanIfNotExists(String name, String description, java.math.BigDecimal price, String currency,
            int durationMonths,
            com.cryptonex.domain.PlanType planType, com.cryptonex.domain.PaymentProvider provider) {
        if (subscriptionPlanRepository.findByName(name) == null) {
            com.cryptonex.model.SubscriptionPlan plan = new com.cryptonex.model.SubscriptionPlan();
            plan.setName(name);
            plan.setDescription(description);
            plan.setPrice(price);
            plan.setCurrency(currency);
            plan.setDurationMonths(durationMonths);
            plan.setPlanType(planType);
            plan.setProviderType(provider);
            plan.setActive(true);
            subscriptionPlanRepository.save(plan);
        }
    }
}
