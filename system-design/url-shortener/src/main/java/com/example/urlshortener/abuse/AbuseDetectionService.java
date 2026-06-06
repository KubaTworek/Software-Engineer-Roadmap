package com.example.urlshortener.abuse;

import com.example.urlshortener.queue.ClickMessage;
import com.example.urlshortener.service.ShortUrlCacheService;
import com.example.urlshortener.service.ShortUrlService;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AbuseDetectionService {
    private static final Logger log = LoggerFactory.getLogger(AbuseDetectionService.class);

    private final AbuseDetectionProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final UserAgentClassifier userAgentClassifier;
    private final ShortUrlService shortUrlService;
    private final ShortUrlCacheService cacheService;

    public AbuseDetectionService(
        AbuseDetectionProperties properties,
        StringRedisTemplate redisTemplate,
        UserAgentClassifier userAgentClassifier,
        ShortUrlService shortUrlService,
        ShortUrlCacheService cacheService
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.userAgentClassifier = userAgentClassifier;
        this.shortUrlService = shortUrlService;
        this.cacheService = cacheService;
    }

    public AbuseVerdict evaluate(ClickMessage message) {
        String deviceType = userAgentClassifier.deviceType(message.userAgent());
        String browser = userAgentClassifier.browser(message.userAgent());

        if (!properties.enabled()) {
            return AbuseVerdict.clean(deviceType, browser);
        }

        String reason = firstSuspiciousReason(message);
        boolean suspicious = reason != null;

        if (tooManyClicksFromSameIp(message)) {
            suspicious = true;
            reason = appendReason(reason, "high_click_rate_from_same_ip");
        }

        boolean shouldAutoBlock = false;
        if (suspicious && tooManySuspiciousEventsForShortCode(message.shortCode())) {
            shouldAutoBlock = true;
            reason = appendReason(reason, "auto_block_threshold_exceeded");
        }

        if (shouldAutoBlock) {
            try {
                shortUrlService.block(message.shortCode(), "auto abuse detection: " + reason);
                cacheService.evict(message.shortCode());
                log.warn("Auto-blocked shortCode={} reason={}", message.shortCode(), reason);
            } catch (Exception exception) {
                log.warn("Failed to auto-block shortCode={} reason={}", message.shortCode(), reason, exception);
            }
        }

        return suspicious ? AbuseVerdict.suspicious(reason, shouldAutoBlock, deviceType, browser) : AbuseVerdict.clean(deviceType, browser);
    }

    private String firstSuspiciousReason(ClickMessage message) {
        String userAgent = message.userAgent() == null ? "" : message.userAgent().toLowerCase(Locale.ROOT);
        if (userAgent.isBlank()) return "missing_user_agent";

        for (String token : properties.suspiciousUserAgentTokens()) {
            if (!token.isBlank() && userAgent.contains(token.toLowerCase(Locale.ROOT))) {
                return "suspicious_user_agent:" + token;
            }
        }

        String domain = referrerDomain(message.referrer());
        if (domain != null) {
            for (String blockedDomain : properties.blockedReferrerDomains()) {
                if (!blockedDomain.isBlank() && domain.endsWith(blockedDomain.toLowerCase(Locale.ROOT))) {
                    return "blocked_referrer_domain:" + blockedDomain;
                }
            }
        }

        return null;
    }

    private boolean tooManyClicksFromSameIp(ClickMessage message) {
        if (message.ipAddress() == null || message.ipAddress().isBlank()) return false;
        try {
            String key = "abuse:ip:" + message.shortCode() + ":" + sha256(message.ipAddress());
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) redisTemplate.expire(key, properties.ipWindow());
            return value != null && value > properties.suspiciousClicksPerIpPerShortCode();
        } catch (Exception exception) {
            log.debug("Redis unavailable for IP abuse detection", exception);
            return false;
        }
    }

    private boolean tooManySuspiciousEventsForShortCode(String shortCode) {
        try {
            String key = "abuse:short:" + shortCode;
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) redisTemplate.expire(key, properties.shortCodeWindow());
            return value != null && value > properties.autoBlockSuspiciousEvents();
        } catch (Exception exception) {
            log.debug("Redis unavailable for short-code abuse detection", exception);
            return false;
        }
    }

    private String referrerDomain(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            String host = URI.create(referrer).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String appendReason(String current, String next) {
        return current == null || current.isBlank() ? next : current + "," + next;
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
    }
}
