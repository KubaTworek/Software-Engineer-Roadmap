package com.example.urlshortener.api;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/urls")
public class ShortUrlApiController {
    private final ShortUrlService shortUrlService;
    public ShortUrlApiController(ShortUrlService shortUrlService) { this.shortUrlService = shortUrlService; }

    @PostMapping
    public ResponseEntity<CreateShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        return ResponseEntity.status(201).body(shortUrlService.create(request));
    }

    @GetMapping("/{shortCode}")
    public UrlDetailsResponse details(@PathVariable String shortCode) {
        return shortUrlService.getDetails(shortCode);
    }
}
