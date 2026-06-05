package com.example.observability.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TelemetryServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelemetryServerApplication.class, args);
    }
}
