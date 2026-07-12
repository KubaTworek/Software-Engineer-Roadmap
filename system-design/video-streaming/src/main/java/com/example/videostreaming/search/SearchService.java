package com.example.videostreaming.search;

import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.catalog.VideoStatus;
import com.example.videostreaming.catalog.VideoVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

import static com.example.videostreaming.search.SearchDtos.*;

/**
 * Serwis wyszukiwania filmów.
 *
 * Główna odpowiedzialność:
 * - indeksowanie opublikowanych filmów w OpenSearch,
 * - wyszukiwanie filmów po tytule i opisie,
 * - filtrowanie wyników tylko do publicznych i opublikowanych treści,
 * - fallback do PostgreSQL, jeśli OpenSearch jest wyłączony albo niedostępny,
 * - cache'owanie wyników wyszukiwania.
 *
 * Ważne:
 * OpenSearch jest tutaj optymalizacją i warstwą search engine,
 * ale nie jest jedynym źródłem prawdy. Źródłem prawdy nadal jest baza danych
 * obsługiwana przez VideoRepository.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    /**
     * Konfiguracja wyszukiwarki.
     *
     * Zawiera m.in.:
     * - czy OpenSearch jest włączony,
     * - endpoint OpenSearch,
     * - nazwę indeksu.
     */
    private final SearchProperties props;

    /**
     * Klient HTTP do komunikacji z OpenSearch.
     *
     * Używany do:
     * - tworzenia indeksu,
     * - zapisywania dokumentów,
     * - wykonywania zapytań search.
     */
    private final RestClient restClient;

    /**
     * Repozytorium filmów.
     *
     * Używane jako fallback, gdy OpenSearch nie działa.
     * Dzięki temu wyszukiwanie w MVP nadal zwraca wyniki,
     * nawet jeśli search engine jest chwilowo niedostępny.
     */
    private final VideoRepository videos;

    public SearchService(SearchProperties props, RestClient.Builder builder, VideoRepository videos) {
        this.props = props;
        this.restClient = builder.baseUrl(props.endpoint()).build();
        this.videos = videos;
    }

    /**
     * Tworzy indeks OpenSearch, jeśli search engine jest włączony.
     *
     * Definiuje podstawowy mapping pól:
     * - title i description jako text, czyli pola do pełnotekstowego wyszukiwania,
     * - status i visibility jako keyword, czyli pola do dokładnego filtrowania,
     * - publishedAt jako date, czyli pole przydatne do sortowania/rankingu.
     *
     * Metoda ignoruje błąd tworzenia indeksu, bo:
     * - indeks może już istnieć,
     * - OpenSearch może jeszcze startować,
     * - aplikacja nie powinna przestać działać tylko dlatego,
     *   że search engine jest chwilowo niedostępny.
     */
    public void ensureIndex() {
        if (!props.enabled()) return;

        try {
            Map<String, Object> mapping = Map.of(
                    "mappings", Map.of("properties", Map.of(
                            "title", Map.of("type", "text"),
                            "description", Map.of("type", "text"),
                            "status", Map.of("type", "keyword"),
                            "visibility", Map.of("type", "keyword"),
                            "publishedAt", Map.of("type", "date")
                    ))
            );

            restClient.put()
                    .uri("/" + props.index())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapping)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.debug("Search index may already exist or OpenSearch is not ready: {}", ex.getMessage());
        }
    }

    /**
     * Indeksuje pojedynczy film w OpenSearch.
     *
     * Wywoływane po zmianach w katalogu, np.:
     * - po publikacji filmu,
     * - po zmianie tytułu/opisu,
     * - po zmianie danych wpływających na widoczność w search.
     *
     * Do indeksu trafiają tylko dane potrzebne do wyszukiwania i prezentacji wyniku.
     *
     * Ważne:
     * Błąd indeksowania nie przerywa głównej operacji biznesowej.
     * Jeśli film został zapisany w bazie, to baza nadal jest źródłem prawdy.
     * Search może zostać odbudowany później.
     */
    public void index(Video video) {
        if (!props.enabled()) return;

        try {
            ensureIndex();

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", video.getId().toString());
            doc.put("title", video.getTitle());
            doc.put("description", video.getDescription());
            doc.put("status", video.getStatus().name());
            doc.put("visibility", video.getVisibility().name());
            doc.put(
                    "publishedAt",
                    video.getPublishedAt() == null
                            ? Instant.now().toString()
                            : video.getPublishedAt().toString()
            );

            restClient.put()
                    .uri("/" + props.index() + "/_doc/" + video.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(doc)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Could not index video {}: {}", video.getId(), ex.getMessage());
        }
    }

    /**
     * Wyszukuje filmy po frazie użytkownika.
     *
     * Główna ścieżka:
     * - wysyła zapytanie do OpenSearch,
     * - szuka po tytule i opisie,
     * - tytuł ma większą wagę niż opis,
     * - zwraca tylko filmy PUBLISHED + PUBLIC.
     *
     * Fallback:
     * Jeśli OpenSearch jest wyłączony, niedostępny albo zapytanie jest puste,
     * metoda używa prostego wyszukiwania po PostgreSQL.
     *
     * Cache:
     * Wyniki są cache'owane po query + limit.
     * To zmniejsza liczbę zapytań do OpenSearch przy popularnych frazach.
     *
     * Limit:
     * Limit jest obcinany do zakresu 1–50, żeby endpoint nie zwracał
     * zbyt dużych odpowiedzi i nie generował ciężkich zapytań.
     */
    @Cacheable(cacheNames = "search", key = "#query + ':' + #limit")
    public SearchResponse search(String query, int limit) {
        if (!props.enabled() || query == null || query.isBlank()) {
            return fallback(query, limit);
        }

        try {
            Map<String, Object> request = Map.of(
                    "size", Math.min(Math.max(limit, 1), 50),
                    "query", Map.of("bool", Map.of(
                            "must", List.of(Map.of("multi_match", Map.of(
                                    "query", query,
                                    "fields", List.of("title^3", "description")
                            ))),
                            "filter", List.of(
                                    Map.of("term", Map.of("status", VideoStatus.PUBLISHED.name())),
                                    Map.of("term", Map.of("visibility", VideoVisibility.PUBLIC.name()))
                            )
                    ))
            );

            Map body = restClient.post()
                    .uri("/" + props.index() + "/_search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            List<SearchResult> results = parseSearchResponse(body);

            return new SearchResponse(query, results);
        } catch (Exception ex) {
            log.warn("OpenSearch unavailable, falling back to database search: {}", ex.getMessage());
            return fallback(query, limit);
        }
    }

    /**
     * Proste wyszukiwanie awaryjne po bazie danych.
     *
     * Używane, gdy:
     * - OpenSearch jest wyłączony,
     * - OpenSearch nie odpowiada,
     * - zapytanie jest puste.
     *
     * Ograniczenia:
     * To nie jest pełnoprawna wyszukiwarka.
     * Pobiera najnowsze publiczne filmy i filtruje je w pamięci po title/description.
     * Dobre dla MVP i lokalnego developmentu, ale nie dla dużej skali.
     *
     * Nadal zachowuje podstawową regułę bezpieczeństwa:
     * zwraca tylko filmy PUBLISHED + PUBLIC.
     */
    private SearchResponse fallback(String query, int limit) {
        String q = query == null ? "" : query.toLowerCase();

        List<SearchResult> results = videos.findByStatusAndVisibilityOrderByPublishedAtDesc(
                        VideoStatus.PUBLISHED,
                        VideoVisibility.PUBLIC,
                        org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 50))
                )
                .stream()
                .filter(v ->
                        q.isBlank()
                                || v.getTitle().toLowerCase().contains(q)
                                || (
                                v.getDescription() != null
                                        && v.getDescription().toLowerCase().contains(q)
                        )
                )
                .map(this::toResult)
                .toList();

        return new SearchResponse(query, results);
    }

    /**
     * Parsuje surową odpowiedź OpenSearch do DTO używanego przez API.
     *
     * OpenSearch zwraca generyczną strukturę JSON:
     * hits -> hits[] -> _source.
     *
     * Ta metoda wyciąga z niej tylko pola potrzebne aplikacji:
     * - id,
     * - title,
     * - description,
     * - publishedAt.
     *
     * Status i visibility są ustawiane jako PUBLISHED + PUBLIC,
     * bo zapytanie do OpenSearch już wcześniej filtruje wyniki do tych wartości.
     *
     * Niepoprawne rekordy są pomijane, żeby pojedynczy uszkodzony dokument
     * w indeksie nie psuł całej odpowiedzi search API.
     */
    @SuppressWarnings("unchecked")
    private List<SearchResult> parseSearchResponse(Map body) {
        if (body == null) return List.of();

        Object hitsObj = body.get("hits");
        if (!(hitsObj instanceof Map hits)) return List.of();

        Object arrObj = hits.get("hits");
        if (!(arrObj instanceof List<?> list)) return List.of();

        List<SearchResult> results = new ArrayList<>();

        for (Object item : list) {
            if (!(item instanceof Map hit)) continue;

            Object srcObj = hit.get("_source");
            if (!(srcObj instanceof Map source)) continue;

            try {
                UUID id = UUID.fromString(String.valueOf(source.get("id")));
                String title = String.valueOf(source.get("title"));
                String description = source.get("description") == null
                        ? null
                        : String.valueOf(source.get("description"));
                Instant publishedAt = source.get("publishedAt") == null
                        ? null
                        : Instant.parse(String.valueOf(source.get("publishedAt")));

                results.add(new SearchResult(
                        id,
                        title,
                        description,
                        VideoStatus.PUBLISHED,
                        VideoVisibility.PUBLIC,
                        publishedAt
                ));
            } catch (Exception ignored) {
                // Pomijamy pojedynczy błędny dokument z indeksu.
                // Search API powinno zwrócić pozostałe poprawne wyniki.
            }
        }

        return results;
    }

    /**
     * Mapuje encję Video z bazy danych na wynik wyszukiwania.
     *
     * Używane głównie w fallbacku do PostgreSQL.
     */
    private SearchResult toResult(Video video) {
        return new SearchResult(
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                video.getStatus(),
                video.getVisibility(),
                video.getPublishedAt()
        );
    }
}