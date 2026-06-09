package pl.jakubtworek.chatsystem.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import pl.jakubtworek.chatsystem.conversation.ConversationMemberRepository;
import pl.jakubtworek.chatsystem.presence.PresenceResponse;
import pl.jakubtworek.chatsystem.presence.PresenceService;

import java.security.Principal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listener zdarzeń WebSocket odpowiedzialny za presence użytkowników.
 *
 * Ta klasa reaguje na:
 * - połączenie użytkownika z WebSocket,
 * - rozłączenie użytkownika z WebSocket.
 *
 * Na tej podstawie aktualizuje status użytkownika:
 * - ONLINE, gdy użytkownik ma przynajmniej jedno aktywne połączenie,
 * - OFFLINE, gdy użytkownik nie ma już żadnego aktywnego połączenia.
 *
 * To jest ważne, bo jeden użytkownik może mieć kilka aktywnych sesji naraz,
 * np. przeglądarka, telefon i druga karta w przeglądarce.
 *
 * Dlatego nie można ustawiać OFFLINE przy pierwszym disconnect.
 * Trzeba policzyć, czy to było ostatnie aktywne połączenie użytkownika.
 */
@Component
public class WebSocketPresenceEventListener {

    /**
     * Serwis odpowiedzialny za zapis i odczyt presence.
     *
     * To on faktycznie zmienia status użytkownika,
     * np. ONLINE/OFFLINE oraz lastSeenAt.
     */
    private final PresenceService presenceService;

    /**
     * Repozytorium członkostwa w konwersacjach.
     *
     * Używane do znalezienia wszystkich rozmów użytkownika,
     * żeby rozesłać informację o zmianie presence do właściwych konwersacji.
     */
    private final ConversationMemberRepository memberRepository;

    /**
     * Publisher WebSocket.
     *
     * Wysyła event presence.updated do członków konwersacji.
     */
    private final RealtimeMessagePublisher publisher;

    /**
     * Lokalny licznik aktywnych połączeń per użytkownik.
     *
     * Klucz: userId.
     * Wartość: liczba aktywnych połączeń WebSocket tego użytkownika
     * na tej instancji aplikacji.
     *
     * ConcurrentHashMap i AtomicInteger są użyte, bo eventy connect/disconnect
     * mogą być obsługiwane równolegle przez różne wątki.
     *
     * Ważne ograniczenie:
     * to działa poprawnie lokalnie na jednej instancji aplikacji.
     * Przy wielu instancjach produkcyjnych ten stan powinien trafić do Redis
     * albo innego współdzielonego storage presence.
     */
    private final ConcurrentHashMap<UUID, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    public WebSocketPresenceEventListener(
            PresenceService presenceService,
            ConversationMemberRepository memberRepository,
            RealtimeMessagePublisher publisher
    ) {
        this.presenceService = presenceService;
        this.memberRepository = memberRepository;
        this.publisher = publisher;
    }

    /**
     * Obsługuje zdarzenie połączenia WebSocket.
     *
     * Spring publikuje SessionConnectedEvent,
     * gdy klient poprawnie zestawi sesję STOMP/WebSocket.
     *
     * Jeśli to pierwsze aktywne połączenie danego użytkownika,
     * oznaczamy go jako ONLINE i publikujemy presence.updated
     * do jego konwersacji.
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        UUID userId = extractUserId(event.getUser());

        /*
         * Brak userId oznacza, że nie mamy poprawnie uwierzytelnionego użytkownika
         * albo Principal nie zawiera UUID.
         *
         * W takim przypadku nie aktualizujemy presence.
         */
        if (userId == null) {
            return;
        }

        /*
         * Zwiększamy licznik aktywnych połączeń użytkownika.
         *
         * computeIfAbsent tworzy licznik, jeśli to pierwsze połączenie.
         * incrementAndGet zwraca aktualną liczbę połączeń po zwiększeniu.
         */
        int connections = activeConnections
                .computeIfAbsent(userId, ignored -> new AtomicInteger(0))
                .incrementAndGet();

        /*
         * Status ONLINE publikujemy tylko przy przejściu z 0 na 1 połączenie.
         *
         * Jeśli użytkownik otworzy drugą kartę, nadal jest ONLINE,
         * więc nie ma sensu spamować wszystkich konwersacji kolejnym eventem.
         */
        if (connections == 1) {
            PresenceResponse presence = presenceService.markOnline(userId);
            publishToUserConversations(userId, presence);
        }
    }

    /**
     * Obsługuje zdarzenie rozłączenia WebSocket.
     *
     * Spring publikuje SessionDisconnectEvent,
     * gdy klient zamyka połączenie albo połączenie zostaje zerwane.
     *
     * Użytkownik przechodzi na OFFLINE tylko wtedy,
     * gdy po tym disconnect nie ma już żadnego aktywnego połączenia.
     */
    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        UUID userId = extractUserId(event.getUser());

        if (userId == null) {
            return;
        }

        /*
         * Pobieramy licznik połączeń użytkownika.
         *
         * Jeśli go nie ma, oznacza to niespójne albo spóźnione zdarzenie disconnect.
         * Nie robimy wtedy nic, żeby nie ustawić błędnie OFFLINE.
         */
        AtomicInteger counter = activeConnections.get(userId);

        if (counter == null) {
            return;
        }

        /*
         * Zmniejszamy licznik aktywnych połączeń.
         */
        int connections = counter.decrementAndGet();

        /*
         * Jeśli licznik spadł do 0 lub mniej,
         * to było ostatnie znane połączenie użytkownika na tej instancji.
         *
         * Usuwamy wpis z mapy, oznaczamy użytkownika jako OFFLINE
         * i publikujemy zmianę presence do jego konwersacji.
         */
        if (connections <= 0) {
            activeConnections.remove(userId);

            PresenceResponse presence = presenceService.markOffline(userId);
            publishToUserConversations(userId, presence);
        }
    }

    /**
     * Publikuje aktualny presence użytkownika do wszystkich jego konwersacji.
     *
     * Dzięki temu osoby będące w tych samych rozmowach dostają event:
     * presence.updated
     *
     * Frontend może na tej podstawie pokazać:
     * - zieloną kropkę ONLINE,
     * - status OFFLINE,
     * - last seen.
     */
    private void publishToUserConversations(UUID userId, PresenceResponse presence) {
        memberRepository.findMembershipsForUser(userId).forEach(member ->
                publisher.publishPresence(member.getConversation().getId(), presence)
        );
    }

    /**
     * Bezpiecznie wyciąga UUID użytkownika z Principal.
     *
     * W tej aplikacji Principal.name powinno zawierać userId jako UUID.
     *
     * Jeśli Principal jest pusty albo ma niepoprawny format,
     * zwracamy null i pomijamy aktualizację presence.
     */
    private UUID extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}