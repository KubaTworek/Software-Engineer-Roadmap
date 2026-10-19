package com.example.paymentsystem.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Serwis odpowiedzialny za idempotencję operacji HTTP.
 *
 * Idempotencja zabezpiecza system przed wykonaniem tej samej operacji kilka razy,
 * np. gdy klient:
 * - kliknie "Zapłać" dwa razy,
 * - ponowi request po timeoutcie,
 * - dostanie błąd sieciowy i spróbuje jeszcze raz.
 *
 * W Payment Systemie jest to krytyczne, bo bez idempotencji moglibyśmy:
 * - utworzyć dwie płatności dla tego samego zamówienia,
 * - podwójnie obciążyć klienta,
 * - wykonać podwójny refund,
 * - niespójnie zaksięgować operację w ledgerze.
 */
@Service
public class IdempotencyService {

    /**
     * Repozytorium zapisujące wynik pierwszego requestu dla danego Idempotency-Key.
     *
     * Rekord idempotencji przechowuje:
     * - klucz idempotencji,
     * - scope operacji,
     * - hash requestu,
     * - response body,
     * - HTTP status.
     *
     * Dzięki temu kolejne identyczne requesty mogą dostać dokładnie tę samą odpowiedź.
     */
    private final IdempotencyRecordRepository repository;
    private final IdempotencyClaimWriter claimWriter;

    public IdempotencyService(IdempotencyRecordRepository repository, IdempotencyClaimWriter claimWriter) {
        this.repository = repository;
        this.claimWriter = claimWriter;
    }

    /**
     * Atomically reserves a command before any side effect. The database unique
     * key, rather than a JVM lock, coordinates all application instances.
     */
    public IdempotencyClaim claim(String key, String scope, String requestBody) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1-200 characters");
        }
        String requestHash = hash(requestBody);
        Optional<IdempotencyRecord> existing = repository.findById(new IdempotencyRecordId(key, scope));
        if (existing.isPresent()) return classify(existing.get(), requestHash);

        try {
            IdempotencyRecord created = new IdempotencyRecord(key, scope, requestHash,
                    Instant.now().plus(Duration.ofMinutes(2)));
            claimWriter.insert(created);
            return new IdempotencyClaim(IdempotencyClaim.Outcome.ACQUIRED, created);
        } catch (DataIntegrityViolationException raceLost) {
            IdempotencyRecord winner = repository.findById(new IdempotencyRecordId(key, scope))
                    .orElseThrow(() -> raceLost);
            return classify(winner, requestHash);
        }
    }

    private IdempotencyClaim classify(IdempotencyRecord record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) throw new IdempotencyConflictException();
        if (record.getStatus() == IdempotencyStatus.COMPLETED || record.getStatus() == IdempotencyStatus.UNKNOWN) {
            return new IdempotencyClaim(IdempotencyClaim.Outcome.REPLAY, record);
        }
        if (record.getStatus() == IdempotencyStatus.FAILED_RETRYABLE) {
            reacquire(record);
            return new IdempotencyClaim(IdempotencyClaim.Outcome.ACQUIRED, record);
        }
        return new IdempotencyClaim(IdempotencyClaim.Outcome.IN_PROGRESS, record);
    }

    @Transactional
    protected void reacquire(IdempotencyRecord record) {
        record.reacquire(Instant.now().plus(Duration.ofMinutes(2)));
        repository.save(record);
    }

    @Transactional
    public void complete(String key, String scope, String responseBody, HttpStatus status, String resourceId) {
        IdempotencyRecord record = required(key, scope);
        record.complete(responseBody, status.value(), resourceId);
    }

    @Transactional
    public void unknown(String key, String scope, String responseBody, String resourceId) {
        IdempotencyRecord record = required(key, scope);
        record.markUnknown(responseBody, HttpStatus.SERVICE_UNAVAILABLE.value(), resourceId);
    }

    @Transactional
    public void retryable(String key, String scope) {
        required(key, scope).retryable();
    }

    private IdempotencyRecord required(String key, String scope) {
        return repository.findById(new IdempotencyRecordId(key, scope))
                .orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared"));
    }

    /**
     * Liczy SHA-256 z request body.
     *
     * Nie zapisujemy całego requestu jako podstawy porównania.
     * Zapisujemy jego hash, bo:
     * - jest krótszy,
     * - łatwo go porównać,
     * - nie musimy trzymać pełnego payloadu requestu,
     * - zmiana nawet jednego pola da inny hash.
     *
     * Uwaga:
     * hash będzie stabilny tylko wtedy, gdy requestBody ma stabilną postać.
     * Dlatego controller powinien używać kanonicznej serializacji JSON,
     * np. przez ObjectMapper zapisujący request DTO do stringa.
     */
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            /**
             * SHA-256 jest standardowym algorytmem dostępnym w JVM.
             * Jeżeli z jakiegoś powodu nie da się go użyć,
             * jest to problem infrastrukturalny/programistyczny,
             * więc rzucamy IllegalStateException.
             */
            throw new IllegalStateException(e);
        }
    }
}
