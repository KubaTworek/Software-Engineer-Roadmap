# GraphQL — kontrolowany koszt elastycznego API

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** GraphQL — kontrolowany koszt elastycznego API.
> - **Typowy błąd:** N+1 w resolverach i autoryzacja jedynie wejścia do całego zapytania.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=GraphQlBoundaryTest,GraphQlHttpRuntimeTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** `NaiveProductResolver` = `naive`; `ProductGraphQlController` = `production-boundary`.
> - **Granica:** Test granicy modułu lub lokalnej transakcji nie gwarantuje atomowości między procesami ani zgodności niezależnie wdrażanych klientów.
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
Model `QueryComplexityGuard` pozostaje małym ćwiczeniem. Granica runtime ma
osobny `ExpandedOperationBudget` podłączony jako Instrumentation: działa po
walidacji/koercji zmiennych i przed resolverami. Liczy aliasy i każde użycie
fragmentu, ogranicza pracę rozwijania AST, depth=5, koszt=100 i listę ids do 20.
To konserwatywny budżet, także dla pól pomijanych przez skip/include.
`ProductGraphQlController` deleguje odczyt do `ProductQueryUseCase`, a pole
internalMarginPercent niezależnie sprawdza tożsamość Spring Security i ownership.

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

Test runtime wykonuje HTTP: odrzuca aliasy/fragmenty i za dużą listę ze zmiennej
bez odczytu danych, sprawdza ownership pola oraz propagację null z niedostępnego
non-null pola do nullable Product. BatchLoaderRegistry daje DataLoader/cache
per request; dwa aliasy tego samego ID odczytują port raz, kolejny request ponownie.
To deduplikacja i batching dispatchu: obecny port wykonuje odczyt per unikalny
ID w pamięci. Prawdziwy SQL IN wymaga adaptera batch po stronie storage.
Produkcja nadal potrzebuje limitu rozmiaru requestu, persisted queries zależnie
od klienta, deadline storage, bezpiecznego modelu błędów i tracingu resolverów.
Mechanizm rejestracji opisuje [Spring GraphQL](https://docs.spring.io/spring-graphql/reference/request-execution.html).
