# Booking & Capacity Platform

Projekt edukacyjno-portfolio, który stosuje cały Stage 1 modułu
[`backend-engineering`](../../backend-engineering/README.md) w jednej domenie.
Nie jest checklistą frameworków ani wzorcem aplikacji produkcyjnej 1:1. To
wykonywalne laboratorium poprawności pojedynczej aplikacji Java/Spring:
problem → niezmiennik → kontrprzykład → rozwiązanie → test → ograniczenie.

## Najkrótsza ścieżka przez projekt

Główny problem brzmi: wiele requestów jednocześnie rezerwuje ograniczoną pulę.

```text
POST reservation
    -> atomowy UPDATE capacity_pools
    -> INSERT reservation ze stanem PENDING i expires_at
    -> confirm | cancel | scheduler expiry
    -> zwolnienie capacity najwyżej raz
```

Najważniejsze niezmienniki:

```text
0 <= available_capacity <= total_capacity
liczba przyjętych rezerwacji nie przekracza pojemności
CONFIRMED ma confirmed_at
CANCELLED ma cancelled_at
PENDING po expires_at nie może zostać potwierdzone
anulowanie lub expiry zwalnia jedno miejsce najwyżej raz
```

Uruchom najpierw:

```shell
mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

Następnie porównaj `NaiveReservationService` z
`AtomicSqlReservationService` w testach współbieżności. Naiwna klasa jest celowym
kontrprzykładem i nie należy do głównego flow.

## Rozszerzenia: Linux, build i operacje SQL

[Przewodnik operacyjny i build](docs/operations-and-build.md) łączy budżet repliki
BookingMemoryBudget, pomiar cgroup/RSS, obserwacje transakcji Booking i diagnostykę
classpath. Test BookingMemoryBudgetTest chroni rachunek limitu także przed overflow.
Skrypty są jawnie oddzielone od głównego flow rezerwacji; nie zmieniają jego gwarancji.

## Mapa Stage 1

Cały projekt odpowiada Stage 1. Nazwy testów i dokumentów opisują teraz
mechanizm, a nie historyczną kolejność dodawania funkcji.

| Blok roadmapy | Zastosowanie w projekcie | Najkrótszy dowód |
| --- | --- | --- |
| 1A — concurrency, czas, async | race condition, cztery strategie capacity, bounded executor, `CompletableFuture`, cancellation, virtual threads + `Semaphore`, demand/cancel Reactive Streams, trwały scheduler expiry | `ReservationConcurrencyIntegrationTest`, `StageOneEducationTest`, `DemandAwareReservationPublisherTest`, `ReservationEntityTest` |
| 1B — JVM i performance | allocation pressure, lock contention, pule wątków, JFR, JMH oraz test całej usługi w modelu open/closed | `JvmProfilingIntegrationTest`, profile `jmh-smoke`, scenariusz k6 |
| 1C — Spring | bean lifecycle, AOP/proxy, self-invocation, transakcje, lazy loading, N+1, konfiguracja z walidacją, MVC, JWT i data-based authorization | `SpringProxyPitfallIntegrationTest`, `AuthorizationIntegrationTest` |
| 1D — dane | Flyway, constrainty, optimistic/pessimistic/atomic locking, indeksy, `EXPLAIN`, pełny keyset `(createdAt,id)`, Redis, Mongo/read model | `SqlPerformanceIntegrationTest`, `NoSqlCacheIntegrationTest`, profil `infrastructure-tests` |
| 1E — bezpieczna zmiana | jawny `Clock`, characterization boundary, branch by abstraction i ochrona semantyki zaokrąglenia | `StageOneEducationTest` |
| 1F — networking | connect/read/request timeout, DNS TTL, keep-alive, HTTP/2 multiplexing, half-open socket, pula połączeń i zwielokrotnienie retry | `NetworkingStageOneTest`, `StageOneEducationTest` |

## Model domenowy i własność danych

- `Organization` jest tenantem organizatora.
- `Event` opisuje wydarzenie; nie przechowuje zmiennej dostępności.
- `CapacityPool` jest osobnym, gorącym agregatem zapisu z `@Version`.
- `Customer` reprezentuje osobę rezerwującą, nie konto administracyjne.
- `Reservation` jest maszyną stanów z czasem utworzenia i wygaśnięcia.
- `AppUser` i `RefreshToken` służą authentication/authorization.
- PostgreSQL jest źródłem prawdy. Redis i Mongo są adapterami odczytowymi.

Wydzielenie `CapacityPool` pozwala porównać ochronę stanu przez lock JVM,
optimistic locking, pessimistic locking i warunkowy update SQL bez ukrywania
problemu w dużej encji `Event`.

### Cykl życia rezerwacji

```text
                     confirm przed deadline
              +--------------------------------> CONFIRMED
              |
PENDING ------+------ cancel ------------------> CANCELLED
              |
              +------ expires_at <= Clock -----> EXPIRED
```

`Clock` jest wstrzykiwany do przypadków użycia. Test ustawia dokładny moment
graniczny, zamiast używać `sleep`. Scheduler nie trzyma zadań wyłącznie w
pamięci: po restarcie ponownie pyta PostgreSQL o przeterminowane rekordy. Małe
batche z `FOR UPDATE SKIP LOCKED` pozwalają kilku workerom dzielić kolejkę bez
czekania na te same wiersze. To nie jest rozproszony scheduler z fencing — ten
problem należy do Stage 3.

## Współbieżność: granice gwarancji

| Implementacja | Co chroni | Ograniczenie |
| --- | --- | --- |
| `NaiveReservationService` | nic pomiędzy odczytem i zapisem | pokazuje lost update/overselling |
| `SynchronizedReservationService` | sekcja krytyczna jednej JVM | nie chroni kilku instancji |
| `OptimisticLockingReservationService` | wersja rekordu i wykrycie konfliktu | konflikt wymaga jawnego retry |
| `PessimisticLockingReservationService` | wiersz bazy przez czas transakcji | lock wait i ryzyko deadlocka |
| `AtomicSqlReservationService` | warunek i decrement w jednym SQL | logika częściowo znajduje się w SQL |

Główny `ReservationService` stosuje atomowy update. Constraint bazy pozostaje
ostatnią linią obrony, ponieważ walidacja w Javie nie chroni przed inną
transakcją, skryptem administracyjnym ani błędem w nowej ścieżce kodu.

Virtual threads służą do blokującego I/O. Nie przyspieszają pracy CPU-bound i
nie powiększają PostgreSQL. `VirtualThreadReservationBatch` uruchamia tanie
wątki, ale przepuszcza do downstreamu tylko liczbę operacji określoną przez
`Semaphore`. Przerwanie jest propagowane; nie jest traktowane jak zwykły błąd.

`DemandAwareReservationPublisher` pokazuje sam kontrakt `Flow`: publisher nie
wysyła więcej elementów niż zamówiono i po `cancel()` nie emituje kolejnych
sygnałów. Przykład nie udaje pełnego frameworka reaktywnego ani nie miesza
backpressure z wyborem schedulera.

## Spring, transakcje i JPA

Przykłady `pitfall` są izolowane od głównego flow i pokazują:

- self-invocation omijające proxy `@Transactional`/AOP;
- różnicę między wywołaniem obiektu a wywołaniem proxowanego beana;
- `LazyInitializationException` po zamknięciu persistence context;
- N+1 oraz naprawy przez DTO projection, fetch join i `EntityGraph`;
- flush jako synchronizację persistence context z bazą, nie commit;
- lifecycle beana i właściwy moment użycia zależności.

`ReservationPolicyProperties` i `JwtProperties` są walidowane przy starcie.
Brak sekretu JWT jest błędem konfiguracji, a nie powodem do cichego użycia
wspólnego klucza developerskiego. Lokalnie trzeba ustawić `JWT_SECRET`.

## SQL, indeksy i paginacja

Migracje `V1`–`V4` tworzą schemat, indeksy pod rzeczywiste access patterns oraz
constrainty domenowe. Nie edytuj zastosowanej migracji — dodaj następną.

Najważniejsze access patterns:

- eventy: `(city, category, starts_at)`;
- rezerwacje organizacji: `(organization_id, status, created_at DESC)`;
- rezerwacje klienta: `(customer_id, created_at DESC, id DESC)`;
- kolejka expiry: częściowy indeks `(expires_at, id)` dla aktywnych statusów.

Offset pagination jest prosta, ale duży offset musi zostać pominięty przez bazę i
zwykle wymaga `COUNT(*)`. Keyset korzysta ze stabilnego kursora
`(afterCreatedAt, afterId)`. Obie wartości są obowiązkowe razem; samo
`createdAt` mogłoby pominąć rekordy utworzone w tej samej chwili. API zwraca
własne `OffsetPageResponse`, a nie niestabilny JSON `PageImpl` Spring Data.

H2 daje szybką informację zwrotną, lecz nie dowodzi semantyki PostgreSQL.
Profil infrastrukturalny uruchamia Flyway i wykonuje PostgreSQL-specyficzne
`SKIP LOCKED`, optimistic locking, wyścig find-or-create oraz constrainty na
prawdziwym silniku.

## NoSQL i wyszukiwanie

PostgreSQL pozostaje source of truth.

- cache detali i snapshot dostępności może być nieaktualny; write path nie ufa
  wartości z cache;
- skrypt Lua łączy `INCR` i ustawienie pierwszego TTL w jedną atomową operację
  Redisa; test współbieżny sprawdza wszystkie zwrócone sekwencje i wygaśnięcie;
- `EventSearchDocument` jest denormalizowanym read modelem pod access pattern;
- wariant in-memory umożliwia szybkie testy, wariant Mongo pokazuje granicę
  infrastrukturalną i eventual consistency.

Warunkowy zapis Mongo odrzuca starszą wersję zdarzenia, ale read model nadal
musi być odtwarzalny z PostgreSQL. Projekt nie implementuje CDC ani pełnej
rekoncyliacji, ponieważ są głównym tematem Stage 2.

## JVM, JFR i JMH

`JvmProfilingService` dostarcza scenariusze allocation pressure, lock contention,
CPU-bound kontra I/O-bound, bounded pool i koszt reprezentacji pieniądza.

Nagranie JFR:

```shell
java -XX:StartFlightRecording=filename=booking.jfr,duration=60s,settings=profile \
  -jar target/booking-capacity-platform-0.1.0-SNAPSHOT.jar
```

Przed nagraniem zapisz pytanie, np. „czy p99 rośnie przez lock contention czy
GC?”. JFR obserwuje system. JMH izoluje mikrofragment i musi mieć warmup, forki,
iteracje oraz dwa workloady o tej samej semantyce.

```shell
mvnw.cmd --batch-mode --no-transfer-progress -Pjmh-smoke -Dtest=JmhSmokeTest test
```

Smoke ma jeden krótki fork i nie daje liczb nadających się do raportu. Adnotacje
benchmarku opisują pełniejszy eksperyment; wynik interpretuj razem z parametrami
JVM i maszyny, nigdy jako „magiczną” stałą.

## Networking

`DownstreamTimeoutPolicy` oddziela:

- connect timeout — zestawienie TCP/TLS;
- read timeout — oczekiwanie na bajty na istniejącym połączeniu;
- request deadline — budżet całego przypadku użycia;
- backoff — czas, który również zużywa deadline.

Retry na wielu warstwach się mnoży: 2 próby klienta × 2 proxy × 2 service mesh =
8 wywołań. `ConnectionPoolGate` pokazuje osobny budżet oczekiwania na połączenie
i bounded concurrency. `CachingDnsResolver` pokazuje, że zmiana adresu instancji
nie jest widoczna przed końcem TTL. `ConnectionReuseModel` porównuje churn portów
i handshake'ów TLS dla nowych połączeń, keep-alive oraz multipleksowania HTTP/2.
`HalfOpenConnection` pokazuje, że lokalny stan socketu nie wykrywa awarii drugiej
strony aż do kolejnego I/O. Test z lokalnym serwerem HTTP dowodzi dodatkowo, że
timeout klienta nie anuluje automatycznie efektu rozpoczętego w downstreamie.

Są to deterministyczne modele semantyki, a nie emulatory stosu TCP. Rzeczywisty
koszt TLS, limity portów ephemeral i zachowanie proxy nadal trzeba potwierdzić
metrykami systemowymi i testem środowiska.

## Bezpieczna refaktoryzacja

`ReservationFeePolicy` pokazuje branch by abstraction: najpierw wydzielamy czas
i chronimy semantykę characterization testem, potem zmieniamy algorytm.
`ChronoUnit.DAYS` liczy pełną odległość; `Period#getDays` zwraca tylko składową
dni po odjęciu miesięcy. `BigDecimal` z jawnym rounding mode chroni kontrakt
pieniężny, którego refaktor do `double` mógłby nieświadomie zmienić.

## API

```text
POST /api/events
GET  /api/events/{eventId}
GET  /api/events?city=Warsaw&category=music&from=2026-06-01T00:00:00Z

POST /api/events/{eventId}/reservations
GET  /api/reservations/{reservationId}
POST /api/reservations/{reservationId}/confirm
POST /api/reservations/{reservationId}/cancel
POST /api/reservations/{reservationId}/confirm-async?paymentScenario=APPROVED

GET /api/customers/{customerId}/reservations?page=0&size=50
GET /api/customers/{customerId}/reservations/keyset?afterCreatedAt=...&afterId=...&size=50
```

Endpointy pod `/api/secure/**`, `/api/spring-pitfalls/**`, `/api/profiling/**` i
`/api/nosql/**` są laboratoriami mechanizmu. Przykłady requestów znajdują się w
[`docs/api.http`](docs/api.http).

Publiczny pozostaje katalog eventów i jawnie wydzielone endpointy laboratoryjne.
Całe write API wymaga authentication. `@PreAuthorize` łączy rolę z właścicielem
danych: customer może operować na własnej rezerwacji, a manager wyłącznie na
zasobach swojej organizacji. Test HTTP rozróżnia `401` od `403` i pokazuje próbę
IDOR między tenantami. Nie jest to pełny Secure SDLC ani polityka ochrony PII.

## Testy: co naprawdę dowodzą

| Rodzaj | Cel | Czego nie dowodzi |
| --- | --- | --- |
| unit | maszyna stanów, czas, demand, timeout budget, refaktoryzacja | konfiguracja Spring/JPA |
| property/stateful | jqwik generuje i zmniejsza setki historii; sprawdzany jest niezmiennik pojedynczego zwolnienia | wszystkie możliwe przeploty produkcyjne |
| MVC slice | walidacja kontraktu bez uruchamiania bazy i infrastruktury | pełny filter chain oraz transakcje |
| Spring integration | proxy, transakcja, mapowanie, pełny use case | dokładna semantyka PostgreSQL |
| concurrency | niezmiennik pod kontrolowanym przeplotem | capacity przy dowolnym ruchu produkcyjnym |
| infrastructure | PostgreSQL locking/races, atomowy Redis + TTL, wersjonowana projekcja Mongo i pełny smoke HTTP przez trzy magazyny | zachowanie bez dostępnego Dockera |
| JMH | koszt konkretnego mikrofragmentu | throughput i p99 całej usługi |
| k6 | load/stress/spike/soak, p95/p99, saturation i różnica open/closed model | przyczyna regresji bez korelacji z JFR, GC i metrykami bazy |
| mutation | czy testy domeny zabijają celowe zmiany operatorów i warunków | poprawność poza wskazanym zakresem klas |

Projekt historycznie ma dużo `@SpringBootTest`. Nowe testy mechanizmów powinny
preferować test bez kontekstu; pełny kontekst zostaje tam, gdzie badana gwarancja
obejmuje proxy, transakcję, security filter chain albo repozytorium.

Workflow [`project-1-ci.yml`](../../.github/workflows/project-1-ci.yml) egzekwuje
te granice osobnymi jobami: szybka suite i JMH smoke, PIT, Testcontainers oraz
krótki test k6 uruchamiający prawdziwą aplikację z PostgreSQL. Dzięki temu awaria
Dockera albo regresja testu obciążeniowego nie ukrywa wyniku testów jednostkowych.

```shell
# szybka, domyślna suite
mvnw.cmd --batch-mode --no-transfer-progress clean verify

# kontrola uruchomienia JMH
mvnw.cmd --batch-mode --no-transfer-progress -Pjmh-smoke -Dtest=JmhSmokeTest test

# prawdziwy PostgreSQL; wymaga działającego Dockera
mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify

# jakość asercji dla rdzenia domeny; raport: target/pit-reports
mvnw.cmd --batch-mode --no-transfer-progress -Pmutation-testing clean test-compile org.pitest:pitest-maven:mutationCoverage

# test całej usługi; profil load-test tworzy lokalnego managera, a setup k6 event
k6 run -e TEST_TYPE=load performance/k6/reservation-load.js
```

Cel PIT nie uruchamia sam faz kompilacji. Dlatego komenda mutacyjna zawiera
`clean test-compile`: działa także na świeżym checkoutcie, bez `target/`
pozostawionego przez wcześniejsze testy. Osobne joby CI współdzielą cache
zależności Maven, ale nie skompilowane klasy aplikacji i testów.

## Uruchomienie lokalne

Wymagania: Java 21 i Docker Compose. Maven Wrapper przypina Maven 3.9.9, więc
globalna instalacja Mavena nie jest wymagana.

Projekt używa Spring Boot 4.0.3 i Java 21, zgodnie z głównym modułem
`backend-engineering`. Migracja jest celowo widoczna w kodzie: startery MVC,
testów i Flyway są jawne, JSON używa Jackson 3 (`tools.jackson`), właściwości
MongoDB mają prefiks `spring.mongodb`, a reprezentacja UUID jest ustawiona na
`STANDARD`. Te szczegóły są częścią kontraktu uruchomieniowego, dlatego chroni
je pełny test Testcontainers, a nie tylko kompilacja.

```shell
docker compose up -d

# PowerShell
$env:JWT_SECRET = "local-secret-with-at-least-32-characters"
mvnw.cmd spring-boot:run
```

Usługi: PostgreSQL 16 na `5432`, Redis 7 na `6379`, MongoDB 7 na `27017` i
HTTP na `8080`. `docker compose stop` zatrzymuje je bez usuwania wolumenów.

## Dokumentacja techniczna

| Plik | Temat |
| --- | --- |
| [`docs/concurrency.md`](docs/concurrency.md) | race condition i strategie capacity |
| [`docs/async.md`](docs/async.md) | pule, futures, timeout i cancellation |
| [`docs/spring-pitfalls.md`](docs/spring-pitfalls.md) | proxy, transakcje i JPA |
| [`docs/sql-performance.md`](docs/sql-performance.md) | access patterns, indeksy i plany |
| [`docs/jvm-profiling.md`](docs/jvm-profiling.md) | JFR, GC i profiling |
| [`docs/performance-testing.md`](docs/performance-testing.md) | load/stress/spike/soak, open/closed model i kryteria przerwania |
| [`docs/security.md`](docs/security.md) | JWT i data-based authorization |
| [`docs/nosql-cache.md`](docs/nosql-cache.md) | Redis, Mongo i granice cache |
| [`docs/decisions.md`](docs/decisions.md) | decyzje i trade-offy |

Dokumentacja i testy używają stabilnych nazw tematów zamiast numerów dawnych
iteracji projektu.

## Granice projektu

Projekt dowodzi lokalnej poprawności monolitu i mechanizmów Stage 1. Nie deklaruje:

- exactly-once dla zewnętrznych efektów;
- spójności Redis/Mongo z PostgreSQL bez rekoncyliacji;
- bezpieczeństwa wieloinstancyjnego dla locka `synchronized`;
- pełnego Secure SDLC, ochrony PII i polityk infrastruktury brzegowej;
- uniwersalnych wyników capacity — scenariusz k6 jest wykonywalny, ale wynik
  zależy od środowiska, datasetu i limitów downstreamów;
- poprawności po podziale sieci lub awarii regionu.

Te granice nie są ukrytym TODO. Wyznaczają przejście do projektów Stage 2 i 3.
W obrębie Stage 1 każda deklarowana gwarancja powinna mieć test, constraint,
pomiar albo wykonywalny scenariusz oraz komentarz wyjaśniający, gdzie się kończy.

## Kontrakt tekstu klienta

Nowe nazwy przechodzą jawne NFC i walidację Unicode w obu ścieżkach zapisu JPA/JDBC. Nie zmieniamy przy tym klucza emaila. [Kontrakt tekstu klienta](docs/text-contracts.md).
