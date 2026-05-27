package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.OutboundMessage;
import pl.jakubtworek.booking.repository.OutboundMessageRepository;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class EmailSenderService {
    private final OutboundMessageRepository outboundMessageRepository;
    private final ThreadPoolExecutor executor;

    public EmailSenderService(OutboundMessageRepository outboundMessageRepository, ThreadPoolExecutor bookingAsyncExecutor) {
        this.outboundMessageRepository = outboundMessageRepository;
        this.executor = bookingAsyncExecutor;
    }

    public CompletableFuture<SideEffectResult> sendConfirmationEmail(ReservationResponse reservation) {
        return CompletableFuture.supplyAsync(() -> sendEmail(reservation), executor)
                .exceptionally(error -> saveEmailFailure(reservation, error));
    }

    @Transactional
    public SideEffectResult sendEmail(ReservationResponse reservation) {
        String payload = "Confirmation email for " + reservation.customerEmail();
        if (reservation.customerEmail().contains("email-fail")) {
            throw new IllegalStateException("Simulated email provider failure");
        }
        outboundMessageRepository.save(OutboundMessage.sent(reservation.id(), "EMAIL", payload));
        return SideEffectResult.success("email");
    }

    @Transactional
    public SideEffectResult saveEmailFailure(ReservationResponse reservation, Throwable error) {
        outboundMessageRepository.save(OutboundMessage.failed(
                reservation.id(),
                "EMAIL",
                "Confirmation email for " + reservation.customerEmail(),
                error
        ));
        return SideEffectResult.failure("email", error);
    }
}
