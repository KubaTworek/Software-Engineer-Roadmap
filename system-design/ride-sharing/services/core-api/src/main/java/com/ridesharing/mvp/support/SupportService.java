package com.ridesharing.mvp.support;

import com.ridesharing.mvp.common.ApiException;
import com.ridesharing.mvp.outbox.OutboxService;
import com.ridesharing.mvp.ride.RideRepository;
import com.ridesharing.mvp.user.AppUser;
import com.ridesharing.mvp.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis domenowy obsługujący tickety supportowe.
 *
 * W aplikacji ride-sharing SupportService odpowiada za zgłoszenia użytkowników,
 * np. problemy z przejazdem, płatnością, bezpieczeństwem, kierowcą albo aplikacją.
 *
 * Ta klasa jest właściwym miejscem na reguły supportowe:
 * - tworzenie ticketu przez pasażera/kierowcę,
 * - powiązanie ticketu z przejazdem,
 * - listowanie zgłoszeń użytkownika,
 * - aktualizacja ticketu przez admina,
 * - publikacja eventów supportowych przez outbox.
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    /**
     * Repozytorium ticketów supportowych.
     *
     * Służy do zapisu nowych zgłoszeń, pobierania listy ticketów
     * oraz aktualizacji statusu, priorytetu, rozwiązania i przypisanego admina.
     */
    private final SupportTicketRepository tickets;

    /**
     * Repozytorium przejazdów.
     *
     * Używane przy tworzeniu ticketu powiązanego z konkretnym rideId.
     * Jeżeli użytkownik zgłasza problem z kursem, ticket powinien wskazywać ten przejazd.
     */
    private final RideRepository rides;

    /**
     * Repozytorium użytkowników.
     *
     * W tej klasie obecnie nie jest używane.
     * Jeśli nie jest potrzebne do innych metod, warto je usunąć, żeby nie sugerować
     * nieistniejącej logiki.
     */
    private final AppUserRepository users;

    /**
     * Outbox dla eventów supportowych.
     *
     * Po utworzeniu albo aktualizacji ticketu zapisujemy event,
     * który później może zostać opublikowany do Kafki.
     *
     * Takie eventy mogą zasilać:
     * - panel admina,
     * - powiadomienia,
     * - analitykę jakości supportu,
     * - automatyzacje SLA.
     */
    private final OutboxService outbox;

    /**
     * Tworzy nowe zgłoszenie supportowe.
     *
     * Flow:
     * 1. Jeżeli request zawiera rideId, pobiera przejazd z bazy.
     * 2. Tworzy ticket przypisany do reportera.
     * 3. Ustawia domyślny status OPEN.
     * 4. Jeżeli priorytet nie został podany, ustawia NORMAL.
     * 5. Zapisuje ticket.
     * 6. Zapisuje event SupportTicketCreated do outboxa.
     *
     * To podstawowy endpoint dla pasażera/kierowcy, który zgłasza problem.
     */
    @Transactional
    public SupportTicketDto create(
            AppUser reporter,
            SupportController.CreateTicketRequest request
    ) {
        /*
         * rideId jest opcjonalne.
         * Ticket może dotyczyć konkretnego przejazdu, ale może też dotyczyć ogólnego problemu z kontem.
         */
        var ride = request.rideId() == null
                ? null
                : rides.findById(request.rideId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));

        /*
         * Tworzymy zgłoszenie z reportującym użytkownikiem.
         * Reporter pochodzi z tokenu, a nie z request body, więc użytkownik nie może
         * utworzyć ticketu jako ktoś inny.
         */
        var ticket = tickets.save(SupportTicket.builder()
                .id(UUID.randomUUID())
                .ride(ride)
                .reporter(reporter)
                .category(request.category())
                .priority(request.priority() == null
                        ? SupportPriority.NORMAL
                        : request.priority())
                .status(SupportTicketStatus.OPEN)
                .title(request.title())
                .description(request.description())
                .build());

        /*
         * Event supportowy przez outbox.
         * Dzięki temu inne moduły mogą asynchronicznie zareagować na nowe zgłoszenie.
         */
        outbox.supportEvent(
                ticket.getId(),
                "SupportTicketCreated",
                Map.of(
                        "ticketId", ticket.getId().toString(),
                        "category", ticket.getCategory()
                )
        );

        return SupportTicketDto.from(ticket);
    }

    /**
     * Zwraca tickety utworzone przez danego użytkownika.
     *
     * Używane w widoku "moje zgłoszenia".
     * Reporter jest brany z tokenu, więc użytkownik widzi tylko swoje sprawy.
     */
    public List<SupportTicketDto> myTickets(AppUser reporter) {
        return tickets.findByReporterIdOrderByCreatedAtDesc(reporter.getId())
                .stream()
                .map(SupportTicketDto::from)
                .toList();
    }

    /**
     * Zwraca listę ticketów dla admina/supportu.
     *
     * Jeżeli status jest null, zwraca ostatnie 100 ticketów.
     * Jeżeli status jest podany, zwraca ostatnie 100 ticketów o tym statusie.
     *
     * To prosty widok operatorski. Produkcyjnie warto dodać paginację,
     * sortowanie, filtrowanie po priorytecie, kategorii, assignedAdmin i SLA.
     */
    public List<SupportTicketDto> list(SupportTicketStatus status) {
        var rows = status == null
                ? tickets.findTop100ByOrderByCreatedAtDesc()
                : tickets.findTop100ByStatusOrderByCreatedAtDesc(status);

        return rows.stream()
                .map(SupportTicketDto::from)
                .toList();
    }

    /**
     * Aktualizuje ticket supportowy przez admina.
     *
     * Flow:
     * 1. Pobiera ticket po ID.
     * 2. Aktualizuje tylko pola przekazane w requestcie.
     * 3. Jeżeli assignToMe=true, przypisuje ticket do aktualnego admina.
     * 4. Jeżeli status staje się RESOLVED albo CLOSED, ustawia closedAt.
     * 5. Zapisuje ticket.
     * 6. Publikuje event SupportTicketUpdated przez outbox.
     *
     * To jest częściowy update typu PATCH — null oznacza "nie zmieniaj pola".
     */
    @Transactional
    public SupportTicketDto update(
            UUID ticketId,
            AppUser admin,
            SupportController.UpdateTicketRequest request
    ) {
        var ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ticket not found"));

        /*
         * Aktualizacja tylko pól obecnych w requestcie.
         * Dzięki temu admin może zmienić np. sam status bez nadpisywania priorytetu.
         */
        if (request.status() != null) {
            ticket.setStatus(request.status());
        }

        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }

        if (request.resolution() != null) {
            ticket.setResolution(request.resolution());
        }

        /*
         * assignToMe przypisuje zgłoszenie do admina wykonującego request.
         * Admin pochodzi z tokenu, a nie z body.
         */
        if (request.assignToMe() != null && request.assignToMe()) {
            ticket.setAssignedAdmin(admin);
        }

        /*
         * Ustawiamy closedAt przy statusach końcowych.
         * Warunek ticket.getClosedAt() == null chroni pierwotny czas zamknięcia
         * przed nadpisaniem przy kolejnych update'ach.
         */
        if (ticket.getStatus() == SupportTicketStatus.CLOSED
                || ticket.getStatus() == SupportTicketStatus.RESOLVED) {
            if (ticket.getClosedAt() == null) {
                ticket.setClosedAt(Instant.now());
            }
        }

        var saved = tickets.save(ticket);

        /*
         * Event aktualizacji ticketu.
         * Inne systemy mogą go wykorzystać np. do powiadomień albo raportowania SLA.
         */
        outbox.supportEvent(
                saved.getId(),
                "SupportTicketUpdated",
                Map.of(
                        "ticketId", saved.getId().toString(),
                        "status", saved.getStatus().name()
                )
        );

        return SupportTicketDto.from(saved);
    }
}