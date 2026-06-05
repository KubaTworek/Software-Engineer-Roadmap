package com.example.urlshortener.api;

import com.example.urlshortener.service.ShortUrlService;
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
    public RedirectController(ShortUrlService shortUrlService) { this.shortUrlService = shortUrlService; }

    @GetMapping("/{shortCode:[A-Za-z0-9]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String longUrl = shortUrlService.resolveLongUrl(shortCode);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(longUrl));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
