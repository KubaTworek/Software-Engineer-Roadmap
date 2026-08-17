package com.example.videostreaming.qoe;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.messaging.EventPublisher;
import com.example.videostreaming.messaging.VideoEvents;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

import static com.example.videostreaming.qoe.QoeDtos.*;

/**
 * Kontroler ingestowania eventów QoE.
 *
 * QoE = Quality of Experience.
 *
 * Ten endpoint służy do zbierania technicznych informacji z playera,
 * np. o starcie odtwarzania, buforowaniu, bitrate, błędach CDN,
 * urządzeniu i sesji playbacku.
 *
 * Główna odpowiedzialność:
 * - przyjąć event z klienta,
 * - przypisać go do aktualnie zalogowanego użytkownika,
 * - nadać eventId, jeśli klient go nie dostarczył,
 * - opublikować event do kolejki/analityki,
 * - szybko odpowiedzieć klientowi statusem QUEUED.
 *
 * Ważne:
 * Kontroler nie zapisuje eventu bezpośrednio do bazy.
 * Publikuje go asynchronicznie, żeby nie spowalniać playera.
 */
@RestController
@RequestMapping("/api/qoe/events")
public class QoeController {

    /**
     * Publisher eventów aplikacyjnych.
     *
     * W tym przypadku wysyła QoePlaybackEvent do kolejki,
     * gdzie może zostać przetworzony przez osobny consumer:
     * - zapis do tabel analitycznych,
     * - agregacje QoE,
     * - alerty,
     * - dashboardy,
     * - ranking CDN,
     * - rekomendacje lub feature store.
     */
    private final EventPublisher publisher;

    public QoeController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Przyjmuje pojedynczy event QoE z aplikacji klienckiej.
     *
     * Typowe eventy:
     * - playback_started,
     * - first_frame,
     * - rebuffer_started,
     * - rebuffer_ended,
     * - bitrate_changed,
     * - playback_error,
     * - playback_completed.
     *
     * Flow:
     * 1. Klient wysyła event QoE z playera.
     * 2. Backend ustala eventId.
     * 3. Backend bierze userId z aktualnej sesji/autoryzacji.
     * 4. Backend publikuje event do kolejki.
     * 5. Klient dostaje szybką odpowiedź QUEUED.
     *
     * Dlaczego asynchronicznie:
     * Eventów QoE może być bardzo dużo.
     * Nie powinny blokować playbacku ani obciążać głównej ścieżki API.
     */
    @PostMapping
    public IngestQoeResponse ingest(@Valid @RequestBody IngestQoeRequest request,
                                    @AuthenticationPrincipal User user) {
        /*
         * eventId może zostać dostarczony przez klienta.
         *
         * To pomaga przy retry po stronie aplikacji mobilnej/webowej:
         * jeśli klient wyśle ten sam event ponownie, downstream może go deduplikować.
         *
         * Jeśli klient nie poda eventId, backend generuje nowe UUID.
         */
        UUID eventId = request.eventId() == null
                ? UUID.randomUUID()
                : request.eventId();

        /*
         * Publikujemy event QoE do kolejki.
         *
         * userId pochodzi z AuthenticationPrincipal, a nie z request body.
         * Dzięki temu klient nie może podszyć eventu pod innego użytkownika.
         */
        publisher.publishQoe(new VideoEvents.QoePlaybackEvent(
                eventId,
                user.getId(),
                request.videoId(),
                request.sessionId(),
                request.eventType(),
                request.startupTimeMs(),
                request.rebufferTimeMs(),
                request.bitrateKbps(),
                request.cdnProvider(),
                request.player(),
                request.deviceType(),
                request.country(),
                request.attributes(),
                request.occurredAt() == null ? Instant.now() : request.occurredAt()
        ));

        /*
         * QUEUED oznacza, że API przyjęło event i przekazało go do pipeline'u.
         *
         * Nie oznacza jeszcze, że event został zapisany w warehouse
         * albo uwzględniony w agregacjach.
         */
        return new IngestQoeResponse(eventId, "QUEUED");
    }
}