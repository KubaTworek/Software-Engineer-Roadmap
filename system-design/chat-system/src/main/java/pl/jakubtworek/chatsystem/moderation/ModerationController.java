package pl.jakubtworek.chatsystem.moderation;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.List;
import java.util.UUID;

/**
 * REST controller odpowiedzialny za podstawową moderację wiadomości.
 *
 * W tej aplikacji moderacja obejmuje dwa główne przypadki:
 * - użytkownik zgłasza wiadomość jako naruszającą zasady,
 * - system/moderator pobiera listę otwartych zgłoszeń.
 *
 * Controller nie decyduje, czy zgłoszenie jest zasadne.
 * Nie sprawdza też samodzielnie dostępu do wiadomości.
 *
 * Te reguły powinny znajdować się w ModerationService:
 * - czy wiadomość istnieje,
 * - czy zgłaszający ma prawo ją widzieć,
 * - czy użytkownik nie zgłasza tej samej wiadomości wiele razy,
 * - jaki status ma zgłoszenie,
 * - kto może przeglądać otwarte zgłoszenia.
 */
@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    /**
     * Serwis zawierający właściwą logikę moderacji.
     *
     * Controller tylko przyjmuje requesty HTTP
     * i przekazuje dane do warstwy biznesowej.
     */
    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /**
     * Zgłasza konkretną wiadomość do moderacji.
     *
     * Endpoint:
     * POST /api/moderation/messages/{messageId}/reports
     *
     * Typowy flow:
     * - użytkownik widzi wiadomość w czacie,
     * - wybiera opcję "zgłoś",
     * - frontend wysyła messageId oraz powód zgłoszenia,
     * - backend tworzy MessageReport.
     *
     * Najważniejsze:
     * - reporterId pochodzi z JWT, nie z request body,
     * - messageId pochodzi z URL,
     * - request przechodzi walidację przez @Valid,
     * - ModerationService powinien sprawdzić, czy użytkownik ma dostęp do tej wiadomości.
     *
     * Bez sprawdzenia dostępu użytkownik mógłby zgłaszać losowe messageId,
     * nawet z rozmów, których nie jest członkiem.
     */
    @PostMapping("/messages/{messageId}/reports")
    public MessageReportResponse reportMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID messageId,
            @Valid @RequestBody ReportMessageRequest request
    ) {
        return moderationService.reportMessage(
                principal.id(),
                messageId,
                request
        );
    }

    /**
     * Pobiera listę otwartych zgłoszeń moderacyjnych.
     *
     * Endpoint:
     * GET /api/moderation/reports/open
     *
     * To jest endpoint administracyjny/moderatorski.
     *
     * W obecnej wersji metoda nie przyjmuje principal,
     * więc sama klasa nie wymusza roli moderatora.
     * To jest potencjalna luka, jeśli endpoint jest dostępny dla każdego
     * zalogowanego użytkownika.
     *
     * Docelowo warto dodać:
     * - role systemowe, np. ADMIN/MODERATOR,
     * - @PreAuthorize albo sprawdzanie uprawnień w ModerationService,
     * - paginację, bo liczba zgłoszeń może rosnąć.
     */
    @GetMapping("/reports/open")
    public List<MessageReportResponse> getOpenReports() {
        return moderationService.getOpenReports();
    }
}