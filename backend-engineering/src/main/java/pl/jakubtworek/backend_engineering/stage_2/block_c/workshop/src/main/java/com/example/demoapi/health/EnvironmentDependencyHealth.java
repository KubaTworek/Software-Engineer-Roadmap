package com.example.demoapi.health;

import org.springframework.stereotype.Component;

/** Lightweight workshop simulation; a real probe should read a cached dependency signal. */
@Component
public class EnvironmentDependencyHealth implements DependencyHealth {

    @Override
    public boolean isHealthy() {
        return !"false".equalsIgnoreCase(System.getenv("DB_UP"));
    }
}
