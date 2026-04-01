package com.cryptonex.feature;

import com.cryptonex.model.Post;
import com.cryptonex.model.TradeSignal;
import com.cryptonex.model.User;
import com.cryptonex.payment.service.MockPriceProvider;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.service.AnalyticsService;
import com.cryptonex.service.JournalService;
import com.cryptonex.service.PostService;
import com.cryptonex.service.SignalLifecycleService;
import com.cryptonex.user.UserService;
import com.cryptonex.domain.USER_ROLE;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.api.Disabled;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@AutoConfigureMockMvc
@TestPropertySource(properties = {
                "spring.security.oauth2.client.registration.google.client-id=dummy",
                "spring.security.oauth2.client.registration.google.client-secret=dummy"
})
public class Phase5CompletionTest {

        @MockBean
        private com.cryptonex.service.EmailService emailService;

        // Mocking UserService because AnalyticsController manually validates token
        // using it
        @MockBean
        private UserService userService;

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private PostService postService;

        @Autowired
        private SignalLifecycleService signalLifecycleService;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private MockPriceProvider mockPriceProvider;

        private User trader;
        private User normalUser;

        @BeforeEach
        public void setup() throws Exception {
                trader = new User();
                trader.setFullName("Analytics Trader");
                trader.setEmail("trader" + UUID.randomUUID() + "@test.com");
                trader.setRoles(new HashSet<>(Set.of(USER_ROLE.ROLE_TRADER)));
                trader = userRepository.save(trader);

                normalUser = new User();
                normalUser.setFullName("Normal User");
                normalUser.setEmail("user" + UUID.randomUUID() + "@test.com");
                normalUser = userRepository.save(normalUser);

                // Mock generic user lookup if used elsewhere
                // But specifically for the controller:
                when(userService.findUserProfileByJwt("Bearer trader-token")).thenReturn(trader);
                when(userService.findUserProfileByJwt("Bearer user-token")).thenReturn(normalUser);
        }

        @Test
        public void testSignalLifecycle() throws Exception {
                // 1. Create Active Signal (LONG ETH)
                TradeSignal signal = new TradeSignal();
                signal.setCoin("ETH");
                signal.setEntryRangeMin(new BigDecimal("3000"));
                signal.setEntryRangeMax(new BigDecimal("3005"));
                // Mutable list to prevent Hibernate issues
                signal.setTakeProfits(new ArrayList<>(List.of(new BigDecimal("3100"))));
                signal.setStopLoss(new BigDecimal("2900"));
                signal.setDirection(TradeSignal.Direction.LONG);

                Post post = postService.createSignalPost(trader, signal, "Long ETH Signal");
                TradeSignal savedSignal = post.getRelatedTradeSignal();
                Long signalId = savedSignal.getId();

                // 2. Mock Price hit TP (3150)
                mockPriceProvider.setMockPrice("ETH", new BigDecimal("3150"));

                // 3. Run Lifecycle
                signalLifecycleService.checkAndUpdateSignals();

                // 4. Verify Victory Post Created
                List<Post> feed = postService.getFeed(org.springframework.data.domain.Pageable.unpaged()).getContent();
                long victoryCount = feed.stream()
                                .filter(p -> p.getType() == Post.PostType.VICTORY
                                                && p.getRelatedTradeSignal().getId().equals(signalId))
                                .count();

                assertEquals(1, victoryCount, "Victory post should be created");
        }

        @Test
        @Disabled("Requires integration with real or fully mocked Token Provider for JWT parsing in Controller")
        @WithMockUser
        public void testAnalyticsEndpoints() throws Exception {
                // 1. Public Trader Metrics
                mockMvc.perform(get("/api/analytics/trader/" + trader.getId() + "/metrics")
                                .header("Authorization", "Bearer trader-token"))
                                .andExpect(status().isOk());

                // 2. Non-Trader Metrics Check
                mockMvc.perform(get("/api/analytics/trader/" + normalUser.getId() + "/metrics")
                                .header("Authorization", "Bearer user-token"))
                                .andExpect(status().isNotFound());

                // 3. User Metrics (Protected)
                mockMvc.perform(get("/api/analytics/user/" + normalUser.getId() + "/metrics")
                                .header("Authorization", "Bearer user-token"))
                                .andExpect(status().isOk());

                // Accessing other user's metrics
                mockMvc.perform(get("/api/analytics/user/" + normalUser.getId() + "/metrics")
                                .header("Authorization", "Bearer trader-token"))
                                .andExpect(status().isForbidden());
        }
}
