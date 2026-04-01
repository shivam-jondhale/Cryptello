package com.cryptonex.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record FlashAlert(String symbol, String message, BigDecimal price, double changePercentage, Instant timestamp) {
}
