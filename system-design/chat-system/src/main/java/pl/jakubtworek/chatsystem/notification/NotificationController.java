package pl.jakubtworek.chatsystem.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.List;

/**
 * REST controller odpowiedzialny za odczyt powiadomień użytkownika.
 *
 * W tej aplikacji powiadomienia są tworzone głównie wtedy,
 * gdy ktoś wyśle wiadomość do użytkownika offline.
 *
 * Typowy flow:
 * 1. Użytkownik A wysyła wiadomość.
 * 2. MessageService zapisuje wiadomość i publikuje event MESSAGE_CREATED przez outbox.
 * 3. MessageCreatedEventHandler obsługuje event.
 * 4. NotificationService sprawdza, którzy odbiorcy są offline.
 * 5. Dla offline recipients tworzone są powiadomienia.
 * 6. Ten controller pozwala użytkownikowi pobrać swoje zapisane powiadomienia.
 *
 * Controller nie decyduje, kiedy tworzyć powiadomienia.
 * Ta logika znajduje się w NotificationService.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    /**
     * Serwis zawierający logikę powiadomień.
     *
     * Controller tylko pobiera userId z kontekstu bezpieczeństwa
     * i deleguje odczyt powiadomień do serwisu.
     */
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Pobiera powiadomienia aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/notifications/me
     *
     * Użytkownik nie podaje userId w URL.
     * To celowe — backend bierze userId z JWT przez @AuthenticationPrincipal.
     *
     * Dzięki temu użytkownik nie może łatwo odpytać:
     * /api/notifications/{someoneElseId}
     *
     * i pobrać cudzych powiadomień.
     *
     * Zwraca listę PushNotificationResponse,
     * czyli uproszczony widok zapisanych powiadomień dla klienta.
     */
    @GetMapping("/me")
    public List<PushNotificationResponse> myNotifications(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return notificationService.getMyNotifications(principal.id());
    }
}