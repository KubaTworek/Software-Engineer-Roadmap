package pl.jakubtworek.booking.service.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.UserRole;
import pl.jakubtworek.booking.repository.EventRepository;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

@Component("reservationSecurity")
public class ReservationSecurity {
    private final ReservationRepository reservationRepository;
    private final EventRepository eventRepository;

    public ReservationSecurity(ReservationRepository reservationRepository,
                               EventRepository eventRepository) {
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public boolean canCreate(Authentication authentication, UUID eventId, String customerEmail) {
        var principal = AuthenticationAccess.principal(authentication);
        if (principal == null) {
            return false;
        }
        if (principal.role() == UserRole.CUSTOMER) {
            return principal.email().equalsIgnoreCase(customerEmail);
        }
        if (principal.organizationId() == null
                || (principal.role() != UserRole.EVENT_MANAGER && principal.role() != UserRole.ORG_ADMIN)) {
            return false;
        }

        /*
         * Sama rola managera nie wystarcza. Bez tego sprawdzenia użytkownik
         * organizacji A mógłby podać identyfikator wydarzenia organizacji B —
         * klasyczny IDOR ukryty w poprawnie uwierzytelnionym żądaniu.
         */
        return eventRepository.findById(eventId)
                .map(event -> event.getOrganization() != null
                        && principal.organizationId().equals(event.getOrganization().getId()))
                .orElse(false);
    }

    public boolean canManage(Authentication authentication, UUID reservationId) {
        var principal = AuthenticationAccess.principal(authentication);
        if (principal == null) {
            return false;
        }
        Reservation reservation = reservationRepository.findDetailedById(reservationId).orElse(null);
        if (reservation == null) {
            return false;
        }
        if (principal.role() == UserRole.CUSTOMER) {
            return reservation.getCustomer().getEmail().equalsIgnoreCase(principal.email());
        }
        return principal.organizationId() != null
                && reservation.getOrganization() != null
                && principal.organizationId().equals(reservation.getOrganization().getId())
                && (principal.role() == UserRole.EVENT_MANAGER || principal.role() == UserRole.ORG_ADMIN);
    }

    @Transactional(readOnly = true)
    public boolean canView(Authentication authentication, UUID reservationId) {
        var principal = AuthenticationAccess.principal(authentication);
        if (principal == null) {
            return false;
        }
        Reservation reservation = reservationRepository.findDetailedById(reservationId).orElse(null);
        if (reservation == null) {
            return false;
        }
        if (principal.role() == UserRole.SUPPORT) {
            return true;
        }
        if (principal.role() == UserRole.CUSTOMER) {
            return reservation.getCustomer().getEmail().equalsIgnoreCase(principal.email());
        }
        if (principal.organizationId() == null || reservation.getOrganization() == null) {
            return false;
        }
        boolean sameOrganization = principal.organizationId().equals(reservation.getOrganization().getId());
        return sameOrganization && switch (principal.role()) {
            case EVENT_MANAGER, ORG_ADMIN, HR -> true;
            default -> false;
        };
    }
}
