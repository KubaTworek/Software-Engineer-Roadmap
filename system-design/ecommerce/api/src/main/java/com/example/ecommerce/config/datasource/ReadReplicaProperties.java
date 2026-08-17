package com.example.ecommerce.config.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.datasource")
public record ReadReplicaProperties(
        Routing routing,
        DataSourceConfig writer,
        List<ReplicaConfig> replicas
) {
    public record Routing(boolean enabled) {
    }

    public record DataSourceConfig(
            String jdbcUrl,
            String username,
            String password,
            String driverClassName
    ) {
    }

    public record ReplicaConfig(
            String name,
            String jdbcUrl,
            String username,
            String password,
            String driverClassName
    ) {
    }
}
