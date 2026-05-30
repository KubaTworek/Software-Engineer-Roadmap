package pl.jakubtworek.booking.service.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.entity.UserRole;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

@Component("reservationSecurity")
public class ReservationSecurity {
    private final ReservationRepository reservationRepository;

    public ReservationSecurity(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
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
