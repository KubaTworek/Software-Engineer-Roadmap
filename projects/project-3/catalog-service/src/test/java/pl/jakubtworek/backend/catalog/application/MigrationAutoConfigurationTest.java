package pl.jakubtworek.backend.catalog.application;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationAutoConfigurationTest {
    @Test
    void runtimeIncludesBootFlywayIntegrationNotOnlyMigrationEngine() {
        // Sam flyway-core pozwala testom ręcznie wykonać migrate(), ale nie uruchamia
        // migracji podczas startu Boot 4. Na pustej bazie Hibernate validate wtedy zawodzi.
        // To kontrakt classpath; wykonanie DDL nadal wymaga testu z PostgreSQL.
        assertThat(ClassUtils.isPresent(
                "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                getClass().getClassLoader())).isTrue();
    }
}
