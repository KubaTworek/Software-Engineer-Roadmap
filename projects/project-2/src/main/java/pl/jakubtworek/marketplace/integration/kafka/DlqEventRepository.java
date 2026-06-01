package pl.jakubtworek.marketplace.integration.kafka;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DlqEventRepository {
    void save(DlqEvent event);
    Optional<DlqEvent> findById(UUID id);
    List<DlqEvent> findByStatus(DlqEventStatus status, int limit);
    List<DlqEvent> findAll();
}
