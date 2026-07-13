package com.ridesharing.mvp.scaling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Komponent odpowiedzialny za przypisanie miasta do shardu.
 *
 * W Etapie 3 jest to uproszczony resolver shardingu per city.
 * W aplikacji ride-sharing cityId jest naturalnym kluczem podziału,
 * bo większość przejazdów, kierowców, lokalizacji i matchingu działa lokalnie
 * w ramach jednego miasta albo regionu.
 *
 * Ten komponent nie przełącza jeszcze fizycznie datasource.
 * Zwraca nazwę shardu, którą później można wykorzystać do routingu bazy,
 * routingu eventów, diagnostyki albo decyzji infrastrukturalnych.
 */
@Component
public class CityShardResolver {

    /**
     * Mapa przypisująca cityId do konkretnego shardu.
     *
     * Przykład:
     * - warsaw, krakow, gdansk -> shard-pl-1,
     * - berlin, munich -> shard-de-1.
     *
     * W MVP mapa jest statyczna i trzymana w pamięci.
     * Produkcyjnie takie mapowanie powinno pochodzić z konfiguracji,
     * service discovery, control plane albo tabeli konfiguracyjnej.
     */
    private final Map<String, String> cityToShard;

    /**
     * Tworzy statyczną mapę city -> shard.
     *
     * defaultShard pochodzi z konfiguracji:
     * app.sharding.default-shard
     *
     * Jeżeli konfiguracja nie istnieje, domyślnie używany jest shard-eu-1.
     * To zabezpiecza system przed brakiem mapowania dla nowego albo nieznanego miasta.
     */
    public CityShardResolver(
            @Value("${app.sharding.default-shard:shard-eu-1}") String defaultShard
    ) {
        this.cityToShard = Map.of(
                "warsaw", "shard-pl-1",
                "krakow", "shard-pl-1",
                "gdansk", "shard-pl-1",
                "berlin", "shard-de-1",
                "munich", "shard-de-1",
                "default", defaultShard
        );
    }

    /**
     * Zwraca shard dla podanego miasta.
     *
     * Flow:
     * 1. Jeżeli cityId jest puste albo null, zwraca shard domyślny.
     * 2. Normalizuje cityId do lowercase.
     * 3. Szuka miasta w mapie.
     * 4. Jeżeli miasta nie ma w mapie, zwraca shard domyślny.
     *
     * Dzięki temu system nie wywraca się na nieznanym mieście,
     * tylko kieruje je do bezpiecznego shardu fallbackowego.
     */
    public String resolve(String cityId) {
        if (cityId == null || cityId.isBlank()) {
            return cityToShard.get("default");
        }

        return cityToShard.getOrDefault(
                cityId.toLowerCase(),
                cityToShard.get("default")
        );
    }
}