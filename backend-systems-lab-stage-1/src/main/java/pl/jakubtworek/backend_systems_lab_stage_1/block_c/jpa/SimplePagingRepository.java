package pl.jakubtworek.backend_systems_lab_stage_1.block_c.jpa;

import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * PagingAndSortingRepository provides:
 * - pagination,
 * - sorting.
 *
 * JpaRepository already extends this interface,
 * so usually JpaRepository is enough.
 */
public interface SimplePagingRepository
        extends PagingAndSortingRepository<User, Long> {
}