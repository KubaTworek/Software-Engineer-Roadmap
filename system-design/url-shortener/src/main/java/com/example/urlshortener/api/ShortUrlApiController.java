package com.example.urlshortener.api;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.idempotency.IdempotentCreateService;
import com.example.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public command/query boundary. The controller deliberately contains no URL
 * generation or persistence rules: retries are routed through the durable
 * idempotency boundary, while an unkeyed request keeps ordinary POST semantics.
 */
@RestController
@RequestMapping("/api/v1/urls")
public class ShortUrlApiController {
    private final ShortUrlService shortUrlService;
    private final IdempotentCreateService idempotentCreateService;

    public ShortUrlApiController(ShortUrlService shortUrlService,
            IdempotentCreateService idempotentCreateService) {
        this.shortUrlService = shortUrlService;
        this.idempotentCreateService = idempotentCreateService;
    }

    @PostMapping
    public ResponseEntity<CreateShortUrlResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateShortUrlRequest request) {
        CreateShortUrlResponse response = idempotencyKey == null
                ? shortUrlService.create(request)
                : idempotentCreateService.create(idempotencyKey, request);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{shortCode}")
    public UrlDetailsResponse details(@PathVariable String shortCode) {
        return shortUrlService.getDetails(shortCode);
    }
}
