package com.ridesharing.warehouse;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WarehouseSchemaInitializer {
    private final JdbcTemplate jdbc;

    public WarehouseSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void init() {
        jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS fact_events (
                      id BIGSERIAL PRIMARY KEY,
                      source_topic VARCHAR(128) NOT NULL,
                      event_type VARCHAR(128),
                      aggregate_id VARCHAR(128),
                      city_id VARCHAR(64),
                      payload JSONB NOT NULL,
                      received_at TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                """);
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_fact_events_city_received ON fact_events(city_id, received_at)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_fact_events_type_received ON fact_events(event_type, received_at)");
    }
}
