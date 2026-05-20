package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

/**
 * Interface-based projection.
 *
 * Spring Data JPA can fetch only selected fields
 * instead of loading full entity.
 *
 * Useful for performance optimization.
 */
public interface UserNameProjection {

    String getFirstName();

    String getLastName();
}