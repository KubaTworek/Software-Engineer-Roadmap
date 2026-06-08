package com.example.ratelimiter.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * RateLimiterProperties mapuje konfigurację z application.yml / application.properties
 * na obiekt używany w aplikacji.
 *
 * Prefix:
 *
 * rate-limiter:
 *   region: eu-central-1
 *   default-failure-strategy: LOCAL_FALLBACK
 *   rules:
 *     - id: global-limit
 *       type: GLOBAL
 *       capacity: 100000
 *       refill-tokens-per-second: 1666
 *
 * Ta klasa jest centralnym modelem konfiguracji Rate Limitera.
 * Korzystają z niej m.in.:
 * - RateLimiterEngine,
 * - RuleMatcher,
 * - RedisTokenBucketLimiter,
 * - LocalFallbackLimiter,
 * - ClientIpResolver,
 * - UsageEventPublisher,
 * - QuotaService.
 *
 * Dzięki temu limity i zachowanie systemu można zmieniać konfiguracyjnie,
 * bez zaszywania wartości w kodzie.
 */
@Validated
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /**
     * Nazwa regionu, w którym działa instancja aplikacji.
     *
     * Przydaje się w scenariuszu multi-region:
     * - osobne limity regionalne,
     * - metryki per region,
     * - usage events z informacją o regionie,
     * - debugowanie rozproszonego ruchu.
     */
    private String region = "local";

    /**
     * Domyślna strategia awarii, używana gdy konkretna reguła
     * nie ma własnego failureStrategy.
     *
     * To określa, co robimy, gdy Redis jest niedostępny:
     * - FAIL_OPEN: przepuszczamy request,
     * - FAIL_CLOSED: blokujemy request,
     * - LOCAL_FALLBACK: używamy lokalnego limitera w pamięci.
     */
    private FailureStrategy defaultFailureStrategy = FailureStrategy.LOCAL_FALLBACK;

    /**
     * Konfiguracja lokalnego limitera awaryjnego.
     *
     * Używany, gdy Redis nie działa i wybrana strategia to LOCAL_FALLBACK.
     */
    private LocalFallback localFallback = new LocalFallback();

    /**
     * Konfiguracja związana z bezpieczeństwem i identyfikacją klienta.
     *
     * Obejmuje:
     * - trusted proxies,
     * - nazwę headera z API key,
     * - nazwę headera z userId,
     * - nazwę headera z tenantId,
     * - nazwę headera z planem.
     */
    private Security security = new Security();

    /**
     * Konfiguracja publikowania usage events.
     *
     * Usage events mogą trafiać np. do Kafki
     * i służyć do analityki, dashboardów oraz billingu.
     */
    private UsageEvents usageEvents = new UsageEvents();

    /**
     * Konfiguracja długoterminowych quota.
     *
     * Quota to np. miesięczny limit requestów dla tenanta,
     * niezależny od krótkich limitów typu requests per minute.
     */
    private Quotas quotas = new Quotas();

    /**
     * Konfiguracja lokalnego cache'a konfiguracji.
     *
     * W docelowej platformie dynamiczny config może pochodzić z bazy danych
     * albo Config Service. Cache ogranicza częstotliwość kosztownych odczytów.
     */
    private ConfigCache configCache = new ConfigCache();

    /**
     * Lista reguł rate limitingu.
     *
     * To najważniejsza część konfiguracji.
     * Każda reguła opisuje:
     * - kogo dotyczy,
     * - jaki ma limit,
     * - ile tokenów odnawia się na sekundę,
     * - ile kosztuje request,
     * - dla jakiego endpointu/metody działa,
     * - jak zachować się przy awarii Redisa.
     */
    private List<Rule> rules = new ArrayList<>();

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public FailureStrategy getDefaultFailureStrategy() { return defaultFailureStrategy; }
    public void setDefaultFailureStrategy(FailureStrategy defaultFailureStrategy) { this.defaultFailureStrategy = defaultFailureStrategy; }

    public LocalFallback getLocalFallback() { return localFallback; }
    public void setLocalFallback(LocalFallback localFallback) { this.localFallback = localFallback; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public UsageEvents getUsageEvents() { return usageEvents; }
    public void setUsageEvents(UsageEvents usageEvents) { this.usageEvents = usageEvents; }

    public Quotas getQuotas() { return quotas; }
    public void setQuotas(Quotas quotas) { this.quotas = quotas; }

    public ConfigCache getConfigCache() { return configCache; }
    public void setConfigCache(ConfigCache configCache) { this.configCache = configCache; }

    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }

    /**
     * Pojedyncza reguła rate limitingu.
     *
     * Reguła mówi:
     * - do jakiego typu limitu należy,
     * - dla kogo obowiązuje,
     * - jaki ma Token Bucket,
     * - jaki jest koszt requestu,
     * - jaki ma priorytet,
     * - co zrobić, gdy Redis nie działa.
     *
     * Przykłady:
     *
     * GLOBAL:
     * - limit dla całej aplikacji.
     *
     * TENANT:
     * - limit dla konkretnego tenanta.
     *
     * USER:
     * - limit dla konkretnego użytkownika.
     *
     * ENDPOINT:
     * - limit dla konkretnej ścieżki, np. POST /api/payments.
     *
     * PLAN:
     * - limit zależny od planu, np. FREE / PRO / ENTERPRISE.
     */
    public static class Rule {

        /**
         * Unikalny identyfikator reguły.
         *
         * Jest używany m.in.:
         * - w Redis key,
         * - w logach,
         * - w metrykach,
         * - w debug endpointach,
         * - w Admin API.
         */
        @NotBlank
        private String id;

        /**
         * Pozwala włączyć lub wyłączyć regułę bez usuwania jej z konfiguracji.
         *
         * To jest bezpieczne dla operacji administracyjnych:
         * łatwiej tymczasowo wyłączyć limit niż usuwać całą definicję.
         */
        private boolean enabled = true;

        /**
         * Typ reguły określa, do czego jest dopasowywana.
         *
         * RuleMatcher używa tego pola, aby zdecydować,
         * czy reguła pasuje do danego RequestContext.
         */
        private RuleType type = RuleType.GLOBAL;

        /**
         * Priorytet reguły.
         *
         * Niższa wartość zwykle oznacza wyższy priorytet.
         *
         * Priorytety pomagają przy:
         * - sortowaniu reguł,
         * - override'ach,
         * - debugowaniu kolejności dopasowania.
         *
         * Przykład:
         * tenant override może mieć priority=10,
         * a plan default priority=1000.
         */
        private int priority = 1000;

        /**
         * Algorytm używany przez regułę.
         *
         * Na tym etapie wspieramy TOKEN_BUCKET.
         * Enum zostawia miejsce na przyszłe algorytmy,
         * np. FIXED_WINDOW albo SLIDING_WINDOW.
         */
        private Algorithm algorithm = Algorithm.TOKEN_BUCKET;

        /**
         * Pojemność bucketu, czyli maksymalna liczba tokenów.
         *
         * To kontroluje burst.
         *
         * Przykład:
         * capacity=100 oznacza, że klient może chwilowo wykonać
         * do 100 requestów/kosztów naraz, jeśli bucket jest pełny.
         */
        @Positive
        private long capacity = 60;

        /**
         * Tempo odnawiania tokenów.
         *
         * Przykład:
         * refillTokensPerSecond=1.0 oznacza, że bucket odzyskuje
         * 1 token na sekundę.
         *
         * Długoterminowy throughput wynika właśnie z tej wartości.
         */
        @Positive
        private double refillTokensPerSecond = 1.0;

        /**
         * Koszt requestu dla tej reguły.
         *
         * Lekkie endpointy mogą mieć cost=1,
         * cięższe operacje, np. eksport danych, mogą mieć cost=50.
         *
         * Token Bucket zużywa z bucketu tyle tokenów, ile wynosi cost.
         */
        @Positive
        private long cost = 1;

        /**
         * Strategia awarii specyficzna dla tej reguły.
         *
         * Jeśli null, RateLimiterEngine użyje defaultFailureStrategy.
         *
         * To pozwala mieć różne zachowanie dla różnych limitów:
         * - login może być FAIL_CLOSED,
         * - zwykłe endpointy mogą być LOCAL_FALLBACK,
         * - mniej krytyczne limity mogą być FAIL_OPEN.
         */
        private FailureStrategy failureStrategy;

        /**
         * Tryb regionalności reguły.
         *
         * REGIONAL:
         * - limit liczony osobno w każdym regionie.
         *
         * GLOBAL_APPROXIMATE:
         * - limit ma charakter globalny/przybliżony.
         *
         * W tym projekcie to pole głównie przygotowuje model pod multi-region.
         */
        private RegionMode regionMode = RegionMode.REGIONAL;

        /**
         * Tenant, dla którego obowiązuje reguła.
         *
         * Używane dla reguł typu TENANT albo override'ów tenantowych.
         */
        private String tenantId;

        /**
         * User, dla którego obowiązuje reguła.
         *
         * Używane dla reguł typu USER.
         */
        private String userId;

        /**
         * Hash API key, dla którego obowiązuje reguła.
         *
         * Trzymamy hash, nie surowy API key.
         *
         * To jest ważne, bo konfiguracja może być logowana,
         * zwracana przez admin/debug API albo przechowywana w bazie.
         */
        private String apiKeyHash;

        /**
         * Plan klienta, np. FREE, PRO, ENTERPRISE.
         *
         * Używane przez reguły typu PLAN.
         */
        private String plan;

        /**
         * Metoda HTTP, np. GET albo POST.
         *
         * Najczęściej używane razem z pathPattern
         * dla reguł endpointowych.
         */
        private String method;

        /**
         * Wzorzec ścieżki endpointu.
         *
         * Przykłady:
         * - /api/users
         * - /api/payments
         * - /api/exports
         * - /api/*
         *
         * RuleMatcher używa tego pola do dopasowania reguł endpointowych.
         */
        private String pathPattern;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public RuleType getType() { return type; }
        public void setType(RuleType type) { this.type = type; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public Algorithm getAlgorithm() { return algorithm; }
        public void setAlgorithm(Algorithm algorithm) { this.algorithm = algorithm; }

        public long getCapacity() { return capacity; }
        public void setCapacity(long capacity) { this.capacity = capacity; }

        public double getRefillTokensPerSecond() { return refillTokensPerSecond; }
        public void setRefillTokensPerSecond(double refillTokensPerSecond) { this.refillTokensPerSecond = refillTokensPerSecond; }

        public long getCost() { return cost; }
        public void setCost(long cost) { this.cost = cost; }

        public FailureStrategy getFailureStrategy() { return failureStrategy; }
        public void setFailureStrategy(FailureStrategy failureStrategy) { this.failureStrategy = failureStrategy; }

        public RegionMode getRegionMode() { return regionMode; }
        public void setRegionMode(RegionMode regionMode) { this.regionMode = regionMode; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getApiKeyHash() { return apiKeyHash; }
        public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }

        public String getPlan() { return plan; }
        public void setPlan(String plan) { this.plan = plan; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public String getPathPattern() { return pathPattern; }
        public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
    }

    /**
     * Konfiguracja lokalnego fallback limitera.
     *
     * Używana wtedy, gdy Redis nie działa i strategia awarii to LOCAL_FALLBACK.
     *
     * Ten limiter jest lokalny dla jednej instancji aplikacji,
     * więc nie zapewnia pełnej globalnej spójności.
     * Jego rola to utrzymać podstawową ochronę systemu podczas awarii Redisa.
     */
    public static class LocalFallback {

        /**
         * Włącza lub wyłącza lokalny fallback.
         */
        private boolean enabled = true;

        /**
         * Domyślny limit używany przez fallback limiter.
         *
         * To uproszczony limit awaryjny, niezależny od Redisa.
         */
        private long defaultLimit = 50;

        /**
         * Okno czasowe fallback limitera w sekundach.
         *
         * Przykład:
         * defaultLimit=50, defaultWindowSeconds=60
         * oznacza 50 requestów na minutę lokalnie na instancję.
         */
        private long defaultWindowSeconds = 60;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getDefaultLimit() { return defaultLimit; }
        public void setDefaultLimit(long defaultLimit) { this.defaultLimit = defaultLimit; }

        public long getDefaultWindowSeconds() { return defaultWindowSeconds; }
        public void setDefaultWindowSeconds(long defaultWindowSeconds) { this.defaultWindowSeconds = defaultWindowSeconds; }
    }

    /**
     * Konfiguracja bezpieczeństwa i identyfikacji klienta.
     *
     * Te wartości decydują, z których nagłówków aplikacja pobiera:
     * - API key,
     * - userId,
     * - tenantId,
     * - plan klienta.
     *
     * Oprócz tego zawiera listę trusted proxies,
     * która jest używana przy obsłudze X-Forwarded-For.
     */
    public static class Security {

        /**
         * Lista zaufanych proxy/load balancerów.
         *
         * Tylko jeśli request pochodzi z jednego z tych adresów,
         * aplikacja ufa nagłówkowi X-Forwarded-For.
         *
         * To chroni przed sytuacją, w której klient sam fałszuje XFF,
         * żeby ominąć limity per IP.
         */
        private List<String> trustedProxies = new ArrayList<>();

        /**
         * Nazwa headera zawierającego API key.
         */
        private String apiKeyHeader = "X-Api-Key";

        /**
         * Nazwa headera zawierającego userId.
         */
        private String userHeader = "X-User-Id";

        /**
         * Nazwa headera zawierającego tenantId.
         */
        private String tenantHeader = "X-Tenant-Id";

        /**
         * Nazwa headera zawierającego plan klienta.
         *
         * Jeśli request nie zawiera tego nagłówka,
         * RateLimitFilter domyślnie ustawia plan FREE.
         */
        private String planHeader = "X-Plan";

        public List<String> getTrustedProxies() { return trustedProxies; }
        public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }

        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }

        public String getUserHeader() { return userHeader; }
        public void setUserHeader(String userHeader) { this.userHeader = userHeader; }

        public String getTenantHeader() { return tenantHeader; }
        public void setTenantHeader(String tenantHeader) { this.tenantHeader = tenantHeader; }

        public String getPlanHeader() { return planHeader; }
        public void setPlanHeader(String planHeader) { this.planHeader = planHeader; }
    }

    /**
     * Konfiguracja usage events.
     *
     * Usage event to informacja o tym, że Rate Limiter podjął decyzję
     * dla konkretnego requestu.
     *
     * Może zawierać m.in.:
     * - tenant,
     * - user,
     * - endpoint,
     * - decyzję allowed/denied,
     * - koszty,
     * - timestamp.
     */
    public static class UsageEvents {

        /**
         * Włącza lub wyłącza publikowanie usage events.
         */
        private boolean enabled = true;

        /**
         * Nazwa topiku Kafka, do którego publikujemy usage events.
         */
        private String kafkaTopic = "rate-limit-usage-events";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getKafkaTopic() { return kafkaTopic; }
        public void setKafkaTopic(String kafkaTopic) { this.kafkaTopic = kafkaTopic; }
    }

    /**
     * Konfiguracja billing quotas.
     *
     * Quota to długoterminowy limit zużycia,
     * np. miesięczny limit requestów dla tenanta.
     *
     * To jest inne niż krótki rate limit.
     */
    public static class Quotas {

        /**
         * Włącza lub wyłącza obsługę quotas.
         */
        private boolean enabled = true;

        /**
         * Domyślny miesięczny limit dla tenanta.
         */
        private long monthlyDefaultLimit = 1000000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getMonthlyDefaultLimit() { return monthlyDefaultLimit; }
        public void setMonthlyDefaultLimit(long monthlyDefaultLimit) { this.monthlyDefaultLimit = monthlyDefaultLimit; }
    }

    /**
     * Konfiguracja lokalnego cache'a dynamicznej konfiguracji.
     *
     * Przydaje się, gdy reguły pochodzą z zewnętrznego Config Service
     * albo bazy danych.
     */
    public static class ConfigCache {

        /**
         * Maksymalna liczba wpisów w cache'u.
         */
        private long maximumSize = 10000;

        /**
         * Czas życia wpisu po zapisie.
         *
         * Po tym czasie wpis powinien zostać odświeżony.
         */
        private long expireAfterWriteSeconds = 30;

        public long getMaximumSize() { return maximumSize; }
        public void setMaximumSize(long maximumSize) { this.maximumSize = maximumSize; }

        public long getExpireAfterWriteSeconds() { return expireAfterWriteSeconds; }
        public void setExpireAfterWriteSeconds(long expireAfterWriteSeconds) { this.expireAfterWriteSeconds = expireAfterWriteSeconds; }
    }

    /**
     * Typ reguły rate limitingu.
     *
     * Decyduje o tym, z jakim fragmentem RequestContext reguła jest porównywana.
     */
    public enum RuleType {
        GLOBAL,
        TENANT,
        USER,
        API_KEY,
        PLAN,
        ENDPOINT
    }

    /**
     * Obsługiwane algorytmy rate limitingu.
     *
     * Obecnie wspierany jest tylko TOKEN_BUCKET,
     * ale enum pozwala łatwo dodać kolejne strategie.
     */
    public enum Algorithm {
        TOKEN_BUCKET
    }

    /**
     * Strategia zachowania przy awarii Redisa.
     */
    public enum FailureStrategy {
        /**
         * Przepuszczamy request, gdy Redis nie działa.
         * Chroni dostępność, ale osłabia ochronę przed nadużyciami.
         */
        FAIL_OPEN,

        /**
         * Blokujemy request, gdy Redis nie działa.
         * Chroni backend, ale może spowodować niedostępność API.
         */
        FAIL_CLOSED,

        /**
         * Przechodzimy na lokalny limiter w pamięci aplikacji.
         * Daje częściową ochronę bez globalnej spójności.
         */
        LOCAL_FALLBACK
    }

    /**
     * Tryb działania w środowisku multi-region.
     */
    public enum RegionMode {
        /**
         * Limit liczony lokalnie w danym regionie.
         */
        REGIONAL,

        /**
         * Limit traktowany jako globalny, ale egzekwowany przybliżenie.
         *
         * To kompromis: mniejsza latencja i większa odporność
         * kosztem idealnej globalnej dokładności.
         */
        GLOBAL_APPROXIMATE
    }
}