package com.cryptonex.payment;

import com.cryptonex.domain.PaymentOrderStatus;
import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.domain.PlanType;
import com.cryptonex.domain.SubscriptionStatus;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.Subscription;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import com.cryptonex.payment.service.StripeWebhookHelper;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.SubscriptionPlanRepository;
import com.cryptonex.repository.SubscriptionRepository;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PaymentEdgeCaseTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private PaymentOrderRepository paymentOrderRepository;

        @Autowired
        private SubscriptionPlanRepository subscriptionPlanRepository;

        @Autowired
        private SubscriptionRepository subscriptionRepository;

        @Autowired
        private UserRepository userRepository;

        @MockBean
        private StripeWebhookHelper stripeWebhookHelper;

        @MockBean
        private UserService userService;
        @MockBean
        private com.cryptonex.service.WalletService walletService;
        @MockBean
        private com.cryptonex.service.AlertService alertService;
        @MockBean
        private com.cryptonex.service.EmailService emailService;
        @MockBean
        private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

        private User user;
        private SubscriptionPlan platformPlan;
        private SubscriptionPlan traderPlan;
        private ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        public void setup() {
                paymentOrderRepository.deleteAll();
                subscriptionRepository.deleteAll();
                subscriptionPlanRepository.deleteAll();
                userRepository.deleteAll();

                user = new User();
                user.setFullName("Edge Case User");
                user.setEmail("edge@test.com");
                user.setPassword("password");
                user = userRepository.save(user);

                platformPlan = new SubscriptionPlan();
                platformPlan.setName("PLATFORM_PLAN");
                platformPlan.setPrice(new java.math.BigDecimal("10.00")); // $10
                platformPlan.setCurrency("USD");
                platformPlan.setDurationMonths(1);
                platformPlan.setPlanType(PlanType.PLATFORM);
                platformPlan.setProviderType(PaymentProvider.STRIPE);
                platformPlan.setActive(true);
                subscriptionPlanRepository.save(platformPlan);

                traderPlan = new SubscriptionPlan();
                traderPlan.setName("TRADER_PLAN");
                traderPlan.setPrice(new java.math.BigDecimal("500"));
                traderPlan.setCurrency("INR");
                traderPlan.setDurationMonths(1);
                traderPlan.setPlanType(PlanType.TRADER);
                traderPlan.setProviderType(PaymentProvider.CASHFREE);
                traderPlan.setActive(true);
                subscriptionPlanRepository.save(traderPlan);
        }

        @Test
        public void testP3_5_1_UserAbandonsPayment() {
                // 1. Create Order
                PaymentOrder order = new PaymentOrder();
                order.setUser(user);
                order.setPlan(platformPlan);
                order.setAmount(new java.math.BigDecimal("10.00"));
                order.setCurrency("USD");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(PaymentOrderStatus.PENDING);
                paymentOrderRepository.save(order);

                // 2. Simulate Abandonment (Time passes, no webhook)
                // In a real test, we just check state immediately as "no webhook arrived"

                // 3. Verify State
                PaymentOrder checkOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.PENDING, checkOrder.getStatus());

                List<Subscription> subs = subscriptionRepository.findAll();
                assertTrue(subs.isEmpty(), "No subscription should be created");
        }

        @Test
        public void testP3_5_2_LateWebhook() throws Exception {
                // 1. Create Order (PENDING)
                PaymentOrder order = new PaymentOrder();
                order.setUser(user);
                order.setPlan(platformPlan);
                order.setAmount(new java.math.BigDecimal("10.00"));
                order.setCurrency("USD");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setProviderOrderId("cs_late_webhook");
                paymentOrderRepository.save(order);

                // 2. Simulate Delay (conceptually)
                // 3. Trigger Success Webhook
                Event mockEvent = mock(Event.class);
                when(mockEvent.getId()).thenReturn("evt_late");
                when(mockEvent.getType()).thenReturn("checkout.session.completed");
                when(stripeWebhookHelper.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);

                String payload = "{"
                                + "\"data\": {"
                                + "  \"object\": {"
                                + "    \"id\": \"cs_late_webhook\","
                                + "    \"amount_total\": 1000,"
                                + "    \"currency\": \"usd\""
                                + "  }"
                                + "}"
                                + "}";

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", "valid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                // 4. Verify Success
                PaymentOrder checkOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.SUCCESS, checkOrder.getStatus());

                List<Subscription> subs = subscriptionRepository.findAll();
                assertEquals(1, subs.size());
                assertEquals(SubscriptionStatus.ACTIVE, subs.get(0).getStatus());
        }

        @Test
        public void testP3_5_3_FailedPaymentEvent_Stripe() throws Exception {
                // 1. Create Order
                PaymentOrder order = new PaymentOrder();
                order.setUser(user);
                order.setPlan(platformPlan);
                order.setAmount(new java.math.BigDecimal("10.00"));
                order.setCurrency("USD");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setProviderOrderId("cs_failed_stripe");
                paymentOrderRepository.save(order);

                // 2. Trigger Failed Webhook (payment_intent.payment_failed)
                Event mockEvent = mock(Event.class);
                when(mockEvent.getId()).thenReturn("evt_failed");
                when(mockEvent.getType()).thenReturn("payment_intent.payment_failed");
                when(stripeWebhookHelper.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);

                String payload = "{"
                                + "\"data\": {"
                                + "  \"object\": {"
                                + "    \"id\": \"pi_failed_stripe\"," // PaymentIntent ID
                                + "    \"amount\": 1000,"
                                + "    \"currency\": \"usd\""
                                + "  }"
                                + "}"
                                + "}";

                // Note: We need to link PI to Order.
                // If our order stores Session ID, we might need to look up by that.
                // But let's assume for this test we stored PI ID or we can lookup by metadata.
                // Or simpler: use checkout.session.expired if supported.
                // Let's stick to payment_intent.payment_failed.
                // If the controller expects providerOrderId to match, we need to ensure it
                // matches.
                // But Stripe Session ID != Payment Intent ID.
                // This reveals a complexity: we store Session ID.
                // A payment_failed event comes from PaymentIntent.
                // The Session object in 'checkout.session.completed' has 'payment_intent'
                // field.
                // If we only store Session ID, we can't easily match a PaymentIntent failure
                // unless we stored PI ID too.
                // OR we handle 'checkout.session.expired'.

                // Let's try 'checkout.session.expired' which has the Session object.
                when(mockEvent.getType()).thenReturn("checkout.session.expired");
                payload = "{"
                                + "\"data\": {"
                                + "  \"object\": {"
                                + "    \"id\": \"cs_failed_stripe\"," // Matches stored ID
                                + "    \"amount_total\": 1000,"
                                + "    \"currency\": \"usd\""
                                + "  }"
                                + "}"
                                + "}";

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", "valid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                // 3. Verify FAILED
                PaymentOrder checkOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.FAILED, checkOrder.getStatus());

                List<Subscription> subs = subscriptionRepository.findAll();
                assertTrue(subs.isEmpty());
        }

        @Test
        public void testP3_5_3_FailedPaymentEvent_Cashfree() throws Exception {
                // 1. Create Order
                PaymentOrder order = new PaymentOrder();
                order.setUser(user);
                order.setPlan(traderPlan);
                order.setAmount(new java.math.BigDecimal("500")); // 500 INR
                order.setCurrency("INR");
                order.setProvider(PaymentProvider.CASHFREE);
                order.setStatus(PaymentOrderStatus.PENDING);
                order.setProviderOrderId("cf_failed_link");
                paymentOrderRepository.save(order);

                // 2. Trigger Failed Webhook (FAILED status)
                Map<String, Object> payloadMap = new HashMap<>();
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("link_id", "cf_failed_link");
                dataMap.put("link_status", "FAILED"); // Explicit FAILED
                dataMap.put("link_amount", 500.00);
                dataMap.put("link_currency", "INR");
                payloadMap.put("data", dataMap);

                String payload = objectMapper.writeValueAsString(payloadMap);

                mockMvc.perform(post("/api/webhooks/cashfree")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                // 3. Verify FAILED
                PaymentOrder checkOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(PaymentOrderStatus.FAILED, checkOrder.getStatus());

                List<Subscription> subs = subscriptionRepository.findAll();
                assertTrue(subs.isEmpty());
        }

        @Test
        public void testP3_5_4_PlatformVsTraderPlan() throws Exception {
                // 1. Platform Plan Success
                PaymentOrder pOrder = new PaymentOrder();
                pOrder.setUser(user);
                pOrder.setPlan(platformPlan);
                pOrder.setAmount(new java.math.BigDecimal("10.00"));
                pOrder.setCurrency("USD");
                pOrder.setProvider(PaymentProvider.STRIPE);
                pOrder.setStatus(PaymentOrderStatus.PENDING);
                pOrder.setProviderOrderId("cs_platform_success");
                pOrder.setPurpose("PLATFORM_SUBSCRIPTION");
                paymentOrderRepository.save(pOrder);

                // Trigger Webhook
                Event mockEvent = mock(Event.class);
                when(mockEvent.getId()).thenReturn("evt_platform");
                when(mockEvent.getType()).thenReturn("checkout.session.completed");
                when(stripeWebhookHelper.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);

                String pPayload = "{"
                                + "\"data\": {"
                                + "  \"object\": {"
                                + "    \"id\": \"cs_platform_success\","
                                + "    \"amount_total\": 1000,"
                                + "    \"currency\": \"usd\""
                                + "  }"
                                + "}"
                                + "}";

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", "valid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(pPayload));

                // Verify Platform Sub
                Subscription pSub = subscriptionRepository.findByUser(user).get(0);
                assertEquals(PlanType.PLATFORM, pSub.getPlan().getPlanType());
                assertNull(pSub.getTraderId());

                // 2. Trader Plan Success
                // Clear subs for clean check
                subscriptionRepository.deleteAll();

                PaymentOrder tOrder = new PaymentOrder();
                tOrder.setUser(user);
                tOrder.setPlan(traderPlan);
                tOrder.setAmount(new java.math.BigDecimal("500"));
                tOrder.setCurrency("INR");
                tOrder.setProvider(PaymentProvider.CASHFREE);
                tOrder.setStatus(PaymentOrderStatus.PENDING);
                tOrder.setProviderOrderId("cf_trader_success");
                tOrder.setPurpose("TRADER_SUBSCRIPTION:traderId=999");
                paymentOrderRepository.save(tOrder);

                // Trigger Webhook
                Map<String, Object> payloadMap = new HashMap<>();
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("link_id", "cf_trader_success");
                dataMap.put("link_status", "PAID");
                dataMap.put("link_amount", 500.00);
                dataMap.put("link_currency", "INR");
                payloadMap.put("data", dataMap);

                mockMvc.perform(post("/api/webhooks/cashfree")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payloadMap)));

                // Verify Trader Sub
                Subscription tSub = subscriptionRepository.findByUser(user).get(0);
                assertEquals(PlanType.TRADER, tSub.getPlan().getPlanType());
                assertEquals(999L, tSub.getTraderId());
        }
}
