package com.cryptonex.market.model;

public record TradingPair(String base, String quote) {
    public static TradingPair of(String base, String quote) {
        return new TradingPair(base.toUpperCase(), quote.toUpperCase());
    }

    @Override
    public String toString() {
        return base + "/" + quote;
    }
}
