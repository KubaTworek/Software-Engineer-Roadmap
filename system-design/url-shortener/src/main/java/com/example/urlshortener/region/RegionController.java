package com.example.urlshortener.region;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kontroler REST zwracający informacje o aktualnym regionie aplikacji.
 *
 * <p>
 * Ten endpoint jest przydatny w architekturze multi-region, gdzie ta sama aplikacja
 * może działać równolegle w kilku regionach, np.:
 * </p>
 *
 * <ul>
 *     <li>{@code eu-central-1},</li>
 *     <li>{@code us-east-1},</li>
 *     <li>{@code ap-southeast-1}.</li>
 * </ul>
 *
 * <p>
 * Endpoint pozwala szybko sprawdzić, w jakim regionie działa dana instancja,
 * czy region jest primary, czy przyjmuje zapisy oraz jakie TTL-e są używane
 * dla edge cache.
 * </p>
 *
 * <p>
 * Może być wykorzystywany przez:
 * </p>
 *
 * <ul>
 *     <li>operatorów systemu,</li>
 *     <li>monitoring,</li>
 *     <li>testy smoke/regionalne,</li>
 *     <li>debugowanie ruchu między regionami,</li>
 *     <li>load balancer lub gateway do diagnostyki.</li>
 * </ul>
 *
 * <p>
 * Wszystkie endpointy tej klasy mają prefiks:
 * </p>
 *
 * <pre>
 * /api/v1/region
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/region")
public class RegionController {

    /**
     * Konfiguracja regionu aplikacji.
     *
     * <p>
     * Obiekt {@link RegionProperties} przechowuje ustawienia związane z multi-region,
     * np.:
     * </p>
     *
     * <ul>
     *     <li>identyfikator bieżącego regionu,</li>
     *     <li>identyfikator regionu primary,</li>
     *     <li>czy system działa w trybie active-active,</li>
     *     <li>czy bieżący region może przyjmować zapisy,</li>
     *     <li>TTL dla pozytywnych lookupów edge,</li>
     *     <li>TTL dla negatywnych lookupów edge.</li>
     * </ul>
     */
    private final RegionProperties regionProperties;

    /**
     * Konstruktor kontrolera.
     *
     * <p>
     * Spring wstrzykuje {@link RegionProperties} przez constructor injection.
     * Dzięki temu kontroler nie musi samodzielnie odczytywać konfiguracji
     * z plików YAML/properties.
     * </p>
     *
     * @param regionProperties konfiguracja aktualnego regionu aplikacji
     */
    public RegionController(RegionProperties regionProperties) {
        this.regionProperties = regionProperties;
    }

    /**
     * Zwraca informacje o aktualnym regionie aplikacji.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * GET /api/v1/region
     * </pre>
     *
     * <p>
     * Przykładowa odpowiedź:
     * </p>
     *
     * <pre>
     * {
     *   "regionId": "eu-central-1",
     *   "primaryRegion": "eu-central-1",
     *   "activeActive": false,
     *   "acceptsWrites": true,
     *   "edgeCacheTtlSeconds": 300,
     *   "negativeCacheTtlSeconds": 30
     * }
     * </pre>
     *
     * <p>
     * Znaczenie pól:
     * </p>
     *
     * <ul>
     *     <li>
     *         {@code regionId} — identyfikator regionu, w którym działa ta instancja.
     *     </li>
     *     <li>
     *         {@code primaryRegion} — region uznawany za primary, czyli główny region
     *         obsługujący zapisy w trybie active-passive.
     *     </li>
     *     <li>
     *         {@code activeActive} — informacja, czy system działa w trybie multi-write.
     *         Jeśli {@code true}, wiele regionów może przyjmować zapisy.
     *     </li>
     *     <li>
     *         {@code acceptsWrites} — informacja, czy bieżący region przyjmuje operacje
     *         zapisu. Zwykle {@code true}, jeśli region jest primary albo jeśli system
     *         działa w trybie active-active.
     *     </li>
     *     <li>
     *         {@code edgeCacheTtlSeconds} — TTL w sekundach dla pozytywnych odpowiedzi
     *         edge cache, czyli dla istniejących i aktywnych linków.
     *     </li>
     *     <li>
     *         {@code negativeCacheTtlSeconds} — TTL w sekundach dla negatywnych odpowiedzi
     *         edge cache, np. link nie istnieje, wygasł albo jest zablokowany.
     *     </li>
     * </ul>
     *
     * @return mapa z informacjami o aktualnym regionie
     */
    @GetMapping
    public Map<String, Object> region() {

        /*
         * Zwracamy prostą mapę zamiast dedykowanego DTO.
         *
         * To jest szybkie i wygodne dla endpointu diagnostycznego.
         * Przy bardziej rozbudowanym API lepiej byłoby jednak użyć jawnego recordu,
         * np. RegionResponse, żeby mieć silniejsze typowanie i czytelniejszy kontrakt.
         */
        return Map.of(
                /*
                 * Identyfikator bieżącego regionu aplikacji.
                 *
                 * Przykład:
                 * eu-central-1
                 * us-east-1
                 */
                "regionId", regionProperties.getRegionId(),

                /*
                 * Region primary.
                 *
                 * W trybie active-passive tylko primary powinien przyjmować zapisy.
                 */
                "primaryRegion", regionProperties.getPrimaryRegion(),

                /*
                 * Informacja, czy system działa w trybie active-active.
                 *
                 * Jeśli true, wiele regionów może przyjmować zapisy.
                 * Jeśli false, zapisy powinny trafiać tylko do primary region.
                 */
                "activeActive", regionProperties.isActiveActive(),

                /*
                 * Informacja, czy bieżący region akceptuje zapisy.
                 *
                 * W typowej implementacji isPrimaryRegion() może zwracać true wtedy, gdy:
                 *
                 * - regionId == primaryRegion,
                 * - albo activeActive == true.
                 *
                 * Warto sprawdzić implementację RegionProperties, bo nazwa metody
                 * isPrimaryRegion() może sugerować wyłącznie porównanie regionId z primaryRegion.
                 */
                "acceptsWrites", regionProperties.isPrimaryRegion(),

                /*
                 * TTL pozytywnego cache edge w sekundach.
                 *
                 * Dotyczy aktywnych, poprawnych linków.
                 */
                "edgeCacheTtlSeconds", regionProperties.getEdgeCacheTtl().toSeconds(),

                /*
                 * TTL negatywnego cache edge w sekundach.
                 *
                 * Dotyczy np. 404/410, jeśli edge albo globalny handler wspiera
                 * cache'owanie negatywnych odpowiedzi.
                 */
                "negativeCacheTtlSeconds", regionProperties.getNegativeCacheTtl().toSeconds()
        );
    }
}