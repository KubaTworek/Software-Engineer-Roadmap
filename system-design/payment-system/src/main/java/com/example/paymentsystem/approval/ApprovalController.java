package com.example.paymentsystem.approval;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;

/** Backoffice boundary for requesting and granting high-risk operations. */
@RestController
@RequestMapping("/v1/approvals")
public class ApprovalController {
    private final ApprovalService service;
    public ApprovalController(ApprovalService service) { this.service = service; }

    @PostMapping
    ApprovalRequest request(@Valid @RequestBody CreateApprovalRequest request, Principal principal) {
        return service.request(request.operation(), request.targetId(), request.amount(), request.currency(),
                principal.getName());
    }

    @PostMapping("/{approvalId}/approve")
    ApprovalRequest approve(@PathVariable UUID approvalId, Principal principal) {
        return service.approve(approvalId, principal.getName());
    }
}
