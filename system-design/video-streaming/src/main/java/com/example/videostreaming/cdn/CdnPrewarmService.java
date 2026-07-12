package com.example.videostreaming.cdn;

import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.catalog.VideoStatus;
import com.example.videostreaming.storage.ObjectStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za pre-warming CDN.
 *
 * Pre-warming oznacza wcześniejsze odpytanie CDN o manifest i segmenty wideo,
 * zanim zrobią to realni użytkownicy.
 *
 * Cel:
 * - wypełnić cache CDN,
 * - ograniczyć cold start popularnych materiałów,
 * - zmniejszyć nagły ruch do origin storage,
 * - poprawić start playbacku przy premierach i filmach trendingowych.
 *
 * Ważne:
 * To nie jest wymagane do poprawnego działania playbacku.
 * To optymalizacja wydajnościowa.
 */
@Service
public class CdnPrewarmService {

    private static final Logger log = LoggerFactory.getLogger(CdnPrewarmService.class);

    /**
     * Repozytorium filmów.
     *
     * Używane do pobrania statusu filmu i klucza manifestu HLS.
     */
    private final VideoRepository videos;

    /**
     * Serwis object storage.
     *
     * Używany do:
     * - listowania wszystkich assetów HLS pod prefixem filmu,
     * - budowania URL-i CDN dla konkretnych object key.
     */
    private final ObjectStorageService storage;

    /**
     * Konfiguracja CDN.
     *
     * Zawiera m.in.:
     * - czy pre-warming jest włączony,
     * - ile popularnych filmów rozgrzewać,
     * - ile maksymalnie obiektów rozgrzewać per film,
     * - interwał schedulera.
     */
    private final CdnProperties props;

    /**
     * Dostęp do SQL.
     *
     * Tutaj używany do prostego zapytania po qoe_events,
     * żeby wybrać najpopularniejsze filmy z ostatnich 24 godzin.
     *
     * To jest pragmatyczne MVP.
     * Produkcyjnie popularność zwykle pochodziłaby z agregacji w warehouse
     * albo osobnego serwisu rankingowego.
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Klient HTTP używany do wykonania requestów HEAD do CDN.
     *
     * HEAD wystarcza do rozgrzania wielu CDN-ów,
     * bo CDN musi sprawdzić/pobrać obiekt z origin,
     * ale nie pobieramy pełnego body segmentu do aplikacji.
     */
    private final RestClient restClient;

    /**
     * Licznik requestów pre-warmingu.
     *
     * Mierzy liczbę prób rozgrzania pojedynczych obiektów CDN,
     * nie liczbę filmów.
     */
    private final Counter prewarmRequests;

    /**
     * Licznik nieudanych requestów pre-warmingu.
     *
     * Wysoka wartość może oznaczać:
     * - błędny CDN base URL,
     * - brak assetów w storage,
     * - problem z origin,
     * - błędne content-type/permissions,
     * - niedostępność CDN.
     */
    private final Counter prewarmFailures;

    public CdnPrewarmService(VideoRepository videos,
                             ObjectStorageService storage,
                             CdnProperties props,
                             JdbcTemplate jdbcTemplate,
                             RestClient.Builder builder,
                             MeterRegistry registry) {
        this.videos = videos;
        this.storage = storage;
        this.props = props;
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = builder.build();

        this.prewarmRequests = Counter.builder("video_cdn_prewarm_requests_total")
                .register(registry);

        this.prewarmFailures = Counter.builder("video_cdn_prewarm_failures_total")
                .register(registry);
    }

    /**
     * Uruchamia pre-warming asynchronicznie.
     *
     * Przydatne, gdy inny komponent chce zainicjować pre-warming,
     * ale nie powinien czekać na przejście po wszystkich segmentach.
     *
     * Przykład:
     * - po publikacji filmu,
     * - przed premierą,
     * - po wykryciu wzrostu popularności.
     *
     * Uwaga:
     * @Async wymaga włączonego async processingu w konfiguracji Springa.
     */
    @Async
    public void prewarmAsync(UUID videoId) {
        prewarm(videoId);
    }

    /**
     * Cyklicznie rozgrzewa CDN dla popularnych filmów.
     *
     * Popularność jest liczona na podstawie liczby eventów QoE
     * z ostatnich 24 godzin.
     *
     * To prosta heurystyka:
     * więcej eventów QoE zwykle oznacza więcej odtworzeń lub aktywniejsze oglądanie.
     *
     * Flow:
     * 1. Scheduler odpala się co app.cdn.popular-prewarm-interval-ms.
     * 2. Jeśli pre-warming jest wyłączony, nic nie robi.
     * 3. Pobiera najpopularniejsze video_id z qoe_events.
     * 4. Dla każdego filmu uruchamia prewarm(id).
     *
     * Uwaga:
     * W systemie z wieloma instancjami aplikacji ten scheduler odpali się
     * na każdej instancji. Produkcyjnie potrzebny byłby distributed lock,
     * np. ShedLock, leader election albo osobny scheduler jobów.
     */
    @Scheduled(fixedDelayString = "${app.cdn.popular-prewarm-interval-ms}")
    public void prewarmPopularVideos() {
        if (!props.prewarmEnabled()) {
            return;
        }

        List<UUID> ids = jdbcTemplate.query(
                """
                SELECT video_id
                FROM qoe_events
                WHERE occurred_at > now() - interval '24 hours'
                GROUP BY video_id
                ORDER BY count(*) DESC
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getObject("video_id", UUID.class),
                props.popularPrewarmLimit()
        );

        /*
         * Każdy film obsługujemy niezależnie.
         *
         * Błąd pre-warmingu jednego filmu nie powinien zatrzymać
         * rozgrzewania pozostałych popularnych materiałów.
         */
        ids.forEach(id -> {
            try {
                prewarm(id);
            } catch (Exception ex) {
                log.warn("Popular video prewarm failed for {}: {}", id, ex.getMessage());
            }
        });
    }

    /**
     * Wykonuje właściwy pre-warming CDN dla jednego filmu.
     *
     * Flow:
     * 1. Pobiera film z bazy.
     * 2. Sprawdza, czy film jest READY albo PUBLISHED.
     * 3. Pobiera prefix HLS na podstawie master.m3u8.
     * 4. Listuje obiekty HLS w storage.
     * 5. Buduje CDN URL dla każdego obiektu.
     * 6. Wysyła HEAD request do CDN.
     * 7. Zwraca listę wyników dla diagnostyki.
     *
     * READY jest dozwolone, bo pre-warming może być wykonany przed publikacją,
     * np. kilka minut przed premierą.
     *
     * PUBLISHED jest dozwolone, bo można rozgrzewać już publiczne i popularne filmy.
     */
    public List<String> prewarm(UUID videoId) {
        Video video = videos.findById(videoId).orElseThrow();

        /*
         * Nie rozgrzewamy filmów, które nie mają jeszcze gotowych assetów.
         *
         * Jeśli film jest np. UPLOADING albo PROCESSING,
         * segmenty HLS mogą jeszcze nie istnieć.
         */
        if (video.getStatus() != VideoStatus.PUBLISHED && video.getStatus() != VideoStatus.READY) {
            throw new IllegalStateException("Video must be READY or PUBLISHED before CDN pre-warming");
        }

        /*
         * Brak master manifestu oznacza, że nie ma czego rozgrzewać.
         *
         * Może się zdarzyć przy niepełnym transkodowaniu albo uszkodzonym stanie danych.
         */
        if (video.getHlsMasterObjectKey() == null) {
            return List.of();
        }

        /*
         * Prefix HLS wyliczamy z klucza manifestu.
         *
         * Przykład:
         * videos/{videoId}/hls/master.m3u8
         *
         * Po usunięciu /master.m3u8 dostajemy:
         * videos/{videoId}/hls
         *
         * Pod tym prefixem znajdują się manifest i segmenty.
         */
        String prefix = video.getHlsMasterObjectKey().replace("/master.m3u8", "");

        List<String> warmed = new ArrayList<>();

        for (String key : storage.listObjectKeys(prefix)) {
            /*
             * Ograniczamy liczbę obiektów per film.
             *
             * To chroni system przed przypadkiem, gdzie długi film ma tysiące segmentów
             * i jeden pre-warming robi zbyt dużo requestów naraz.
             */
            if (warmed.size() >= props.prewarmMaxObjects()) {
                break;
            }

            String url = storage.cdnUrl(key);

            try {
                prewarmRequests.increment();

                /*
                 * HEAD request do CDN.
                 *
                 * Celem nie jest pobranie całego segmentu do aplikacji,
                 * tylko zmuszenie CDN do sprawdzenia albo załadowania obiektu.
                 *
                 * Dla części CDN-ów lepsze może być GET z Range albo dedykowane API
                 * providera. HEAD jest najprostszy i tani dla MVP.
                 */
                ResponseEntity<Void> response = restClient.method(HttpMethod.HEAD)
                        .uri(url)
                        .retrieve()
                        .toBodilessEntity();

                warmed.add(url + " -> " + response.getStatusCode());
            } catch (Exception ex) {
                prewarmFailures.increment();

                log.warn("CDN prewarm failed for {}: {}", url, ex.getMessage());

                /*
                 * Nie przerywamy całego pre-warmingu przez jeden uszkodzony obiekt.
                 *
                 * Zwracamy FAILED dla diagnostyki i idziemy dalej.
                 */
                warmed.add(url + " -> FAILED");
            }
        }

        return warmed;
    }
}