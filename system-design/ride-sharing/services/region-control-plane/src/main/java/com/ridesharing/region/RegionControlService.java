package com.ridesharing.region;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Serwis control-plane dla routingu regionalnego.
 *
 * W architekturze multi-region / active-active ten komponent decyduje,
 * który region powinien obsłużyć dany agregat lub miasto.
 *
 * W aplikacji ride-sharing jest to ważne, bo przejazdy są silnie lokalne:
 * - pasażer, kierowca, pickup i płatność zwykle należą do konkretnego rynku,
 * - latency ma znaczenie,
 * - matching i location powinny działać blisko miasta,
 * - zapis stanu ride powinien mieć jednego właściciela.
 *
 * Ta implementacja to baseline control-plane.
 * Nie wykonuje prawdziwego failoveru ani replikacji danych.
 */
@Service
public class RegionControlService {

    /**
     * Region, w którym działa aktualna instancja aplikacji.
     *
     * Property:
     * app.region
     *
     * Domyślnie:
     * eu-central
     *
     * Używany jako fallback, jeśli wybrany home region nie przyjmuje zapisów
     * albo nie został znaleziony na liście regionów.
     */
    private final String localRegion;

    /**
     * Statyczna lista znanych regionów.
     *
     * RegionDescriptor prawdopodobnie zawiera:
     * - regionId,
     * - status,
     * - wagę/prioritet,
     * - informację, czy region przyjmuje zapisy,
     * - nazwę klastra Kafki,
     * - rolę regionu.
     *
     * Produkcyjnie ta lista powinna pochodzić z konfiguracji, service discovery
     * albo control-plane storage, a nie być zahardkodowana.
     */
    private final List<RegionDescriptor> regions = List.of(
            new RegionDescriptor(
                    "eu-central",
                    "ACTIVE",
                    100,
                    true,
                    "kafka-eu",
                    "primary"
            ),
            new RegionDescriptor(
                    "eu-west",
                    "ACTIVE",
                    80,
                    true,
                    "kafka-eu-west",
                    "primary"
            ),
            new RegionDescriptor(
                    "us-east",
                    "ACTIVE",
                    70,
                    true,
                    "kafka-us",
                    "primary"
            )
    );

    /**
     * Konstruktor ustawiający lokalny region z konfiguracji.
     */
    public RegionControlService(
            @Value("${app.region:eu-central}") String localRegion
    ) {
        this.localRegion = localRegion;
    }

    /**
     * Zwraca listę skonfigurowanych regionów.
     *
     * To endpoint/control-plane view, przydatny diagnostycznie.
     *
     * Uwaga: lista jest niemodyfikowalna, bo pochodzi z List.of().
     */
    public List<RegionDescriptor> regions() {
        return regions;
    }

    /**
     * Podejmuje decyzję routingową dla agregatu.
     *
     * Flow:
     * 1. Wyznacza home region na podstawie userRegionHint, cityId albo hash aggregateId.
     * 2. Sprawdza, czy home region istnieje i przyjmuje zapisy.
     * 3. Jeśli tak, kieruje zapis do home region.
     * 4. Jeśli nie, używa localRegion jako fallback.
     * 5. Zwraca RoutingDecision z informacją o modelu spójności i powodzie.
     *
     * Model "local_strong_global_eventual" oznacza zwykle:
     * - silna spójność lokalnie w regionie właścicielskim,
     * - eventual consistency między regionami.
     *
     * Ważne: ta metoda tylko podejmuje decyzję.
     * Nie wymusza ownershipu w bazie ani nie rozwiązuje konfliktów zapisu.
     */
    public RoutingDecision route(
            String aggregateId,
            String cityId,
            String userRegionHint
    ) {
        /*
         * Home region to region logicznie właściwy dla danego agregatu/miasta/użytkownika.
         */
        String home = homeRegion(
                cityId,
                aggregateId,
                userRegionHint
        );

        /*
         * Jeśli home region istnieje i przyjmuje zapisy, używamy go.
         * W przeciwnym razie spadamy do localRegion.
         *
         * To jest prosty failover, ale nie sprawdza realnego health regionu,
         * lagów replikacji ani ownership transferu.
         */
        String routed = regions.stream()
                .filter(r -> r.regionId().equals(home) && r.acceptsWrites())
                .findFirst()
                .map(RegionDescriptor::regionId)
                .orElse(localRegion);

        return new RoutingDecision(
                aggregateId,
                home,
                routed,
                "local_strong_global_eventual",
                "city_home_region_plus_hash_fallback",
                Instant.now()
        );
    }

    /**
     * Wyznacza home region.
     *
     * Priorytet sygnałów:
     * 1. userRegionHint, jeśli podany.
     * 2. cityId, jeśli znane miasto.
     * 3. hash aggregateId jako fallback.
     *
     * To jest prosta reguła routingu.
     * Produkcyjnie ownership powinien być stabilny i zapisany,
     * żeby agregat nie zmienił regionu po zmianie konfiguracji.
     */
    private String homeRegion(
            String cityId,
            String aggregateId,
            String hint
    ) {
        /*
         * Hint od użytkownika/klienta ma najwyższy priorytet.
         *
         * To jest wygodne, ale ryzykowne, jeśli pochodzi z niezaufanego klienta.
         * Publiczny klient nie powinien móc dowolnie wybrać regionu zapisu.
         */
        if (hint != null && !hint.isBlank()) {
            return hint;
        }

        /*
         * Miasta europejskie kierujemy do eu-central.
         *
         * Uwaga: London logicznie często bardziej pasowałby do eu-west,
         * ale tutaj został przypisany do eu-central.
         */
        if (cityId != null
                && List.of(
                "warsaw",
                "krakow",
                "berlin",
                "paris",
                "london"
        ).contains(cityId.toLowerCase())) {
            return "eu-central";
        }

        /*
         * Miasta USA kierujemy do us-east.
         */
        if (cityId != null
                && List.of(
                "new-york",
                "boston",
                "miami"
        ).contains(cityId.toLowerCase())) {
            return "us-east";
        }

        /*
         * Fallback hashujący aggregateId na jeden z trzech regionów.
         *
         * Dzięki temu nieznane miasta/agregaty są rozkładane między regiony.
         * Ale jeśli później zmieni się liczba regionów, hash modulo 3 może
         * przenieść wiele agregatów do innych regionów.
         */
        int bucket = Math.abs(
                (aggregateId == null ? "default" : aggregateId).hashCode()
        ) % 3;

        return bucket == 0
                ? "eu-central"
                : bucket == 1
                ? "eu-west"
                : "us-east";
    }
}