package com.example.urlshortener.analytics;

import com.example.urlshortener.queue.ClickEventPublisher;
import com.example.urlshortener.queue.ClickMessage;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za zbieranie danych o kliknięciu i wysłanie ich
 * do asynchronicznego pipeline'u analytics.
 *
 * <p>
 * Ta klasa jest zwykle wywoływana w momencie obsługi redirectu, np. w endpointcie:
 * </p>
 *
 * <pre>
 * GET /{shortCode}
 * </pre>
 *
 * <p>
 * Jej zadaniem nie jest zapisywanie kliknięcia bezpośrednio do bazy danych.
 * Zamiast tego serwis buduje wiadomość {@link ClickMessage} i publikuje ją
 * przez {@link ClickEventPublisher}, np. do RabbitMQ.
 * </p>
 *
 * <p>
 * Dzięki temu redirect może pozostać szybki. Użytkownik dostaje odpowiedź
 * przekierowania, a cięższe operacje analytics — zapis do bazy, agregaty,
 * abuse detection — wykonywane są później przez consumer kolejki.
 * </p>
 *
 * <p>
 * Główne dane zbierane przez tę klasę:
 * </p>
 *
 * <ul>
 *     <li>unikalne {@code eventId},</li>
 *     <li>{@code shortCode},</li>
 *     <li>czas kliknięcia,</li>
 *     <li>adres IP klienta,</li>
 *     <li>User-Agent,</li>
 *     <li>Referer,</li>
 *     <li>kraj z nagłówka CDN, np. Cloudflare,</li>
 *     <li>request id do korelacji logów.</li>
 * </ul>
 *
 * <p>
 * Ważne: ten serwis powinien być lekki i nie powinien wykonywać kosztownych
 * operacji, takich jak zapytania do bazy danych albo zewnętrzne requesty HTTP.
 * Jest częścią ścieżki redirectu, więc jego opóźnienie wpływa na użytkownika.
 * </p>
 */
@Service
public class ClickTrackingService {

    /**
     * Komponent publikujący wiadomości kliknięć do kolejki.
     *
     * <p>
     * W praktyce może to być implementacja oparta o RabbitMQ, Kafka, Pub/Sub
     * albo inną kolejkę. Ten serwis zna tylko abstrakcję publikowania eventu,
     * nie musi wiedzieć, jak dokładnie działa transport.
     * </p>
     */
    private final ClickEventPublisher publisher;

    /**
     * Zegar używany do pobrania czasu kliknięcia.
     *
     * <p>
     * Zamiast używać bezpośrednio {@code Instant.now()}, klasa korzysta z
     * {@link Clock}. To ułatwia testowanie, ponieważ w testach można wstrzyknąć
     * stały zegar i uzyskać deterministyczny czas.
     * </p>
     */
    private final Clock clock;

    /**
     * Konstruktor serwisu.
     *
     * <p>
     * Spring wstrzykuje zależności przez konstruktor. Jest to preferowany sposób,
     * bo zależności są jawne i łatwe do zamockowania w testach.
     * </p>
     *
     * @param publisher publisher eventów kliknięć
     * @param clock zegar systemowy albo testowy
     */
    public ClickTrackingService(ClickEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Buduje i publikuje event kliknięcia dla danego short code.
     *
     * <p>
     * Metoda zbiera dane z requestu HTTP, tworzy {@link ClickMessage}
     * i przekazuje go do publishera.
     * </p>
     *
     * <p>
     * Typowy przepływ:
     * </p>
     *
     * <ol>
     *     <li>Endpoint redirectu obsługuje request {@code GET /{shortCode}}.</li>
     *     <li>System ustala docelowy {@code longUrl}.</li>
     *     <li>Przed zwróceniem lub tuż przed zwróceniem redirectu wywołuje {@code track()}.</li>
     *     <li>{@code track()} publikuje event do kolejki.</li>
     *     <li>Consumer kolejki zapisuje event i aktualizuje analytics.</li>
     * </ol>
     *
     * <p>
     * W tej implementacji metoda nie łapie wyjątków z publishera. Oznacza to,
     * że jeśli publikacja do kolejki rzuci wyjątek, może on wpłynąć na redirect,
     * zależnie od tego, jak wywołujący obsługuje błąd.
     * W systemie produkcyjnym często warto rozważyć try-catch albo fire-and-forget,
     * żeby awaria analytics nie psuła przekierowania.
     * </p>
     *
     * @param shortCode kod skróconego linku, w który kliknięto
     * @param request request HTTP użytkownika
     */
    public void track(String shortCode, HttpServletRequest request) {

        /*
         * Tworzymy i publikujemy ClickMessage.
         *
         * Poszczególne pola:
         *
         * 1. eventId:
         *    Losowy UUID. Służy do deduplikacji eventów po stronie analytics.
         *
         * 2. shortCode:
         *    Kod skróconego linku, którego dotyczy kliknięcie.
         *
         * 3. clickedAt:
         *    Czas kliknięcia w UTC/Instant, pobrany z wstrzykniętego Clock.
         *
         * 4. clientIp:
         *    IP klienta ustalone z nagłówków proxy albo request.getRemoteAddr().
         *
         * 5. User-Agent:
         *    Nagłówek HTTP opisujący przeglądarkę, bota lub klienta HTTP.
         *
         * 6. Referer:
         *    Strona źródłowa, z której użytkownik kliknął link.
         *
         * 7. CF-IPCountry:
         *    Nagłówek ustawiany przez Cloudflare z kodem kraju klienta.
         *    Jeśli aplikacja nie działa za Cloudflare, może być nullem.
         *
         * 8. requestId:
         *    Identyfikator requestu używany do korelacji logów i tracingu.
         */
        publisher.publish(new ClickMessage(
                UUID.randomUUID().toString(),
                shortCode,
                Instant.now(clock),
                clientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getHeader(HttpHeaders.REFERER),
                request.getHeader("CF-IPCountry"),
                requestId(request)
        ));
    }

    /**
     * Ustala adres IP klienta na podstawie requestu HTTP.
     *
     * <p>
     * W aplikacjach uruchomionych za reverse proxy, load balancerem albo CDN,
     * {@code request.getRemoteAddr()} często nie zawiera IP użytkownika końcowego,
     * tylko IP najbliższego proxy.
     * </p>
     *
     * <p>
     * Dlatego metoda sprawdza kolejno:
     * </p>
     *
     * <ol>
     *     <li>{@code X-Forwarded-For},</li>
     *     <li>{@code X-Real-IP},</li>
     *     <li>{@code request.getRemoteAddr()} jako fallback.</li>
     * </ol>
     *
     * @param request request HTTP
     * @return ustalony adres IP klienta
     */
    private String clientIp(HttpServletRequest request) {

        /*
         * X-Forwarded-For może zawierać listę adresów IP oddzielonych przecinkami.
         *
         * Przykład:
         *
         * X-Forwarded-For: 203.0.113.10, 10.0.0.5, 10.0.0.6
         *
         * Pierwszy adres zwykle oznacza oryginalnego klienta, a kolejne adresy
         * oznaczają proxy po drodze.
         */
        String forwardedFor = request.getHeader("X-Forwarded-For");

        /*
         * Jeśli X-Forwarded-For istnieje, bierzemy pierwszy adres z listy.
         */
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        /*
         * X-Real-IP to popularny nagłówek ustawiany przez reverse proxy,
         * np. Nginx. Zwykle zawiera pojedynczy adres IP klienta.
         */
        String realIp = request.getHeader("X-Real-IP");

        /*
         * Jeśli X-Real-IP istnieje, używamy go jako adresu klienta.
         */
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        /*
         * Fallback.
         *
         * Jeśli żaden nagłówek proxy nie jest dostępny, używamy remoteAddr
         * z requestu. W środowisku bez reverse proxy będzie to adres klienta.
         * W środowisku z proxy może to być adres proxy.
         */
        return request.getRemoteAddr();
    }

    /**
     * Pobiera request id z nagłówka HTTP.
     *
     * <p>
     * Request id jest używany do korelacji logów, tracingu i debugowania.
     * Jeśli wiele usług obsługuje jeden request, wspólny request id pozwala
     * łatwiej znaleźć powiązane logi w różnych komponentach.
     * </p>
     *
     * <p>
     * Nagłówek:
     * </p>
     *
     * <pre>
     * X-Request-Id
     * </pre>
     *
     * <p>
     * Jeśli nagłówek nie istnieje albo jest pusty, metoda zwraca {@code null}.
     * </p>
     *
     * @param request request HTTP
     * @return request id albo {@code null}
     */
    private String requestId(HttpServletRequest request) {

        /*
         * Pobieramy X-Request-Id z requestu.
         */
        String requestId = request.getHeader("X-Request-Id");

        /*
         * Pustą wartość traktujemy tak samo jak brak nagłówka.
         */
        return requestId == null || requestId.isBlank()
                ? null
                : requestId;
    }
}