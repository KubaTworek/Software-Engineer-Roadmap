package com.ridesharing.mvp.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.user.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Serwis zapewniający idempotencję dla krytycznych operacji API.
 *
 * W aplikacji ride-sharing jest to szczególnie ważne dla operacji typu:
 * - zamówienie przejazdu,
 * - anulowanie przejazdu,
 * - akceptacja przejazdu przez kierowcę,
 * - płatność,
 * - refund.
 *
 * Problem, który rozwiązuje ta klasa:
 * klient może wysłać ten sam request kilka razy, np. przez timeout, retry,
 * słaby internet albo podwójne kliknięcie. Bez idempotencji system mógłby utworzyć
 * kilka przejazdów albo kilka razy wykonać tę samą operację.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    /**
     * Repozytorium kluczy idempotencji.
     *
     * Każdy unikalny Idempotency-Key dla danego użytkownika i endpointu
     * zapisuje informację o request body, statusie przetwarzania i odpowiedzi.
     */
    private final IdempotencyKeyRepository repository;

    /**
     * ObjectMapper jest używany do:
     * - serializacji requestu do JSON-a przed hashowaniem,
     * - serializacji odpowiedzi do zapisania w bazie,
     * - deserializacji zapisanej odpowiedzi przy ponowionym requestcie.
     */
    private final ObjectMapper objectMapper;

    /**
     * Wykonuje operację z obsługą idempotencji.
     *
     * Flow:
     * 1. Jeżeli klient nie podał Idempotency-Key, operacja wykonuje się normalnie.
     * 2. Jeżeli klucz jest za długi, request jest odrzucany.
     * 3. Tworzony jest hash request body.
     * 4. System sprawdza, czy ten klucz był już użyty przez tego użytkownika na tym endpointcie.
     * 5. Jeżeli był użyty z innym body, zwraca CONFLICT.
     * 6. Jeżeli był zakończony sukcesem, zwraca zapisaną wcześniej odpowiedź.
     * 7. Jeżeli nadal jest w trakcie, zwraca CONFLICT.
     * 8. Jeżeli to nowy klucz, zapisuje status PROCESSING i wykonuje operację.
     * 9. Po sukcesie zapisuje response body i oznacza klucz jako COMPLETED.
     * 10. Po błędzie oznacza klucz jako FAILED i propaguje wyjątek.
     *
     * Dzięki temu ponowienie identycznego requestu nie tworzy drugiego skutku ubocznego.
     */
    @Transactional
    public <T> T execute(
            String key,
            AppUser user,
            String endpoint,
            Object request,
            Class<T> responseType,
            Supplier<T> operation
    ) {
        /*
         * Brak klucza oznacza zwykłe wykonanie operacji.
         *
         * To daje kompatybilność z klientami, które jeszcze nie obsługują idempotencji.
         * Dla naprawdę krytycznych endpointów można jednak wymagać klucza obowiązkowo.
         */
        if (key == null || key.isBlank()) {
            return operation.get();
        }

        /*
         * Ograniczenie długości chroni bazę i logi przed nadużyciami.
         * 255 znaków to rozsądny limit dla nagłówka Idempotency-Key.
         */
        if (key.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key is too long");
        }

        /*
         * Hashujemy request body, żeby wykryć niebezpieczny przypadek:
         * ten sam Idempotency-Key użyty do innej operacji logicznej.
         */
        var hash = hash(request);

        /*
         * Klucz jest scoped per user + endpoint.
         *
         * Dzięki temu dwóch różnych użytkowników może przypadkowo użyć tego samego tekstowego klucza
         * bez konfliktu między sobą.
         */
        var existing = repository.findByKeyAndUserIdAndEndpoint(key, user.getId(), endpoint);

        if (existing.isPresent()) {
            var idem = existing.get();

            /*
             * Ten sam klucz, ale inne request body, to błąd klienta.
             *
             * Przykład:
             * Idempotency-Key: abc
             * pierwszy request: pickup=A, dropoff=B
             * drugi request: pickup=C, dropoff=D
             *
             * System nie może zgadywać, którą operację klient miał na myśli.
             */
            if (!idem.getRequestHash().equals(hash)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used with a different request body"
                );
            }

            /*
             * Jeżeli operacja już zakończyła się sukcesem, zwracamy dokładnie zapisaną odpowiedź.
             *
             * To jest sedno idempotencji:
             * retry klienta dostaje ten sam wynik, bez ponownego wykonania operacji biznesowej.
             */
            if (idem.getStatus() == IdempotencyStatus.COMPLETED && idem.getResponseBody() != null) {
                return read(idem.getResponseBody(), responseType);
            }

            /*
             * Jeżeli pierwszy request jeszcze się przetwarza, nie wykonujemy operacji drugi raz.
             *
             * Klient powinien ponowić request później z tym samym Idempotency-Key.
             */
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Request with this Idempotency-Key is still processing"
            );
        }

        /*
         * Tworzymy nowy rekord idempotencji przed wykonaniem operacji biznesowej.
         *
         * saveAndFlush jest ważne, bo rekord powinien być zapisany natychmiast,
         * zanim dojdzie do skutku ubocznego, np. utworzenia przejazdu.
         */
        var idem = IdempotencyKey.builder()
                .id(UUID.randomUUID())
                .key(key)
                .user(user)
                .endpoint(endpoint)
                .requestHash(hash)
                .status(IdempotencyStatus.PROCESSING)

                /*
                 * lockedUntil pozwala w przyszłości obsłużyć przypadek,
                 * w którym proces padł podczas przetwarzania requestu.
                 *
                 * W tej wersji kodu pole jest zapisywane, ale nie jest jeszcze używane
                 * do odzyskiwania zawieszonych operacji.
                 */
                .lockedUntil(Instant.now().plusSeconds(60))

                /*
                 * Klucz wygasa po 24 godzinach.
                 * Po tym czasie można go wyczyścić jobem sprzątającym.
                 */
                .expiresAt(Instant.now().plusSeconds(24 * 3600))
                .build();

        repository.saveAndFlush(idem);

        try {
            /*
             * Faktyczna operacja biznesowa, np. utworzenie przejazdu.
             * Powinna zostać wykonana tylko raz dla danego klucza.
             */
            var response = operation.get();

            /*
             * Po sukcesie zapisujemy odpowiedź.
             * Kolejny identyczny request zwróci tę odpowiedź bez ponownego wykonania operation.get().
             */
            idem.setStatus(IdempotencyStatus.COMPLETED);
            idem.setHttpStatus(200);
            idem.setResponseBody(write(response));
            repository.save(idem);

            return response;
        } catch (RuntimeException ex) {
            /*
             * Jeżeli operacja rzuci błąd, oznaczamy rekord jako FAILED.
             *
             * Obecna implementacja nie pozwala automatycznie ponowić FAILED requestu
             * z tym samym kluczem, bo przy kolejnym wywołaniu wpadnie w gałąź "still processing".
             * To warto dopracować.
             */
            idem.setStatus(IdempotencyStatus.FAILED);
            repository.save(idem);

            throw ex;
        }
    }

    /**
     * Tworzy SHA-256 hash z request body.
     *
     * Hash zamiast pełnego requestu ułatwia porównanie, czy ten sam klucz
     * został użyty z dokładnie takim samym payloadem.
     */
    private String hash(Object body) {
        try {
            var json = objectMapper.writeValueAsString(body);
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot hash request body");
        }
    }

    /**
     * Serializuje odpowiedź biznesową do JSON-a.
     *
     * Zapisana odpowiedź jest później zwracana przy retry identycznego requestu.
     */
    private String write(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot serialize idempotent response"
            );
        }
    }

    /**
     * Odtwarza odpowiedź z JSON-a zapisanego w rekordzie idempotencji.
     *
     * Dzięki temu klient dostaje taki sam typ odpowiedzi jak przy pierwszym wykonaniu operacji.
     */
    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot deserialize idempotent response"
            );
        }
    }
}