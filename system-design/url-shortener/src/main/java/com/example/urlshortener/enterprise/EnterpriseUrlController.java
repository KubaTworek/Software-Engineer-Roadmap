package com.example.urlshortener.enterprise;

import com.example.urlshortener.admin.AdminAuthService;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enterprise")
public class EnterpriseUrlController {
    private final EnterpriseApiKeyService apiKeyService;
    private final EnterpriseProperties properties;
    private final ShortUrlService shortUrlService;
    private final AdminAuthService adminAuthService;

    public EnterpriseUrlController(
        EnterpriseApiKeyService apiKeyService,
        EnterpriseProperties properties,
        ShortUrlService shortUrlService,
        AdminAuthService adminAuthService
    ) {
        this.apiKeyService = apiKeyService;
        this.properties = properties;
        this.shortUrlService = shortUrlService;
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/api-keys")
    public ResponseEntity<CreateEnterpriseApiKeyResponse> createApiKey(
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @Valid @RequestBody CreateEnterpriseApiKeyRequest request
    ) {
        adminAuthService.requireAdmin(adminToken);
        return ResponseEntity.status(201).body(apiKeyService.create(request));
    }

    @PostMapping("/urls/bulk")
    public BulkCreateShortUrlResponse bulkCreate(
        @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
        @Valid @RequestBody BulkCreateShortUrlRequest request
    ) {
        apiKeyService.authenticate(apiKey);
        if (request.urls().size() > properties.getBulkMaxSize()) {
            throw new IllegalArgumentException("Bulk create limit exceeded. Max: " + properties.getBulkMaxSize());
        }

        List<CreateShortUrlResponse> created = request.urls().stream()
            .map(shortUrlService::create)
            .toList();

        return new BulkCreateShortUrlResponse(request.urls().size(), created.size(), created);
    }
}
