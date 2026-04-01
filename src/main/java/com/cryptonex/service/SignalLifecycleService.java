package com.cryptonex.service;

import com.cryptonex.model.TradeSignal;
import com.cryptonex.payment.service.PriceProvider;
import com.cryptonex.repository.TradeSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalLifecycleService {

    private final TradeSignalRepository tradeSignalRepository;
    private final PostService postService;
    private final PriceProvider priceProvider;

    @Transactional
    public void checkAndUpdateSignals() {
        int page = 0;
        int size = 50;
        Page<TradeSignal> activeSignals;

        do {
            activeSignals = tradeSignalRepository.findPageByStatus(TradeSignal.SignalStatus.ACTIVE,
                    PageRequest.of(page, size));
            log.info("Found {} active signals to process.", activeSignals.getTotalElements());
            for (TradeSignal signal : activeSignals) {
                processSignal(signal);
            }
            page++;
        } while (activeSignals.hasNext());
    }

    private void processSignal(TradeSignal signal) {
        try {
            BigDecimal currentPrice = priceProvider.getLatestPrice(signal.getCoin());

            if (signal.getDirection() == TradeSignal.Direction.LONG) {
                checkLongSignal(signal, currentPrice);
            } else { // SHORT
                checkShortSignal(signal, currentPrice);
            }

        } catch (Exception e) {
            log.error("Error processing signal {}: {}", signal.getId(), e.getMessage(), e);
        }
    }

    private void checkLongSignal(TradeSignal signal, BigDecimal price) {
        // Stop Loss
        if (price.compareTo(signal.getStopLoss()) <= 0) {
            closeSignal(signal, TradeSignal.SignalStatus.HIT_SL);
            return;
        }

        // Take Profit
        if (signal.getTakeProfits().stream().anyMatch(tp -> price.compareTo(tp) >= 0)) {
            triggerTp(signal, price);
        }
    }

    private void checkShortSignal(TradeSignal signal, BigDecimal price) {
        // Stop Loss (Price >= SL)
        if (price.compareTo(signal.getStopLoss()) >= 0) {
            closeSignal(signal, TradeSignal.SignalStatus.HIT_SL);
            return;
        }

        // Take Profit (Price <= TP)
        if (signal.getTakeProfits().stream().anyMatch(tp -> price.compareTo(tp) <= 0)) {
            triggerTp(signal, price);
        }
    }

    private void triggerTp(TradeSignal signal, BigDecimal price) {
        closeSignal(signal, TradeSignal.SignalStatus.HIT_TP);

        try {
            postService.createVictoryPostForSignal(signal);
            log.info("Victory Post created for signal {}", signal.getId());
        } catch (Exception e) {
            log.error("Error creating victory post for signal {}: {}", signal.getId(), e.getMessage());
        }
    }

    private void closeSignal(TradeSignal signal, TradeSignal.SignalStatus status) {
        signal.setStatus(status);
        signal.setClosedAt(LocalDateTime.now());
        tradeSignalRepository.save(signal);
    }
}
