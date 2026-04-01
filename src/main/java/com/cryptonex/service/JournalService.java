package com.cryptonex.service;

import com.cryptonex.model.TradeSignal;
import com.cryptonex.model.User;
import com.cryptonex.model.UserTrade;
import com.cryptonex.repository.TradeSignalRepository;
import com.cryptonex.repository.UserTradeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class JournalService {

    @Autowired
    private UserTradeRepository userTradeRepository;

    @Autowired
    private TradeSignalRepository tradeSignalRepository;

    @Transactional
    public UserTrade addToMyTrades(User user, Long signalId, java.math.BigDecimal entryPrice) throws Exception {
        TradeSignal signal = tradeSignalRepository.findById(signalId)
                .orElseThrow(() -> new Exception("Signal not found"));

        UserTrade trade = new UserTrade();
        trade.setUser(user);
        trade.setTradeSignal(signal);
        trade.setCoin(signal.getCoin());
        trade.setDirection(signal.getDirection());
        trade.setEntryPrice(entryPrice != null ? entryPrice : java.math.BigDecimal.ZERO); // Or fetch current price
        trade.setStatus(UserTrade.TradeStatus.OPEN);

        return userTradeRepository.save(trade);
    }

    @Transactional
    public UserTrade closeTrade(User user, Long tradeId, java.math.BigDecimal exitPrice) throws Exception {
        UserTrade trade = userTradeRepository.findById(tradeId)
                .orElseThrow(() -> new Exception("Trade not found"));

        if (!trade.getUser().getId().equals(user.getId())) {
            throw new Exception("Unauthorized access to trade");
        }

        trade.setExitPrice(exitPrice);
        trade.setExitTime(LocalDateTime.now());

        // Calculate PnL
        java.math.BigDecimal pnl = exitPrice.subtract(trade.getEntryPrice());

        if (trade.getDirection() == TradeSignal.Direction.SHORT) {
            pnl = pnl.negate();
        }
        trade.setPnl(pnl);

        // Determine Status (Simple logic for now)
        if (pnl.compareTo(java.math.BigDecimal.ZERO) > 0) {
            trade.setStatus(UserTrade.TradeStatus.CLOSED_TP);
        } else {
            trade.setStatus(UserTrade.TradeStatus.CLOSED_SL); // Or MANUAL
        }

        return userTradeRepository.save(trade);
    }

    public java.util.List<UserTrade> getUserTrades(User user) {
        return userTradeRepository.findByUserIdOrderByEntryTimeDesc(user.getId());
    }
}
