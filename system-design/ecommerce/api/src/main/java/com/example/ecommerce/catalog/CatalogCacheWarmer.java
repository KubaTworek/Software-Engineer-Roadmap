package com.example.ecommerce.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker odpowiedzialny za cykliczne podgrzewanie cache katalogu.
 *
 * W e-commerce katalog produktów i kategorie są bardzo często odczytywane:
 * - strona główna,
 * - menu kategorii,
 * - listing produktów,
 * - wejścia z SEO,
 * - pierwsze ekrany aplikacji mobilnej.
 *
 * Ten komponent regularnie wywołuje najważniejsze metody CatalogService,
 * żeby utrzymywać ich wyniki w cache i zmniejszyć liczbę zapytań do bazy.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.catalog-cache",
        name = "warmer-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CatalogCacheWarmer {

    /**
     * Logger techniczny.
     *
     * Używany tylko do informacji diagnostycznej, że cache warmer wykonał cykl.
     * Poziom DEBUG jest dobry, bo ten worker działa cyklicznie i nie powinien
     * zaśmiecać logów produkcyjnych przy każdym uruchomieniu.
     */
    private static final Logger log = LoggerFactory.getLogger(CatalogCacheWarmer.class);

    /**
     * Serwis katalogu.
     *
     * Wywołujemy publiczne metody serwisu, ponieważ to właśnie na nich
     * znajdują się adnotacje cache, np. @Cacheable.
     *
     * Dzięki temu warmer nie zapisuje cache ręcznie, tylko przechodzi przez
     * ten sam mechanizm, którego używa normalny ruch z API.
     */
    private final CatalogService catalog;

    /**
     * Constructor injection.
     *
     * Worker potrzebuje tylko CatalogService, bo jego zadaniem jest
     * wymuszenie odczytu najważniejszych danych katalogowych.
     */
    public CatalogCacheWarmer(CatalogService catalog) {
        this.catalog = catalog;
    }

    /**
     * Cyklicznie podgrzewa cache katalogu.
     *
     * @Scheduled uruchamia metodę w stałym odstępie czasu.
     * Domyślnie co 60 sekund, ale wartość można zmienić w konfiguracji:
     *
     * app.catalog-cache.warmer-fixed-delay-ms=60000
     *
     * fixedDelay oznacza, że kolejny cykl startuje dopiero po zakończeniu poprzedniego.
     * To bezpieczniejsze niż fixedRate, bo jeśli odczyt katalogu potrwa dłużej,
     * zadania nie zaczną nakładać się na siebie.
     *
     * Wywoływane metody:
     * - catalog.categories() — ładuje cache kategorii,
     * - catalog.activeProducts() — ładuje cache głównej listy aktywnych produktów.
     *
     * Efekt:
     * pierwszy realny użytkownik po restarcie aplikacji lub po wyczyszczeniu cache
     * nie musi płacić pełnego kosztu pobrania danych z bazy.
     */
    @Scheduled(fixedDelayString = "${app.catalog-cache.warmer-fixed-delay-ms:60000}")
    public void warmCatalogCaches() {
        catalog.categories();
        catalog.activeProducts();

        log.debug("Catalog cache warmer completed");
    }
}