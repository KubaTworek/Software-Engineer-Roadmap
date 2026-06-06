package com.example.urlshortener.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DistributedStorageReplicationService {
    private static final Logger log = LoggerFactory.getLogger(DistributedStorageReplicationService.class);
    private final DistributedUrlStore distributedUrlStore;

    public DistributedStorageReplicationService(DistributedUrlStore distributedUrlStore) {
        this.distributedUrlStore = distributedUrlStore;
    }

    public void publishUpsert(UrlLookupRecord record) {
        distributedUrlStore.upsert(record);
        log.debug("Distributed URL upsert emitted for shortCode={} region={}", record.shortCode(), record.regionId());
    }

    public void publishDelete(String shortCode) {
        distributedUrlStore.delete(shortCode);
        log.debug("Distributed URL delete emitted for shortCode={}", shortCode);
    }
}
