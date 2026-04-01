package com.cryptonex.payment;

import com.cryptonex.domain.PaymentProvider;
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
import com.cryptonex.repository.UserRepository;
import com.cryptonex.response.PaymentResponse;
import com.cryptonex.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DomainModelIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentServiceImpl paymentService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private PaymentProviderFactory paymentProviderFactory;

    @MockBean
    private UserService userService; // Mock user service for JWT lookup if needed, or we can use real one if we set
                                     // up DB

    @MockBean
    private com.cryptonex.service.WalletService walletService;

    @MockBean
    private com.cryptonex.service.AlertService alertService;

    @MockBean
    private com.cryptonex.service.EmailService emailService;

    @MockBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    private PaymentProviderStrategy paymentProviderStrategy;
    private User user;
    private SubscriptionPlan platformPlan;
    private SubscriptionPlan traderPlan;

    @BeforeEach
    public void setup() {
        paymentProviderStrategy = org.mockito.Mockito.mock(PaymentProviderStrategy.class);
        when(paymentProviderFactory.getProvider(any(PaymentProvider.class))).thenReturn(paymentProviderStrategy);

        // Clear DB
        paymentOrderRepository.deleteAll();
        subscriptionRepository.deleteAll();
        subscriptionPlanRepository.deleteAll();
        userRepository.deleteAll();

        // Create User
        user = new User();
        user.setFullName("Domain Test User");
        user.setEmail("domain@test.com");
        user.setPassword("password");
        user = userRepository.save(user);

        // Create Plans
        platformPlan = new SubscriptionPlan();
        platformPlan.setName("CRYPTELLO_PRO_30D_INR");
        platformPlan.setPrice(new BigDecimal("49900"));
        platformPlan.setCurrency("INR");
        platformPlan.setDurationMonths(1);
        platformPlan.setPlanType(PlanType.PLATFORM);
        platformPlan.setProviderType(PaymentProvider.CASHFREE);
        platformPlan.setActive(true);
        subscriptionPlanRepository.save(platformPlan);

        traderPlan = new SubscriptionPlan();
        traderPlan.setName("TRADER_PREMIUM_30D_INR");
        traderPlan.setPrice(new BigDecimal("29900"));
        traderPlan.setCurrency("INR");
        traderPlan.setDurationMonths(1);
        traderPlan.setPlanType(PlanType.TRADER);
        traderPlan.setProviderType(PaymentProvider.CASHFREE);
        traderPlan.setActive(true);
        subscriptionPlanRepository.save(traderPlan);
    }

    @Test
    public void testP3_1_1_PaymentOrderInvariants() throws Exception {
        // Mock provider response
        when(paymentProviderStrategy.createOrder(any(PaymentOrder.class)))
                .thenReturn(new PaymentResponse("https://cashfree.com/pay", "cf_order_inv"));

        // Trigger subscription payment creation
        // We use service directly to avoid JWT mocking complexity for this specific
        // domain test,
        // but the logic is the same.
        PaymentResponse response = paymentService.createSubscriptionPayment(user, platformPlan.getId(), null);

        assertNotNull(response);

        // Fetch created PaymentOrder
        PaymentOrder order = paymentOrderRepository.findByProviderAndProviderOrderId(PaymentProvider.CASHFREE,
                "cf_order_inv");
        assertNotNull(order);

        // Verify Invariants
        assertEquals(new BigDecimal("49900"), order.getAmount());
        assertEquals("INR", order.getCurrency());
        assertEquals(platformPlan.getId(), order.getPlan().getId());
        assertEquals(PaymentProvider.CASHFREE, order.getProvider());
        assertEquals(com.cryptonex.domain.PaymentOrderStatus.PENDING, order.getStatus());
        assertEquals("cf_order_inv", order.getProviderOrderId());
        assertEquals("https://cashfree.com/pay", order.getProviderPaymentLinkUrl());
    }

    @Test
    public void testP3_1_2_SubscriptionPlanVariants() {
        // Fetch plans
        SubscriptionPlan fetchedPlatform = subscriptionPlanRepository.findById(platformPlan.getId()).orElseThrow();
        SubscriptionPlan fetchedTrader = subscriptionPlanRepository.findById(traderPlan.getId()).orElseThrow();

        // Verify Platform Plan
        assertEquals(PlanType.PLATFORM, fetchedPlatform.getPlanType());
        assertEquals(PaymentProvider.CASHFREE, fetchedPlatform.getProviderType());
        assertEquals(1, fetchedPlatform.getDurationMonths());
        assertEquals(new BigDecimal("49900"), fetchedPlatform.getPrice());
        assertEquals("INR", fetchedPlatform.getCurrency());
        assertTrue(fetchedPlatform.isActive());

        // Verify Trader Plan
        assertEquals(PlanType.TRADER, fetchedTrader.getPlanType());
        assertEquals(PaymentProvider.CASHFREE, fetchedTrader.getProviderType());
        assertEquals(1, fetchedTrader.getDurationMonths());
        assertEquals(new BigDecimal("29900"), fetchedTrader.getPrice());
        assertEquals("INR", fetchedTrader.getCurrency());
        assertTrue(fetchedTrader.isActive());
    }

    @Test
    public void testP3_1_3_SubscriptionCreationAndRenewal() {
        // Mock provider
        when(paymentProviderStrategy.createOrder(any(PaymentOrder.class)))
                .thenReturn(new PaymentResponse("https://link", "order_renew"));

        // 1. First Payment
        PaymentOrder order1 = new PaymentOrder();
        order1.setUser(user);
        order1.setPlan(platformPlan);
        order1.setAmount(platformPlan.getPrice());
        order1.setCurrency(platformPlan.getCurrency());
        order1.setProvider(platformPlan.getProviderType());
        order1.setStatus(com.cryptonex.domain.PaymentOrderStatus.PENDING);
        order1.setProviderOrderId("order_renew_1");
        paymentOrderRepository.save(order1);

        // Process Success
        paymentService.processPaymentSuccess(order1);

        // Verify Subscription
        List<Subscription> subs = subscriptionRepository.findAll();
        assertEquals(1, subs.size());
        Subscription sub = subs.get(0);
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals(java.time.LocalDate.now(), sub.getCurrentPeriodStart().toLocalDate());
        assertEquals(java.time.LocalDate.now().plusDays(30), sub.getCurrentPeriodEnd().toLocalDate());

        java.time.LocalDateTime firstEndDate = sub.getCurrentPeriodEnd();

        // 2. Second Payment (Renewal)
        PaymentOrder order2 = new PaymentOrder();
        order2.setUser(user);
        order2.setPlan(platformPlan);
        order2.setAmount(platformPlan.getPrice());
        order2.setCurrency(platformPlan.getCurrency());
        order2.setProvider(platformPlan.getProviderType());
        order2.setStatus(com.cryptonex.domain.PaymentOrderStatus.PENDING);
        order2.setProviderOrderId("order_renew_2");
        paymentOrderRepository.save(order2);

        // Process Success
        paymentService.processPaymentSuccess(order2);

        // Verify Renewal
        Subscription renewedSub = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(SubscriptionStatus.ACTIVE, renewedSub.getStatus());
        // Start date should ideally remain same or update?
        // Requirement: "currentPeriodEnd has moved 30 days further (existing end date +
        // 30)"
        // It doesn't explicitly say start date changes, but usually start date of
        // *current period* might update.
        // However, checking End Date is the critical part.
        assertEquals(firstEndDate.plusDays(30).toLocalDate(), renewedSub.getCurrentPeriodEnd().toLocalDate());
    }

    @Test
    public void testP3_1_4_TraderVsPlatformSubscriptions() {
        Long traderId = 123L;

        // Mock provider
        when(paymentProviderStrategy.createOrder(any(PaymentOrder.class)))
                .thenReturn(new PaymentResponse("https://link", "order_trader"));

        // 1. Create Trader Subscription
        PaymentOrder order = new PaymentOrder();
        order.setUser(user);
        order.setPlan(traderPlan);
        order.setAmount(traderPlan.getPrice());
        order.setCurrency(traderPlan.getCurrency());
        order.setProvider(traderPlan.getProviderType());
        order.setStatus(com.cryptonex.domain.PaymentOrderStatus.PENDING);
        order.setProviderOrderId("order_trader_1");
        order.setPurpose("TRADER_SUBSCRIPTION:traderId=" + traderId);
        paymentOrderRepository.save(order);

        paymentService.processPaymentSuccess(order);

        // Verify
        List<Subscription> subs = subscriptionRepository.findAll();
        // We might have subs from other tests if DB wasn't cleared, but @BeforeEach
        // clears it.
        assertEquals(1, subs.size());
        Subscription sub = subs.get(0);
        assertEquals(PlanType.TRADER, sub.getPlan().getPlanType());
        assertEquals(traderId, sub.getTraderId());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());

        // 2. Platform Subscription (No Trader ID)
        paymentRepositoryClear(); // Helper to clear for clean state

        PaymentOrder platOrder = new PaymentOrder();
        platOrder.setUser(user);
        platOrder.setPlan(platformPlan);
        platOrder.setAmount(platformPlan.getPrice());
        platOrder.setCurrency(platformPlan.getCurrency());
        platOrder.setProvider(platformPlan.getProviderType());
        platOrder.setStatus(com.cryptonex.domain.PaymentOrderStatus.PENDING);
        platOrder.setProviderOrderId("order_plat_1");
        platOrder.setPurpose("PLATFORM_SUBSCRIPTION");
        paymentOrderRepository.save(platOrder);

        paymentService.processPaymentSuccess(platOrder);

        Subscription platSub = subscriptionRepository.findAll().get(0);
        assertEquals(PlanType.PLATFORM, platSub.getPlan().getPlanType());
        assertNull(platSub.getTraderId());
    }

    private void paymentRepositoryClear() {
        subscriptionRepository.deleteAll();
        paymentOrderRepository.deleteAll();
    }
}
