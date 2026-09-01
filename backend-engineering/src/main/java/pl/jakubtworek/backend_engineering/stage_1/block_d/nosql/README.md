# nosql

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** nosql.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „nosql” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ConditionalDocumentStoreTest,QuorumConfigurationTest,ReplicatedValueStoreTest" test`
> - **Role klas:** `AtomicFixedWindowRateLimiter` = `correct`; `QuorumConfiguration` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## NoSQL — model danych wynika z operacji



NoSQL nie jest jedną technologią ani automatycznym sposobem na skalowanie. Baza
dokumentowa, key-value, wide-column i grafowa optymalizują inne operacje i mają
inne granice transakcji. Punktem wyjścia powinien być access pattern oraz
niezmiennik biznesowy, a dopiero później wybór silnika.

## Mapa laboratorium

| Obszar | Pytanie | Wykonywalny przykład |
| --- | --- | --- |
| `modeling` | czy tabela obsługuje konkretny query shape bez scanu? | `AccessPatternDesign` |
| `document` | co embedować, a co trzymać jako referencję? | `OrderDocument`, `UserProfileDocument` |
| `key_value` | jak połączyć licznik i TTL w jednej operacji? | `AtomicFixedWindowRateLimiter` |
| `wide_column` | jak ograniczyć rozmiar i ruch partycji? | `BucketedPartitionKey`, `PartitionLoadAnalyzer` |
| `consistency` | co daje CAS, quorum i read-your-writes? | `ConditionalDocumentStore`, `QuorumConfiguration`, `ReplicatedValueStore` |
| `graph` | kiedy relacja jest ważniejsza od rekordu? | `UserNode`, `ProductNode` |

## Projektowanie access-pattern-first

Przed utworzeniem tabeli zapisz dla każdej operacji:

1. pola wymagane jako equality filters,
2. porządek sortowania i sposób stronicowania,
3. pola potrzebne w odpowiedzi,
4. maksymalny rozmiar pojedynczej partycji,
5. oczekiwany rozkład ruchu między kluczami,
6. wymaganą spójność i reakcję na konflikt.

`AccessPatternDesign` łączy te wymagania ze schematem tabeli. Test pokazuje, że
`orders_by_user_status` obsłuży odczyt dla użytkownika i statusu, ale nie obsłuży
globalnego odczytu wszystkich statusów w kolejności czasu. To nie jest zachęta
do scanu. Nowy query shape zwykle oznacza nową projekcję, zmianę klucza albo
świadomą rezygnację z wymagania.

## Conditional writes zamiast utraconej aktualizacji

Dwa procesy mogą odczytać wersję `1`, obliczyć różne zmiany i próbować zapisać
wynik. Bez warunku drugi zapis nadpisze pierwszy. `ConditionalDocumentStore`
realizuje odpowiednik:

```text
UPDATE document
SET value = :newValue, version = 2
IF version = 1
```

Tylko pierwszy zapis zostanie zastosowany. Drugi otrzyma aktualną wersję i musi
odrzucić operację, połączyć zmiany albo ponownie wykonać logikę. Retry nie może
bezrefleksyjnie powtarzać nieidempotentnego efektu biznesowego.

## Partycje, bucketing i hot keys

Klucz o niskiej kardynalności, np. `status=ACTIVE`, kieruje dużą część ruchu do
jednej partycji. Podobny problem tworzy poprawny semantycznie, ale ekstremalnie
popularny tenant. `PartitionLoadAnalyzer` mierzy udział najbardziej obciążonego
klucza, a `BucketedPartitionKey` dodaje dwa mechanizmy:

- bucket czasowy ogranicza wzrost partycji,
- deterministyczny shard rozprasza równoczesne zapisy.

Sharding zwiększa koszt odczytu: klient musi znać shard albo wykonać fan-out do
kilku partycji i scalić wynik. Liczby bucketów oraz shardów wynikają z pomiarów,
retencji i limitów silnika, a nie z uniwersalnej recepty.

## Quorum: N, R i W

Dla `N` replik, odczytu wymagającego `R` odpowiedzi i zapisu wymagającego `W`:

- `R + W > N` zapewnia przecięcie zbioru odczytu z udanym zapisem,
- `2W > N` zapewnia przecięcie dwóch udanych quorum zapisu,
- mniejsze `R` lub `W` zwiększa tolerancję awarii, ale osłabia gwarancje.

Same nierówności nie gwarantują linearizability. Nadal znaczenie mają wersje,
wybór koordynatora, konflikty, sloppy quorum, hinted handoff, read repair oraz to,
czy wszystkie repliki stosują ten sam porządek wersji. `QuorumConfiguration`
pokazuje matematykę zbiorów, nie emuluje konkretnej bazy.

## Replication lag i read-your-writes

`ReplicatedValueStore` oddziela stan leadera od opóźnionej repliki. Po zapisie
zwykły odczyt z repliki może zwrócić poprzednią wartość. Klient otrzymuje token
minimalnej wersji; odczyt read-your-writes trafia do repliki dopiero, gdy ta
dogoniła token, a wcześniej jest kierowany do leadera.

Jest to gwarancja sesyjna dla konkretnego klienta. Nie oznacza, że wszyscy
użytkownicy natychmiast zobaczą ten sam stan. W realnym systemie podobny efekt
można uzyskać przez routing do leadera, session token, sticky reads lub oczekiwanie
na wymaganą pozycję logu replikacji.

## Atomowy rate limiting i TTL

Sekwencja `GET`, zwiększenie licznika w aplikacji, `SET` oraz osobne `EXPIRE` ma
race condition i może pozostawić klucz bez TTL po częściowej awarii.
`AtomicFixedWindowRateLimiter` wykonuje decyzję, increment i ustalenie czasu
wygaśnięcia jako jedną sekcję atomową. W Redisie odpowiednikiem może być skrypt
Lua albo odpowiednio zaprojektowana funkcja serwerowa.

Granica TTL jest domknięta: dokładnie w chwili `resetAt` stara wartość przestaje
obowiązywać. Odrzucone żądanie nie przedłuża okna, bo zmieniłoby fixed window w
inny algorytm. Fixed window może dopuszczać burst na granicy dwóch okien; sliding
window lub token bucket rozwiązują inny problem kosztem większej złożoności.

## Czego te przykłady nie udają

Są to deterministyczne modele semantyki, dzięki którym failure mode można
sprawdzić szybkim testem. Monitor JVM nie jest rozproszoną transakcją, mapa nie
jest Redisem, a kolejka w pamięci nie jest protokołem replikacji. Produkcyjną
gwarancję należy potwierdzić dokumentacją konkretnej bazy i testem integracyjnym
obejmującym jej prawdziwe operacje serwerowe.

## Uruchomienie

Z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=AccessPatternDesignTest,ConditionalDocumentStoreTest,QuorumConfigurationTest,ReplicatedValueStoreTest,PartitioningTest,AtomicFixedWindowRateLimiterTest" test
```

Testy pokazują zarówno poprawną ścieżkę, jak i sytuacje, w których zapytanie nie
pasuje do tabeli, writer ma starą wersję, replika jest opóźniona, partycja staje
się gorąca albo limit został już wyczerpany.
