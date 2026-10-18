# configuration

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** configuration.
> - **Typowy błąd:** Wbudowanie konfiguracji środowiska w obraz lub uznanie base64 za szyfrowanie.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress -f src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/workshop/pom.xml verify`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Test modelu i manifestu nie uruchamia klastra; routing, SIGTERM, PVC i rollout wymagają osobnego eksperymentu wdrożeniowego.
<!-- material-card:end -->

## Konfiguracja runtime



Ten katalog jest drugą warstwą aplikacji referencyjnej z `../workshop`. Nie zawiera własnej aplikacji Spring ani Deploymentu.

## Artefakty

| Plik | Odpowiedzialność | Czas życia |
|---|---|---|
| `k8s/configmap.yaml` | jawne ustawienia i plik `app.yaml` | niezależny od obrazu |
| `k8s/secret.yaml` | demonstracyjny sekret `DB_DSN` | rotowany niezależnie od obrazu |
| `k8s/pvc.yaml` | dane, które mają przeżyć restart Poda | dłuższy niż Pod |

## Env czy plik?

Zmienne środowiskowe dobrze pasują do małych wartości skalarnych. Montowany plik jest czytelniejszy dla większej konfiguracji i może być atomowo podmieniany przez kubelet. Aplikacja musi jednak świadomie zdecydować, czy odczytuje plik raz przy starcie, czy obserwuje jego zmianę.

Spring mapuje np. `STARTUP_DELAY` na `app.startup-delay`. Walidacja `@ConfigurationProperties` powoduje fail-fast, zamiast odsuwać błąd do pierwszego żądania.

## Dane tymczasowe i trwałe

- `/tmp/demo-api` pochodzi z `emptyDir`: jest współdzielone przez kontenery Poda, ale znika wraz z Podem.
- `/data/demo-api` pochodzi z PVC: może przetrwać odtworzenie Poda zgodnie z polityką storage class.
- obraz i root filesystem pozostają niezmienne.

## Sekrety

Wartość w `secret.yaml` jest wyłącznie lokalnym przykładem. Nie należy commitować prawdziwych poświadczeń. Base64 w polu `data` jest kodowaniem, nie szyfrowaniem; tutaj użyto czytelnego `stringData`, aby nie tworzyć fałszywego poczucia bezpieczeństwa.

Zastosowanie warstwy:

```powershell
kubectl apply -f k8s/
```

Konsumentem tych nazw i kluczy jest `../kubernetes/k8s/deployment.yaml`, a ich zgodność sprawdza `DeliveryArtifactsTest` w aplikacji referencyjnej.
