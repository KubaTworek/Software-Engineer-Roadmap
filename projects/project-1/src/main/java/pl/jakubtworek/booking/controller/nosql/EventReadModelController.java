package pl.jakubtworek.booking.controller.nosql;

import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentListResponse;
import pl.jakubtworek.booking.dto.nosql.EventSearchDocumentResponse;
import pl.jakubtworek.booking.service.EventSearchReadModelService;

import java.util.UUID;

@RestController
@RequestMapping("/api/nosql/read-model/events")
public class EventReadModelController {
    private final EventSearchReadModelService eventSearchReadModelService;

    public EventReadModelController(EventSearchReadModelService eventSearchReadModelService) {
        this.eventSearchReadModelService = eventSearchReadModelService;
    }

    @PostMapping("/{eventId}/rebuild")
    public EventSearchDocumentResponse rebuild(@PathVariable UUID eventId) {
        return eventSearchReadModelService.rebuildOne(eventId);
    }

    @GetMapping("/{eventId}")
    public EventSearchDocumentResponse get(@PathVariable UUID eventId) {
        return eventSearchReadModelService.get(eventId);
    }

    @GetMapping
    public EventSearchDocumentListResponse search(@RequestParam String city,
                                                  @RequestParam String category,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return eventSearchReadModelService.search(city, category, limit);
    }

    @DeleteMapping
    public void clear() {
        eventSearchReadModelService.clear();
    }
}
