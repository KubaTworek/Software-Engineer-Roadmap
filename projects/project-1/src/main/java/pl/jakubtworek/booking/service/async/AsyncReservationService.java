package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.exception.BusinessRuleException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class AsyncReservationService {
    private static final Duration PAYMENT_TIMEOUT = Duration.ofSeconds(2);

    private final PaymentProviderClient paymentProviderClient;
    private final ReservationStatusService reservationStatusService;
    private final EmailSenderService emailSenderService;
    private final AuditLogService auditLogService;
    private final ExternalNotificationService externalNotificationService;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService scheduler;

    public AsyncReservationService(
            PaymentProviderClient paymentProviderClient,
            ReservationStatusService reservationStatusService,
            EmailSenderService emailSenderService,
            AuditLogService auditLogService,
            ExternalNotificationService externalNotificationService,
            ThreadPoolExecutor bookingAsyncExecutor,
            ScheduledExecutorService bookingScheduler
    ) {
        this.paymentProviderClient = paymentProviderClient;
        this.reservationStatusService = reservationStatusService;
        this.emailSenderService = emailSenderService;
        this.auditLogService = auditLogService;
        this.externalNotificationService = externalNotificationService;
        this.executor = bookingAsyncExecutor;
        this.scheduler = bookingScheduler;
    }

    public CompletableFuture<ReservationResponse> confirm(UUID reservationId, PaymentScenario scenario) {
        CompletableFuture<ReservationResponse> confirmation = confirmAfterPaymentValidation(reservationId, scenario);
        confirmation.thenAccept(this::runSideEffectsInBackground);
        return confirmation;
    }

    public CompletableFuture<AsyncConfirmationResult> confirmAndWaitForSideEffects(UUID reservationId, PaymentScenario scenario) {
        return confirmAfterPaymentValidation(reservationId, scenario)
                .thenCompose(reservation -> runSideEffects(reservation)
                        .thenApply(sideEffects -> new AsyncConfirmationResult(
                                reservation,
                                sideEffects.deliverySummary(),
                                sideEffects.auditResult()
                        )));
    }

    private CompletableFuture<ReservationResponse> confirmAfterPaymentValidation(UUID reservationId, PaymentScenario scenario) {
        CompletableFuture<PaymentValidationResult> paymentValidation = paymentProviderClient.validatePayment(reservationId, scenario);

        return CompletableFutureTimeouts.withTimeout(paymentValidation, PAYMENT_TIMEOUT, scheduler)
                .thenCompose(result -> {
                    if (!result.approved()) {
                        throw new BusinessRuleException("Payment was not approved: " + result.reason());
                    }
                    return CompletableFuture.supplyAsync(
                            () -> reservationStatusService.confirmAfterPayment(reservationId),
                            executor
                    );
                })
                .exceptionallyCompose(error -> handlePaymentFailure(reservationId, error));
    }

    private CompletableFuture<ReservationResponse> handlePaymentFailure(UUID reservationId, Throwable error) {
        if (isBusinessRule(error)) {
            return CompletableFuture.failedFuture(unwrapCompletionException(error));
        }
        return CompletableFuture.supplyAsync(
                () -> reservationStatusService.markPaymentTimeout(reservationId),
                executor
        );
    }

    private void runSideEffectsInBackground(ReservationResponse reservation) {
        runSideEffects(reservation).exceptionally(error -> null);
    }

    private CompletableFuture<SideEffects> runSideEffects(ReservationResponse reservation) {
        CompletableFuture<SideEffectResult> email = emailSenderService.sendConfirmationEmail(reservation);
        CompletableFuture<SideEffectResult> notification = externalNotificationService.notifyExternalSystems(reservation);
        CompletableFuture<DeliverySummary> deliverySummary = email.thenCombine(notification, DeliverySummary::new);

        CompletableFuture<SideEffectResult> audit = auditLogService.writeReservationConfirmed(reservation.id());

        return CompletableFuture.allOf(deliverySummary, audit)
                .thenApply(ignored -> new SideEffects(deliverySummary.join(), audit.join()));
    }

    private boolean isBusinessRule(Throwable error) {
        return unwrapCompletionException(error) instanceof BusinessRuleException;
    }

    private Throwable unwrapCompletionException(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                return current;
            }
            current = current.getCause();
        }
        return current;
    }

    private record SideEffects(DeliverySummary deliverySummary, SideEffectResult auditResult) {
    }
}
