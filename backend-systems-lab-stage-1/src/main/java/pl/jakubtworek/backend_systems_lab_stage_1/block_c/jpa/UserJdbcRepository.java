package pl.jakubtworek.backend_systems_lab_stage_1.block_c.jpa;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Example alternative to JPA.
 *
 * JdbcTemplate gives full SQL control.
 *
 * Useful when:
 * - performance is critical,
 * - query is very complex,
 * - ORM overhead is undesirable.
 */
@Repository
public class UserJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes raw SQL query.
     */
    public List<String> findUsernames() {

        return jdbcTemplate.query(
                "SELECT first_name FROM users",
                (rs, rowNum) -> rs.getString("first_name")
        );
    }
}