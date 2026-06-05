package pl.jakubtworek.backend.gateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import pl.jakubtworek.backend.common.web.CorrelationId;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Prosty kontroler pełniący rolę API Gateway / reverse proxy.
 *
 * Jego zadaniem nie jest implementowanie logiki biznesowej, tylko przekazanie requestu
 * do odpowiedniego mikroserwisu:
 *
 * - /events/**        -> catalog-service
 * - /reservations/**  -> reservation-service
 * - /orders/**        -> order-service
 *
 * Ten gateway jest celowo prosty. W produkcyjnym systemie podobną rolę często pełniłby
 * Spring Cloud Gateway, Kong, NGINX, Envoy albo ALB/API Gateway w cloudzie.
 */
@RestController
public class GatewayProxyController {

    /**
     * Nagłówki hop-by-hop nie powinny być forwardowane przez proxy.
     *
     * Są to nagłówki dotyczące pojedynczego połączenia HTTP, a nie właściwej odpowiedzi
     * biznesowej. Jeśli proxy przekaże je dalej, kontener HTTP może dodać własne wartości
     * i powstają błędy trudne do diagnozy.
     *
     * Przykład z tego projektu:
     *
     * Downstream zwraca:
     * Transfer-Encoding: chunked
     *
     * Tomcat/Netty dodaje drugi raz:
     * Transfer-Encoding: chunked
     *
     * Wtedy k6/Go odrzuca odpowiedź błędem:
     * "too many transfer encodings: [\"chunked\" \"chunked\"]".
     */
    private static final Set<String> HOP_BY_HOP_RESPONSE_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "content-length"
    );

    private final WebClient webClient;

    /**
     * Bazowe adresy downstream services.
     *
     * W Docker Compose będą to adresy po nazwach serwisów, np.:
     *
     * - http://catalog-service:8081
     * - http://reservation-service:8082
     * - http://order-service:8083
     *
     * Lokalnie bez Dockera mogłyby to być np.:
     *
     * - http://localhost:8081
     * - http://localhost:8082
     * - http://localhost:8083
     */
    private final String catalogUrl;
    private final String reservationUrl;
    private final String orderUrl;

    public GatewayProxyController(WebClient.Builder builder,
                                  @Value("${services.catalog.url}") String catalogUrl,
                                  @Value("${services.reservation.url}") String reservationUrl,
                                  @Value("${services.order.url}") String orderUrl) {
        this.webClient = builder.build();
        this.catalogUrl = catalogUrl;
        this.reservationUrl = reservationUrl;
        this.orderUrl = orderUrl;
    }

    /**
     * Przekazuje wszystkie requesty GET /events/** do catalog-service.
     *
     * request.getRequestURI() zachowuje pełną ścieżkę, np.:
     *
     * /events
     * /events/{id}
     * /events/{id}/availability
     */
    @GetMapping("/events/**")
    Mono<ResponseEntity<String>> catalogGet(HttpServletRequest request) {
        return proxyGet(catalogUrl, request.getRequestURI(), request);
    }

    /**
     * Przekazuje utworzenie rezerwacji do reservation-service.
     *
     * Body jest przyjmowane jako String, ponieważ gateway nie powinien znać modelu domenowego
     * rezerwacji. Ma tylko przekazać JSON dalej.
     */
    @PostMapping("/reservations")
    Mono<ResponseEntity<String>> reservationPost(@RequestBody String body, HttpServletRequest request) {
        return proxyPost(reservationUrl + "/reservations", body, request);
    }

    /**
     * Przekazuje pobranie pojedynczej rezerwacji do reservation-service.
     */
    @GetMapping("/reservations/{id}")
    Mono<ResponseEntity<String>> reservationGet(@PathVariable String id, HttpServletRequest request) {
        return proxyGet(reservationUrl, "/reservations/" + id, request);
    }

    /**
     * Przekazuje utworzenie zamówienia do order-service.
     *
     * Idempotency-Key jest tutaj ważny, bo POST /orders może być wykonany ponownie przez klienta,
     * retry albo timeout po stronie sieci. Dzięki temu order-service może rozpoznać duplikat
     * i nie utworzyć drugiego zamówienia dla tej samej operacji.
     */
    @PostMapping("/orders")
    Mono<ResponseEntity<String>> orderPost(@RequestBody String body,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                           HttpServletRequest request) {
        return webClient.post()
                .uri(orderUrl + "/orders")

                // Przekazujemy tylko techniczne nagłówki korelacyjne.
                // Nie kopiujemy wszystkich nagłówków klienta, bo gateway mógłby przypadkowo
                // przekazać nagłówki hop-by-hop, cookies albo inne dane, których downstream nie potrzebuje.
                .headers(headers -> copyForwardHeaders(headers, request))

                // Idempotency-Key jest świadomie przepuszczany dalej, bo jest częścią kontraktu POST /orders.
                .headers(headers -> {
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                })

                // Zachowujemy Content-Type z requestu klienta.
                // Jeśli klient go nie podał, zakładamy JSON, bo endpointy POST w tym projekcie operują na JSON-ie.
                .contentType(resolveContentType(request))

                // Gateway nie parsuje JSON-a. Przekazuje body downstreamowi w niezmienionej formie.
                .bodyValue(body)

                // Zamieniamy ClientResponse na ResponseEntity i czyścimy problematyczne nagłówki.
                .exchangeToMono(this::toSanitizedEntity);
    }

    /**
     * Przekazuje pobranie pojedynczego zamówienia do order-service.
     */
    @GetMapping("/orders/{id}")
    Mono<ResponseEntity<String>> orderGet(@PathVariable String id, HttpServletRequest request) {
        return proxyGet(orderUrl, "/orders/" + id, request);
    }

    /**
     * Uniwersalna metoda do proxy GET.
     *
     * Zachowuje query string, więc request:
     *
     * /events?page=1&size=20
     *
     * zostanie przekazany do:
     *
     * {catalogUrl}/events?page=1&size=20
     */
    private Mono<ResponseEntity<String>> proxyGet(String baseUrl, String path, HttpServletRequest request) {
        String queryString = request.getQueryString();
        String target = baseUrl + path + (queryString == null ? "" : "?" + queryString);

        return webClient.get()
                .uri(target)
                .headers(headers -> copyForwardHeaders(headers, request))
                .exchangeToMono(this::toSanitizedEntity);
    }

    /**
     * Uniwersalna metoda do proxy POST.
     *
     * Stosowana tam, gdzie nie trzeba specjalnie obsługiwać dodatkowych nagłówków,
     * np. Idempotency-Key dla zamówień.
     */
    private Mono<ResponseEntity<String>> proxyPost(String url, String body, HttpServletRequest request) {
        return webClient.post()
                .uri(url)
                .headers(headers -> copyForwardHeaders(headers, request))
                .contentType(resolveContentType(request))
                .bodyValue(body)
                .exchangeToMono(this::toSanitizedEntity);
    }

    /**
     * Konwertuje odpowiedź WebClienta na ResponseEntity zwracane klientowi gatewaya.
     *
     * Ważne:
     * - zachowujemy status HTTP z downstream service,
     * - zachowujemy bezpieczne nagłówki odpowiedzi,
     * - usuwamy hop-by-hop headers,
     * - body przekazujemy jako String.
     */
    private Mono<ResponseEntity<String>> toSanitizedEntity(ClientResponse response) {
        HttpStatusCode status = response.statusCode();
        HttpHeaders safeHeaders = sanitizedResponseHeaders(response.headers().asHttpHeaders());

        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> ResponseEntity.status(status).headers(safeHeaders).body(body));
    }

    /**
     * Kopiuje tylko te nagłówki odpowiedzi, które są bezpieczne do przekazania klientowi.
     *
     * Nie przekazujemy np. Transfer-Encoding ani Content-Length, bo finalną odpowiedź buduje
     * kontener HTTP gatewaya. To on powinien zdecydować, czy odpowiedź będzie chunked,
     * jaki będzie Content-Length itd.
     */
    private HttpHeaders sanitizedResponseHeaders(HttpHeaders source) {
        HttpHeaders target = new HttpHeaders();

        source.forEach((name, values) -> {
            if (!HOP_BY_HOP_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                target.put(name, values);
            }
        });

        return target;
    }

    /**
     * Ustala Content-Type dla requestów POST.
     *
     * Jeśli klient podał Content-Type, gateway przekazuje go dalej.
     * Jeśli nie podał, przyjmujemy application/json, bo w tym projekcie endpointy POST
     * przyjmują JSON.
     *
     * To jest istotne, bo bez poprawnego Content-Type downstream service może nie sparsować body
     * jako JSON i zwrócić 400/415/500.
     */
    private MediaType resolveContentType(HttpServletRequest request) {
        String contentType = request.getContentType();

        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_JSON;
        }

        return MediaType.parseMediaType(contentType);
    }

    /**
     * Przekazuje nagłówki korelacyjne do downstream services.
     *
     * Dzięki temu jeden request można prześledzić przez:
     *
     * - api-gateway,
     * - catalog-service,
     * - reservation-service,
     * - order-service,
     * - payment-mock-service,
     * - notification-service.
     *
     * Te wartości są potem widoczne w logach i pomagają diagnozować awarie bez zgadywania.
     */
    private void copyForwardHeaders(HttpHeaders headers, HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationId.CORRELATION_ID_HEADER);
        String requestId = request.getHeader(CorrelationId.REQUEST_ID_HEADER);

        if (correlationId != null && !correlationId.isBlank()) {
            headers.set(CorrelationId.CORRELATION_ID_HEADER, correlationId);
        }

        if (requestId != null && !requestId.isBlank()) {
            headers.set(CorrelationId.REQUEST_ID_HEADER, requestId);
        }
    }
}