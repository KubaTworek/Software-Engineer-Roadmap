package com.example.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class NotificationSystemApplication {
    @Bean Clock systemClock() { return Clock.systemUTC(); }
    public static void main(String[] args) {
        SpringApplication.run(NotificationSystemApplication.class, args);
    }
}
