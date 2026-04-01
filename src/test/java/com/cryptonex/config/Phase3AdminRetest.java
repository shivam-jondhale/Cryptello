package com.cryptonex.config;

import com.cryptonex.domain.PaymentProvider;
import com.cryptonex.domain.USER_ROLE;
import com.cryptonex.model.User;
import com.cryptonex.model.WebhookEvent;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "stripe.webhook.secret=whsec_test_secret",
        "razorpay.api.secret=rzp_test_secret"
})
public class Phase3AdminRetest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private com.cryptonex.repository.WatchlistRepository watchlistRepository;

    @Autowired
    private com.cryptonex.repository.PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private com.cryptonex.repository.TwoFactorOtpRepository twoFactorOtpRepository;

    @Autowired
    private com.cryptonex.repository.WalletRepository walletRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminJwt;
    private String userJwt;

    @BeforeEach
    void setUp() throws Exception {
        watchlistRepository.deleteAll();
        twoFactorOtpRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        webhookEventRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        // Create Admin
        User admin = new User();
        admin.setFullName("Admin User");
        admin.setEmail("admin@test.com");
        admin.setPassword(passwordEncoder.encode("AdminPass123!"));
        admin.getRoles().add(USER_ROLE.ROLE_ADMIN);
        userRepository.save(admin);

        // Create Normal User
        User user = new User();
        user.setFullName("Normal User");
        user.setEmail("user@test.com");
        user.setPassword(passwordEncoder.encode("UserPass123!"));
        user.getRoles().add(USER_ROLE.ROLE_USER);
        userRepository.save(user);

        // Login to get JWTs
        adminJwt = obtainJwt("admin@test.com", "AdminPass123!");
        userJwt = obtainJwt("user@test.com", "UserPass123!");

        // Create some Webhook Events
        WebhookEvent event1 = new WebhookEvent();
        event1.setProvider(PaymentProvider.STRIPE);
        event1.setEventId("evt_1");
        event1.setPayload("{}");
        webhookEventRepository.save(event1);

        WebhookEvent event2 = new WebhookEvent();
        event2.setProvider(PaymentProvider.RAZORPAY);
        event2.setEventId("evt_2");
        event2.setPayload("{}");
        webhookEventRepository.save(event2);
    }

    private String obtainJwt(String email, String password) throws Exception {
        String json = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String response = mockMvc.perform(post("/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("jwt").asText();
    }

    @Test
    void adminShouldAccessReconcileEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/reconcile")
                .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void userShouldNotAccessReconcileEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/reconcile")
                .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isForbidden()); // Or 403/401 depending on config
    }

    @Test
    void adminShouldAccessRetryEndpoint() throws Exception {
        WebhookEvent event = webhookEventRepository.findAll().get(0);
        mockMvc.perform(post("/api/admin/reconcile/" + event.getId() + "/retry")
                .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk());
    }
}
