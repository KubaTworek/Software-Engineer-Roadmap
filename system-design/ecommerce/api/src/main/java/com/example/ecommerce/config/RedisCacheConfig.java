package com.example.ecommerce.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisCacheConfig implements CachingConfigurer {

    @Bean
    RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> caches = Map.of(
                "categories", defaults.entryTtl(Duration.ofHours(6)),
                "categoryTree", defaults.entryTtl(Duration.ofHours(6)),
                "products", defaults.entryTtl(Duration.ofMinutes(15)),
                "productBySlug", defaults.entryTtl(Duration.ofMinutes(30)),
                "productDetails", defaults.entryTtl(Duration.ofMinutes(30)),
                "searchResults", defaults.entryTtl(Duration.ofMinutes(5)),
                "catalogHome", defaults.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(caches)
                .build();
    }

    @Bean("stableKeyGenerator")
    KeyGenerator stableKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            StringBuilder builder = new StringBuilder(target.getClass().getSimpleName())
                    .append(":")
                    .append(method.getName());

            for (Object param : params) {
                builder.append(":").append(param == null ? "null" : param.toString());
            }

            return builder.toString();
        };
    }
}
