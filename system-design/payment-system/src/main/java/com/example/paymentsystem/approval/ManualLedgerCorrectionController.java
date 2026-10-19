package com.example.paymentsystem.approval;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/ledger/corrections")
public class ManualLedgerCorrectionController {
    private final ManualLedgerCorrectionService service;
    public ManualLedgerCorrectionController(ManualLedgerCorrectionService service) { this.service = service; }

    @PostMapping
    ResponseEntity<Void> post(@RequestHeader("X-Approval-Id") UUID approvalId,
                              @Valid @RequestBody ManualLedgerCorrectionRequest request,
                              Principal principal) {
        service.post(request, approvalId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
