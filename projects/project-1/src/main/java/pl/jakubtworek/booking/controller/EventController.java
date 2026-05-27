package pl.jakubtworek.booking.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.dto.EventCreateRequest;
import pl.jakubtworek.booking.dto.EventResponse;
import pl.jakubtworek.booking.service.EventService;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody EventCreateRequest request) {
        return eventService.create(request);
    }

    @GetMapping("/{eventId}")
    public EventResponse get(@PathVariable UUID eventId) {
        return eventService.get(eventId);
    }
}
