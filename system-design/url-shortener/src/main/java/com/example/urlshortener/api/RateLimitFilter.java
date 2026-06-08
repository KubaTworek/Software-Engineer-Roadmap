package com.example.urlshortener.api;

import com.example.urlshortener.config.RateLimitProperties;
import com.example.urlshortener.service.RateLimitService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtr HTTP odpowiedzialny za nakładanie limitów liczby requestów.
 *
 * <p>
 * Klasa rozszerza {@link OncePerRequestFilter}, co oznacza, że Spring uruchomi
 * ten filtr maksymalnie raz dla jednego requestu HTTP w ramach jednego przebiegu
 * przez łańcuch filtrów.
 * </p>
 *
 * <p>
 * Celem tego filtra jest ochrona najważniejszych endpointów systemu przed
 * nadmiernym ruchem:
 * </p>
 *
 * <ul>
 *     <li>tworzenie krótkich linków, czyli {@code POST /api/v1/urls},</li>
 *     <li>redirecty, czyli {@code GET /{shortCode}}.</li>
 * </ul>
 *
 * <p>
 * Filtr sam nie implementuje algorytmu rate limitingu. Deleguje to do
 * {@link RateLimitService}. Dzięki temu filtr odpowiada tylko za warstwę HTTP:
 * rozpoznanie requestu, ustalenie klienta i dobranie właściwego limitu.
 * </p>
 *
 * <p>
 * Limity są pobierane z {@link RateLimitProperties}, czyli z konfiguracji aplikacji,
 * np. z {@code application.yml}.
 * </p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Serwis wykonujący właściwe sprawdzenie limitu.
     *
     * <p>
     * Typowo taki serwis używa Redisa i algorytmu fixed window, sliding window
     * albo token bucket. W tej klasie wywoływana jest metoda:
     * </p>
     *
     * <pre>
     * checkFixedWindow(key, requests, window)
     * </pre>
     *
     * <p>
     * Jeśli limit został przekroczony, serwis powinien rzucić wyjątek, np.
     * {@code RateLimitExceededException}. Taki wyjątek powinien zostać obsłużony
     * przez globalny handler i zamieniony na odpowiedź HTTP {@code 429 Too Many Requests}.
     * </p>
     */
    private final RateLimitService rateLimitService;

    /**
     * Konfiguracja limitów requestów.
     *
     * <p>
     * Zawiera osobne limity dla różnych typów operacji, np.:
     * </p>
     *
     * <ul>
     *     <li>{@code create} — limit tworzenia linków,</li>
     *     <li>{@code redirect} — limit wejść w skrócone linki.</li>
     * </ul>
     *
     * <p>
     * Dzięki temu można mieć ostrzejszy limit dla tworzenia URL-i i łagodniejszy
     * dla redirectów.
     * </p>
     */
    private final RateLimitProperties properties;

    /**
     * Konstruktor filtra.
     *
     * <p>
     * Spring wstrzykuje zależności przez konstruktor. To preferowane podejście,
     * bo zależności są jawne i łatwiejsze do testowania.
     * </p>
     *
     * @param rateLimitService serwis sprawdzający limity
     * @param properties konfiguracja limitów
     */
    public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    /**
     * Główna metoda filtra wykonywana dla każdego requestu HTTP.
     *
     * <p>
     * Metoda:
     * </p>
     *
     * <ol>
     *     <li>odczytuje ścieżkę requestu,</li>
     *     <li>odczytuje metodę HTTP,</li>
     *     <li>ustala identyfikator klienta,</li>
     *     <li>sprawdza, czy request podlega rate limitingowi,</li>
     *     <li>wywołuje {@link RateLimitService}, jeśli request jest limitowany,</li>
     *     <li>przekazuje request dalej w łańcuchu filtrów.</li>
     * </ol>
     *
     * <p>
     * Jeśli {@code rateLimitService.checkFixedWindow(...)} rzuci wyjątek,
     * wykonanie nie dojdzie do {@code filterChain.doFilter(...)} i request zostanie
     * przerwany. Wtedy odpowiedź HTTP powinna zostać przygotowana przez mechanizm
     * obsługi wyjątków.
     * </p>
     *
     * @param request request HTTP
     * @param response response HTTP
     * @param filterChain dalszy łańcuch filtrów i finalnie kontroler
     * @throws ServletException jeśli wystąpi błąd servletowy
     * @throws IOException jeśli wystąpi błąd wejścia/wyjścia
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        /*
         * Pobieramy ścieżkę requestu, np.:
         *
         * /api/v1/urls
         * /aB92xK7
         * /api/v1/dashboard/summary
         *
         * getRequestURI() nie zawiera query stringa, więc np.
         * /aB92xK7?utm_source=x nadal będzie miało path /aB92xK7.
         */
        String path = request.getRequestURI();

        /*
         * Pobieramy metodę HTTP i normalizujemy ją do uppercase.
         *
         * Przykład:
         * post -> POST
         * get  -> GET
         *
         * W praktyce servlet container zwykle i tak zwraca uppercase, ale jawna
         * normalizacja zwiększa odporność kodu.
         */
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        /*
         * Ustalamy identyfikator klienta.
         *
         * W tej wersji jest nim adres IP pobrany z X-Forwarded-For albo remoteAddr.
         * Na podstawie tego identyfikatora budowany jest klucz rate limitingu.
         */
        String clientId = clientId(request);

        /*
         * Limit dla tworzenia URL-i.
         *
         * Ten endpoint jest bardziej wrażliwy na abuse niż sam redirect, bo atakujący
         * mógłby masowo generować skrócone linki, np. do phishingu albo spamu.
         *
         * Warunek dopasowuje dokładnie:
         *
         * POST /api/v1/urls
         */
        if ("POST".equals(method) && "/api/v1/urls".equals(path)) {
            /*
             * Pobieramy konfigurację limitu dla operacji create.
             *
             * Przykład:
             * 10 requestów / 1 minuta / IP
             */
            RateLimitProperties.Limit limit = properties.getCreate();

            /*
             * Sprawdzamy limit fixed window.
             *
             * Klucz ma postać:
             *
             * rl:create:{clientId}
             *
             * Dzięki temu każdy klient ma osobny licznik tworzenia URL-i.
             */
            rateLimitService.checkFixedWindow(
                    "rl:create:" + clientId,
                    limit.getRequests(),
                    limit.getWindow()
            );

            /*
             * Limit dla redirectów.
             *
             * Redirecty mają zwykle dużo wyższy dopuszczalny limit niż tworzenie linków,
             * ponieważ normalny ruch może być bardzo duży, szczególnie dla popularnych
             * lub viralowych linków.
             *
             * Rate limiting redirectów chroni głównie przed:
             * - prostym DDoS z jednego IP,
             * - brute force short code,
             * - nadmiernym ruchem botów.
             */
        } else if ("GET".equals(method) && isRedirectPath(path)) {
            /*
             * Pobieramy konfigurację limitu dla redirectów.
             */
            RateLimitProperties.Limit limit = properties.getRedirect();

            /*
             * Sprawdzamy limit fixed window.
             *
             * Klucz ma postać:
             *
             * rl:redirect:{clientId}
             *
             * Obecnie limit jest per klient/IP, nie per shortCode.
             */
            rateLimitService.checkFixedWindow(
                    "rl:redirect:" + clientId,
                    limit.getRequests(),
                    limit.getWindow()
            );
        }

        /*
         * Jeśli request nie podlega rate limitingowi albo limit nie został
         * przekroczony, przekazujemy go dalej do kolejnych filtrów i kontrolera.
         */
        filterChain.doFilter(request, response);
    }

    /**
     * Sprawdza, czy ścieżka wygląda jak publiczny redirect do short code.
     *
     * <p>
     * Metoda używa wyrażenia regularnego, aby nie nakładać limitu redirectów
     * na wszystkie endpointy GET, np.:
     * </p>
     *
     * <ul>
     *     <li>{@code /api/v1/dashboard/summary},</li>
     *     <li>{@code /actuator/health},</li>
     *     <li>{@code /api/v1/urls/aB92xK7}.</li>
     * </ul>
     *
     * <p>
     * Dopasowywane są tylko ścieżki jednoelementowe, np.:
     * </p>
     *
     * <ul>
     *     <li>{@code /abc},</li>
     *     <li>{@code /aB92xK7},</li>
     *     <li>{@code /promo-2026},</li>
     *     <li>{@code /my_alias}.</li>
     * </ul>
     *
     * <p>
     * Wyrażenie:
     * </p>
     *
     * <pre>
     * /[A-Za-z0-9_-]{3,32}|/[A-Za-z0-9]{1,32}
     * </pre>
     *
     * oznacza w praktyce:
     * </p>
     *
     * <ul>
     *     <li>aliasy z literami, cyframi, podkreślnikiem i myślnikiem o długości 3–32,</li>
     *     <li>albo krótkie automatyczne kody Base62 o długości 1–32.</li>
     * </ul>
     *
     * @param path ścieżka requestu HTTP
     * @return {@code true}, jeśli ścieżka wygląda jak redirect path
     */
    private boolean isRedirectPath(String path) {
        return path.matches("/[A-Za-z0-9_-]{3,32}|/[A-Za-z0-9]{1,32}");
    }

    /**
     * Ustala identyfikator klienta na potrzeby rate limitingu.
     *
     * <p>
     * W tej implementacji identyfikatorem klienta jest adres IP.
     * Metoda sprawdza kolejno:
     * </p>
     *
     * <ol>
     *     <li>{@code X-Forwarded-For},</li>
     *     <li>{@code request.getRemoteAddr()}.</li>
     * </ol>
     *
     * <p>
     * Jeśli aplikacja działa za reverse proxy lub CDN, {@code X-Forwarded-For}
     * zwykle zawiera oryginalny adres IP klienta. Jeśli aplikacja działa bez proxy,
     * {@code getRemoteAddr()} powinno zwrócić adres klienta.
     * </p>
     *
     * @param request request HTTP
     * @return identyfikator klienta używany w kluczu rate limitingu
     */
    private String clientId(HttpServletRequest request) {

        /*
         * X-Forwarded-For może zawierać wiele adresów IP oddzielonych przecinkami.
         *
         * Przykład:
         *
         * X-Forwarded-For: 203.0.113.10, 10.0.0.5, 10.0.0.6
         *
         * Pierwszy adres jest zwykle adresem oryginalnego klienta, a kolejne
         * oznaczają proxy na trasie requestu.
         */
        String forwardedFor = request.getHeader("X-Forwarded-For");

        /*
         * Jeśli X-Forwarded-For istnieje, bierzemy pierwszy adres.
         */
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        /*
         * Fallback do remoteAddr.
         */
        return request.getRemoteAddr();
    }
}