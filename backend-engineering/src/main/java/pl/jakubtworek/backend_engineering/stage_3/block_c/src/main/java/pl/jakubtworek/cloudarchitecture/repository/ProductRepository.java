package pl.jakubtworek.backend_engineering.stage_3.block_c.src.main.java.pl.jakubtworek.cloudarchitecture.repository;

import com.example.cloudarchitecture.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository backed by Cloud SQL.
 *
 * The application should use a controlled connection pool because serverless
 * platforms may create many service instances in parallel.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {}
