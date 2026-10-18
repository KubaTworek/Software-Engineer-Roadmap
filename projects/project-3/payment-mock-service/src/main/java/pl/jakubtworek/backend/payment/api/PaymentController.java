package pl.jakubtworek.backend.payment.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend.payment.chaos.PaymentChaosSettings;
import pl.jakubtworek.backend.payment.persistence.PaymentEntity;
import pl.jakubtworek.backend.payment.persistence.PaymentRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;
import java.util.UUID;
import java.time.Clock;

@RestController
@RequestMapping("/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final Random random = new Random();
    private final PaymentChaosSettings chaosSettings;
    private final PaymentRepository repository;
    private final Clock clock;

    public PaymentController(PaymentChaosSettings chaosSettings, PaymentRepository repository, Clock clock) {
        this.chaosSettings = chaosSettings;
        this.repository = repository;
        this.clock = clock;
    }

    @PostMapping
    @Transactional
    PaymentResponse pay(@RequestHeader("Idempotency-Key") String idempotencyKey,
                        @Valid @RequestBody PaymentRequest request) throws InterruptedException {
        /*
         * Retry po timeoutcie nie mówi klientowi, czy pierwsza próba dotarła do providera.
         * Provider musi więc zapamiętać rezultat pod stabilnym kluczem operacji. Mapa jest
         * edukacyjną granicą procesu; produkcyjnie rekord musi być trwały i współdzielony
         * przez wszystkie repliki payment-service.
         */
        repository.lockOperationKey("payment:" + idempotencyKey);
        repository.lockOperationKey("order:" + request.orderId());
        PaymentEntity payment = repository.findById(idempotencyKey)
                .or(() -> repository.findByOrderId(request.orderId()))
                .orElseGet(() -> {
                    simulateProvider(request);
                    return repository.save(PaymentEntity.paid(idempotencyKey, request, clock.instant()));
                });
        payment.requireSameRequest(request);
        PaymentResponse response = payment.toResponse();
        log.info("payment_result_returned orderId={} userId={} paymentId={} idempotencyKey={}",
                request.orderId(), request.userId(), response.paymentId(), idempotencyKey);
        return response;
    }

    /**
     * Advisory lock serializuje ten sam klucz i orderId pomiędzy replikami przed efektem.
     * Naiwne get→charge→insert potrafi obciążyć dwa razy, choć jedna transakcja przegra UNIQUE.
     */
    private void simulateProvider(PaymentRequest request) {
        try {
            Thread.sleep(random.nextInt(Math.max(1, chaosSettings.maxDelayMs())));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment interrupted", exception);
        }
        if (random.nextDouble() < chaosSettings.failureRate()) {
            log.warn("payment_failed orderId={} userId={} simulated=true", request.orderId(), request.userId());
            throw new IllegalStateException("Simulated payment provider failure");
        }
    }

    /** Lookup rozstrzyga niepewny timeout bez ponownego wykonywania efektu finansowego. */
    @GetMapping("/orders/{orderId}")
    @Transactional(readOnly = true)
    PaymentResponse findByOrder(@PathVariable UUID orderId) {
        return repository.findByOrderId(orderId)
                .map(PaymentEntity::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order " + orderId));
    }

    /**
     * Edukacyjna kompensacja sagi. Refund nie cofa transakcji rozproszonej; jest nowym,
     * idempotentnym efektem biznesowym, którego rezultat także trzeba zapamiętać.
     */
    @PostMapping("/orders/{orderId}/refund")
    @Transactional
    PaymentResponse refund(@PathVariable UUID orderId,
                           @RequestHeader("Idempotency-Key") String idempotencyKey) {
        repository.lockOperationKey("refund:" + idempotencyKey);
        repository.lockOperationKey("order:" + orderId);
        PaymentEntity payment = repository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order " + orderId));
        payment.refund(idempotencyKey, clock.instant());
        PaymentResponse response = payment.toResponse();
        log.info("payment_refunded orderId={} paymentId={}", orderId, response.paymentId());
        return response;
    }
}
