# cdc reconciliation

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** cdc reconciliation.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „cdc reconciliation” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=OnlineProjectionBackfillTest,PoisonRecordPartitionTest,ReconciliationAndRepairTest" test`
> - **Role klas:** `InMemoryCdcSource` = `simulation`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## CDC, rekoncyliacja i naprawa danych



To laboratorium domyka drogę od autorytatywnego zapisu do materialized view:

```text
baza źródłowa
  -> spójny snapshot + high watermark
  -> strumień zmian za watermarkiem
  -> idempotentna, wersjonowana projekcja
  -> okresowe wykrywanie driftu
  -> repair albo budowa nowej generacji
```

Najważniejszy niezmiennik brzmi: **read model można odtworzyć i naprawić bez
ponownego wykonywania efektów biznesowych**.

## Mapa kodu

| Element | Odpowiedzialność |
| --- | --- |
| `InMemoryCdcSource` | autorytatywne rekordy i monotoniczna pozycja logu odpowiadająca WAL/LSN |
| `CdcSnapshot` | spójny obraz danych z high watermarkem |
| `CdcRecord` | before/after image, operacja, partycja, pozycja i wersja źródłowa |
| `OrderProjectionStore` | deduplikacja, tombstone version, ochrona przed reorderem i wykrycie luki |
| `PartitionCdcProcessor` | zatrzymanie partycji na poison recordzie i jawna kwarantanna |
| `ProjectionReconciler` | wykrycie missing/mismatch/orphan i naprawa projekcji |
| `HistoricalProjectionReplay` | replay w trybie `REBUILD`, bez efektów biznesowych |
| `OnlineProjectionBackfill` | snapshot, catch-up, weryfikacja i atomowa zmiana aliasu |
| `ProjectionRouter` | aktywna generacja read modelu widoczna dla zapytań |

## CDC nie jest tym samym co zdarzenie domenowe

CDC odczytuje fakty o zmianach w bazie: insert, update i delete. Zdarzenie
domenowe opisuje znaczenie biznesowe, np. `OrderPaid`. Zmiana kolumny `status`
z `NEW` na `PAID` może dziś odpowiadać temu zdarzeniu, ale taki kontrakt jest
związany ze schematem tabeli i sposobem zapisu aplikacji.

Dlatego najbezpieczniejsze zastosowanie Debezium w integracji biznesowej to
często **CDC tabeli outbox**, a nie publikowanie każdej zmiany tabel domenowych.
Outbox zawiera stabilny typ zdarzenia, event id, aggregate id, wersję schematu i
payload. Debezium odpowiada wtedy za niezawodne wyprowadzenie już podjętej
decyzji domenowej z transakcji bazy.

Raw-table CDC jest przydatne do:

- materialized views i search indexów,
- cache invalidation,
- data lake i audytu zmian,
- migracji danych i synchronizacji systemów legacy.

Nie powinno bezpośrednio uruchamiać płatności, wysyłki maila ani innego efektu,
jeśli semantyka technicznej zmiany nie jest stabilnym kontraktem biznesowym.

## Manual relay kontra Debezium

| Cecha | Relay odczytujący outbox | Debezium / log-based CDC |
| --- | --- | --- |
| odczyt | polling i claim rekordów | WAL/binlog przez connector |
| wpływ na bazę | zapytania, indeks i aktualizacja `published_at` | replication slot i retencja logu |
| opóźnienie | zależy od interwału pollingu | zwykle niskie i ciągłe |
| backpressure | limit batcha i częstotliwość | connector, broker i kontrola lagu |
| awaria po publikacji | możliwy duplikat przed oznaczeniem rekordu | możliwy redelivery przed zapisem offsetu |
| operacyjność | kod workera w aplikacji | osobna platforma connectorów, offsety i schema history |
| sprzątanie | aplikacja kontroluje retencję outboxa | osobny cleanup; CDC musi zdążyć odczytać rekord |

Relay jest rozsądny przy małej skali i prostym środowisku. Debezium usuwa
polling z hot path bazy i dobrze skaluje strumień, ale wymaga monitorowania
connectora, replication slotu, retencji WAL, uprawnień i kompatybilności
schematu. W obu rozwiązaniach konsumenci pozostają idempotentni.

`published_at` w manualnym relayu nie tworzy exactly-once. W CDC offset
connectora również nie obejmuje efektu konsumenta. Zmienia się transport, nie
fundamentalna granica transakcji.

## Snapshot i przejście do streamu

Poprawny bootstrap nie wykonuje „SELECT wszystkiego, a potem włącza CDC bez
punktu wspólnego”. Między tymi krokami mogłyby zniknąć zmiany.

Model laboratorium:

1. źródło atomowo otwiera spójny snapshot i zapisuje `highWatermark`;
2. rekordy snapshotu są emitowane jako operacje `READ`;
3. w czasie snapshotu nowe transakcje nadal trafiają do logu;
4. po snapshotcie konsument czyta rekordy o pozycji większej od watermarku;
5. dopiero po dogonieniu logu projekcja jest gotowa do użycia.

W PostgreSQL Debezium używa spójnego snapshotu oraz pozycji WAL/LSN. Connector
musi zachować offset i schema history. Utrata replication slotu albo usunięcie
potrzebnego WAL może wymusić nowy snapshot, a więc jest zdarzeniem operacyjnym,
nie niewinnym restartem.

## Duplikaty, kolejność i wersja źródłowa

`eventId` usuwa identyczne redelivery. To nie wystarcza, gdy dwa różne eventy
dla tego samego klucza docierają w złej kolejności. Projekcja przechowuje
`highestVersionByKey`:

- ta sama wersja lub starsza → `STALE`, bez nadpisania danych;
- kolejna wersja → `APPLIED`;
- wyższa wersja z luką → pełny after-image jest zastosowany jako
  `GAP_APPLIED`, a luka staje się sygnałem do rekoncyliacji;
- snapshot może zacząć od wersji większej niż 1, ponieważ jest pełnym obrazem.

Porządek Kafki istnieje tylko w partycji. Klucz partycji powinien odpowiadać
kluczowi encji. Podczas replayu wielu partycji nie wolno polegać na globalnej
kolejności timestampów. Dla update/delete potrzebny jest tombstone version;
usunięcie wiersza bez zapamiętania wersji pozwoliłoby staremu update'owi wskrzesić
rekord.

Pełny after-image upraszcza odbudowę projekcji. Delta typu `balance += 10` jest
znacznie bardziej wrażliwa na duplikaty, brak eventu i reorder.

## Poison record i zatrzymana partycja

Błąd deserializacji lub sprzeczny envelope nie jest błędem przejściowym.
Natychmiastowy retry tego samego rekordu zatrzyma partycję i wygeneruje szum.
Automatyczne pominięcie pozwoli jej ruszyć, ale może trwale uszkodzić projekcję.

`PartitionCdcProcessor` domyślnie:

1. zatrzymuje commit przed poison recordem;
2. zwraca rekord i przyczynę;
3. czeka na decyzję operatora;
4. po jawnej kwarantannie zapisuje powód i kontynuuje;
5. pozostawia rekoncyliatorowi wykrycie ewentualnego braku.

Kwarantanna musi zachować surowe bajty, topic/partition/offset, schema id,
connector position, headers i fingerprint błędu. „Skip” bez audytu i alarmu jest
utratą danych przedstawioną jako sukces.

## Rekoncyliacja jako anti-entropy

Zielony consumer lag nie dowodzi zgodności danych. Błąd mógł powstać wcześniej,
poison record mógł zostać pominięty, operator mógł ręcznie zmienić read model,
a stary bug mógł zapisywać błędną wartość.

`ProjectionReconciler` wykrywa:

- `MISSING` — rekord istnieje w źródle, lecz nie w projekcji;
- `VALUE_MISMATCH` — klucz istnieje po obu stronach, ale pełna wartość lub wersja
  się różni;
- `ORPHAN` — projekcja zawiera rekord nieobecny w źródle.

Produkcja zwykle zaczyna od porównania count/checksum per shard i time bucket,
a dopiero dla różniącego się zakresu porównuje klucze oraz canonical checksum
rekordu. Pełny scan całej bazy w godzinach szczytu może sam wywołać incydent.

Rekoncyliację uruchamia się okresowo, po deploymencie zmieniającym projekcję,
po replayu, po obsłudze DLQ oraz przed przełączeniem generacji. Wynik powinien
mieć metryki: liczba różnic według typu, najstarszy drift, tempo naprawy i zakres.

## Naprawa bez ponownego efektu biznesowego

`ProjectionPipeline` wymaga celu przetwarzania:

- `LIVE` może powiadomić jawnie skonfigurowany efekt;
- `REBUILD` aktualizuje wyłącznie nową projekcję;
- `REPAIR` służy do administracyjnej korekty read modelu.

`ProjectionReconciler` nie posiada nawet portu do płatności, maila czy publikacji
zdarzenia domenowego. Naprawa `order-1.status=PAID` nie może ponownie obciążyć
karty. Jeśli potrzebna jest kompensacja biznesowa, powinna być osobną,
audytowalną komendą zatwierdzoną przez właściwy proces, nie efektem ubocznym
technicznego replayu.

## Replay historii

`HistoricalProjectionReplay` przyjmuje historię w trybie `REBUILD`. Duplikat i
stara wersja są ignorowane, a luka jest liczona. Bezpieczny runbook replayu:

1. utwórz nową, pustą generację projekcji;
2. przypnij wersję kodu i schematów zdolną odczytać całą historię;
3. wyłącz wszystkie efekty biznesowe;
4. odtwórz historię z kontrolowanym limitem throughput;
5. wykonaj catch-up live tail;
6. uruchom rekoncyliację;
7. przełącz alias dopiero po spełnieniu kryteriów;
8. zachowaj starą generację na rollback przez określony czas.

Replay in-place jest trudniejszy do zatrzymania i rollbacku. Nowa generacja daje
czytelną granicę, metryki postępu i możliwość porównania obu wyników.

## Backfill równolegle z ruchem produkcyjnym

`OnlineProjectionBackfill` pokazuje blue/green read model:

```text
active-v1 obsługuje query i live changes
             |
snapshot @ watermark W -> candidate-v2
             |
zmiany W+1...N trafiają do v1 i są doganiane przez v2
             |
reconciliation(source, candidate-v2)
             |
atomic alias switch v1 -> v2
```

Jeśli watermark zmieni się podczas bramki weryfikacyjnej albo zostanie wykryty
drift, przełączenie nie następuje. W realnym systemie candidate po cutoverze musi
kontynuować od dokładnie zapisanego offsetu; potrzebny jest kontrolowany handoff
partycji lub okres dual-write/dual-consume. Backfill musi mieć rate limit,
checkpoint, możliwość wznowienia i osobne SLO, aby nie zagłodził ruchu online.

## Obserwowalność i runbook

Monitoruj co najmniej:

- connector state i liczbę restartów,
- source LSN kontra committed connector offset,
- consumer lag i wiek najstarszej zmiany,
- rozmiar replication slotu oraz tempo wzrostu WAL,
- postęp snapshotu i backfillu,
- poison/quarantine count per schema i partycja,
- liczbę luk wersji,
- drift według typu oraz czas do naprawy,
- stan i wiek starej oraz nowej generacji projekcji.

Runbook powinien rozróżniać: restart connectora z zachowanym offsetem, utratę
slotu, niekompatybilny schemat, poison record, rosnący lag, drift projekcji i
nieudany cutover. Każdy z tych przypadków wymaga innej decyzji.

## Testy

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=SnapshotToStreamTest,PoisonRecordPartitionTest,ReconciliationAndRepairTest,OnlineProjectionBackfillTest" test
```

## Granice laboratorium

- `InMemoryCdcSource` modeluje WAL/LSN, ale nie uruchamia prawdziwego Debezium;
- pozycja źródła jest globalna, podczas gdy broker ma osobne offsety partycji;
- snapshot mieści się w pamięci i nie modeluje chunkingu ani blokad bazy;
- cutover aliasu jest lokalny; produkcja musi skoordynować offset aktywnego
  consumera i routing zapytań;
- canonical checksum i porównanie shardów opisano, lecz mały model porównuje
  pełne rekordy;
- kwarantanna jest jawna, ale nie ma osobnego trwałego topicu ani panelu operatora.

Powiązane materiały:

- [Kafka i commit offsetów](../kafka/README.md),
- [idempotentny consumer, retry i DLQ](../consumer/README.md),
- [wersjonowanie zdarzeń](../versioning/README.md),
- [Outbox i granice use case'u](../../block_a/use_case/README.md),
- [wykonywalne laboratoria PostgreSQL](../../../stage_1/block_d/sql/README.md),
- [observability pipeline](../../../stage_3/block_b/README.md).
