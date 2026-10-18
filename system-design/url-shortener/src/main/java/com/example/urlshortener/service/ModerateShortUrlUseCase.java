package com.example.urlshortener.service;

import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.outbox.UrlProjectionOutboxService;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.repository.UrlModerationAuditRepository;
import com.example.urlshortener.model.UrlModerationAudit;
import com.example.urlshortener.exception.StaleUrlVersionException;
import com.example.urlshortener.exception.OwnershipDeniedException;
import com.example.urlshortener.validation.UrlValidator;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerateShortUrlUseCase {
    private final ShortUrlRepository repository;
    private final Clock clock;
    private final UrlProjectionOutboxService outbox;
    private final UrlLookupFactory lookups;
    private final ShortUrlResponseMapper responses;
    private final UrlModerationAuditRepository audit;
    private final UrlValidator validator;

    public ModerateShortUrlUseCase(ShortUrlRepository repository, Clock clock, UrlProjectionOutboxService outbox,
            UrlLookupFactory lookups, ShortUrlResponseMapper responses, UrlModerationAuditRepository audit,
            UrlValidator validator) {
        this.repository = repository;
        this.clock = clock;
        this.outbox = outbox;
        this.lookups = lookups;
        this.responses = responses;
        this.audit = audit;
        this.validator = validator;
    }

    @Transactional
    public UrlDetailsResponse block(String code, String reason) {
        return block(code, reason, null, "admin");
    }

    @Transactional
    public UrlDetailsResponse block(String code, String reason, Long expectedVersion, String actor) {
        ShortUrl url = load(code);
        requireVersion(url, expectedVersion);
        url.block(reason, Instant.now(clock));
        return persist(url, "BLOCK", actor, reason);
    }

    @Transactional
    public UrlDetailsResponse unblock(String code) {
        return unblock(code, null, "admin");
    }

    @Transactional
    public UrlDetailsResponse unblock(String code, Long expectedVersion, String actor) {
        ShortUrl url = load(code);
        requireVersion(url, expectedVersion);
        if (url.isExpired(Instant.now(clock))) {
            throw new IllegalArgumentException("Expired URL cannot be unblocked");
        }
        url.unblock();
        return persist(url, "UNBLOCK", actor, null);
    }

    @Transactional
    public UrlDetailsResponse delete(String code, Long expectedVersion, String actor) {
        ShortUrl url = load(code);
        requireVersion(url, expectedVersion);
        url.delete();
        return persist(url, "DELETE", actor, null);
    }

    @Transactional
    public UrlDetailsResponse deleteOwned(String code, Long ownerId) {
        ShortUrl url = load(code);
        if (url.getUserId() == null || !url.getUserId().equals(ownerId)) throw new OwnershipDeniedException();
        url.delete();
        return persist(url, "OWNER_DELETE", "enterprise:" + ownerId, null);
    }

    @Transactional
    public UrlDetailsResponse updateOwned(String code, Long ownerId, String destination, Long expectedVersion) {
        ShortUrl url = load(code);
        if (url.getUserId() == null || !url.getUserId().equals(ownerId)) throw new OwnershipDeniedException();
        requireVersion(url, expectedVersion);
        url.updateDestination(validator.validatePublicHttpUrl(destination).toString());
        return persist(url, "UPDATE_DESTINATION", "enterprise:" + ownerId, null);
    }

    private UrlDetailsResponse persist(ShortUrl url, String action, String actor, String reason) {
        ShortUrl saved = repository.saveAndFlush(url);
        // @Version protects a concurrent moderation decision from lost update.
        outbox.enqueue(lookups.from(saved));
        audit.save(new UrlModerationAudit(saved.getShortCode(), action, actor == null ? "admin" : actor,
                reason, Instant.now(clock), saved.getVersion()));
        return responses.details(saved);
    }

    private void requireVersion(ShortUrl url, Long expected) {
        if (expected != null && expected != url.getVersion()) {
            throw new StaleUrlVersionException(expected, url.getVersion());
        }
    }

    private ShortUrl load(String code) {
        return repository.findByShortCode(code).orElseThrow(() -> new ShortUrlNotFoundException(code));
    }
}
