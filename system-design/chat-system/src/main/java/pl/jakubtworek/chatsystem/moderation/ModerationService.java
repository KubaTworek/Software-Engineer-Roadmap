package pl.jakubtworek.chatsystem.moderation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.NotFoundException;
import pl.jakubtworek.chatsystem.conversation.ConversationService;
import pl.jakubtworek.chatsystem.message.Message;
import pl.jakubtworek.chatsystem.message.MessageRepository;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za podstawową moderację treści.
 *
 * W tej wersji obsługuje dwa scenariusze:
 * - pre-moderację wiadomości wychodzącej, czyli blokowanie prostych zakazanych fraz
 *   przed zapisaniem wiadomości,
 * - zgłaszanie już istniejących wiadomości przez użytkowników.
 *
 * To nie jest jeszcze pełny system moderacyjny.
 * To raczej minimalny fundament pod:
 * - filtry antyspamowe,
 * - wykrywanie phishingu,
 * - raporty użytkowników,
 * - kolejkę zgłoszeń dla moderatorów,
 * - późniejsze statusy typu OPEN / REVIEWED / REJECTED / ACTION_TAKEN.
 */
@Service
public class ModerationService {

    /**
     * Prosty zestaw zakazanych fraz.
     *
     * Jeśli wiadomość zawiera którąkolwiek z tych fraz,
     * zostaje odrzucona przed zapisem.
     *
     * To jest bardzo podstawowa moderacja.
     * W produkcji lepiej trzymać takie reguły w bazie, config serverze
     * albo osobnym silniku antyspamowym, a nie na sztywno w kodzie.
     */
    private static final Set<String> BANNED_TERMS = Set.of(
            "scam-link",
            "malware-test",
            "phishing-test"
    );

    /**
     * Repozytorium zgłoszeń wiadomości.
     *
     * Przechowuje raporty tworzone przez użytkowników.
     */
    private final MessageReportRepository reportRepository;

    /**
     * Repozytorium wiadomości.
     *
     * Potrzebne przy zgłaszaniu, żeby znaleźć wiadomość po messageId.
     */
    private final MessageRepository messageRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Potrzebne, żeby znaleźć aktualnego użytkownika jako autora zgłoszenia.
     */
    private final UserRepository userRepository;

    /**
     * Serwis konwersacji.
     *
     * Używany jako kontrola dostępu przy zgłaszaniu wiadomości.
     *
     * Użytkownik może zgłosić tylko wiadomość z konwersacji,
     * której jest członkiem.
     */
    private final ConversationService conversationService;

    public ModerationService(
            MessageReportRepository reportRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            ConversationService conversationService
    ) {
        this.reportRepository = reportRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.conversationService = conversationService;
    }

    /**
     * Waliduje treść wiadomości przed jej zapisaniem.
     *
     * Ta metoda jest wywoływana w MessageService przed utworzeniem Message.
     *
     * Jeśli wiadomość zawiera zakazaną frazę:
     * - rzucamy BadRequestException,
     * - wiadomość nie zostaje zapisana,
     * - nie powstają receipts,
     * - nie powstaje outbox event,
     * - nic nie trafia do WebSocket/push notifications.
     *
     * Jeśli body jest puste albo null, metoda nic nie robi.
     * To pozwala na wiadomości składające się wyłącznie z załączników.
     */
    public void validateOutgoingMessage(String body) {
        if (body == null || body.isBlank()) {
            return;
        }

        /*
         * Normalizujemy tekst do lowercase,
         * żeby wykrywać zakazane frazy niezależnie od wielkości liter.
         *
         * Locale.ROOT jest bezpieczniejsze niż domyślne locale systemu,
         * bo nie zależy od ustawień językowych serwera.
         */
        String normalized = body.toLowerCase(Locale.ROOT);

        /*
         * Sprawdzamy, czy wiadomość zawiera którąkolwiek zakazaną frazę.
         *
         * contains() jest prosty, ale ma ograniczenia:
         * - łatwo go obejść spacjami albo znakami specjalnymi,
         * - może dawać false positive,
         * - nie rozpoznaje kontekstu.
         */
        for (String term : BANNED_TERMS) {
            if (normalized.contains(term)) {
                throw new BadRequestException("Message rejected by basic content moderation");
            }
        }
    }

    /**
     * Tworzy zgłoszenie wiadomości przez użytkownika.
     *
     * Endpoint wywołujący:
     * POST /api/moderation/messages/{messageId}/reports
     *
     * Najważniejsza zasada bezpieczeństwa:
     * użytkownik może zgłosić tylko wiadomość,
     * którą faktycznie ma prawo zobaczyć.
     */
    @Transactional
    public MessageReportResponse reportMessage(
            UUID currentUserId,
            UUID messageId,
            ReportMessageRequest request
    ) {
        /*
         * Najpierw pobieramy wiadomość.
         * Jeśli nie istnieje, zwracamy NotFoundException.
         */
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        /*
         * Sprawdzamy, czy zgłaszający jest członkiem konwersacji,
         * w której znajduje się wiadomość.
         *
         * Bez tego użytkownik mógłby zgłaszać losowe messageId
         * z cudzych rozmów.
         */
        conversationService.ensureMember(
                message.getConversation().getId(),
                currentUserId
        );

        /*
         * Pobieramy użytkownika zgłaszającego.
         * Reporter musi istnieć jako AppUser.
         */
        AppUser reporter = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Current user not found"));

        /*
         * Tworzymy raport.
         *
         * reason jest wymaganym powodem zgłoszenia.
         * details jest opcjonalne, więc obsługujemy null.
         *
         * trim() usuwa przypadkowe spacje z początku i końca.
         */
        MessageReport report = reportRepository.save(
                new MessageReport(
                        message,
                        reporter,
                        request.reason().trim(),
                        request.details() == null ? null : request.details().trim()
                )
        );

        return MessageReportResponse.from(report);
    }

    /**
     * Pobiera wszystkie otwarte zgłoszenia moderacyjne.
     *
     * Endpoint wywołujący:
     * GET /api/moderation/reports/open
     *
     * Zwracane są tylko zgłoszenia o statusie OPEN,
     * posortowane od najstarszych.
     *
     * Dzięki temu moderator może obsługiwać zgłoszenia w kolejności wpływu.
     */
    @Transactional(readOnly = true)
    public List<MessageReportResponse> getOpenReports() {
        return reportRepository
                .findByStatusOrderByCreatedAtAsc(ModerationStatus.OPEN)
                .stream()
                .map(MessageReportResponse::from)
                .toList();
    }
}