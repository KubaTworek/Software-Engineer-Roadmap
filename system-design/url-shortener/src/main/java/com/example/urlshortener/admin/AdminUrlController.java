package com.example.urlshortener.admin;

import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/urls")
public class AdminUrlController {

    private final AdminAuthService adminAuthService;
    private final ShortUrlService shortUrlService;

    public AdminUrlController(AdminAuthService adminAuthService, ShortUrlService shortUrlService) {
        this.adminAuthService = adminAuthService;
        this.shortUrlService = shortUrlService;
    }

    @PostMapping("/{shortCode}/block")
    public UrlDetailsResponse block(
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @PathVariable String shortCode,
        @Valid @RequestBody BlockUrlRequest request
    ) {
        adminAuthService.requireAdmin(adminToken);
        return shortUrlService.block(shortCode, request.reason());
    }

    @PostMapping("/{shortCode}/unblock")
    public UrlDetailsResponse unblock(
        @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
        @PathVariable String shortCode
    ) {
        adminAuthService.requireAdmin(adminToken);
        return shortUrlService.unblock(shortCode);
    }
}
