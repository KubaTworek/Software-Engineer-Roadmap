package com.example.videostreaming.live;

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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Worker odpowiedzialny za techniczne przetwarzanie transmisji live.
 *
 * Główna odpowiedzialność:
 * - odbiera event startu live z kolejki RabbitMQ,
 * - uruchamia osobny proces FFmpeg dla transmisji,
 * - generuje HLS live playlist i segmenty,
 * - cyklicznie uploaduje HLS do object storage,
 * - obsługuje recording live, jeśli jest włączony,
 * - odbiera event stopu live,
 * - aktualizuje status transmisji w bazie,
 * - wystawia metryki startów, stopów, błędów i czasu sesji.
 *
 * Ważne:
 * Ten worker wykonuje ciężką pracę poza API.
 * LiveStreamService tylko publikuje event start/stop,
 * a tutaj uruchamiany jest realny pipeline FFmpeg.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.live",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LiveTranscodingWorker {

    private static final Logger log = LoggerFactory.getLogger(LiveTranscodingWorker.class);

    /**
     * Repozytorium transmisji live.
     *
     * Worker odczytuje konfigurację streamu i aktualizuje status:
     * - LIVE po starcie FFmpeg,
     * - ENDED po zatrzymaniu,
     * - FAILED po błędzie.
     */
    private final LiveStreamRepository streams;

    /**
     * Serwis storage/CDN.
     *
     * Używany do uploadu:
     * - playlisty HLS .m3u8,
     * - segmentów .ts,
     * - nagrania MP4 dla live-to-VOD.
     */
    private final ObjectStorageService storage;

    /**
     * Konfiguracja live pipeline'u.
     *
     * Zawiera m.in.:
     * - ścieżkę do FFmpeg,
     * - katalog roboczy,
     * - długość segmentów standardowych i low-latency,
     * - flagę enabled.
     */
    private final LiveProperties props;

    /**
     * Executor uruchamiający aktywne sesje live w osobnych wątkach.
     *
     * Każda transmisja live ma swój długotrwały proces FFmpeg,
     * dlatego nie może blokować wątku RabbitListenera.
     */
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Mapa aktywnych transmisji obsługiwanych przez tę instancję workera.
     *
     * Klucz: liveStreamId.
     * Wartość: Future procesu sesji + czas startu.
     *
     * Uwaga:
     * To działa lokalnie per instancja. W produkcji przy wielu workerach
     * potrzebny byłby distributed lock/lease, żeby dwa workery nie uruchomiły
     * tej samej transmisji równolegle.
     */
    private final Map<UUID, RunningLiveSession> running = new ConcurrentHashMap<>();

    /**
     * Licznik startów live pipeline'u.
     */
    private final Counter startCounter;

    /**
     * Licznik żądań zatrzymania live.
     */
    private final Counter stopCounter;

    /**
     * Licznik błędów sesji live.
     */
    private final Counter failureCounter;

    /**
     * Timer mierzący czas trwania sesji live.
     */
    private final Timer sessionTimer;

    public LiveTranscodingWorker(LiveStreamRepository streams,
                                 ObjectStorageService storage,
                                 LiveProperties props,
                                 MeterRegistry registry) {
        this.streams = streams;
        this.storage = storage;
        this.props = props;

        this.startCounter = Counter.builder("video_live_start_total")
                .register(registry);

        this.stopCounter = Counter.builder("video_live_stop_total")
                .register(registry);

        this.failureCounter = Counter.builder("video_live_failure_total")
                .register(registry);

        this.sessionTimer = Timer.builder("video_live_session_seconds")
                .register(registry);
    }

    /**
     * Obsługuje event startu transmisji live.
     *
     * Flow:
     * 1. RabbitMQ dostarcza LiveStartRequested.
     * 2. Worker sprawdza, czy live worker jest włączony.
     * 3. Worker sprawdza, czy ta transmisja już działa na tej instancji.
     * 4. Worker pobiera LiveStream z bazy.
     * 5. Worker uruchamia runSession(...) w osobnym wątku.
     * 6. Zapisuje Future w mapie running.
     *
     * Kluczowe:
     * RabbitListener nie powinien sam odpalać długiego procesu FFmpeg,
     * bo zablokowałby konsumpcję kolejki.
     */
    @RabbitListener(queues = "${app.messaging.live-start-queue}")
    public void start(VideoEvents.LiveStartRequested event) {
        if (!props.enabled()) {
            log.info("Live worker disabled; ignoring start for {}", event.liveStreamId());
            return;
        }

        /*
         * Idempotencja na poziomie jednej instancji workera.
         *
         * Jeśli ta instancja już obsługuje dany stream,
         * nie uruchamiamy drugiego FFmpeg.
         */
        if (running.containsKey(event.liveStreamId())) {
            log.info("Live stream {} is already running on this worker", event.liveStreamId());
            return;
        }

        LiveStream stream = streams.findById(event.liveStreamId()).orElseThrow();

        Future<?> future = executor.submit(() -> runSession(stream.getId()));

        running.put(stream.getId(), new RunningLiveSession(future, Instant.now()));

        startCounter.increment();
    }

    /**
     * Obsługuje event zatrzymania transmisji live.
     *
     * Flow:
     * 1. Usuwa sesję z mapy running.
     * 2. Jeśli sesja działa na tej instancji, anuluje jej Future.
     * 3. Mierzy czas trwania sesji.
     * 4. Oznacza stream jako ENDED.
     * 5. Zwiększa metrykę stopów.
     *
     * Uwaga:
     * Future.cancel(true) przerywa wątek, ale sam proces FFmpeg jest realnie
     * domykany w finally w runSession przez destroyForcibly().
     */
    @RabbitListener(queues = "${app.messaging.live-stop-queue}")
    @Transactional
    public void stop(VideoEvents.LiveStopRequested event) {
        RunningLiveSession session = running.remove(event.liveStreamId());

        if (session != null) {
            session.future().cancel(true);
            sessionTimer.record(java.time.Duration.between(session.startedAt(), Instant.now()));
        }

        streams.findById(event.liveStreamId()).ifPresent(stream -> {
            stream.markEnded();
            streams.save(stream);
        });

        stopCounter.increment();

        log.info("Stop requested for live stream {}", event.liveStreamId());
    }

    /**
     * Uruchamia pełną sesję live dla jednej transmisji.
     *
     * Flow:
     * 1. Tworzy katalog roboczy.
     * 2. Pobiera konfigurację streamu z bazy.
     * 3. Uruchamia FFmpeg na internal ingest URL.
     * 4. Oznacza stream jako LIVE i zapisuje HLS master key.
     * 5. Co sekundę synchronizuje lokalne pliki HLS do storage.
     * 6. Po zakończeniu FFmpeg dosyła ostatnie segmenty.
     * 7. Jeśli recording jest włączony, uploaduje MP4 recording.
     * 8. Oznacza stream jako ENDED albo FAILED.
     * 9. Czyści katalog roboczy.
     *
     * To jest długotrwała metoda działająca w osobnym wątku.
     */
    private void runSession(UUID liveStreamId) {
        Path sessionDir = Path.of(props.workDir(), liveStreamId.toString());
        Process process = null;

        try {
            LiveStream stream = streams.findById(liveStreamId).orElseThrow();

            Path hlsDir = sessionDir.resolve("hls");
            Files.createDirectories(hlsDir);

            /*
             * Prefix, pod którym będą publikowane live assety:
             * live/{liveStreamId}/hls/index.m3u8
             * live/{liveStreamId}/hls/segment_000001.ts
             */
            String hlsPrefix = "live/" + liveStreamId + "/hls";
            String masterKey = hlsPrefix + "/index.m3u8";

            /*
             * Jeśli recording jest włączony, zapisujemy docelowy object key
             * nagrania MP4. Ten plik może potem służyć do live-to-VOD.
             */
            String recordingKey = stream.isRecordingEnabled()
                    ? "live/" + liveStreamId + "/recording/source.mp4"
                    : null;

            process = startFfmpeg(stream, hlsDir);

            /*
             * Od tego momentu stream jest widoczny jako LIVE.
             *
             * Playback endpoint może zacząć zwracać CDN URL do index.m3u8.
             */
            markLive(liveStreamId, masterKey, recordingKey);

            /*
             * Dopóki FFmpeg działa, regularnie synchronizujemy HLS do storage.
             *
             * To proste MVP live origin:
             * FFmpeg pisze lokalnie, worker uploaduje zmiany do object storage,
             * a player/CDN czyta z publicznego CDN URL.
             */
            while (!Thread.currentThread().isInterrupted() && process.isAlive()) {
                syncHlsDirectory(hlsDir, hlsPrefix);
                sleep(1000);
            }

            int exit = process.waitFor();

            /*
             * Po zakończeniu procesu robimy finalny sync,
             * żeby dosłać ostatnie segmenty i najnowszą playlistę.
             */
            syncHlsDirectory(hlsDir, hlsPrefix);

            /*
             * Jeśli nagrywanie było włączone i FFmpeg utworzył recording.mp4,
             * uploadujemy go do storage.
             */
            if (stream.isRecordingEnabled()) {
                Path recording = sessionDir.resolve("recording.mp4");

                if (Files.exists(recording)) {
                    storage.uploadFile(recordingKey, recording, "video/mp4");
                }
            }

            /*
             * exit == 0 oznacza normalne zakończenie FFmpeg.
             *
             * Thread interrupted oznacza zwykle stop zainicjowany przez API.
             */
            if (exit == 0 || Thread.currentThread().isInterrupted()) {
                markEnded(liveStreamId);
            } else {
                markFailed(liveStreamId, "ffmpeg exited with code " + exit);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            log.info("Live session {} interrupted; marking ended", liveStreamId);

            markEnded(liveStreamId);
        } catch (Exception ex) {
            failureCounter.increment();

            log.error("Live session {} failed", liveStreamId, ex);

            markFailed(
                    liveStreamId,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            );
        } finally {
            /*
             * Jeśli proces FFmpeg nadal żyje, ubijamy go.
             *
             * To zabezpiecza przed zombie procesami po stopie lub błędzie.
             */
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }

            running.remove(liveStreamId);

            /*
             * Sprzątamy lokalne pliki HLS i recording.
             * Bez tego worker z czasem zapełniłby dysk.
             */
            deleteQuietly(sessionDir);
        }
    }

    /**
     * Buduje i uruchamia komendę FFmpeg dla live streamu.
     *
     * Wejście:
     * - internalIngestUrl, np. RTMP endpoint z NGINX RTMP.
     *
     * Wyjście:
     * - live HLS playlist index.m3u8,
     * - segmenty .ts,
     * - opcjonalnie recording.mp4 dla live-to-VOD.
     *
     * Tryb low latency:
     * - używa krótszych segmentów,
     * - wymusza tune=zerolatency,
     * - ale to nadal nie jest pełne LL-HLS z partial segments.
     */
    private Process startFfmpeg(LiveStream stream, Path hlsDir) throws IOException {
        int segmentSeconds = stream.getLatencyMode() == LiveLatencyMode.LOW_LATENCY
                ? props.lowLatencySegmentSeconds()
                : props.standardSegmentSeconds();

        /*
         * hls_list_size kontroluje długość playlisty live.
         *
         * Jeśli DVR jest włączony, lista obejmuje okno DVR.
         * Jeśli DVR jest wyłączony, trzymamy krótką listę ostatnich segmentów.
         */
        int listSize = stream.isDvrEnabled()
                ? Math.max(3, stream.getDvrWindowSeconds() / Math.max(segmentSeconds, 1))
                : 6;

        /*
         * Flagi HLS:
         * - delete_segments: usuwa stare segmenty lokalnie,
         * - append_list: dopisuje do playlisty przy DVR,
         * - program_date_time: dodaje timestampi do playlisty,
         * - independent_segments: segmenty mogą być dekodowane niezależnie.
         */
        String hlsFlags = stream.isDvrEnabled()
                ? "delete_segments+append_list+program_date_time+independent_segments"
                : "delete_segments+program_date_time+independent_segments";

        Path recording = Path.of(props.workDir(), stream.getId().toString(), "recording.mp4");
        Path playlist = hlsDir.resolve("index.m3u8");
        Path segmentPattern = hlsDir.resolve("segment_%06d.ts");

        java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of(
                props.ffmpegPath(), "-y",
                "-i", stream.getInternalIngestUrl(),
                "-map", "0:v:0", "-map", "0:a:0?",
                "-c:v", "h264", "-preset", "veryfast", "-tune", "zerolatency",
                "-c:a", "aac", "-b:a", "128k",
                "-f", "hls",
                "-hls_time", String.valueOf(segmentSeconds),
                "-hls_list_size", String.valueOf(listSize),
                "-hls_flags", hlsFlags,
                "-hls_segment_filename", segmentPattern.toString(),
                playlist.toString()
        ));

        /*
         * Jeśli recording jest włączony, FFmpeg ma dwa outputy:
         * - HLS live,
         * - MP4 recording.
         *
         * To pozwala później użyć recording.mp4 jako źródła dla live-to-VOD.
         *
         * Uwaga:
         * To MVP. Produkcyjnie lepiej rozdzielić live packaging i recording
         * bardziej świadomie, np. osobny pipeline albo poprawnie skonfigurowane
         * multi-output z kontrolą błędów.
         */
        if (stream.isRecordingEnabled()) {
            command = new java.util.ArrayList<>(List.of(
                    props.ffmpegPath(), "-y",
                    "-i", stream.getInternalIngestUrl(),
                    "-map", "0:v:0", "-map", "0:a:0?",
                    "-c:v", "h264", "-preset", "veryfast", "-tune", "zerolatency",
                    "-c:a", "aac", "-b:a", "128k",
                    "-f", "hls",
                    "-hls_time", String.valueOf(segmentSeconds),
                    "-hls_list_size", String.valueOf(listSize),
                    "-hls_flags", hlsFlags,
                    "-hls_segment_filename", segmentPattern.toString(),
                    playlist.toString(),
                    "-map", "0:v:0", "-map", "0:a:0?",
                    "-c:v", "h264", "-c:a", "aac", "-movflags", "+faststart",
                    recording.toString()
            ));
        }

        log.info("Starting live ffmpeg for stream {}: {}", stream.getId(), String.join(" ", command));

        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .inheritIO()
                .start();
    }

    /**
     * Synchronizuje lokalny katalog HLS do object storage.
     *
     * Worker cyklicznie wywołuje tę metodę podczas transmisji,
     * żeby nowe segmenty i aktualna playlista były dostępne przez CDN.
     *
     * Content-Type:
     * - .m3u8 => application/vnd.apple.mpegurl,
     * - .ts   => video/mp2t.
     */
    private void syncHlsDirectory(Path dir, String prefix) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }

        try (var files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();

                String type = name.endsWith(".m3u8")
                        ? "application/vnd.apple.mpegurl"
                        : "video/mp2t";

                storage.uploadFile(prefix + "/" + name, file, type);
            }
        }
    }

    /**
     * Oznacza stream jako LIVE i zapisuje klucze assetów.
     *
     * masterKey wskazuje na playlistę HLS.
     * recordingKey wskazuje na przyszłe nagranie MP4, jeśli recording jest włączony.
     */
    @Transactional
    protected void markLive(UUID id, String masterKey, String recordingKey) {
        streams.findById(id).ifPresent(stream -> {
            stream.markLive(masterKey, recordingKey);
            streams.save(stream);
        });
    }

    /**
     * Oznacza stream jako ENDED.
     *
     * Jeśli stream jest już VOD_READY, nie cofamy statusu do ENDED.
     */
    @Transactional
    protected void markEnded(UUID id) {
        streams.findById(id).ifPresent(stream -> {
            if (stream.getStatus() != LiveStatus.VOD_READY) {
                stream.markEnded();
            }

            streams.save(stream);
        });
    }

    /**
     * Oznacza stream jako FAILED i zapisuje ostatni błąd.
     *
     * Dzięki temu admin widzi, dlaczego live pipeline się wywrócił.
     */
    @Transactional
    protected void markFailed(UUID id, String error) {
        streams.findById(id).ifPresent(stream -> {
            stream.markFailed(error);
            streams.save(stream);
        });
    }

    /**
     * Krótki sleep między synchronizacjami HLS.
     *
     * Jeśli wątek zostanie przerwany, ustawiamy ponownie flagę interrupt,
     * żeby pętla runSession mogła się zakończyć.
     */
    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Usuwa lokalny katalog sesji live.
     *
     * Pliki są usuwane w odwrotnej kolejności:
     * najpierw pliki, potem katalogi.
     *
     * Błędy są ignorowane, bo cleanup nie powinien zmieniać wyniku sesji.
     * Produkcyjnie warto takie błędy logować przynajmniej na debug/warn.
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

    /**
     * Informacja o aktywnej sesji live na tej instancji workera.
     *
     * future:
     * - uchwyt do zadania uruchomionego w ExecutorService.
     *
     * startedAt:
     * - czas startu sesji, używany do metryki video_live_session_seconds.
     */
    private record RunningLiveSession(Future<?> future, Instant startedAt) {}
}