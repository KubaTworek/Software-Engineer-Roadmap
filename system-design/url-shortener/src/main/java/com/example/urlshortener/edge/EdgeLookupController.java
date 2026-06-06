package com.example.urlshortener.edge;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.region.RegionProperties;
import com.example.urlshortener.storage.DistributedUrlStore;
import com.example.urlshortener.storage.UrlLookupRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/edge")
public class EdgeLookupController {
    private final DistributedUrlStore distributedUrlStore;
    private final EdgeProperties edgeProperties;
    private final RegionProperties regionProperties;
    private final Clock clock;

    public EdgeLookupController(
        DistributedUrlStore distributedUrlStore,
        EdgeProperties edgeProperties,
        RegionProperties regionProperties,
        Clock clock
    ) {
        this.distributedUrlStore = distributedUrlStore;
        this.edgeProperties = edgeProperties;
        this.regionProperties = regionProperties;
        this.clock = clock;
    }

    @GetMapping("/urls/{shortCode}")
    public ResponseEntity<EdgeLookupResponse> lookup(
        @PathVariable String shortCode,
        @RequestHeader(value = "X-Edge-Token", required = false) String token
    ) {
        verifyToken(token);

        UrlLookupRecord record = distributedUrlStore.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        boolean redirectable = record.status() == UrlStatus.ACTIVE && !record.isExpired(Instant.now(clock));
        Duration ttl = redirectable ? regionProperties.getEdgeCacheTtl() : regionProperties.getNegativeCacheTtl();

        if (!redirectable) {
            if (record.status() != UrlStatus.ACTIVE) {
                throw new ShortUrlGoneException(shortCode, "status=" + record.status());
            }
            throw new ShortUrlGoneException(shortCode, "expired");
        }

        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(ttl).cachePublic())
            .body(new EdgeLookupResponse(
                record.shortCode(),
                record.longUrl(),
                record.status(),
                record.expiresAt(),
                record.regionId(),
                ttl.toSeconds(),
                true
            ));
    }

    private void verifyToken(String token) {
        if (!edgeProperties.isEnabled()) {
            throw new AdminUnauthorizedException("Edge lookup is disabled");
        }
        if (token == null || !token.equals(edgeProperties.getInternalToken())) {
            throw new AdminUnauthorizedException("Invalid edge token");
        }
    }
}
