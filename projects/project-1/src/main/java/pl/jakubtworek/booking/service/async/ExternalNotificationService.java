package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.OutboundMessage;
import pl.jakubtworek.booking.repository.OutboundMessageRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ExternalNotificationService {
    private final OutboundMessageRepository outboundMessageRepository;
    private final ThreadPoolExecutor executor;

    public ExternalNotificationService(OutboundMessageRepository outboundMessageRepository, ThreadPoolExecutor bookingAsyncExecutor) {
        this.outboundMessageRepository = outboundMessageRepository;
        this.executor = bookingAsyncExecutor;
    }

    public CompletableFuture<SideEffectResult> notifyExternalSystems(ReservationResponse reservation) {
        CompletableFuture<SideEffectResult> providerA = CompletableFuture.supplyAsync(() -> notifyProvider(reservation, "WEBHOOK_A", 80), executor);
        CompletableFuture<SideEffectResult> providerB = CompletableFuture.supplyAsync(() -> notifyProvider(reservation, "WEBHOOK_B", 20), executor);

        return providerA.applyToEither(providerB, result -> result)
                .exceptionally(error -> saveNotificationFailure(reservation, error));
    }

    @Transactional
    public SideEffectResult notifyProvider(ReservationResponse reservation, String channel, long latencyMs) {
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Notification interrupted", e);
        }
        outboundMessageRepository.save(OutboundMessage.sent(
                reservation.id(),
                channel,
                "External notification for reservation " + reservation.id()
        ));
        return SideEffectResult.success(channel);
    }

    @Transactional
    public SideEffectResult saveNotificationFailure(ReservationResponse reservation, Throwable error) {
        outboundMessageRepository.save(OutboundMessage.failed(
                reservation.id(),
                "WEBHOOK",
                "External notification for reservation " + reservation.id(),
                error
        ));
        return SideEffectResult.failure("notification", error);
    }
}
