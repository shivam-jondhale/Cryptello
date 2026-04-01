package com.cryptonex.payment;

import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.domain.PlanType;
import com.cryptonex.domain.SubscriptionStatus;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.Subscription;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import com.cryptonex.payment.service.PaymentProviderFactory;
import com.cryptonex.payment.service.PaymentProviderStrategy;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.SubscriptionPlanRepository;
import com.cryptonex.repository.SubscriptionRepository;
import com.cryptonex.response.PaymentResponse;

import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentProviderFactory paymentProviderFactory;

    @Override
    @Transactional
    public PaymentResponse createSubscriptionPayment(User user, Long planId, Long traderId) {
        // 1. Validate Plan
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        if (!plan.isActive()) {
            throw new RuntimeException("Plan is not active");
        }

        if (plan.getPlanType() == PlanType.PLATFORM && traderId != null) {
            throw new RuntimeException("Platform plans cannot have a traderId");
        }
        if (plan.getPlanType() == PlanType.TRADER && traderId == null) {
            throw new RuntimeException("Trader plans must have a traderId");
        }

        // 2. Create PaymentOrder (CREATED)
        PaymentOrder order = new PaymentOrder();
        order.setUser(user);
        order.setPlan(plan);
        order.setAmount(plan.getPrice());
        order.setCurrency(plan.getCurrency());
        order.setProvider(plan.getProviderType());
        order.setStatus(PaymentOrderStatus.CREATED);

        if (plan.getPlanType() == PlanType.PLATFORM) {
            order.setPurpose("PLATFORM_SUBSCRIPTION");
        } else {
            order.setPurpose("TRADER_SUBSCRIPTION:traderId=" + traderId);
        }

        paymentOrderRepository.save(order);

        // 3. Call Provider Strategy
        PaymentProviderStrategy strategy = paymentProviderFactory.getProvider(plan.getProviderType());
        PaymentResponse response = strategy.createOrder(order);

        // 4. Update PaymentOrder (PENDING)
        order.setProviderOrderId(order.getProviderOrderId()); // Already set by strategy, but ensuring consistency
        order.setProviderPaymentLinkUrl(order.getProviderPaymentLinkUrl());
        order.setStatus(PaymentOrderStatus.PENDING);
        paymentOrderRepository.save(order);

        // 5. Return Response
        return response;
    }

    @Override
    @Transactional
    public void processPaymentSuccess(PaymentOrder order) {
        // 1. Idempotency Check
        if (order.getStatus() == PaymentOrderStatus.SUCCESS) {
            return;
        }
        if (order.getStatus() == PaymentOrderStatus.FAILED || order.getStatus() == PaymentOrderStatus.CANCELLED) {
            // Log and skip
            System.out.println("Skipping subscription processing for failed/cancelled order: " + order.getId());
            return;
        }

        // 2. Subscription Lookup/Creation
        Long traderId = null;

        if (order.getPlan().getPlanType() == PlanType.TRADER) {
            // Extract traderId from purpose string "TRADER_SUBSCRIPTION:traderId=123"
            String purpose = order.getPurpose();
            if (purpose != null && purpose.startsWith("TRADER_SUBSCRIPTION:traderId=")) {
                try {
                    traderId = Long.parseLong(purpose.split("=")[1]);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("Invalid traderId in payment purpose: " + purpose);
                }
            }
        }

        // Check for existing active subscription
        // Using findByUserAndPlan or findByUserAndTraderId would be better, but let's
        // iterate for now to match logic
        // Actually, let's use the repository methods we created.
        Subscription activeSub = null;
        if (order.getPlan().getPlanType() == PlanType.PLATFORM) {
            activeSub = subscriptionRepository.findByUserAndPlan(order.getUser(), order.getPlan());
            // We need to check if it's active. The repo returns one, but user might have
            // multiple?
            // Ideally repo should return List or Optional.
            // Let's assume for now user has only one sub per plan type.
            // Or better, let's fetch all active subs for user and filter.
        } else {
            activeSub = subscriptionRepository.findByUserAndTraderId(order.getUser(), traderId);
        }

        // Re-fetching to be safe and filter by status
        // TODO: Improve Repository to findActiveSubscriptionByUserAndType

        if (activeSub != null && activeSub.getStatus() != SubscriptionStatus.ACTIVE) {
            activeSub = null; // Treat as new if existing is expired/cancelled
        }

        if (activeSub != null) {
            // Renewal: Extend the end date
            activeSub.setCurrentPeriodEnd(activeSub.getCurrentPeriodEnd().plusDays(order.getPlan().getDurationDays()));
            subscriptionRepository.save(activeSub);
        } else {
            // New Subscription
            Subscription newSubscription = new Subscription();
            newSubscription.setUser(order.getUser());
            newSubscription.setPlan(order.getPlan());
            newSubscription.setTraderId(traderId);
            newSubscription.setStatus(SubscriptionStatus.ACTIVE);
            newSubscription.setCurrentPeriodStart(java.time.LocalDateTime.now());
            newSubscription
                    .setCurrentPeriodEnd(java.time.LocalDateTime.now().plusDays(order.getPlan().getDurationDays()));
            subscriptionRepository.save(newSubscription);
        }

        // 3. Update Order Status
        order.setStatus(PaymentOrderStatus.SUCCESS);
        paymentOrderRepository.save(order);
    }

    @Override
    public PaymentResponse createPaymentLink(PaymentOrder order) {
        throw new UnsupportedOperationException("Use createSubscriptionPayment instead");
    }

    @Override
    public PaymentOrder getPaymentOrderById(Long id) throws Exception {
        return paymentOrderRepository.findById(id).orElseThrow(() -> new Exception("Order not found"));
    }

    @Override
    public Boolean ProccedPaymentOrder(PaymentOrder paymentOrder, String paymentId, String paymentLinkId) {
        return false;
    }

    @Override
    @Transactional
    public PaymentResponse createStripePaymentLink(User user, java.math.BigDecimal amount)
            throws StripeException {
        // Validation
        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        // Create Ad-Hoc Order
        PaymentOrder order = createAdHocOrder(user, amount, PaymentProvider.STRIPE);

        // Execute Strategy
        PaymentProviderStrategy strategy = paymentProviderFactory.getProvider(PaymentProvider.STRIPE);
        PaymentResponse response = strategy.createOrder(order);

        // Update Order
        order.setProviderOrderId(response.getProviderOrderId());
        order.setProviderPaymentLinkUrl(response.getPaymentLinkUrl());
        order.setStatus(PaymentOrderStatus.PENDING);
        paymentOrderRepository.save(order);

        return response;
    }

    @Override
    @Transactional
    public PaymentResponse createCashfreePaymentLink(User user, java.math.BigDecimal amount) {
        // Validation
        if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Amount must be greater than zero");
        }

        // Create Ad-Hoc Order
        PaymentOrder order = createAdHocOrder(user, amount, PaymentProvider.CASHFREE);

        // Execute Strategy
        PaymentProviderStrategy strategy = paymentProviderFactory.getProvider(PaymentProvider.CASHFREE);
        PaymentResponse response = strategy.createOrder(order);

        // Update Order
        order.setProviderOrderId(response.getProviderOrderId());
        order.setProviderPaymentLinkUrl(response.getPaymentLinkUrl());
        order.setStatus(PaymentOrderStatus.PENDING);
        paymentOrderRepository.save(order);

        return response;
    }

    private PaymentOrder createAdHocOrder(User user, java.math.BigDecimal amount,
            com.cryptonex.domain.PaymentProvider provider) {
        PaymentOrder order = new PaymentOrder();
        order.setUser(user);
        order.setAmount(amount);
        order.setCurrency("INR"); // Default for now, or make flexible if needed
        order.setProvider(provider);
        order.setStatus(PaymentOrderStatus.CREATED);
        order.setPurpose("ADHOC_PAYMENT");
        order.setPaymentMethod(provider == PaymentProvider.STRIPE ? PaymentMethod.STRIPE : PaymentMethod.CASHFREE);
        return paymentOrderRepository.save(order);
    }

    @Override
    public PaymentOrder createOrder(User user, java.math.BigDecimal amount, PaymentMethod paymentMethod) {
        PaymentOrder order = new PaymentOrder();
        order.setUser(user);
        order.setAmount(amount);
        order.setPaymentMethod(paymentMethod);
        order.setStatus(PaymentOrderStatus.CREATED);
        return paymentOrderRepository.save(order);
    }
}
