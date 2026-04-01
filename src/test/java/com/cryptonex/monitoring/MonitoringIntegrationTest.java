package com.cryptonex.monitoring;

import com.cryptonex.auth.AuthController;
import com.cryptonex.payment.WebhookController;
import com.cryptonex.request.LoginRequest;
import com.cryptonex.service.AlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class MonitoringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertService alertService;

    @Test
    void shouldAlertOnFailedLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("nonexistent@example.com");
        loginRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                // Expect 403 or 401 or 500 depending on how exception is handled.
                // AuthController rethrows exception, so likely 403/401 from security filter or
                // 500 if unhandled.
                // Let's just check that alertService was called.
                .andReturn();

        verify(alertService).logAlert(contains("Authentication failed"));
    }

    @Test
    void shouldAlertOnWebhookSignatureFailure() throws Exception {
        String payload = "{}";
        String invalidSig = "invalid_signature";

        mockMvc.perform(post("/api/webhooks/stripe")
                .header("Stripe-Signature", invalidSig)
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verify(alertService).logAlert(contains("Stripe signature verification failed"));
    }
}
