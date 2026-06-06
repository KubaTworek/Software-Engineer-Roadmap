package com.example.urlshortener.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.edge")
public class EdgeProperties {
    private boolean enabled = true;
    private String internalToken = "local-edge-token";
    private String cacheControlForRedirects = "public, max-age=60, stale-while-revalidate=300";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
    public String getCacheControlForRedirects() { return cacheControlForRedirects; }
    public void setCacheControlForRedirects(String cacheControlForRedirects) { this.cacheControlForRedirects = cacheControlForRedirects; }
}
