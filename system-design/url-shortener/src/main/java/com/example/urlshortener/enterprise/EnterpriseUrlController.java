package com.example.urlshortener.enterprise;

import com.example.urlshortener.admin.AdminAuthService;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-key authenticated ownership boundary for bulk create and URL lifecycle. */
@RestController
@RequestMapping("/api/v1/enterprise")
public class EnterpriseUrlController {
    private final EnterpriseApiKeyService keys;
    private final EnterpriseProperties properties;
    private final ShortUrlService urls;
    private final AdminAuthService admins;
    private final IdempotentBulkCreateService idempotentBulk;

    public EnterpriseUrlController(EnterpriseApiKeyService keys, EnterpriseProperties properties,
            ShortUrlService urls, AdminAuthService admins, IdempotentBulkCreateService idempotentBulk) {
        this.keys = keys; this.properties = properties; this.urls = urls; this.admins = admins;
        this.idempotentBulk = idempotentBulk;
    }

    @PostMapping("/api-keys")
    public ResponseEntity<CreateEnterpriseApiKeyResponse> createApiKey(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @Valid @RequestBody CreateEnterpriseApiKeyRequest request) {
        admins.requireAdmin(token);
        return ResponseEntity.status(201).body(keys.create(request));
    }

    @PostMapping("/urls/bulk")
    public BulkCreateShortUrlResponse bulkCreate(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody BulkCreateShortUrlRequest request) {
        EnterprisePrincipal principal = keys.authenticate(apiKey);
        if (request.urls().size() > properties.getBulkMaxSize()) {
            throw new IllegalArgumentException("Bulk create limit exceeded. Max: " + properties.getBulkMaxSize());
        }
        if (idempotencyKey != null) {
            return idempotentBulk.create(principal.id(), idempotencyKey, request);
        }
        List<CreateShortUrlResponse> created = request.urls().stream().map(url -> urls.create(url, principal.id())).toList();
        return new BulkCreateShortUrlResponse(request.urls().size(), created.size(), created);
    }

    @DeleteMapping("/urls/{code}")
    public UrlDetailsResponse deleteOwned(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @PathVariable String code) {
        return urls.deleteOwned(code, keys.authenticate(apiKey).id());
    }

    @PatchMapping("/urls/{code}")
    public UrlDetailsResponse updateOwned(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @PathVariable String code, @Valid @RequestBody UpdateDestinationRequest request) {
        return urls.updateOwned(code, keys.authenticate(apiKey).id(), request.longUrl(), parseVersion(ifMatch));
    }

    private Long parseVersion(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value.replace("\"", "").trim()); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("If-Match must contain the numeric URL version"); }
    }
}
