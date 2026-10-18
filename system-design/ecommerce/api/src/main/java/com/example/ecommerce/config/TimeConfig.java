package com.example.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Centralny zegar sprawia, że deadline rezerwacji jest deterministyczny w testach. */
@Configuration
public class TimeConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
