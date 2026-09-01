# configuration

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** configuration.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „configuration” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
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
