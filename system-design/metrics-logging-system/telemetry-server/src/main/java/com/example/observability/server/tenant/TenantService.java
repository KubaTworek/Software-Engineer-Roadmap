package com.example.observability.server.tenant;

import com.example.observability.server.repository.TelemetryRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Serwis domenowy do zarządzania tenantami.
 *
 * Tenant w tej aplikacji jest podstawową jednostką izolacji danych.
 * Wszystkie logi, metryki, trace'y, alerty i API keys są przypisane do tenantId.
 *
 * Ta klasa obsługuje:
 * - tworzenie tenantów,
 * - aktualizację metadanych tenanta,
 * - listowanie i pobieranie tenantów,
 * - generowanie API keys dla tenantów,
 * - listowanie API keys bez ujawniania tokenów.
 *
 * Nie obsługuje RBAC bezpośrednio.
 * Zakłada, że kontroler wcześniej sprawdził, czy caller ma prawo
 * tworzyć/edytować tenanta albo generować klucze.
 */
@Service
public class TenantService {

    /**
     * Repozytorium zapisujące tenantów i API keys.
     *
     * TenantService nie zna szczegółów SQL-a ani tabel.
     * Zleca zapis/odczyt do TelemetryRepository.
     */
    private final TelemetryRepository repository;

    /**
     * Generator losowości do tworzenia API key.
     *
     * SecureRandom jest właściwym wyborem dla sekretów,
     * w przeciwieństwie do zwykłego Random.
     */
    private final SecureRandom random = new SecureRandom();

    public TenantService(TelemetryRepository repository) {
        this.repository = repository;
    }

    /**
     * Tworzy albo nadpisuje tenanta.
     *
     * Przepływ:
     * 1. Wymaga tenantId.
     * 2. Normalizuje tenantId do bezpiecznego formatu.
     * 3. Ustawia wartości domyślne dla pustych pól.
     * 4. Wymusza retentionDays >= 1.
     * 5. Zapisuje tenanta przez repository.upsertTenant().
     *
     * Domyślne wartości:
     * - displayName = tenantId,
     * - plan = dev,
     * - primaryRegion = local,
     * - retentionDays = 30,
     * - status = active.
     */
    public TenantModels.Tenant create(TenantModels.CreateTenantRequest request) {
        String tenantId = normalize(
                required(request.tenantId(), "tenantId")
        );

        TenantModels.Tenant tenant = new TenantModels.Tenant(
                tenantId,
                blankDefault(request.displayName(), tenantId),
                "active",
                blankDefault(request.plan(), "dev"),
                blankDefault(request.primaryRegion(), "local"),
                request.retentionDays() == null
                        ? 30
                        : Math.max(1, request.retentionDays()),
                Instant.now(),
                Instant.now()
        );

        /*
         * Nazwa metody sugeruje upsert.
         *
         * Jeśli tenantId już istnieje, zachowanie zależy od implementacji repository:
         * może zaktualizować rekord albo zrobić insert, który się wywali.
         */
        repository.upsertTenant(tenant);

        return tenant;
    }

    /**
     * Aktualizuje istniejącego tenanta.
     *
     * Najpierw pobiera aktualny rekord z repository,
     * a potem podmienia tylko pola przekazane w request.
     *
     * Dzięki temu update jest częściowy:
     * null w request oznacza "zostaw obecną wartość".
     */
    public TenantModels.Tenant update(
            String tenantId,
            TenantModels.UpdateTenantRequest request
    ) {
        TenantModels.Tenant existing = repository.getTenant(tenantId);

        TenantModels.Tenant updated = new TenantModels.Tenant(
                tenantId,
                request.displayName() == null
                        ? existing.displayName()
                        : request.displayName(),
                request.status() == null
                        ? existing.status()
                        : request.status(),
                request.plan() == null
                        ? existing.plan()
                        : request.plan(),
                request.primaryRegion() == null
                        ? existing.primaryRegion()
                        : request.primaryRegion(),
                request.retentionDays() == null
                        ? existing.retentionDays()
                        : Math.max(1, request.retentionDays()),
                existing.createdAt(),
                Instant.now()
        );

        repository.upsertTenant(updated);

        return updated;
    }

    /**
     * Zwraca listę wszystkich tenantów.
     *
     * To powinno być dostępne tylko dla platform admina,
     * bo pokazuje globalną listę tenantów systemu.
     */
    public List<TenantModels.Tenant> list() {
        return repository.listTenants();
    }

    /**
     * Pobiera pojedynczego tenanta po tenantId.
     *
     * Kontroler powinien pilnować, żeby tenant admin widział tylko własnego tenanta,
     * a platform admin mógł pobierać dowolny tenant.
     */
    public TenantModels.Tenant get(String tenantId) {
        return repository.getTenant(tenantId);
    }

    /**
     * Tworzy nowy API key dla tenanta.
     *
     * Przepływ:
     * 1. Generuje keyId.
     * 2. Generuje losowy sekret tokena.
     * 3. Buduje token z prefiksem tms_.
     * 4. Ustawia role albo fallback viewer.
     * 5. Zapisuje klucz przez repository.
     * 6. Zwraca jawny token tylko raz.
     *
     * To jest jedyne miejsce, gdzie jawny token powinien być widoczny.
     * Później w bazie powinien istnieć tylko hash tokena.
     */
    public TenantModels.CreatedApiKey createApiKey(
            String tenantId,
            TenantModels.CreateApiKeyRequest request
    ) {
        /*
         * keyId jest identyfikatorem rekordu API key,
         * nie sekretem używanym do autoryzacji.
         */
        String keyId = "key_" + Long.toUnsignedString(
                random.nextLong(),
                36
        );

        /*
         * 24 bajty losowości zakodowane Base64URL bez paddingu.
         *
         * Token dostaje prefiks tms_, żeby łatwo rozpoznać typ sekretu
         * w logach, UI albo przy debugowaniu.
         */
        byte[] raw = new byte[24];
        random.nextBytes(raw);

        String token = "tms_" + Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw);

        /*
         * Jeśli request nie podaje ról, klucz dostaje minimalną rolę viewer.
         *
         * To bezpieczniejsze niż domyślne writer/admin.
         */
        Set<String> roles = request.roles() == null || request.roles().isEmpty()
                ? Set.of("viewer")
                : request.roles();

        /*
         * Repository powinno zapisać hash tokena, a nie token jawny.
         *
         * Jeśli insertTenantApiKey zapisuje token bez hashowania,
         * to jest krytyczny problem bezpieczeństwa.
         */
        repository.insertTenantApiKey(
                tenantId,
                keyId,
                token,
                blankDefault(request.name(), keyId),
                roles,
                request.expiresAt()
        );

        /*
         * Zwracamy jawny token tylko w odpowiedzi create.
         *
         * listApiKeys() powinno później zwracać tylko metadane,
         * bez sekretu.
         */
        return new TenantModels.CreatedApiKey(
                tenantId,
                keyId,
                token,
                blankDefault(request.name(), keyId),
                roles
        );
    }

    /**
     * Listuje API keys dla tenanta.
     *
     * Wynik powinien zawierać metadane:
     * - keyId,
     * - name,
     * - roles,
     * - status,
     * - expiresAt,
     * - createdAt.
     *
     * Nie powinien zawierać jawnego tokena ani token_hash.
     */
    public List<TenantModels.ApiKeyView> listApiKeys(String tenantId) {
        return repository.listTenantApiKeys(tenantId);
    }

    /**
     * Wymusza obecność pola tekstowego.
     *
     * Używane dla tenantId przy tworzeniu tenanta.
     */
    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }

        return value;
    }

    /**
     * Zwraca fallback, jeśli wartość jest null albo blank.
     *
     * Używane do domyślnych wartości takich jak:
     * - displayName,
     * - plan,
     * - primaryRegion,
     * - API key name.
     */
    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value;
    }

    /**
     * Normalizuje tenantId do prostego formatu technicznego.
     *
     * Reguły:
     * - trim,
     * - lowercase,
     * - wszystko poza a-z, 0-9, _, - zamienia na "-".
     *
     * Dzięki temu tenantId nadaje się do:
     * - kluczy w quota,
     * - prefixów object storage,
     * - filtrów i tagów,
     * - prostszego debugowania.
     */
    private String normalize(String value) {
        return value
                .trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_-]", "-");
    }
}