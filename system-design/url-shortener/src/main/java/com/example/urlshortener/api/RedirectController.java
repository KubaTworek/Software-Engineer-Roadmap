package com.example.urlshortener.api;

import com.example.urlshortener.analytics.ClickTrackingService;
import com.example.urlshortener.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final ShortUrlService shortUrlService;
    private final ClickTrackingService clickTrackingService;

    public RedirectController(ShortUrlService shortUrlService, ClickTrackingService clickTrackingService) {
        this.shortUrlService = shortUrlService;
        this.clickTrackingService = clickTrackingService;
    }

    @GetMapping("/{shortCode:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String longUrl = shortUrlService.resolveLongUrl(shortCode);
        clickTrackingService.track(shortCode, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(longUrl));

        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
