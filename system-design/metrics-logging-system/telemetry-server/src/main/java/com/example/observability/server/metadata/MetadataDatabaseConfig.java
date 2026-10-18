package com.example.observability.server.metadata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;
import javax.sql.DataSource;

/**
 * Oddziela silnie spójny control plane od append-only telemetry data plane.
 * ClickHouse pozostaje magazynem logów/metryk, a PostgreSQL chroni unikalność,
 * revocation i transakcyjne zmiany konfiguracji.
 */
@Configuration
public class MetadataDatabaseConfig {
    /**
     * Jawny template data plane. Samo dodanie metadataJdbc powoduje, że
     * auto-konfiguracja Springa nie tworzy już domyślnego JdbcTemplate; bez
     * tego beana niekwalifikowane repozytoria omyłkowo używały PostgreSQL.
     */
    @Bean("clickHouseJdbc")
    @Primary
    JdbcTemplate clickHouseJdbc(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("metadataJdbc")
    JdbcTemplate metadataJdbc(
            @Value("${telemetry.metadata.url}") String url,
            @Value("${telemetry.metadata.username}") String username,
            @Value("${telemetry.metadata.password}") String password) {
        var dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(dataSource);
    }
}
