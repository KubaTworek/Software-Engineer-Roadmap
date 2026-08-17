package com.example.ecommerce.idempotency;

import com.example.ecommerce.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Serwis odpowiedzialny za obsługę idempotencji operacji biznesowych.
 *
 * W tym projekcie najważniejsze zastosowanie to checkout.
 *
 * Idempotencja oznacza, że wielokrotne wysłanie tego samego requestu
 * z tym samym Idempotency-Key nie spowoduje wielokrotnego wykonania operacji.
 *
 * Chroni to przed:
 * - podwójnym kliknięciem "Kupuję",
 * - retry po stronie frontendu,
 * - timeoutem HTTP,
 * - ponowieniem requestu przez mobile app,
 * - retry na API Gateway,
 * - przypadkowym utworzeniem dwóch zamówień i dwóch płatności.
 */
@Service
public class IdempotencyService {

    /**
     * Repozytorium rekordów idempotencji.
     *
     * Każdy rekord reprezentuje jedną logiczną próbę wykonania operacji,
     * np. checkoutu.
     */
    private final IdempotencyRecordRepository records;

    /**
     * ObjectMapper używany do serializacji requestu.
     *
     * Request jest zamieniany na JSON, a potem hashowany.
     * Dzięki temu możemy sprawdzić, czy ten sam Idempotency-Key
     * nie został użyty z innym body requestu.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytorium rekordów oraz ObjectMappera
     * do policzenia stabilnego hash requestu.
     */
    public IdempotencyService(
            IdempotencyRecordRepository records,
            ObjectMapper objectMapper
    ) {
        this.records = records;
        this.objectMapper = objectMapper;
    }

    /**
     * Liczy hash requestu.
     *
     * Flow:
     * 1. Serializuje request do JSON-a.
     * 2. Liczy SHA-256 z tego JSON-a.
     * 3. Zwraca hash w formacie hex.
     *
     * Ten hash jest później zapisywany w IdempotencyRecord.
     *
     * Po co?
     * Żeby wykryć niebezpieczną sytuację:
     *
     * Request 1:
     * Idempotency-Key: abc
     * shippingMethod: STANDARD
     *
     * Request 2:
     * Idempotency-Key: abc
     * shippingMethod: EXPRESS
     *
     * To nie jest ten sam logiczny request.
     * System powinien wtedy zwrócić 409 Conflict zamiast udawać,
     * że wszystko jest w porządku.
     */
    public String hashRequest(Object request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] jsonBytes = objectMapper
                    .writeValueAsString(request)
                    .getBytes(StandardCharsets.UTF_8);

            return HexFormat.of()
                    .formatHex(digest.digest(jsonBytes));
        } catch (Exception e) {
            /*
             * Jeśli nie potrafimy policzyć hasha, nie możemy bezpiecznie
             * wykonać operacji idempotentnej.
             *
             * To błąd techniczny aplikacji, więc rzucamy IllegalStateException.
             */
            throw new IllegalStateException("Could not hash idempotent request", e);
        }
    }

    /**
     * Rozpoczyna operację idempotentną albo zwraca istniejący rekord.
     *
     * Rekord jest identyfikowany przez:
     * - Idempotency-Key,
     * - userId,
     * - operation.
     *
     * Dzięki userId ten sam klucz może zostać użyty przez różnych użytkowników
     * bez konfliktu między ich operacjami.
     *
     * Dzięki operation ten sam klucz nie miesza różnych procesów,
     * np. CHECKOUT i REFUND.
     */
    @Transactional
    public IdempotencyRecord startOrGet(
            String key,
            Long userId,
            String operation,
            String requestHash
    ) {
        var existing = records.findByIdempotencyKeyAndUserIdAndOperation(
                key,
                userId,
                operation
        );

        /*
         * Jeśli rekord już istnieje, oznacza to, że klient ponowił request
         * z tym samym Idempotency-Key.
         */
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            /*
             * Ten sam Idempotency-Key nie może zostać użyty dla innego request body.
             *
             * To chroni przed błędami klienta i niejednoznacznością:
             * czy mamy zwrócić poprzedni wynik, czy wykonać nową operację?
             *
             * Odpowiedź 409 Conflict jasno mówi:
             * ten klucz był już użyty do innego requestu.
             */
            if (!record.getRequestHash().equals(requestHash)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request body"
                );
            }

            /*
             * Body requestu jest takie samo, więc zwracamy istniejący rekord.
             *
             * CheckoutService zdecyduje dalej:
             * - jeśli COMPLETED, zwróci poprzednie zamówienie i płatność,
             * - jeśli FAILED albo STARTED, może obsłużyć to zgodnie z logiką procesu.
             */
            return record;
        }

        /*
         * Nie ma jeszcze rekordu dla tego klucza, użytkownika i operacji.
         *
         * Tworzymy nowy rekord idempotencji.
         * Od tego momentu kolejne requesty z tym samym kluczem będą trafiały
         * do tego samego rekordu.
         */
        return records.save(
                new IdempotencyRecord(
                        key,
                        userId,
                        operation,
                        requestHash
                )
        );
    }

    /**
     * Oznacza operację idempotentną jako zakończoną sukcesem.
     *
     * Dla checkoutu zapisujemy:
     * - orderId,
     * - paymentId.
     *
     * Dzięki temu ponowiony request może zwrócić dokładnie ten sam wynik,
     * bez tworzenia nowego zamówienia i nowej płatności.
     */
    @Transactional
    public void complete(
            IdempotencyRecord record,
            Long orderId,
            Long paymentId
    ) {
        record.complete(orderId, paymentId);
    }

    /**
     * Oznacza operację idempotentną jako zakończoną błędem.
     *
     * Używane, gdy checkout nie doszedł do końca, np.:
     * - koszyk był pusty,
     * - zabrakło stocku,
     * - nie udało się utworzyć płatności,
     * - wystąpił błąd techniczny.
     *
     * Taki status pozwala później odróżnić operację zakończoną sukcesem
     * od próby, która została przerwana.
     */
    @Transactional
    public void fail(IdempotencyRecord record) {
        record.fail();
    }
}