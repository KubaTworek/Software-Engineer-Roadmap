package com.example.urlshortener.storage;

import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.region.RegionProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDistributedUrlStore implements DistributedUrlStore {
    private final ShortUrlRepository repository;
    private final RegionProperties regionProperties;

    public JpaDistributedUrlStore(ShortUrlRepository repository, RegionProperties regionProperties) {
        this.repository = repository;
        this.regionProperties = regionProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UrlLookupRecord> findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode).map(this::toRecord);
    }

    @Override
    public void upsert(UrlLookupRecord record) {
        // JPA entity remains the source of truth in the reference implementation.
        // External distributed stores can implement this as an idempotent put.
    }

    @Override
    public void delete(String shortCode) {
        // External distributed stores can implement this as a delete/tombstone.
    }

    private UrlLookupRecord toRecord(ShortUrl entity) {
        return new UrlLookupRecord(
            entity.getShortCode(),
            entity.getLongUrl(),
            entity.getStatus(),
            entity.getExpiresAt(),
            regionProperties.getRegionId(),
            entity.getUpdatedAt()
        );
    }
}
