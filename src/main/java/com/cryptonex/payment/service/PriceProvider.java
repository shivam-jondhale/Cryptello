package com.cryptonex.payment.service;

import java.math.BigDecimal;

public interface PriceProvider {
    /**
     * Fetches the current price of a coin.
     * 
     * @param coinSymbol The symbol of the coin (e.g., "BTC", "ETH").
     * @return The current price in USD/USDT as BigDecimal.
     * @throws Exception if price cannot be fetched.
     */
    BigDecimal getLatestPrice(String coinSymbol) throws Exception;
}
