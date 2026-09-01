# transactional

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** transactional.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „transactional” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=AccountTest,PaymentServiceTest,TransactionBoundaryIntegrationTest" test`
> - **Role klas:** `CorrectSelfInvocationSolutionService` = `correct`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Transakcje i spójność danych w Spring



## Cel laboratorium

Transakcja nie jest dekoracją repozytorium ani sposobem na automatyczne ponowienie operacji. Jest granicą atomowej zmiany stanu w jednym zasobie transakcyjnym. W tym laboratorium zasobem jest relacyjna baza danych, a przypadkiem biznesowym — przelew między dwoma kontami.

Po przejściu materiału powinieneś umieć odpowiedzieć:

- jaki niezmiennik chroni dana transakcja,
- gdzie zaczyna się i kończy transakcja Springa,
- które anomalie dopuszcza wybrany poziom izolacji,
- kiedy wybrać blokadę optymistyczną, pesymistyczną albo atomowy `UPDATE`,
- które wyjątki powodują rollback,
- dlaczego `REQUIRES_NEW` tworzy inną granicę atomowości,
- dlaczego jedna `@Transactional` nie obejmuje bazy i brokera wiadomości.

## Mapa kodu

| Element | Co pokazuje |
|---|---|
| `Account` | niezmiennik salda i `@Version` do wykrywania utraconej aktualizacji |
| `PaymentService.transferOptimistically` | jedna transakcja biznesowa i optimistic locking |
| `PaymentService.transferPessimistically` | `SELECT ... FOR UPDATE` oraz stała kolejność blokowania rekordów |
| `PaymentService.transferThenFailAfterIndependentAudit` | rollback transakcji głównej przy niezależnym commicie audytu |
| `AuditService` | propagacja `REQUIRES_NEW` na wywołaniu przechodzącym przez inne proxy |
| `RollbackService` | domyślne reguły rollbacku i `rollbackFor` na rzeczywistym zapisie |
| `SelfInvocationService` | wywołanie `this.method()` omijające proxy Springa |
| `CorrectSelfInvocationSolutionService` | przeniesienie granicy transakcji do osobnego beana |
| `ReportService` | deklarowanie poziomu izolacji dla konkretnego use case'u |
| `OrderStepService` | semantyka `NESTED` i jej zależność od transaction managera |

Scenariusze wykonywane ręcznie w dwóch sesjach PostgreSQL znajdują się w [`../../block_d/sql/transaction/transaction.sql`](../../block_d/sql/transaction/transaction.sql). SQL lepiej niż test na H2 pokazuje snapshoty, phantom read, write skew i błędy serializacji.

## Najpierw niezmiennik, później adnotacja

Dla przelewu interesują nas co najmniej trzy reguły:

1. kwota jest dodatnia,
2. konto źródłowe nie może zejść poniżej zera,
3. suma środków po poprawnym przelewie pozostaje niezmieniona.

Pierwsze dwie reguły chroni model `Account`. Trzecia wymaga objęcia wypłaty i wpłaty jedną transakcją w `PaymentService`. Dwie osobne transakcje repozytorium nie wystarczą: awaria pomiędzy nimi pozostawiłaby system z obciążonym jednym kontem i nieuznanym drugim.

Granica transakcji powinna zwykle obejmować kompletny przypadek użycia. Kontroler jest za wysoko, bo łączy HTTP z trwałością danych. Pojedyncza metoda repozytorium jest za nisko, bo nie zna całej operacji biznesowej. Najczęściej właściwym miejscem jest publiczna metoda serwisu aplikacyjnego.

## ACID bez skrótów myślowych

- **Atomicity** — wszystkie zapisy transakcji zostaną zatwierdzone albo żaden.
- **Consistency** — po poprawnym commicie obowiązują constrainty bazy i niezmienniki faktycznie wymuszone przez aplikację. Baza nie odgadnie reguł biznesowych.
- **Isolation** — wynik współbieżnego wykonania zależy od poziomu izolacji i mechanizmu kontroli konfliktów.
- **Durability** — po potwierdzonym commicie baza odpowiada za trwałość zgodnie ze swoją konfiguracją; nie oznacza to automatycznie replikacji do innego systemu.

ACID dotyczy konkretnej bazy i konkretnej transakcji. Nie daje atomowości pomiędzy PostgreSQL, Redisem, brokerem i zewnętrznym API.

## Poziomy izolacji i anomalie

Nazwy poziomów pochodzą ze standardu SQL, ale szczegóły implementacji różnią się między bazami. Poniższa tabela opisuje praktyczny model PostgreSQL:

| Poziom | Snapshot | Możliwe problemy | Typowe zastosowanie |
|---|---|---|---|
| `READ COMMITTED` | nowy dla każdego statementu | non-repeatable read, phantom, decyzja na nieaktualnym odczycie | krótkie operacje OLTP bez wielokrotnego podejmowania decyzji na tych samych danych |
| `REPEATABLE READ` | jeden na transakcję | write skew; konflikt zapisu może przerwać transakcję | raport lub proces wymagający stabilnego obrazu danych |
| `SERIALIZABLE` | wykonanie równoważne kolejności sekwencyjnej | transakcja może zostać przerwana błędem serializacji | krytyczne niezmienniki obejmujące wiele rekordów |

PostgreSQL nie wykonuje dirty read nawet po zadeklarowaniu `READ UNCOMMITTED` — traktuje ten poziom jak `READ COMMITTED`. Nie należy przenosić tej właściwości automatycznie na każdy silnik SQL.

### Lost update

Typowy scenariusz wygląda tak:

1. dwa requesty czytają saldo `100`,
2. pierwszy wylicza `90`, drugi `80`,
3. oba zapisują wartość absolutną,
4. ostatni zapis nadpisuje wcześniejszy efekt.

Możliwe zabezpieczenia:

- atomowy warunkowy SQL, np. `UPDATE ... SET balance = balance - :amount WHERE balance >= :amount`,
- optimistic locking przez kolumnę wersji,
- pessimistic locking przez `SELECT ... FOR UPDATE`,
- silniejsza izolacja wraz z retry całej transakcji.

Nie ma jednej najlepszej techniki. Decydują częstotliwość konfliktów, koszt retry, długość transakcji i rodzaj niezmiennika.

### Write skew

`@Version` chroni konflikt zapisu tego samego rekordu. Nie ochroni reguły obejmującej kilka różnych rekordów. Dwie transakcje mogą przeczytać „na dyżurze jest dwóch lekarzy”, wyłączyć różne osoby i razem złamać regułę „co najmniej jeden lekarz pozostaje”. To write skew. Rozwiązaniem może być `SERIALIZABLE`, jawna blokada wspólnego rekordu reprezentującego invariant albo zmiana modelu danych pozwalająca wymusić regułę constraintem.

## Optimistic i pessimistic locking

`Account.version` jest oznaczone `@Version`. Hibernate umieszcza wersję w warunku `UPDATE`. Jeżeli od czasu odczytu ktoś zmienił rekord, aktualizacja nie obejmie żadnego wiersza i transakcja zakończy się konfliktem optymistycznym. Nie należy ukrywać konfliktu: API może zwrócić `409 Conflict`, a retry ma sens tylko wtedy, gdy można bezpiecznie ponownie wykonać cały przypadek użycia na świeżych danych.

`AccountRepository.findByIdForUpdate` korzysta z `PESSIMISTIC_WRITE`. Blokada jest utrzymywana do końca transakcji. `PaymentService` pobiera konta rosnąco po identyfikatorze, aby wszystkie przelewy stosowały tę samą kolejność blokad. Ogranicza to deadlocki, ale ich nie wyklucza — aplikacja nadal musi mieć timeout i obsługę przejściowego błędu bazy.

| Właściwość | Optimistic | Pessimistic |
|---|---|---|
| Koszt bez konfliktu | niski | lock i możliwe oczekiwanie |
| Zachowanie przy konflikcie | jedna transakcja przegrywa przy zapisie | druga czeka albo dostaje timeout |
| Najlepszy profil | konflikty rzadkie | konflikty częste, krótka sekcja krytyczna |
| Obowiązek aplikacji | obsługa konfliktu/retry | kolejność locków, timeout, deadlock retry |

Nie wykonuj wywołania HTTP ani długiego obliczenia, trzymając blokadę bazodanową. Wydłuża to lock wait, zużywa połączenie z puli i zwiększa ryzyko kaskadowego przeciążenia.

## Jak Spring otwiera transakcję

`@Transactional` jest najczęściej realizowane przez proxy. Transakcja rozpoczyna się, gdy klient wywołuje przechwytywalną metodę na proxy, a nie w chwili wejścia do dowolnej metody oznaczonej adnotacją.

Konsekwencje:

- `this.transactionalMethod()` omija proxy,
- obiekt utworzony przez `new` nie otrzymuje obsługi transakcji,
- prywatna metoda nie jest samodzielnym punktem wejścia proxy,
- nie należy polegać na metodach `final` przy proxy klasowym,
- wyjątek musi wydostać się przez interceptor, aby Spring zastosował regułę rollbacku.

W `PaymentService.transferThenFailAfterIndependentAudit` prywatna metoda pomocnicza działa wewnątrz transakcji już otwartej dla publicznego punktu wejścia. Nie próbuje deklarować nowej propagacji. Jeśli potrzebna jest inna propagacja, przenieś operację do osobnego beana, tak jak w `AuditService`.

## Propagacja

| Propagacja | Znaczenie | Istotna pułapka |
|---|---|---|
| `REQUIRED` | dołącza do istniejącej transakcji albo tworzy nową | wewnętrzna metoda może oznaczyć wspólną transakcję jako rollback-only |
| `REQUIRES_NEW` | zawiesza zewnętrzną i tworzy niezależną | potrzebuje drugiego połączenia; może zwiększyć presję na pool i contention |
| `NESTED` | savepoint wewnątrz tej samej fizycznej transakcji | zależy od transaction managera i wsparcia savepointów; nie jest „małym REQUIRES_NEW” |
| `MANDATORY` | wymaga aktywnej transakcji | dobre do wykrywania złej granicy wywołania |
| `NOT_SUPPORTED` | zawiesza aktywną transakcję | operacja nie korzysta z jej atomowości |

Domyślny `JpaTransactionManager` nie gwarantuje praktycznej obsługi `NESTED` dla operacji JPA. Przykład `OrderStepService` pokazuje deklarację i warunki użycia, a nie obietnicę działania z każdą konfiguracją. Savepointy najłatwiej demonstrować przez JDBC i odpowiedni transaction manager.

`REQUIRES_NEW` w audycie oznacza świadomą decyzję: wpis „próba przelewu” może istnieć, mimo że przelew został wycofany. Treść zdarzenia nie może więc fałszywie oznaczać sukcesu. Niezależna transakcja audytu może również czekać na locki trzymane przez transakcję zewnętrzną.

## Rollback i wyjątki

Domyślnie Spring wycofuje transakcję dla `RuntimeException` i `Error`, a nie dla checked exception. `RollbackService` zapisuje rekord przed rzuceniem wyjątku, dzięki czemu różnicę można zaobserwować:

- runtime exception — zapis znika,
- checked exception z `rollbackFor` — zapis znika,
- checked exception bez `rollbackFor` — zapis zostaje zatwierdzony.

Nie łap wyjątku tylko po to, aby go zalogować i zwrócić sukces. Jeśli wyjątek zostanie połknięty wewnątrz metody, interceptor może nie wiedzieć, że transakcja powinna zostać wycofana. Z kolei złapanie wyjątku z wewnętrznej operacji, która już oznaczyła wspólną transakcję jako rollback-only, może zakończyć się `UnexpectedRollbackException` przy commicie.

## Flush, commit i dirty checking

Zmiana zarządzanej encji nie wymaga `save` po każdym setterze. Hibernate wykrywa zmianę i wykonuje SQL podczas flush. Flush nie jest jednak commitem: wysłanie `UPDATE` do bazy nie oznacza jeszcze trwałego zatwierdzenia. Błąd constraintu, konflikt wersji albo problem z commitem nadal może wycofać całą transakcję.

`readOnly = true` jest wskazówką optymalizacyjną dla frameworka i sterownika. Nie jest mechanizmem bezpieczeństwa ani niezawodną blokadą zapisu we wszystkich konfiguracjach.

## Gdzie kończy się lokalna transakcja

Nie można bezpiecznie wykonać sekwencji „commit zamówienia, potem publish do brokera” bez rozważenia awarii pomiędzy krokami. Nie można też publikować przed commitem — konsument może zobaczyć zdarzenie dla stanu, który zostanie wycofany.

Granica wygląda następująco:

```text
lokalny przypadek użycia
  └─ jedna transakcja DB: agregat + rekord outbox
       └─ po commicie relay publikuje co najmniej raz
            └─ idempotentny konsument wykonuje własną transakcję lokalną
                 └─ saga koordynuje kolejne kroki i kompensacje
```

Implementacje znajdują się w Stage 2:

- `stage_2/block_a/integration/shared/outbox` — atomowy zapis zdarzenia z agregatem,
- `stage_2/block_a/integration/sales/saga` — proces z lokalnymi transakcjami i kompensacją,
- `stage_2/block_b/consumer` — idempotentne przetwarzanie komunikatów.

Outbox daje atomowość zapisu biznesowego i zamiaru publikacji, ale relay może wysłać komunikat wielokrotnie. „Exactly once” na poziomie brokera nie oznacza dokładnie jednego efektu biznesowego w zewnętrznej bazie. W praktyce dąży się do effectively-once: at-least-once delivery, stabilny identyfikator operacji, unikalny constraint i atomowy zapis efektu wraz z informacją o przetworzonym komunikacie.

Saga nie cofa historii. Kompensacja jest nową operacją biznesową, np. zwrotem płatności, i sama może wymagać retry, idempotencji oraz obsługi ręcznej.

## Jak testować transakcje

Test oznaczony `@Transactional`, który po zakończeniu sam się wycofuje, jest wygodny, ale może ukryć prawdziwy commit, constrainty wykonywane przy flush oraz zachowanie `REQUIRES_NEW`. Dla granic transakcji potrzebne są testy wywołujące serwis spoza transakcji testowej i odczytujące stan po zakończeniu wywołania.

W tym laboratorium testy powinny sprawdzać obserwowalny stan, nie samą obecność adnotacji:

- oba salda zmieniają się razem,
- błąd domenowy nie pozostawia częściowego zapisu,
- konflikt `@Version` odrzuca nieaktualną encję,
- rollback runtime i checked exception różnią się zgodnie z kontraktem,
- `REQUIRES_NEW` zachowuje wpis audytu po rollbacku zewnętrznym,
- pesymistyczny wariant pobiera blokady w deterministycznej kolejności.

H2 nadaje się do szybkich testów mapowania i granic Springa, lecz nie jest dowodem zachowania PostgreSQL pod współbieżnością. Anomalie izolacji, lock timeouty, deadlocki i `SERIALIZABLE` należy dodatkowo testować na docelowym silniku, najlepiej w Testcontainers.

## Pytania kontrolne

1. Jaki invariant chroni transakcja i czy obejmuje jeden, czy wiele rekordów?
2. Czy konflikt ma być wykryty przy zapisie, czy drugi request ma czekać?
3. Czy retry ponawia cały przypadek użycia od świeżego odczytu?
4. Czy zewnętrzne I/O odbywa się poza transakcją i blokadami?
5. Czy checked exception ma zatwierdzić, czy wycofać zmiany?
6. Czy audyt opisuje próbę, czy zatwierdzony fakt?
7. Co się stanie między commitem bazy a publikacją zdarzenia?
8. Czy consumer chroni efekt biznesowy unikalnym constraintem?
