# docker

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** docker.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „docker” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Obraz kontenera



Ten katalog jest trzecią warstwą aplikacji referencyjnej z `../workshop`. Zawiera jeden Dockerfile i nie duplikuje kodu Java ani manifestów Kubernetes.

## Budowanie

Kontekstem jest katalog aplikacji, a Dockerfile pochodzi z tej warstwy:

```powershell
cd ../workshop
docker build -f ../docker/Dockerfile -t demo-api-workshop:dev .
docker run --rm -p 8080:8080 demo-api-workshop:dev
```

Taki układ pokazuje dwie oddzielne odpowiedzialności: `workshop` produkuje artefakt, a `docker` definiuje sposób jego opakowania.

## Decyzje w Dockerfile

- multi-stage build nie przenosi Mavena i źródeł do obrazu runtime,
- wersja Javy jest jawna i wspólna dla build oraz runtime,
- zależności są pobierane w osobnej warstwie dla lepszego cache,
- proces działa jako dedykowany użytkownik `app`, nie root,
- exec form `ENTRYPOINT` czyni JVM procesem PID 1 i pozwala jej odebrać `SIGTERM`,
- konfiguracja środowiskowa nie jest zaszyta w obrazie.

Non-root w obrazie i `runAsNonRoot` w Kubernetes są komplementarne. Pierwsze ustala bezpieczny domyślny użytkownik, drugie wymusza politykę przy wdrożeniu. Produkcyjny pipeline powinien dodatkowo przypinać obrazy bazowe po digest, generować SBOM i skanować podatności.

## Diagnostyka

```powershell
docker inspect demo-api-workshop:dev
docker run --rm demo-api-workshop:dev id
docker history demo-api-workshop:dev
```

Test `DeliveryArtifactsTest` sprawdza nazwę kanonicznego JAR-a, użytkownika non-root i exec form entrypointu. Nie zastępuje to uruchomienia obrazu, ale chroni najważniejsze założenia edukacyjne przed przypadkowym usunięciem.
