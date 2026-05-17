package pl.jakubtworek.backend_systems_lab_stage_1.block_c.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * JpaRepository provides:
 * - CRUD operations,
 * - pagination,
 * - sorting,
 * - flush support,
 * - query derivation.
 */
public interface UserRepository
        extends JpaRepository<User, Long> {

    /**
     * Query derivation.
     *
     * Spring automatically generates SQL
     * based on method name.
     */
    List<User> findByLastName(String lastName);

    /**
     * Query derivation with multiple conditions.
     */
    List<User> findByAgeGreaterThan(Integer age);

    /**
     * Pagination support.
     *
     * Pageable contains:
     * - page number,
     * - page size,
     * - sorting.
     */
    Page<User> findAllByAgeGreaterThan(
            Integer age,
            Pageable pageable
    );

    /**
     * Interface projection.
     *
     * Spring fetches only required columns.
     */
    List<UserNameProjection> findAllProjectedBy();

    /**
     * Custom JPQL query.
     *
     * JPQL operates on entities, not table names.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.age > :age
            """)
    List<User> findOlderThan(@Param("age") int age);

    /**
     * JOIN FETCH prevents N+1 problem.
     *
     * Orders are loaded in the same SQL query.
     */
    @Query("""
            SELECT DISTINCT u
            FROM User u
            LEFT JOIN FETCH u.orders
            """)
    List<User> findAllWithOrders();

    /**
     * EntityGraph is alternative to JOIN FETCH.
     *
     * It tells Hibernate which relations
     * should be fetched eagerly.
     */
    @EntityGraph(attributePaths = "orders")
    List<User> findByFirstName(String firstName);

    /**
     * Native SQL query.
     *
     * Useful for database-specific features.
     */
    @Query(
            value = """
                    SELECT *
                    FROM users
                    WHERE age > :age
                    """,
            nativeQuery = true
    )
    List<User> findOlderThanNative(@Param("age") int age);
}