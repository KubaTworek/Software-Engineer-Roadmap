package com.example.urlshortener.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za publikowanie zmian URL-i do rozproszonego storage.
 *
 * <p>
 * W architekturze multi-region lub edge/CDN sama lokalna baza danych aplikacji
 * nie zawsze wystarcza do szybkiego globalnego lookupu short code.
 * Dlatego system może utrzymywać dodatkową warstwę distributed storage, np.:
 * </p>
 *
 * <ul>
 *     <li>DynamoDB Global Tables,</li>
 *     <li>Cassandra / ScyllaDB,</li>
 *     <li>CockroachDB,</li>
 *     <li>Spanner,</li>
 *     <li>globalny key-value store,</li>
 *     <li>lokalną implementację JPA w trybie development.</li>
 * </ul>
 *
 * <p>
 * Ten serwis jest cienką warstwą pośrednią między logiką domenową
 * a abstrakcją {@link DistributedUrlStore}.
 * </p>
 *
 * <p>
 * Nazwy metod zawierają słowo {@code publish}, ponieważ semantycznie operacja
 * oznacza opublikowanie zmiany do globalnej warstwy lookup. W obecnej implementacji
 * publikacja jest jednak wykonywana synchronicznie przez bezpośrednie wywołanie
 * {@code distributedUrlStore.upsert(...)} albo {@code distributedUrlStore.delete(...)}.
 * </p>
 *
 * <p>
 * W bardziej produkcyjnej wersji ta klasa mogłaby zamiast bezpośredniego zapisu
 * publikować event do kolejki albo korzystać ze wzorca transactional outbox.
 * </p>
 */
@Service
public class DistributedStorageReplicationService {

    /**
     * Logger diagnostyczny.
     *
     * <p>
     * Używany do zapisywania informacji o tym, że zmiana URL-a została wysłana
     * do distributed storage.
     * </p>
     *
     * <p>
     * Logi są na poziomie {@code debug}, ponieważ przy dużym ruchu tworzenia
     * lub aktualizacji URL-i takich wpisów może być dużo.
     * </p>
     */
    private static final Logger log = LoggerFactory.getLogger(DistributedStorageReplicationService.class);

    /**
     * Abstrakcja rozproszonego magazynu danych URL.
     *
     * <p>
     * Serwis nie wie, czy pod spodem znajduje się PostgreSQL, DynamoDB, Cassandra
     * czy inny system. Korzysta wyłącznie z kontraktu {@link DistributedUrlStore}.
     * </p>
     *
     * <p>
     * Dzięki temu można zmienić implementację storage bez zmieniania logiki
     * serwisów domenowych, takich jak {@code ShortUrlService}.
     * </p>
     */
    private final DistributedUrlStore distributedUrlStore;

    /**
     * Konstruktor serwisu.
     *
     * <p>
     * Spring wstrzykuje implementację {@link DistributedUrlStore} przez constructor injection.
     * </p>
     *
     * @param distributedUrlStore implementacja distributed URL store
     */
    public DistributedStorageReplicationService(DistributedUrlStore distributedUrlStore) {
        this.distributedUrlStore = distributedUrlStore;
    }

    /**
     * Publikuje operację upsert rekordu URL do distributed storage.
     *
     * <p>
     * Upsert oznacza:
     * </p>
     *
     * <ul>
     *     <li>jeśli rekord dla danego {@code shortCode} nie istnieje — utwórz go,</li>
     *     <li>jeśli rekord istnieje — zaktualizuj go.</li>
     * </ul>
     *
     * <p>
     * Ta metoda powinna być wywoływana po zmianach, które wpływają na globalny lookup,
     * np.:
     * </p>
     *
     * <ul>
     *     <li>utworzenie nowego short URL,</li>
     *     <li>blokada short URL,</li>
     *     <li>odblokowanie short URL,</li>
     *     <li>zmiana statusu,</li>
     *     <li>zmiana daty wygaśnięcia,</li>
     *     <li>zmiana docelowego long URL, jeśli system wspiera edycję linków.</li>
     * </ul>
     *
     * <p>
     * Aktualny rekord jest reprezentowany przez {@link UrlLookupRecord}. Zawiera
     * dane potrzebne do szybkiego lookupu, np.:
     * </p>
     *
     * <ul>
     *     <li>{@code shortCode},</li>
     *     <li>{@code longUrl},</li>
     *     <li>{@code status},</li>
     *     <li>{@code expiresAt},</li>
     *     <li>{@code regionId},</li>
     *     <li>{@code updatedAt}.</li>
     * </ul>
     *
     * @param record rekord lookupu, który ma zostać zapisany albo zaktualizowany
     */
    public void publishUpsert(UrlLookupRecord record) {
        /*
         * W obecnej implementacji zapis do distributed storage odbywa się bezpośrednio.
         *
         * To proste rozwiązanie dla referencyjnej implementacji, ale warto pamiętać,
         * że jest to operacja synchroniczna. Jeśli distributedUrlStore będzie wolny
         * albo niedostępny, może to wpłynąć na request, który wywołał tę metodę.
         */
        distributedUrlStore.upsert(record);

        /*
         * Logujemy informację diagnostyczną.
         *
         * shortCode pozwala znaleźć konkretny link, a regionId mówi, z którego
         * regionu pochodziła zmiana.
         */
        log.debug(
                "Distributed URL upsert emitted for shortCode={} region={}",
                record.shortCode(),
                record.regionId()
        );
    }

    /**
     * Publikuje operację usunięcia rekordu URL z distributed storage.
     *
     * <p>
     * Metoda usuwa rekord lookupu dla wskazanego short code.
     * Może być używana np. przy trwałym usunięciu linku albo przy sprzątaniu danych.
     * </p>
     *
     * <p>
     * W praktyce dla URL shortenera często bezpieczniejsze jest jednak wykonanie
     * soft delete lub ustawienie statusu {@code DELETED/BLOCKED}, a nie fizyczne
     * usuwanie rekordu. Dzięki temu edge/global lookup może zwrócić kontrolowane
     * {@code 410 Gone}, zamiast pozwolić na ponowne użycie lub niejednoznaczne 404.
     * </p>
     *
     * @param shortCode kod skróconego linku do usunięcia z distributed storage
     */
    public void publishDelete(String shortCode) {
        /*
         * Usuwamy rekord z distributed storage.
         */
        distributedUrlStore.delete(shortCode);

        /*
         * Logujemy operację usunięcia.
         */
        log.debug(
                "Distributed URL delete emitted for shortCode={}",
                shortCode
        );
    }
}