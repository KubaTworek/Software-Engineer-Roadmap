# gRPC — kontrakt, deadline i kompatybilność

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** gRPC — kontrakt, deadline i kompatybilność.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „gRPC — kontrakt, deadline i kompatybilność” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=GrpcContractTest,GrpcInProcessRuntimeTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Problem i niezmiennik

Wywołanie service-to-service potrzebuje typed contract, ograniczonego budżetu
czasu i jednoznacznej klasyfikacji błędów. Numer pola Protobuf nie może zmienić
znaczenia, a child call nie może otrzymać deadline dłuższego od rodzica.

## Poprawne rozwiązanie i kontrprzykład

`ProtoCompatibilityChecker` realizuje konserwatywną politykę bezpiecznej
ewolucji: wykrywa usunięcie bez `reserved`, zmianę wire type i ponowne użycie
numeru. Samo usunięcie pola nie musi złamać wire compatibility; `reserved`
zapobiega jego późniejszemu, niebezpiecznemu wykorzystaniu. `RpcDeadline` używa
czasu monotonicznego, a `GrpcRetryPolicy` wymaga idempotencji i wolnego budżetu.

Plik `product_query.proto`, wygenerowane stuby, `ProductGrpcService` oraz test
in-process potwierdzają rzeczywiste wywołanie, deadline, cancellation i
propagację metadata do wspólnego `ProductQueryUseCase`.

Naiwnym rozwiązaniem jest uznanie, że kompilujący się `.proto` jest automatycznie
kompatybilny albo że retry `UNAVAILABLE` jest zawsze bezpieczne.

## Najważniejszy test

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=GrpcContractTest,GrpcInProcessRuntimeTest" test
```

Test sprawdza reserved fields, zakaz reuse numeru, dziedziczenie deadline,
warunki retry oraz wywołanie wygenerowanego klienta i serwera.

## Kiedy użyć, a kiedy nie

gRPC dobrze pasuje do wewnętrznych, wielojęzykowych integracji, streamingu i
kontraktów generowanych ze schematu. Publiczne API przeglądarkowe, proste webhooki
lub integracje wymagające łatwego debugowania mogą lepiej pasować do HTTP/JSON.

## Granice produkcyjne

Laboratorium uruchamia wygenerowane stuby i prawdziwy runtime gRPC w transporcie
in-process. Nie testuje jednak sieci HTTP/2, TLS/mTLS, limitów wiadomości ani load
balancingu. Te właściwości wymagają testu wdrożeniowego z realnym portem i proxy.
