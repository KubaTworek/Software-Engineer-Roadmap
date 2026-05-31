package pl.jakubtworek.booking.integration.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.OutboundMessage;
import pl.jakubtworek.booking.entity.ReservationStatus;
import pl.jakubtworek.booking.exception.BusinessRuleException;
import pl.jakubtworek.booking.repository.AuditLogRepository;
import pl.jakubtworek.booking.repository.OutboundMessageRepository;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.ReservationService;
import pl.jakubtworek.booking.service.async.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM outbound_messages",
        "DELETE FROM audit_logs",
        "DELETE FROM reservations",
        "DELETE FROM capacity_pools",
        "DELETE FROM refresh_tokens",
        "DELETE FROM app_users",
        "DELETE FROM events",
        "DELETE FROM customers",
        "DELETE FROM organizations"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AsyncStage3IntegrationTest {
    @Autowired
    EventService eventService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    AsyncReservationService asyncReservationService;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    OutboundMessageRepository outboundMessageRepository;

    @Autowired
    PaymentProviderClient paymentProviderClient;

    @Test
    void approvedPaymentConfirmsReservationAndRunsFanOutFanInSideEffects() throws Exception {
        // given
        ReservationResponse pending = createReservation("async-approved@example.com");

        // when
        AsyncConfirmationResult result = asyncReservationService
                .confirmAndWaitForSideEffects(pending.id(), PaymentScenario.APPROVED)
                .get(5, TimeUnit.SECONDS);

        // then
        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.deliverySummary().email().success()).isTrue();
        assertThat(result.deliverySummary().notification().success()).isTrue();
        assertThat(result.auditResult().success()).isTrue();

        assertThat(auditLogRepository.findByReservationId(pending.id()))
                .hasSize(1)
                .anySatisfy(audit -> assertThat(audit.getType()).isEqualTo("RESERVATION_CONFIRMED"));

        List<OutboundMessage> messages = outboundMessageRepository.findByReservationId(pending.id());
        assertThat(messages)
                .extracting(OutboundMessage::getChannel)
                .contains("EMAIL");
        assertThat(messages)
                .extracting(OutboundMessage::getStatus)
                .contains("SENT");
    }

    @Test
    void emailFailureUsesFallbackAndDoesNotRollbackReservationConfirmation() throws Exception {
        // given
        ReservationResponse pending = createReservation("email-fail@example.com");

        // when
        AsyncConfirmationResult result = asyncReservationService
                .confirmAndWaitForSideEffects(pending.id(), PaymentScenario.APPROVED)
                .get(5, TimeUnit.SECONDS);

        // then
        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(result.deliverySummary().email().success()).isFalse();
        assertThat(reservationService.get(pending.id()).status()).isEqualTo(ReservationStatus.CONFIRMED);

        assertThat(outboundMessageRepository.findByReservationId(pending.id()))
                .anySatisfy(message -> {
                    assertThat(message.getChannel()).isEqualTo("EMAIL");
                    assertThat(message.getStatus()).isEqualTo("FAILED");
                    assertThat(message.getErrorMessage()).contains("Simulated email provider failure");
                });
    }

    @Test
    void slowPaymentProviderTimesOutAndMarksReservationAsPaymentTimeout() throws Exception {
        // given
        ReservationResponse pending = createReservation("payment-timeout@example.com");

        // when
        long startedAt = System.nanoTime();
        ReservationResponse result = asyncReservationService
                .confirm(pending.id(), PaymentScenario.SLOW)
                .get(4, TimeUnit.SECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        // then
        assertThat(result.status()).isEqualTo(ReservationStatus.PAYMENT_TIMEOUT);
        assertThat(elapsedMillis).isLessThan(3_500);
        assertEventually(() -> assertThat(paymentProviderClient.wasLastSlowCallInterrupted()).isTrue());
        assertThat(reservationService.get(pending.id()).status()).isEqualTo(ReservationStatus.PAYMENT_TIMEOUT);
    }

    @Test
    void failingPaymentProviderUsesFallbackAndMarksReservationAsPaymentTimeout() throws Exception {
        // given
        ReservationResponse pending = createReservation("payment-failure@example.com");

        // when
        ReservationResponse result = asyncReservationService
                .confirm(pending.id(), PaymentScenario.FAILING)
                .get(5, TimeUnit.SECONDS);

        // then
        assertThat(result.status()).isEqualTo(ReservationStatus.PAYMENT_TIMEOUT);
        assertThat(reservationService.get(pending.id()).status()).isEqualTo(ReservationStatus.PAYMENT_TIMEOUT);
    }

    @Test
    void declinedPaymentIsPropagatedAsBusinessErrorAndReservationStaysPending() {
        // given
        ReservationResponse pending = createReservation("payment-declined@example.com");

        // when & then
        assertThatThrownBy(() -> asyncReservationService
                .confirm(pending.id(), PaymentScenario.DECLINED)
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessRuleException.class);

        assertThat(reservationService.get(pending.id()).status()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void slowPaymentValidationCanBeCancelledAndInterruptsWorkerThread() {
        // given
        CompletableFuture<PaymentValidationResult> validation = paymentProviderClient
                .validatePayment(UUID.randomUUID(), PaymentScenario.SLOW);

        sleepBrieflySoSlowTaskStarts();

        // when
        validation.cancel(true);

        // then
        assertThat(validation).isCancelled();
        assertThatThrownBy(validation::join).isInstanceOf(CancellationException.class);
        assertEventually(() -> assertThat(paymentProviderClient.wasLastSlowCallInterrupted()).isTrue());
    }

    private ReservationResponse createReservation(String customerEmail) {
        EventResponse event = eventService.create(new EventCreateRequest(
                "Async Engineering Workshop",
                "Warsaw",
                "education",
                OffsetDateTime.now().plusDays(30),
                10
        ));
        return reservationService.create(event.id(), new ReservationCreateRequest("Async User", customerEmail));
    }

    private void sleepBrieflySoSlowTaskStarts() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for slow task to start", interrupted);
        }
    }

    private void assertEventually(Runnable assertion) {
        AssertionError lastError = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError error) {
                lastError = error;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for condition", interrupted);
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }
}
