package pl.jakubtworek.backend.gateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import pl.jakubtworek.backend.common.web.CorrelationId;
import reactor.core.publisher.Mono;

@RestController
public class GatewayProxyController {
    private final WebClient webClient;
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

    @GetMapping("/events/**")
    Mono<ResponseEntity<String>> catalogGet(HttpServletRequest request) {
        return proxyGet(catalogUrl, request.getRequestURI(), request);
    }

    @PostMapping("/reservations")
    Mono<ResponseEntity<String>> reservationPost(@RequestBody String body, HttpServletRequest request) {
        return proxyPost(reservationUrl + "/reservations", body, request);
    }

    @GetMapping("/reservations/{id}")
    Mono<ResponseEntity<String>> reservationGet(@PathVariable String id, HttpServletRequest request) {
        return proxyGet(reservationUrl, "/reservations/" + id, request);
    }

    @PostMapping("/orders")
    Mono<ResponseEntity<String>> orderPost(@RequestBody String body,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                           HttpServletRequest request) {
        return webClient.post()
                .uri(orderUrl + "/orders")
                .headers(headers -> copyForwardHeaders(headers, request))
                .headers(headers -> {
                    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        headers.set("Idempotency-Key", idempotencyKey);
                    }
                })
                .bodyValue(body)
                .retrieve()
                .toEntity(String.class);
    }

    @GetMapping("/orders/{id}")
    Mono<ResponseEntity<String>> orderGet(@PathVariable String id, HttpServletRequest request) {
        return proxyGet(orderUrl, "/orders/" + id, request);
    }

    private Mono<ResponseEntity<String>> proxyGet(String baseUrl, String path, HttpServletRequest request) {
        String queryString = request.getQueryString();
        String target = baseUrl + path + (queryString == null ? "" : "?" + queryString);
        return webClient.get()
                .uri(target)
                .headers(headers -> copyForwardHeaders(headers, request))
                .retrieve()
                .toEntity(String.class);
    }

    private Mono<ResponseEntity<String>> proxyPost(String url, String body, HttpServletRequest request) {
        return webClient.post()
                .uri(url)
                .headers(headers -> copyForwardHeaders(headers, request))
                .bodyValue(body)
                .retrieve()
                .toEntity(String.class);
    }

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
