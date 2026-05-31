package pl.jakubtworek.booking.exception;

import org.hibernate.LazyInitializationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.jakubtworek.booking.dto.ErrorResponse;
import pl.jakubtworek.booking.dto.FieldErrorResponse;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;

/**
 * Globalny handler wyjątków dla kontrolerów REST.
 *
 * @RestControllerAdvice pozwala przechwytywać wyjątki rzucane z kontrolerów
 * i serwisów wywoływanych przez kontrolery, a następnie mapować je na spójne
 * odpowiedzi HTTP.
 *
 * Dzięki temu kontrolery nie muszą zawierać powtarzalnego try/catch.
 *
 * Ten handler odpowiada za:
 *
 * - 404 dla brakujących zasobów,
 * - 409 dla braku dostępnych miejsc,
 * - 400 dla błędów reguł biznesowych i walidacji,
 * - 500 dla celowego przykładu LazyInitializationException,
 * - rozpakowanie wyjątków z CompletableFuture.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Obsługuje sytuację, w której zasób nie istnieje.
     *
     * Przykłady:
     * - event nie istnieje,
     * - rezerwacja nie istnieje,
     * - capacity pool nie istnieje,
     * - read model document nie istnieje.
     *
     * Mapowanie:
     * NotFoundException -> HTTP 404 NOT_FOUND.
     */
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        404,
                        "NOT_FOUND",
                        exception.getMessage()
                ));
    }

    /**
     * Obsługuje brak dostępnych miejsc na event.
     *
     * To nie jest błąd walidacji requestu.
     * Request może być poprawny, ale aktualny stan systemu nie pozwala go wykonać,
     * bo pula miejsc jest wyczerpana.
     *
     * Dlatego sensownym statusem jest HTTP 409 CONFLICT.
     */
    @ExceptionHandler(CapacityUnavailableException.class)
    ResponseEntity<ErrorResponse> handleCapacityUnavailable(CapacityUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(
                        409,
                        "CAPACITY_UNAVAILABLE",
                        exception.getMessage()
                ));
    }

    /**
     * Obsługuje naruszenia reguł biznesowych oraz część błędów stanu aplikacji.
     *
     * Przykłady:
     * - próba potwierdzenia rezerwacji, która nie jest PENDING,
     * - odrzucona płatność,
     * - błędny refresh token,
     * - niepoprawny argument domenowy.
     *
     * W tej wersji projektu mapujemy je na HTTP 400.
     *
     * Uwaga:
     * nie każdy IllegalStateException w prawdziwej aplikacji powinien być 400.
     * Czasem IllegalStateException oznacza błąd programistyczny i powinien być 500.
     * Tutaj jest to uproszczone edukacyjnie.
     */
    @ExceptionHandler({
            BusinessRuleException.class,
            IllegalStateException.class,
            IllegalArgumentException.class
    })
    ResponseEntity<ErrorResponse> handleBusinessRule(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(
                        400,
                        "BUSINESS_RULE_VIOLATION",
                        exception.getMessage()
                ));
    }

    /**
     * Obsługuje wyjątki opakowane przez CompletableFuture.
     *
     * CompletableFuture często nie zwraca oryginalnego wyjątku bezpośrednio.
     * Zamiast tego opakowuje go w CompletionException.
     *
     * Przykład:
     *
     * CompletableFuture.join()
     *
     * jeśli future zakończył się błędem, rzuci CompletionException.
     *
     * Dlatego tutaj rozpakowujemy cause i mapujemy znane wyjątki tak samo,
     * jak w synchronicznym flow.
     */
    @ExceptionHandler(CompletionException.class)
    ResponseEntity<ErrorResponse> handleCompletionException(CompletionException exception) {
        Throwable cause = exception.getCause();

        if (cause instanceof NotFoundException notFound) {
            return handleNotFound(notFound);
        }

        if (cause instanceof CapacityUnavailableException capacityUnavailable) {
            return handleCapacityUnavailable(capacityUnavailable);
        }

        if (cause instanceof BusinessRuleException businessRule) {
            return handleBusinessRule(businessRule);
        }

        /*
         * Jeśli nie znamy przyczyny, nie ujawniamy szczegółów technicznych klientowi.
         * Zwracamy ogólny błąd async.
         */
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "ASYNC_ERROR",
                        "Async operation failed"
                ));
    }

    /**
     * Obsługuje LazyInitializationException.
     *
     * Ten wyjątek pojawia się, gdy kod próbuje odczytać lazy relation poza aktywnym
     * persistence contextem Hibernate.
     *
     * W projekcie ten handler jest szczególnie przydatny w etapie Spring pitfalls,
     * gdzie celowo pokazujemy endpoint powodujący LazyInitializationException.
     *
     * Produkcyjnie nie powinno się traktować tego jako normalnego błędu biznesowego.
     * To zwykle błąd projektowy w granicy transakcji lub mapowania DTO.
     */
    @ExceptionHandler(LazyInitializationException.class)
    ResponseEntity<ErrorResponse> handleLazyInitialization(LazyInitializationException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        500,
                        "LAZY_INITIALIZATION",
                        "Lazy relation was accessed outside an active persistence context"
                ));
    }

    /**
     * Obsługuje błędy walidacji request body.
     *
     * MethodArgumentNotValidException pojawia się najczęściej wtedy, gdy:
     *
     * - kontroler ma @Valid @RequestBody,
     * - DTO ma adnotacje typu @NotBlank, @Email, @Positive,
     * - request nie spełnia tych reguł.
     *
     * Zwracamy HTTP 400 z listą błędów pól.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldErrorResponse> fields = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldErrorResponse(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                400,
                "VALIDATION_ERROR",
                "Request validation failed",
                fields
        );

        return ResponseEntity.badRequest().body(body);
    }
}