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
@Profile("dev")
public class CoinGeckoPriceProvider implements PriceProvider {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final Map<TradingPair, Consumer<PriceTick>> listeners = new ConcurrentHashMap<>();

    public CoinGeckoPriceProvider(OkHttpClient client, ObjectMapper objectMapper,
            @Value("${coingecko.api.key}") String apiKey) {
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
        String coinId = mapPairToCoinGeckoId(pair);
        String quote = pair.quote().toLowerCase();
        String url = "https://api.coingecko.com/api/v3/simple/price?ids=" + coinId + "&vs_currencies=" + quote;

        Request request = new Request.Builder()
                .url(url)
                .header("x-cg-demo-api-key", apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JsonNode node = objectMapper.readTree(body);
                if (node.has(coinId)) {
                    double price = node.get(coinId).get(quote).asDouble();
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
        Request request = new Request.Builder()
                .url("https://api.coingecko.com/api/v3/ping")
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    private String mapPairToCoinGeckoId(TradingPair pair) {
        String base = pair.base().toUpperCase();
        if (base.equals("BTC"))
            return "bitcoin";
        if (base.equals("ETH"))
            return "ethereum";
        if (base.equals("SOL"))
            return "solana";
        if (base.equals("TON"))
            return "the-open-network";
        return base.toLowerCase();
    }
}
