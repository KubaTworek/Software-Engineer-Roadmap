package com.example.urlshortener.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration
@ConditionalOnProperty(prefix = "app.datasource", name = "read-replica-enabled", havingValue = "true")
public class DataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource(ReadReplicaProperties properties) {
        DataSource writeDataSource = hikari(properties.writeUrl(), properties.writeUsername(), properties.writePassword(), "write-pool");
        DataSource readDataSource = hikari(properties.readUrl(), properties.readUsername(), properties.readPassword(), "read-pool");

        ReadWriteRoutingDataSource routingDataSource = new ReadWriteRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceType.WRITE, writeDataSource);
        targets.put(DataSourceType.READ, readDataSource);
        routingDataSource.setTargetDataSources(targets);
        routingDataSource.setDefaultTargetDataSource(writeDataSource);
        routingDataSource.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(routingDataSource);
    }

    private DataSource hikari(String url, String username, String password, String poolName) {
        HikariDataSource dataSource = DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .url(url)
            .username(username)
            .password(password)
            .build();
        dataSource.setPoolName(poolName);
        dataSource.setMaximumPoolSize(poolName.equals("write-pool") ? 10 : 20);
        dataSource.setMinimumIdle(2);
        return dataSource;
    }
}
