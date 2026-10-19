package com.example.ratelimiter.quota;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RequestContext;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Realtime quota is deliberately separated from short-window protection.
 * This Redis counter is an online estimate, not a billing ledger: durable
 * settlement belongs to the usage-event pipeline and reconciliation.
 */
@Service
public class QuotaService {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DefaultRedisScript<Long> ADD_UNTIL_MONTH_RETENTION = new DefaultRedisScript<>("""
            local value = redis.call('INCRBY', KEYS[1], ARGV[1])
            redis.call('EXPIREAT', KEYS[1], ARGV[2])
            return value
            """, Long.class);

    private final StringRedisTemplate redis;
    private final RateLimiterProperties properties;

    public QuotaService(StringRedisTemplate redis, RateLimiterProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public QuotaSnapshot recordAcceptedUsage(RequestContext ctx, long cost) {
        if (!properties.getQuotas().isEnabled()) return new QuotaSnapshot(false, 0, 0, 0);
        if (ctx.tenantId() == null || ctx.tenantId().isBlank()) {
            return new QuotaSnapshot(false, 0, 0, 0);
        }
        String tenant = ctx.tenantId();
        Instant eventTime = Instant.ofEpochMilli(ctx.timestampMs());
        YearMonth month = YearMonth.from(eventTime.atZone(ZoneOffset.UTC));
        long expiresAt = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC)
                .plusDays(40).toEpochSecond();
        Long used = redis.execute(ADD_UNTIL_MONTH_RETENTION, List.of(key(tenant, month)),
                String.valueOf(cost), String.valueOf(expiresAt));
        long current = used == null ? 0 : used;
        long limit = properties.getQuotas().getMonthlyDefaultLimit();
        return new QuotaSnapshot(true, limit, current, Math.max(0, limit - current));
    }

    public Map<String, String> getTenantQuota(String tenantId) {
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        String used = redis.opsForValue().get(key(requiredTenant(tenantId), month));
        return Map.of("tenantId", tenantId, "month", MONTH.format(month),
                "limit", String.valueOf(properties.getQuotas().getMonthlyDefaultLimit()),
                "used", used == null ? "0" : used);
    }

    private String key(String tenant, YearMonth month) {
        return "quota:" + tenant + ":" + MONTH.format(month);
    }

    private String requiredTenant(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Tenant identity is required for quota accounting");
        }
        return tenant;
    }

    public record QuotaSnapshot(boolean enabled, long limit, long used, long remaining) {}
}
