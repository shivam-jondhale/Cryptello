package com.cryptonex.feature;

import com.cryptonex.model.Post;
import com.cryptonex.model.TradeSignal;
import com.cryptonex.model.User;
import com.cryptonex.model.UserTrade;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.service.JournalService;
import com.cryptonex.service.EmailService;
import com.cryptonex.service.PostService;
import com.cryptonex.domain.USER_ROLE;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=dummy",
        "spring.security.oauth2.client.registration.google.client-secret=dummy"
})
public class Phase5IntegrationTest {

    @MockBean
    private EmailService emailService;

    @Autowired
    private PostService postService;

    @Autowired
    private JournalService journalService;

    @Autowired
    private UserRepository userRepository;

    private User trader;
    private User follower;

    @BeforeEach
    public void setup() {
        trader = new User();
        trader.setFullName("Phase5 Trader");
        trader.setEmail("trader" + UUID.randomUUID() + "@test.com");
        trader.setRoles(new HashSet<>(Set.of(USER_ROLE.ROLE_TRADER)));
        trader = userRepository.save(trader);

        follower = new User();
        follower.setFullName("Phase5 Follower");
        follower.setEmail("follower" + UUID.randomUUID() + "@test.com");
        follower = userRepository.save(follower);
    }

    @Test
    public void testTradeSignalAndJournalingPrecision() throws Exception {
        // 1. Create Signal with precise BigDecimal values
        TradeSignal signalData = new TradeSignal();
        signalData.setCoin("ETH");
        signalData.setEntryRangeMin(new BigDecimal("3000.1234")); // Check precision
        signalData.setEntryRangeMax(new BigDecimal("3005.5678"));
        signalData.setTakeProfits(List.of(new BigDecimal("3100.0000"), new BigDecimal("3200.0000")));
        signalData.setStopLoss(new BigDecimal("2900.0000"));
        signalData.setDirection(TradeSignal.Direction.LONG);

        Post post = postService.createSignalPost(trader, signalData, "Precision Test Signal");

        // 2. Add to Journal
        BigDecimal entryPrice = new BigDecimal("3002.5000");
        UserTrade trade = journalService.addToMyTrades(follower, post.getRelatedTradeSignal().getId(), entryPrice);

        assertEquals(0, trade.getEntryPrice().compareTo(entryPrice));

        // 3. Close Trade and Verify PnL Precision
        BigDecimal exitPrice = new BigDecimal("3150.7500");
        trade = journalService.closeTrade(follower, trade.getId(), exitPrice);

        BigDecimal expectedPnL = exitPrice.subtract(entryPrice); // 3150.7500 - 3002.5000 = 148.2500

        assertEquals(0, trade.getPnl().compareTo(expectedPnL));
        assertEquals(UserTrade.TradeStatus.CLOSED_TP, trade.getStatus());
    }

    @Test
    public void testShortTradePnL() throws Exception {
        // Short Signal
        TradeSignal signalData = new TradeSignal();
        signalData.setCoin("SOL");
        signalData.setEntryRangeMin(new BigDecimal("100.00"));
        signalData.setEntryRangeMax(new BigDecimal("105.00"));
        signalData.setTakeProfits(List.of(new BigDecimal("90.00")));
        signalData.setStopLoss(new BigDecimal("110.00"));
        signalData.setDirection(TradeSignal.Direction.SHORT);

        Post post = postService.createSignalPost(trader, signalData, "Short SOL");

        // Entry
        BigDecimal entryPrice = new BigDecimal("102.00");
        UserTrade trade = journalService.addToMyTrades(follower, post.getRelatedTradeSignal().getId(), entryPrice);

        // Exit (Lower price = Profit for Short)
        BigDecimal exitPrice = new BigDecimal("95.00");
        trade = journalService.closeTrade(follower, trade.getId(), exitPrice);

        // PnL = -(Exit - Entry) = -(95 - 102) = -(-7) = 7
        BigDecimal expectedPnL = exitPrice.subtract(entryPrice).negate();

        assertEquals(0, trade.getPnl().compareTo(expectedPnL));
        assertTrue(trade.getPnl().compareTo(BigDecimal.ZERO) > 0);
    }
}
