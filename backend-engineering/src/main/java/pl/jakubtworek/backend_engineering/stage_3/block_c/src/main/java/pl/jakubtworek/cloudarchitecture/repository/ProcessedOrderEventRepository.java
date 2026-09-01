package pl.jakubtworek.cloudarchitecture.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.jakubtworek.cloudarchitecture.entity.ProcessedOrderEventEntity;

/** Durable store of messages already handled by the order worker. */
public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEventEntity, Long> {
}
