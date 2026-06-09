package pl.jakubtworek.chatsystem.presence;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za logikę presence użytkowników.
 *
 * Presence w aplikacji czatu oznacza informację:
 * - czy użytkownik jest aktualnie ONLINE,
 * - czy użytkownik jest OFFLINE,
 * - kiedy był ostatnio widziany.
 *
 * Ten serwis jest używany przez:
 * - PresenceController, czyli REST API presence,
 * - WebSocketPresenceEventListener, który automatycznie ustawia ONLINE/OFFLINE
 *   na podstawie połączeń WebSocket,
 * - NotificationService, żeby sprawdzić, czy trzeba wysłać push notification
 *   do użytkownika offline.
 *
 * Ważne:
 * presence jest funkcją UX-ową, nie krytycznym źródłem prawdy jak wiadomości.
 * W obecnej wersji jest trzymany w bazie przez UserPresenceRepository.
 * Przy większej skali warto przenieść aktualny status online do Redis z TTL,
 * a w bazie zostawić głównie lastSeenAt.
 */
@Service
public class PresenceService {

    /**
     * Repozytorium statusów presence.
     *
     * Przechowuje rekord UserPresence powiązany z użytkownikiem.
     */
    private final UserPresenceRepository presenceRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Potrzebne przy tworzeniu pierwszego rekordu presence dla użytkownika.
     * Jeżeli użytkownik nie istnieje, zwracamy NotFoundException.
     */
    private final UserRepository userRepository;

    public PresenceService(UserPresenceRepository presenceRepository, UserRepository userRepository) {
        this.presenceRepository = presenceRepository;
        this.userRepository = userRepository;
    }

    /**
     * Oznacza użytkownika jako ONLINE.
     *
     * Typowe wywołanie:
     * - po zestawieniu pierwszego aktywnego WebSocket połączenia użytkownika,
     * - ręcznie przez POST /api/presence/me/online.
     *
     * Jeśli użytkownik nie ma jeszcze rekordu presence,
     * metoda tworzy go automatycznie.
     *
     * Zwraca PresenceResponse, żeby warstwa REST albo WebSocket mogła od razu
     * odesłać/publikować aktualny status użytkownika.
     */
    @Transactional
    public PresenceResponse markOnline(UUID userId) {
        /*
         * Pobieramy istniejący rekord presence albo tworzymy nowy.
         * Dzięki temu nie musimy zakładać, że presence istnieje od momentu rejestracji.
         */
        UserPresence presence = getOrCreate(userId);

        /*
         * Ustawiamy status ONLINE i timestamp aktualizacji.
         *
         * Szczegóły tego, czy lastSeenAt zmienia się przy wejściu online,
         * są ukryte w encji UserPresence.
         */
        presence.markOnline(Instant.now());

        /*
         * Zapisujemy stan i zwracamy DTO dla klienta/innych warstw.
         */
        return PresenceResponse.from(presenceRepository.save(presence));
    }

    /**
     * Oznacza użytkownika jako OFFLINE.
     *
     * Typowe wywołanie:
     * - po utracie ostatniego aktywnego połączenia WebSocket,
     * - przy logout,
     * - ręcznie przez POST /api/presence/me/offline.
     *
     * Przy przejściu offline zwykle aktualizowany jest lastSeenAt,
     * żeby frontend mógł pokazać "ostatnio widziany".
     */
    @Transactional
    public PresenceResponse markOffline(UUID userId) {
        UserPresence presence = getOrCreate(userId);

        /*
         * Ustawiamy status OFFLINE i aktualizujemy czas ostatniej aktywności.
         */
        presence.markOffline(Instant.now());

        return PresenceResponse.from(presenceRepository.save(presence));
    }

    /**
     * Pobiera presence użytkownika.
     *
     * Jeśli rekord presence jeszcze nie istnieje,
     * metoda tworzy tymczasowy obiekt UserPresence na potrzeby odpowiedzi,
     * ale go nie zapisuje, bo transakcja jest readOnly.
     *
     * Dzięki temu użytkownik bez rekordu presence może być traktowany jako domyślnie offline.
     */
    @Transactional(readOnly = true)
    public PresenceResponse getPresence(UUID userId) {
        UserPresence presence = presenceRepository.findByUserId(userId)
                .orElseGet(() -> new UserPresence(
                        userRepository.findById(userId)
                                .orElseThrow(() -> new NotFoundException("User not found"))
                ));

        return PresenceResponse.from(presence);
    }

    /**
     * Sprawdza, czy użytkownik jest aktualnie ONLINE.
     *
     * Ta metoda jest szczególnie ważna dla powiadomień:
     * jeśli użytkownik jest offline, NotificationService może wysłać push.
     *
     * Brak rekordu presence oznacza false,
     * czyli użytkownik traktowany jest jako offline.
     */
    @Transactional(readOnly = true)
    public boolean isOnline(UUID userId) {
        return presenceRepository.findByUserId(userId)
                .map(presence -> presence.getStatus() == PresenceStatus.ONLINE)
                .orElse(false);
    }

    /**
     * Pobiera istniejący rekord presence albo tworzy nowy dla użytkownika.
     *
     * To centralizuje logikę lazy creation:
     * presence nie musi być zakładane przy rejestracji konta.
     * Powstaje dopiero wtedy, gdy pierwszy raz jest potrzebne.
     *
     * Jeśli użytkownik o podanym userId nie istnieje,
     * rzucamy NotFoundException.
     */
    private UserPresence getOrCreate(UUID userId) {
        return presenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    AppUser user = userRepository.findById(userId)
                            .orElseThrow(() -> new NotFoundException("User not found"));

                    return new UserPresence(user);
                });
    }
}