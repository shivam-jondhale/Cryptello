package com.cryptonex.payment;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.domain.PlanType;
import com.cryptonex.domain.SubscriptionStatus;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.Subscription;
import com.cryptonex.model.SubscriptionPlan;
import com.cryptonex.model.User;
import com.cryptonex.payment.service.StripeClientWrapper;
import com.cryptonex.payment.service.StripePaymentProvider;
import com.cryptonex.payment.service.StripeWebhookHelper;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.SubscriptionPlanRepository;
import com.cryptonex.repository.SubscriptionRepository;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.response.PaymentResponse;
import com.cryptonex.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class StripeIntegrationTest {

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

        @Autowired
        private StripePaymentProvider stripePaymentProvider;

        @MockBean
        private StripeClientWrapper stripeClientWrapper;

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
        private SubscriptionPlan stripePlan;
        private ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        public void setup() {
                paymentOrderRepository.deleteAll();
                subscriptionRepository.deleteAll();
                subscriptionPlanRepository.deleteAll();
                userRepository.deleteAll();

                user = new User();
                user.setFullName("Stripe Test User");
                user.setEmail("stripe@test.com");
                user.setPassword("password");
                user = userRepository.save(user);

                stripePlan = new SubscriptionPlan();
                stripePlan.setName("STRIPE_PRO_USD");
                stripePlan.setPrice(new BigDecimal("10.00")); // 10.00 USD
                stripePlan.setCurrency("USD");
                stripePlan.setDurationMonths(1);
                stripePlan.setPlanType(PlanType.PLATFORM);
                stripePlan.setProviderType(PaymentProvider.STRIPE);
                stripePlan.setActive(true);
                subscriptionPlanRepository.save(stripePlan);
        }

        @Test
        public void testP3_4_2_StripeSessionCreation() throws StripeException {
                // 1. Create Order
                PaymentOrder order = new PaymentOrder();
                order.setId(2001L);
                order.setUser(user);
                order.setPlan(stripePlan);
                order.setAmount(new BigDecimal("10.00"));
                order.setCurrency("USD");
                order.setPurpose("Test Stripe Purpose");
                order.setProvider(PaymentProvider.STRIPE);

                // 2. Mock Stripe Session
                Session mockSession = mock(Session.class);
                when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_123");
                when(mockSession.getId()).thenReturn("cs_test_123");

                when(stripeClientWrapper.createSession(any(SessionCreateParams.class))).thenReturn(mockSession);

                // 3. Execute
                PaymentResponse response = stripePaymentProvider.createOrder(order);

                // 4. Verify
                assertEquals("https://checkout.stripe.com/pay/cs_test_123", response.getPaymentLinkUrl());
                assertEquals("cs_test_123", response.getProviderOrderId());

                ArgumentCaptor<SessionCreateParams> paramsCaptor = ArgumentCaptor.forClass(SessionCreateParams.class);
                verify(stripeClientWrapper).createSession(paramsCaptor.capture());

                SessionCreateParams params = paramsCaptor.getValue();
                assertEquals("stripe@test.com", params.getCustomerEmail());
                assertEquals("2001", params.getClientReferenceId());

                SessionCreateParams.LineItem item = params.getLineItems().get(0);
                assertEquals(1000L, item.getPriceData().getUnitAmount());
                assertEquals("USD", item.getPriceData().getCurrency());
        }

        @Test
        public void testP3_4_3_WebhookSuccess() throws Exception {
                // 1. Create Pending Order
                PaymentOrder order = new PaymentOrder();
                order.setUser(user);
                order.setPlan(stripePlan);
                order.setAmount(new BigDecimal("10.00"));
                order.setCurrency("USD");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(com.cryptonex.domain.PaymentOrderStatus.PENDING);
                order.setProviderOrderId("cs_test_success");
                paymentOrderRepository.save(order);

                // 2. Mock Stripe Event
                Event mockEvent = mock(Event.class);
                when(mockEvent.getId()).thenReturn("evt_test_success");
                when(mockEvent.getType()).thenReturn("checkout.session.completed");

                when(stripeWebhookHelper.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);

                // 3. Construct Payload (JSON that Controller parses)
                // The controller reads data.object.id, amount_total, currency
                String payload = "{"
                                + "\"data\": {"
                                + "  \"object\": {"
                                + "    \"id\": \"cs_test_success\","
                                + "    \"amount_total\": 1000,"
                                + "    \"currency\": \"usd\""
                                + "  }"
                                + "}"
                                + "}";

                // 4. Call Webhook
                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", "valid_sig")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                // 5. Verify DB
                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(com.cryptonex.domain.PaymentOrderStatus.SUCCESS, updatedOrder.getStatus());

                List<Subscription> subs = subscriptionRepository.findAll();
                assertEquals(1, subs.size());
                assertEquals(SubscriptionStatus.ACTIVE, subs.get(0).getStatus());
        }

        @Test
        public void testP3_4_4_WebhookInvalidSignature() throws Exception {
                // 1. Mock Exception
                when(stripeWebhookHelper.constructEvent(anyString(), anyString(), anyString()))
                                .thenThrow(new SignatureVerificationException("Invalid signature", "sig_header"));

                // 2. Call Webhook
                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", "invalid_sig")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        public void testP3_4_5_WebhookIdempotency() throws Exception {
                // 1. Create Pending Order
                PaymentOrder order = new PaymentOrder();
                order.setUser(user);
                order.setPlan(stripePlan);
                order.setAmount(new BigDecimal("10.00"));
                order.setCurrency("USD");
                order.setProvider(PaymentProvider.STRIPE);
                order.setStatus(com.cryptonex.domain.PaymentOrderStatus.PENDING);
                order.setProviderOrderId("cs_test_idem");
                paymentOrderRepository.save(order);

                // 2. Mock Stripe Event
                Event mockEvent = mock(Event.class);
                when(mockEvent.getId()).thenReturn("evt_test_idem");
                when(mockEvent.getType()).thenReturn("checkout.session.completed");

                when(stripeWebhookHelper.constructEvent(anyString(), anyString(), anyString())).thenReturn(mockEvent);

                String payload = "{"
                                + "\"data\": {"
                                + "  \"object\": {"
                                + "    \"id\": \"cs_test_idem\","
                                + "    \"amount_total\": 1000,"
                                + "    \"currency\": \"usd\""
                                + "  }"
                                + "}"
                                + "}";

                // 3. Call Webhook TWICE
                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", "valid_sig")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                mockMvc.perform(post("/api/webhooks/stripe")
                                .header("Stripe-Signature", "valid_sig")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                                .andExpect(status().isOk());

                // 4. Verify DB
                List<Subscription> subs = subscriptionRepository.findAll();
                assertEquals(1, subs.size());

                PaymentOrder updatedOrder = paymentOrderRepository.findById(order.getId()).orElseThrow();
                assertEquals(com.cryptonex.domain.PaymentOrderStatus.SUCCESS, updatedOrder.getStatus());
        }
}
