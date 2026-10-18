package com.example.urlshortener.service;

import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.region.RegionProperties;
import com.example.urlshortener.storage.UrlLookupRecord;
import org.springframework.stereotype.Component;

@Component
public class UrlLookupFactory {
    private final RegionProperties region;

    public UrlLookupFactory(RegionProperties region) { this.region = region; }

    public UrlLookupRecord from(ShortUrl url) {
        return new UrlLookupRecord(url.getShortCode(), url.getLongUrl(), url.getStatus(), url.getExpiresAt(),
                region.getRegionId(), url.getUpdatedAt(), url.getVersion());
    }
}
