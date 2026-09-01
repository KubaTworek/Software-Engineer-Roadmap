# Stage 2C — delivery i operacje

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** Stage 2C — delivery i operacje.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Stage 2C — delivery i operacje” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CanaryAndRollbackTest,FeatureFlagTest,GameDayAndIncidentTest" test`
> - **Role klas:** `ProgressiveDeliveryController` = `production-boundary`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Stage 2C — od aplikacji do bezpiecznego wdrożenia



Ten blok jest jednym przekrojowym laboratorium, a nie zbiorem czterech podobnych aplikacji. Kod Java ma jedno kanoniczne źródło w `workshop`. Kolejne katalogi dodają do niego osobne warstwy dostarczania:

```text
workshop (aplikacja)
    -> configuration (konfiguracja runtime i dane trwałe)
    -> docker (niezmienny obraz i użytkownik procesu)
    -> kubernetes (probes, resources, securityContext i rollout)
    -> progressive_delivery (canary, rollback, flagi i operacje incydentowe)
    -> workshop (pełny lokalny przepływ oraz testy kontraktów)
```

## Mapa odpowiedzialności

| Warstwa | Jest źródłem prawdy dla | Celowo nie zawiera |
|---|---|---|
| `workshop` | kodu Java, lifecycle, endpointów, testów aplikacji i kontraktów dostarczenia | kopii manifestów i Dockerfile |
| `configuration` | ConfigMap, Secret i PVC | aplikacji Spring, Deployment i Service |
| `docker` | wieloetapowego Dockerfile, użytkownika i procesu PID 1 | kodu Java i manifestów K8s |
| `kubernetes` | Deployment, Service i wariantów HPA | kopii aplikacji i konfiguracji runtime |
| `progressive_delivery` | decyzji canary, rollbacku, shadow traffic, flag i runbooka | kolejnej kopii aplikacji lub manifestów |

Granice są celowe. Dzięki nim zmiana endpointu probe wymaga zmiany aplikacji i manifestu, a test kontraktowy wykrywa ich rozjazd.

## Przejście przez laboratorium

### 1. Aplikacja

Uruchom wrapper z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml verify
```

Aplikacja rozróżnia startup, readiness i liveness, przestaje przyjmować ruch przed zamknięciem oraz propaguje konfigurację typowaną przez `@ConfigurationProperties`.

### 2. Konfiguracja runtime

`configuration/k8s` pokazuje trzy różne kontrakty:

- ConfigMap dla jawnej konfiguracji,
- Secret dla danych wrażliwych — manifest zawiera wyłącznie wartość demonstracyjną,
- PVC dla danych, które mają przeżyć restart Poda.

Konfiguracja środowiska nie jest wbudowana w obraz. Ten sam digest obrazu powinien przechodzić przez środowiska, a różnice powinny trafiać do konfiguracji wdrożenia.

### 3. Obraz

Budowanie odbywa się z katalogu `workshop`, ponieważ jest on kontekstem zawierającym `pom.xml` i `src`:

```powershell
cd src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop
docker build -f ../docker/Dockerfile -t demo-api-workshop:dev .
```

Dockerfile używa multi-stage build, ma mały runtime, uruchamia proces bez roota i stosuje exec form `ENTRYPOINT`, aby JVM otrzymywała `SIGTERM`.

### 4. Kubernetes

```powershell
kubectl apply -f ../configuration/k8s/
kubectl apply -f ../kubernetes/k8s/
kubectl rollout status deployment/demo-api
```

Deployment dodaje named port, trzy różne probe’y, requests/limits, read-only root filesystem, wyłączony token ServiceAccount, wolumeny oraz bezpieczny rolling update.

### 5. Progressive delivery i incydent

[Laboratorium progressive delivery](progressive_delivery/README.md) zaczyna się po
uzyskaniu readiness. Łączy porównanie baseline/canary, automatyczny rollback, kill
switch, read-only shadow traffic, kompatybilność schematu, fault injection oraz
wykonywalny runbook z timeline’em i postmortem.

## Najważniejsze zależności operacyjne

- `startupProbe` chroni wolny start; dopóki nie przejdzie, pozostałe probe’y nie powinny restartować procesu.
- `readinessProbe` steruje ruchem. Może uwzględniać zależność krytyczną, ale nie powinna zabijać procesu.
- `livenessProbe` odpowiada wyłącznie na pytanie, czy proces utknął. Awaria bazy nie jest sama w sobie powodem restartowania wszystkich replik.
- `terminationGracePeriodSeconds` musi być dłuższe od czasu graceful shutdown aplikacji.
- HPA oparty na CPU wymaga `resources.requests.cpu`; bez tego procent wykorzystania nie ma mianownika.
- `readOnlyRootFilesystem` wymaga jawnych wolumenów dla wszystkich ścieżek zapisu.
- Secret zakodowany base64 nie jest zaszyfrowany. Produkcyjnie potrzebne są RBAC, szyfrowanie etcd lub zewnętrzny secret manager.

## Co weryfikują testy

Testy w `workshop` sprawdzają zarówno zachowanie kodu, jak i kontrakt między warstwami:

- przejścia startup/readiness/shutdown i awarię zależności,
- ochronę ścieżek zapisu przed path traversal,
- zgodność kluczy Deployment z ConfigMap i Secret,
- selektory, named port i endpointy probe,
- relację timeoutu shutdown z grace period,
- rollout, resources, security context, volumes i HPA,
- uruchomienie właściwego artefaktu jako użytkownik non-root.
- decyzję canary na error rate/p99, rollback, flagi, shadow i procedurę incydentową.

To nie zastępuje walidacji przez API server (`kubectl apply --dry-run=server`) ani testu na prawdziwym klastrze, ale szybko wykrywa drift w repozytorium.
