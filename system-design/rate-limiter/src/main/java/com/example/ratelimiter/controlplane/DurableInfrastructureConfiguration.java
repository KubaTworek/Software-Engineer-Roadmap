package com.example.ratelimiter.controlplane;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.config.RuleConfigurationValidator;
import com.example.ratelimiter.usage.JdbcUsageOutbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Production-boundary wiring kept behind an explicit profile. The default
 * learning path remains infrastructure-free; `durable` opts into PostgreSQL
 * for configuration and usage receipts.
 */
@Configuration
@Profile("durable")
public class DurableInfrastructureConfiguration {
    @Bean(destroyMethod = "close")
    DataSource durableDataSource(
            @Value("${rate-limiter.durable.jdbc-url}") String jdbcUrl,
            @Value("${rate-limiter.durable.username}") String username,
            @Value("${rate-limiter.durable.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(1_000);
        config.setPoolName("rate-limiter-control-plane");
        return new HikariDataSource(config);
    }

    @Bean
    ControlPlaneStore controlPlaneStore(DataSource dataSource, ObjectMapper json,
            RuleConfigurationValidator validator) {
        return new JdbcControlPlaneStore(dataSource, json, validator);
    }

    @Bean
    RuleReplica ruleReplica() {
        return new RuleReplica();
    }

    @Bean
    JdbcUsageOutbox usageOutbox(DataSource dataSource) {
        return new JdbcUsageOutbox(dataSource);
    }
}
