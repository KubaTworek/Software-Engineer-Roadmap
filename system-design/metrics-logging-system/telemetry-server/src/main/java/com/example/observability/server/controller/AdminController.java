package com.example.observability.server.controller;

import com.example.observability.server.auth.Rbac;
import com.example.observability.server.cold.ColdExportJob;
import com.example.observability.server.cold.ObjectStorageService;
import com.example.observability.server.cold.ColdRestoreService;
import com.example.observability.server.quota.QuotaProperties;
import com.example.observability.server.quota.QuotaService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Kontroler administracyjny dla operacji operatorskich systemu observability.
 *
 * Ten controller nie obsługuje standardowego ingestu ani query użytkownika.
 * Służy do zarządzania elementami infrastrukturalnymi:
 *
 * - sprawdzania limitów tenantów,
 * - ręcznego uruchamiania eksportu danych do cold storage,
 * - podglądu obiektów zapisanych w object storage.
 *
 * Każdy endpoint wymaga uprawnień admina dla danego tenanta.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    /**
     * Serwis odpowiedzialny za odczyt limitów/quota dla tenantów.
     *
     * Quoty są używane m.in. przez ingest i query layer,
     * żeby ograniczać koszt i chronić system przed noisy neighborami.
     */
    private final QuotaService quotaService;

    /**
     * Job eksportujący dane z warstwy hot storage do cold storage.
     *
     * W praktyce odpowiada za przenoszenie starszych logów i metryk
     * z ClickHouse do tańszego storage'u obiektowego.
     */
    private final ColdExportJob coldExportJob;

    /**
     * Abstrakcja nad object storage.
     *
     * W tej implementacji może to być lokalny filesystem,
     * ale kontrakt jest przygotowany tak, żeby później podmienić backend
     * na S3, GCS, Azure Blob albo MinIO.
     */
    private final ObjectStorageService objectStorageService;
    private final ColdRestoreService coldRestoreService;

    public AdminController(
            QuotaService quotaService,
            ColdExportJob coldExportJob,
            ObjectStorageService objectStorageService,
            ColdRestoreService coldRestoreService
    ) {
        this.quotaService = quotaService;
        this.coldExportJob = coldExportJob;
        this.objectStorageService = objectStorageService;
        this.coldRestoreService = coldRestoreService;
    }

    /**
     * Zwraca konfigurację quota dla konkretnego tenanta.
     *
     * Endpoint:
     * GET /api/v1/admin/quotas/{tenantId}
     *
     * Przykładowe użycie:
     * - panel admina pokazuje aktualne limity,
     * - operator sprawdza, dlaczego tenant dostaje HTTP 429,
     * - diagnostyka problemów z ingestem albo query.
     *
     * Rbac.requireAdmin(tenantId) pilnuje, żeby tylko administrator
     * danego tenanta mógł zobaczyć jego limity.
     */
    @GetMapping("/quotas/{tenantId}")
    public QuotaProperties.TenantQuota quota(@PathVariable String tenantId) {
        Rbac.requireAdmin(tenantId);
        return quotaService.tenantQuota(tenantId);
    }

    /**
     * Ręcznie uruchamia eksport danych z hot storage do cold storage.
     *
     * Endpoint:
     * POST /api/v1/admin/cold-export?tenantId=demo&start=...&end=...
     *
     * Eksportuje dane z podanego zakresu czasu:
     * - logi,
     * - metryki.
     *
     * Zwracane URI wskazują miejsce zapisu plików w object storage.
     *
     * To jest endpoint administracyjny, bo eksport cold data może być kosztowny:
     * - czyta większe zakresy danych,
     * - generuje pliki,
     * - zapisuje je do storage,
     * - może obciążać ClickHouse.
     *
     * Uwaga:
     * tenantId jest używany tylko do autoryzacji.
     * Sama metoda coldExportJob.exportLogs/exportMetrics w tej wersji
     * przyjmuje tylko zakres czasu, więc jeśli job nie filtruje po tenancie
     * wewnętrznie, to jest to miejsce wymagające ostrożności.
     */
    @PostMapping("/cold-export")
    public Map<String, String> export(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam Instant start,
            @RequestParam Instant end
    ) {
        Rbac.requireAdmin(tenantId);

        return Map.of(
                "logsUri", coldExportJob.exportLogs(tenantId, start, end),
                "metricsUri", coldExportJob.exportMetrics(tenantId, start, end)
        );
    }

    /**
     * Listuje obiekty znajdujące się w object storage.
     *
     * Endpoint:
     * GET /api/v1/admin/objects?tenantId=demo&prefix=...
     *
     * Typowe użycie:
     * - sprawdzenie, czy cold export faktycznie zapisał pliki,
     * - debugowanie retencji/cold storage,
     * - podgląd struktury obiektów po prefixie.
     *
     * prefix ogranicza listowanie do konkretnego katalogu/prefiksu,
     * np. logs/demo/ albo metrics/demo/.
     *
     * Uwaga:
     * tenantId jest tu używany do sprawdzenia uprawnień,
     * ale prefix nie jest automatycznie ograniczany do tenanta.
     * W produkcyjnej wersji należałoby wymusić tenant-scoped prefix,
     * żeby admin jednego tenanta nie mógł listować obiektów innego.
     */
    @GetMapping("/objects")
    public List<ObjectStorageService.ObjectInfo> objects(
            @RequestParam(defaultValue = "demo") String tenantId,
            @RequestParam(defaultValue = "") String prefix
    ) {
        Rbac.requireAdmin(tenantId);
        String safeSuffix = prefix == null ? "" : prefix.replace('\\', '/');
        if (safeSuffix.contains("..")) throw new IllegalArgumentException("invalid object prefix");
        var objects = new java.util.ArrayList<ObjectStorageService.ObjectInfo>();
        objects.addAll(objectStorageService.list("logs/" + tenantId + "/" + safeSuffix));
        objects.addAll(objectStorageService.list("metrics/" + tenantId + "/" + safeSuffix));
        return objects;
    }

    /**
     * Wykonuje kontrolowane odtworzenie obiektu cold storage do izolowanej
     * tabeli drill. Operacja weryfikuje tenant, sumę SHA-256, poprawność JSONL
     * i liczbę rekordów z manifestu. Nie zapisuje danych do tabel produkcyjnych,
     * dzięki czemu test odtwarzania nie może nadpisać bieżącej telemetrii.
     */
    @PostMapping("/cold-restore-drill")
    public ColdRestoreService.RestoreResult restoreDrill(
            @RequestParam String tenantId,
            @RequestParam String objectKey
    ) {
        Rbac.requireAdmin(tenantId);
        return coldRestoreService.drill(tenantId, objectKey);
    }
}
