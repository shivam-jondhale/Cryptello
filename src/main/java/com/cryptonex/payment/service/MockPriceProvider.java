package com.cryptonex.payment.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockPriceProvider implements PriceProvider {

    private final Map<String, BigDecimal> mockPrices = new ConcurrentHashMap<>();

    public MockPriceProvider() {
        // Default Mock Prices
        mockPrices.put("BTC", new BigDecimal("50000.00"));
        mockPrices.put("ETH", new BigDecimal("3000.00"));
        mockPrices.put("SOL", new BigDecimal("100.00"));
    }

    @Override
    public BigDecimal getLatestPrice(String coinSymbol) {
        return mockPrices.getOrDefault(coinSymbol.toUpperCase(), new BigDecimal("100.00"));
    }

    // Helper for testing to force price updates
    public void setMockPrice(String coin, BigDecimal price) {
        mockPrices.put(coin.toUpperCase(), price);
    }
}
