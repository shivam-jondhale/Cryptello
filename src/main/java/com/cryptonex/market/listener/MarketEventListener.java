package com.cryptonex.market.listener;

import com.cryptonex.controller.LiveMarketController;
import com.cryptonex.market.model.PriceTick;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MarketEventListener implements MessageListener {

    @Autowired
    private LiveMarketController controller;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            if ("price.updates".equals(channel)) {
                PriceTick tick = objectMapper.readValue(message.getBody(), PriceTick.class);
                controller.broadcast(tick);
            } else if ("trade.events".equals(channel)) {
                com.cryptonex.market.model.TradeEvent event = objectMapper.readValue(message.getBody(),
                        com.cryptonex.market.model.TradeEvent.class);
                controller.sendToUser(event);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
