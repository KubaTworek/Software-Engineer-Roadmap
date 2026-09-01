# temporal correctness

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** temporal correctness.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „temporal correctness” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DeadlineAndClockTest,RecurringJobSchedulerTest,TimeRepresentationTest" test`
> - **Role klas:** `FencedJobExecutionLedger` = `correct`; `InMemoryScheduleStore` = `simulation`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Poprawność czasu i zadań okresowych



Czas w backendzie nie jest pojedynczą wartością. Może oznaczać moment na osi
czasu, lokalną intencję biznesową, czas trwania, deadline albo obserwację zegara
konkretnej maszyny. Pomylenie tych znaczeń prowadzi do błędów, których często
nie widać aż do zmiany czasu, restartu albo awarii części systemu.

## Mapa laboratorium

| Element | Pytanie, na które odpowiada |
| --- | --- |
| `TimeRepresentation` | co przechowywać, a co wyliczać dla strefy użytkownika |
| `LocalScheduleResolver` | co zrobić z brakującą i podwójną godziną DST |
| `DailyBusinessSchedule` | dlaczego „codziennie o 09:00” nie znaczy „co 24 godziny” |
| `Deadline` | jak testować expiry i fail-closed safety margin przez `Clock` |
| `ClockSkewWindow` | kiedy kolejność względem granicy jest niepewna |
| `MonotonicTimer` | jak mierzyć czas trwania mimo korekty wall clock |
| `RecurringJobScheduler` | jak wyznaczać należne uruchomienia po restarcie |
| `InMemoryScheduleStore` | atomowy checkpoint i unikalny klucz wykonania |
| `FencedJobExecutionLedger` | deduplikacja, zakaz overlapu i odrzucenie starego właściciela |

## `Instant`, `LocalDateTime` i `ZonedDateTime`

| Typ | Znaczenie | Typowe użycie | Czego nie gwarantuje |
| --- | --- | --- | --- |
| `Instant` | jednoznaczny punkt na osi UTC | `createdAt`, expiry, event time, audit | lokalnej intencji użytkownika |
| `LocalDateTime` | data i godzina bez strefy i offsetu | „wizyta 10:00 czasu lokalnego” przed rozwiązaniem strefy | jednoznacznego momentu |
| `ZonedDateTime` | lokalna data, strefa i reguły offsetu | prezentacja i harmonogram kalendarzowy | stabilności przyszłych reguł politycznych |
| `Duration` | ilość czasu | timeout, TTL, interwał techniczny | daty kalendarzowej typu „następny miesiąc” |

Zdarzenie, które już zaszło, najczęściej zapisujemy jako `Instant` i prezentujemy
w aktualnej strefie użytkownika. Dla przyszłej intencji biznesowej, np. „każdego
dnia o 09:00 Europe/Warsaw”, sam `Instant` nie wystarcza. Trzeba zachować lokalną
regułę oraz identyfikator `ZoneId`, a kolejne wystąpienia wyliczać z reguł strefy.
Offset `+01:00` nie jest zamiennikiem `Europe/Warsaw`: offset nie zna DST.

W bazie PostgreSQL `timestamptz` reprezentuje moment, ale nie przechowuje
oryginalnej strefy użytkownika. Jeśli strefa jest częścią domeny, zapisujemy ją
osobno. UTC jest dobrym formatem wymiany momentów, nie odpowiedzią na każde
pytanie kalendarzowe.

## DST: gap i overlap

W `Europe/Warsaw` lokalny czas `2026-03-29 02:30` nie istnieje. Zegar przechodzi
z 02:00 na 03:00. `LocalScheduleResolver` wymaga jawnej decyzji:

- `REJECT` — użytkownik musi wybrać poprawny czas;
- `SHIFT_FORWARD` — intencja przesuwa się o długość przerwy, tutaj na 03:30.

Z kolei `2026-10-25 02:30` występuje dwa razy: najpierw z offsetem `+02:00`,
potem `+01:00`. Polityka `EARLIER_OFFSET` albo `LATER_OFFSET` wybiera konkretny
`Instant`. Ciche użycie domyślnej decyzji biblioteki może być poprawne
technicznie, ale nadal błędne biznesowo.

`DailyBusinessSchedule` zachowuje lokalną godzinę. Doba między dwoma
uruchomieniami o 09:00 może mieć 23 albo 25 godzin. `Duration.ofHours(24)` służy
do interwału technicznego, a nie do modelowania „następnego dnia o tej samej
godzinie”.

## Wall clock kontra czas monotoniczny

`Clock` i `Instant.now()` odpowiadają na pytanie „która jest godzina?”. Ten zegar
może zostać skorygowany przez NTP, administratora albo warstwę wirtualizacji.
Różnica dwóch odczytów wall clock może więc zafałszować latency.

Do pomiaru czasu trwania używamy monotonicznego źródła:

```java
MonotonicTimer timer = MonotonicTimer.start();
operation.run();
Duration elapsed = timer.elapsed();
```

`System.nanoTime()` nie jest timestampem i nie wolno go zapisywać jako czasu
zdarzenia. Jego początek jest arbitralny; znaczenie ma wyłącznie różnica odczytów
w obrębie procesu. Test używa kontrolowanego tickera i pokazuje, że cofnięcie
wall clock nie zmienia zmierzonego czasu trwania.

## Expiry, deadline i clock skew

`Deadline` jest absolutnym końcem budżetu. `remaining(clock)` nigdy nie zwraca
ujemnego czasu, a granica `now == expiresAt` jest już wygaśnięta. Wstrzyknięty
`Clock` pozwala przetestować dokładną granicę bez `sleep`.

Gdy token albo lease jest oceniany na innej maszynie niż ta, która go wystawiła,
zegary mogą się różnić. `ClockSkewWindow` rozdziela trzy stany:

```text
now + maxSkew < boundary       -> zdecydowanie przed
okno wokół boundary            -> wynik niepewny
now - maxSkew >= boundary       -> zdecydowanie po granicy
```

Nie każda domena obsługuje niepewność tak samo. Autoryzacja może wygaszać token
wcześniej przez safety margin (fail closed), cache może zaakceptować krótką
staleness, a pieniądze mogą wymagać czasu autorytatywnego z bazy lub koordynatora.
Dodanie tolerancji nie synchronizuje zegarów i nie tworzy porządku przyczynowego.

Deadline przekazywany między usługami powinien być zmniejszany o już zużyty
budżet. Sam timeout każdego hopu od nowa może sprawić, że cały request trwa kilka
razy dłużej niż obiecał klient.

## Scheduler odporny na restart

Proces nie jest źródłem prawdy o ostatnim uruchomieniu. `InMemoryScheduleStore`
modeluje trwały store współdzielony przez kolejne instancje schedulera:

```text
definicja zadania
  -> atomowo odczytaj checkpoint
  -> wyznacz due slots według misfire policy
  -> zapisz nowy checkpoint i unikalne execution keys
  -> wykonaj poza transakcją planowania
  -> zapisz wynik z fencing tokenem
```

Restartowany `RecurringJobScheduler` dostaje ten sam store i nie planuje
ponownie slotu, który został już atomowo przejęty. Produkcyjnie odpowiada temu
transakcja w bazie, unikalny constraint na `execution_key` i jawny stan
`PLANNED/RUNNING/COMPLETED/FAILED`.

### Misfire

Misfire oznacza, że zaplanowana chwila minęła, gdy scheduler nie działał.

| Polityka | Zachowanie po powrocie | Ryzyko |
| --- | --- | --- |
| `SKIP` | pomija zaległe sloty i przechodzi do przyszłego | utrata oczekiwanego efektu |
| `FIRE_ONCE` | scala zaległości do najnowszego slotu | pomija osobne historyczne okresy |
| `CATCH_UP_BOUNDED` | zwraca ograniczoną partię najstarszych slotów | backlog nadal wymaga kontroli obciążenia |

Polityka należy do domeny. Raport godzinowy może wymagać catch-up, odświeżenie
cache zwykle `FIRE_ONCE`, a cykliczne przypomnienie może pomijać nieaktualne
wykonania. Bez limitu catch-up powrót po długiej awarii może wywołać retry storm.

## Duplikaty i równoległe wykonanie

Nie należy utożsamiać pojedynczego procesu schedulera z exactly-once. Proces może
zakończyć się po efekcie, lecz przed zapisem `COMPLETED`; lease może wygasnąć w
trakcie pauzy GC; odpowiedź zależności może zaginąć. Dlatego:

- `executionKey = jobName + scheduledAt` identyfikuje logiczne wykonanie;
- trwały unique constraint odrzuca drugi claim tego samego slotu;
- efekt biznesowy nadal powinien być idempotentny;
- `FencedJobExecutionLedger` nie uruchamia dwóch slotów tego samego joba naraz;
- nowy właściciel z wyższym tokenem może przejąć pracę;
- zapis starego właściciela jest odrzucany przez chroniony zasób.

Zakaz overlapu nie zawsze jest wymagany. Zadania partycjonowane mogą wykonywać
różne partycje równolegle, ale klucz blokady i execution key muszą wtedy zawierać
identyfikator partycji.

## Lease i fencing

Test `ScheduledJobLeaseIntegrationTest` łączy scheduler z istniejącym
[laboratorium koordynacji](../../../stage_3/block_a/concepts/coordination/README.md):

1. worker A otrzymuje lease i token 1, po czym zatrzymuje się;
2. lease wygasa według zegara autorytatywnego;
3. worker B otrzymuje token 2, przejmuje wykonanie i zapisuje wynik;
4. worker A wraca i próbuje zapisać wynik z tokenem 1;
5. ledger odrzuca stary zapis.

Samo pytanie workera `lease.isExpiredAt(Instant.now())` nie wystarcza. Jego zegar
może być przesunięty, a proces może zostać zatrzymany już po sprawdzeniu. Fencing
działa tylko wtedy, gdy token jest egzekwowany przez bazę lub usługę przyjmującą
chroniony zapis.

## Testy

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=TimeRepresentationTest,DeadlineAndClockTest,RecurringJobSchedulerTest,ScheduledJobLeaseIntegrationTest" test
```

Testy nie używają `sleep`. Kontrolują `Clock`, ticker, checkpoint i moment
wygaśnięcia lease'a, dzięki czemu przypadki graniczne są deterministyczne.

## Granice laboratorium

- in-memory store modeluje atomową transakcję, ale nie trwałość procesu;
- scheduler używa fixed-rate `Duration`; regułę kalendarzową pokazuje osobno
  `DailyBusinessSchedule`;
- nie ma parsera cron, kalendarza świąt ani aktualizacji tzdb;
- ledger pokazuje protokół, lecz produkcyjny fencing wymaga warunkowego zapisu
  w rzeczywistym systemie danych;
- bounded catch-up ogranicza jedną partię, a produkcja potrzebuje również limitu
  globalnej przepustowości i obserwowalności backlogu.

Najważniejsza zasada: **najpierw nazwij znaczenie czasu, potem wybierz typ,
źródło zegara i gwarancję schedulera**.
