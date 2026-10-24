# System Design — E-commerce Platform

Materiały do nauki: [książki, źródła i zastosowanie](../MATERIALY.md#e-commerce).

<!-- reading-guide:start -->
## Ścieżki czytania

| Cel czytania | Punkt wejścia |
| --- | --- |
| Teoria i model problemu | [Wymagania](#wymagania) |
| Implementacja i jej dowody | [Mapa mechanizmów](#mapa-mechanizmów) |
| Uruchomienie i eksperymenty | [Uruchomienie](#uruchomienie) |
| Ograniczenia i scenariusze awarii | [Failure modes](#failure-modes) |

Wspólne mechanizmy i ich testy znajdziesz w [indeksie pojęć backend-engineering](../../backend-engineering/README.md#globalny-indeks-pojęć). Tutaj śledź ich konsekwencje dla opisywanego systemu.
Diagramy docelowe i warianty rozwoju czytaj razem z mapą implementacji; sam opis architektury nie oznacza, że wszystkie jej komponenty są uruchamiane w laboratorium.
<!-- reading-guide:end -->

## Zacznij tutaj

To studium nie próbuje implementować całego sklepu. Pokazuje najtrudniejszą
granicę systemu e-commerce: przejście od zmiennego koszyka do trwałego zamówienia,
rezerwacji ograniczonego stocku i wyniku płatności, a następnie bezpieczne
rozpropagowanie tego wyniku do systemów pochodnych.

| Pytanie | Odpowiedź |
| --- | --- |
| Najważniejszy niezmiennik | Zamówienie `PAID` ma dokładnie jedną zakończoną płatność i potwierdzone, niewygasłe rezerwacje wszystkich pozycji. |
| Krytyczny przepływ | koszyk → snapshot ceny → zamówienie → atomowa rezerwacja → payment intent → wynik PSP → potwierdzenie stocku → outbox |
| Szybka weryfikacja | `mvnw.cmd --batch-mode --no-transfer-progress test` |
| PostgreSQL | `mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify` |
| Świadome ograniczenie | PSP, broker, e-mail i OpenSearch są granicami demonstracyjnymi; webhook i reconciliation pokazują protokół, ale implementacja nie deklaruje exactly-once, settlement ani zgodności PCI DSS. |

## Mapa mechanizmów

| Mechanizm | Kod | Test | Gwarancja i granica |
| --- | --- | --- | --- |
| ochrona przed oversellingiem | `InventoryService`, `InventoryItemRepository` | `InventoryInvariantTest`, `PostgreSqlInfrastructureIT` | PostgreSQL `FOR UPDATE` i `@Version`; dwa równoległe requesty nie rezerwują ostatniej sztuki |
| lifecycle rezerwacji | `InventoryReservation` | `ReservationStateMachineTest` | pojedyncze przejście z `ACTIVE` do stanu terminalnego |
| payment/order state machine | `Payment`, `CustomerOrder` | `PaymentStateMachineTest` | wynik płatności nie może zostać odwrócony przez spóźniony callback |
| idempotentny checkout | `IdempotencyService`, unique constraint | `CheckoutFlowIntegrationTest` | retry zwraca ten sam order/payment i nie rezerwuje stocku ponownie |
| podpisany webhook PSP | `PaymentWebhookVerifier`, `PaymentService` | `PaymentWebhookVerifierTest` | HMAC surowego body, timestamp, event ID, kwota, waluta i stan monotoniczny |
| wynik płatności nieznany | `PaymentReconciliationWorker`, `PaymentProviderClient` | `PaymentReconciliationWorkerTest` | brak odpowiedzi PSP nie jest mapowany na porażkę; zmiana wymaga terminalnego wyniku providera |
| transactional outbox | `OutboxService`, `OutboxPublisher` | `OutboxEventStateTest`, `PostgreSqlInfrastructureIT` | `SKIP LOCKED`, lease claimu i recovery; delivery pozostaje at-least-once |
| pricing i pieniądze | `PromotionEngine`, `MoneyPolicy` | `PromotionEngineTest`, `MoneyPolicyTest` | priority/stackability, okno czasowe, jedna waluta i jawne rounding |
| kontrakt HTTP | `GlobalExceptionHandler`, springdoc | `ApiContractTest` | Problem Details i obecność krytycznych elementów w generowanym OpenAPI |
| idempotentny e-mail | `NotificationWorker`, `EmailClient` | `NotificationWorkerTest` | stabilny klucz `outbox-{id}` musi zostać honorowany przez produkcyjny provider |
| odbudowywalny indeks | `SearchIndexWorker` | `SearchIndexWorkerTest` | PostgreSQL pozostaje source of truth, OpenSearch jest projekcją |
| zgodność schematu | migracje Flyway + `ddl-auto=validate` | `SchemaContractTest`, `PostgreSqlInfrastructureIT` | ten sam jawny schemat uruchamia się na H2 i PostgreSQL; Hibernate go nie modyfikuje |

## Zakres

### Funkcje objęte studium

- katalog produktów i wariantów;
- koszyk ze snapshotem ceny;
- checkout chroniony kluczem idempotencji;
- rezerwacja, potwierdzenie, zwolnienie i wygaśnięcie stocku;
- zamówienie i płatność jako maszyny stanów;
- outbox, retry, dead-letter state i niezależne markery konsumentów;
- projekcja wyszukiwarki oraz powiadomienia;
- promocje, loyalty, zwroty, marketplace, ERP i WMS jako przykłady
  downstreamów lub sąsiednich domen.

### Poza zakresem wykonywalnej gwarancji

- certyfikowana integracja z PSP, 3-D Secure/SCA i settlement;
- produkcyjny broker oraz exactly-once processing;
- pełne naliczanie podatków zależne od jurysdykcji;
- realna replikacja PostgreSQL z `docker-compose`;
- kompletna ochrona danych kartowych i certyfikacja PCI DSS;
- optymalny silnik promocji, fraud detection i globalny multi-region active-active.

## Wymagania

### Funkcjonalne

1. Klient przegląda katalog i buduje koszyk.
2. Checkout zapisuje historyczny snapshot nazwy, SKU, ceny i waluty.
3. System nie sprzedaje większej liczby sztuk niż dostępna.
4. Retry tego samego checkoutu nie tworzy kolejnego zamówienia ani płatności.
5. Wynik płatności potwierdza albo zwalnia rezerwację.
6. Anulowanie przed płatnością zwalnia inventory w tej samej transakcji.
7. Zmiany domenowe trafiają do outboxa i mogą odbudować projekcje.

### Niefunkcjonalne

- katalog może być eventually consistent i agresywnie cache'owany;
- decyzje checkout/inventory/payment wymagają strong consistency w obrębie
  pojedynczej bazy źródłowej;
- p95 odczytu katalogu powinno być znacznie niższe niż p95 checkoutu;
- awaria OpenSearch, e-maila lub ERP nie może wycofać opłaconego zamówienia;
- backlog asynchroniczny musi być ograniczony, mierzalny i możliwy do replayu;
- system powinien degradować się przez wyłączenie funkcji pobocznych, a nie przez
  przyjmowanie zamówień bez poprawnego stocku.

## Capacity estimation

Poniższe liczby są założeniem ćwiczenia, nie wynikiem pomiaru:

| Wielkość | Założenie średnie | Peak |
| --- | ---: | ---: |
| aktywni użytkownicy dziennie | 1 000 000 | — |
| odczyty katalogu | 5 000 RPS | 25 000 RPS |
| modyfikacje koszyka | 800 RPS | 4 000 RPS |
| checkout | 100 RPS | 1 000 RPS |
| callbacki PSP | 100 RPS | 1 500 RPS |
| eventy outbox | 500/s | 5 000/s |

Przy 100 RPS checkoutu powstaje około 8,6 mln zamówień dziennie. Jeżeli średni
rekord zamówienia z pozycjami zajmuje 4 KiB, sam gorący zapis to około 34 GiB
dziennie przed indeksami, WAL-em, kopiami i historią audytową. Taka skala wymaga
retencji, archiwizacji i partycjonowania tabel czasowych, zanim wymaga shardowania
całej domeny.

Peak jest ważniejszy od średniej. Kampania lub flash sale tworzy hot SKU, dla
którego tysiące checkoutów konkurują o jeden rekord inventory. Większa liczba
instancji API nie zwiększa wtedy przepustowości tej decyzji; potrzebne są kolejka,
admission control albo osobny mechanizm alokacji tokenów stocku.

Warto policzyć również przepływ danych, a nie wyłącznie RPS. Jeżeli odpowiedź
katalogowa ma średnio 20 KiB, peak 25 000 RPS oznacza około 500 MiB/s ruchu przed
kompresją i cache'em brzegowym. Zdjęcia i wideo nie powinny przechodzić przez API:
obiekt trafia do object storage, a klient pobiera go przez CDN z podpisanym lub
publicznym URL-em. Przy 5 000 eventów/s i 2 KiB na rekord outbox generuje około
824 GiB surowych danych dziennie, jeśli rekordów nie archiwizujemy. Capacity plan
musi więc obejmować bandwidth, połączenia, IOPS, rozmiar indeksów, WAL, retencję i
tempo opróżniania backlogu, a nie jedynie liczbę instancji aplikacji.

## Architektura

```text
                         ┌──────────────┐
 client ──HTTP──────────▶│ ecommerce-api│
                         └──────┬───────┘
                                │ jedna transakcja lokalna
                       ┌────────▼─────────┐
                       │ PostgreSQL writer │
                       │ order/inventory   │
                       │ payment/outbox    │
                       └────────┬─────────┘
                                │ poll / docelowo broker
                    ┌───────────┴───────────┐
                    ▼                       ▼
             search-indexer         notification-worker
                    │                       │
                    ▼                       ▼
              OpenSearch               e-mail provider
```

`api/` jest jedyną kanoniczną aplikacją zapisującą. Nadrzędny katalog jest
reactorem Maven, a nie czwartą aplikacją. `search-indexer` i
`notification-service` są obecnie workerami współdzielącymi bazę. Jest to prosty
wariant transactional-log tailing, nie niezależne mikroserwisy. Docelowe
wydzielenie wymaga brokera, kontraktów zdarzeń i niezależnego ownership danych.

## Model danych i source of truth

| Dane | Source of truth | Typowe access patterny |
| --- | --- | --- |
| produkt i wariant | PostgreSQL catalog | po slug/SKU/ID, lista kategorii |
| dostępność | PostgreSQL inventory | po variant ID, blokowany zapis rezerwacji |
| koszyk | PostgreSQL; Redis może być cache'em | aktywny koszyk użytkownika |
| zamówienie | PostgreSQL order | po ID+user, historia użytkownika |
| płatność | PostgreSQL + okresowa rekoncyliacja PSP | po order, idempotency key, provider event ID |
| event integracyjny | PostgreSQL outbox | najstarsze NEW/FAILED, claim batcha |
| wyszukiwanie | OpenSearch, odbudowywalna projekcja | tekst, filtry, ranking |

Zamówienie nie odwołuje się do aktualnej ceny w katalogu. Przechowuje snapshot,
ponieważ cena, nazwa i wariant mogą zmienić się już po zakupie. Kwoty pieniężne
używają `BigDecimal` i jawnej waluty; reguły zaokrąglania powinny należeć do
jednego komponentu money/pricing, a nie do kontrolerów.

## Granice domenowe i odpowiedzialność danych

E-commerce nie jest jedną domeną CRUD. Katalog odpowiada na pytanie „co można
oferować?”, inventory — „ile można jeszcze przydzielić?”, pricing — „jaka cena
obowiązuje w tym kontekście?”, a order — „co klient rzeczywiście kupił?”. Te
odpowiedzi są powiązane, lecz mają inny lifecycle, wolumen i wymagany poziom
spójności. Wspólna encja `Product` używana przez wszystkie moduły szybko staje
się modelem anemicznym, który miesza opis produktu, cenę, stock, ranking i dane
historyczne zamówienia.

| Obszar | Odpowiedzialność | Własne dane | Dlaczego granica jest istotna |
| --- | --- | --- | --- |
| Identity | konto, principal, role i zgody | użytkownik, sesja, consent | checkout ufa principalowi, nie `userId` z requestu |
| Catalog | produkt, wariant, atrybuty, kategorie | product, variant, category | opis zmienia się niezależnie od zamówienia |
| Pricing | cena bazowa, cennik, podatki i rabaty | price list, promotion rule | cena zależy od rynku, czasu, waluty i klienta |
| Cart | intencja zakupu użytkownika | cart, cart item | koszyk nie jest rezerwacją ani umową sprzedaży |
| Inventory | podaż i alokacja stocku | item, reservation, movement | tylko ten obszar może zdecydować o dostępności |
| Checkout | orkiestracja granicy zakupu | idempotency record, quote | zamienia zmienny koszyk w trwałą decyzję |
| Order | historyczny fakt biznesowy | order, order item | przechowuje snapshot niezależny od katalogu |
| Payment | intencja i wynik rozliczenia | payment, provider event | timeout nie jest równoznaczny z porażką |
| Fulfillment | kompletacja i dostawa | shipment, parcel, tracking | jedno zamówienie może mieć wiele przesyłek |
| Search | wyszukiwanie i ranking | odbudowywalny dokument | jest projekcją zoptymalizowaną do odczytu |
| Notification | komunikacja z klientem | delivery attempt, template version | błąd e-maila nie unieważnia zamówienia |

Granica domenowa nie musi od razu oznaczać osobnego procesu. W tym projekcie
modularny monolit daje wspólną transakcję dla checkoutu, a rozdział pakietów
utrzymuje ownership. Mikroserwis ma sens dopiero wtedy, gdy niezależne skalowanie,
tempo zmian, bezpieczeństwo albo zespół uzasadniają koszt komunikacji sieciowej,
eventual consistency i operacji wielu baz.

## Katalog, wariant i wyszukiwanie

Produkt opisuje ofertę widoczną dla człowieka, natomiast wariant/SKU jest
konkretną jednostką sprzedaży, np. „koszulka, granatowa, M”. Cena, kod kreskowy i
inventory zwykle dotyczą wariantu, a tytuł, opis i marka — produktu. Ta różnica
zapobiega przechowywaniu stocku na poziomie abstrakcyjnego produktu.

Relacyjny katalog dobrze obsługuje integralność zapisu i panel administracyjny,
ale nie musi dobrze wykonywać pełnotekstowego wyszukiwania, facetingu, literówek
i rankingu. Dlatego dokument wyszukiwarki denormalizuje produkt, warianty,
kategorie, dostępność i pola rankingowe. Jest świadomie duplikowany, ponieważ
optymalizuje inny access pattern.

W produkcyjnym indeksie trzeba jawnie rozstrzygnąć:

- analizatory językowe, stemming, synonimy oraz normalizację znaków;
- filtrowanie po polach keyword i faceting bez wysokiej cardinality;
- ranking tekstowy kontra sygnały biznesowe, np. dostępność i popularność;
- autocomplete jako osobny indeks/prefix field, a nie wildcard od początku;
- wersję dokumentu chroniącą przed nadpisaniem nowszego stanu starym eventem;
- reindex przez nowy indeks i atomową zmianę aliasu;
- zachowanie przy opóźnieniu indeksu i możliwość fallbacku do katalogu.

Search nie powinien być źródłem ceny akceptowanej przez checkout. Wynik może być
chwilowo stary, więc karta produktu i checkout ponownie odczytują dane źródłowe.

## Koszyk, pricing, promocje i pieniądze

Koszyk reprezentuje edytowalną intencję, a nie obietnicę ceny lub dostępności.
Może przechowywać snapshot wyświetlonej ceny, aby pokazać użytkownikowi zmianę,
ale checkout musi przeliczyć ofertę na serwerze. Typowy model tworzy wtedy
krótkotrwały `Quote` zawierający pozycje, rabaty, podatek, dostawę, walutę,
wersję reguł i termin ważności.

Poprawny pipeline pricingu powinien być deterministyczny dla tych samych danych:

```text
cena cennikowa
  → rabaty pozycji
  → rabaty koszyka / kupon
  → koszt dostawy
  → podatek zgodny z jurysdykcją
  → total i jawne zaokrąglenie
```

Kolejność ma znaczenie. Rabat 10% przed podatkiem może dać inny wynik niż po
naliczeniu podatku, a promocje mogą się wykluczać albo mieć priorytety. Silnik
promocji potrzebuje reguł stackability, limitu użyć i idempotentnego naliczenia.
Kupon „ostatnie 100 użyć” jest problemem współbieżności podobnym do inventory.

Pieniądze wymagają wartości i kodu waluty. Nie używamy `double`; skala oraz
rounding mode są częścią kontraktu. Dla systemu wielowalutowego zapisujemy kwotę
oryginalną, kurs, źródło kursu i czas jego obowiązywania. Suma zamówienia powinna
wynikać z zapisanych składowych, aby dało się ją wyjaśnić podczas reklamacji.

## Projektowanie API sklepu

API powinno ujawniać zasoby i decyzje biznesowe, a nie strukturę tabel. Odczyty
katalogu są bezpieczne i cache'owalne, natomiast checkout jest komendą z efektem
ubocznym i wymaga klucza idempotencji.

| Operacja | Przykładowa semantyka | Ważny kontrakt |
| --- | --- | --- |
| `GET /products/{slug}` | odczyt produktu | `ETag`, cache-control, 404 |
| `GET /products?cursor=...` | lista/filtrowanie | stabilny sort i keyset pagination |
| `PUT /carts/{id}/items/{variantId}` | ustawienie ilości | idempotentne, opcjonalne `If-Match` |
| `DELETE /carts/{id}/items/{variantId}` | usunięcie pozycji | retry daje ten sam stan |
| `POST /checkout` | utworzenie zamówienia | `Idempotency-Key`, hash body, 201/409 |
| `GET /orders/{id}` | status dla właściciela | autoryzacja object-level, read-your-writes |
| `POST /payments/webhooks/{provider}` | wynik PSP | podpis, timestamp, deduplikacja event ID |
| `POST /orders/{id}/cancel` | komenda domenowa | dozwolony stan i idempotentna kompensacja |

Błędy powinny używać jednolitego Problem Details (`application/problem+json`)
z kodem maszynowym, correlation ID i bez stack trace. Rozróżniamy błąd składni
requestu, naruszenie reguły domenowej, konflikt współbieżności, niedostępność
zależności i błąd wewnętrzny. Sama odpowiedź `400` nie daje klientowi stabilnego
kontraktu.

Lista produktów wymaga stabilnego sortowania z tie-breakerem, np.
`(createdAt, productId)`. Offset jest prosty, lecz przy dużym zbiorze kosztowny i
niestabilny podczas równoległych zmian; cursor/keyset lepiej nadaje się do feedu.
Publiczny kontrakt powinien mieć OpenAPI, test kompatybilności i politykę
deprecacji. Dodawanie pola jest zwykle kompatybilne, ale zmiana znaczenia,
obowiązkowości albo enumu może złamać klienta bez zmiany URL.

## Macierz spójności

Nie każdy fragment sklepu wymaga tej samej spójności. Próba zapewnienia globalnej
strong consistency zwiększa latency i coupling; używanie eventual consistency
wszędzie prowadzi natomiast do oversellingu i niewyjaśnionych płatności.

| Decyzja | Wymagana semantyka | Uzasadnienie |
| --- | --- | --- |
| rezerwacja ostatniej sztuki | serializowana/atomowa per SKU | nie można zaakceptować dwóch właścicieli |
| utworzenie order + reservation + outbox | jedna lokalna transakcja | brak częściowo zapisanego checkoutu |
| retry checkoutu | ten sam wynik dla tego samego żądania | klient nie wie, czy odpowiedź zginęła |
| wynik płatności | deduplikacja + stan monotoniczny | callback może być spóźniony i powtórzony |
| karta produktu | bounded stale | stary opis nie narusza inwariantu zakupu |
| search | eventual consistency | indeks można odbudować z source of truth |
| e-mail | at-least-once z idempotentnym providerem | wiadomość nie należy do transakcji orderu |
| historia tuż po checkoutcie | read-your-writes | replika może jeszcze nie widzieć zamówienia |

W systemie rozproszonym „saga” nie tworzy rozproszonej transakcji. Koordynuje
lokalne transakcje i kompensacje. Kompensacja nie cofa czasu: refund jest nowym
zdarzeniem finansowym, a release stocku nowym ruchem magazynowym. Każdy krok musi
być idempotentny, mieć deadline i rozróżniać wynik negatywny od nieznanego.

## Krytyczny flow checkoutu

1. API wymaga `Idempotency-Key` i hashuje semantyczne body requestu.
2. Unique constraint ogranicza klucz do użytkownika oraz operacji.
3. Pobierany jest aktywny koszyk; jego pozycje zawierają snapshot ceny.
4. Powstaje zamówienie `PENDING_PAYMENT` z immutable snapshotem pozycji.
5. Dla każdego wariantu inventory pobierane jest z pessimistic write lock.
6. Rezerwacja zmniejsza `sellable = available - reserved` i otrzymuje deadline.
7. Powstaje dokładnie jeden payment intent dla zamówienia.
8. Zamówienie, rezerwacje, płatność i eventy outbox commitują się razem.
9. Retry zakończonego requestu zwraca ten sam wynik.

Jedna transakcja jest świadomym wyborem modularnego monolitu. Po fizycznym
wydzieleniu domen nie istnieje transakcja obejmująca wszystkie bazy; proces musi
stać się sagą z timeoutami, idempotentnymi komendami i kompensacjami.

## Inventory i problem oversellingu

Naiwny algorytm `SELECT → if available → UPDATE` traci poprawność, gdy dwie
transakcje odczytają tę samą wartość. Projekt stosuje:

- pessimistic write lock przy decyzji reserve/confirm/release;
- `@Version` jako dodatkową ochronę przed utraconą aktualizacją w innych ścieżkach;
- invarianty w encji blokujące ujemne wartości i confirm bez rezerwacji;
- unique constraint `order + variant`, który blokuje podwójną rezerwację pozycji;
- osobny stan `EXPIRED`, odmienny od ręcznego `RELEASED`.

Alternatywą jest pojedynczy conditional update:

```sql
update inventory_items
set reserved_quantity = reserved_quantity + :quantity
where variant_id = :variant
  and available_quantity - reserved_quantity >= :quantity;
```

`updatedRows == 1` oznacza sukces. Conditional update zwykle skraca czas blokady,
ale utrudnia bogatszą diagnostykę. Dla flash sale warto rozważyć admission queue
lub tokeny, aby baza nie stała się kolejką tysięcy oczekujących transakcji.

## Maszyny stanów

```text
Reservation: ACTIVE ──▶ CONFIRMED
                    ├─▶ RELEASED
                    └─▶ EXPIRED

Payment:     PENDING ──▶ SUCCEEDED
                    └─▶ FAILED

Order: PENDING_PAYMENT ──▶ PAID
                       ├─▶ PAYMENT_FAILED ──▶ CANCELLED
                       └─▶ CANCELLED
```

Stan terminalny nie może zostać nadpisany spóźnionym callbackiem. Szczególnie
`FAILED → SUCCEEDED` nie jest zwykłym setterem: wymaga nowej próby płatności albo
rekoncyliacji i jawnego audytu.

Wyścig `payment success` kontra `reservation expiration` rozstrzyga blokada
rezerwacji. Success sprawdza deadline przed potwierdzeniem. Jeśli expiration
wygra, płatność nie może bezwarunkowo oznaczyć zamówienia jako opłacone; potrzebny
jest refund/void lub ręczna ścieżka naprawcza.

## Idempotencja

Zakres klucza to `(userId, operation, idempotencyKey)`. Ten sam klucz z innym
hashem body daje `409 Conflict`. Rekord `PROCESSING` oznacza operację w toku;
klient powinien ponowić odczyt z backoffem zamiast wykonywać efekt drugi raz.

Unique constraint jest ostatnią linią obrony między instancjami, ale sam wzorzec
`find → insert` nie usuwa wyścigu. Przegrany insert musi zostać zmapowany na
odczyt istniejącego wyniku lub odpowiedź `409`, nigdy na drugi checkout.

Rekord utworzony w tej samej transakcji co checkout znika przy rollbacku. To
świadomy wariant: klient może ponowić request. Trwałe przechowywanie błędów wymaga
osobnej transakcji claim/finalize i obsługi przypadku, gdy wynik idempotencji
zostanie zatwierdzony, a biznesowa transakcja nie.

## Płatności i webhook

Endpointy `mock-success` i `mock-failure` są dostępne wyłącznie dla administratora
i służą do laboratorium. Endpoint `POST /api/payments/webhooks/provider` pokazuje
rzeczywistą granicę zaufania: weryfikuje HMAC nad surowym body oraz timestamp
przed deserializacją. Następnie `PaymentService` sprawdza:

1. podpis HMAC lub podpis asymetryczny na surowym body;
2. timestamp i maksymalny wiek callbacka;
3. unikalny `providerEventId` chroniący przed replayem;
4. zgodność merchant account, waluty i kwoty;
5. dozwolone przejście maszyny stanów;
6. audyt bez danych karty, tokenów i sekretów.

Timeout requestu do PSP nie oznacza porażki płatności. Oznacza wynik nieznany.
`PaymentReconciliationWorker` odpytuje port `PaymentProviderClient` dla starych
płatności `PENDING`. Brak odpowiedzi pozostawia wynik nieznany; tylko terminalna
odpowiedź providera przechodzi przez ten sam idempotentny handler co webhook.
Lokalny `NoopPaymentProviderClient` jest świadomie symulacją, nie integracją PSP.
Przechowywanie
PAN/CVV jest poza zakresem; system powinien używać tokenizacji/hosted checkout,
aby ograniczyć zakres PCI DSS.

### Autoryzacja, capture i rozliczenie

„Płatność udana” może oznaczać różne rzeczy. W modelu authorize/capture PSP
najpierw blokuje środki, a capture następuje dopiero po potwierdzeniu możliwości
realizacji. Sprzedaż natychmiastowa łączy oba kroki, ale utrudnia obsługę braku
stocku. `void` anuluje niewychwyconą autoryzację, `refund` tworzy zwrot po capture,
a chargeback jest procesem zainicjowanym poza sklepem.

System powinien przechowywać własny payment intent i identyfikatory PSP, lecz nie
traktować callbacka jako księgi finansowej. Okresowa reconciliation porównuje
zamówienia, płatności, refundy i settlement z raportem providera. Rozbieżność jest
sygnałem operacyjnym wymagającym kolejki naprawczej, nie automatycznego
„ustawienia statusu na sukces”. Dla marketplace dochodzi ledger rozdzielający
należności sprzedawców, prowizję platformy i payouty.

3-D Secure/SCA może przerwać synchroniczny request i wymagać interakcji klienta.
Dlatego payment intent posiada stan oczekujący, a zamówienie nie zakłada, że
wywołanie PSP zakończy się w jednym HTTP request-response.

## Outbox i delivery semantics

Event domenowy jest zapisywany w tej samej transakcji co zmiana agregatu. Worker
pobiera batch przez PostgreSQL `FOR UPDATE SKIP LOCKED`, przechodzi przez
`PROCESSING`, a błąd otrzymuje
wykładniczy backoff. Po pięciu błędach rekord trafia do `DEAD` i wymaga alertu
oraz kontrolowanego replayu. `claimedAt` pełni rolę krótkiego lease: rekord
pozostawiony w `PROCESSING` przez martwego workera może zostać przejęty ponownie.

Nie ma atomowej transakcji pomiędzy PostgreSQL a brokerem lub providerem e-mail.
Awaria po efekcie zewnętrznym, lecz przed commitem markera, tworzy duplikat.
Recovery po wygaśnięciu claimu nie wie, czy publikacja zdążyła nastąpić.
Dlatego semantyka brzmi **at-least-once**, a konsument musi używać stabilnego
event ID jako klucza idempotencji.

Ordering powinien być wymagany tylko w obrębie agregatu, np. `orderId`. Globalne
FIFO ogranicza skalowanie i zwykle nie jest potrzebne. Po przejściu na broker
aggregate ID powinno być partition key.

## Search jako odbudowywalna projekcja

OpenSearch nie jest source of truth. Event tylko wskazuje, który produkt zmienił
się; indexer doczytuje bieżący stan z katalogu i inventory, a następnie wykonuje
upsert dokumentu po `productId`. `InventoryUpdated` mapuje `variantId` na produkt.

Potrzebne operacyjnie mechanizmy to:

- initial snapshot i przejście do strumienia zmian;
- wersja dokumentu lub monotoniczna wersja agregatu dla reorderu;
- okresowa rekoncyliacja liczby/hashów rekordów;
- alias indeksu dla bezprzerwowego reindexu;
- usunięcie dokumentu dla produktu usuniętego;
- bounded retry i dead-letter zamiast nieskończonego blokowania partycji.

## Cache

Cache katalogu może zwracać krótkotrwale stare opisy i ceny prezentacyjne, ale
checkout ponownie waliduje snapshot oraz stock w bazie źródłowej. Cache nigdy nie
jest autorytetem do decyzji o sprzedaży.

Klucze powinny zawierać wszystkie wymiary odpowiedzi: produkt, język, walutę,
tenant/market i wersję kontraktu. Hot key wymaga request coalescing, jitter TTL i
ochrony przed stampede. Awaria Redis powinna degradować wydajność katalogu, nie
poprawność checkoutu.

## Skalowanie i partycjonowanie

1. Najpierw skaluj stateless API i read cache.
2. Dodaj read replicas wyłącznie dla odczytów tolerujących lag; po checkoutcie
   stosuj read-your-writes na writerze.
3. Partycjonuj historyczne orders/outbox po czasie dla retencji i maintenance.
4. Oddziel workery od request path, stosując bounded batch i backpressure.
5. Sharduj dopiero po zmierzeniu limitu jednego writera. Dobry shard key zachowuje
   order, payment i idempotency record razem; `userId` ułatwia historię klienta,
   ale komplikuje globalne raporty i hot tenants.

Inventory dla jednego SKU jest naturalnym punktem serializacji. Shardowanie nie
usuwa hot SKU, ponieważ wszystkie decyzje nadal trafiają do jednego właściciela.

### Flash sale i kontrola przeciążenia

Przy nagłym piku problemem nie jest tylko CPU API. Hot SKU serializuje transakcje,
pool połączeń do bazy staje się kolejką, a retry klientów może zwielokrotnić ruch.
Skalowanie instancji bez limitu pogarsza przeciążenie downstreamu.

Ochrona powinna mieć kilka warstw: rate limit per użytkownik, ograniczoną kolejkę,
limit współbieżnych checkoutów, krótki deadline, retry budget i load shedding.
Dla ekstremalnej kampanii można poprzedzić bazę pulą tokenów lub kolejką per SKU,
ale taki model komplikuje uczciwość, wygasanie alokacji i odzyskiwanie po awarii.
Wirtualna poczekalnia jest decyzją produktowo-operacyjną, nie tylko techniczną.

## Fulfillment, anulowanie, zwroty i refundy

Opłacenie zamówienia nie kończy procesu. Fulfillment może podzielić order na
przesyłki z różnych magazynów, dopuścić częściowy backorder albo oznaczyć pozycję
jako niemożliwą do realizacji. Stan przesyłki i stan zamówienia nie powinny być
jednym enumem: jedna paczka może być dostarczona, gdy druga nadal czeka.

Anulowanie, zwrot towaru i refund to różne procesy:

- anulowanie przed capture zwykle zwalnia rezerwację i wykonuje `void`;
- anulowanie po capture wymaga refundu i decyzji o zatrzymaniu wysyłki;
- zwrot rozpoczyna się po wydaniu towaru i posiada własną autoryzację RMA;
- przyjęty zwrot nie zawsze wraca do `sellable` — może być uszkodzony;
- częściowy refund musi wskazywać pozycje, podatki, dostawę i powód;
- każdy ruch stocku i pieniędzy powinien pozostawić audytowalny zapis.

W dużym systemie stan orderu bywa projekcją stanów payment, fulfillment i return.
Nie wolno pozwolić, aby pojedynczy spóźniony event cofnął proces z terminalnego
stanu do wcześniejszego.

## Sąsiednie domeny dużej platformy

Krytyczny checkout nie wyczerpuje architektury e-commerce. Poniższe obszary mają
inne access patterny i nie powinny być dopisywane do agregatu `Order` tylko
dlatego, że uczestniczą w zakupie.

### Media i CDN

Zdjęcia oraz filmy produktów należą do object storage, a katalog przechowuje ich
identyfikatory i metadane. Upload używa pre-signed URL, walidacji typu i rozmiaru,
skanowania malware oraz asynchronicznego generowania wariantów. CDN odciąża API,
lecz wymaga wersjonowanych, immutable URL albo kontrolowanej invalidacji. API nie
powinno proxy'ować dużych plików przez własną pulę wątków i pamięć.

### Fraud, risk i abuse

Risk scoring może korzystać z urządzenia, velocity, adresu, historii klienta i
sygnałów PSP. Decyzja musi rozróżniać allow, deny i manual review. Model ML nie
zastępuje audytowalnych reguł ani limitów. Ochrony wymagają również credential
stuffing, card testing, scraping cen, masowe używanie kuponów i boty flash-sale.
Fail-open lub fail-closed jest decyzją biznesową zależną od operacji: niedostępny
recommender może się wyłączyć, ale niedostępna kontrola ryzyka płatności może
zatrzymać transakcję wysokiego ryzyka.

### Marketplace i settlement

Marketplace dodaje seller ownership oferty, moderację, prowizję, podatki,
split-order, payout oraz spory. Płatność klienta nie oznacza natychmiastowego
payoutu sprzedawcy. Ledger powinien oddzielać kwotę brutto, prowizję platformy,
refundy, chargebacki i saldo możliwe do wypłaty. Raport finansowy nie powinien
powstawać przez sumowanie aktualnych statusów zamówień.

### Rekomendacje i analityka

Rekomendacje są projekcją i mogą degradować się do bestsellerów. Impression,
click, add-to-cart i purchase tworzą strumień analityczny, który nie może
blokować request path. Event analityczny potrzebuje zgody, retencji, wersji
schematu i ochrony przed wysoką cardinality. Wyniki eksperymentów A/B wymagają
stabilnego przypisania wariantu i guardrail metrics, nie tylko wzrostu CTR.

### Cykl życia danych

Dane zamówień, płatności, audytu i PII mają różne okresy retencji. Usunięcie konta
może oznaczać anonimizację danych kontaktowych przy zachowaniu dokumentów
finansowych wymaganych prawem. Proces usunięcia musi objąć bazę źródłową, cache,
search, analitykę i późniejsze odtworzenie z backupu. Backup powinien mieć
procedurę ponownego zastosowania tombstone'ów, aby restore nie „wskrzesił” PII.

## Dostępność, deployment i disaster recovery

Minimalna topologia produkcyjna obejmuje kilka instancji stateless API w co
najmniej dwóch strefach, PostgreSQL writer z repliką/failoverem, osobne workery,
Redis oraz klaster wyszukiwarki. Każdy komponent ma inną rolę:

- liveness odpowiada, czy proces należy zrestartować;
- readiness blokuje ruch, dopóki proces nie może bezpiecznie obsługiwać requestu;
- graceful shutdown przestaje przyjmować ruch i kończy lub zwalnia claimy;
- bounded pool chroni bazę i providera przed nieograniczoną współbieżnością;
- autoscaling używa także saturation/queue age, nie tylko CPU;
- migracje schematu stosują expand/contract, aby stara i nowa wersja mogły
  współistnieć podczas rolloutu.

HA ogranicza przerwy przy typowej awarii instancji lub strefy. Disaster recovery
odpowiada na utratę lub uszkodzenie danych. Trzeba ustalić mierzalne RPO (ile
danych wolno utracić) i RTO (jak długo trwa przywrócenie), wykonywać backupy PITR
oraz regularnie odtwarzać je w izolowanym środowisku. Backup bez testu restore
jest jedynie nadzieją.

W multi-region active-passive ruch przełącza się po potwierdzeniu zdrowia danych
i zależności. Active-active dla inventory wymaga znacznie trudniejszego modelu
własności lub globalnego konsensusu; konflikt „ostatniej sztuki” nie znika dzięki
dwóm regionom. W wielu sklepach lepszy jest regionalny owner stocku niż pozorna
dostępność zapisu wszędzie.

## Najważniejsze alternatywy i trade-offy

| Decyzja w laboratorium | Kiedy jest rozsądna | Kiedy rozważyć alternatywę |
| --- | --- | --- |
| modularny monolit | jeden zespół, wspólna transakcja, szybka nauka domeny | niezależne tempo zmian/skala i dojrzałe granice |
| PostgreSQL jako source of truth | silne invarianty i relacyjne zapytania | ekstremalny wolumen z jasno ograniczonym access patternem |
| pessimistic lock inventory | krótka transakcja i umiarkowana konkurencja | hot SKU: conditional update, token allocation lub kolejka |
| polling outboxa | prostota i umiarkowany throughput | broker/CDC przy wielu konsumentach i dużym backlogu |
| OpenSearch | faceting, relevance, typo tolerance, duży katalog | PostgreSQL FTS dla prostszego i mniejszego katalogu |
| koszyk w PostgreSQL | trwałość i łatwiejsza diagnostyka | Redis przy ogromnym churnie, z zaplanowaną utratą/fallbackiem |
| orkiestracja checkoutu | czytelny owner procesu i kompensacji | choreografia tylko przy małej liczbie luźno związanych reakcji |

Technologii nie wybiera się po nazwie problemu. Najpierw określamy niezmiennik,
access pattern, akceptowalną niespójność i failure mode, a dopiero później bazę,
brokera lub framework.

## Failure modes

| Awaria | Oczekiwane zachowanie |
| --- | --- |
| klient traci odpowiedź checkoutu | retry z tym samym kluczem zwraca ten sam wynik |
| dwa checkouty walczą o ostatnią sztukę | jeden lock/conditional update wygrywa, drugi dostaje konflikt |
| PSP timeout | stan pozostaje nieznany/pending; reconciliation ustala wynik |
| duplikat callbacka PSP | provider event ID i maszyna stanów usuwają drugi efekt |
| rezerwacja wygasa podczas płatności | jedna transakcja wygrywa lock; nie powstaje paid order bez stocku |
| broker niedostępny | biznesowy commit pozostaje; outbox retry z backoffem |
| crash po publikacji | możliwy duplikat; consumer deduplikuje event ID |
| OpenSearch niedostępny | katalog źródłowy działa; indeks ma backlog i może być odbudowany |
| e-mail provider niedostępny | zamówienie pozostaje ważne; retry/DLQ i alert |
| replica lag | krytyczny odczyt po zapisie idzie do writera |

## Observability

### SLI i przykładowe SLO

Wartości są celem ćwiczenia, nie pomiarem projektu:

- 99,9% checkoutów bez błędu systemowego w oknie 30 dni;
- p95 checkoutu poniżej 800 ms bez czasu interakcji użytkownika z PSP;
- brak naruszeń invariantów inventory;
- 99% eventów outbox opublikowanych w 30 s;
- 99% dokumentów search nie starszych niż 60 s od source of truth.

### Metryki

- checkout attempts/success/failure z niskokardynalnym reason;
- lock wait, deadlock i optimistic-lock conflict;
- active/expired reservations oraz wiek najstarszej aktywnej rezerwacji;
- payment pending age i reconciliation mismatch;
- outbox backlog, oldest-event age, attempts oraz DEAD count;
- consumer lag, duplicate count i processing latency;
- search indexing lag oraz reconciliation drift.

`BusinessMetrics` wykonuje obecnie histogram czasu checkoutu, oczekiwania na
blokadę inventory i publikacji batcha outbox. Liczniki rozróżniają wynik
płatności, publikację, odrzucony webhook oraz rekoncyliację zakończoną od nadal
nieznanego wyniku PSP. Gauges pokazują backlog i wiek najstarszego eventu.
`@Observed` tworzy osobne obserwacje checkoutu i outboxa, a Micrometer Tracing
umieszcza `traceId`/`spanId` w kontekście logowania. Produkcyjny eksport OTLP oraz
propagacja correlation/causation ID w payloadzie brokera pozostają granicą
wdrożeniową.

Nie umieszczaj `userId`, `orderId`, SKU ani event ID w labelach metryk. Należą do
logów i trace'ów. Trace powinien łączyć checkout, payment callback i publikację
outbox przez correlation/causation ID.

### Alert i runbook

Alertuj na wiek, nie tylko rozmiar backlogu. Pierwsze kroki runbooka:

1. rozróżnij awarię producenta, bazy, brokera i konsumenta;
2. zatrzymaj automatyczny replay, jeśli efekt może nie być idempotentny;
3. określ najstarszy event i dotknięte typy agregatów;
4. usuń przyczynę, wykonaj mały canary replay;
5. porównaj projekcję z source of truth i dopiero zwiększ batch.

## Security i trust boundaries

- klient nie może podać user ID zamiast principal z uwierzytelnienia;
- endpointy admina i mock PSP wymagają roli administratora;
- produkcyjny webhook PSP jest osobną trust boundary i wymaga podpisu;
- ceny, rabaty, shipping i total są liczone po stronie serwera;
- rate limiting powinien chronić login, koszyk, checkout i webhook;
- logi nie zawierają bearer tokenów, sekretów, pełnych adresów ani danych karty;
- sekrety są rotowane przez secret manager, nie plik `application.yml`;
- audit log operacji administracyjnych commitowany jest razem ze zmianą;
- retencja i anonimizacja PII muszą zachować wymagane prawnie dane finansowe.

Implementacja webhooka używa HMAC SHA-256, constant-time comparison, pięciominutowego
okna czasowego i unikalnego `providerEventId`. Sekret z domyślnej konfiguracji
jest wyłącznie lokalnym fallbackiem; środowisko inne niż edukacyjne musi ustawić
`PAYMENT_WEBHOOK_SECRET`. Endpointy H2 console, OpenAPI i mock PSP nie powinny być
publicznie dostępne w profilu produkcyjnym.

## Ewolucja schematu i kompatybilny rollout

Flyway jest właścicielem DDL, a Hibernate działa z `ddl-auto=validate`. `V1`
opisuje jawny baseline, natomiast `V2` dodaje lease outboxa. Kolejne zmiany nie
edytują zastosowanej migracji — dodają nowy plik. Rollout zero-downtime stosuje:

1. **expand** — dodanie nullable kolumny/tabeli/indeksu kompatybilnego ze starą aplikacją;
2. wdrożenie kodu zapisującego oba formaty lub wykonanie kontrolowanego backfillu;
3. przełączenie odczytu i obserwację błędów/driftu;
4. **contract** — usunięcie starego pola dopiero po wycofaniu wszystkich starych instancji.

Duży indeks buduje się online/concurrently, a duży backfill porcjami z limitem,
checkpointem i mierzoną presją na replikację. Rollback aplikacji nie zawsze
oznacza rollback migracji; destrukcyjne DDL powinno być osobnym, opóźnionym krokiem.

## Test strategy

Szybka suite bez infrastruktury obejmuje:

- invarianty encji i maszyny stanów;
- kontrakty workera i stabilne klucze idempotencji;
- uruchomienie migracji Flyway i walidację mapowań na H2;
- start całego kontekstu API;
- pełny lokalny checkout i idempotentny retry;
- Problem Details oraz generowany kontrakt OpenAPI;
- HMAC/timestamp webhooka, reconciliation, pricing i rounding pieniędzy.

Profil `infrastructure-tests` używa PostgreSQL 16 Testcontainers i dowodzi:

- że dwa równoległe rezerwowania ostatniej sztuki dają dokładnie jednego zwycięzcę;
- rzeczywistą semantykę pessimistic lock;
- unique constraints idempotencji i payment provider event;
- że dwaj workerzy claimują różne rekordy przez `SKIP LOCKED`;
- migracje Flyway i krytyczne unique constraints na prawdziwym dialekcie.

H2 jest użyteczny do szybkiego feedbacku, ale nie jest dowodem zachowania
PostgreSQL. Test kontekstu dowodzi tylko, że aplikacja startuje.

## Uruchomienie

```powershell
cd system-design/ecommerce

# wszystkie szybkie testy trzech modułów
.\mvnw.cmd --batch-mode --no-transfer-progress test

# pełny build
.\mvnw.cmd --batch-mode --no-transfer-progress verify

# kontrakty PostgreSQL; wymaga działającego Dockera
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify

# środowisko demonstracyjne
docker compose up -d postgres-writer redis opensearch
```

Domyślna aplikacja używa H2 i mocków, dlatego nie wymaga lokalnych usług.
`postgres-replica-1/2` w compose nie konfigurują streaming replication — są
wyłącznie nazwanymi instancjami demonstracyjnymi i nie dowodzą read replica lag.

OpenAPI jest dostępne lokalnie pod `/v3/api-docs`, Swagger UI pod
`/swagger-ui/index.html`, Prometheus pod `/actuator/prometheus`. Profil
PostgreSQL uruchamia Flyway przed Hibernate; aplikacja odmawia startu, jeżeli
mapowanie encji nie zgadza się ze schematem. Profil `production` wyłącza H2
console i dokumentację interaktywną oraz włącza publisher i reconciliation;
należy go łączyć z właściwym profilem infrastruktury i secret managerem.

## Kryteria dalszego wydzielenia usług

Wydziel domenę dopiero wtedy, gdy istnieje przynajmniej jeden mierzalny powód:

- inny profil skalowania lub niezależny SLO;
- odrębny ownership zespołu i cykl wdrożeń;
- izolacja awarii o realnej wartości;
- wymóg bezpieczeństwa lub danych;
- obecny modularny monolit osiągnął zmierzony limit.

Kosztem wydzielenia są kontrakty, observability, retry, idempotencja, eventual
consistency, replay, migracje i operacje. Diagram z większą liczbą prostokątów
nie jest automatycznie lepszym systemem.
