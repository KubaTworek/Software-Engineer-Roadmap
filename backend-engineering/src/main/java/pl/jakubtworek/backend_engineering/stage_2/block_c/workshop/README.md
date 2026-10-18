# Aplikacja referencyjna — lifecycle, konfiguracja i storage

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** Rozdzielenie startup, readiness i liveness oraz zachowanie aplikacji podczas shutdownu.
> - **Typowy błąd:** Obsługa ruchu podczas startupu lub shutdownu oraz mylenie PVC z emptyDir.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml verify`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** `ApplicationHealthState` = model lifecycle; `EnvironmentDependencyHealth` = `simulation`; `ProbeController` = `production-boundary`.
> - **Granica:** Test modelu i manifestu nie uruchamia klastra; routing, SIGTERM, PVC i rollout wymagają osobnego eksperymentu wdrożeniowego.
<!-- material-card:end -->

## Problem i niezmiennik

Aplikacja może mieć działający proces, a jednocześnie nie być gotowa na ruch.
Laboratorium pokazuje ten stan przy opóźnionym starcie, awarii zależności i
zamykaniu instancji. Niezmiennik: instancja przyjmuje ruch biznesowy dopiero po
inicjalizacji i przestaje być gotowa podczas shutdownu. Awaria zależności obniża
readiness, lecz sama nie oznacza śmierci procesu.

To jedyna aplikacja Java w Stage 2C. Kolejne warstwy to
[konfiguracja runtime](../configuration/README.md), [obraz Docker](../docker/README.md)
i [wdrożenie Kubernetes](../kubernetes/README.md). Decyzje po wdrożeniu opisuje
[progressive delivery](../progressive_delivery/README.md).

## Uruchomienie

Polecenia wykonuj z katalogu `backend-engineering`; wymagają Javy 21.
Szybkie testy nie potrzebują Dockera ani klastra.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml verify
.\mvnw.cmd -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml spring-boot:run
```

Domyślny port to `8080`, a opóźnienie startu to `5s`. Przy uruchomieniu poza
kontenerem ustaw `SCRATCH_DIRECTORY` i `PERSISTENT_DIRECTORY` na katalogi, do
których masz prawo zapisu. Ich nazwy nie zapewniają trwałości: dopiero montowanie
wolumenu ustala, czy dane przetrwają utratę Poda.

## Przepływ i endpointy

`StartupCoordinator` ustawia gotowość po inicjalizacji, `ApplicationHealthState`
łączy stan lifecycle z sygnałem zależności, a `ShutdownCoordinator` wycofuje
readiness przy zamykaniu kontekstu. Naiwnym rozwiązaniem byłby jeden endpoint
health sterujący jednocześnie restartem i ruchem.

| Metoda i ścieżka | Znaczenie |
| --- | --- |
| `GET /` | przykładowy ruch aplikacyjny, odrzucany przy braku gotowości |
| `GET /startupz` | koniec inicjalizacji; wcześniej `503` |
| `GET /readyz` | gotowość do ruchu; brak gotowości daje `503` |
| `GET /livez` | żywotność procesu; symulowany deadlock daje `500` |
| `GET /config/mounted` | odczyt montowanego pliku konfiguracji |
| `GET/POST /storage/scratch` | odczyt i zapis pliku w katalogu tymczasowym |
| `GET/POST /storage/persistent` | odczyt i zapis pliku w katalogu montowanym jako trwały |
| `GET /debug/info` | ograniczony kontekst diagnostyczny |
| `GET /admin/simulate-readiness-failure` | lokalne wstrzyknięcie awarii readiness |
| `GET /admin/simulate-deadlock` | lokalny model utraty liveness |

## Dowody i ograniczenia

- `ApplicationHealthStateTest` sprawdza startup, awarię zależności i shutdown.
- `StorageServicesTest` sprawdza pliki oraz ochronę przed path traversal.
- `DeliveryArtifactsTest` kontroluje zgodność konfiguracji, obrazu i manifestów.

Test artefaktów nie dowodzi działania control plane, montowania PVC ani dostarczenia
SIGTERM. Te zachowania sprawdź na kind/minikube zgodnie z instrukcją Kubernetes.
Model zależności nie odpytuje prawdziwej bazy. Endpointy `/admin/simulate-*`,
`/debug/info` i `/config/mounted` służą lokalnej diagnostyce; przed publicznym wdrożeniem
trzeba ograniczyć ich dostęp. Symulacja deadlocku zmienia stan health, a nie
zakleszcza fizycznie wątków.

## Scenariusze diagnostyczne

```powershell
kubectl get pods -o wide
kubectl describe pod -l app=demo-api
kubectl logs deployment/demo-api
kubectl get service,endpointslices
kubectl rollout history deployment/demo-api
kubectl top pod
```

Zaczynaj od stanu obiektów i zdarzeń, następnie sprawdzaj logi, endpointy, konfigurację i sieć. `kubectl exec` lub ephemeral container są narzędziami późniejszego etapu diagnozy, szczególnie gdy właściwy obraz jest celowo minimalny.

## CI/CD

Plik `.github/workflows/ci.yaml` jest przykładem pipeline’u dla wydzielonego laboratorium; zagnieżdżony workflow nie jest aktywnym workflow całego repozytorium. Aktywne CI repozytorium znajduje się w głównym `.github/workflows` i uruchamia ten jeden moduł Maven.

Pipeline powinien promować ten sam digest obrazu, a nie budować ponownie dla każdego środowiska. Tag commita jest użyteczny do identyfikacji, ale deployment produkcyjny najlepiej przypinać po digest. Rollback aplikacji nie zawsze oznacza rollback danych — migracje muszą być kompatybilne wstecz w okresie rollout.
