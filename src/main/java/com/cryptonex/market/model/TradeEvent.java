package com.cryptonex.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeEvent(String userId, String orderId, String symbol, String type, String status, BigDecimal price,
        Instant timestamp) {
}
