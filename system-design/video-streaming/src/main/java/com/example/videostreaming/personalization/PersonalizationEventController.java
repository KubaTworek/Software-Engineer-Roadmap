package com.example.videostreaming.personalization;

import com.example.videostreaming.auth.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Kontroler zbierający eventy personalizacji.
 *
 * Główna odpowiedzialność:
 * - przyjmuje eventy zachowania użytkownika,
 * - przypisuje event do aktualnie zalogowanego usera,
 * - zapisuje event do repozytorium,
 * - zwraca eventId i status accepted.
 *
 * Te eventy są później używane przez:
 * - feature store,
 * - rekomendacje,
 * - trending,
 * - ranking homepage,
 * - A/B testing analytics,
 * - lokalny data warehouse.
 *
 * Przykładowe eventy:
 * - view,
 * - playback_start,
 * - playback_complete,
 * - search_click,
 * - recommendation_click,
 * - like,
 * - add_to_watchlist.
 *
 * Ważne:
 * To są eventy produktowe/personalizacyjne, nie techniczne QoE.
 * QoE, czyli buforowanie, bitrate i błędy playera, jest obsługiwane osobnym pipeline'em.
 */
@RestController
@RequestMapping("/api/personalization/events")
public class PersonalizationEventController {

    /**
     * Repozytorium eventów personalizacji.
     *
     * W tej wersji zapisuje event bezpośrednio do bazy.
     *
     * Produkcyjnie przy dużym wolumenie lepiej wysyłać eventy do kolejki
     * albo event streamingu, np. Kafka/PubSub/Kinesis,
     * a dopiero consumer zapisywałby je do warehouse.
     */
    private final PersonalizationEventRepository events;

    public PersonalizationEventController(PersonalizationEventRepository events) {
        this.events = events;
    }

    /**
     * Przyjmuje pojedynczy event personalizacji z klienta.
     *
     * Flow:
     * 1. Klient wysyła event zachowania użytkownika.
     * 2. Backend bierze userId z AuthenticationPrincipal.
     * 3. Jeśli request nie ma eventId, backend generuje UUID.
     * 4. Event jest zapisywany w repozytorium.
     * 5. API zwraca eventId i status accepted.
     *
     * eventId może pochodzić z klienta.
     * To pomaga przy retry — ten sam event można rozpoznać i deduplikować,
     * jeśli repozytorium ma constraint po eventId.
     *
     * userId nie pochodzi z request body.
     * Dzięki temu klient nie może zapisywać eventów jako inny użytkownik.
     */
    @PostMapping
    public TrackEventResponse track(@AuthenticationPrincipal User user,
                                    @Valid @RequestBody TrackEventRequest request) {
        /*
         * Jeśli klient nie poda eventId, generujemy go po stronie backendu.
         *
         * Przy aplikacjach mobilnych/webowych warto jednak generować eventId
         * już po stronie klienta, żeby retry po timeoutach nie tworzyły duplikatów.
         */
        UUID id = request.eventId() == null
                ? UUID.randomUUID()
                : request.eventId();

        /*
         * Zapisujemy surowy event personalizacji.
         *
         * Najważniejsze pola:
         * - userId: kto wykonał akcję,
         * - eventType: co zrobił,
         * - videoId: jakiej treści dotyczyło zdarzenie,
         * - sessionId: w ramach jakiej sesji,
         * - source: skąd przyszedł event, np. home, search, recommendations,
         * - deviceType/country: kontekst użytkownika,
         * - attributes: dodatkowe dane specyficzne dla eventu,
         * - occurredAt: kiedy event faktycznie wydarzył się po stronie klienta.
         */
        events.save(
                id,
                user.getId(),
                request.eventType(),
                request.videoId(),
                request.sessionId(),
                request.source(),
                request.deviceType(),
                request.country(),
                request.attributes(),
                request.occurredAt()
        );

        /*
         * accepted oznacza, że event został przyjęty przez API.
         *
         * W obecnej wersji zapis jest synchroniczny, więc event jest już zapisany.
         * Gdyby endpoint publikował event do kolejki, status mógłby być np. queued.
         */
        return new TrackEventResponse(id, "accepted");
    }
}