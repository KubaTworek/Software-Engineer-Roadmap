package com.example.ratelimiter.admin;

import com.example.ratelimiter.config.DynamicConfigService;
import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RequestContext;
import com.example.ratelimiter.core.RuleMatcher;
import com.example.ratelimiter.quota.QuotaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AdminController wystawia administracyjne API Rate Limitera.
 *
 * Ta klasa NIE bierze udziału bezpośrednio w limitowaniu każdego requestu użytkownika.
 * Jej zadaniem jest zarządzanie konfiguracją oraz debugowanie tego,
 * jakie reguły zostałyby zastosowane dla konkretnego requestu.
 *
 * W praktyce jest to warstwa kontrolna platformy:
 * - pozwala podejrzeć aktualne reguły,
 * - dodać lub zaktualizować regułę,
 * - wyłączyć regułę,
 * - sprawdzić effective-rules dla danego kontekstu,
 * - podejrzeć quota danego tenanta.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    /**
     * DynamicConfigService odpowiada za aktualną konfigurację rate limitów.
     *
     * W tym projekcie jest to centralne miejsce, z którego pobieramy reguły.
     * Dzięki temu konfiguracja może być zmieniana w runtime,
     * bez restartowania aplikacji.
     */
    private final DynamicConfigService configService;

    /**
     * RuleMatcher odpowiada za dopasowanie reguł do konkretnego requestu.
     *
     * Na podstawie RequestContext sprawdza, które limity mają zastosowanie,
     * np. globalne, tenantowe, userowe, endpointowe albo planowe.
     *
     * To ważne, bo jeden request może podlegać wielu regułom naraz.
     */
    private final RuleMatcher ruleMatcher;

    /**
     * QuotaService odpowiada za długoterminowe limity użycia,
     * np. miesięczny limit requestów dla tenanta.
     *
     * To jest osobna koncepcja od klasycznego rate limitingu:
     * - rate limit chroni system przed nadmiernym ruchem w krótkim czasie,
     * - quota służy raczej do planów, billingów i limitów miesięcznych.
     */
    private final QuotaService quotaService;

    public AdminController(
            DynamicConfigService configService,
            RuleMatcher ruleMatcher,
            QuotaService quotaService
    ) {
        this.configService = configService;
        this.ruleMatcher = ruleMatcher;
        this.quotaService = quotaService;
    }

    /**
     * Zwraca aktualną konfigurację reguł rate limitera.
     *
     * Endpoint przydatny do:
     * - sprawdzenia, jakie reguły są obecnie aktywne,
     * - debugowania problemów z limitami,
     * - potwierdzenia, że dynamiczna konfiguracja została załadowana.
     *
     * Zwracamy też version, ponieważ wersjonowanie konfiguracji pozwala
     * sprawdzić, czy aplikacja korzysta już z najnowszego zestawu reguł.
     */
    @GetMapping("/rules")
    public Map<String, Object> rules() {
        return Map.of(
                "version", configService.version(),
                "rules", configService.allRules()
        );
    }

    /**
     * Dodaje nową regułę albo aktualizuje istniejącą.
     *
     * To jest podstawowy mechanizm dynamicznego zarządzania limitami.
     * Dzięki temu można np.:
     * - zmienić limit dla planu FREE,
     * - dodać ostrzejszy limit dla konkretnego endpointu,
     * - dodać override dla konkretnego tenanta lub użytkownika.
     *
     * @Valid wymusza walidację obiektu Rule zgodnie z adnotacjami
     * zdefiniowanymi w RateLimiterProperties.Rule.
     *
     * Po zmianie reguły configService powinien zwiększyć wersję konfiguracji,
     * żeby było jasne, że konfiguracja uległa zmianie.
     */
    @PostMapping("/rules")
    public Map<String, Object> upsertRule(@Valid @RequestBody RateLimiterProperties.Rule rule) {
        configService.upsertRule(rule);

        return Map.of(
                "status", "upserted",
                "version", configService.version(),
                "ruleId", rule.getId()
        );
    }

    /**
     * Wyłącza regułę o podanym ID.
     *
     * Warto zwrócić uwagę, że metoda nazywa się disableRule, a nie deleteRule.
     * To sugeruje bezpieczniejsze podejście: reguła może zostać oznaczona
     * jako nieaktywna zamiast fizycznie usunięta.
     *
     * To ma sens w systemie produkcyjnym, bo:
     * - łatwiej debugować historię konfiguracji,
     * - łatwiej przywrócić regułę,
     * - zmniejszamy ryzyko przypadkowej utraty konfiguracji.
     */
    @DeleteMapping("/rules/{id}")
    public Map<String, Object> disableRule(@PathVariable String id) {
        boolean changed = configService.disableRule(id);

        return Map.of(
                "status", changed ? "disabled" : "not_found",
                "version", configService.version()
        );
    }

    /**
     * Endpoint diagnostyczny pokazujący, które reguły zostałyby zastosowane
     * dla przykładowego requestu.
     *
     * To jeden z najważniejszych endpointów administracyjnych w całym systemie.
     * Przy wielu limitach, priorytetach i override'ach bardzo łatwo zgubić się
     * w tym, dlaczego request został zablokowany albo przepuszczony.
     *
     * Ten endpoint pozwala zasymulować kontekst requestu, np.:
     * - metoda HTTP,
     * - ścieżka,
     * - tenant,
     * - user,
     * - API key hash,
     * - plan,
     * - IP klienta.
     *
     * Następnie RuleMatcher zwraca listę reguł, które pasują do tego kontekstu.
     *
     * Przykład:
     * GET /admin/debug/effective-rules?method=POST&path=/api/payments&tenantId=t1&userId=u1&plan=FREE
     *
     * Może zwrócić np.:
     * - global limit,
     * - tenant limit,
     * - user limit,
     * - endpoint limit,
     * - plan limit.
     *
     * Ten endpoint nie konsumuje tokenów i nie wykonuje realnego limitowania.
     * Służy tylko do debugowania konfiguracji.
     */
    @GetMapping("/debug/effective-rules")
    public Map<String, Object> effectiveRules(
            @RequestParam(defaultValue = "GET") String method,
            @RequestParam(defaultValue = "/api/users") String path,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String apiKeyHash,
            @RequestParam(defaultValue = "FREE") String plan,
            @RequestParam(defaultValue = "127.0.0.1") String clientIp
    ) {
        /*
         * RequestContext jest wspólnym modelem opisującym request
         * z punktu widzenia rate limitera.
         *
         * Nie musi to być prawdziwy request HTTP.
         * Tutaj tworzymy go ręcznie na podstawie query parametrów,
         * żeby zasymulować sytuację i sprawdzić dopasowane reguły.
         *
         * System.currentTimeMillis() jest użyte jako aktualny czas kontekstu.
         * W tym endpointcie nie ma to dużego znaczenia, bo nie konsumujemy limitów,
         * ale w realnym rate limitingu czas jest potrzebny np. dla Token Bucket.
         */
        RequestContext ctx = new RequestContext(
                method,
                path,
                clientIp,
                apiKeyHash,
                userId,
                tenantId,
                plan,
                System.currentTimeMillis()
        );

        return Map.of(
                "context", ctx,
                "matchedRules", ruleMatcher.match(ctx),
                "configVersion", configService.version()
        );
    }

    /**
     * Zwraca informacje o quota dla danego tenanta.
     *
     * Ten endpoint dotyczy limitów długoterminowych, np. miesięcznych.
     * To nie jest klasyczny rate limit typu "100 requestów na minutę",
     * tylko raczej informacja o wykorzystaniu dostępnego pakietu.
     *
     * Przykład użycia:
     * GET /admin/tenants/acme/quota
     *
     * Może zwrócić np.:
     * - miesięczny limit,
     * - aktualne zużycie,
     * - pozostały limit.
     */
    @GetMapping("/tenants/{tenantId}/quota")
    public Map<String, String> tenantQuota(@PathVariable String tenantId) {
        return quotaService.getTenantQuota(tenantId);
    }
}