package com.example.urlshortener.service;

import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.repository.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlQuery {
    private final ShortUrlRepository repository;
    private final ShortUrlResponseMapper responses;

    public ShortUrlQuery(ShortUrlRepository repository, ShortUrlResponseMapper responses) {
        this.repository = repository;
        this.responses = responses;
    }

    @Transactional(readOnly = true)
    public UrlDetailsResponse details(String code) {
        return repository.findByShortCode(code).map(responses::details)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
    }
}
