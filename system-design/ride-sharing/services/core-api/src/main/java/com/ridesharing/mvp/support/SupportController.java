package com.ridesharing.mvp.support;

import com.ridesharing.mvp.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Kontroler obsługujący zgłoszenia supportowe.
 *
 * W aplikacji ride-sharing support jest potrzebny do obsługi problemów takich jak:
 * - reklamacja przejazdu,
 * - problem z płatnością,
 * - nieprawidłowe zachowanie kierowcy lub pasażera,
 * - anulowanie kursu,
 * - problem bezpieczeństwa,
 * - korekta rozliczenia.
 *
 * Ten controller obsługuje dwa typy użytkowników:
 * - zwykły użytkownik tworzy i przegląda własne tickety,
 * - ADMIN przegląda i aktualizuje wszystkie tickety.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
public class SupportController {

    /**
     * Serwis domenowy supportu.
     *
     * To tutaj powinny znajdować się reguły:
     * - kto może utworzyć ticket,
     * - kto może zobaczyć ticket,
     * - kto może zmienić status,
     * - jak zapisać assignment,
     * - jakie eventy outbox opublikować.
     */
    private final SupportService supportService;

    /**
     * Tworzy nowe zgłoszenie supportowe.
     *
     * Endpoint dostępny dla zalogowanego użytkownika.
     *
     * Flow:
     * 1. Użytkownik wysyła kategorię, tytuł, opis i opcjonalne rideId.
     * 2. SupportService tworzy ticket powiązany z użytkownikiem.
     * 3. Jeżeli rideId jest podane, ticket może zostać powiązany z konkretnym przejazdem.
     * 4. System zwraca DTO utworzonego zgłoszenia.
     *
     * rideId jest opcjonalne, bo nie każde zgłoszenie musi dotyczyć konkretnego kursu.
     */
    @PostMapping("/tickets")
    public SupportTicketDto create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateTicketRequest request
    ) {
        return supportService.create(principal.user(), request);
    }

    /**
     * Zwraca zgłoszenia aktualnie zalogowanego użytkownika.
     *
     * To widok "moje sprawy" w aplikacji pasażera albo kierowcy.
     * Użytkownik nie podaje userId w requestcie — system bierze go z tokenu.
     *
     * Dzięki temu użytkownik nie może łatwo podejrzeć cudzych ticketów.
     */
    @GetMapping("/tickets/me")
    public List<SupportTicketDto> mine(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return supportService.myTickets(principal.user());
    }

    /**
     * Zwraca listę ticketów dla panelu administracyjnego.
     *
     * Dostęp tylko dla ADMIN.
     *
     * Parametr status pozwala filtrować zgłoszenia, np.:
     * - OPEN,
     * - IN_PROGRESS,
     * - RESOLVED,
     * - CLOSED.
     *
     * To endpoint operatorski dla supportu i administracji.
     */
    @GetMapping("/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SupportTicketDto> list(
            @RequestParam(required = false) SupportTicketStatus status
    ) {
        return supportService.list(status);
    }

    /**
     * Aktualizuje ticket supportowy.
     *
     * Dostęp tylko dla ADMIN.
     *
     * Używane do:
     * - zmiany statusu zgłoszenia,
     * - zmiany priorytetu,
     * - dodania rozwiązania,
     * - przypisania zgłoszenia do aktualnego admina.
     *
     * principal.user() jest przekazywany do SupportService,
     * żeby zapisać kto wykonał akcję. To istotne dla audytu pracy supportu.
     */
    @PatchMapping("/tickets/{ticketId}")
    @PreAuthorize("hasRole('ADMIN')")
    public SupportTicketDto update(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID ticketId,
            @RequestBody UpdateTicketRequest request
    ) {
        return supportService.update(ticketId, principal.user(), request);
    }

    /**
     * Request tworzenia ticketu.
     *
     * Pola:
     * - rideId: opcjonalny przejazd, którego dotyczy zgłoszenie,
     * - category: kategoria problemu, np. PAYMENT, SAFETY, DRIVER, APP,
     * - priority: priorytet zgłoszenia,
     * - title: krótki tytuł sprawy,
     * - description: dokładny opis problemu.
     *
     * category, title i description są wymagane, bo bez nich ticket byłby mało użyteczny.
     */
    public record CreateTicketRequest(
            UUID rideId,
            @NotBlank String category,
            SupportPriority priority,
            @NotBlank String title,
            @NotBlank String description
    ) {}

    /**
     * Request aktualizacji ticketu przez admina.
     *
     * Wszystkie pola są opcjonalne, więc endpoint działa jak częściowy PATCH.
     *
     * Pola:
     * - status: nowy status zgłoszenia,
     * - priority: zmiana priorytetu,
     * - resolution: opis rozwiązania,
     * - assignToMe: jeżeli true, ticket zostanie przypisany do aktualnego admina.
     */
    public record UpdateTicketRequest(
            SupportTicketStatus status,
            SupportPriority priority,
            String resolution,
            Boolean assignToMe
    ) {}
}