package com.example.urlshortener.edge;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.region.RegionProperties;
import com.example.urlshortener.security.SecretSupport;
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

/** Authenticated origin contract used by a CDN worker to build a short-lived edge projection. */
@RestController
@RequestMapping("/internal/edge")
public class EdgeLookupController {
    private final DistributedUrlStore store;
    private final EdgeProperties edge;
    private final RegionProperties region;
    private final Clock clock;

    public EdgeLookupController(DistributedUrlStore store, EdgeProperties edge, RegionProperties region, Clock clock) {
        this.store = store;
        this.edge = edge;
        this.region = region;
        this.clock = clock;
    }

    @GetMapping("/urls/{code}")
    public ResponseEntity<EdgeLookupResponse> lookup(@PathVariable String code,
            @RequestHeader(value = "X-Edge-Token", required = false) String token) {
        verifyToken(token);
        UrlLookupRecord record = store.findByShortCode(code).orElseThrow(() -> new ShortUrlNotFoundException(code));
        boolean redirectable = record.status() == UrlStatus.ACTIVE && !record.isExpired(Instant.now(clock));
        Duration ttl = redirectable ? region.getEdgeCacheTtl() : region.getNegativeCacheTtl();
        if (!redirectable) {
            throw new ShortUrlGoneException(code, record.status() == UrlStatus.ACTIVE ? "expired" : "status=" + record.status());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(ttl).cachePublic())
                .body(new EdgeLookupResponse(record.shortCode(), record.longUrl(), record.status(), record.expiresAt(),
                        record.regionId(), ttl.toSeconds(), true));
    }

    private void verifyToken(String token) {
        if (!edge.isEnabled()) throw new AdminUnauthorizedException("Edge lookup is disabled");
        boolean matches = SecretSupport.constantTimeEquals(token, edge.getInternalToken());
        for (String previous : edge.getPreviousInternalTokens()) matches |= SecretSupport.constantTimeEquals(token, previous);
        if (!matches) throw new AdminUnauthorizedException("Invalid edge token");
    }
}
