package com.example.videostreaming.upload;

import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.messaging.EventPublisher;
import com.example.videostreaming.messaging.VideoEvents;
import com.example.videostreaming.storage.ObjectStorageService;
import com.example.videostreaming.storage.StorageProperties;
import com.example.videostreaming.transcoding.TranscodingJob;
import com.example.videostreaming.transcoding.TranscodingJobRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static com.example.videostreaming.upload.UploadDtos.*;

/**
 * Kontroler uploadu plików źródłowych dla filmu.
 *
 * Główna odpowiedzialność:
 * - utworzyć rekord uploadu,
 * - wygenerować signed URL do object storage,
 * - oznaczyć film jako UPLOADING / UPLOADED / PROCESSING,
 * - po zakończeniu uploadu utworzyć job transkodowania,
 * - opublikować event do kolejki workerów.
 *
 * Ważne:
 * Plik wideo NIE przechodzi przez backend aplikacyjny.
 * Backend tylko wydaje signed URL, a klient/admin uploaduje plik bezpośrednio
 * do object storage, np. MinIO/S3.
 */
@RestController
@RequestMapping("/api/videos/{videoId}/uploads")
@PreAuthorize("hasRole('ADMIN')")
public class UploadController {

    /**
     * Repozytorium filmów.
     *
     * Używane do sprawdzenia, czy film istnieje,
     * oraz do zmiany statusu filmu w trakcie procesu uploadu.
     */
    private final VideoRepository videos;

    /**
     * Repozytorium uploadów.
     *
     * Przechowuje techniczne informacje o konkretnym uploadzie:
     * object key, oryginalną nazwę pliku, content type, rozmiar i status.
     */
    private final UploadRepository uploads;

    /**
     * Repozytorium jobów transkodowania.
     *
     * Po potwierdzeniu uploadu zapisujemy job,
     * który później zostanie wykonany przez worker transkodujący.
     */
    private final TranscodingJobRepository jobs;

    /**
     * Abstrakcja nad object storage.
     *
     * Tutaj generowany jest signed PUT URL,
     * pod który klient może wysłać plik bezpośrednio do storage.
     */
    private final ObjectStorageService storage;

    /**
     * Konfiguracja storage.
     *
     * Używana m.in. do określenia czasu ważności signed URL-a.
     */
    private final StorageProperties props;

    /**
     * Publisher eventów aplikacyjnych.
     *
     * Po zakończeniu uploadu publikuje event TranscodingRequested,
     * który trafia do kolejki obsługiwanej przez workery.
     */
    private final EventPublisher publisher;

    public UploadController(VideoRepository videos,
                            UploadRepository uploads,
                            TranscodingJobRepository jobs,
                            ObjectStorageService storage,
                            StorageProperties props,
                            EventPublisher publisher) {
        this.videos = videos;
        this.uploads = uploads;
        this.jobs = jobs;
        this.storage = storage;
        this.props = props;
        this.publisher = publisher;
    }

    /**
     * Inicjuje upload pliku źródłowego dla konkretnego filmu.
     *
     * Flow:
     * 1. Admin wskazuje videoId i metadane pliku.
     * 2. System sprawdza, czy film istnieje.
     * 3. System generuje bezpieczny object key w storage.
     * 4. System zapisuje rekord Upload w bazie.
     * 5. Film dostaje status UPLOADING.
     * 6. API zwraca signed PUT URL.
     *
     * Klient/admin po tej odpowiedzi powinien wykonać HTTP PUT bezpośrednio
     * na zwrócony URL, wysyłając tam plik wideo.
     *
     * Dlaczego tak:
     * Backend nie powinien przyjmować wielogigabajtowych plików.
     * To odciąża aplikację i pozwala skalować upload przez object storage.
     */
    @PostMapping
    public CreateUploadResponse create(@PathVariable UUID videoId,
                                       @Valid @RequestBody CreateUploadRequest request) throws Exception {
        Video video = videos.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        /*
         * Oczyszczamy nazwę pliku z niebezpiecznych znaków.
         *
         * Oryginalna nazwa może pochodzić od użytkownika/admina,
         * więc nie powinna być używana bezpośrednio jako część ścieżki w storage.
         */
        String safeFilename = request.filename().replaceAll("[^a-zA-Z0-9._-]", "_");

        /*
         * Generujemy unikalny object key dla raw video.
         *
         * Struktura:
         * raw/{videoId}/{randomUUID}-{filename}
         *
         * Dzięki temu:
         * - łatwo znaleźć pliki źródłowe danego filmu,
         * - unikamy kolizji nazw,
         * - oddzielamy raw uploady od gotowych assetów HLS/DASH.
         */
        String objectKey = "raw/" + videoId + "/" + UUID.randomUUID() + "-" + safeFilename;

        /*
         * Zapisujemy rekord uploadu przed faktycznym wysłaniem pliku.
         *
         * Ten rekord reprezentuje zamiar uploadu i pozwala później sprawdzić,
         * czy complete dotyczy poprawnego pliku i filmu.
         */
        Upload upload = uploads.save(
                new Upload(
                        video,
                        objectKey,
                        request.filename(),
                        request.contentType(),
                        request.sizeBytes()
                )
        );

        /*
         * Film przechodzi w status UPLOADING.
         *
         * Na tym etapie plik jeszcze nie musi istnieć w storage.
         * System wie tylko, że rozpoczęto proces uploadu.
         */
        video.markUploading();
        videos.save(video);

        /*
         * Zwracamy signed URL do bezpośredniego uploadu.
         *
         * Metoda HTTP to PUT, bo klient ma wysłać cały obiekt
         * pod wskazany object key.
         */
        return new CreateUploadResponse(
                upload.getId(),
                videoId,
                objectKey,
                storage.presignedPutUrl(objectKey),
                "PUT",
                props.presignedUploadExpiryMinutes()
        );
    }

    /**
     * Potwierdza zakończenie uploadu i uruchamia transkodowanie.
     *
     * Ten endpoint powinien być wywołany dopiero po tym,
     * jak klient/admin poprawnie wyśle plik do signed URL-a.
     *
     * Flow:
     * 1. Sprawdzenie filmu.
     * 2. Sprawdzenie uploadu.
     * 3. Walidacja, czy upload należy do tego filmu.
     * 4. Oznaczenie uploadu jako completed.
     * 5. Oznaczenie filmu jako UPLOADED.
     * 6. Oznaczenie filmu jako PROCESSING.
     * 7. Utworzenie joba transkodowania.
     * 8. Publikacja eventu TranscodingRequested do kolejki.
     *
     * Po tej operacji API nie transkoduje pliku synchronicznie.
     * Transkodowanie wykonuje osobny worker.
     */
    @PostMapping("/{uploadId}/complete")
    public CompleteUploadResponse complete(@PathVariable UUID videoId,
                                           @PathVariable UUID uploadId) {
        Video video = videos.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        Upload upload = uploads.findById(uploadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found"));

        /*
         * Chronimy przed sytuacją, w której ktoś próbuje zakończyć upload
         * przypisany do innego filmu.
         *
         * To ważne, bo videoId i uploadId są parametrami URL-a.
         */
        if (!upload.getVideo().getId().equals(video.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload does not belong to video");
        }

        /*
         * Oznaczamy upload jako zakończony.
         *
         * W pełniejszej wersji warto tutaj dodatkowo sprawdzić,
         * czy obiekt faktycznie istnieje w storage i czy jego rozmiar/checksum
         * zgadza się z deklaracją z CreateUploadRequest.
         */
        upload.complete();
        uploads.save(upload);

        /*
         * Aktualizujemy status filmu.
         *
         * UPLOADED oznacza, że raw plik został zgłoszony jako dostępny.
         * PROCESSING oznacza, że film czeka na transkodowanie albo jest już
         * przetwarzany przez worker.
         */
        video.markUploaded(upload.getObjectKey());
        video.markProcessing();
        videos.save(video);

        /*
         * Tworzymy trwały rekord joba transkodowania.
         *
         * To ważne, bo event w kolejce nie powinien być jedynym śladem pracy.
         * Job w bazie pozwala śledzić status, retry, błędy i historię przetwarzania.
         */
        TranscodingJob job = jobs.save(
                new TranscodingJob(video, upload.getObjectKey())
        );

        /*
         * Publikujemy event do kolejki.
         *
         * Worker transkodujący odbierze ten event, pobierze raw plik ze storage,
         * wygeneruje HLS/DASH i zaktualizuje status filmu po zakończeniu pracy.
         *
         * Wartość attempt=1 oznacza pierwszą próbę przetwarzania.
         */
        publisher.publishTranscodingRequested(
                new VideoEvents.TranscodingRequested(
                        job.getId(),
                        video.getId(),
                        upload.getObjectKey(),
                        1,
                        Instant.now()
                )
        );

        return new CompleteUploadResponse(
                upload.getId(),
                video.getId(),
                "COMPLETED",
                "QUEUED"
        );
    }
}