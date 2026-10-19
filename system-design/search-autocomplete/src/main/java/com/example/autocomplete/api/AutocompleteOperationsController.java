package com.example.autocomplete.api;

import com.example.autocomplete.index.RealtimeDeltaIndex;
import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.model.SuggestionMetrics;
import com.example.autocomplete.policy.SafetyPolicyFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Operatorski control plane; SecurityConfig wymaga roli OPERATOR. */
@RestController
@RequestMapping("/api/v1/autocomplete/operations")
public class AutocompleteOperationsController {
    private final SafetyPolicyFilter policy;
    private final RealtimeDeltaIndex delta;

    public AutocompleteOperationsController(SafetyPolicyFilter policy, RealtimeDeltaIndex delta) {
        this.policy = policy;
        this.delta = delta;
    }

    @PutMapping("/policy/blocked-terms")
    ResponseEntity<PolicyVersion> replacePolicy(@Valid @RequestBody BlockedTermsRequest request) {
        policy.replaceBlockedTerms(request.terms());
        return ResponseEntity.ok(new PolicyVersion(policy.version()));
    }

    @PostMapping("/delta")
    ResponseEntity<Void> addDelta(@Valid @RequestBody DeltaSuggestionRequest request) {
        Suggestion suggestion = new Suggestion(
                request.id(), request.text(), "query",
                new SuggestionMetrics(request.popularity(), request.ctr(), request.conversionRate(),
                        request.freshness(), request.quality()),
                request.aliases(), request.categories(), request.locales(), request.countries(), false
        );
        boolean accepted = delta.add(suggestion, request.sequence(), Duration.ofSeconds(request.ttlSeconds()));
        return accepted ? ResponseEntity.accepted().build() : ResponseEntity.unprocessableEntity().build();
    }

    public record BlockedTermsRequest(@NotEmpty Set<@NotBlank String> terms) {}
    public record PolicyVersion(long version) {}
    public record DeltaSuggestionRequest(
            @NotBlank String id,
            @NotBlank @Size(max = 200) String text,
            @Positive long sequence,
            @Positive long ttlSeconds,
            int popularity,
            double ctr,
            double conversionRate,
            double freshness,
            double quality,
            List<String> aliases,
            Set<String> categories,
            Set<String> locales,
            Set<String> countries
    ) {}
}
