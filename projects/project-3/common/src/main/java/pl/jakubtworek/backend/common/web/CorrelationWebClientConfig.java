package pl.jakubtworek.backend.common.web;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

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
     * Filtr kontraktu dla każdego WebClient.Builder udostępnianego przez moduł common.
     *
     * Spring Boot 4 nie udostępnia dawnego WebClientCustomizer. Filtr pozostaje osobnym
     * beanem, a OutboundHttpClientConfig składa go z technicznym connectorem w jeden
     * kanoniczny builder. Dzięki temu nie trzeba ręcznie dodawać nagłówków w klientach.
     */
    @Bean
    ExchangeFilterFunction correlationPropagationFilter() {
        return (request, next) -> {
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
            String deadline = DeadlineContext.currentHeader();

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
            if (deadline != null) {
                mutated.header(DeadlineContext.HEADER, deadline);
            }

            /*
             * Przekazujemy zmodyfikowany request do kolejnego elementu łańcucha WebClienta.
             *
             * next.exchange(...) faktycznie wykonuje request HTTP albo przekazuje go dalej
             * do kolejnych filtrów WebClienta.
             */
            return next.exchange(mutated.build());
        };
    }
}
