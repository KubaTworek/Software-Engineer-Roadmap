# Przekrojowy przepływ adapterów i projekcji

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** Przekrojowy przepływ adapterów i projekcji.
> - **Typowy błąd:** Osobny commit agregatu i outboxa pozostawiający lukę publikacji.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ReferenceFlowTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** `InMemoryProductQueryUseCase` = `simulation`; `VersionedProductSearchProjectionAdapter` = `production-boundary`.
> - **Granica:** Test granicy modułu lub lokalnej transakcji nie gwarantuje atomowości między procesami ani zgodności niezależnie wdrażanych klientów.
<!-- material-card:end -->

## Problem i niezmiennik

Dodanie GraphQL, gRPC, wyszukiwarki i WebSocket nie może utworzyć czterech modeli
biznesowych. Wszystkie wejścia delegują do tego samego portu aplikacyjnego, a
ponowne dostarczenie outboxa nie publikuje drugi raz tej samej wersji projekcji.

## Przepływ

`ProductQueryUseCase` jest kanoniczną granicą odczytu. `ProductGraphQlController`
i `ProductGrpcService` są adapterami. Po zmianie produktu
`ProductChangedMessage` trafia do `CatalogProjectionRelay`, który aktualizuje
wersjonowany indeks i dopiero po przyjęciu nowej wersji publikuje komunikat live:

```text
GraphQL / gRPC → ProductQueryUseCase
outbox → CatalogProjectionRelay → search projection → WebSocket
```

## Najważniejszy test

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=ReferenceFlowTest test
```

Test pokazuje wspólny use case i idempotentną granicę projekcja–live update.

## Granice produkcyjne

Relay jest synchronicznym modelem. W produkcji marker, retry i publikacja live
mają osobne granice awarii; search oraz WebSocket muszą być rekoncyliowalne ze
źródłem prawdy, a event ID wymaga trwałej deduplikacji.
