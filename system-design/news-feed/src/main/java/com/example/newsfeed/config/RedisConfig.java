package com.example.newsfeed.config;

import com.example.newsfeed.feed.FeedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.*;

@Configuration
public class RedisConfig {

    @Bean
    public GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisTemplate<String, FeedResponse> feedRedisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer jsonSerializer
    ) {
        RedisTemplate<String, FeedResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
