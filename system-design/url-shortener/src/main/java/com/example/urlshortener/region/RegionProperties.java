package com.example.urlshortener.region;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.region")
public class RegionProperties {
    /** Stable region identifier, e.g. eu-central-1 or us-east-1. */
    private String regionId = "local";
    /** Region that owns writes when activeActive is false. */
    private String primaryRegion = "local";
    /** Enables accepting writes in every region. Requires globally unique ID generation. */
    private boolean activeActive = false;
    /** TTL returned to edge/CDN for positive lookups. */
    private Duration edgeCacheTtl = Duration.ofMinutes(10);
    /** TTL returned to edge/CDN for blocked, expired or missing links. */
    private Duration negativeCacheTtl = Duration.ofSeconds(30);

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }
    public String getPrimaryRegion() { return primaryRegion; }
    public void setPrimaryRegion(String primaryRegion) { this.primaryRegion = primaryRegion; }
    public boolean isActiveActive() { return activeActive; }
    public void setActiveActive(boolean activeActive) { this.activeActive = activeActive; }
    public Duration getEdgeCacheTtl() { return edgeCacheTtl; }
    public void setEdgeCacheTtl(Duration edgeCacheTtl) { this.edgeCacheTtl = edgeCacheTtl; }
    public Duration getNegativeCacheTtl() { return negativeCacheTtl; }
    public void setNegativeCacheTtl(Duration negativeCacheTtl) { this.negativeCacheTtl = negativeCacheTtl; }
    public boolean isPrimaryRegion() { return activeActive || regionId.equals(primaryRegion); }
}
