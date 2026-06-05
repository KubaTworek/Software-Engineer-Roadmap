package com.example.autocomplete.api;

import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.service.AutocompleteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AutocompleteController {
    private final AutocompleteService autocompleteService;

    public AutocompleteController(AutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        int effectiveLimit = autocompleteService.sanitizeLimit(limit);
        List<SuggestionResponse> suggestions = autocompleteService.autocomplete(query, effectiveLimit)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(new AutocompleteResponse(
                query,
                effectiveLimit,
                suggestions.size(),
                suggestions
        ));
    }

    private SuggestionResponse toResponse(Suggestion suggestion) {
        return new SuggestionResponse(
                suggestion.text(),
                suggestion.type(),
                suggestion.popularity()
        );
    }
}
