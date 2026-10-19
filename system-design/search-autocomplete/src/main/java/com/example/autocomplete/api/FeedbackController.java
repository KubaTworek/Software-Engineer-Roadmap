package com.example.autocomplete.api;

import com.example.autocomplete.feedback.FeedbackStore;
import com.example.autocomplete.feedback.FeedbackType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
    private final FeedbackStore store;

    public FeedbackController(FeedbackStore store) {
        this.store = store;
    }

    @PostMapping
    ResponseEntity<Void> record(@Valid @RequestBody FeedbackRequest body,
                                Authentication authentication, HttpServletRequest request) {
        String user = authentication == null ? "anonymous" : authentication.getName();
        boolean created = store.record(body.eventId(), body.type(), user, body.sessionId(), body.query(),
                body.suggestionIds(), request.getRemoteAddr());
        return created ? ResponseEntity.accepted().build() : ResponseEntity.ok().build();
    }

    public record FeedbackRequest(
            @NotBlank @Size(max = 128) String eventId,
            @NotNull FeedbackType type,
            @NotBlank @Size(max = 128) String sessionId,
            @NotBlank @Size(max = 100) String query,
            @NotEmpty @Size(max = 20) List<@NotBlank String> suggestionIds
    ) {}
}
