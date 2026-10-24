# Rozszerzenia roadmapy — mapa implementacji i weryfikacji

Stan sprawdzony lokalnie 2026-10-25. [Poprawki po audycie](../remediation-2026-10-25.md). To mapa nowych materiałów, nie deklaracja
gotowości produkcyjnej całego repozytorium. Ścieżki komend odnoszą się do
katalogu wskazanego w kolumnie „Weryfikacja”.

| Temat | Implementacja i opis | Weryfikacja |
| --- | --- | --- |
| Linux i pamięć poza heapem | [1G](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_g/README.md), parser cgroup v2 i probe v1/v2 | backend-engineering: resource-pressure-drill.ps1 dla memory/cpu/fd — potwierdzone OOMKilled, nr_throttled i Too many open files |
| Build i aktualizacje | [1H](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_h/README.md), rzeczywista podmiana biblioteki runtime | BinaryCompatibilityLabTest; [Booking](../../projects/project-1/docs/operations-and-build.md): dwa clean package z identycznym SHA-256 |
| Batch i checkpoint | [Spring Batch](../../backend-engineering/labs/durable-processes/README.md), definicja Job i trwały JobRepository | rollback porcji, milion rekordów, wznowienie; osobny test dwóch JVM z repozytorium na dysku; H2 nie dowodzi locków PostgreSQL |
| Temporal | [2E](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_e/README.md), Signal, timer, retry, version marker, kompensacja | replay, nowy worker bez cache, cancel po charge z refundem, błąd refundu — COMPENSATION_FAILED |
| Modernizacja | [2F](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_f/README.md), model watermark/ownership i [shadow Catalog](../../projects/project-2/docs/modernization.md) | CutoverSimulationTest i ShadowCatalogReaderTest; brak obietnicy globalnego koordynatora rolloutów |
| Operacje SQL | [PostgreSQL](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/operations/README.md) oraz [replication drill](../../backend-engineering/labs/database-operations/README.md) | dwa kontrakty PostgreSQL; realny pause/replay i retencja slotu WAL — sukces |
| Projekt 1 | [budżet, diagnostyka procesu i build](../../projects/project-1/docs/operations-and-build.md) | mvnw.cmd verify — sukces; benchmark JMH wymaga osobnego profilu |
| Projekt 2 | [staging i Temporal worker](../../projects/project-2/docs/durable-processes.md), migracje V6–V7, [ADR 0012](../../projects/project-2/docs/adr/0012-durable-import-and-temporal-reconciliation.md) | gradlew.bat test check assemble oraz pełne integrationTest — sukces |
| Projekt 3 | [polityka świeżości](../../projects/project-3/docs/database-operations.md), [intencja przed płatnością](../../projects/project-3/docs/architecture.md) i restore | pełny build i infrastruktura; odrzucony commit, smoke, restore z manifestem/HTTP i ruch podczas awarii — sukces; repliki to osobny drill |
| Etap 4 | [RFC, review i postmortem](README.md) | konkretny RFC i zadania na trzech projektach; review z człowiekiem pozostaje rzeczywistym zadaniem, nie fikcyjnym wynikiem |
| GraphQL | [istniejące laboratorium](../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/graphql/README.md) i adapter projektu 2 | główne verify oraz testy projektu 2 obejmują kontrakty HTTP, budżet AST, listy ze zmiennych, field authorization i cache per request |

## Komendy przekrojowe

- `backend-engineering`: `mvnw.cmd --batch-mode --no-transfer-progress verify` — 474 testy, bez błędów, jeden celowo pominięty test.
- `backend-engineering`: `mvnw.cmd --batch-mode --no-transfer-progress -f labs/durable-processes/pom.xml verify` — osobny build SDK i Batch.
- `backend-engineering`: `mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests -Dtest=PostgreSqlOperationsTest test` — dwa testy na PostgreSQL.
- `project-1`: `mvnw.cmd --batch-mode --no-transfer-progress verify` oraz `pwsh -File scripts/check-reproducible-build.ps1`.
- `project-2`: `gradlew.bat test check assemble --no-daemon` i `gradlew.bat integrationTest --no-daemon`.
- `project-3`: `mvnw.cmd --batch-mode --no-transfer-progress verify`.
- Katalog główny: `pwsh -File scripts/check-documentation.ps1` oraz `git diff --check`.

Nie uruchamiano HA klastra Temporal, wdrożenia replik ticketingu w chmurze,
produkcyjnego cutoveru zapisów ani awarii pełnego dysku. Te działania wymagają
konkretnego środowiska i mierzalnych warunków przerwania. Testy lokalne nie są
ich zastępstwem. Nowe drille mają osobny job CI, a samodzielny build Batch/Temporal
jest częścią macierzy Maven; wynik zdalnego CI powstanie dopiero po publikacji zmian.

[Domknięcie pięciu obszarów](../completion-2026-10-25.md) zawiera aktualne dowody
Unicode/SQL, OIDC/JWKS, lifecycle/restore, SLO/endurance oraz ekonomii jednostkowej.
