package com.cryptonex.market.service;

import com.cryptonex.market.model.PriceTick;
import com.cryptonex.market.model.TradingPair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
@Profile("prod")
public class CoinMarketCapPriceProvider implements PriceProvider {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final Map<TradingPair, Consumer<PriceTick>> listeners = new ConcurrentHashMap<>();

    public CoinMarketCapPriceProvider(OkHttpClient client, ObjectMapper objectMapper,
            @Value("${coinmarketcap.api.key}") String apiKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public void subscribe(TradingPair pair, Consumer<PriceTick> listener) {
        listeners.put(pair, listener);
    }

    @Override
    public Optional<PriceTick> getLatestPrice(TradingPair pair) {
        String symbol = pair.base().toUpperCase();
        String convert = pair.quote().toUpperCase();
        String url = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest?symbol=" + symbol + "&convert="
                + convert;

        Request request = new Request.Builder()
                .url(url)
                .header("X-CMC_PRO_API_KEY", apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JsonNode node = objectMapper.readTree(body);
                // Response structure: data -> SYMBOL -> quote -> CONVERT -> price
                if (node.has("data") && node.get("data").has(symbol)) {
                    double price = node.get("data").get(symbol).get("quote").get(convert).get("price").asDouble();
                    return Optional.of(PriceTick.now(pair, BigDecimal.valueOf(price)));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    @Override
    public boolean isHealthy() {
        // Simple check
        return true;
    }
}
