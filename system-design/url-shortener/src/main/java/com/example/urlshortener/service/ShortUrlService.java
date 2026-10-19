package com.example.urlshortener.service;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import org.springframework.stereotype.Service;

/** Thin educational facade; each method delegates to one transactionally explicit use case. */
@Service
public class ShortUrlService {
    private final CreateShortUrlUseCase create;
    private final ResolveShortUrlUseCase resolve;
    private final ModerateShortUrlUseCase moderate;
    private final ShortUrlQuery query;

    public ShortUrlService(CreateShortUrlUseCase create, ResolveShortUrlUseCase resolve, ModerateShortUrlUseCase moderate, ShortUrlQuery query) {
        this.create = create;
        this.resolve = resolve;
        this.moderate = moderate;
        this.query = query;
    }

    public CreateShortUrlResponse create(CreateShortUrlRequest request) { return create.create(request); }
    public CreateShortUrlResponse create(CreateShortUrlRequest request, Long ownerId) { return create.create(request, ownerId); }
    public String resolveLongUrl(String shortCode) { return resolve.resolve(shortCode); }
    public UrlDetailsResponse getDetails(String shortCode) { return query.details(shortCode); }
    public UrlDetailsResponse block(String shortCode, String reason) { return moderate.block(shortCode, reason); }
    public UrlDetailsResponse unblock(String shortCode) { return moderate.unblock(shortCode); }
    public UrlDetailsResponse block(String code, String reason, Long version, String actor) { return moderate.block(code, reason, version, actor); }
    public UrlDetailsResponse unblock(String code, Long version, String actor) { return moderate.unblock(code, version, actor); }
    public UrlDetailsResponse delete(String code, Long version, String actor) { return moderate.delete(code, version, actor); }
    public UrlDetailsResponse deleteOwned(String code, Long ownerId) { return moderate.deleteOwned(code, ownerId); }
    public UrlDetailsResponse updateOwned(String code, Long ownerId, String destination, Long version) {
        return moderate.updateOwned(code, ownerId, destination, version);
    }
}
