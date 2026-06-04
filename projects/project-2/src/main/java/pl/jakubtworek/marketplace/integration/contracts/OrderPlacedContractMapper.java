package pl.jakubtworek.marketplace.integration.contracts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Warstwa antykorupcyjna między zewnętrznym kontraktem eventu a wewnętrznym eventem domenowym.
 *
 * Ta klasa odpowiada za zamianę różnych wersji kontraktu integracyjnego OrderPlaced
 * na jeden znormalizowany typ używany wewnątrz aplikacji: OrderPlaced.
 *
 * Dzięki temu handlery aplikacyjne nie muszą wiedzieć, czy payload przyszedł jako:
 * - OrderPlacedV1,
 * - OrderPlacedV2,
 * - albo potencjalnie kolejna wersja w przyszłości.
 *
 * Handlery dostają jeden spójny model eventu i mogą działać bez znajomości szczegółów
 * historycznych kontraktów.
 *
 * To jest ważne w event-driven architecture, ponieważ eventy opublikowane wcześniej
 * mogą żyć długo:
 * - w Kafce,
 * - w outboxie,
 * - w DLQ,
 * - w backupach,
 * - w mechanizmach replay.
 *
 * Nie można więc zakładać, że system zawsze będzie przetwarzał tylko najnowszy format.
 */
public class OrderPlacedContractMapper {

    /**
     * Lokalny ObjectMapper używany do deserializacji kontraktów eventów.
     *
     * Używamy kopii mappera, żeby nie modyfikować globalnej konfiguracji ObjectMappera.
     *
     * findAndRegisterModules() pozwala obsługiwać m.in.:
     * - Instant,
     * - UUID,
     * - typy z modułów Java Time.
     *
     * FAIL_ON_UNKNOWN_PROPERTIES = false jest kluczowe dla kompatybilności w przód.
     * Dzięki temu dodanie nowego pola do eventu nie psuje starszego konsumenta.
     */
    private final ObjectMapper objectMapper;

    public OrderPlacedContractMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Zamienia payload JSON oraz wersję eventu na wewnętrzny OrderPlaced.
     *
     * eventVersion decyduje, do którego kontraktu JSON ma zostać zdeserializowany payload.
     *
     * Obsługiwane wersje:
     * - 1 -> OrderPlacedV1,
     * - 2 -> OrderPlacedV2.
     *
     * Jeśli wersja nie jest obsługiwana, rzucany jest UnsupportedEventVersionException.
     * W flow z Kafką taki event powinien po retry trafić do DLQ, a nie zostać
     * przypadkowo przetworzony błędnie.
     */
    public OrderPlaced toDomainEvent(String payload, int eventVersion) {
        try {
            return switch (eventVersion) {
                case 1 -> fromV1(objectMapper.readValue(payload, OrderPlacedV1.class));
                case 2 -> fromV2(objectMapper.readValue(payload, OrderPlacedV2.class));
                default -> throw new UnsupportedEventVersionException(
                        "Unsupported OrderPlaced event version: " + eventVersion
                );
            };
        } catch (UnsupportedEventVersionException e) {
            /*
             * Nie opakowujemy ponownie błędu nieobsługiwanej wersji.
             * Dzięki temu wyżej w stacku można rozpoznać, że problem dotyczy kontraktu,
             * a nie np. chwilowego błędu infrastruktury.
             */
            throw e;
        } catch (Exception e) {
            /*
             * Każdy inny błąd deserializacji traktujemy jako błąd kontraktu.
             * Przykład:
             * - niepoprawny JSON,
             * - brak wymaganego pola,
             * - niepoprawny format UUID,
             * - niepoprawna data.
             */
            throw new UnsupportedEventVersionException(
                    "Cannot deserialize OrderPlaced v" + eventVersion,
                    e
            );
        }
    }

    /**
     * Mapuje kontrakt OrderPlacedV1 na aktualny wewnętrzny OrderPlaced.
     *
     * V1 ma prostszy format total:
     * - totalAmount,
     * - currency.
     *
     * Metoda wykonuje też podstawową walidację wymaganych pól.
     */
    private OrderPlaced fromV1(OrderPlacedV1 event) {
        /*
         * Wspieramy dwa możliwe pola identyfikujące agregat:
         * - aggregateId,
         * - orderId.
         *
         * To zwiększa tolerancję na historyczne różnice w nazwach pól kontraktu.
         */
        UUID aggregateId = firstNonNull(event.aggregateId(), event.orderId());

        if (aggregateId == null) {
            throw new UnsupportedEventVersionException(
                    "OrderPlaced v1 requires aggregateId or orderId"
            );
        }

        if (event.totalAmount() == null || event.currency() == null) {
            throw new UnsupportedEventVersionException(
                    "OrderPlaced v1 requires totalAmount and currency"
            );
        }

        /*
         * Linie zamówienia są opcjonalne w kontrakcie V1.
         *
         * Jeśli ich brakuje, mapujemy do pustej listy. To zachowuje kompatybilność,
         * ale trzeba mieć świadomość konsekwencji: niektóre handlery, np. inventory,
         * mogą potrzebować linii do rezerwacji stocku.
         */
        List<OrderPlaced.Line> lines = event.lines() == null
                ? List.of()
                : event.lines().stream()
                .map(line -> new OrderPlaced.Line(
                        line.productId(),
                        line.quantity(),
                        Money.of(line.unitPriceAmount(), line.currency())
                ))
                .toList();

        return new OrderPlaced(
                firstNonNull(event.eventId(), UUID.randomUUID()),
                aggregateId,
                event.customerId(),
                Money.of(event.totalAmount(), event.currency()),
                lines,
                firstNonNull(event.occurredAt(), Instant.now()),
                firstNonNull(event.correlationId(), UUID.randomUUID()),
                event.causationId()
        );
    }

    /**
     * Mapuje kontrakt OrderPlacedV2 na aktualny wewnętrzny OrderPlaced.
     *
     * V2 ma bardziej uporządkowany format total:
     * - total.amount,
     * - total.currency.
     *
     * To jest przykład ewolucji kontraktu bez zmuszania handlerów aplikacyjnych
     * do obsługi wielu kształtów payloadu.
     */
    private OrderPlaced fromV2(OrderPlacedV2 event) {
        UUID aggregateId = firstNonNull(event.aggregateId(), event.orderId());

        if (aggregateId == null) {
            throw new UnsupportedEventVersionException(
                    "OrderPlaced v2 requires aggregateId or orderId"
            );
        }

        if (event.total() == null) {
            throw new UnsupportedEventVersionException(
                    "OrderPlaced v2 requires total"
            );
        }

        List<OrderPlaced.Line> lines = event.lines() == null
                ? List.of()
                : event.lines().stream()
                .map(line -> new OrderPlaced.Line(
                        line.productId(),
                        line.quantity(),
                        Money.of(
                                line.unitPrice().amount(),
                                line.unitPrice().currency()
                        )
                ))
                .toList();

        return new OrderPlaced(
                firstNonNull(event.eventId(), UUID.randomUUID()),
                aggregateId,
                event.customerId(),
                Money.of(
                        event.total().amount(),
                        event.total().currency()
                ),
                lines,
                firstNonNull(event.occurredAt(), Instant.now()),
                firstNonNull(event.correlationId(), UUID.randomUUID()),
                event.causationId()
        );
    }

    /**
     * Zwraca pierwszą wartość, jeśli nie jest null, w przeciwnym razie drugą.
     *
     * Pomaga obsłużyć kompatybilność między kontraktami, w których to samo znaczenie
     * mogło mieć różne nazwy pól, np. aggregateId albo orderId.
     */
    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}