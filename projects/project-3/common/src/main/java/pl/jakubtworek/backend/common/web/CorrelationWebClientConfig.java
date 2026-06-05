package pl.jakubtworek.backend.common.web;

import org.slf4j.MDC;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;

/**
 * Konfiguracja propagacji correlation ID i request ID dla wywołań wychodzących przez WebClient.
 *
 * Cel:
 *
 * Jeśli request przychodzi do jednego serwisu z nagłówkami:
 *
 * - X-Correlation-Id
 * - X-Request-Id
 *
 * to każdy downstream call wykonany przez WebClient powinien dostać te same nagłówki.
 *
 * Dzięki temu można prześledzić jeden przepływ przez wiele mikroserwisów, np.:
 *
 * api-gateway
 *   -> order-service
 *   -> reservation-service
 *   -> payment-mock-service
 *
 * Bez tej propagacji każdy serwis miałby własne, oderwane identyfikatory i diagnostyka
 * w logach byłaby dużo trudniejsza.
 */
@Configuration
public class CorrelationWebClientConfig {

    /**
     * Globalny customizer dla WebClient.Builder.
     *
     * Spring Boot aplikuje ten customizer do builderów WebClienta tworzonych w aplikacji.
     * Dzięki temu nie trzeba ręcznie dodawać nagłówków korelacyjnych w każdym kliencie HTTP.
     */
    @Bean
    WebClientCustomizer correlationWebClientCustomizer() {
        return builder -> builder.filter((request, next) -> {
            /*
             * Tworzymy kopię istniejącego requestu.
             *
             * ClientRequest jest niemutowalny, więc nie modyfikujemy go bezpośrednio.
             * Zamiast tego budujemy nowy request na bazie oryginalnego.
             */
            ClientRequest.Builder mutated = ClientRequest.from(request);

            /*
             * Pobieramy correlationId i requestId z MDC.
             *
             * Te wartości są ustawiane wcześniej przez CorrelationIdFilter,
             * który obsługuje request przychodzący do serwisu.
             *
             * Uwaga:
             * MDC bazuje na ThreadLocal. W klasycznym przepływie servletowym działa to dobrze.
             * Przy intensywnym użyciu reaktywności, schedulerów albo async execution trzeba
             * zadbać o propagację kontekstu między wątkami.
             */
            String correlationId = MDC.get(CorrelationId.MDC_CORRELATION_ID);
            String requestId = MDC.get(CorrelationId.MDC_REQUEST_ID);

            /*
             * Jeśli correlationId istnieje, dodajemy go jako nagłówek HTTP do requestu wychodzącego.
             *
             * To pozwala downstream service zapisać ten sam correlationId w swoich logach.
             */
            if (correlationId != null && !correlationId.isBlank()) {
                mutated.header(CorrelationId.CORRELATION_ID_HEADER, correlationId);
            }

            /*
             * Analogicznie propagujemy requestId.
             *
             * W tym projekcie requestId jest przekazywany dalej między serwisami.
             * Alternatywnie można projektowo uznać, że każdy serwis generuje własny requestId,
             * a tylko correlationId pozostaje wspólny dla całego flow.
             */
            if (requestId != null && !requestId.isBlank()) {
                mutated.header(CorrelationId.REQUEST_ID_HEADER, requestId);
            }

            /*
             * Przekazujemy zmodyfikowany request do kolejnego elementu łańcucha WebClienta.
             *
             * next.exchange(...) faktycznie wykonuje request HTTP albo przekazuje go dalej
             * do kolejnych filtrów WebClienta.
             */
            return next.exchange(mutated.build());
        });
    }
}