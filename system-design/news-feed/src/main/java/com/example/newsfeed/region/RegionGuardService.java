package com.example.newsfeed.region;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serwis pilnujący, czy dana instancja aplikacji może wykonywać zapisy.
 *
 * W systemie multi-region nie każda instancja powinna przyjmować write requesty.
 *
 * Przykład:
 * - eu-central-1 jest regionem głównym do zapisu,
 * - us-east-1 działa jako read replica,
 * - local działa lokalnie w development.
 *
 * Ten serwis chroni aplikację przed zapisem w złym regionie.
 *
 * To jest ważne, bo zapis w wielu regionach naraz bez kontroli może prowadzić do:
 * - konfliktów danych,
 * - rozjechania feed inbox,
 * - duplikatów eventów,
 * - niespójnych liczników,
 * - problemów z kolejnością operacji.
 */
@Service
public class RegionGuardService {

    /**
     * Region, w którym aktualnie działa ta instancja aplikacji.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   region:
     *     current: eu-central-1
     *
     * Lokalnie domyślnie jest to "local".
     */
    private final String currentRegion;

    /**
     * Region, który ma prawo wykonywać zapisy.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   region:
     *     write-owner: eu-central-1
     *
     * Jeśli currentRegion różni się od writeOwner,
     * aplikacja powinna blokować operacje mutujące.
     */
    private final String writeOwner;

    /**
     * Flaga informująca, czy ta instancja działa jako read replica.
     *
     * Konfiguracja:
     *
     * newsfeed:
     *   region:
     *     read-replica: true
     *
     * Read replica może obsługiwać odczyty,
     * ale nie powinna przyjmować zapisów.
     */
    private final boolean readReplica;

    /**
     * Wstrzyknięcie konfiguracji regionu.
     *
     * Domyślnie wszystko działa w trybie local,
     * więc lokalny development nie wymaga dodatkowej konfiguracji.
     */
    public RegionGuardService(
            @Value("${newsfeed.region.current:local}") String currentRegion,
            @Value("${newsfeed.region.write-owner:local}") String writeOwner,
            @Value("${newsfeed.region.read-replica:false}") boolean readReplica
    ) {
        this.currentRegion = currentRegion;
        this.writeOwner = writeOwner;
        this.readReplica = readReplica;
    }

    /**
     * Wymusza, żeby dana operacja była wykonywana tylko w regionie zapisującym.
     *
     * Ta metoda powinna być wywoływana przed operacjami mutującymi, np.:
     * - tworzenie posta,
     * - lajkowanie,
     * - komentowanie,
     * - follow / unfollow,
     * - usuwanie posta,
     * - operacje moderacyjne zmieniające stan.
     *
     * Jeśli aktualny region nie jest właścicielem zapisu,
     * metoda rzuca wyjątek i blokuje operację.
     */
    public void requireWriteRegion() {
        /*
         * Blokujemy zapis, jeśli instancja jest read replicą.
         *
         * Nawet jeśli currentRegion przypadkiem równa się writeOwner,
         * flaga readReplica powinna mieć pierwszeństwo bezpieczeństwa.
         */
        if (readReplica || !currentRegion.equals(writeOwner)) {
            /*
             * Błąd jest celowo jawny.
             *
             * Dzięki temu w logach widać:
             * - w jakim regionie działa aplikacja,
             * - który region jest właścicielem zapisu.
             */
            throw new IllegalStateException(
                    "Writes are disabled in this region. Current="
                            + currentRegion
                            + ", owner="
                            + writeOwner
            );
        }
    }

    /**
     * Zwraca aktualny region aplikacji.
     *
     * Używane przez RegionController do endpointu diagnostycznego:
     *
     * GET /api/v1/region
     */
    public String currentRegion() {
        return currentRegion;
    }
}