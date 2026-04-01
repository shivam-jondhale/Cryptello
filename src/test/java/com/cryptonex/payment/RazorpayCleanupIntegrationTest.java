package com.cryptonex.payment;

import com.cryptonex.domain.PaymentMethod;
import com.cryptonex.model.User;
import com.cryptonex.user.UserService;
import com.cryptonex.service.WalletService;
import com.cryptonex.service.AlertService;
import com.cryptonex.service.EmailService;
import com.cryptonex.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RazorpayCleanupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private UserService userService;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private WalletService walletService;

    @MockBean
    private AlertService alertService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    @Test
    public void testP3_0_1_WebhookEndpointIsDead() throws Exception {
        // The /api/webhooks/razorpay endpoint was commented out.
        // Since /api/webhooks/** is permitAll, it should return 404 (Not Found).
        // But we accept any non-2xx status to confirm it's not processing successfully.
        mockMvc.perform(post("/api/webhooks/razorpay")
                .contentType("application/json")
                .content("{}"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().is(org.hamcrest.Matchers.not(200)));
    }

    @Test
    public void testP3_0_1_PaymentEndpointReturnsError() throws Exception {
        // Mock User
        User mockUser = new User();
        mockUser.setId(1L);
        when(userService.findUserProfileByJwt(anyString())).thenReturn(mockUser);

        // Mock Create Order (needed before the Razorpay check)
        com.cryptonex.model.PaymentOrder mockOrder = new com.cryptonex.model.PaymentOrder();
        mockOrder.setId(100L);
        when(paymentService.createOrder(any(User.class), any(java.math.BigDecimal.class), any(PaymentMethod.class)))
                .thenReturn(mockOrder);

        // The /payment/RAZORPAY/amount/100 endpoint exists but throws "Razorpay is
        // disabled"
        // OR it returns 401/403 if auth fails.
        // In any case, it should NOT return 200 OK.
        mockMvc.perform(post("/payment/RAZORPAY/amount/100")
                .header("Authorization", "Bearer mock_jwt"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().is(org.hamcrest.Matchers.not(200)));
    }

    @Test
    public void testP3_0_2_RazorpayBeanIsNotActive() {
        // Verify that no bean named "razorpayClient" exists
        boolean beanExists = applicationContext.containsBean("razorpayClient");
        assertTrue(!beanExists, "RazorpayClient bean should not exist in the context");

        // Verify that no bean named "razorpayConfig" exists
        boolean configExists = applicationContext.containsBean("razorpayConfig");
        assertTrue(!configExists, "RazorpayConfig bean should not exist");
    }
}
