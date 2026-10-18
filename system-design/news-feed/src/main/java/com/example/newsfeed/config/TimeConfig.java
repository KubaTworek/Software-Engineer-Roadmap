package com.example.newsfeed.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

/** Jeden jawny zegar pozwala zamrozić czas w testach lease, TTL i retencji. */
@Configuration
public class TimeConfig {
    @Bean
    Clock clock() { return Clock.systemUTC(); }
}
