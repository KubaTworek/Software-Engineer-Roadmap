package pl.jakubtworek.marketplace.integrationtest;

import org.testcontainers.containers.PostgreSQLContainer;

public final class IntegrationPostgresContainer {

    private static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("marketplace")
                    .withUsername("marketplace")
                    .withPassword("marketplace");

    static {
        INSTANCE.start();
    }

    private IntegrationPostgresContainer() {
    }

    public static PostgreSQLContainer<?> getInstance() {
        return INSTANCE;
    }
}