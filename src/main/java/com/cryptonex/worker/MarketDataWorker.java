package com.cryptonex.worker;

import com.cryptonex.market.model.PriceTick;
import com.cryptonex.market.model.TradingPair;
import com.cryptonex.market.service.PriceProvider;
import com.cryptonex.model.Coin;
import com.cryptonex.repository.CoinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("worker")
@EnableScheduling
public class MarketDataWorker {

    @Autowired
    private PriceProvider priceProvider;

    @Autowired
    private CoinRepository coinRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 10000) // Fetch every 10 seconds
    public void fetchPrices() {
        List<Coin> coins = coinRepository.findAll();
        for (Coin coin : coins) {
            if (coin.getSymbol() == null)
                continue;
            // Assume USD quote for now
            TradingPair pair = TradingPair.of(coin.getSymbol(), "USD");
            Optional<PriceTick> tick = priceProvider.getLatestPrice(pair);
            tick.ifPresent(this::publishPrice);
        }
    }

    private void publishPrice(PriceTick tick) {
        // Check for flash alerts BEFORE updating the cache to compare with previous
        // price
        checkFlashAlerts(tick);

        // Publish to Redis channel
        redisTemplate.convertAndSend("price.updates", tick);
        // Also cache the latest price
        redisTemplate.opsForValue().set("price:" + tick.pair().toString(), tick);
    }

    private void checkFlashAlerts(PriceTick tick) {
        String key = "price:" + tick.pair().toString();
        Object cachedObj = redisTemplate.opsForValue().get(key);

        if (cachedObj instanceof PriceTick oldTick) {
            java.math.BigDecimal oldPrice = oldTick.price();
            java.math.BigDecimal newPrice = tick.price();

            // Avoid division by zero
            if (oldPrice.compareTo(java.math.BigDecimal.ZERO) == 0)
                return;

            java.math.BigDecimal change = newPrice.subtract(oldPrice);
            double percentageChange = change.divide(oldPrice, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100;

            // Threshold: 5% change (pump or dump)
            if (Math.abs(percentageChange) >= 5.0) {
                String type = percentageChange > 0 ? "PUMPING" : "DUMPING";
                String message = String.format("%s is %s! %.2f%% change detected.", tick.pair().base(), type,
                        percentageChange);

                com.cryptonex.market.model.FlashAlert alert = new com.cryptonex.market.model.FlashAlert(
                        tick.pair().base(),
                        message,
                        newPrice,
                        percentageChange,
                        java.time.Instant.now());

                redisTemplate.convertAndSend("flash.alerts", alert);
                System.out.println("FLASH ALERT: " + message);
            }
        }
    }
}
