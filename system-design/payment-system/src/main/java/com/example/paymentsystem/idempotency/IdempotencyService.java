package com.example.paymentsystem.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

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

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * Sprawdza, czy dla danego requestu istnieje już zapisany wynik.
     *
     * Parametry:
     * - key: wartość nagłówka Idempotency-Key,
     * - scope: zakres operacji, np. POST:/v1/payments,
     * - requestBody: body requestu, z którego liczymy hash.
     *
     * Dlaczego potrzebujemy scope?
     *
     * Ten sam Idempotency-Key może teoretycznie zostać użyty w różnych endpointach.
     * Scope rozdziela operacje, np.:
     * - POST:/v1/payments,
     * - POST:/v1/payments/{id}/refunds,
     * - POST:/v1/payments/{id}/capture.
     *
     * Dzięki temu klucz jest unikalny w kontekście konkretnej operacji.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findReplay(
            String key,
            String scope,
            String requestBody
    ) {
        /**
         * Szukamy rekordu po złożonym kluczu:
         * - Idempotency-Key,
         * - scope.
         *
         * Jeżeli rekordu nie ma, oznacza to pierwszy request.
         * Operacja może zostać wykonana normalnie.
         */
        Optional<IdempotencyRecord> existing = repository.findById(
                new IdempotencyRecordId(key, scope)
        );

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        /**
         * Rekord istnieje, więc request jest powtórzeniem.
         *
         * Musimy jednak sprawdzić, czy payload jest identyczny.
         * Ten sam Idempotency-Key nie może być użyty do innego requestu.
         *
         * Przykład błędnego użycia:
         *
         * Pierwszy request:
         * {
         *   "orderId": "ord_1",
         *   "amount": 10000
         * }
         *
         * Drugi request z tym samym Idempotency-Key:
         * {
         *   "orderId": "ord_1",
         *   "amount": 20000
         * }
         *
         * Taka sytuacja musi skończyć się konfliktem, bo system nie wie,
         * czy klient chce powtórzyć poprzednią operację, czy wykonać nową.
         */
        if (!existing.get().getRequestHash().equals(hash(requestBody))) {
            throw new IdempotencyConflictException();
        }

        /**
         * Hash requestu się zgadza.
         *
         * To znaczy, że klient ponowił dokładnie ten sam request.
         * Controller powinien zwrócić zapisane wcześniej:
         * - response body,
         * - HTTP status.
         *
         * Nie wolno wykonywać operacji biznesowej drugi raz.
         */
        return existing;
    }

    /**
     * Zapisuje wynik pierwszego wykonania operacji.
     *
     * Ten zapis powinien nastąpić dopiero po udanym wykonaniu logiki biznesowej,
     * np. po:
     * - utworzeniu płatności,
     * - zapisaniu paymentu,
     * - uzyskaniu providerPaymentId od PSP,
     * - przygotowaniu response body.
     *
     * Dzięki temu kolejne requesty z tym samym Idempotency-Key
     * dostaną dokładnie tę samą odpowiedź.
     */
    @Transactional
    public void save(
            String key,
            String scope,
            String requestBody,
            String responseBody,
            HttpStatus status
    ) {
        repository.save(new IdempotencyRecord(
                key,
                scope,
                hash(requestBody),
                responseBody,
                status.value()
        ));
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