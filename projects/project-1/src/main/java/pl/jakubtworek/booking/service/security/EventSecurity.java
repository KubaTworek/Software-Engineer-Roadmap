package pl.jakubtworek.booking.service.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.entity.UserRole;
import pl.jakubtworek.booking.repository.EventRepository;

import java.util.UUID;

@Component("eventSecurity")
public class EventSecurity {
    private final EventRepository eventRepository;

    public EventSecurity(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public boolean canManage(Authentication authentication, UUID eventId) {
        var principal = AuthenticationAccess.principal(authentication);
        if (principal == null || principal.organizationId() == null) {
            return false;
        }
        if (principal.role() != UserRole.EVENT_MANAGER && principal.role() != UserRole.ORG_ADMIN) {
            return false;
        }
        return eventRepository.findById(eventId)
                .filter(event -> event.getOrganization() != null)
                .map(event -> event.getOrganization().getId().equals(principal.organizationId()))
                .orElse(false);
    }
}
