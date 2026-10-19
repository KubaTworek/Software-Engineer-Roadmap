package com.example.urlshortener.service;

import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.model.ShortUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ShortUrlResponseMapper {
    private final String publicBaseUrl;

    public ShortUrlResponseMapper(@Value("${app.public-base-url}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public CreateShortUrlResponse created(ShortUrl url) {
        return new CreateShortUrlResponse(url.getId(), url.getShortCode(), shortUrl(url.getShortCode()),
                url.getLongUrl(), url.getExpiresAt(), url.getCreatedAt());
    }

    public UrlDetailsResponse details(ShortUrl url) {
        return new UrlDetailsResponse(url.getId(), url.getShortCode(), shortUrl(url.getShortCode()), url.getLongUrl(),
                url.getStatus(), url.getExpiresAt(), url.getCreatedAt(), url.getUpdatedAt(), url.getVersion(),
                url.getBlockedReason(), url.getBlockedAt());
    }

    private String shortUrl(String code) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl).pathSegment(code).build().toUriString();
    }
}
