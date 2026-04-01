package com.cryptonex.service;

import com.cryptonex.model.TradeSignal;
import com.cryptonex.model.UserTrade;
import com.cryptonex.repository.TradeSignalRepository;
import com.cryptonex.repository.UserTradeRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsService {

    @Autowired
    private TradeSignalRepository tradeSignalRepository;

    @Autowired
    private UserTradeRepository userTradeRepository;

    public TraderMetrics getTraderMetrics(Long traderId) {
        List<TradeSignal> signals = tradeSignalRepository.findByPostAuthorId(traderId);

        long totalSignals = signals.size();
        long hitTp = signals.stream().filter(s -> s.getStatus() == TradeSignal.SignalStatus.HIT_TP).count();
        long hitSl = signals.stream().filter(s -> s.getStatus() == TradeSignal.SignalStatus.HIT_SL).count();
        long closedManual = signals.stream().filter(s -> s.getStatus() == TradeSignal.SignalStatus.CLOSED_MANUALLY)
                .count();

        // Denominator excludes ACTIVE
        long completedSignals = hitTp + hitSl + closedManual;
        double winRate = completedSignals > 0 ? (double) hitTp / completedSignals : 0.0;

        TraderMetrics metrics = new TraderMetrics();
        metrics.setTotalSignals(totalSignals);
        metrics.setWinRate(winRate);
        metrics.setTpCount(hitTp);
        metrics.setSlCount(hitSl);

        return metrics;
    }

    public UserMetrics getUserMetrics(Long userId) {
        List<UserTrade> trades = userTradeRepository.findByUserId(userId);

        long totalTrades = trades.size();
        long wins = trades.stream().filter(t -> t.getStatus() == UserTrade.TradeStatus.CLOSED_TP).count();
        long losses = trades.stream().filter(t -> t.getStatus() == UserTrade.TradeStatus.CLOSED_SL).count();
        long manual = trades.stream().filter(t -> t.getStatus() == UserTrade.TradeStatus.CLOSED_MANUAL).count();

        // Denominator excludes OPEN
        long completedTrades = wins + losses + manual;
        double winRate = completedTrades > 0 ? (double) wins / completedTrades : 0.0;

        UserMetrics metrics = new UserMetrics();
        metrics.setTotalTrades(totalTrades);
        metrics.setWinRate(winRate);
        metrics.setWins(wins);
        metrics.setLosses(losses);

        return metrics;
    }

    @Data
    public static class TraderMetrics {
        private long totalSignals;
        private double winRate;
        private long tpCount;
        private long slCount;
    }

    @Data
    public static class UserMetrics {
        private long totalTrades;
        private double winRate;
        private long wins;
        private long losses;
    }
}
