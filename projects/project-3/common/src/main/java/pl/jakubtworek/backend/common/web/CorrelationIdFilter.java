package pl.jakubtworek.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filtr odpowiedzialny za correlation ID i request ID.
 *
 * Correlation ID pozwala prześledzić jeden logiczny przepływ przez wiele serwisów, np.:
 *
 * api-gateway
 *   -> reservation-service
 *   -> order-service
 *   -> payment-mock-service
 *
 * Request ID identyfikuje pojedynczy request HTTP obsługiwany przez dany serwis.
 *
 * W praktyce:
 *
 * - correlationId pomaga śledzić cały flow biznesowy,
 * - requestId pomaga diagnozować konkretny request techniczny,
 * - oba identyfikatory trafiają do MDC,
 * - oba identyfikatory są zwracane w nagłówkach odpowiedzi.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Główna logika filtra.
     *
     * Filtr wykonuje się raz na request, bo dziedziczy po OncePerRequestFilter.
     *
     * Dla każdego requestu:
     *
     * 1. Próbuje odczytać X-Correlation-Id z nagłówka.
     * 2. Jeśli nagłówek nie istnieje, generuje nowy UUID.
     * 3. Próbuje odczytać X-Request-Id z nagłówka.
     * 4. Jeśli nagłówek nie istnieje, generuje nowy UUID.
     * 5. Wpisuje oba identyfikatory do MDC.
     * 6. Ustawia oba identyfikatory w response headers.
     * 7. Przekazuje request dalej.
     * 8. Czyści MDC po zakończeniu requestu.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        /*
         * Correlation ID może przyjść od klienta albo z upstream service.
         *
         * Jeśli request wchodzi przez API Gateway, gateway może wygenerować correlationId.
         * Potem ten sam correlationId powinien być przekazywany do kolejnych mikroserwisów.
         *
         * Jeśli nagłówek nie istnieje albo jest pusty, generujemy nowy UUID.
         */
        String correlationId = Optional.ofNullable(request.getHeader(CorrelationId.CORRELATION_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());

        /*
         * Request ID identyfikuje konkretny request HTTP.
         *
         * W prostym wariancie propagujemy go dalej tak samo jak correlationId.
         * W bardziej restrykcyjnej architekturze można rozważyć generowanie nowego requestId
         * na każdym hopie i trzymanie correlationId jako wspólnego identyfikatora całego flow.
         */
        String requestId = Optional.ofNullable(request.getHeader(CorrelationId.REQUEST_ID_HEADER))
                .filter(value -> !value.isBlank())
                .orElse(UUID.randomUUID().toString());

        /*
         * MDC, czyli Mapped Diagnostic Context, dodaje dane do kontekstu logowania
         * aktualnego wątku.
         *
         * Jeśli logback/logstash encoder jest skonfigurowany pod JSON logs,
         * pola correlationId i requestId będą automatycznie widoczne w logach.
         */
        MDC.put(CorrelationId.MDC_CORRELATION_ID, correlationId);
        MDC.put(CorrelationId.MDC_REQUEST_ID, requestId);

        /*
         * Zwracamy identyfikatory w odpowiedzi.
         *
         * Dzięki temu klient, test k6 albo frontend może łatwo skopiować correlationId
         * i użyć go do szukania logów oraz trace'ów.
         */
        response.setHeader(CorrelationId.CORRELATION_ID_HEADER, correlationId);
        response.setHeader(CorrelationId.REQUEST_ID_HEADER, requestId);

        try {
            /*
             * Przekazujemy request dalej do kolejnych filtrów i kontrolera.
             */
            filterChain.doFilter(request, response);
        } finally {
            /*
             * Czyścimy MDC po zakończeniu requestu.
             *
             * To jest krytyczne, bo kontenery servletowe używają puli wątków.
             * Bez czyszczenia correlationId z jednego requestu mógłby "przeciec"
             * do logów kolejnego requestu obsługiwanego przez ten sam wątek.
             */
            MDC.remove(CorrelationId.MDC_CORRELATION_ID);
            MDC.remove(CorrelationId.MDC_REQUEST_ID);
        }
    }
}