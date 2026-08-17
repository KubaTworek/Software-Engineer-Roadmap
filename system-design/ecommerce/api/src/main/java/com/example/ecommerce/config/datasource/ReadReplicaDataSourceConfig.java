package com.example.ecommerce.config.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "app.datasource.routing", name = "enabled", havingValue = "true")
public class ReadReplicaDataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource(ReadReplicaProperties properties) {
        HikariDataSource writer = create(properties.writer());

        Map<Object, Object> targets = new HashMap<>();
        targets.put("writer", writer);

        int replicaCount = properties.replicas() == null ? 0 : properties.replicas().size();

        for (int i = 0; i < replicaCount; i++) {
            var replica = properties.replicas().get(i);
            targets.put("replica-" + i, create(replica));
        }

        ReadReplicaRoutingDataSource routing = new ReadReplicaRoutingDataSource(replicaCount);
        routing.setDefaultTargetDataSource(writer);
        routing.setTargetDataSources(targets);
        routing.afterPropertiesSet();

        return routing;
    }

    private HikariDataSource create(ReadReplicaProperties.DataSourceConfig config) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.jdbcUrl());
        ds.setUsername(config.username());
        ds.setPassword(config.password());
        ds.setDriverClassName(config.driverClassName());
        return ds;
    }

    private HikariDataSource create(ReadReplicaProperties.ReplicaConfig config) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.jdbcUrl());
        ds.setUsername(config.username());
        ds.setPassword(config.password());
        ds.setDriverClassName(config.driverClassName());
        return ds;
    }
}
