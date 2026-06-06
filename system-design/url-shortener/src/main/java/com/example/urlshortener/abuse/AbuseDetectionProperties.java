package com.example.urlshortener.abuse;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.abuse")
public record AbuseDetectionProperties(
    boolean enabled,
    int suspiciousClicksPerIpPerShortCode,
    Duration ipWindow,
    int autoBlockSuspiciousEvents,
    Duration shortCodeWindow,
    List<String> blockedReferrerDomains,
    List<String> suspiciousUserAgentTokens
) {
    public AbuseDetectionProperties {
        if (suspiciousClicksPerIpPerShortCode <= 0) suspiciousClicksPerIpPerShortCode = 120;
        if (ipWindow == null) ipWindow = Duration.ofMinutes(5);
        if (autoBlockSuspiciousEvents <= 0) autoBlockSuspiciousEvents = 500;
        if (shortCodeWindow == null) shortCodeWindow = Duration.ofMinutes(15);
        if (blockedReferrerDomains == null) blockedReferrerDomains = List.of();
        if (suspiciousUserAgentTokens == null) suspiciousUserAgentTokens = List.of("sqlmap", "nikto", "masscan", "zgrab", "python-requests", "go-http-client");
    }
}
