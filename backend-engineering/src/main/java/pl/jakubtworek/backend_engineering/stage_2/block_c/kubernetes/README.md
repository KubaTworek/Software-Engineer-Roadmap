# kubernetes

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** kubernetes.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „kubernetes” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Warstwa Kubernetes



Ten katalog wdraża aplikację z `../workshop`, obraz z `../docker` i konfigurację z `../configuration`. Nie zawiera ich kopii.

## Artefakty

| Plik | Pokazywany kontrakt |
|---|---|
| `k8s/deployment.yaml` | repliki, rollout, lifecycle, resources, security i volumes |
| `k8s/service.yaml` | stabilny endpoint oraz routing po labelach i named port |
| `k8s/hpa-cpu.yaml` | skalowanie względem CPU request |
| `examples/hpa-custom-metric.yaml` | alternatywa oparta na metryce aplikacyjnej, wymagająca adaptera metrics API |

## Kolejność uruchomienia

```powershell
kubectl apply -f ../configuration/k8s/
kubectl apply -f k8s/
kubectl rollout status deployment/demo-api
kubectl port-forward service/demo-api 8080:80
```

Lokalny obraz trzeba wcześniej zbudować i załadować do klastra kind/minikube.

## Trzy probe’y, trzy pytania

- startup: czy aplikacja zakończyła inicjalizację?
- readiness: czy nowy ruch może być teraz skierowany do tej repliki?
- liveness: czy proces potrafi kontynuować pracę bez restartu?

Nie należy używać tego samego warunku dla wszystkich trzech. Szczególnie liveness nie powinna zależeć bezpośrednio od bazy, bo jej awaria wywołałaby lawinowe restarty zdrowych procesów.

## Rollout i shutdown

`maxUnavailable: 0` oraz `maxSurge: 1` utrzymują pojemność podczas aktualizacji, ale wymagają zapasu zasobów w klastrze. Przy `SIGTERM` aplikacja najpierw opuszcza readiness, a następnie kończy pracę. `SHUTDOWN_TIMEOUT=25s` mieści się w `terminationGracePeriodSeconds=30`, pozostawiając margines dla kubeleta.

## Bezpieczeństwo i zasoby

Manifest wymusza non-root, blokuje escalation, usuwa capabilities, ustawia seccomp, wyłącza automatyczny token ServiceAccount i montuje read-only root filesystem. Jawne requests/limits są istotne nie tylko dla schedulera: CPU request stanowi mianownik dla HPA opartego na procentowym wykorzystaniu.

Wariant custom metrics jest szkieletem edukacyjnym. Zadziała dopiero po dostarczeniu metryki przez Prometheus Adapter lub inny adapter Kubernetes Metrics API. Jest poza katalogiem aplikowanym domyślnie, ponieważ dwa HPA nie powinny jednocześnie sterować tym samym Deploymentem.
