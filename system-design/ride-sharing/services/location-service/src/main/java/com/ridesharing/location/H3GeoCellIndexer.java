package com.ridesharing.location;

import com.uber.h3core.H3Core;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Implementacja GeoCellIndexer oparta o H3.
 *
 * H3 to geospatial indexing system, który dzieli powierzchnię Ziemi
 * na sześciokątne komórki o różnych poziomach dokładności.
 *
 * W ride-sharingu H3 jest przydatne do:
 * - grupowania lokalizacji kierowców,
 * - szybkiego wyszukiwania kierowców w pobliżu pickup pointu,
 * - liczenia podaży i popytu per obszar,
 * - driver positioning,
 * - dynamic pricingu,
 * - heatmap i analityki miejskiej.
 *
 * Ta klasa nie przechowuje lokalizacji.
 * Jej zadaniem jest wyłącznie przeliczenie lat/lng na H3 cell
 * oraz wyznaczenie sąsiednich komórek.
 */
@Component
public class H3GeoCellIndexer implements GeoCellIndexer {

    /**
     * Klient biblioteki H3.
     *
     * H3Core wykonuje właściwe operacje geospatial:
     * - lat/lng -> H3 cell,
     * - H3 cell -> sąsiednie komórki.
     */
    private final H3Core h3;

    /**
     * Rozdzielczość H3.
     *
     * Im wyższa rozdzielczość, tym mniejsza komórka.
     * Dla ride-sharingu resolution 8 jest rozsądnym punktem startowym,
     * bo daje obszary wystarczająco lokalne do matchingu i podaży/popytu,
     * ale nie rozbija miasta na zbyt drobne fragmenty.
     *
     * Produkcyjnie resolution może zależeć od miasta, gęstości ruchu
     * albo celu użycia, np. matching vs analytics.
     */
    private final int resolution;

    /**
     * Tworzy indexer H3 z rozdzielczością z konfiguracji.
     *
     * Property:
     * app.geo.h3-resolution
     *
     * Domyślnie:
     * 8
     *
     * Konstruktor może rzucić IOException, bo H3Core.newInstance()
     * inicjalizuje natywną/biblioteczną część H3.
     * Jeśli H3 nie wystartuje, aplikacja powinna nie uruchomić Location Service,
     * bo bez indexera nearby search będzie niespójny.
     */
    public H3GeoCellIndexer(
            @Value("${app.geo.h3-resolution:8}") int resolution
    ) throws IOException {
        this.h3 = H3Core.newInstance();
        this.resolution = resolution;
    }

    /**
     * Zamienia współrzędne geograficzne na adres komórki H3.
     *
     * Przykład:
     * lat/lng pickup pointu albo lokalizacji kierowcy
     * -> H3 cell używana jako klucz indeksu przestrzennego.
     *
     * Ten wynik może potem służyć do:
     * - zapisania kierowcy w mapie cell -> drivers,
     * - szukania kandydatów w tej samej komórce,
     * - agregacji popytu,
     * - surge pricingu per obszar.
     */
    @Override
    public String cell(double lat, double lng) {
        return h3.latLngToCellAddress(
                lat,
                lng,
                resolution
        );
    }

    /**
     * Zwraca komórkę bazową oraz sąsiednie komórki w promieniu gridowym.
     *
     * ringSize oznacza liczbę "pierścieni" wokół komórki startowej:
     * - 0: tylko komórka bazowa,
     * - 1: komórka bazowa + bezpośredni sąsiedzi,
     * - 2: szerszy obszar itd.
     *
     * W Location Service ta metoda jest naturalnym kandydatem do nearby search:
     * 1. Wyznaczamy cell dla pickup pointu.
     * 2. Pobieramy pobliskie komórki H3.
     * 3. Szukamy dostępnych kierowców zapisanych w tych komórkach.
     * 4. Dopiero potem sortujemy ich po dystansie/ETA.
     */
    @Override
    public List<String> nearbyCells(
            double lat,
            double lng,
            int ringSize
    ) {
        return h3.gridDisk(
                cell(lat, lng),
                ringSize
        );
    }
}