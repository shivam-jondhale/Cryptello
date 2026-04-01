package com.cryptonex.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataWorker {

    @Autowired
    private SignalLifecycleService signalLifecycleService;

    // Run every 60 seconds
    @Scheduled(fixedDelay = 60000)
    public void runMarketCycle() {
        try {
            signalLifecycleService.checkAndUpdateSignals();
        } catch (Exception e) {
            System.err.println("Market Data Worker failed: " + e.getMessage());
        }
    }
}
