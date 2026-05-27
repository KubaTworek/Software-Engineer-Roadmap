package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.entity.AuditLog;
import pl.jakubtworek.booking.repository.AuditLogRepository;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final ThreadPoolExecutor executor;

    public AuditLogService(AuditLogRepository auditLogRepository, ThreadPoolExecutor bookingAsyncExecutor) {
        this.auditLogRepository = auditLogRepository;
        this.executor = bookingAsyncExecutor;
    }

    public CompletableFuture<SideEffectResult> writeReservationConfirmed(UUID reservationId) {
        return CompletableFuture.supplyAsync(() -> writeAuditLog(reservationId), executor)
                .exceptionally(error -> SideEffectResult.failure("audit", error));
    }

    @Transactional
    public SideEffectResult writeAuditLog(UUID reservationId) {
        auditLogRepository.save(new AuditLog(reservationId, "RESERVATION_CONFIRMED", "Reservation was confirmed asynchronously"));
        return SideEffectResult.success("audit");
    }
}
