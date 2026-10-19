# kubernetes

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** kubernetes.
> - **Typowy błąd:** Readiness przed końcem inicjalizacji i grace period krótszy od shutdown timeout.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml verify`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Test modelu i manifestu nie uruchamia klastra; routing, SIGTERM, PVC i rollout wymagają osobnego eksperymentu wdrożeniowego.
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
