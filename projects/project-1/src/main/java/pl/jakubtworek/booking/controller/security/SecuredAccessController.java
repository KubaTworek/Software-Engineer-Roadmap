package pl.jakubtworek.booking.controller.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.dto.security.PaymentSummaryResponse;
import pl.jakubtworek.booking.dto.security.SecuredMessageResponse;
import pl.jakubtworek.booking.dto.security.UserSummaryResponse;
import pl.jakubtworek.booking.entity.AppUser;
import pl.jakubtworek.booking.repository.AppUserRepository;
import pl.jakubtworek.booking.service.EventService;
import pl.jakubtworek.booking.service.ReservationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/secure")
public class SecuredAccessController {
    private final ReservationService reservationService;
    private final EventService eventService;
    private final AppUserRepository appUserRepository;

    public SecuredAccessController(ReservationService reservationService,
                                   EventService eventService,
                                   AppUserRepository appUserRepository) {
        this.reservationService = reservationService;
        this.eventService = eventService;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/reservations/{reservationId}")
    @PreAuthorize("@reservationSecurity.canView(authentication, #reservationId)")
    public ReservationResponse getReservation(@PathVariable UUID reservationId) {
        return reservationService.get(reservationId);
    }

    @GetMapping("/events/{eventId}/manager-view")
    @PreAuthorize("@eventSecurity.canManage(authentication, #eventId)")
    public EventResponse getManagerEventView(@PathVariable UUID eventId) {
        return eventService.get(eventId);
    }

    @GetMapping("/organizations/{organizationId}/users")
    @PreAuthorize("@organizationUserSecurity.canManage(authentication, #organizationId)")
    public List<UserSummaryResponse> getOrganizationUsers(@PathVariable UUID organizationId) {
        return appUserRepository.findByOrganizationId(organizationId).stream()
                .map(this::toUserSummary)
                .toList();
    }

    @GetMapping("/hr/organizations/{organizationId}/employees")
    @PreAuthorize("hasRole('HR') and @tenantSecurity.sameOrganization(authentication, #organizationId)")
    public List<UserSummaryResponse> getEmployeesForHr(@PathVariable UUID organizationId) {
        return appUserRepository.findByOrganizationId(organizationId).stream()
                .map(this::toUserSummary)
                .toList();
    }

    @GetMapping("/support/reservations/{reservationId}/payment-summary")
    @PreAuthorize("hasRole('SUPPORT') and @reservationSecurity.canView(authentication, #reservationId)")
    public PaymentSummaryResponse getSupportPaymentSummary(@PathVariable UUID reservationId) {
        return new PaymentSummaryResponse(
                reservationId,
                "VISIBLE_TO_SUPPORT_AS_STATUS_ONLY",
                "**** **** **** ****",
                "Support can inspect operational status, but not full payment data."
        );
    }

    @GetMapping("/support/reservations/{reservationId}/full-payment-data")
    @PreAuthorize("hasRole('ORG_ADMIN') and @reservationSecurity.canView(authentication, #reservationId)")
    public PaymentSummaryResponse getFullPaymentData(@PathVariable UUID reservationId) {
        return new PaymentSummaryResponse(
                reservationId,
                "CAPTURED",
                "4111 1111 1111 1111",
                "Educational endpoint: SUPPORT must not be able to access this."
        );
    }

    @GetMapping("/me")
    public SecuredMessageResponse me() {
        return new SecuredMessageResponse("Authenticated request reached a secured endpoint.");
    }

    private UserSummaryResponse toUserSummary(AppUser user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getOrganization() == null ? null : user.getOrganization().getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}
