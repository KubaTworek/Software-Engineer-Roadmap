package pl.jakubtworek.chatsystem.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.conversation.Conversation;
import pl.jakubtworek.chatsystem.conversation.ConversationMember;
import pl.jakubtworek.chatsystem.conversation.ConversationMemberRepository;
import pl.jakubtworek.chatsystem.conversation.ConversationType;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.presence.PresenceService;
import pl.jakubtworek.chatsystem.user.AppUser;

import java.util.List;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za powiadomienia użytkowników.
 *
 * W tym projekcie powiadomienia są tworzone po zdarzeniu MESSAGE_CREATED,
 * czyli po trwałym zapisaniu wiadomości w bazie.
 *
 * Główna odpowiedzialność:
 * - znaleźć odbiorców wiadomości,
 * - pominąć nadawcę,
 * - sprawdzić, którzy odbiorcy są offline,
 * - utworzyć rekord PushNotification,
 * - spróbować wysłać powiadomienie przez PushNotificationProvider,
 * - oznaczyć powiadomienie jako SENT albo FAILED.
 *
 * Ta klasa nie powinna być wywoływana synchronicznie w głównej ścieżce wysyłki wiadomości.
 * Najlepiej, gdy odpala się asynchronicznie z handlera eventu MESSAGE_CREATED.
 */
@Service
public class NotificationService {

    /**
     * Repozytorium członków konwersacji.
     *
     * Potrzebne do ustalenia, kto powinien dostać powiadomienie
     * po utworzeniu nowej wiadomości.
     */
    private final ConversationMemberRepository memberRepository;

    /**
     * Serwis presence.
     *
     * Pozwala sprawdzić, czy dany odbiorca jest aktualnie online.
     *
     * Jeśli użytkownik jest online, nie wysyłamy push notification,
     * bo powinien dostać wiadomość przez WebSocket.
     */
    private final PresenceService presenceService;

    /**
     * Repozytorium zapisanych powiadomień.
     *
     * Każda próba powiadomienia jest zapisywana w bazie,
     * dzięki czemu można później pokazać historię powiadomień
     * albo diagnozować błędy wysyłki.
     */
    private final PushNotificationRepository notificationRepository;

    /**
     * Abstrakcja zewnętrznego dostawcy push notifications.
     *
     * W lokalnej wersji może to być provider logujący.
     * W produkcji można podmienić implementację na:
     * - Firebase Cloud Messaging,
     * - Apple Push Notification service,
     * - Web Push,
     * - inny provider.
     */
    private final PushNotificationProvider pushProvider;

    public NotificationService(
            ConversationMemberRepository memberRepository,
            PresenceService presenceService,
            PushNotificationRepository notificationRepository,
            PushNotificationProvider pushProvider
    ) {
        this.memberRepository = memberRepository;
        this.presenceService = presenceService;
        this.notificationRepository = notificationRepository;
        this.pushProvider = pushProvider;
    }

    /**
     * Tworzy i wysyła powiadomienia do offline odbiorców wiadomości.
     *
     * Ta metoda jest wywoływana po zapisaniu wiadomości,
     * najczęściej przez MessageCreatedEventHandler.
     *
     * Nie wysyłamy powiadomień do:
     * - nadawcy wiadomości,
     * - użytkowników, którzy są online.
     *
     * Online użytkownicy powinni dostać realtime event przez WebSocket,
     * a offline użytkownicy dostają push.
     */
    @Transactional
    public void notifyOfflineRecipients(Message message) {
        Conversation conversation = message.getConversation();
        AppUser sender = message.getSender();

        /*
         * Pobieramy członków konwersacji i filtrujemy tylko realnych odbiorców pushy:
         * - pomijamy nadawcę,
         * - pomijamy użytkowników online.
         *
         * Dla rozmowy 1:1 będzie to maksymalnie jeden odbiorca.
         * Dla grupy może to być wielu odbiorców.
         */
        List<ConversationMember> recipients = memberRepository.findMembersByConversationId(conversation.getId()).stream()
                .filter(member -> !member.getUser().getId().equals(sender.getId()))
                .filter(member -> !presenceService.isOnline(member.getUser().getId()))
                .toList();

        for (ConversationMember recipientMember : recipients) {
            AppUser recipient = recipientMember.getUser();

            /*
             * Tytuł zależy od typu rozmowy:
             * - dla grupy: tytuł grupy,
             * - dla rozmowy 1:1: nazwa nadawcy.
             */
            String title = notificationTitle(conversation, sender);

            /*
             * Body powiadomienia zawiera nazwę nadawcy i krótki podgląd wiadomości.
             *
             * Uwaga: w produkcyjnej aplikacji trzeba uważać na prywatność.
             * Nie każdy użytkownik chce widzieć treść wiadomości w pushu na ekranie blokady.
             */
            String body = sender.getDisplayName() + ": " + preview(message.getBody());

            /*
             * Zapisujemy powiadomienie przed próbą wysyłki.
             *
             * Dzięki temu mamy ślad, że system próbował obsłużyć powiadomienie
             * dla konkretnego odbiorcy, rozmowy i wiadomości.
             */
            PushNotification notification =
                    new PushNotification(recipient, conversation, message, title, body);

            PushNotification saved = notificationRepository.save(notification);

            try {
                /*
                 * Wysyłamy powiadomienie przez provider.
                 *
                 * Provider ukrywa szczegóły integracji z FCM/APNs/Web Push.
                 */
                pushProvider.send(
                        new PushPayload(
                                recipient.getId(),
                                conversation.getId(),
                                message.getId(),
                                title,
                                body
                        )
                );

                /*
                 * Jeśli provider nie rzucił wyjątku, oznaczamy powiadomienie jako wysłane.
                 */
                saved.markSent();

            } catch (RuntimeException ex) {
                /*
                 * Błąd providera nie powinien wycofać samego zapisu wiadomości.
                 *
                 * Oznaczamy powiadomienie jako FAILED,
                 * żeby można było później monitorować błędy lub dodać retry.
                 */
                saved.markFailed();
            }
        }
    }

    /**
     * Pobiera powiadomienia aktualnie zalogowanego użytkownika.
     *
     * Używane przez endpoint:
     * GET /api/notifications/me
     *
     * Zwracamy tylko powiadomienia odbiorcy o currentUserId.
     */
    @Transactional(readOnly = true)
    public List<PushNotificationResponse> getMyNotifications(UUID currentUserId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(PushNotificationResponse::from)
                .toList();
    }

    /**
     * Buduje tytuł powiadomienia.
     *
     * Dla grupy lepszy jest tytuł rozmowy,
     * bo użytkownik od razu wie, w której grupie pojawiła się wiadomość.
     *
     * Dla rozmowy bezpośredniej tytułem jest nazwa nadawcy.
     */
    private String notificationTitle(Conversation conversation, AppUser sender) {
        if (conversation.getType() == ConversationType.GROUP) {
            return conversation.getTitle() == null
                    ? "New group message"
                    : conversation.getTitle();
        }

        return sender.getDisplayName();
    }

    /**
     * Tworzy krótki podgląd wiadomości do powiadomienia.
     *
     * Robi trzy rzeczy:
     * - obsługuje null,
     * - normalizuje białe znaki,
     * - ucina tekst do 120 znaków.
     *
     * Dzięki temu push notification nie zawiera zbyt długiej treści.
     */
    private String preview(String body) {
        String normalized = body == null
                ? ""
                : body.replaceAll("\\s+", " ").trim();

        return normalized.length() <= 120
                ? normalized
                : normalized.substring(0, 117) + "...";
    }
}