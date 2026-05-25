package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

/**
 * Cloud SQL connection pool configuration.
 *
 * Serverless platforms can scale horizontally, so each instance must keep
 * database connection pools small and predictable.
 */
@Configuration
public class DataSourceConfig {
    /**
     * Creates a HikariCP pool.
     *
     * Example: maxPoolSize=5 and max Cloud Run instances=10 means up to
     * roughly 50 database connections from this service.
     */
    @Bean
    public DataSource dataSource(
            @Value("${DB_JDBC_URL}") String jdbcUrl,
            @Value("${DB_USER}") String username,
            @Value("${DB_PASSWORD}") String password,
            @Value("${DB_POOL_SIZE:5}") int maxPoolSize
    ) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setPoolName("cloud-sql-pool");
        return new HikariDataSource(config);
    }
}
