package pl.jakubtworek.booking.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.dto.EventSearchResponse;
import pl.jakubtworek.booking.service.EventService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;
    private final pl.jakubtworek.booking.service.SqlPerformanceService sqlPerformanceService;

    public EventController(EventService eventService,
                           pl.jakubtworek.booking.service.SqlPerformanceService sqlPerformanceService) {
        this.eventService = eventService;
        this.sqlPerformanceService = sqlPerformanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventCreateRequest request) {
        return eventService.create(request);
    }

    @GetMapping
    public List<EventSearchResponse> search(@RequestParam String city,
                                            @RequestParam("from") OffsetDateTime from,
                                            @RequestParam String category) {
        return sqlPerformanceService.searchEvents(city, from, category);
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable UUID eventId) {
        return eventService.get(eventId);
    }
}
