package com.example.observability.server.controller;

import com.example.observability.server.auth.Rbac;
import com.example.observability.server.tenant.TenantModels;
import com.example.observability.server.tenant.TenantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API do zarządzania tenantami i ich API keys.
 *
 * Ten controller realizuje część "self-service tenant management" z Fazy 3.
 *
 * Odpowiada za:
 * - listowanie tenantów,
 * - tworzenie nowych tenantów,
 * - pobieranie konfiguracji konkretnego tenanta,
 * - aktualizację ustawień tenanta,
 * - tworzenie API keys,
 * - listowanie API keys.
 *
 * W systemie observability tenant jest podstawową jednostką izolacji:
 * - dane logów,
 * - dane metryk,
 * - trace'y,
 * - limity/quota,
 * - uprawnienia,
 * - retencja,
 * - konfiguracja regionu.
 */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    /**
     * Warstwa domenowa odpowiedzialna za operacje na tenantach.
     *
     * Controller nie powinien sam wykonywać logiki biznesowej ani SQL.
     * Jego zadaniem jest:
     * - sprawdzić uprawnienia,
     * - przyjąć request HTTP,
     * - przekazać go do TenantService,
     * - zwrócić odpowiedź.
     */
    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    /**
     * Zwraca listę wszystkich tenantów w systemie.
     *
     * Endpoint:
     * GET /api/v1/tenants
     *
     * Wymaga platform admina, a nie zwykłego admina tenanta.
     *
     * Powód:
     * - lista tenantów pokazuje globalny stan platformy,
     * - zwykły admin jednego tenanta nie powinien widzieć innych tenantów,
     * - to endpoint operatorski / platformowy.
     */
    @GetMapping
    public List<TenantModels.Tenant> list() {
        Rbac.requirePlatformAdmin();
        return tenantService.list();
    }

    /**
     * Tworzy nowego tenanta.
     *
     * Endpoint:
     * POST /api/v1/tenants
     *
     * Wymaga platform admina, bo utworzenie tenanta wpływa na globalne zasoby:
     * - storage,
     * - limity,
     * - routing,
     * - potencjalnie region,
     * - przyszłe koszty ingestu/query.
     *
     * request zawiera parametry początkowe tenanta,
     * np. tenantId, nazwa, status, quota, retention albo region.
     */
    @PostMapping
    public TenantModels.Tenant create(
            @RequestBody TenantModels.CreateTenantRequest request
    ) {
        Rbac.requirePlatformAdmin();
        return tenantService.create(request);
    }

    /**
     * Pobiera szczegóły konkretnego tenanta.
     *
     * Endpoint:
     * GET /api/v1/tenants/{tenantId}
     *
     * Wymaga uprawnienia read dla danego tenanta.
     *
     * Dzięki temu:
     * - platform admin może czytać każdego tenanta,
     * - admin/viewer konkretnego tenanta może zobaczyć tylko swój tenant,
     * - dane konfiguracyjne nie wyciekają między tenantami.
     */
    @GetMapping("/{tenantId}")
    public TenantModels.Tenant get(@PathVariable String tenantId) {
        Rbac.requireRead(tenantId);
        return tenantService.get(tenantId);
    }

    /**
     * Aktualizuje konfigurację konkretnego tenanta.
     *
     * Endpoint:
     * PATCH /api/v1/tenants/{tenantId}
     *
     * Wymaga roli admin w ramach tego tenanta.
     *
     * Typowe zmiany:
     * - nazwa tenanta,
     * - status,
     * - retencja,
     * - quota,
     * - konfiguracja regionu,
     * - ustawienia feature flags.
     *
     * To jest operacja administracyjna, bo może wpływać na:
     * - koszty,
     * - dostępność danych,
     * - zachowanie ingestu,
     * - zachowanie query.
     */
    @PatchMapping("/{tenantId}")
    public TenantModels.Tenant update(
            @PathVariable String tenantId,
            @RequestBody TenantModels.UpdateTenantRequest request
    ) {
        Rbac.requireAdmin(tenantId);
        return tenantService.update(tenantId, request);
    }

    /**
     * Tworzy nowy API key dla tenanta.
     *
     * Endpoint:
     * POST /api/v1/tenants/{tenantId}/api-keys
     *
     * Wymaga roli admin dla danego tenanta.
     *
     * API key jest używany przez:
     * - agentów,
     * - integracje,
     * - aplikacje wysyłające logi,
     * - aplikacje wysyłające metryki,
     * - zewnętrzne narzędzia query/admin.
     *
     * request powinien określać zakres uprawnień klucza,
     * np. writer, viewer, admin albo ograniczone scopes.
     *
     * Zwracany typ CreatedApiKey prawdopodobnie zawiera sekret klucza.
     * Taki sekret powinien być pokazany tylko raz przy tworzeniu,
     * a w bazie powinien być zapisany wyłącznie hash.
     */
    @PostMapping("/{tenantId}/api-keys")
    public TenantModels.CreatedApiKey createKey(
            @PathVariable String tenantId,
            @RequestBody TenantModels.CreateApiKeyRequest request
    ) {
        Rbac.requireAdmin(tenantId);
        return tenantService.createApiKey(tenantId, request);
    }

    /**
     * Listuje API keys utworzone dla danego tenanta.
     *
     * Endpoint:
     * GET /api/v1/tenants/{tenantId}/api-keys
     *
     * Wymaga roli admin dla danego tenanta.
     *
     * Ten endpoint powinien zwracać tylko bezpieczny widok kluczy:
     * - id,
     * - nazwa,
     * - role/scopes,
     * - data utworzenia,
     * - data wygaśnięcia,
     * - status.
     *
     * Nie powinien zwracać pełnego sekretu API key.
     */
    @GetMapping("/{tenantId}/api-keys")
    public List<TenantModels.ApiKeyView> keys(@PathVariable String tenantId) {
        Rbac.requireAdmin(tenantId);
        return tenantService.listApiKeys(tenantId);
    }
}