# Wzorce API i granic zdalnych

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#api)

Wzorce interakcji oraz kontraktu klient–serwer. **12 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [ETag, przyjęcie i polling długiej operacji](../../labs/04-api.md). Studium w
Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** API jest kontraktem między niezależnymi stronami. Sam kształt JSON nie opisuje
wszystkich gwarancji: potrzebne są semantyka retry, czas trwania operacji, wersje, błędy i zakres uprawnień.
Odróżniaj przyjęcie zadania od wyniku oraz walidator wersji od credentiala. Kontrakty HTTP poniżej są
przykładami rozmowy; szkice Java pokazują logikę bez deklaracji działającego endpointu.

## Spis kart

- [Asynchronous Request–Reply / Long-running Operation](#asynchronous-requestreply--long-running-operation)
- [Webhook / Callback Delivery](#webhook--callback-delivery)
- [Conditional Request / ETag / If-Match](#conditional-request--etag--if-match)
- [Problem Details / Error Envelope](#problem-details--error-envelope)
- [Cursor Pagination / Continuation Token](#cursor-pagination--continuation-token)
- [Tolerant Reader / Robust Contract Evolution](#tolerant-reader--robust-contract-evolution)
- [Idempotent Method / Safe Method](#idempotent-method--safe-method)
- [Batch API / Bulk Operation](#batch-api--bulk-operation)
- [Hypermedia / HATEOAS / Link Relations](#hypermedia--hateoas--link-relations)
- [Long Polling / Server-Sent Events / WebSocket](#long-polling--server-sent-events--websocket)
- [Consumer-owned Contract / API Versioning](#consumer-owned-contract--api-versioning)
- [Intercepting Filter / Middleware / Interceptor](#intercepting-filter--middleware--interceptor)

## Asynchronous Request–Reply / Long-running Operation

**Intuicja:** Długie tworzenie raportu nie musi utrzymywać jednego połączenia HTTP do końca. Async
Request–Reply przyjmuje zamiar, oddaje uchwyt i pozwala później sprawdzić wynik. Uchwyt ma własny lifecycle, a
retry przyjęcia powinno wracać do tej samej operacji, jeśli oznacza ten sam zamiar.

**Problem i mechanizm:** Długie zadanie zwraca uchwyt operacji, a wynik jest odczytywany później bez
utrzymywania jednego połączenia.

**Role i współpraca:** Submit tworzy operację, worker ją wykonuje, status endpoint raportuje postęp i
terminalny wynik. Scope chroni odczyt i wynik, retencja określa czas dostępności.

**Przykład:** POST /exports zwraca 202 i Location /operations/42; GET pokazuje RUNNING, SUCCEEDED lub FAILED.

**Mechanizm w kodzie:** Przykładowy kontrakt HTTP: pokazuje żądanie i odpowiedź z objaśnionymi danymi. W
module nie uruchamiamy tego endpointu.

```http
POST /exports HTTP/1.1
Idempotency-Key: export-42

HTTP/1.1 202 Accepted
Location: /operations/123

GET /operations/123 HTTP/1.1
```

**Konkretny przypadek użycia:** Eksport raportu trwający pięć minut

- **Wejście:** POST tworzy eksport z kluczem żądania; połączenie HTTP ma krótki limit czasu.
- **Przebieg:** API zwraca 202 i Location operacji, worker aktualizuje jej stan, klient odpytuje ten zasób.
- **Oczekiwany wynik:** Ponowiony POST wskazuje ten sam eksport, a sukces udostępnia wynik.
- **Błąd lub granica:** 202 oznacza przyjęcie do obsługi, nie zakończenie; trzeba określić retencję,
  anulowanie i trwałość przyjęcia.

**Kiedy zastosować:** Dla długich zadań o jawnym stanie. Status 202 nie dowodzi wykonania i nie zastępuje
trwałego przekazania pracy do workera.

**Koszt i pułapka:** Przyjęcie nie oznacza wykonania; potrzebne są deadline zadania, retencja wyniku,
idempotencja startu i autoryzacja pollingu.

**Rozwinięcie i ślad wykonania:** LongRunningOperations oddziela accepted od running i terminalnego wyniku.
Submit z tym samym kluczem wraca do tej samej operacji, więc timeout odpowiedzi nie uruchamia drugiego
eksportu. Polling wymaga scope tenanta; sam nieprzewidywalny albo znany identyfikator nie jest regułą
autoryzacji.

**Ćwiczenie sprawdzające:** Opisz retry POST po timeout i odczyt operacji innego użytkownika.

## Webhook / Callback Delivery

**Intuicja:** Klient nie musi stale pytać o zmianę, jeśli dostawca potrafi powiadomić go na zarejestrowany
adres. Webhook jest odwróceniem kierunku wywołania, nie niezawodnym jednorazowym telefonem. Utrata odpowiedzi
i retry powodują duplikaty, więc bezpieczeństwo odbioru oraz idempotencja są częścią kontraktu.

**Problem i mechanizm:** Producent wywołuje zarejestrowany endpoint odbiorcy po wystąpieniu zdarzenia.

**Role i współpraca:** Producent podpisuje dostawę według protokołu, odbiorca waliduje ją i trwale przyjmuje,
handler przetwarza event. Rejestracja URL ma własną granicę zaufania.

**Przykład:** PaymentCaptured trafia z eventId, podpisem i timestamp; odbiorca weryfikuje i zapisuje inbox
przed 2xx.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
verified = signatures.verify(rawBody, headers, acceptedTimeWindow);
transaction.run(() -> inbox.storeIfAbsent(verified.eventId(), verified.body()));
return http.ok();
// Worker przetworzy trwały inbox zgodnie z osobną polityką retry.
```

**Konkretny przypadek użycia:** Powiadomienie partnera o płatności

- **Wejście:** Event payment42 ma zostać dostarczony na skonfigurowany endpoint.
- **Przebieg:** Nadawca podpisuje treść, wysyła z timeout i ponawia według polityki; odbiorca deduplikuje
  eventId.
- **Oczekiwany wynik:** Dwie dostawy powodują jedną zmianę biznesową po stronie odbiorcy.
- **Błąd lub granica:** Utrata odpowiedzi po przetworzeniu oznacza niepewny wynik; webhook nie obiecuje
  jednego wywołania ani globalnej kolejności.

**Kiedy zastosować:** Dla powiadomień między systemami. Szybkie 2xx może oznaczać jedynie trwałe enqueue,
jeśli dalsze wykonanie jest asynchroniczne.

**Koszt i pułapka:** Powtórzenia, replay i SSRF rejestracji URL wymagają ochrony; szybkie 2xx może oznaczać
tylko trwałe przyjęcie do kolejki.

**Rozwinięcie i ślad wykonania:** Odbiorca najpierw weryfikuje podpis oraz zakres replay, potem przyjmuje
event trwale i zwraca uzgodnione potwierdzenie. Retry producenta po utracie 2xx może przynieść tę samą
wiadomość. Deduplikacja powinna korzystać ze stabilnego eventId, a nie zmieniającej się sygnatury próby
dostarczenia.

**Ćwiczenie sprawdzające:** Dostarcz ten sam webhook dwa razy i odrzuć stary podpisany replay.

## Conditional Request / ETag / If-Match

**Intuicja:** Edytor klienta może pracować na nieaktualnym dokumencie. Conditional Request pozwala powiedzieć
„zapisz tylko, jeśli nadal widzę tę samą wersję”. ETag identyfikuje reprezentację, a If-Match warunkuje
zmianę; store musi porównać i zapisać atomowo, inaczej sam nagłówek nie zapobiegnie lost update.

**Problem i mechanizm:** Klient warunkuje zapis zgodnością wersji albo odczyt zgodnością reprezentacji.

**Role i współpraca:** GET wydaje walidator, klient go odsyła, adapter mapuje warunek na kontrolę wersji. To
niezależne od prawa użytkownika do zasobu.

**Przykład:** GET zwraca ETag "v7"; PUT z If-Match "v7" jest odrzucany po cudzej aktualizacji, a If-None-Match
może dać 304.

**Mechanizm w kodzie:** Przykładowy kontrakt HTTP: pokazuje żądanie i odpowiedź z objaśnionymi danymi. W
module nie uruchamiamy tego endpointu.

```http
PUT /documents/d1 HTTP/1.1
If-Match: "v7"
Content-Type: application/json

{"body":"updated"}

HTTP/1.1 412 Precondition Failed
```

**Konkretny przypadek użycia:** Równoczesna edycja dokumentu

- **Wejście:** Dwaj klienci odczytali dokument z ETag v7.
- **Przebieg:** Pierwszy zapis z If-Match v7 tworzy v8, drugi z tym samym warunkiem dostaje 412.
- **Oczekiwany wynik:** Drugi klient nie nadpisuje cicho zmiany pierwszego.
- **Błąd lub granica:** Porównanie i zapis muszą być atomowe; oddzielny odczyt wersji w kontrolerze pozostawia
  wyścig.

**Kiedy zastosować:** Dla współbieżnej edycji i kontrolowanych odczytów. Nie interpretuj ETag jako tokenu
autoryzacji ani weak validator jako dowolnej zgody na zapis.

**Koszt i pułapka:** ETag reprezentacji nie zawsze jest wersją całego agregatu; semantyka weak i strong
validator jest różna.

**Rozwinięcie i ślad wykonania:** ConditionalDocuments porównuje dokładny strong ETag i publikuje nową wersję
pod tym samym monitorem. Drugi klient z v0 dostaje PreconditionFailed po pierwszym zapisie v1. Jest to model
odpowiedzi 412; nie implementuje całej gramatyki If-Match z listą wartości i gwiazdką.

**Ćwiczenie sprawdzające:** Zaprojektuj konflikt dwóch edytujących klientów i odpowiedź 412.

## Problem Details / Error Envelope

**Intuicja:** Klient potrzebuje odróżnić niepoprawną walutę od chwilowej awarii, bez parsowania polskiego
tekstu komunikatu. Problem Details daje uzgodnioną kopertę błędu z maszynowym typem i czytelnym objaśnieniem.
Dodatkowe pola mogą opisywać naruszenia, ale nie powinny wyciekać prywatne szczegóły infrastruktury.

**Problem i mechanizm:** Błędy mają stabilny maszynowy kontrakt z bezpiecznym opisem i identyfikatorem
diagnostycznym.

**Role i współpraca:** Status opisuje kategorię HTTP, type rodzaj problemu, title i detail treść dla
człowieka, instance konkretny przypadek. Rozszerzenia mają własny kontrakt.

**Przykład:** Niepoprawna waluta daje 400 z type, title, status i listą naruszeń bez stack trace oraz
sekretów.

**Mechanizm w kodzie:** Przykładowy kontrakt HTTP: pokazuje żądanie i odpowiedź z objaśnionymi danymi. W
module nie uruchamiamy tego endpointu.

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{"type":"https://api.example.test/problems/invalid-currency",
 "title":"Invalid currency","status":400,
 "detail":"Choose a supported currency code."}
```

**Konkretny przypadek użycia:** Spójny opis błędu walidacji

- **Wejście:** Żądanie zamówienia ma ujemną liczbę sztuk.
- **Przebieg:** API zwraca właściwy status i stabilny typ problemu, opis oraz listę naruszonych pól.
- **Oczekiwany wynik:** Klient rozpoznaje kategorię błędu bez parsowania ludzkiego komunikatu.
- **Błąd lub granica:** Stack trace, SQL i sekrety nie powinny trafić do odpowiedzi; format problemu nie
  zastępuje semantyki statusów.

**Kiedy zastosować:** Dla stabilnej obsługi błędów przez różnych klientów. Zmienny tekst diagnostyczny nie
powinien być jedynym identyfikatorem decyzji o retry.

**Koszt i pułapka:** Klient nie powinien parsować lokalizowanego tekstu; status i kod muszą odróżniać retry od
błędu trwałego.

**Rozwinięcie i ślad wykonania:** Stabilny typ problemu powinien nadawać się do decyzji klienta, a detail do
objaśnienia konkretnego przypadku. Nie zmuszaj klienta do parsowania tekstu „za mało środków”. Dodaj
correlationId jako bezpieczny punkt diagnostyki, bez ujawniania pełnego stack trace lub sekretów środowiska.

**Ćwiczenie sprawdzające:** Zdefiniuj osobno błąd walidacji, konflikt wersji i timeout zależności.

## Cursor Pagination / Continuation Token

**Intuicja:** Podczas przewijania klient potrzebuje kontynuować ten sam porządek, a nie zgadywać fizyczne
położenie wiersza. Cursor reprezentuje stan kontynuacji i może zawierać ostatni klucz oraz tożsamość filtrów.
Nieprzezroczysty token pozwala zmienić implementację bez przenoszenia wiedzy o SQL do klienta.

**Problem i mechanizm:** API zwraca nieprzezroczysty uchwyt postępu ze stabilnym porządkiem i związaniem z
filtrami.

**Role i współpraca:** Serwer koduje i waliduje cursor, zapytanie stosuje keyset i limit, odpowiedź zwraca
następny uchwyt. Integralność i scope są niezależne od Base64.

**Przykład:** nextCursor koduje createdAt oraz id i jest podpisany albo bezpiecznie walidowany; następna
strona używa keyset query.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Cursor cursor = cursors.decodeAndValidate(token, tenantId, filterHash);
List<OrderSummary> page = orders.after(cursor.lastKey(), pageSize);
String next = page.isEmpty() ? null
        : cursors.encode(tenantId, filterHash, page.getLast().key());
```

**Konkretny przypadek użycia:** Przegląd historii zamówień

- **Wejście:** Lista jest sortowana po createdAt i id, w trakcie dochodzą nowe wpisy.
- **Przebieg:** Cursor koduje ostatnią parę kluczy i zakres tenant A; kolejne zapytanie używa tej samej
  kolejności.
- **Oczekiwany wynik:** Remisy czasu nie powodują pominięcia rekordu tylko dlatego, że znacznik czasu jest
  identyczny.
- **Błąd lub granica:** Zmiana pól sortowania podczas przejścia może przesuwać rekordy; pełny snapshot wymaga
  osobnej gwarancji.

**Kiedy zastosować:** Dla stronicowania dużych kolekcji. Zmiana filtrów lub klucza sortowania wymaga
określonej semantyki, a nie przypadkowego wykorzystania starego tokenu.

**Koszt i pułapka:** Base64 nie chroni przed zmianą parametrów; cursor nie daje sam z siebie snapshotu całej
kolekcji.

**Rozwinięcie i ślad wykonania:** Token kontynuacji powinien być związany z porządkiem i filtrem. Jeśli klient
zmieni status zamówień między stronami, stary cursor może już nie opisywać tej samej sesji odczytu. Ustal
regułę odrzucenia, nowego zapytania albo snapshotu, zamiast pozostawić przypadkowe duplikaty i pominięcia.

**Ćwiczenie sprawdzające:** Zmień filtr pomiędzy stronami i ustal odrzucenie lub semantykę nowej sesji.

## Tolerant Reader / Robust Contract Evolution

**Intuicja:** Dodanie opisu produktu nie powinno wymagać natychmiastowego wdrożenia wszystkich klientów.
Tolerant Reader dopuszcza określone rozszerzenia, ale nie ignoruje znaczenia wymaganych danych. Nieznane pole
i nieznany status płatności są różnymi problemami; tolerancja powinna być selektywna.

**Problem i mechanizm:** Odbiorca ignoruje dozwolone dodatki, ale rygorystycznie waliduje pola wpływające na
znaczenie i bezpieczeństwo.

**Role i współpraca:** Dekoder czyta potrzebne pola, walidator sprawdza znaczenie, wersje kontraktu określają
reguły ewolucji. Nieobsługiwany istotny wariant ma jawny wynik.

**Przykład:** Nowe opcjonalne pole description nie psuje starego klienta; nieznany status płatności nie
zamienia się domyślnie w PAID.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
JsonObject body = parser.parse(payload);
String id = body.requireString("id");
PaymentStatus status = statuses.requireKnown(body.requireString("status"));
// Nieznane, nieistotne pole description może zostać pominięte.
```

**Konkretny przypadek użycia:** Dodanie pola odpowiedzi

- **Wejście:** Provider dodaje opcjonalne estimatedDelivery, starszy klient zna tylko status.
- **Przebieg:** Reader ignoruje nieznane pola, ale nadal waliduje wymagane pola i znaczenie znanych wartości.
- **Oczekiwany wynik:** Starszy klient działa, nowy wykorzystuje dodatkową informację.
- **Błąd lub granica:** Nieznana wartość enum może wymagać jawnego fallback; tolerancja nie pozwala ignorować
  błędnej waluty albo kwoty.

**Kiedy zastosować:** Dla kompatybilnego rozwoju publicznych danych. Bezkrytyczne przyjmowanie wszystkiego
może ukryć błąd i naruszyć wymagania bezpieczeństwa.

**Koszt i pułapka:** Bezkrytyczna tolerancja może maskować błąd i obniżać bezpieczeństwo; ewolucja wymaga
testów wersji.

**Rozwinięcie i ślad wykonania:** Ignorowanie nowych pól jest zwykle innym ryzykiem niż ignorowanie nowego
statusu płatności. Odbiorca może nadal działać bez description, ale unknown status nie uprawnia go do
założenia PAID. Tolerancja powinna być selektywna i testowana na znaczeniu, nie wyłącznie na sukcesie
parsowania.

**Ćwiczenie sprawdzające:** Dodaj pole i nową wartość enum, porównaj skutki dla starego klienta.

## Idempotent Method / Safe Method

**Intuicja:** Safe i idempotent odpowiadają na inne pytania. Safe oznacza, że żądana semantyka nie zmienia
stanu biznesowego. Idempotent pozwala zmieniać stan, lecz powtórzenie ma taki sam zamierzony efekt jak
wykonanie raz. Odpowiedzi oraz logi mogą się przy tym różnić.

**Problem i mechanizm:** Kontrakt określa, czy powtórzenie ma taki sam zamierzony efekt oraz czy metoda ma
zmieniać stan biznesowy.

**Role i współpraca:** Metoda HTTP i kontrakt operacji określają efekt, serwer realizuje go zgodnie z tą
semantyką. POST może mieć dodatkową idempotencję przez klucz, nie zmieniając ogólnej roli metody.

**Przykład:** PUT ustawia adres na konkretną wartość; ponowne PUT nie dopisuje kolejnego adresu. GET nie
powinien pobierać płatności.

**Mechanizm w kodzie:** Przykładowy kontrakt HTTP: pokazuje żądanie i odpowiedź z objaśnionymi danymi. W
module nie uruchamiamy tego endpointu.

```http
PUT /profiles/42/address HTTP/1.1
Content-Type: application/json

{"city":"Warsaw"}

```

**Konkretny przypadek użycia:** Semantyka ponowionego DELETE

- **Wejście:** Klient usuwa zasób R, odpowiedź ginie i klient powtarza request.
- **Przebieg:** Pierwszy DELETE usuwa, drugi utrzymuje zasób nieobecny; status drugiej odpowiedzi może się
  różnić.
- **Oczekiwany wynik:** Powtórzenie nie odtwarza zasobu i nie zwielokrotnia żądanego efektu.
- **Błąd lub granica:** Idempotencja nie oznacza identycznej odpowiedzi; GET nie powinien wykonywać żądanej
  operacji pobrania opłaty.

**Kiedy zastosować:** Gdy projektujesz retry i pośredników API. Nie używaj GET do pobrania opłaty ani nie
zakładaj, że każdy POST jest bezpieczny do powtórzenia.

**Koszt i pułapka:** Idempotencja efektu nie wymaga identycznej odpowiedzi i nie znaczy braku logów; POST może
mieć idempotency key.

**Rozwinięcie i ślad wykonania:** Dwa PUT ustawiające ten sam stan mogą być idempotentne mimo innego czasu
odpowiedzi i wpisów logu. Dwa POST increment bez klucza tworzą dwa różne efekty. Bezpieczna metoda nie powinna
żądać zmiany stanu biznesowego; idempotentna może go zmieniać, więc pojęcia nie są równoważne.

**Ćwiczenie sprawdzające:** Porównaj dwa DELETE oraz dwa POST increment bez deduplikacji.

## Batch API / Bulk Operation

**Intuicja:** Wiele podobnych operacji można przekazać jedną paczką, zmniejszając koszt round-trip. Batch API
musi jednak powiedzieć, czy działa wszystko-albo-nic, czy dopuszcza sukces części. Bez tego klient nie wie,
które pozycje ponowić po błędzie i może zdublować już zatwierdzone efekty.

**Problem i mechanizm:** Wiele operacji przechodzi przez jedną granicę z jawną atomowością oraz wynikiem
każdej pozycji.

**Role i współpraca:** Paczka ma limit i identyfikatory elementów, wynik wiąże każdy element z decyzją.
Transakcja lub niezależna idempotencja określa granicę potwierdzenia.

**Przykład:** Import 100 SKU zwraca sukces albo błąd per SKU i określa, czy paczka jest all-or-nothing.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<ItemResult> results = new ArrayList<>();
for (ImportItem item : request.items()) {
    results.add(importOnce(item.idempotencyKey(), item));
}
return new BatchResult(results); // Jawny partial success.
```

**Konkretny przypadek użycia:** Import stu klientów przez API

- **Wejście:** Batch zawiera 100 niezależnych pozycji z własnymi kluczami idempotencji.
- **Przebieg:** API ogranicza rozmiar i zwraca wynik każdej pozycji, w tym trzy błędy walidacji.
- **Oczekiwany wynik:** Klient ponawia tylko nieukończone pozycje bez dublowania 97 udanych zapisów.
- **Błąd lub granica:** Częściowy sukces musi być jawny; obietnica all-or-nothing wymaga innej granicy
  transakcji i limitów.

**Kiedy zastosować:** Dla wielu małych operacji o jasno opisanej atomowości. Duży batch podnosi czas, pamięć i
zakres rollback zamiast bez końca obniżać koszt.

**Koszt i pułapka:** Duża paczka zwiększa pamięć, czas i zakres rollback; retry całości może powielić
częściowy sukces.

**Rozwinięcie i ślad wykonania:** Wynik paczki powinien identyfikować każdą pozycję stabilnym ID i jasno
wskazywać, czy część mogła zostać zatwierdzona. Przy partial success retry całej paczki wymaga deduplikacji.
Zbyt wielki batch może przekroczyć deadline, nawet jeśli pojedyncza pozycja jest szybka i poprawna.

**Ćwiczenie sprawdzające:** Zdefiniuj limit paczki i ponowienie tylko nieudanych elementów.

## Hypermedia / HATEOAS / Link Relations

**Intuicja:** Klient może otrzymywać nie tylko dane, lecz także nazwy dostępnych przejść. Hypermedia opisuje
je linkami i relacjami, zamiast wymagać konstruowania wszystkich adresów w kodzie klienta. Link jest
obserwacją możliwości z chwili odczytu, więc operacja nadal sprawdza aktualny stan i prawo wykonania.

**Problem i mechanizm:** Odpowiedź opisuje dostępne przejścia nazwanymi relacjami zamiast wymagać od klienta
konstruowania wszystkich adresów.

**Role i współpraca:** Reprezentacja zawiera relacje, klient wybiera znaną relację, serwer rozstrzyga komendę.
Model domenowy pozostaje właścicielem legalnych przejść.

**Przykład:** Zamówienie NEW ma link pay, a PAID ma receipt; serwer i tak waliduje przejście przy komendzie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<Link> links = new ArrayList<>();
if (order.canPay()) links.add(new Link("pay", "/orders/42/payment"));
if (order.isPaid()) links.add(new Link("receipt", "/orders/42/receipt"));
return new OrderRepresentation(order.summary(), links);
```

**Konkretny przypadek użycia:** Nawigacja po zwrocie zamówienia

- **Wejście:** Odczyt order42 w stanie SHIPPED pozwala rozpocząć zwrot, po terminie już nie.
- **Przebieg:** Odpowiedź udostępnia link o relacji return tylko według bieżącego kontraktu nawigacji.
- **Oczekiwany wynik:** Klient odnajduje adres operacji bez składania ścieżek z identyfikatora.
- **Błąd lub granica:** Link nie jest autoryzacją ani rezerwacją stanu; serwer ponownie sprawdza uprawnienia i
  termin przy wywołaniu.

**Kiedy zastosować:** Gdy odkrywanie operacji i luźniejsze powiązanie z adresami ma wartość. Nie traktuj
starego linku pay jako nieodwołalnego prawa do ponownej płatności.

**Koszt i pułapka:** Obecność linku nie zastępuje autoryzacji ani kontroli wersji; między odczytem a komendą
stan może się zmienić.

**Rozwinięcie i ślad wykonania:** Link pay jest obserwacją dozwolonej akcji w momencie odczytu, a nie trwałym
uprawnieniem. Po zmianie stanu serwer ponownie sprawdza regułę i wersję. Dzięki temu stary widok klienta nie
wymusza nielegalnego przejścia tylko dlatego, że zachował poprzednią odpowiedź z linkiem.

**Ćwiczenie sprawdzające:** Usuń pay po opłaceniu i sprawdź odrzucenie starego linku klienta.

## Long Polling / Server-Sent Events / WebSocket

**Intuicja:** Status zmieniający się co chwilę nie zawsze warto pobierać krótkim pollingiem. Long Polling
utrzymuje oczekiwanie requestu, SSE daje jednokierunkowy strumień serwera, WebSocket dwukierunkową sesję. Są
to mechanizmy transportu; reconnect i historia dostawy wymagają dodatkowego kontraktu.

**Problem i mechanizm:** Aktualizacje dostarcza się przez ponawiany długi odczyt, jednokierunkowy strumień lub
dwukierunkową sesję.

**Role i współpraca:** Połączenie przenosi aktualizacje, eventId wskazuje postęp, klient wznawia po zerwaniu.
Retencja i deduplikacja określają, co oznacza utracony fragment.

**Przykład:** Status operacji idzie przez SSE z eventId i kontrolowanym wznowieniem; czat wybiera WebSocket
dla komend i odpowiedzi.

**Mechanizm w kodzie:** Przykładowy kontrakt HTTP: pokazuje żądanie i odpowiedź z objaśnionymi danymi. W
module nie uruchamiamy tego endpointu.

```http
GET /operations/123/events HTTP/1.1
Accept: text/event-stream
Last-Event-ID: 17

HTTP/1.1 200 OK
Content-Type: text/event-stream

id: 18
event: progress
data: {"percent":60}
```

**Konkretny przypadek użycia:** Aktualizacje statusu eksportu

- **Wejście:** Przeglądarka potrzebuje jednostronnych postępów, a czat wymaga komunikacji w obie strony.
- **Przebieg:** SSE obsługuje postęp, WebSocket czat, long polling pozostaje wariantem prostszego transportu.
- **Oczekiwany wynik:** Dobór protokołu odpowiada kierunkowi komunikacji i wymaganiom opóźnienia.
- **Błąd lub granica:** Reconnect może zgubić albo powtórzyć event; wznowienie wymaga identyfikatorów i
  retencji, niezależnie od otwartego połączenia.

**Kiedy zastosować:** Według kierunków komunikacji i wymaganego modelu infrastruktury. Żaden wariant sam nie
staje się trwałą kolejką z exactly-once.

**Koszt i pułapka:** To mechanizmy transportu, nie gwarancja trwałości; reconnect wymaga historii, limitów i
identyfikacji ostatniego zdarzenia.

**Rozwinięcie i ślad wykonania:** Reconnect powinien określać ostatni przetworzony event, dopuszczalną
retencję oraz limit klienta. Po utracie połączenia nie wiadomo, czy ostatnia wiadomość została odebrana, więc
potrzebna jest deduplikacja odtworzenia. Dwukierunkowy kanał WebSocket sam nie daje trwałej kolejki ani
autoryzacji każdego zasobu.

**Ćwiczenie sprawdzające:** Zerwij połączenie po wysyłce przed odbiorem i zaprojektuj replay.

## Consumer-owned Contract / API Versioning

**Intuicja:** Publiczne API żyje dłużej niż pojedynczy deployment klienta. Wersjonowanie wyznacza, jak długo
różne oczekiwania mogą współistnieć i jak migrować odbiorców. Consumer-owned Contract pomaga uchwycić realne
potrzeby klienta, ale nie przenosi automatycznie całej własności modelu providera na jednego konsumenta.

**Problem i mechanizm:** Publiczne kontrakty mają właściciela, zakres kompatybilności i okres życia wersji.

**Role i współpraca:** Provider utrzymuje kontrakty, konsument deklaruje użycie, telemetry pokazuje
pozostających klientów starej wersji. Zmiana znaczenia pola jest istotniejsza niż kosmetyczna ścieżka v2.

**Przykład:** Zmiana jednostki amount wymaga nowego kontraktu albo nowego jednoznacznego pola, nie cichego
podmienienia semantyki.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record PriceV1(long amountInCents) { }
record PriceV2(BigDecimal amount, String currency) { }
// Adapter v1 zachowuje stare jednostki; nie zmieniamy ich po cichu.
```

**Konkretny przypadek użycia:** Wycofanie starej reprezentacji API

- **Wejście:** Konsumenci A i B używają v1; v2 zmienia znaczenie pola total.
- **Przebieg:** Kontrakty opisują realne użycia, provider utrzymuje obie reprezentacje przez uzgodnione okno
  migracji.
- **Oczekiwany wynik:** Niekompatybilna semantyka nie trafia po cichu pod tę samą wersję.
- **Błąd lub granica:** Sam numer w URL nie zapewnia kompatybilności; trzeba znać użycie, termin wycofania i
  ścieżkę migracji.

**Kiedy zastosować:** Gdy potrzebna jest niekompatybilna ewolucja. Zaplanuj dane i okres przejściowy, a nie
tylko drugi routing endpointu.

**Koszt i pułapka:** Nowa ścieżka /v2 sama nie rozwiązuje migracji danych i odbiorców; stare wersje mają koszt
utrzymania.

**Rozwinięcie i ślad wykonania:** Wersjonowanie ma chronić znaczenie operacji: amount w groszach nie może po
cichu stać się kwotą w złotych. Monitoruj użycie starego kontraktu i uzgodnij okres migracji. Współistnienie
wersji zwiększa zakres testów i koszt danych, nawet jeśli routing /v1 oraz /v2 jest prosty.

**Ćwiczenie sprawdzające:** Zaplanuj wycofanie v1 z telemetrią użycia i datą odcięcia.

## Intercepting Filter / Middleware / Interceptor

**Intuicja:** Logowanie kontekstu, uwierzytelnienie i translacja błędów powtarzają się na granicy requestów.
Middleware lub Intercepting Filter składa te zachowania w określonej kolejności. Efekt zależy od położenia:
cache uruchomiony przed ustaleniem tenanta może użyć zbyt szerokiego klucza.

**Problem i mechanizm:** Wspólne zachowania granicy są składane wokół wywołania w określonej kolejności.

**Role i współpraca:** Każdy etap otrzymuje kontekst i możliwość przekazania dalej. Interceptor może opakować
wywołanie, a handler use case powinien pozostać właścicielem domeny.

**Przykład:** trace → authentication → tenant resolution → authorization → handler → response mapping.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
trace context -> authentication -> tenant resolution
    -> authorization -> cache z prawidłowym scope -> handler
    -> mapowanie odpowiedzi i bezpiecznych błędów
```

**Konkretny przypadek użycia:** Wspólna obsługa żądania HTTP

- **Wejście:** Request przechodzi przez correlation, uwierzytelnienie i pomiar czasu.
- **Przebieg:** Middleware tworzą jawny łańcuch, a finally kończy pomiar także po błędzie dalszego handlera.
- **Oczekiwany wynik:** Kontroler widzi przygotowany kontekst, metryka obejmuje wynik i błąd.
- **Błąd lub granica:** Kolejność zmienia zachowanie; walidacja uprawnień konkretnego zasobu nie znika przez
  ogólny filtr uwierzytelnienia.

**Kiedy zastosować:** Dla wspólnych reguł granicy. Nie wkładaj do ogólnego filtra złożonego procesu
biznesowego tylko dlatego, że wszystkie żądania przez niego przechodzą.

**Koszt i pułapka:** Middleware nie powinno przejmować domeny; self-invocation może ominąć proxy, a kolejność
auth i cache wpływa na bezpieczeństwo.

**Rozwinięcie i ślad wykonania:** Kolejność middleware określa, jaki kontekst zna kolejny etap. Cache
uruchomiony przed uwierzytelnieniem i wyznaczeniem tenanta może użyć zbyt szerokiego klucza. Obejmij testem
odmowę dostępu do już rozgrzanego cache, bo happy path świeżego zapytania nie ujawnia takiego wycieku.

**Ćwiczenie sprawdzające:** Pokaż wyciek danych, gdy wspólny cache działa przed ustaleniem użytkownika i
tenanta.
