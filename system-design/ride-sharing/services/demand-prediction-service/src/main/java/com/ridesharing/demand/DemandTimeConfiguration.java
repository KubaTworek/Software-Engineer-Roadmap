package com.ridesharing.demand;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class DemandTimeConfiguration {
    @Bean
    Clock demandClock() {
        return Clock.systemUTC();
    }
}
