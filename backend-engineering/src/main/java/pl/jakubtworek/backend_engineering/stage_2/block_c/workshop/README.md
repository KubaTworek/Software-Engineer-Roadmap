# workshop

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** workshop.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „workshop” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## z katalogu workshop



sistent storage |
| `/debug/info` | ograniczony kontekst diagnostyczny |

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
