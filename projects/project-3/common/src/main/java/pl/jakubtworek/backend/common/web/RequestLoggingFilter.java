package pl.jakubtworek.backend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Filtr logujący zakończenie każdego requestu HTTP.
 *
 * Jego główne zadania:
 *
 * - zmierzyć czas obsługi requestu,
 * - zapisać podstawowe dane HTTP do MDC,
 * - zalogować status odpowiedzi,
 * - umożliwić późniejszą analizę requestów w logach strukturalnych.
 *
 * MDC, czyli Mapped Diagnostic Context, pozwala dodać pola techniczne do logów.
 * Jeśli logback/logstash encoder jest skonfigurowany do logowania MDC jako JSON,
 * pola takie jak httpMethod, httpPath, httpStatus i durationMs będą widoczne
 * jako osobne atrybuty w logach.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /**
     * Pomija logowanie endpointów technicznych.
     *
     * /actuator i /health są często odpytywane przez:
     *
     * - Prometheus,
     * - load balancer,
     * - ECS/Kubernetes health checks,
     * - lokalne narzędzia diagnostyczne.
     *
     * Gdybyśmy logowali każdy taki request, logi byłyby zaszumione i mniej użyteczne.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/actuator") || path.equals("/health");
    }

    /**
     * Wykonuje pomiar czasu i loguje request po zakończeniu całego łańcucha filtrów.
     *
     * Ważne:
     * filterChain.doFilter(...) przekazuje request dalej do następnych filtrów,
     * kontrolera i ewentualnie handlerów wyjątków.
     *
     * Logowanie znajduje się w finally, żeby request został zalogowany również wtedy,
     * gdy po drodze wystąpi wyjątek.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        /*
         * Używamy System.nanoTime(), a nie currentTimeMillis().
         *
         * nanoTime jest przeznaczony do mierzenia upływu czasu, bo nie zależy od zmian
         * zegara systemowego, synchronizacji NTP ani zmiany czasu.
         */
        long started = System.nanoTime();

        /*
         * Dodajemy podstawowe informacje HTTP do MDC przed obsługą requestu.
         *
         * Dzięki temu logi wygenerowane głębiej w aplikacji mogą odziedziczyć te pola,
         * o ile są zapisane w tym samym wątku.
         */
        MDC.put("httpMethod", request.getMethod());
        MDC.put("httpPath", request.getRequestURI());

        try {
            /*
             * Przekazujemy request dalej.
             *
             * Dopiero po powrocie z tej metody znamy finalny status HTTP odpowiedzi.
             */
            filterChain.doFilter(request, response);
        } finally {
            /*
             * Liczymy całkowity czas obsługi requestu od wejścia do filtra
             * do zakończenia obsługi przez aplikację.
             */
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            /*
             * Status odpowiedzi jest dostępny dopiero po wykonaniu downstream chain.
             */
            MDC.put("httpStatus", String.valueOf(response.getStatus()));
            MDC.put("durationMs", String.valueOf(durationMs));

            /*
             * Log requestu.
             *
             * W logach tekstowych wiadomość jest czytelna dla człowieka.
             * W logach JSON właściwe pola powinny pochodzić z MDC.
             */
            log.info("http_request_completed method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);

            /*
             * Czyścimy tylko pola dodane przez ten filtr.
             *
             * To jest ważne, bo w aplikacjach servletowych wątki są używane ponownie.
             * Jeśli nie wyczyścimy MDC, dane z jednego requestu mogłyby "przeciec"
             * do logów kolejnego requestu obsługiwanego przez ten sam wątek.
             */
            MDC.remove("httpMethod");
            MDC.remove("httpPath");
            MDC.remove("httpStatus");
            MDC.remove("durationMs");
        }
    }
}