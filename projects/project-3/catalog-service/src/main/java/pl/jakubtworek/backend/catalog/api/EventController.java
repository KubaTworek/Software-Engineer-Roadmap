package pl.jakubtworek.backend.catalog.api;

import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.backend.catalog.application.EventCatalogService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {
    private final EventCatalogService service;

    public EventController(EventCatalogService service) {
        this.service = service;
    }

    @GetMapping
    List<EventResponse> list() {
        return service.listEvents();
    }

    @GetMapping("/{id}")
    EventResponse get(@PathVariable UUID id) {
        return service.getEvent(id);
    }

    @GetMapping("/{id}/availability")
    AvailabilityResponse availability(@PathVariable UUID id) {
        return service.getAvailability(id);
    }
}
