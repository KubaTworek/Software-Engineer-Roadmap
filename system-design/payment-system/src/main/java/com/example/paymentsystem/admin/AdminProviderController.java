package com.example.paymentsystem.admin;

import com.example.paymentsystem.payment.PaymentProvider;
import com.example.paymentsystem.psp.CircuitBreakerService;
import com.example.paymentsystem.psp.ProviderHealthRepository;
import com.example.paymentsystem.psp.ProviderHealthStatus;
import com.example.paymentsystem.audit.AuditService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.security.Principal;
import java.util.Arrays;

@RestController
@RequestMapping("/v1/admin/providers")
public class AdminProviderController {
    private final ProviderHealthRepository repository;
    private final CircuitBreakerService circuitBreakerService;
    private final AuditService audit;

    public AdminProviderController(ProviderHealthRepository repository, CircuitBreakerService circuitBreakerService,
                                   AuditService audit) {
        this.repository = repository;
        this.circuitBreakerService = circuitBreakerService;
        this.audit = audit;
    }

    @GetMapping
    public List<ProviderHealthView> list() {
        // A GET must not create rows. Missing health means the configured default CLOSED state.
        return Arrays.stream(PaymentProvider.values())
                .map(provider -> repository.findById(provider)
                        .map(health -> new ProviderHealthView(provider, health.getStatus(), health.getFailureCount()))
                        .orElseGet(() -> new ProviderHealthView(provider, ProviderHealthStatus.CLOSED, 0)))
                .toList();
    }

    @PostMapping("/{provider}/health")
    public void setHealth(@PathVariable PaymentProvider provider, @RequestBody ProviderHealthRequest request,
                          Principal principal) {
        circuitBreakerService.force(provider, request.status());
        audit.record(principal.getName(), "FORCE_PROVIDER_HEALTH", "PROVIDER", provider.name(), request.status().name());
    }

    public record ProviderHealthView(PaymentProvider provider, ProviderHealthStatus status, int failureCount) { }
}
