package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Verifies whether the instance is ready to receive production traffic.
 *
 * Readiness depends on critical external services such as Cloud SQL and Redis.
 */
@Service
public class ReadinessService {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public ReadinessService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    /** Checks database and cache availability. */
    public void verifyDependencies() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        redisTemplate.getConnectionFactory().getConnection().ping();
    }
}
