package com.example.videostreaming.live;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.messaging.EventPublisher;
import com.example.videostreaming.messaging.VideoEvents;
import com.example.videostreaming.storage.ObjectStorageService;
import com.example.videostreaming.transcoding.TranscodingJob;
import com.example.videostreaming.transcoding.TranscodingJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Serwis domenowy odpowiedzialny za cykl życia transmisji live.
 *
 * Obsługuje:
 * - tworzenie transmisji i generowanie stream key,
 * - aktualizację konfiguracji live,
 * - start/stop transmisji przez eventy do workerów,
 * - zwracanie playback URL do HLS,
 * - konwersję nagrania live do zwykłego VOD.
 *
 * Ważne:
 * Ta klasa nie uruchamia FFmpeg bezpośrednio.
 * Start i stop live są zlecane asynchronicznie przez kolejkę/eventy.
 */
@Service
public class LiveStreamService {

    /**
     * Repozytorium transmisji live.
     *
     * Jest głównym źródłem prawdy dla:
     * - statusu transmisji,
     * - stream key,
     * - ingest URL,
     * - HLS manifest key,
     * - DVR/recording settings,
     * - powiązanego VOD.
     */
    private final LiveStreamRepository streams;

    /**
     * Repozytorium katalogu VOD.
     *
     * Używane tylko wtedy, gdy zakończony live ma zostać
     * przekształcony w normalny materiał VOD.
     */
    private final VideoRepository videos;

    /**
     * Repozytorium jobów transkodowania VOD.
     *
     * Live-to-VOD korzysta z istniejącego pipeline'u VOD:
     * tworzymy Video + TranscodingJob i publikujemy standardowy event.
     */
    private final TranscodingJobRepository jobs;

    /**
     * Publisher eventów do kolejek.
     *
     * Używany do:
     * - zlecenia startu live pipeline'u,
     * - zlecenia zatrzymania live pipeline'u,
     * - zlecenia transkodowania nagrania live jako VOD.
     */
    private final EventPublisher publisher;

    /**
     * Serwis storage/CDN.
     *
     * W tej klasie używany głównie do zbudowania publicznego CDN URL
     * na podstawie object key manifestu HLS.
     */
    private final ObjectStorageService storage;

    /**
     * Konfiguracja live streamingu.
     *
     * Zawiera m.in.:
     * - publiczny ingest base URL,
     * - wewnętrzny ingest base URL,
     * - domyślne i maksymalne DVR window,
     * - flagę live-to-VOD.
     */
    private final LiveProperties props;

    public LiveStreamService(LiveStreamRepository streams,
                             VideoRepository videos,
                             TranscodingJobRepository jobs,
                             EventPublisher publisher,
                             ObjectStorageService storage,
                             LiveProperties props) {
        this.streams = streams;
        this.videos = videos;
        this.jobs = jobs;
        this.publisher = publisher;
        this.storage = storage;
        this.props = props;
    }

    /**
     * Tworzy nową transmisję live.
     *
     * Flow:
     * 1. Generuje stream key dla encodera.
     * 2. Ustawia DVR window w bezpiecznym zakresie.
     * 3. Ustawia domyślnie DVR i recording na true, jeśli request ich nie określa.
     * 4. Ustawia tryb latencji, domyślnie STANDARD.
     * 5. Buduje publiczny i wewnętrzny ingest URL.
     * 6. Zapisuje stream w statusie początkowym, zwykle SCHEDULED.
     *
     * Stream key trafia do ingest URL-a i jest używany przez OBS/FFmpeg
     * do wysyłania transmisji RTMP.
     */
    @Transactional
    public LiveStream create(LiveDtos.CreateLiveRequest request, User owner) {
        String key = generateStreamKey();

        int dvrWindow = normalizeDvrWindow(request.dvrWindowSeconds());

        boolean dvrEnabled = request.dvrEnabled() == null || request.dvrEnabled();
        boolean recordingEnabled = request.recordingEnabled() == null || request.recordingEnabled();

        LiveLatencyMode mode = request.latencyMode() == null
                ? LiveLatencyMode.STANDARD
                : request.latencyMode();

        LiveStream stream = new LiveStream(
                request.title(),
                request.description(),
                owner,
                mode,
                key,
                props.publicIngestBaseUrl().replaceAll("/$", "") + "/" + key,
                props.internalIngestBaseUrl().replaceAll("/$", "") + "/" + key,
                dvrEnabled,
                dvrWindow,
                recordingEnabled
        );

        return streams.save(stream);
    }

    /**
     * Aktualizuje konfigurację transmisji live.
     *
     * Nie pozwalamy edytować aktywnego streamu, czyli statusów:
     * - STARTING,
     * - LIVE,
     * - STOPPING.
     *
     * Powód:
     * zmiana DVR, recording albo latency mode w trakcie działania pipeline'u
     * mogłaby rozjechać stan bazy z realnie działającym FFmpeg/live workerem.
     */
    @Transactional
    public LiveStream update(UUID id, LiveDtos.UpdateLiveRequest request) {
        LiveStream stream = streams.findById(id).orElseThrow();

        if (stream.getStatus() == LiveStatus.LIVE
                || stream.getStatus() == LiveStatus.STARTING
                || stream.getStatus() == LiveStatus.STOPPING) {
            throw new IllegalStateException("Live stream cannot be edited while active");
        }

        stream.update(
                request.title(),
                request.description(),
                request.latencyMode(),
                request.dvrEnabled(),
                request.dvrWindowSeconds(),
                request.recordingEnabled()
        );

        return streams.save(stream);
    }

    /**
     * Startuje transmisję live.
     *
     * Ta metoda tylko zmienia status na STARTING i publikuje event.
     * Faktyczne uruchomienie FFmpeg/live pipeline'u wykonuje worker.
     *
     * Dozwolone statusy startowe:
     * - SCHEDULED: normalny start zaplanowanej transmisji,
     * - FAILED: ponowna próba po błędzie,
     * - ENDED: ponowne uruchomienie zakończonego streamu, jeśli system to dopuszcza.
     *
     * Idempotencja:
     * jeśli stream jest już STARTING albo LIVE, metoda zwraca obecny stan.
     */
    @Transactional
    public LiveStream start(UUID id) {
        LiveStream stream = streams.findById(id).orElseThrow();

        if (stream.getStatus() == LiveStatus.LIVE || stream.getStatus() == LiveStatus.STARTING) {
            return stream;
        }

        if (stream.getStatus() != LiveStatus.SCHEDULED
                && stream.getStatus() != LiveStatus.FAILED
                && stream.getStatus() != LiveStatus.ENDED) {
            throw new IllegalStateException("Live stream cannot be started from status " + stream.getStatus());
        }

        stream.markStarting();
        streams.save(stream);

        publisher.publishLiveStart(
                new VideoEvents.LiveStartRequested(
                        stream.getId(),
                        stream.getStreamKey(),
                        Instant.now()
                )
        );

        return stream;
    }

    /**
     * Zatrzymuje transmisję live.
     *
     * Metoda oznacza stream jako STOPPING i publikuje event do workera.
     * Worker powinien zatrzymać proces FFmpeg, domknąć playlistę HLS
     * i ewentualnie zapisać recordingObjectKey.
     *
     * Idempotencja:
     * jeśli stream jest już ENDED albo VOD_READY, nie robimy nic.
     */
    @Transactional
    public LiveStream stop(UUID id) {
        LiveStream stream = streams.findById(id).orElseThrow();

        if (stream.getStatus() == LiveStatus.ENDED || stream.getStatus() == LiveStatus.VOD_READY) {
            return stream;
        }

        stream.markStopping();
        streams.save(stream);

        publisher.publishLiveStop(
                new VideoEvents.LiveStopRequested(
                        stream.getId(),
                        Instant.now()
                )
        );

        return stream;
    }

    /**
     * Pobiera transmisję po ID.
     *
     * Używane przez kontroler do szczegółów streamu.
     * Brak streamu kończy się wyjątkiem z repozytorium.
     */
    @Transactional(readOnly = true)
    public LiveStream get(UUID id) {
        return streams.findById(id).orElseThrow();
    }

    /**
     * Zwraca dane playbacku dla playera.
     *
     * Stream jest odtwarzalny tylko w statusach:
     * - LIVE: transmisja trwa,
     * - ENDED: transmisja zakończona, ale HLS/DVR może być jeszcze dostępny,
     * - VOD_READY: stream ma już powiązany materiał VOD.
     *
     * playbackUrl jest budowany na podstawie hlsMasterObjectKey.
     * Jeśli worker nie ustawił jeszcze manifestu, URL będzie null.
     *
     * expiresAt ma krótki TTL i reprezentuje ważność odpowiedzi playbackowej.
     * Produkcyjnie tutaj można dodać signed cookies, signed URLs albo token playbacku.
     */
    @Transactional(readOnly = true)
    public LiveDtos.LivePlaybackResponse playback(UUID id) {
        LiveStream stream = streams.findById(id).orElseThrow();

        if (stream.getStatus() != LiveStatus.LIVE
                && stream.getStatus() != LiveStatus.ENDED
                && stream.getStatus() != LiveStatus.VOD_READY) {
            throw new IllegalStateException("Live stream is not playable yet");
        }

        String playbackUrl = stream.getHlsMasterObjectKey() == null
                ? null
                : storage.cdnUrl(stream.getHlsMasterObjectKey());

        return new LiveDtos.LivePlaybackResponse(
                stream.getId(),
                playbackUrl,
                stream.getStatus(),
                stream.getLatencyMode(),
                stream.isDvrEnabled(),
                stream.getDvrWindowSeconds(),
                stream.getLatencyMode() == LiveLatencyMode.LOW_LATENCY,
                Instant.now().plusSeconds(900).toString()
        );
    }

    /**
     * Konwertuje zakończoną transmisję live do VOD.
     *
     * Flow:
     * 1. Sprawdza, czy live-to-VOD jest włączone.
     * 2. Sprawdza, czy live worker zapisał recordingObjectKey.
     * 3. Jeśli VOD już istnieje, zwraca istniejące powiązanie.
     * 4. Tworzy nowy rekord Video.
     * 5. Oznacza Video jako uploaded z recordingObjectKey.
     * 6. Tworzy TranscodingJob.
     * 7. Publikuje standardowy event TranscodingRequested.
     * 8. Podpina utworzony Video do LiveStream.
     *
     * Najważniejsze:
     * Live-to-VOD nie ma osobnego transkodera.
     * Wykorzystuje istniejący pipeline VOD, co upraszcza system.
     */
    @Transactional
    public LiveDtos.LiveToVodResponse convertToVod(UUID id, User owner) {
        LiveStream stream = streams.findById(id).orElseThrow();

        if (!props.liveToVodEnabled()) {
            throw new IllegalStateException("Live-to-VOD is disabled");
        }

        if (stream.getRecordingObjectKey() == null || stream.getRecordingObjectKey().isBlank()) {
            throw new IllegalStateException("No recording object is available for this stream");
        }

        if (stream.getVodVideo() != null) {
            return new LiveDtos.LiveToVodResponse(
                    stream.getId(),
                    stream.getVodVideo().getId(),
                    stream.getStatus().name()
            );
        }

        Video video = new Video(
                stream.getTitle(),
                stream.getDescription(),
                owner
        );

        /*
         * Nagranie live traktujemy jak raw upload VOD.
         * Dalej przejmuje to zwykły TranscodingWorker VOD.
         */
        video.markUploaded(stream.getRecordingObjectKey());

        video = videos.save(video);

        TranscodingJob job = jobs.save(
                new TranscodingJob(video, stream.getRecordingObjectKey())
        );

        publisher.publishTranscodingRequested(
                new VideoEvents.TranscodingRequested(
                        job.getId(),
                        video.getId(),
                        stream.getRecordingObjectKey(),
                        1,
                        Instant.now()
                )
        );

        stream.attachVod(video);
        streams.save(stream);

        return new LiveDtos.LiveToVodResponse(
                stream.getId(),
                video.getId(),
                "TRANSCODING_REQUESTED"
        );
    }

    /**
     * Zwraca stronicowaną listę transmisji.
     *
     * Jeśli status jest podany, filtrujemy po statusie.
     * Jeśli status jest null, zwracamy wszystkie streamy.
     *
     * page jest zabezpieczony przed wartościami ujemnymi.
     * size jest ograniczony do 1–100, żeby nie pozwolić na ciężkie odpowiedzi API.
     */
    @Transactional(readOnly = true)
    public Page<LiveStream> list(LiveStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(size, 1), 100)
        );

        return status == null
                ? streams.findAll(pageable)
                : streams.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Mapuje encję LiveStream na DTO odpowiedzi API.
     *
     * Zwraca:
     * - metadane live,
     * - status,
     * - latency mode,
     * - ingest URL i stream key,
     * - DVR/recording settings,
     * - playback URL,
     * - powiązany VOD,
     * - timestamp startu/końca,
     * - ostatni błąd.
     *
     * Uwaga:
     * streamKey jest sekretem ingestu.
     * W MVP zwracanie go jest wygodne dla panelu admina,
     * ale w publicznych endpointach produkcyjnych nie powinien być ujawniany.
     */
    public LiveDtos.LiveStreamResponse toResponse(LiveStream s) {
        String playbackUrl = s.getHlsMasterObjectKey() == null
                ? null
                : storage.cdnUrl(s.getHlsMasterObjectKey());

        UUID vodId = s.getVodVideo() == null
                ? null
                : s.getVodVideo().getId();

        return new LiveDtos.LiveStreamResponse(
                s.getId(),
                s.getTitle(),
                s.getDescription(),
                s.getStatus(),
                s.getLatencyMode(),
                s.getIngestUrl(),
                s.getStreamKey(),
                s.isDvrEnabled(),
                s.getDvrWindowSeconds(),
                s.isRecordingEnabled(),
                playbackUrl,
                vodId,
                s.getStartedAt(),
                s.getEndedAt(),
                s.getLastError()
        );
    }

    /**
     * Normalizuje DVR window do bezpiecznego zakresu.
     *
     * Jeśli request nie poda wartości, używana jest wartość domyślna.
     * Minimum to 30 sekund.
     * Maksimum pochodzi z konfiguracji.
     *
     * To chroni system przed przypadkiem, gdzie klient ustawi ogromne DVR window
     * i wymusi przechowywanie zbyt wielu segmentów live.
     */
    private int normalizeDvrWindow(Integer requested) {
        int value = requested == null
                ? props.defaultDvrWindowSeconds()
                : requested;

        return Math.max(30, Math.min(value, props.maxDvrWindowSeconds()));
    }

    /**
     * Generuje stream key dla ingestu.
     *
     * Stream key jest częścią URL-a, na który encoder publikuje transmisję.
     *
     * W MVP bazuje na UUID zakodowanym jako Base64 URL-safe.
     * Produkcyjnie lepiej użyć SecureRandom, dłuższego sekretu,
     * rotacji stream key i osobnej kontroli dostępu do ingestu.
     */
    private String generateStreamKey() {
        byte[] bytes = UUID.randomUUID()
                .toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes)
                .substring(0, 24);
    }
}