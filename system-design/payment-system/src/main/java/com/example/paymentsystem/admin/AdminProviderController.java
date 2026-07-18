package com.example.paymentsystem.admin;

import com.example.paymentsystem.payment.PaymentProvider;
import com.example.paymentsystem.psp.CircuitBreakerService;
import com.example.paymentsystem.psp.ProviderHealth;
import com.example.paymentsystem.psp.ProviderHealthRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin/providers")
public class AdminProviderController {
    private final ProviderHealthRepository repository;
    private final CircuitBreakerService circuitBreakerService;

    public AdminProviderController(ProviderHealthRepository repository, CircuitBreakerService circuitBreakerService) {
        this.repository = repository;
        this.circuitBreakerService = circuitBreakerService;
    }

    @GetMapping
    public List<ProviderHealth> list() {
        for (PaymentProvider provider : PaymentProvider.values()) {
            repository.save(repository.findById(provider).orElseGet(() -> new ProviderHealth(provider)));
        }
        return repository.findAll();
    }

    @PostMapping("/{provider}/health")
    public void setHealth(@PathVariable PaymentProvider provider, @RequestBody ProviderHealthRequest request) {
        circuitBreakerService.force(provider, request.status());
    }
}
