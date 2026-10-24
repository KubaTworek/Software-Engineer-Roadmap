package pl.jakubtworek.marketplace.integrationtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.jakubtworek.marketplace.payment.application.*;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.shared.kernel.Money;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Probe audytu: dwa fakty potwierdzają lukę, nie docelową semantykę. */
class PaymentRaceAuditProbeTest extends AbstractIntegrationTest {
    @Autowired PaymentRepository payments;
    @Autowired EventPublisher events;
    @Autowired PlatformTransactionManager transactions;

    @Test @Timeout(30)
    void twoConcurrentLookupsEmitTwoDifferentBusinessFactsForOneResolution() throws Exception {
        UUID order = UUID.randomUUID();
        var payment = Payment.uncertain(order, Money.of("20", "PLN"), "audit-operation", "timeout");
        payments.save(payment);
        var bothReadUnknown = new CountDownLatch(2);
        PaymentGateway gateway = new PaymentGateway() {
            public PaymentReservationResult reserve(UUID id, Money amount) { throw new AssertionError("lookup only"); }
            public PaymentReservationResult lookup(String id) {
                bothReadUnknown.countDown();
                try { if (!bothReadUnknown.await(10, TimeUnit.SECONDS)) throw new AssertionError("Missing concurrent lookup"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                return new PaymentReservationResult(Outcome.ACCEPTED, id, "resolved");
            }
        };
        var service = new ReconcileUnknownPaymentUseCase(gateway, payments, events);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Object> call = () -> new TransactionTemplate(transactions)
                    .execute(tx -> service.reconcile(order, UUID.randomUUID()));
            var a = executor.submit(call);
            var b = executor.submit(call);
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from integration.outbox_events where aggregate_id=? and event_type='PaymentReserved'",
                Integer.class, payment.id().value());
        assertEquals(2, count, "Obecna luka: dwa różne eventId dla tej samej logicznej zmiany");
        System.out.println("AUDIT CONFIRMED: concurrent UNKNOWN resolution emitted PaymentReserved=" + count);
    }
}