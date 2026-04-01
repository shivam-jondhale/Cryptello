package com.cryptonex.payment;

import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.model.PaymentOrder;
import com.cryptonex.model.User;
import com.cryptonex.repository.PaymentOrderRepository;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.response.PaymentResponse;
import com.cryptonex.service.EmailService;
import com.cryptonex.payment.service.PaymentProviderFactory;
import com.cryptonex.payment.service.PaymentProviderStrategy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=dummy",
        "spring.security.oauth2.client.registration.google.client-secret=dummy"
})
public class PaymentModuleRestorationTest {

    @MockBean
    private EmailService emailService;

    @MockBean
    private PaymentProviderFactory paymentProviderFactory;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    public void setup() {
        testUser = new User();
        testUser.setFullName("Restoration Test User");
        testUser.setEmail("restoration" + UUID.randomUUID() + "@test.com");
        testUser.setPassword("password");
        testUser = userRepository.save(testUser);
    }

    @Test
    public void testCreateStripeAdHocPayment() throws Exception {
        BigDecimal amount = new BigDecimal("100.00");

        PaymentResponse mockResponse = new PaymentResponse();
        mockResponse.setPaymentLinkUrl("http://mock-stripe-url.com");
        mockResponse.setProviderOrderId("sess_123");

        // Create a mock strategy for this test
        PaymentProviderStrategy mockStrategy = Mockito.mock(PaymentProviderStrategy.class);
        Mockito.when(mockStrategy.createOrder(any())).thenReturn(mockResponse);

        // Configure Factory to return our mock strategy
        Mockito.when(paymentProviderFactory.getProvider(PaymentProvider.STRIPE)).thenReturn(mockStrategy);

        PaymentResponse response = paymentService.createStripePaymentLink(testUser, amount);

        assertNotNull(response);
        assertEquals("http://mock-stripe-url.com", response.getPaymentLinkUrl());

        // Verify Order is created
        PaymentOrder order = paymentOrderRepository.findAll().stream()
                .filter(o -> o.getUser().getId().equals(testUser.getId()) && o.getAmount().compareTo(amount) == 0)
                .findFirst()
                .orElse(null);

        assertNotNull(order);
        assertEquals(PaymentProvider.STRIPE, order.getProvider());
        assertEquals("ADHOC_PAYMENT", order.getPurpose());
        assertEquals(amount, order.getAmount());
    }

    @Test
    public void testCreateCashfreeAdHocPayment() throws Exception {
        BigDecimal amount = new BigDecimal("500.50");

        PaymentResponse mockResponse = new PaymentResponse();
        mockResponse.setPaymentLinkUrl("http://mock-cashfree-url.com");
        mockResponse.setProviderOrderId("order_123");

        PaymentProviderStrategy mockStrategy = Mockito.mock(PaymentProviderStrategy.class);
        Mockito.when(mockStrategy.createOrder(any())).thenReturn(mockResponse);

        Mockito.when(paymentProviderFactory.getProvider(PaymentProvider.CASHFREE)).thenReturn(mockStrategy);

        PaymentResponse response = paymentService.createCashfreePaymentLink(testUser, amount);

        assertNotNull(response);
        assertEquals("http://mock-cashfree-url.com", response.getPaymentLinkUrl());

        PaymentOrder order = paymentOrderRepository.findAll().stream()
                .filter(o -> o.getUser().getId().equals(testUser.getId()) && o.getAmount().compareTo(amount) == 0)
                .findFirst()
                .orElse(null);

        assertNotNull(order);
        assertEquals(PaymentProvider.CASHFREE, order.getProvider());
        assertEquals("ADHOC_PAYMENT", order.getPurpose());
        assertEquals(amount, order.getAmount());
    }
}
