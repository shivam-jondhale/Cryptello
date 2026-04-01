package com.cryptonex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public ChannelTopic priceTopic() {
        return new ChannelTopic("price.updates");
    }

    @Bean
    public ChannelTopic flashAlertTopic() {
        return new ChannelTopic("flash.alerts");
    }

    @Bean
    public ChannelTopic tradeEventTopic() {
        return new ChannelTopic("trade.events");
    }

    @Bean
    public org.springframework.data.redis.listener.RedisMessageListenerContainer container(
            RedisConnectionFactory connectionFactory,
            org.springframework.data.redis.listener.adapter.MessageListenerAdapter listenerAdapter) {
        org.springframework.data.redis.listener.RedisMessageListenerContainer container = new org.springframework.data.redis.listener.RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic("price.updates"));
        return container;
    }

    @Bean
    public org.springframework.data.redis.listener.adapter.MessageListenerAdapter listenerAdapter(
            com.cryptonex.market.listener.MarketEventListener listener) {
        return new org.springframework.data.redis.listener.adapter.MessageListenerAdapter(listener);
    }
}
