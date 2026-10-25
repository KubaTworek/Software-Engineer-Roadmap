# Transakcje i spójność rozproszona

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#microservices)

Wzorce procesów, logów i kontroli własności. **15 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [CRDT i fencing](../../labs/06-distributed-data.md). Studium w Markdown
rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** W jednym store transakcja może zatwierdzić powiązane zmiany razem. Po
przekroczeniu granicy bazy, procesu lub usługi pojawiają się niezależne momenty sukcesu i nieznany wynik po
timeout. Te wzorce wybierają, co utrwalić, jak ponawiać, kiedy kompensować i kto ma prawo zmieniać stan. Nie
wszystkie dają tę samą gwarancję: saga, 2PC, quorum i CRDT rozwiązują różne problemy przy różnych założeniach.

## Spis kart

-
  [Transactional Outbox / Polling Publisher / Transaction Log Tailing](#transactional-outbox--polling-publisher--transaction-log-tailing)
- [Idempotency Key / Request Deduplication](#idempotency-key--request-deduplication)
- [Saga — Orchestration / Compensating Transaction](#saga--orchestration--compensating-transaction)
- [Saga — Choreography](#saga--choreography)
- [CQRS / Command–Query Separation](#cqrs--commandquery-separation)
- [Event Sourcing / Snapshotting / Upcasting](#event-sourcing--snapshotting--upcasting)
- [Change Data Capture / CDC](#change-data-capture--cdc)
- [Reconciliation / Repair Loop / Anti-entropy](#reconciliation--repair-loop--anti-entropy)
- [Try–Confirm–Cancel / Reservation](#tryconfirmcancel--reservation)
- [Two-phase Commit / 2PC](#two-phase-commit--2pc)
- [Lease / Fencing Token](#lease--fencing-token)
- [Leader Election / Single-writer](#leader-election--single-writer)
- [Quorum / Consensus](#quorum--consensus)
- [CRDT / Conflict-free Merge](#crdt--conflict-free-merge)
- [Read Your Writes / Session Consistency](#read-your-writes--session-consistency)

## Transactional Outbox / Polling Publisher / Transaction Log Tailing

**Intuicja:** Commit zamówienia i wysłanie eventu to dwie operacje, więc proces może paść między nimi. Outbox
zapisuje stan oraz zamiar wysyłki w jednej lokalnej transakcji. Osobny relay dostarcza zapisany zamiar
później; awaria relay nie usuwa faktu, że publikacja nadal jest potrzebna.

**Problem i mechanizm:** Zmiana biznesowa i zamiar publikacji eventu są zapisywane atomowo w jednej bazie;
osobny relay dostarcza event.

**Role i współpraca:** Tabela outbox jest częścią tej samej DB co zmiana, relay publikuje, a konsument
deduplikuje powtórzenia. Polling i log tailing są różnymi sposobami odkrywania zapisanych eventów.

**Przykład:** place(o1) publikuje snapshot z order i pending. Relay wysyła event, po czym usuwa go z pending;
awaria między krokami daje duplikat.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
transaction.run(() -> {
    orders.insert(order);
    outbox.insert(eventId, new OrderPlaced(order.id()));
});
// Osobny relay: publish -> potwierdzenie -> oznaczenie wysłania.
```

**Konkretny przypadek użycia:** Zamówienie i fakt do publikacji

- **Wejście:** Order42 i event E42 mają powstać razem.
- **Przebieg:** Lokalny commit zapisuje oba, relay publikuje i dopiero potem oznacza wysłanie.
- **Oczekiwany wynik:** Po awarii relay zamiar nadal istnieje.
- **Błąd lub granica:** Crash po publish daje duplikat, który odbiorca musi bezpiecznie obsłużyć.

**Kiedy zastosować:** Gdy lokalny commit musi spowodować niezawodną publikację bez wspólnej transakcji z
brokerem. Nie obiecuj jednego dostarczenia; utrata potwierdzenia może dać duplikat.

**Koszt i pułapka:** Polling i CDC to warianty relay, nie gwarancja exactly-once. Model w pamięci nie zapewnia
trwałości po restarcie JVM.

**Rozwinięcie i ślad wykonania:** Przed lokalnym commitem tworzymy zarówno nowy stan, jak i event pending. Po
publikacji, ale przed oznaczeniem wysłania, relay może się zatrzymać, więc kolejna próba dostarczy duplikat.
Test łączy tę historię z Inbox i pokazuje jeden efekt mimo dwóch dostarczeń, bez deklaracji exactly-once
transportu.

**Ćwiczenie sprawdzające:** Uruchom crash po publish przed oznaczeniem wysłania i sprawdź deduplikację
odbiorcy.

## Idempotency Key / Request Deduplication

**Intuicja:** Klient po timeout nie wie, czy utworzył zamówienie. Ponowienie bez identyfikacji może utworzyć
drugie. Idempotency Key nazywa jedną intencję klienta, a serwer wiąże klucz z danymi i wynikiem. Dzięki temu
klient pyta ponownie o tę samą operację, zamiast bezwiednie rozpoczynać nową.

**Problem i mechanizm:** Żądanie ma klucz o określonym zakresie, a system wiąże go z odciskiem danych i
zapamiętanym wynikiem.

**Role i współpraca:** Scope określa tenant i operację, odcisk payloadu wykrywa konflikt, trwały wpis
rozstrzyga in-progress i completed. Sam losowy nagłówek bez takiego store niczego nie deduplikuje.

**Przykład:** tenant1:k1 plus 100PLN zwraca tę samą odpowiedź przy ponowieniu; ten sam klucz dla 200PLN jest
odrzucany.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Key scoped = new Key(tenantId, "create-order", clientKey);
return idempotency.execute(scoped, hashOf(payload), () -> {
    Order order = orders.create(payload);
    return new Accepted(order.id());
});
```

**Konkretny przypadek użycia:** Ponowiony checkout

- **Wejście:** Tenant A dwa razy wysyła key k1 z payloadem 100 PLN po timeout.
- **Przebieg:** Rejestr wiąże scoped key z odciskiem i wynikiem.
- **Oczekiwany wynik:** Powrót do tej samej operacji nie tworzy drugiego zamówienia.
- **Błąd lub granica:** Ten sam klucz z 200 PLN jest konfliktem, a wygaśnięcie wpisu ma jawną semantykę.

**Kiedy zastosować:** Dla ponawialnych zapisów przez niepewną sieć. Ustal czas ważności i zachowanie
równoczesnych prób, nie tylko powtórzeń po zakończeniu.

**Koszt i pułapka:** Potrzebna jest atomowa rezerwacja klucza, lifecycle IN_PROGRESS i polityka TTL; mapa w
demo jest jednowątkowa.

**Rozwinięcie i ślad wykonania:** Klucz należy do zakresu tenanta i operacji, a nie całego świata.
LongRunningOperations zwraca ten sam uchwyt zadania po retry identycznego payloadu. Inny payload pod tym samym
kluczem jest konfliktem. W trwałym rozwiązaniu potrzebne są jeszcze atomowa rezerwacja, stan in-progress oraz
polityka przechowywania wyniku.

**Ćwiczenie sprawdzające:** Sprawdź równoczesne żądania oraz konflikt payloadu; ustal zachowanie po
wygaśnięciu klucza.

## Saga — Orchestration / Compensating Transaction

**Intuicja:** Zapas, płatność i wysyłka mogą mieć osobne transakcje. Saga nie udaje jednej wielkiej
transakcji: zapisuje kolejne lokalne sukcesy, a przy niepowodzeniu uruchamia działania biznesowo odwracające
ich skutki. Orkiestracja skupia plan i postęp w jednym koordynatorze.

**Problem i mechanizm:** Koordynator wykonuje lokalne transakcje i uruchamia semantyczne kompensacje
zakończonych kroków po błędzie.

**Role i współpraca:** Koordynator wydaje polecenia, uczestnicy wykonują lokalne kroki, log procesu pamięta
sukcesy. Kompensacja, np. refund, jest nową operacją i także może wymagać retry.

**Przykład:** reserve → charge → ship; błąd ship uruchamia refund → release w odwrotnej kolejności.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
reserve(orderId) -> zapisz RESERVED
charge(paymentKey) -> zapisz PAID
ship(orderId) -> przy błędzie sprawdź, czy efekt rzeczywiście nie zaszedł
jeśli proces ma się wycofać:
    refund(paymentKey) -> release(reservationId)
```

**Konkretny przypadek użycia:** Saga zakupowa

- **Wejście:** Reserve i charge kończą się sukcesem, ship zawodzi.
- **Przebieg:** Koordynator zapisuje postęp i inicjuje refund, potem release.
- **Oczekiwany wynik:** Próba przywrócenia reguł biznesowych obejmuje tylko zakończone kroki.
- **Błąd lub granica:** Refund może zawieść; proces musi zachować błąd i plan dalszej naprawy.

**Kiedy zastosować:** Gdy proces przekracza niezależne granice danych i dopuszcza przejściowe stany. Dla
nieodwracalnego kroku potrzebny jest plan naprawy, nie fikcyjne undo.

**Koszt i pułapka:** Kompensacja nie jest rollbackiem ACID i sama może zawieść. Nieznany wynik timeout wymaga
odczytu statusu lub idempotencji.

**Rozwinięcie i ślad wykonania:** Koordynator zapisuje, które kroki faktycznie zakończyły się sukcesem, i
kompensuje je od końca. Test z błędem refund sprawdza, że release nadal się wykona. Lokalna lista wykonanych
kroków nie przetrwa restartu; realny proces wymaga utrwalenia postępu i kolejnych prób kompensacji.

**Ćwiczenie sprawdzające:** Test lokalny pokazuje kolejność i błędy suppressed; zaprojektuj trwały retry
kompensacji.

## Saga — Choreography

**Intuicja:** Ten sam wieloetapowy proces można rozłożyć na reakcje uczestników. W choreografii magazyn
reaguje na OrderPlaced, płatności na StockReserved, a inni na wynik płatności. Nie ma jednego sterownika
wszystkich kroków, lecz nadal istnieje wspólna historia biznesowa, której zakończenie i awarie wymagają
odpowiedzialności.

**Problem i mechanizm:** Uczestnicy reagują na fakty i emitują kolejne fakty bez jednego centralnego
koordynatora.

**Role i współpraca:** Uczestnicy znają wejściowe fakty i własne wyjściowe kontrakty. CorrelationId wiąże
proces, a właściciel czasu i naprawy wykrywa brak reakcji.

**Przykład:** OrderPlaced wywołuje StockReserved, następnie PaymentCaptured; PaymentFailed powoduje
ReleaseStock.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
void on(OrderPlaced event) {
    inventory.reserveOnce(event.orderId());
    outbox.append(new StockReserved(event.orderId()));
}
void on(StockReserved event) {
    payments.captureOnce(event.orderId());
}
```

**Konkretny przypadek użycia:** Choreografia realizacji

- **Wejście:** OrderPlaced prowadzi do StockReserved i potem PaymentCaptured.
- **Przebieg:** Każda domena reaguje na fakt, utrwala swoją decyzję i emituje kolejny wynik.
- **Oczekiwany wynik:** Nie ma jednego wykonawcy wszystkich kroków.
- **Błąd lub granica:** Ktoś nadal musi wykryć brak eventu i wygaszenie procesu, inaczej rezerwacja zostanie
  na zawsze.

**Kiedy zastosować:** Gdy przepływ ma niewiele czytelnych reakcji i małe sprzężenie. Przy wielu timerach i
rozgałęzieniach ukryta orkiestracja w subskrypcjach staje się trudna do diagnozowania.

**Koszt i pułapka:** Przepływ ukryty w wielu subskrypcjach utrudnia diagnozę, timeout całego procesu i
dodawanie kroków.

**Rozwinięcie i ślad wykonania:** Każdy uczestnik reaguje na konkretny fakt i produkuje własny. Wskaż jednak
właściciela timeout całego procesu, bo bez niego brak wiadomości może pozostawić rezerwację na zawsze. Diagram
z samymi strzałkami eventów powinien zostać uzupełniony tabelą stanów i warunków naprawy.

**Ćwiczenie sprawdzające:** Narysuj stan procesu, korelację i właściciela wykrycia brakującego eventu.

## CQRS / Command–Query Separation

**Intuicja:** Model wygodny do pilnowania reguł zapisu nie zawsze jest dobry do list i raportów. CQRS
rozdziela odpowiedzialność za komendy i zapytania, aby oba modele odpowiadały własnym potrzebom. Nie wymaga
ani mikroserwisów, ani historii eventów jako źródła prawdy.

**Problem i mechanizm:** Model zapisu i model odczytu rozdziela się według ich odmiennych potrzeb; wdrożenie
nie wymaga osobnych baz.

**Role i współpraca:** Command handler podejmuje decyzję i zapisuje, query handler zwraca model odczytu.
Synchronizacja może być lokalna i natychmiastowa albo asynchroniczna z lagiem.

**Przykład:** Account przyjmuje deposit, BalanceProjection odpowiada na saldo i może być chwilowo opóźniona.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
commands.handle(new ConfirmOrder(orderId, expectedVersion));
OrderSummary summary = queries.findSummary(orderId);
// Odczyt ma własny kontrakt aktualności, niezależny od sukcesu komendy.
```

**Konkretny przypadek użycia:** Inny model listy niż zapisu

- **Wejście:** Komenda zatwierdza Order, lista pokazuje OrderSummary.
- **Przebieg:** Model zapisu chroni niezmienniki, projekcja obsługuje odczyt.
- **Oczekiwany wynik:** Odczyt ma prosty kształt i opisany lag.
- **Błąd lub granica:** CQRS nie wymaga Event Sourcing; opóźniona lista nie może udawać silnie aktualnej.

**Kiedy zastosować:** Gdy potrzeby odczytu i zapisu naprawdę się różnią. Dla prostego CRUD jeden model bywa
wystarczający i tańszy w utrzymaniu.

**Koszt i pułapka:** CQRS nie wymaga Event Sourcing. Koszt synchronizacji rośnie przy asynchronicznym osobnym
read store.

**Rozwinięcie i ślad wykonania:** Zapis i odczyt mają odrębne modele, ale mogą działać w jednej bazie i jednym
procesie. W przykładzie saldo projekcji pozostaje 0 do apply mimo istniejących wpłat. Kontrakt odczytu musi
określać dopuszczalny lag albo mechanizm czekania na minimalną wersję po własnej komendzie.

**Ćwiczenie sprawdzające:** Zapisz komendę i odczytaj projekcję przed oraz po apply.

## Event Sourcing / Snapshotting / Upcasting

**Intuicja:** Zamiast przechowywać tylko aktualne saldo, można zachować historię faktów, z których saldo
wynika. Event Sourcing uznaje taką historię za źródło prawdy. Stan bieżący odtwarza się przez kolejne apply;
snapshot przyspiesza start, ale nie zmienia znaczenia historii.

**Problem i mechanizm:** Źródłem prawdy jest uporządkowana historia zdarzeń; stan odtwarza się przez fold,
opcjonalnie od snapshotu.

**Role i współpraca:** Komenda jest sprawdzana na stanie, nowe eventy dopisywane z kontrolą wersji, fold
odtwarza model. Upcasting tłumaczy historyczny kontrakt do wersji rozumianej przez dzisiejszy kod.

**Przykład:** Deposited(1,100), Deposited(2,200) odtwarzają saldo 300; kod wykrywa lukę i ignoruje powtórzony
prefiks tej samej historii.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Account account = fold(eventStore.load(accountId));
List<Event> newEvents = account.decide(command);
eventStore.append(accountId, expectedVersion, newEvents);
// expectedVersion chroni przed dopisaniem decyzji na starej historii.
```

**Konkretny przypadek użycia:** Historia salda

- **Wejście:** Log konta zawiera Deposited(1,100) i Deposited(2,200).
- **Przebieg:** Fold odtwarza stan, a nowy zapis kontroluje oczekiwaną wersję historii.
- **Oczekiwany wynik:** Pusta projekcja odtwarza 300 i nie podwaja tego samego prefiksu.
- **Błąd lub granica:** Luka albo nieznana historyczna wersja wymaga jawnej obsługi, nie zgadywania stanu.

**Kiedy zastosować:** Gdy odtwarzanie historii i ewolucja modeli są warte kosztu utrzymywania starych faktów.
Sam dodatkowy log audytu nie czyni aktualnej bazy Event Sourcingiem.

**Koszt i pułapka:** To nie zwykłe logowanie audytu. Wersje zdarzeń, współbieżny append i usuwanie PII
wymagają projektu; demo obejmuje jedno konto.

**Rozwinięcie i ślad wykonania:** Historia Deposited jest źródłem prawdy, a saldo wynikiem fold. Replay tego
samego prefiksu nie zwiększa salda dwukrotnie; luka jest błędem, nie zaproszeniem do zgadywania brakującego
eventu. Snapshot skraca odtwarzanie, lecz jego wersja musi wskazywać dokładne miejsce dalszego czytania logu.

**Ćwiczenie sprawdzające:** Odbuduj pustą projekcję i zaprojektuj upcast historycznej wersji zdarzenia.

## Change Data Capture / CDC

**Intuicja:** Jeżeli zmiana już trafia do logu bazy, można obserwować ją po commicie zamiast wykonywać drugi
zapis z aplikacji. CDC przenosi te obserwacje do dalszych odbiorców. Wymaga zachowania postępu oraz
skoordynowania początkowego snapshotu z późniejszym strumieniem zmian.

**Problem i mechanizm:** Zmiany odczytuje się z logu bazy albo innego kontrolowanego feedu zamiast wykonywać
nieatomowy dual write.

**Role i współpraca:** Connector odczytuje log, offset identyfikuje pozycję, odbiorca aktualizuje projekcję.
Obserwacja outboxu daje kontrakt domenowy, dowolnej tabeli kontrakt technicznego schematu.

**Przykład:** Commit zamówienia trafia z WAL przez connector do projekcji; dla kontraktu biznesowego lepiej
obserwować outbox niż dowolną tabelę.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
DB commit -> log transakcyjny -> connector -> transport -> projekcja

start connectora:
    uzgodnij snapshot i pozycję logu
    utrwalaj offset dopiero zgodnie z kontraktem przetwarzania
```

**Konkretny przypadek użycia:** Zasilenie projekcji przez CDC

- **Wejście:** Start connectora obejmuje snapshot i bieżące zmiany WAL.
- **Przebieg:** Uzgadniamy punkt przejścia, zapisujemy postęp i przetwarzamy zmiany.
- **Oczekiwany wynik:** Brak okna między skanem i streamingiem gubiącego aktualizacje.
- **Błąd lub granica:** Surowa zmiana tabeli nie jest automatycznie stabilnym eventem domenowym.

**Kiedy zastosować:** Dla projekcji i integracji opartych na zmianach DB. Nie myl wiersza UPDATE z poprawnie
zaprojektowanym publicznym eventem biznesowym.

**Koszt i pułapka:** Schemat tabel nie jest stabilnym API domenowym; snapshot startowy, offsety i retencja
logu wymagają koordynacji.

**Rozwinięcie i ślad wykonania:** CDC zaczyna od punktu w logu bazy i musi skoordynować go ze snapshotem
początkowym. Offset connectora nie jest numerem zdarzenia domenowego. Jeśli obserwujesz outbox, zachowujesz
publiczny kontrakt faktu; obserwacja tabel aplikacji wiąże odbiorców ze zmianami technicznego schematu.

**Ćwiczenie sprawdzające:** Zaprojektuj przejście snapshot → streaming bez pominięcia zmian.

## Reconciliation / Repair Loop / Anti-entropy

**Intuicja:** Nawet poprawne mechanizmy dostawy mogą wymagać kontroli po błędach danych lub starym backupie.
Reconciliation porównuje stan źródłowy i pochodny, znajduje rozbieżność oraz bezpiecznie ją naprawia. Jest
niezależną pętlą wykrywania, a nie założeniem, że pojedynczy event zawsze naprawi wszystko.

**Problem i mechanizm:** Niezależny proces porównuje stan źródłowy z pochodnym i naprawia rozbieżności
idempotentnie.

**Role i współpraca:** Źródło prawdy wyznacza poprawny stan, skaner porównuje klucze i wersje, repair wykonuje
idempotentną zmianę. Metryki pokazują pozostały zakres rozjazdu.

**Przykład:** Zamówienie PAID bez zlecenia wysyłki zostaje wykryte przez skan i otrzymuje brakujące zlecenie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
for (Order order : orders.paidWithoutShipment()) {
    shipmentCommands.ensureCreated(order.id(), order.version());
}
// ensureCreated jest bezpieczne przy powtórzeniu skanu.
```

**Konkretny przypadek użycia:** Naprawa brakującej wysyłki

- **Wejście:** Order42 jest PAID, ale brak lokalnego zlecenia wysyłki.
- **Przebieg:** Skan porównuje źródło i projekcję, ensureCreated tworzy brak bez dublowania.
- **Oczekiwany wynik:** Kolejny skan nie tworzy drugiego zlecenia.
- **Błąd lub granica:** Stara kopia nie może nadpisać nowszego legalnego stanu z inną wersją.

**Kiedy zastosować:** Gdy koszt pominiętego procesu lub starej projekcji jest istotny. Naprawa powinna
respektować nowsze legalne zmiany, a nie bezwarunkowo kopiować starszy odczyt.

**Koszt i pułapka:** Naprawa nie może nadpisać legalnej nowszej zmiany; porównanie wymaga wersji i jawnego
źródła prawdy.

**Rozwinięcie i ślad wykonania:** Proces repair porównuje źródło prawdy z projekcją według stabilnego klucza i
wersji. Brakujący wynik można wytworzyć, ale nowszego poprawnego wyniku nie wolno nadpisać starą kopią.
Naprawa musi być bezpieczna przy wielokrotnym uruchomieniu i mieć mierzalny postęp oraz limit obciążenia.

**Ćwiczenie sprawdzające:** Wprowadź jedną brakującą i jedną nowszą projekcję, napraw tylko pierwszą.

## Try–Confirm–Cancel / Reservation

**Intuicja:** Przed finalnym zakupem trzeba czasem tymczasowo zatrzymać zasób dla klienta. TCC dzieli operację
na Try, które rezerwuje, Confirm, które finalizuje, i Cancel, które oddaje prawo. Uczestnik musi zapewnić
legalne przejścia oraz rozstrzygnąć wyścig potwierdzenia z wygaśnięciem rezerwacji.

**Problem i mechanizm:** Zasób najpierw jest rezerwowany z terminem, później potwierdzany lub zwalniany przez
właściciela.

**Role i współpraca:** Reservation ma stan, termin i identyfikator kroku. Koordynator wysyła confirm lub
cancel, a właściciel zasobu atomowo ocenia bieżący stan.

**Przykład:** Try blokuje 2 bilety do 12:05; Confirm finalizuje przydział, Cancel zwalnia tylko aktywną
rezerwację.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
ReservationId hold = inventory.tryReserve(sku, units, expiresAt);
if (paymentConfirmed) inventory.confirm(hold);
else inventory.cancel(hold);
// Właściciel rezerwacji rozstrzyga jednoczesne confirm i expiry.
```

**Konkretny przypadek użycia:** Rezerwacja dwóch biletów

- **Wejście:** Try zatrzymuje 2 miejsca do 12:05.
- **Przebieg:** Confirm i expiry konkurują o jeden stan oraz wersję u właściciela zasobu.
- **Oczekiwany wynik:** Tylko jedno legalne przejście wygrywa.
- **Błąd lub granica:** Sam zegar klienta nie rozstrzyga jednoczesnego potwierdzenia i zwolnienia.

**Kiedy zastosować:** Gdy zasób można zarezerwować przed ostateczną decyzją. Koszt czasowo zablokowanej
dostępności jest częścią rozwiązania, nie darmowym zastępstwem transakcji.

**Koszt i pułapka:** Timeout i Confirm mogą się ścigać; stan i wersja muszą atomowo rozstrzygać właściciela.
Rezerwacja ogranicza dostępność.

**Rozwinięcie i ślad wykonania:** Stan rezerwacji określa, kto może wykonać Confirm albo Cancel. W chwili
expiry oba działania mogą konkurować, więc o wyniku decyduje atomowy zapis stanu i wersji, a nie samo
porównanie zegara klienta. Kolejne Confirm tego samego kroku powinno dać zgodny wynik, nie nową rezerwację.

**Ćwiczenie sprawdzające:** Zaprojektuj przeplot expiry i Confirm dokładnie na granicy terminu.

## Two-phase Commit / 2PC

**Intuicja:** Dwa uczestniczące zasoby mogą chcieć podjąć jedną wspólną decyzję zatwierdzenia. W 2PC
koordynator najpierw pyta o gotowość, a potem utrwala i rozsyła decyzję. Uczestnik po prepare musi zachować
możliwość wykonania tej decyzji, co wyjaśnia blokowanie podczas określonych awarii.

**Problem i mechanizm:** Koordynator przygotowuje uczestników, a potem wydaje wspólną decyzję commit lub
abort.

**Role i współpraca:** Koordynator prowadzi decyzję, uczestnicy przygotowują lokalny zapis, trwały log
umożliwia recovery. Nie każdy HTTP endpoint jest uczestnikiem takiego protokołu.

**Przykład:** Dwa zasoby XA zapisują prepare; decyzja jest utrwalona przed rozesłaniem commit.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
coordinator -> A, B: PREPARE
A, B -> coordinator: READY albo ABORT
coordinator: utrwal decyzję
coordinator -> A, B: COMMIT albo ABORT
po restarcie: odzyskaj decyzję, nie zgaduj jej z braku odpowiedzi
```

**Konkretny przypadek użycia:** Wspólny commit uczestników

- **Wejście:** Dwa zasoby wspierają prepare i trwałą decyzję protokołu.
- **Przebieg:** Koordynator zbiera readiness, utrwala commit lub abort i rozsyła wynik.
- **Oczekiwany wynik:** Recovery odczytuje decyzję zamiast jej zgadywać.
- **Błąd lub granica:** Utrata koordynatora po prepare może blokować; dowolne HTTP nie jest uczestnikiem XA.

**Kiedy zastosować:** Gdy zasoby i wymagania uzasadniają wspólny protokół commit. Porównuj z sagą przez
gwarancje i koszt dostępności, nie przez samą liczbę kroków.

**Koszt i pułapka:** Może blokować po utracie koordynatora; nie obejmuje dowolnego HTTP. Saga ma inną
semantykę i nie jest zamiennikiem bez kosztu.

**Rozwinięcie i ślad wykonania:** Po prepare uczestnik obiecuje zachować możliwość wykonania utrwalonej
decyzji i może utrzymywać zasoby podczas braku koordynatora. To wyjaśnia koszt dostępności. Weryfikuj
odzyskiwanie decyzji po restarcie zamiast utożsamiać 2PC z dwoma zwykłymi, niezależnymi wywołaniami commit.

**Ćwiczenie sprawdzające:** Prześledź restart po prepare oraz odzyskanie utrwalonej decyzji.

## Lease / Fencing Token

**Intuicja:** Wygaśnięcie locka nie zatrzymuje automatycznie starego workera: mógł tylko długo nie wykonywać
kodu. Lease ogranicza czas własności, a fencing chroni zasób przed spóźnionym właścicielem. Nowszy token
oznacza nowszą epokę i jest egzekwowany przy faktycznej zmianie danych.

**Problem i mechanizm:** Czasowa własność jest wsparta rosnącym tokenem, który store sprawdza przy każdej
chronionej operacji.

**Role i współpraca:** Issuer przydziela token, worker go przenosi, store pamięta najwyższą epokę i odrzuca
stare zapisy. Demo pokazuje ostatnią z tych ról.

**Przykład:** Worker A ma token 7; po pauzie GC B dostaje 8, więc store odrzuca spóźniony zapis A z 7.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
resource.claim(8);
resource.write(8, "current owner");
resource.write(7, "paused owner resumed"); // Store ma odrzucić zapis.
```

**Konkretny przypadek użycia:** Stary worker po pauzie

- **Wejście:** A miał token 7, B przejął z tokenem 8.
- **Przebieg:** Chroniony store pamięta nową epokę i odrzuca późne write(7).
- **Oczekiwany wynik:** A nie nadpisuje wyniku B po powrocie z pauzy.
- **Błąd lub granica:** Jeśli store nie egzekwuje tokenu, wygaśnięcie lease w innym systemie nie wystarcza.

**Kiedy zastosować:** Dla liderów, schedulerów i rezerwacji pracy po failover. Sam losowy identyfikator locka
nie daje porządku potrzebnego do fencing.

**Koszt i pułapka:** Sam timeout locka i unikalny losowy token nie chronią przed starym właścicielem; store
musi egzekwować porządek.

**Rozwinięcie i ślad wykonania:** FencedResource najpierw przyjmuje claim(8), potem odrzuca write(7), nawet
jeśli stary worker nadal sądzi, że posiada lease. Sam lock manager nie wystarcza: egzekucja tokenu musi leżeć
w zasobie chronionym zapisem. Demo nie implementuje wydawania tokenów ani rozproszonego protokołu wyboru
właściciela.

**Ćwiczenie sprawdzające:** Odtwórz pause → expiry → nowy właściciel → spóźniony zapis starego.

## Leader Election / Single-writer

**Intuicja:** Niektóre operacje wymagają jednego aktywnego autora w danym zakresie. Leader Election wybiera tę
rolę i umożliwia przejęcie po awarii. Brak heartbeat nie dowodzi jednak śmierci poprzednika, więc prawo do
zapisu musi zostać powiązane z uzgodnioną epoką i egzekucją własności.

**Problem i mechanizm:** Jeden aktywny właściciel prowadzi określony strumień decyzji, korzystając z protokołu
wyboru i epok.

**Role i współpraca:** Kandydaci uczestniczą w protokole, nowy lider otrzymuje epokę, store chroni zakres.
Lider shardu i lider całego systemu mają inne granice wpływu.

**Przykład:** Lider shardu numeruje appendy; po zmianie lidera store odrzuca zapisy ze starszej epoki.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
uzgodnij lidera dla shardu S w epoce 42
lider wysyła zapis (S, 42, command)
store akceptuje tylko zgodnego właściciela
po failover epoka 43 unieważnia spóźnione próby z 42
```

**Konkretny przypadek użycia:** Zmiana lidera shardu

- **Wejście:** Stary lider epoki 41 traci kontakt, protokół uzgadnia 42.
- **Przebieg:** Nowe żądania niosą 42, zasób nie uznaje już 41.
- **Oczekiwany wynik:** Jedno aktualne prawo zapisu chroni zakres shardu.
- **Błąd lub granica:** Heartbeat sam nie dowodzi śmierci starego lidera i nie chroni przed split-brain.

**Kiedy zastosować:** Gdy potrzebujesz serializacji decyzji przez jednego właściciela. Nie implementuj wyboru
lidera wyłącznie jako „nie widzę sąsiada, więc jestem liderem”.

**Koszt i pułapka:** Heartbeat sam nie dowodzi wyłączności przy partycji sieci; bez fencing możliwy jest
split-brain.

**Rozwinięcie i ślad wykonania:** Nowy lider ma nowszą epokę, a store odrzuca opóźnione żądania starszej
epoki. Bez tej relacji dwóch liderów może wykonywać skutki podczas partycji sieci. Algorytm wyboru i mechanizm
fencing muszą mieć zgodny zakres: shard, kolejkę albo cały chroniony zasób.

**Ćwiczenie sprawdzające:** Wyjaśnij zachowanie dwóch kandydatów odciętych od siebie i store.

## Quorum / Consensus

**Intuicja:** Repliki mogą mieć różne kopie historii i nie zawsze się komunikować. Quorum określa wymagany
zbiór uczestników decyzji, a consensus dodatkowo uzgadnia wspólny wynik lub uporządkowaną historię według
konkretnego protokołu. Nie są synonimami prostego „dostałem dwie odpowiedzi”.

**Problem i mechanizm:** Decyzja wymaga odpowiedniego zbioru potwierdzeń; consensus dodatkowo uzgadnia
uporządkowaną historię w określonym modelu awarii.

**Role i współpraca:** Członkostwo wyznacza zbiory, protokół ustala reguły głosu, wersji, commit i odczytu.
Przecięcie quorum pomaga, lecz samo nie definiuje całej spójności.

**Przykład:** W klastrze 3 węzłów lider zatwierdza wpis po wymaganej większości zgodnie z protokołem
konsensusu.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
klaster: A, B, C
podział sieci: [A, B] | [C]
większość: 2
prawo commit nadal wynika z protokołu lidera, epok i logu
nie z dowolnego policzenia dwóch odpowiedzi klienta
```

**Konkretny przypadek użycia:** Partycja klastra 2+1

- **Wejście:** A i B widzą się, C jest odcięte.
- **Przebieg:** Opisany protokół uzgadnia lidera i commit z wymaganym quorum.
- **Oczekiwany wynik:** C nie może sam ogłosić dowolnego zgodnego zapisu większości.
- **Błąd lub granica:** Samo policzenie R+W>N bez reguł wersji i odczytu nie dowodzi liniowalności.

**Kiedy zastosować:** Gdy potrzebujesz uzgodnienia mimo częściowej niedostępności. Korzystaj z opisanego
protokołu i modelu awarii zamiast wywodzić liniowalność z samej nierówności R+W>N.

**Koszt i pułapka:** Samo R+W>N nie dowodzi liniowalności dowolnej implementacji; potrzebne są wersje, quorum
intersection i reguły odczytu.

**Rozwinięcie i ślad wykonania:** Większość musi wynikać z konkretnego protokołu i jego członkostwa. Przy
podziale 2+1 część z dwoma może kontynuować tylko zgodnie z regułami wyboru, logu i commit. Same liczby
potwierdzeń bez wersjonowania oraz zasad odczytu nie dowodzą liniowalnej historii.

**Ćwiczenie sprawdzające:** Rozdziel klaster 2+1 i wskaż, która część może potwierdzać zapis.

## CRDT / Conflict-free Merge

**Intuicja:** Przy aktualizacjach offline nie zawsze można pytać centralnego właściciela o każdą decyzję. CRDT
dobiera strukturę i merge tak, aby poprawne stany zbiegały mimo duplikatów i różnej kolejności wymiany. Nie
jest to arbitralne scalanie dowolnych obiektów, lecz matematyczny kontrakt konkretnego typu danych.

**Problem i mechanizm:** Struktura ma operację łączenia o własnościach pozwalających zbiegać replikom mimo
powtórzeń i innej kolejności.

**Role i współpraca:** GCounter ma składową per replicaId. Lokalne increment ją zwiększa, merge bierze maksima
składowych, odczyt sumuje. Każda składowa ma jednego niezależnego autora.

**Przykład:** G-Counter przechowuje licznik per replika; merge bierze maksimum składowych, odczyt sumuje je.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
GCounter a = GCounter.empty().increment("A", 3);
GCounter b = GCounter.empty().increment("B", 2);
GCounter combined = a.merge(b);
// combined.value() == 5; combined.merge(a) nadal daje 5.
```

**Konkretny przypadek użycia:** Licznik z dwóch replik

- **Wejście:** A zwiększa swoją składową o 3, B swoją o 2.
- **Przebieg:** Merge bierze maksimum per replicaId, odczyt sumuje.
- **Oczekiwany wynik:** Wynik wynosi 5 także po ponownym merge A.
- **Błąd lub granica:** Dwóch niezależnych autorów tego samego replicaId może zgubić przyrosty.

**Kiedy zastosować:** Dla danych z regułą zbieżnego łączenia, np. określonych liczników i zbiorów. Globalny
limit zapasu nie wynika automatycznie z możliwości merge.

**Koszt i pułapka:** Nie dowolna reguła biznesowa jest zbieżna; globalny limit zapasu wymaga silniejszego
mechanizmu.

**Rozwinięcie i ślad wykonania:** GCounter przechowuje składową per replika i łączy ją maksimum, więc duplikat
stanu niczego nie dodaje. Testy sprawdzają przemienność, łączność oraz idempotencję. Kontrprzykład dwóch
niezależnych autorów tego samego replicaId pokazuje utratę sumy: każda składowa wymaga jednego właściciela
aktualizacji.

**Ćwiczenie sprawdzające:** Połącz te same stany dwa razy i w odwrotnej kolejności, sprawdź identyczny wynik.

## Read Your Writes / Session Consistency

**Intuicja:** Użytkownik po zapisaniu adresu oczekuje, że jego następny odczyt pokaże tę zmianę.
Asynchroniczna replika może jej jeszcze nie widzieć. Read Your Writes daje gwarancję dla określonej sesji lub
klienta: odczyt nie może być starszy niż własny potwierdzony zapis.

**Problem i mechanizm:** Klient po zapisie otrzymuje odczyt co najmniej tak nowy jak jego własna zmiana.

**Role i współpraca:** Zapis zwraca minimalną wersję, klient przenosi ją dalej, serwer wybiera odpowiednio
świeże źródło albo czeka w limicie. Inni klienci mogą mieć odrębne gwarancje.

**Przykład:** Odpowiedź zawiera minimalną wersję 42; odczyt czeka na projekcję 42 lub idzie do właściciela
zapisu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Version written = orders.update(command);
OrderView view = query.readAtLeast(
        orderId, written, deadline.remaining());
// Jeśli projekcja nie nadąża, kontrakt określa fallback albo timeout.
```

**Konkretny przypadek użycia:** Odczyt własnej edycji

- **Wejście:** Aktualizacja adresu zwraca wersję 42, replika ma 40.
- **Przebieg:** Klient przekazuje minimalną wersję, serwer czeka w limicie albo czyta właściciela.
- **Oczekiwany wynik:** Użytkownik nie widzi starego adresu jako wyniku własnej aktualizacji.
- **Błąd lub granica:** Po deadline potrzebny jawny wynik; nieskończone oczekiwanie może wyczerpać zasoby.

**Kiedy zastosować:** Gdy natychmiastowa obserwacja własnego zapisu jest częścią doświadczenia lub
poprawności. Sticky routing jest tylko techniką i może wymagać dodatkowej ochrony po failover.

**Koszt i pułapka:** Sticky routing może zawieść przy failover; potrzebny jest limit czekania i jasny
fallback.

**Rozwinięcie i ślad wykonania:** Po zapisie klient przenosi minimalną wymaganą wersję. Serwer może poczekać
na projekcję, użyć właściciela zapisu albo zwrócić jawny stan oczekiwania. Zastosuj deadline również do tego
czekania; w przeciwnym razie próba poprawy spójności może wyczerpać wszystkie połączenia przy zatrzymanej
projekcji.

**Ćwiczenie sprawdzające:** Zasymuluj opóźnioną replikę oraz przekroczenie deadline oczekiwania.
