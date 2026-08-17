package com.example.urlshortener.storage;

import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.region.RegionProperties;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referencyjna implementacja interfejsu {@link DistributedUrlStore} oparta o JPA.
 *
 * <p>
 * W docelowej architekturze multi-region lub edge/CDN interfejs
 * {@link DistributedUrlStore} może być zaimplementowany przez prawdziwy
 * rozproszony magazyn danych, np.:
 * </p>
 *
 * <ul>
 *     <li>DynamoDB Global Tables,</li>
 *     <li>Cassandra / ScyllaDB,</li>
 *     <li>Google Spanner,</li>
 *     <li>CockroachDB,</li>
 *     <li>inny globalny key-value store.</li>
 * </ul>
 *
 * <p>
 * Ta klasa jest prostą implementacją referencyjną, która nie używa osobnego
 * distributed storage. Zamiast tego odczytuje dane z lokalnej bazy JPA przez
 * {@link ShortUrlRepository}.
 * </p>
 *
 * <p>
 * Oznacza to, że w tej wersji źródłem prawdy pozostaje encja {@link ShortUrl}
 * zapisana w relacyjnej bazie danych. {@code DistributedUrlStore} jest tutaj
 * przede wszystkim abstrakcją projektową, która pozwala później podmienić
 * implementację bez zmiany kodu używającego tego interfejsu.
 * </p>
 *
 * <p>
 * Praktycznie:
 * </p>
 *
 * <ul>
 *     <li>{@link #findByShortCode(String)} działa realnie i czyta z bazy,</li>
 *     <li>{@link #upsert(UrlLookupRecord)} jest no-op,</li>
 *     <li>{@link #delete(String)} jest no-op.</li>
 * </ul>
 *
 * <p>
 * Taki układ jest sensowny dla lokalnego developmentu i wersji demonstracyjnej,
 * ale nie daje realnej replikacji multi-region.
 * </p>
 */
@Component
public class JpaDistributedUrlStore implements DistributedUrlStore {

    /**
     * Repozytorium głównej encji ShortUrl.
     *
     * <p>
     * Używane do odczytu rekordu po {@code shortCode}.
     * W tej implementacji zastępuje ono prawdziwy distributed storage.
     * </p>
     */
    private final ShortUrlRepository repository;

    /**
     * Konfiguracja regionu aplikacji.
     *
     * <p>
     * Używana podczas mapowania encji {@link ShortUrl} na {@link UrlLookupRecord}.
     * Do rekordu lookupu dokładany jest identyfikator aktualnego regionu.
     * </p>
     */
    private final RegionProperties regionProperties;

    /**
     * Konstruktor komponentu.
     *
     * <p>
     * Spring wstrzykuje {@link ShortUrlRepository} oraz {@link RegionProperties}
     * przez constructor injection.
     * </p>
     *
     * @param repository repozytorium skróconych URL-i
     * @param regionProperties konfiguracja bieżącego regionu
     */
    public JpaDistributedUrlStore(
            ShortUrlRepository repository,
            RegionProperties regionProperties
    ) {
        this.repository = repository;
        this.regionProperties = regionProperties;
    }

    /**
     * Wyszukuje rekord lookupu po short code.
     *
     * <p>
     * W tej implementacji metoda nie odpytuje zewnętrznego distributed storage,
     * tylko lokalne repozytorium JPA.
     * </p>
     *
     * <p>
     * Przepływ:
     * </p>
     *
     * <ol>
     *     <li>Wywołuje {@link ShortUrlRepository#findByShortCode(String)}.</li>
     *     <li>Jeśli encja istnieje, mapuje ją na {@link UrlLookupRecord}.</li>
     *     <li>Jeśli encja nie istnieje, zwraca {@link Optional#empty()}.</li>
     * </ol>
     *
     * <p>
     * Metoda jest oznaczona jako {@code @Transactional(readOnly = true)}, ponieważ
     * wykonuje wyłącznie odczyt z bazy danych.
     * </p>
     *
     * @param shortCode kod skróconego linku
     * @return rekord lookupu, jeśli short code istnieje
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UrlLookupRecord> findByShortCode(String shortCode) {
        /*
         * Szukamy encji ShortUrl w bazie po shortCode.
         *
         * Następnie Optional.map() zamienia znalezioną encję na UrlLookupRecord.
         */
        return repository.findByShortCode(shortCode)
                .map(this::toRecord);
    }

    /**
     * Publikuje lub zapisuje rekord lookupu do distributed storage.
     *
     * <p>
     * W tej implementacji metoda celowo nic nie robi.
     * </p>
     *
     * <p>
     * Powód: JPA encja {@link ShortUrl} jest tutaj źródłem prawdy. Skoro dane
     * zostały już zapisane w głównej tabeli URL-i, nie ma drugiego magazynu,
     * do którego należałoby je replikować.
     * </p>
     *
     * <p>
     * W prawdziwej implementacji distributed storage ta metoda powinna wykonać
     * idempotentny zapis typu put/upsert.
     * </p>
     *
     * <p>
     * Przykład dla zewnętrznego key-value store:
     * </p>
     *
     * <pre>
     * key   = record.shortCode()
     * value = record
     * </pre>
     *
     * <p>
     * Idempotencja jest ważna, ponieważ eventy replikacyjne mogą zostać dostarczone
     * więcej niż raz.
     * </p>
     *
     * @param record rekord lookupu do zapisania
     */
    @Override
    public void upsert(UrlLookupRecord record) {
        /*
         * No-op w implementacji referencyjnej.
         *
         * JPA entity remains the source of truth in the reference implementation.
         * External distributed stores can implement this as an idempotent put.
         */
    }

    /**
     * Usuwa rekord lookupu z distributed storage.
     *
     * <p>
     * W tej implementacji metoda również nic nie robi.
     * </p>
     *
     * <p>
     * W realnym distributed storage można zaimplementować to na dwa sposoby:
     * </p>
     *
     * <ul>
     *     <li>fizyczne usunięcie rekordu,</li>
     *     <li>zapis tombstone, czyli znacznika usunięcia.</li>
     * </ul>
     *
     * <p>
     * Dla URL shortenera często lepszy jest tombstone lub status {@code DELETED},
     * ponieważ pozwala uniknąć niejednoznaczności między:
     * </p>
     *
     * <ul>
     *     <li>link nigdy nie istniał,</li>
     *     <li>link kiedyś istniał, ale został usunięty.</li>
     * </ul>
     *
     * @param shortCode kod skróconego linku do usunięcia
     */
    @Override
    public void delete(String shortCode) {
        /*
         * No-op w implementacji referencyjnej.
         *
         * External distributed stores can implement this as a delete/tombstone.
         */
    }

    /**
     * Mapuje encję {@link ShortUrl} na lekki rekord lookupu {@link UrlLookupRecord}.
     *
     * <p>
     * {@code UrlLookupRecord} jest uproszczonym modelem przeznaczonym do szybkiego
     * odczytu przez edge/CDN lub distributed storage. Nie musi zawierać wszystkich
     * pól encji domenowej.
     * </p>
     *
     * <p>
     * Mapowane są pola potrzebne do decyzji redirectu:
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
     * @param entity encja ShortUrl z bazy danych
     * @return rekord lookupu dla distributed storage / edge
     */
    private UrlLookupRecord toRecord(ShortUrl entity) {
        return new UrlLookupRecord(
                entity.getShortCode(),
                entity.getLongUrl(),
                entity.getStatus(),
                entity.getExpiresAt(),
                regionProperties.getRegionId(),
                entity.getUpdatedAt()
        );
    }
}