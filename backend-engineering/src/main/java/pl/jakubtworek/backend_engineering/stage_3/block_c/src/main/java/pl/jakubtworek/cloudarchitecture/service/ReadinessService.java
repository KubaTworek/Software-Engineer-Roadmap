package pl.jakubtworek.cloudarchitecture.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Verifies whether the instance is ready to receive production traffic.
 *
 * Readiness depends on Cloud SQL and Redis. Redis is optional for product-cache
 * reads, but critical here because the same service uses it for idempotency and
 * rate limiting. In a larger system, separate workloads may expose different
 * readiness policies for different traffic classes.
 */
@Service
public class ReadinessService {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public ReadinessService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    /** Checks database and Redis availability without changing application data. */
    public void verifyDependencies() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            var connectionFactory = Objects.requireNonNull(
                    redisTemplate.getConnectionFactory(),
                    "redis connectionFactory must not be null"
            );
            try (RedisConnection connection = connectionFactory.getConnection()) {
                connection.ping();
            }
        } catch (RuntimeException exception) {
            throw new DependencyUnavailableException("critical dependency is unavailable", exception);
        }
    }
}
