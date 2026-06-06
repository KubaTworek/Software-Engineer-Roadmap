package com.example.urlshortener.region;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/region")
public class RegionController {
    private final RegionProperties regionProperties;

    public RegionController(RegionProperties regionProperties) {
        this.regionProperties = regionProperties;
    }

    @GetMapping
    public Map<String, Object> region() {
        return Map.of(
            "regionId", regionProperties.getRegionId(),
            "primaryRegion", regionProperties.getPrimaryRegion(),
            "activeActive", regionProperties.isActiveActive(),
            "acceptsWrites", regionProperties.isPrimaryRegion(),
            "edgeCacheTtlSeconds", regionProperties.getEdgeCacheTtl().toSeconds(),
            "negativeCacheTtlSeconds", regionProperties.getNegativeCacheTtl().toSeconds()
        );
    }
}
