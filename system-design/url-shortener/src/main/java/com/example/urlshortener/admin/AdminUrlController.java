package com.example.urlshortener.admin;

import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.service.ShortUrlService;
import com.example.urlshortener.service.ProjectionReconciliationReport;
import com.example.urlshortener.service.ProjectionReconciliationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/** Administrative lifecycle boundary. In production the actor comes from IAM, not a caller header. */
@RestController
@RequestMapping("/api/v1/admin/urls")
public class AdminUrlController {
    private final AdminAuthService auth;
    private final ShortUrlService urls;
    private final ProjectionReconciliationService reconciliation;

    public AdminUrlController(AdminAuthService auth, ShortUrlService urls,
            ProjectionReconciliationService reconciliation) {
        this.auth = auth;
        this.urls = urls;
        this.reconciliation = reconciliation;
    }

    @PostMapping("/{code}/block")
    public UrlDetailsResponse block(@RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "X-Admin-Actor", defaultValue = "admin") String actor,
            @PathVariable String code, @Valid @RequestBody BlockUrlRequest request) {
        auth.requireAdmin(token);
        return urls.block(code, request.reason(), expectedVersion(ifMatch), actor);
    }

    @PostMapping("/{code}/unblock")
    public UrlDetailsResponse unblock(@RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "X-Admin-Actor", defaultValue = "admin") String actor,
            @PathVariable String code) {
        auth.requireAdmin(token);
        return urls.unblock(code, expectedVersion(ifMatch), actor);
    }

    @DeleteMapping("/{code}")
    public UrlDetailsResponse delete(@RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "X-Admin-Actor", defaultValue = "admin") String actor,
            @PathVariable String code) {
        auth.requireAdmin(token);
        return urls.delete(code, expectedVersion(ifMatch), actor);
    }

    @PostMapping("/projections/reconcile")
    public ProjectionReconciliationReport reconcile(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "1000") int limit,
            @RequestParam(defaultValue = "false") boolean repair) {
        auth.requireAdmin(token);
        return reconciliation.reconcile(limit, repair);
    }

    private Long expectedVersion(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value.replace("\"", "").trim()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("If-Match must contain the numeric URL version"); }
    }
}
