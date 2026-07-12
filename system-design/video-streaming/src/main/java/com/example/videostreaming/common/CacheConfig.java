package com.example.videostreaming.common;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

@Configuration
public class CacheConfig {
    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
                .withCacheConfiguration("publishedVideos", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration("videoDetails", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("playback", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(20)))
                .withCacheConfiguration("search", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(20)));
    }
}
