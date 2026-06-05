package com.example.urlshortener.service;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.validation.UrlValidator;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ShortUrlService {

    private final ShortUrlRepository repository;
    private final Base62Encoder base62Encoder;
    private final UrlValidator urlValidator;
    private final Clock clock;
    private final String publicBaseUrl;

    public ShortUrlService(ShortUrlRepository repository, Base62Encoder base62Encoder, UrlValidator urlValidator, Clock clock, @Value("${app.public-base-url}") String publicBaseUrl) {
        this.repository = repository;
        this.base62Encoder = base62Encoder;
        this.urlValidator = urlValidator;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request) {
        URI normalizedUrl = urlValidator.validatePublicHttpUrl(request.longUrl());
        ShortUrl entity = new ShortUrl(normalizedUrl.toString(), request.expiresAt());
        ShortUrl savedWithoutCode = repository.saveAndFlush(entity);
        String shortCode = base62Encoder.encode(savedWithoutCode.getId());
        savedWithoutCode.setShortCode(shortCode);
        ShortUrl saved = repository.save(savedWithoutCode);
        return new CreateShortUrlResponse(saved.getId(), saved.getShortCode(), buildShortUrl(saved.getShortCode()), saved.getLongUrl(), saved.getExpiresAt(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public String resolveLongUrl(String shortCode) {
        ShortUrl entity = repository.findByShortCode(shortCode).orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
        validateResolvable(entity, shortCode);
        return entity.getLongUrl();
    }

    @Transactional(readOnly = true)
    public UrlDetailsResponse getDetails(String shortCode) {
        ShortUrl entity = repository.findByShortCode(shortCode).orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
        return new UrlDetailsResponse(entity.getId(), entity.getShortCode(), buildShortUrl(entity.getShortCode()), entity.getLongUrl(), entity.getStatus(), entity.getExpiresAt(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private void validateResolvable(ShortUrl entity, String shortCode) {
        if (entity.getStatus() != UrlStatus.ACTIVE) throw new ShortUrlGoneException(shortCode, "status=" + entity.getStatus());
        Instant now = Instant.now(clock);
        if (entity.isExpired(now)) throw new ShortUrlGoneException(shortCode, "expired");
    }

    private String buildShortUrl(String shortCode) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl).pathSegment(shortCode).build().toUriString();
    }
}
