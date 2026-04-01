package com.cryptonex.market.service;

import com.cryptonex.market.model.PriceTick;
import com.cryptonex.market.model.TradingPair;

import java.util.Optional;
import java.util.function.Consumer;

public interface PriceProvider {
    /**
     * Subscribe to real-time price updates for a trading pair.
     * For polling providers, this registers a listener that gets called on every
     * poll.
     */
    void subscribe(TradingPair pair, Consumer<PriceTick> listener);

    /**
     * Get the latest known price for a trading pair.
     */
    Optional<PriceTick> getLatestPrice(TradingPair pair);

    /**
     * Check if the provider is healthy and reachable.
     */
    boolean isHealthy();
}
