package com.example.autocomplete.config;

import com.example.autocomplete.model.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;

/**
 * Konfiguracja danych wejściowych dla autocomplete.
 *
 * Ta klasa tworzy listę sugestii, która później trafia do:
 * - IndexBootstrapConfig,
 * - SuggestionTrieIndex,
 * - rankera,
 * - safety filtera.
 *
 * W tym projekcie dane są zapisane statycznie w kodzie,
 * żeby łatwo uruchomić aplikację bez bazy danych i pipeline'u.
 *
 * W produkcji sugestie byłyby ładowane np. z:
 * - hurtowni danych,
 * - batchowego index buildera,
 * - pliku snapshotu,
 * - OpenSearch/Elasticsearch,
 * - event pipeline'u.
 */
@Configuration
public class SuggestionDataConfig {

    /**
     * Bean z listą sugestii dostępnych w aplikacji.
     *
     * Spring wstrzykuje tę listę później tam, gdzie potrzebne są dane
     * do budowy indeksu, np. w IndexBootstrapConfig.
     */
    @Bean
    public List<Suggestion> suggestions() {
        List<Suggestion> s = new ArrayList<>();

        /*
         * Sugestia produktowa dla iPhone 15.
         *
         * Zawiera:
         * - wysoką popularność,
         * - dobry CTR,
         * - dobrą konwersję,
         * - wysoką jakość,
         * - kategorie electronics/apple,
         * - locale PL i US,
         * - aliasy wspierające różne zapisy query.
         */
        s.add(sug(
                "q-iphone-15",
                "iPhone 15",
                1000,
                .42,
                .21,
                .35,
                .95,
                Set.of("electronics", "apple"),
                Set.of("en-US", "pl-PL"),
                Set.of("US", "PL"),
                false,
                "iphone15",
                "iphone-15",
                "apple iphone 15"
        ));

        /*
         * Bardziej szczegółowa sugestia dla iPhone 15 Pro.
         *
         * Ma bardzo dobre metryki jakościowe i sprzedażowe,
         * więc ranker może ją często promować wysoko.
         *
         * Alias "iphon 15 pro" symuluje literówkę użytkownika.
         */
        s.add(sug(
                "q-iphone-15-pro",
                "iPhone 15 Pro",
                950,
                .48,
                .27,
                .45,
                .97,
                Set.of("electronics", "apple"),
                Set.of("en-US", "pl-PL"),
                Set.of("US", "PL"),
                false,
                "iphone15pro",
                "iphon 15 pro",
                "iphone-15-pro"
        ));

        /*
         * Sugestia dla MacBook Pro.
         *
         * Dzięki kategorii i brandowi Apple może dostać boost
         * u użytkowników z profilem "u-apple".
         */
        s.add(sug(
                "q-macbook-pro",
                "MacBook Pro",
                920,
                .44,
                .29,
                .30,
                .96,
                Set.of("electronics", "apple"),
                Set.of("en-US", "pl-PL"),
                Set.of("US", "PL"),
                false,
                "macbookpro",
                "mac book pro"
        ));

        /*
         * Sugestia gamingowa.
         *
         * Może być promowana użytkownikom o profilu "u-gaming"
         * oraz w krajach, gdzie ma wysoki trending score.
         */
        s.add(sug(
                "q-playstation-5",
                "Sony PlayStation 5",
                830,
                .36,
                .19,
                .32,
                .89,
                Set.of("gaming", "electronics"),
                Set.of("en-US", "pl-PL"),
                Set.of("US", "PL"),
                false,
                "ps5",
                "playstation five"
        ));

        /*
         * Sugestia developerska.
         *
         * Dobrze pasuje do profilu "u-dev" oraz kategorii developer-tools.
         */
        s.add(sug(
                "q-java-spring-boot",
                "Java Spring Boot",
                720,
                .46,
                .20,
                .26,
                .94,
                Set.of("software", "developer-tools"),
                Set.of("en-US", "pl-PL"),
                Set.of("US", "PL"),
                false,
                "spring boot",
                "springboot java"
        ));

        /*
         * Sugestia związana z Dockerem.
         *
         * Alias "docker-compose" wspiera query wpisywane z myślnikiem.
         * Normalizer zamieni myślnik na spację, więc dopasowanie będzie spójne.
         */
        s.add(sug(
                "q-docker-compose",
                "Docker Compose",
                730,
                .41,
                .15,
                .20,
                .89,
                Set.of("software", "developer-tools"),
                Set.of("en-US", "pl-PL"),
                Set.of("US", "PL"),
                false,
                "docker-compose"
        ));

        /*
         * Sugestia techniczna dla Redis.
         *
         * Może być wysoko dla zapytań związanych z cache
         * albo użytkowników developerskich.
         */
        s.add(sug(
                "q-redis",
                "Redis",
                700,
                .32,
                .11,
                .14,
                .85,
                Set.of("software", "developer-tools"),
                Set.of("en-US", "pl-PL"),
                Set.of("US", "PL"),
                false,
                "redis cache"
        ));

        /*
         * Przykład sugestii spamowej / zablokowanej.
         *
         * Ma wysoką popularność, ale:
         * - manuallyBlocked = true,
         * - quality score = 0.12,
         * - tekst zawiera "free free free".
         *
         * Dzięki temu można przetestować:
         * - SafetyPolicyFilter,
         * - filtrowanie w rankerze,
         * - odporność systemu na złe dane wejściowe.
         */
        s.add(sug(
                "q-spam",
                "iPhone free free free",
                990,
                .02,
                .0,
                .6,
                .12,
                Set.of("spam"),
                Set.of("en-US"),
                Set.of("US"),
                true,
                "iphone spam"
        ));

        /*
         * Generujemy większą liczbę sztucznych produktów.
         *
         * Cel:
         * - zwiększyć rozmiar datasetu,
         * - sprawdzić zachowanie Trie dla większej liczby sugestii,
         * - przetestować candidate limit,
         * - dać rankerowi więcej danych.
         *
         * To są dane testowe, nie realistyczny katalog produkcyjny.
         */
        for (int i = 1; i <= 500; i++) {
            s.add(sug(
                    "q-product-" + i,
                    "Product " + i,
                    500 - (i % 300),
                    .05 + ((i % 30) / 100.0),
                    .02 + ((i % 20) / 150.0),
                    (i % 10) / 10.0,
                    i % 17 == 0 ? .20 : .70,
                    Set.of("general"),
                    Set.of("en-US"),
                    Set.of("US"),
                    false,
                    "product-" + i,
                    "sku " + i
            ));
        }

        /*
         * Zwracamy niemodyfikowalną kopię listy.
         *
         * Dzięki temu inne komponenty nie powinny przypadkowo zmienić
         * bazowego datasetu po starcie aplikacji.
         */
        return List.copyOf(s);
    }

    /**
     * Pomocnicza metoda tworząca Suggestion.
     *
     * Dzięki niej definicje danych wyżej są krótsze i czytelniejsze.
     *
     * Parametry mapują się na:
     * - id sugestii,
     * - tekst wyświetlany użytkownikowi,
     * - metryki rankingowe,
     * - kategorie,
     * - locale,
     * - kraje,
     * - status blokady,
     * - aliasy.
     */
    private Suggestion sug(
            String id,
            String text,
            int pop,
            double ctr,
            double conv,
            double fresh,
            double quality,
            Set<String> cats,
            Set<String> locales,
            Set<String> countries,
            boolean blocked,
            String... aliases
    ) {
        return new Suggestion(
                id,
                text,
                "query",
                new SuggestionMetrics(
                        pop,
                        ctr,
                        conv,
                        fresh,
                        quality
                ),
                List.of(aliases),
                cats,
                locales,
                countries,
                blocked
        );
    }
}