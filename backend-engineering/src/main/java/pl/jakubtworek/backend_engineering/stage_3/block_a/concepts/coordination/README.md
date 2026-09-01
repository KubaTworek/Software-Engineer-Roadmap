# coordination

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** coordination.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „coordination” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ConsistentHashAndIdGenerationTest,DistributedCoordinationTest,ScheduledJobLeaseIntegrationTest" test`
> - **Role klas:** `FencedRegister` = `correct`; `InMemoryLeaseCoordinator` = `simulation`.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Koordynacja w systemach rozproszonych



Pakiet zamienia ogólne hasło „użyj distributed locka” w zestaw osobnych
gwarancji. Główny scenariusz obejmuje dwóch workerów przetwarzających ten sam
zasób. Worker A otrzymuje lease, zatrzymuje się na dłużej niż jego ważność,
a worker B rozpoczyna nowy term. Po wznowieniu A nadal może wykonywać kod — samo
wygaśnięcie wpisu w systemie blokad nie zatrzymuje procesu.

## Model gwarancji

| Mechanizm | Co gwarantuje | Czego nie gwarantuje |
| --- | --- | --- |
| lease | czasowe prawo własności według zegara koordynatora | natychmiastowego zatrzymania starego procesu |
| fencing token | monotoniczny numer kolejnego termu | ochrony, jeśli downstream ignoruje token |
| leader election | wybór właściciela pracy koordynacyjnej | że każdy uczestnik natychmiast zobaczy zmianę lidera |
| distributed lock | ograniczenie równoczesnego wejścia według lock service | poprawności efektu w osobnej bazie lub usłudze |
| consistent hashing | ograniczenie liczby kluczy przenoszonych po zmianie klastra | replikacji, równowagi przy złym hashu ani migracji danych |
| Snowflake-style ID | lokalne generowanie unikalnych, uporządkowanych identyfikatorów | odporności na zduplikowany node ID i cofnięcie zegara |

## Lease i fencing token krok po kroku

```text
t=0  coordinator -> worker A: lease(token=1, expiresAt=5)
t=2  worker A zatrzymuje się przez długą pauzę lub problem sieciowy
t=5  lease wygasa według zegara koordynatora
t=6  coordinator -> worker B: lease(token=2, expiresAt=11)
t=7  worker B zapisuje wynik z tokenem 2
t=8  worker A wraca i próbuje zapisać wynik z tokenem 1 -> odrzucony
```

`InMemoryLeaseCoordinator` wykonuje atomowe acquire, renew i release. Token rośnie
przy każdym nowym termie i nie wraca do zera po zwolnieniu lease'a. Renewal
przedłuża bieżący term bez zmiany tokena. Próba odnowienia lub zwolnienia starego
termu nie może zmienić aktualnego właściciela.

`FencedRegister` reprezentuje zasób docelowy, który pamięta najwyższy zaakceptowany
token. Zapis ze starszym numerem rzuca `StaleFencingTokenException`. To downstream,
a nie klient blokady, wymusza ostateczną gwarancję. Jeśli aplikacja pobierze lock
w Redisie, a potem wykona bezwarunkowy zapis w innej bazie, wygaśnięcie locka samo
nie ochroni bazy przed spóźnionym procesem.

Model rejestru pozwala wykonywać wiele zapisów w jednym termie. Odrzuca token
mniejszy od najwyższego już zaobserwowanego. Produkcyjny zasób musi utrwalać tę
wartość atomowo razem z chronioną zmianą, na przykład warunkiem
`UPDATE ... WHERE fencing_token <= :newToken`.

## Leader election i split-brain

`LeaderElection` nadaje lease'owi znaczenie termu lidera. Heartbeat może odnowić
tylko nadal aktywny term. Po wygaśnięciu node B może wygrać kolejną elekcję, ale
node A może jeszcze przez chwilę uważać się za lidera, bo:

- jest odcięty od koordynatora,
- zatrzymał go długi GC pause,
- nie otrzymał odpowiedzi o utracie termu,
- jego lokalny zegar pokazuje inny czas.

To jest praktyczna postać split-brain. Nie należy budować bezpieczeństwa na
lokalnym `boolean leader`. Każdy krytyczny efekt musi być związany z termem,
wersją albo transakcją, którą potrafi zweryfikować system przechowujący dane.

## Clock skew

O ważności lease'a decyduje zegar autorytatywnego koordynatora. Worker nie powinien
na podstawie własnego czasu dowodzić, że nadal jest właścicielem. Wolniejszy zegar
może widzieć lease jako aktywny, gdy koordynator przyznał już następny term.

W praktyce czas trwania lease'a musi uwzględniać opóźnienia sieci, pauzy procesu
i czas odnowienia. Monotoniczny czas nadaje się do lokalnego mierzenia duration,
ale nie rozwiązuje uzgadniania własności między maszynami. Fencing token ogranicza
wpływ błędnego przekonania workera o aktualnym czasie.

## Identyfikatory generowane w wielu instancjach

`SnowflakeIdGenerator` dzieli dodatni `long` na:

- 41 bitów czasu od ustalonej epoki,
- 10 bitów identyfikatora noda,
- 12 bitów sekwencji w tej samej milisekundzie.

Generator pokazuje dwie ważne granice. Każda aktywna instancja musi mieć unikalny
`nodeId`, a cofnięcie zegara jest problemem poprawności, nie tylko obserwowalności.
Przykład kończy wtedy operację błędem zamiast ryzykować kolizję. Produkcyjne
strategie mogą czekać, używać logicznego czasu albo pobierać zakresy z centralnego
generatora, ale każda z nich ma inny kompromis dostępności i koordynacji.

## Consistent hashing i przebudowa pierścienia

`ConsistentHashRing` umieszcza fizyczny node w wielu punktach wirtualnych. Klucz
należy do pierwszego punktu zgodnego z kierunkiem pierścienia. Po dodaniu noda
przenoszą się przede wszystkim zakresy przejęte przez nowy node; klasyczne
`hash(key) % nodeCount` zmieniłoby przypisanie większości kluczy.

Virtual nodes poprawiają rozkład i pozwalają modelować różną pojemność, ale nie
usuwają wszystkich problemów. Dodanie serwera nadal wymaga migracji lub rozgrzania
danych, a request może dotrzeć zanim dane będą gotowe. W systemie replikowanym
ring musi dodatkowo wybrać kilka różnych fizycznych nodów i określić quorum.

## Granice laboratorium

- koordynator jest pojedynczym, zsynchronizowanym obiektem w pamięci,
- nie modeluje konsensusu, trwałego logu ani awarii samego koordynatora,
- leader election nie implementuje protokołu Raft/Paxos,
- fencing działa dopiero, gdy nowszy token został zaakceptowany przez downstream,
- generator ID zakłada poprawnie przydzielone unikalne node ID,
- ring pokazuje routing, nie transfer i replikację danych.

Te uproszczenia są jawne. Celem jest nauczenie kontraktów, które należy sprawdzić
przy wyborze gotowej bazy, lock service, systemu service discovery lub biblioteki.

## Testy

Z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=DistributedCoordinationTest,ConsistentHashAndIdGenerationTest,ScheduledJobLeaseIntegrationTest' test
```

Testy pokazują również kontrprzykłady: spóźniony zapis bez fencing tokena,
split-brain po wygaśnięciu termu, rozbieżność zegarów i cofnięcie czasu generatora.
`ScheduledJobLeaseIntegrationTest` łączy lease i fencing z
[laboratorium czasu oraz schedulera](../../../../stage_1/block_a/temporal_correctness/README.md):
stary worker nie może zatwierdzić wyniku po przejęciu zadania przez nowy term.
