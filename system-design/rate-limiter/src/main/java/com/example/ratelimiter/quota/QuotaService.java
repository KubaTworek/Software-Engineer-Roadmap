package com.example.ratelimiter.quota;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RequestContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * QuotaService odpowiada za zliczanie długoterminowego użycia API.
 *
 * To jest inna warstwa niż klasyczny rate limiting.
 *
 * Rate limiting:
 * - chroni system przed nadmiernym ruchem w krótkim czasie,
 * - np. 100 requestów na minutę.
 *
 * Quota:
 * - kontroluje miesięczne zużycie klienta/tenanta,
 * - np. 1 000 000 requestów miesięcznie.
 *
 * W tym projekcie quota jest zapisywana w Redisie jako prosty licznik miesięczny.
 * To jest dobre uproszczenie edukacyjne, ale w pełnej produkcji quota/billing
 * często trafia też do trwałej bazy danych albo systemu analitycznego.
 */
@Service
public class QuotaService {

    /**
     * Redis służy tutaj jako storage liczników quota.
     *
     * Dla każdego tenanta i miesiąca trzymamy osobny licznik.
     *
     * Przykład klucza:
     *
     * quota:tenant-123:2026-06
     */
    private final StringRedisTemplate redis;

    /**
     * Konfiguracja Rate Limitera.
     *
     * Z niej pobieramy:
     * - czy quota jest włączona,
     * - domyślny miesięczny limit.
     */
    private final RateLimiterProperties properties;

    public QuotaService(StringRedisTemplate redis, RateLimiterProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * Rejestruje zużycie dla zaakceptowanego requestu.
     *
     * Ta metoda powinna być wywoływana tylko wtedy, gdy request przeszedł
     * przez Rate Limiter, czyli allowed=true.
     *
     * Jeśli request został zablokowany 429, nie powinien zużywać quota,
     * bo operacja biznesowa nie została wykonana.
     *
     * Parametr cost pozwala liczyć droższe endpointy mocniej.
     *
     * Przykład:
     * - GET /api/users      -> cost = 1
     * - POST /api/payments  -> cost = 5
     * - POST /api/exports   -> cost = 50
     */
    public QuotaSnapshot recordAcceptedUsage(RequestContext ctx, long cost) {
        /*
         * Jeśli quotas są wyłączone w konfiguracji,
         * nie zapisujemy nic do Redisa.
         *
         * Zwracamy snapshot z enabled=false, żeby caller mógł jednoznacznie
         * odróżnić brak quota od realnego wyniku.
         */
        if (!properties.getQuotas().isEnabled()) {
            return new QuotaSnapshot(false, 0, 0, 0);
        }

        /*
         * Quota jest liczona per tenant.
         *
         * Jeśli request nie ma tenantId, używamy "unknown".
         * To zabezpiecza przed NullPointerException i pozwala nadal zliczać
         * ruch bez przypisanego tenanta.
         *
         * W produkcji lepiej byłoby jasno zdecydować, czy request bez tenantId
         * ma być odrzucany, czy przypisywany do osobnej kategorii.
         */
        String tenant = ctx.tenantId() == null || ctx.tenantId().isBlank()
                ? "unknown"
                : ctx.tenantId();

        /*
         * Quota jest liczona miesięcznie w UTC.
         *
         * Użycie UTC jest rozsądne, bo unika problemów ze strefami czasowymi
         * i daje jeden wspólny standard dla regionów.
         *
         * Format:
         * yyyy-MM, np. 2026-06.
         */
        String month = YearMonth.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));

        /*
         * Klucz Redis dla quoty danego tenanta w danym miesiącu.
         *
         * Przykład:
         * quota:tenant-123:2026-06
         */
        String key = "quota:" + tenant + ":" + month;

        /*
         * Atomowo zwiększamy licznik zużycia o koszt requestu.
         *
         * INCRBY w Redisie jest atomowe,
         * więc równoległe requesty nie zgubią inkrementacji.
         */
        Long used = redis.opsForValue().increment(key, cost);

        /*
         * TTL 40 dni sprawia, że miesięczny licznik nie zostaje w Redisie na zawsze.
         *
         * 40 dni daje bufor po zakończeniu miesiąca,
         * np. na dashboardy, debug albo opóźnione sprawdzanie zużycia.
         *
         * Uwaga: expire jest ustawiane przy każdym zapisie,
         * więc TTL przesuwa się wraz z aktywnością.
         */
        redis.expire(key, java.time.Duration.ofDays(40));

        /*
         * Limit miesięczny jest domyślny i globalny.
         *
         * W bardziej zaawansowanej wersji warto mieć osobne limity:
         * - per plan,
         * - per tenant,
         * - per kontrakt,
         * - per produkt/API.
         */
        long limit = properties.getQuotas().getMonthlyDefaultLimit();

        long currentUsed = used == null ? 0 : used;

        /*
         * Snapshot zwraca aktualny stan quota po zapisaniu użycia.
         */
        return new QuotaSnapshot(
                true,
                limit,
                currentUsed,
                Math.max(0, limit - currentUsed)
        );
    }

    /**
     * Zwraca aktualne zużycie quota dla danego tenanta w bieżącym miesiącu.
     *
     * Ten endpoint jest używany przez AdminController:
     *
     * GET /admin/tenants/{tenantId}/quota
     *
     * Dzięki temu można szybko sprawdzić:
     * - jaki tenant,
     * - jaki miesiąc,
     * - jaki limit,
     * - ile już zostało zużyte.
     */
    public Map<String, String> getTenantQuota(String tenantId) {
        /*
         * Używamy tego samego formatu miesiąca co przy zapisie,
         * żeby odczytywać dokładnie ten sam Redis key.
         */
        String month = YearMonth.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM"));

        String key = "quota:" + tenantId + ":" + month;

        /*
         * Odczyt aktualnego licznika z Redisa.
         *
         * Jeśli klucz nie istnieje, tenant nie zużył jeszcze nic
         * w bieżącym miesiącu.
         */
        String used = redis.opsForValue().get(key);

        return Map.of(
                "tenantId", tenantId,
                "month", month,
                "limit", String.valueOf(properties.getQuotas().getMonthlyDefaultLimit()),
                "used", used == null ? "0" : used
        );
    }

    /**
     * Snapshot aktualnego stanu quota po operacji zapisu.
     *
     * enabled:
     * - czy mechanizm quota jest aktywny.
     *
     * limit:
     * - miesięczny limit.
     *
     * used:
     * - aktualne zużycie.
     *
     * remaining:
     * - ile zostało do limitu.
     */
    public record QuotaSnapshot(
            boolean enabled,
            long limit,
            long used,
            long remaining
    ) {}
}