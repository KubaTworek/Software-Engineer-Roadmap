package pl.jakubtworek.marketplace.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtr HTTP odpowiedzialny za ustawienie correlationId dla każdego requestu.
 *
 * Correlation ID pozwala śledzić jeden przepływ przez cały system:
 * - request HTTP,
 * - use case,
 * - zapis eventu do outboxa,
 * - publikację do Kafki,
 * - konsumentów,
 * - retry,
 * - DLQ.
 *
 * Filtr działa raz na każde żądanie HTTP, ponieważ rozszerza OncePerRequestFilter.
 *
 * Jeśli klient przekaże nagłówek X-Correlation-Id, aplikacja użyje tej wartości.
 * Jeśli nagłówek nie istnieje albo jest pusty, aplikacja wygeneruje nowe UUID.
 *
 * Wartość correlationId trafia do MDC, dzięki czemu automatycznie pojawia się w logach,
 * jeśli logging.pattern.level zawiera %X{correlationId}.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Nazwa nagłówka HTTP używanego do przekazywania correlationId.
     *
     * Klient może wysłać:
     * X-Correlation-Id: 4f0a9c2e-6e4a-4c66-8b91-3ff3d8f3cabc
     */
    public static final String HEADER = "X-Correlation-Id";

    /**
     * Klucz MDC, pod którym zapisujemy correlationId.
     *
     * Musi być spójny z konfiguracją logowania, np.:
     * %X{correlationId}
     */
    public static final String MDC_KEY = "correlationId";

    /**
     * Obsługuje pojedyncze żądanie HTTP.
     *
     * Przepływ:
     * 1. Odczytuje correlationId z nagłówka X-Correlation-Id.
     * 2. Jeśli nagłówek jest pusty, generuje nowe UUID.
     * 3. Zapisuje correlationId do MDC.
     * 4. Ustawia X-Correlation-Id w odpowiedzi HTTP.
     * 5. Przekazuje request dalej w łańcuchu filtrów.
     * 6. Po zakończeniu requestu usuwa correlationId z MDC.
     *
     * Czyszczenie MDC w finally jest bardzo ważne, ponieważ wątki serwera HTTP są
     * używane ponownie. Bez tego kolejny request obsługiwany przez ten sam wątek
     * mógłby odziedziczyć correlationId z poprzedniego requestu.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}