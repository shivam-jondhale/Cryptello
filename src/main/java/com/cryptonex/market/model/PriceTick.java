package com.cryptonex.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceTick(TradingPair pair, BigDecimal price, Instant timestamp) {
    public static PriceTick now(TradingPair pair, BigDecimal price) {
        return new PriceTick(pair, price, Instant.now());
    }
}
