package pl.jakubtworek.cloudarchitecture.repository;

import pl.jakubtworek.cloudarchitecture.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository backed by Cloud SQL.
 *
 * The application should use a controlled connection pool because serverless
 * platforms may create many service instances in parallel.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {}
