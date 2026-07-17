package com.example.ecommerce.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis odpowiedzialny za zapisywanie eventów domenowych do outboxa.
 *
 * Outbox Pattern rozwiązuje problem niezawodnego publikowania zdarzeń.
 *
 * Zamiast wysyłać event bezpośrednio do brokera, ERP, WMS, search-indexera
 * albo notification-service w środku operacji biznesowej, zapisujemy event
 * do tabeli outbox_events w tej samej bazie i tej samej transakcji.
 *
 * Dzięki temu unikamy sytuacji:
 * - zamówienie zapisane, ale event OrderCreated nie wysłany,
 * - faktura wystawiona, ale ERP o niej nie wie,
 * - produkt utworzony, ale search-indexer go nie zindeksował,
 * - płatność zakończona, ale notification-service nie dostał zdarzenia.
 *
 * OutboxEvent jest później pobierany przez worker albo osobną usługę,
 * która publikuje go dalej.
 */
@Service
public class OutboxService {

    /**
     * Repozytorium eventów outbox.
     *
     * Każdy zapisany rekord reprezentuje zdarzenie domenowe,
     * które powinno zostać później przetworzone asynchronicznie.
     */
    private final OutboxEventRepository events;

    /**
     * ObjectMapper używany do serializacji payloadu eventu do JSON.
     *
     * Payload może być Mapą, DTO albo prostym obiektem.
     * W tabeli outbox przechowujemy go jako JSON string.
     */
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytorium outboxa i ObjectMappera.
     */
    public OutboxService(
            OutboxEventRepository events,
            ObjectMapper objectMapper
    ) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    /**
     * Zapisuje event domenowy do tabeli outbox.
     *
     * Parametry:
     * - aggregateType — typ agregatu, np. "Order", "Product", "Invoice",
     * - aggregateId — ID agregatu, którego dotyczy event,
     * - eventType — typ zdarzenia, np. "OrderCreated", "InvoiceIssued",
     * - payload — dane zdarzenia zapisane jako JSON.
     *
     * Przykład:
     *
     * aggregateType = "Order"
     * aggregateId = "123"
     * eventType = "OrderCreated"
     * payload = {
     *   "orderId": 123,
     *   "orderNumber": "ORD-123",
     *   "userId": 7
     * }
     *
     * @Transactional:
     * Event powinien zapisać się razem z operacją biznesową.
     *
     * Jeśli ta metoda jest wywołana wewnątrz większej transakcji,
     * np. tworzenia zamówienia, zapis eventu zostanie zatwierdzony
     * albo wycofany razem z zamówieniem.
     */
    @Transactional
    public void saveEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object payload
    ) {
        try {
            /*
             * Serializujemy payload do JSON.
             *
             * To jest snapshot danych eventu w momencie jego powstania.
             * Dzięki temu późniejsze zmiany encji nie zmieniają treści eventu.
             */
            String payloadJson = objectMapper.writeValueAsString(payload);

            /*
             * Zapisujemy event jako rekord outbox.
             *
             * Sam zapis do outboxa jest szybki i lokalny dla bazy aplikacji.
             * Dopiero osobny publisher/worker zajmie się wysłaniem eventu dalej.
             */
            events.save(
                    new OutboxEvent(
                            aggregateType,
                            aggregateId,
                            eventType,
                            payloadJson
                    )
            );
        } catch (JsonProcessingException e) {
            /*
             * Jeśli payloadu nie da się zserializować, event nie może być zapisany.
             *
             * To błąd techniczny aplikacji albo źle zbudowanego payloadu.
             * Rzucamy wyjątek runtime, żeby transakcja biznesowa mogła zostać wycofana.
             *
             * Lepiej nie zapisać zamówienia/faktury/operacji niż zapisać ją bez
             * kluczowego eventu, jeśli downstream procesy zależą od outboxa.
             */
            throw new IllegalStateException("Could not serialize outbox event payload", e);
        }
    }
}