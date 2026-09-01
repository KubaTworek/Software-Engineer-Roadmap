# GraphQL — kontrolowany koszt elastycznego API

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** GraphQL — kontrolowany koszt elastycznego API.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „GraphQL — kontrolowany koszt elastycznego API” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=GraphQlBoundaryTest,GraphQlHttpRuntimeTest" test`
> - **Role klas:** `NaiveProductResolver` = `naive`; `ProductGraphQlController` = `production-boundary`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Problem i niezmiennik

Klient może wybrać potrzebny graf pól, ale ta elastyczność nie może tworzyć
nieograniczonego kosztu ani omijać autoryzacji zasobu. Zapytanie przekraczające
budżet jest odrzucane przed resolverami, a pole wrażliwe jest sprawdzane dla
konkretnego obiektu.

## Kontrprzykład i rozwiązanie

`NaiveProductResolver` pokazuje N+1: każdy parent powoduje osobny odczyt.
`BatchingProductResolver` zbiera unikalne klucze, wykonuje jeden batch i odtwarza
kolejność odpowiedzi. `QueryComplexityGuard` liczy depth i koszt, a
`ProductFieldAuthorization` chroni pole niezależnie od dostępu do endpointu.
Koszt list mnoży koszt poddrzewa przez jawny limit cardinality, więc płytkie
zapytanie zwracające tysiące obiektów nie omija budżetu.
`ProductGraphQlController` oraz `schema.graphqls` uruchamiają ten model przez
prawdziwy Spring GraphQL/GraphQL Java i delegują do wspólnego
`ProductQueryUseCase`.

## Najważniejszy test

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=GraphQlBoundaryTest,GraphQlHttpRuntimeTest" test
```

Test pokazuje odrzucenie kosztownego grafu, różnicę N+1/batching oraz field-level
authorization z uwzględnieniem ownership.

## Kiedy użyć, a kiedy nie

GraphQL ma sens, gdy wielu klientów potrzebuje zmiennych przekrojów wspólnego
grafu. Dla stabilnego CRUD lub prostych komend REST bywa czytelniejszy,
łatwiejszy do cache'owania i operacyjnie tańszy. GraphQL nie usuwa wersjonowania:
deprecations, nullability i semantyka pól nadal tworzą kontrakt.

## Granice produkcyjne

Test runtime parsuje SDL i wykonuje zapytanie przez prawdziwy endpoint HTTP.
Produkcja nadal musi dodać persisted queries, limity list, deadline, DataLoader
per request, bezpieczny model błędów i tracing resolverów. Use case i domena
pozostają niezależne od GraphQL.
