package com.example.urlshortener.edge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Local adapter makes the production boundary visible without pretending to call a CDN. */
@Component
public class LoggingEdgeCacheInvalidator implements EdgeCacheInvalidator {
    private static final Logger log = LoggerFactory.getLogger(LoggingEdgeCacheInvalidator.class);
    @Override public void purge(String shortCode) {
        log.info("Edge purge requested shortCode={}; configure a CDN adapter in production", shortCode);
    }
}
