package com.example.videostreaming.transcoding;

import com.example.videostreaming.messaging.EventPublisher;
import com.example.videostreaming.messaging.MessagingProperties;
import com.example.videostreaming.messaging.VideoEvents;
import com.example.videostreaming.storage.ObjectStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Worker odpowiedzialny za transkodowanie VOD.
 *
 * Główna odpowiedzialność:
 * - odbiera joby transkodowania z RabbitMQ,
 * - pobiera raw video z object storage,
 * - uruchamia FFmpeg,
 * - generuje HLS,
 * - uploaduje manifest i segmenty do storage,
 * - oznacza film jako READY,
 * - obsługuje retry i DLQ,
 * - wystawia metryki Prometheus/Micrometer.
 *
 * Ważne:
 * To jest komponent background worker, a nie część request-response API.
 * Dzięki temu upload i publikacja filmu nie blokują się na kosztownym transkodowaniu.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.transcoding",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TranscodingWorker {

    private static final Logger log = LoggerFactory.getLogger(TranscodingWorker.class);

    /**
     * Repozytorium jobów transkodowania.
     *
     * Job jest trwałym zapisem pracy do wykonania.
     * Dzięki temu można śledzić status, liczbę prób, błędy i DLQ.
     */
    private final TranscodingJobCoordinator coordinator;

    /**
     * Repozytorium filmów.
     *
     * Worker aktualizuje status filmu:
     * - PROCESSING podczas pracy,
     * - READY po poprawnym wygenerowaniu HLS,
     * - FAILED po przekroczeniu liczby prób.
     */
    /**
     * Dostęp do object storage.
     *
     * Worker używa go do:
     * - pobrania raw pliku,
     * - uploadu manifestu .m3u8,
     * - uploadu segmentów .ts.
     */
    private final ObjectStorageService storage;
    private final HlsArtifactPublisher artifactPublisher;

    /**
     * Konfiguracja transkodowania.
     *
     * Zawiera m.in.:
     * - ścieżkę do ffmpeg,
     * - katalog roboczy,
     * - długość segmentów HLS,
     * - flagę enabled.
     */
    private final TranscodingProperties props;

    /**
     * Konfiguracja kolejek i retry.
     *
     * Określa m.in. maksymalną liczbę prób
     * oraz bazowe opóźnienie przed ponownym wrzuceniem joba.
     */
    private final MessagingProperties messaging;

    /**
     * Publisher eventów.
     *
     * Używany do:
     * - ponownego opublikowania joba przy retry,
     * - wysłania eventu do DLQ po trwałej porażce.
     */
    private final EventPublisher publisher;

    /**
     * Licznik poprawnie zakończonych transkodowań.
     */
    private final Counter successCounter;

    /**
     * Licznik błędów transkodowania.
     *
     * Zwiększany przy każdej nieudanej próbie,
     * nie tylko przy finalnym przejściu do DLQ.
     */
    private final Counter failureCounter;

    /**
     * Licznik ponownych prób.
     */
    private final Counter retryCounter;

    /**
     * Licznik jobów przeniesionych do DLQ.
     */
    private final Counter dlqCounter;

    /**
     * Timer czasu trwania obsługi joba.
     *
     * Mierzy pełny czas processWithRetry,
     * czyli także czas samego transkodowania i ewentualnej obsługi błędu.
     */
    private final Timer durationTimer;

    public TranscodingWorker(TranscodingJobCoordinator coordinator,
                             ObjectStorageService storage,
                             HlsArtifactPublisher artifactPublisher,
                             TranscodingProperties props,
                             MessagingProperties messaging,
                             EventPublisher publisher,
                             MeterRegistry meterRegistry) {
        this.coordinator = coordinator;
        this.storage = storage;
        this.artifactPublisher = artifactPublisher;
        this.props = props;
        this.messaging = messaging;
        this.publisher = publisher;

        this.successCounter = Counter.builder("video_transcoding_success_total")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("video_transcoding_failure_total")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("video_transcoding_retry_total")
                .register(meterRegistry);
        this.dlqCounter = Counter.builder("video_transcoding_dlq_total")
                .register(meterRegistry);
        this.durationTimer = Timer.builder("video_transcoding_duration_seconds")
                .register(meterRegistry);
    }

    /**
     * Główne wejście workera.
     *
     * RabbitMQ dostarcza tutaj event TranscodingRequested.
     *
     * Jeden event oznacza:
     * "dla tego video i tego raw object key uruchom albo ponów transkodowanie".
     *
     * Transakcja obejmuje aktualizacje statusów w bazie.
     * Samo FFmpeg i operacje storage są operacjami zewnętrznymi,
     * więc nie są realnie cofane przez rollback transakcji DB.
     */
    @RabbitListener(queues = "${app.messaging.transcoding-queue}")
    public void handle(VideoEvents.TranscodingRequested event) {
        if (!props.enabled()) {
            log.info("Transcoding worker disabled, ignoring job {}", event.jobId());
            return;
        }

        /*
         * Mierzymy czas obsługi joba.
         *
         * Ta metryka jest kluczowa do autoskalowania workerów i wykrywania,
         * że transkodowanie zaczyna trwać zbyt długo.
         */
        durationTimer.record(() -> processWithRetry(event));
    }

    /**
     * Obsługuje pojedynczy job z retry i DLQ.
     *
     * Scenariusze:
     * - jeśli job jest już COMPLETED, ignorujemy event,
     * - jeśli process(job) się powiedzie, job i video są aktualizowane,
     * - jeśli process(job) rzuci wyjątek, decydujemy: retry albo DLQ.
     *
     * Idempotencja:
     * Sprawdzenie COMPLETED chroni przed ponownym wykonaniem joba,
     * np. po duplikacie eventu z kolejki.
     */
    private void processWithRetry(VideoEvents.TranscodingRequested event) {
        var claim = coordinator.claim(event.jobId());
        if (claim.isEmpty()) {
            log.info("Ignoring terminal or currently leased transcoding job {}", event.jobId());
            return;
        }

        TranscodingJobCoordinator.ClaimedJob job = claim.get();

        try {
            process(job);
        } catch (Exception ex) {
            String message = ex.getMessage() == null
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();

            log.error(
                    "Transcoding failed for job {} attempt {}/{}",
                    job.jobId(),
                    job.attempt(),
                    messaging.maxAttempts(),
                    ex
            );

            /*
             * Jeśli nie przekroczono limitu prób, zapisujemy status RETRYING
             * i publikujemy nowy event do kolejki.
             */
            if (job.attempt() < messaging.maxAttempts()) {
                coordinator.retry(job, message);

                retryCounter.increment();

                // Retry queue nie ma consumera: TTL odmierza opóźnienie, po czym
                // DLX przekazuje komunikat z powrotem do kolejki głównej.
                publisher.publishTranscodingRetry(
                        new VideoEvents.TranscodingRequested(
                                job.jobId(),
                                job.videoId(),
                                job.sourceObjectKey(),
                                job.attempt() + 1,
                                Instant.now()
                        )
                );
            } else {
                /*
                 * Po przekroczeniu limitu prób job trafia do DLQ,
                 * a film zostaje oznaczony jako FAILED.
                 *
                 * Dzięki temu admin widzi, że materiał wymaga interwencji,
                 * a system nie próbuje transkodować go w nieskończoność.
                 */
                coordinator.deadLetter(job, message);

                publisher.publishTranscodingDlq(
                        new VideoEvents.TranscodingRequested(
                                job.jobId(),
                                job.videoId(),
                                job.sourceObjectKey(),
                                job.attempt(),
                                Instant.now()
                        )
                );

                dlqCounter.increment();
            }

            failureCounter.increment();
        }
    }

    /**
     * Wykonuje właściwe transkodowanie.
     *
     * Flow:
     * 1. Tworzy katalog roboczy dla joba.
     * 2. Oznacza job jako RUNNING.
     * 3. Oznacza film jako PROCESSING.
     * 4. Pobiera raw video ze storage.
     * 5. Uruchamia FFmpeg i generuje HLS.
     * 6. Uploaduje manifest i segmenty do object storage.
     * 7. Ustawia video jako READY z kluczem master.m3u8.
     * 8. Oznacza job jako COMPLETED.
     * 9. Czyści lokalny katalog roboczy.
     *
     * Najważniejsze:
     * Po tej metodzie film ma gotowy manifest HLS,
     * który PlaybackService może później zwrócić klientowi.
     */
    private void process(TranscodingJobCoordinator.ClaimedJob job) throws Exception {
        UUID videoId = job.videoId();

        /*
         * Każdy job ma własny katalog roboczy.
         *
         * To zapobiega kolizjom plików, gdy działa kilka workerów równolegle.
         */
        Path jobDir = Path.of(props.workDir(), job.jobId().toString());
        Path input = jobDir.resolve("source");
        Path outputDir = jobDir.resolve("hls");

        try {
            Files.createDirectories(outputDir);

            log.info("Downloading raw video {} for job {}", job.sourceObjectKey(), job.jobId());

            /*
             * Pobieramy oryginalny plik wideo z object storage.
             *
             * Od tego momentu transkodowanie dzieje się lokalnie
             * w katalogu roboczym workera.
             */
            storage.download(job.sourceObjectKey(), input);

            /*
             * FFmpeg tworzy lokalny manifest master.m3u8
             * oraz segmenty HLS.
             */
            runFfmpeg(input, outputDir);

            /*
             * Gotowe assety publikujemy pod stabilnym prefixem:
             * videos/{videoId}/hls
             *
             * PlaybackService będzie później używał master.m3u8
             * jako głównego wejścia do odtwarzania.
             */
            String prefix = "videos/" + videoId + "/hls/" + job.jobId();

            /*
             * Segmenty i playlisty wariantów są publikowane przed masterem.
             * Master pełni rolę commit markera: jeśli jego upload się powiedzie,
             * wszystkie odwołania zostały wcześniej zweryfikowane i wysłane.
             */
            String masterKey = artifactPublisher.publishCompletePackage(outputDir, prefix);

            /*
             * READY oznacza, że film jest technicznie gotowy do publikacji.
             * To nie znaczy jeszcze, że jest widoczny publicznie.
             * Publiczność kontroluje osobny krok publish w katalogu.
             */
            coordinator.complete(job, masterKey);

            successCounter.increment();

            log.info("Transcoding completed for video {}", videoId);
        } finally {
            /*
             * Sprzątamy katalog roboczy niezależnie od wyniku.
             *
             * Bez tego worker szybko zapełniłby dysk plikami raw/segmentami.
             */
            deleteQuietly(jobDir);
        }
    }

    /**
     * Uruchamia FFmpeg i generuje HLS VOD.
     *
     * Wynik zawiera trzy playlisty wariantów i prawdziwy master manifest ABR.
     * Każda reprezentacja ma niezależne segmenty; player wybiera bitrate na
     * podstawie przepustowości i rozmiaru bufora.
     */
    private void runFfmpeg(Path input, Path outputDir) throws IOException, InterruptedException {
        Files.createDirectories(outputDir);
        for (HlsRenditionProfile profile : HlsRenditionProfile.educationalLadder()) {
            runFfmpegRendition(input, outputDir, profile);
        }

        StringBuilder master = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (HlsRenditionProfile profile : HlsRenditionProfile.educationalLadder()) {
            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(profile.videoBitrateKbps() * 1_000 + 128_000)
                    .append(",RESOLUTION=")
                    .append(profile.width()).append('x').append(profile.height())
                    .append(",CODECS=\"avc1.4d401f,mp4a.40.2\"\n")
                    .append(profile.name()).append("/index.m3u8\n");
        }
        Files.writeString(outputDir.resolve("master.m3u8"), master, StandardCharsets.UTF_8);
    }

    private void runFfmpegRendition(Path input, Path outputDir, HlsRenditionProfile profile)
            throws IOException, InterruptedException {
        Path renditionDirectory = outputDir.resolve(profile.name());
        Files.createDirectories(renditionDirectory);

        List<String> command = List.of(
                props.ffmpegPath(), "-y",
                "-i", input.toString(),

                /*
                 * Kodowanie video.
                 *
                 * h264 jest szeroko wspierany przez przeglądarki,
                 * mobile i smart TV.
                 */
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-b:v", profile.videoBitrateKbps() + "k",
                "-maxrate", (int) (profile.videoBitrateKbps() * 1.07) + "k",
                "-bufsize", (profile.videoBitrateKbps() * 2) + "k",
                "-vf", "scale=w=" + profile.width() + ":h=" + profile.height()
                        + ":force_original_aspect_ratio=decrease,"
                        + "pad=" + profile.width() + ":" + profile.height() + ":(ow-iw)/2:(oh-ih)/2",

                /*
                 * Kodowanie audio.
                 *
                 * AAC jest standardowym wyborem dla HLS.
                 */
                "-c:a", "aac",
                "-b:a", "128k",

                /*
                 * Długość segmentu HLS.
                 *
                 * Krótsze segmenty dają szybszą reakcję playera,
                 * dłuższe segmenty zmniejszają liczbę requestów do CDN.
                 */
                "-hls_time", String.valueOf(props.hlsSegmentSeconds()),

                /*
                 * VOD playlist oznacza zamknięty, skończony materiał,
                 * w przeciwieństwie do live playlist.
                 */
                "-hls_playlist_type", "vod",

                /*
                 * Wzorzec nazw segmentów.
                 */
                "-hls_segment_filename",
                renditionDirectory.resolve("segment_%05d.ts").toString(),

                /*
                 * Główny manifest HLS.
                 */
                renditionDirectory.resolve("index.m3u8").toString()
        );

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .inheritIO()
                .start();

        int exit = process.waitFor();

        if (exit != 0) {
            throw new IllegalStateException("ffmpeg exited with code " + exit);
        }
    }

    /**
     * Usuwa katalog roboczy joba.
     *
     * Pliki są usuwane w odwrotnej kolejności,
     * żeby najpierw usunąć pliki, a dopiero potem katalogi.
     *
     * Błędy sprzątania są ignorowane, bo nie powinny zmieniać wyniku joba.
     * Produkcyjnie warto je logować, żeby wykrywać problemy z dyskiem.
     */
    private void deleteQuietly(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Ignorujemy błąd usuwania pojedynczego pliku.
                        }
                    });
                }
            }
        } catch (Exception ignored) {
            // Ignorujemy błąd sprzątania całego katalogu.
        }
    }
}
