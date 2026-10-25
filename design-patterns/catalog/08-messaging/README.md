# Integracja i messaging

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#eip)

Wzorce kanałów, komunikatów, routingu i odbiorców. **38 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [Splitter, Router, Aggregator oraz Dead Letter](../../labs/02-messaging.md).
Studium w Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Messaging rozdziela utworzenie informacji od jej odebrania i przetworzenia.
Najpierw ustal znaczenie komunikatu, następnie kanał dostawy, routing i sposób potwierdzania skutku. Kanał,
pojedyncza wiadomość i cały proces to różne poziomy. Duplikaty oraz opóźnienia są częścią kontraktu, dlatego
opis każdej karty oddziela przekazanie danych od zakończenia operacji biznesowej. Szkice pokazują logiczne
role, a nie API jednego wybranego brokera.

## Spis kart

-
  [Message / Command Message / Document Message / Event Message](#message--command-message--document-message--event-message)
- [Point-to-Point Channel / Work Queue](#point-to-point-channel--work-queue)
- [Publish–Subscribe Channel](#publishsubscribe-channel)
- [Datatype Channel](#datatype-channel)
- [Request–Reply / Return Address](#requestreply--return-address)
- [Correlation Identifier / Conversation ID](#correlation-identifier--conversation-id)
- [Message Sequence](#message-sequence)
- [Message Expiration / TTL](#message-expiration--ttl)
- [Format Indicator / Schema Version](#format-indicator--schema-version)
- [Message Router / Content-based Router](#message-router--content-based-router)
- [Message Filter](#message-filter)
- [Dynamic Router](#dynamic-router)
- [Recipient List](#recipient-list)
- [Splitter](#splitter)
- [Aggregator](#aggregator)
- [Resequencer](#resequencer)
- [Scatter–Gather](#scattergather)
- [Routing Slip](#routing-slip)
- [Process Manager / Workflow](#process-manager--workflow)
- [Message Translator / Messaging Mapper](#message-translator--messaging-mapper)
- [Envelope Wrapper](#envelope-wrapper)
- [Content Enricher](#content-enricher)
- [Content Filter](#content-filter)
- [Claim Check](#claim-check)
- [Normalizer / Canonical Data Model](#normalizer--canonical-data-model)
- [Messaging Gateway / Channel Adapter](#messaging-gateway--channel-adapter)
- [Polling Consumer / Event-driven Consumer](#polling-consumer--event-driven-consumer)
- [Competing Consumers / Message Dispatcher](#competing-consumers--message-dispatcher)
- [Selective Consumer](#selective-consumer)
- [Durable Subscriber](#durable-subscriber)
- [Idempotent Receiver / Inbox](#idempotent-receiver--inbox)
- [Transactional Client](#transactional-client)
- [Guaranteed Delivery / Store-and-forward](#guaranteed-delivery--store-and-forward)
- [Dead Letter Channel / Dead Letter Queue](#dead-letter-channel--dead-letter-queue)
- [Invalid Message Channel / Quarantine](#invalid-message-channel--quarantine)
- [Messaging Bridge / Message Bus](#messaging-bridge--message-bus)
- [Wire Tap / Message History](#wire-tap--message-history)
- [Message Store / Replay](#message-store--replay)

## Message / Command Message / Document Message / Event Message

**Intuicja:** Komunikat może prosić o wykonanie działania, przenosić dokument albo ogłaszać fakt. Wygląd JSON
nie rozstrzyga, którą rolę pełni. Command może zostać odrzucone przez właściciela, Event opisuje coś już
dokonanego, Document przekazuje uzgodniony stan danych.

**Problem i mechanizm:** Komunikat przenosi intencję wykonania, dokument danych albo fakt; te role mają
odrębne kontrakty.

**Role i współpraca:** Nadawca określa intencję, odbiorca interpretuje ją według kontraktu. ID, wersja i
jednostki danych mają znaczenie niezależnie od wybranego rodzaju wiadomości.

**Przykład:** ReserveStock jest poleceniem do właściciela, StockSnapshot dokumentem, StockReserved faktem
dokonanym.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record ReserveStock(String commandId, String sku, int units) { }
record StockSnapshot(String sku, int available, long version) { }
record StockReserved(String eventId, String reservationId) { }
```

**Konkretny przypadek użycia:** Trzy znaczenia danych magazynu

- **Wejście:** ReserveStock żąda 2 sztuk, StockSnapshot opisuje 12 dostępnych, StockReserved ogłasza
  rezerwację r7.
- **Przebieg:** Właściciel ocenia komendę, odbiorca dokumentu czyta stan, odbiorca faktu reaguje.
- **Oczekiwany wynik:** Nazwy i kontrakty rozróżniają zamiar od dokonanej zmiany.
- **Błąd lub granica:** Replay faktu nie może bezwarunkowo ponownie wykonać komendy pobrania sztuk.

**Kiedy zastosować:** Gdy chcesz jawnie rozdzielić zamiar od faktu i kopii stanu. Nie używaj jednej niejasnej
klasy Message jako substytutu znaczenia wszystkich operacji.

**Koszt i pułapka:** Nazwa eventu nie nadaje odbiorcy prawa do ponownego wykonania polecenia. Kontrakt wymaga
ID, wersji i jednostek.

**Rozwinięcie i ślad wykonania:** Command ma wskazanego właściciela decyzji, dokument niesie stan, a event
opisuje fakt z chwili powstania. Te same pola orderId i amount nie czynią ich wymiennymi. Przy replay fakt
może odbudować widok, ale nie powinien bezwarunkowo ponawiać polecenia płatności.

**Ćwiczenie sprawdzające:** Przekształć niejasne OrderMessage w trzy komunikaty o odmiennym znaczeniu.

## Point-to-Point Channel / Work Queue

**Intuicja:** Zadanie wygenerowania faktury zwykle powinien przejąć jeden wykonawca, a nie każdy worker naraz.
Point-to-Point kieruje wiadomość do jednego z konkurujących odbiorców. Dotyczy to pojedynczego dostarczenia;
awaria przed potwierdzeniem może sprawić, że następna próba trafi do innego odbiorcy.

**Problem i mechanizm:** Wiadomość trafia do jednego z konkurujących logicznych odbiorców kanału.

**Role i współpraca:** Queue rozdziela pracę, worker odbiera, ack potwierdza uzgodnione zakończenie.
Idempotencja chroni skutek, gdy transport ponawia dostarczenie.

**Przykład:** Zadanie GenerateInvoice jest obsługiwane przez jednego workera; ponowne dostarczenie nadal jest
możliwe.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Message message = queue.receive();
transaction.run(() -> inbox.applyOnce(message.id(), () ->
        invoices.generate(message.payload())));
queue.ack(message.deliveryHandle());
```

**Konkretny przypadek użycia:** Kolejka faktur

- **Wejście:** Trzy workery konkurują o GenerateInvoice i1.
- **Przebieg:** Jeden odbiera dostarczenie i zatwierdza lokalny efekt przed ack.
- **Oczekiwany wynik:** Powstaje faktura zgodnie z identyfikacją operacji.
- **Błąd lub granica:** Crash po zapisie przed ack może przekazać kopię innemu workerowi; potrzebna
  deduplikacja.

**Kiedy zastosować:** Dla kolejki zadań i podziału pracy między instancje. Gdy różne domeny mają niezależnie
reagować na ten sam fakt, potrzebny będzie Pub/Sub.

**Koszt i pułapka:** Jeden odbiorca na dostarczenie nie znaczy jeden skutek biznesowy; crash przed ack tworzy
duplikat.

**Rozwinięcie i ślad wykonania:** Dwóch workerów konkuruje o wiadomość, lecz po awarii pierwszy może
pozostawić już wykonany efekt bez ack. Transport przekaże kopię drugiemu. Dlatego liczbę skutków kontroluje
Inbox lub idempotentne API, a nie sama liczba logicznych odbiorców kanału.

**Ćwiczenie sprawdzające:** Pokaż moment ack i skutek awarii po zapisie faktury.

## Publish–Subscribe Channel

**Intuicja:** Jeden fakt może być interesujący równocześnie dla księgowości, wyszukiwarki i analityki. Pub/Sub
daje każdej logicznej subskrypcji własną ścieżkę odbioru. Nie jest to ta sama kolejka robocza dla trzech
zespołów, bo wtedy odbiorcy konkurowaliby o wiadomości zamiast widzieć je niezależnie.

**Problem i mechanizm:** Każda niezależna subskrypcja otrzymuje własny strumień faktów z kanału.

**Role i współpraca:** Publisher emituje, topic organizuje kanał, subskrypcje mają własny postęp. Instancje
jednej subskrypcji mogą dodatkowo dzielić pracę między siebie.

**Przykład:** OrderPlaced czytają osobno Billing, Search i Analytics; instancje Billing mogą współdzielić
jedną subskrypcję.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
subscriptions.create("billing", "orders");
subscriptions.create("search", "orders");
subscriptions.create("analytics", "orders");
publisher.publish("orders", orderPlaced);
```

**Konkretny przypadek użycia:** Trzej odbiorcy zamówienia

- **Wejście:** Billing, Search i Analytics reagują na OrderPlaced o42.
- **Przebieg:** Każdy ma osobną subskrypcję i postęp, dwie instancje Billing dzielą jedną subskrypcję.
- **Oczekiwany wynik:** Każda domena widzi fakt niezależnie od pozostałych.
- **Błąd lub granica:** Wspólna work queue dla trzech domen rozdzielałaby fakty zamiast je rozgłaszać.

**Kiedy zastosować:** Dla niezależnych reakcji na fakty. Zachowanie offline, replay i retencja wynikają z
konfiguracji transportu, nie samej nazwy Pub/Sub.

**Koszt i pułapka:** Fan-out zwiększa liczbę kopii i koszt retencji; trwałość zależy od brokera i
konfiguracji.

**Rozwinięcie i ślad wykonania:** Każda subskrypcja ma własny postęp. Dwie instancje Billing mogą dzielić
jeden strumień pracy, podczas gdy Search zachowuje niezależną kopię faktów. Jeśli Search nie pracuje godzinę,
jego opóźnienie nie powinno wymuszać ponownego naliczenia faktur przez Billing.

**Ćwiczenie sprawdzające:** Narysuj trzy subskrypcje i dwie instancje jednej z nich.

## Datatype Channel

**Intuicja:** Gdy jeden kanał niesie wszystkie rodzaje danych, każdy konsument musi odrzucać mnóstwo
nieinteresujących wiadomości. Datatype Channel grupuje uzgodniony typ lub kategorię danych w osobnym kanale.
Ułatwia określenie oczekiwań odbiorcy, limitów i własności kontraktu, lecz zwiększa liczbę kanałów do
utrzymania.

**Problem i mechanizm:** Wiadomości określonego typu są oddzielane kanałem, co upraszcza oczekiwania
odbiorców.

**Role i współpraca:** Nadawca wybiera kanał właściwy typowi, odbiorca zapisuje się na ten kontrakt. Wersje i
błędne wiadomości nadal wymagają obsługi wewnątrz kanału.

**Przykład:** InvoiceIssued ma własny topic zamiast wspólnego kanału zawierającego dziesiątki niezwiązanych
kontraktów.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
publisher.publish("invoice-issued", invoiceIssued);
publisher.publish("stock-reserved", stockReserved);
// Konsument faktur nie musi filtrować zdarzeń magazynowych.
```

**Konkretny przypadek użycia:** Kanał faktur

- **Wejście:** InvoiceIssued i StockReserved mają innych odbiorców i retencję.
- **Przebieg:** Producent wybiera kanał właściwego kontraktu.
- **Oczekiwany wynik:** Konsument faktur nie musi filtrować całego ruchu magazynu.
- **Błąd lub granica:** Nieznana wersja InvoiceIssued nadal wymaga kontroli, mimo osobnego kanału typu.

**Kiedy zastosować:** Gdy rozdział typów odzwierciedla niezależnych odbiorców i operacje. Nie twórz kanału dla
każdego prywatnego szczegółu implementacji producenta.

**Koszt i pułapka:** Dużo kanałów zwiększa operacje; zgodność wersji w obrębie typu nadal wymaga reguł.

**Rozwinięcie i ślad wykonania:** Oddzielenie InvoiceIssued ogranicza potrzebę sprawdzania typu przez
wszystkich konsumentów. Nie zakładaj jednak, że wszystkie rekordy kanału mają zawsze ten sam poprawny schemat.
Wersja, kwarantanna nieznanych danych i polityka ewolucji pozostają częścią kontraktu.

**Ćwiczenie sprawdzające:** Porównaj kanał per domena i kanał per typ przy 30 eventach.

## Request–Reply / Return Address

**Intuicja:** Asynchroniczna komunikacja nie wyklucza odpowiedzi. Request–Reply łączy dwa niezależne
komunikaty w jedną rozmowę, a Return Address wskazuje, dokąd odesłać wynik. Klient potrzebuje końca
oczekiwania, bo brak odpowiedzi może oznaczać opóźnienie, awarię albo utratę, niekoniecznie niewykonanie
operacji.

**Problem i mechanizm:** Asynchroniczne zapytanie zawiera sposób dostarczenia odpowiedzi, a klient czeka tylko
w zadanym budżecie.

**Role i współpraca:** Request zawiera correlationId i replyTo, serwer tworzy Reply, rejestr oczekujących
wiąże odpowiedź z klientem. Timeout usuwa prawo do nieskończonego czekania.

**Przykład:** CheckStock ma replyTo i correlationId; odpowiedź StockStatus trafia na kanał odpowiedzi.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
String correlationId = ids.next();
pending.register(correlationId, deadline);
requests.send(new Request(correlationId, "stock-replies", sku));
// Serwer odsyła do request.replyTo():
replies.send(new Reply(request.correlationId(), availableUnits));
```

**Konkretny przypadek użycia:** Pytanie o zapas

- **Wejście:** Request q42 ma replyTo i termin 200 ms.
- **Przebieg:** Serwer odsyła wynik z correlationId q42, klient mapuje go do oczekiwania.
- **Oczekiwany wynik:** Odpowiedź trafia do właściwego klienta także w innej kolejności.
- **Błąd lub granica:** Po 200 ms klient przestaje czekać; późny wynik nie dowodzi automatycznego anulowania
  pracy.

**Kiedy zastosować:** Gdy transport asynchroniczny ma obsłużyć krótką rozmowę z wynikiem. Dla długiego procesu
osobny uchwyt operacji często daje czytelniejszy lifecycle niż oczekiwanie na jedną odpowiedź.

**Koszt i pułapka:** Timeout klienta nie anuluje automatycznie pracy serwera; późne odpowiedzi wymagają
polityki usuwania.

**Rozwinięcie i ślad wykonania:** Request ma correlationId oraz adres odpowiedzi, a serwer tworzy nową
wiadomość odpowiedzi powiązaną z tą rozmową. Po timeout wpis oczekujący powinien wygasnąć. Późniejsza
odpowiedź nie może zostać przypisana do nowej rozmowy tylko dlatego, że ponownie wykorzystano lokalny
identyfikator.

**Ćwiczenie sprawdzające:** Obsłuż odpowiedź po timeout i duplikat requestu.

## Correlation Identifier / Conversation ID

**Intuicja:** Jeśli wiele rozmów odbywa się naraz, sama kolejność odpowiedzi nie pozwala ustalić ich
adresatów. Correlation Identifier przenosi to powiązanie w danych. Odpowiedzi mogą przyjść w innej kolejności
i nadal trafią do właściwego oczekującego procesu; własny messageId opisuje natomiast konkretną wiadomość.

**Problem i mechanizm:** Identyfikator wiąże odpowiedź lub serię wiadomości z rozmową, niezależnie od ID
pojedynczego dostarczenia.

**Role i współpraca:** Request nadaje identyfikator rozmowy, Reply go powtarza, rejestr wyszukuje oczekujący
wynik. TraceId może łączyć diagnostykę, lecz nie zastępuje wszystkich tych tożsamości.

**Przykład:** QuoteReply powtarza correlationId z QuoteRequest, a messageId jest nowy.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record Envelope(String messageId, String correlationId, Object body) { }
void onReply(Envelope reply) {
    PendingCall call = pending.forConversation(reply.correlationId());
    call.complete(reply.body());
}
```

**Konkretny przypadek użycia:** Równoległe rozmowy

- **Wejście:** Żądania q1 i q2 wysłano w tej kolejności, odpowiedzi przychodzą q2 i q1.
- **Przebieg:** Rejestr wiąże correlationId z oczekującym procesem.
- **Oczekiwany wynik:** Wyniki nie są zamienione między klientami.
- **Błąd lub granica:** TraceId całego procesu nie musi być dobrym kluczem deduplikacji dwóch różnych komend.

**Kiedy zastosować:** Gdy wynik trzeba przypisać do żądania lub procesu. Zakres identyfikatorów i autoryzacja
muszą zapobiegać pomieszaniu rozmów różnych tenantów.

**Koszt i pułapka:** Correlation ID nie jest automatycznie kluczem deduplikacji ani uprawnieniem do odczytu
cudzej odpowiedzi.

**Rozwinięcie i ślad wykonania:** MessageId identyfikuje konkretną wiadomość, correlationId cały request-reply
lub proces, a traceId ślad diagnostyczny. Przy ponowieniu żądania świadomie wybierz, które ID zachowujesz.
Użycie traceId jako klucza deduplikacji może połączyć legalnie różne komendy tego samego przepływu.

**Ćwiczenie sprawdzające:** Przypisz ID dwóm próbom i dwóm odpowiedziom tego samego procesu.

## Message Sequence

**Intuicja:** Duża logiczna wiadomość może nie mieścić się w jednym rekordzie transportu. Message Sequence
dzieli ją na części i przenosi informację, jak je rozpoznać oraz złożyć. Samo wysłanie kilku elementów bez
indeksu i warunku końca nie pozwala odbiorcy odróżnić kompletności od chwilowej ciszy.

**Problem i mechanizm:** Duży logiczny komunikat dzieli się na oznaczone elementy z ID sekwencji, numerem i
regułą końca.

**Role i współpraca:** SequenceId wiąże części, index określa miejsce, count lub znacznik końca kompletność.
Odbiorca deduplikuje indeksy i pilnuje limitu oczekiwania.

**Przykład:** Import 1000 pozycji ma importId, index i count; konsument wie, których części brakuje.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record Part(String sequenceId, int index, int count, byte[] payload) { }
received.putIfAbsent(part.index(), part);
if (received.size() == expectedCount) {
    complete(orderedByIndex(received));
}
```

**Konkretny przypadek użycia:** Podzielony import

- **Wejście:** Import ma ID imp7 i 3 części o indeksach 0,1,2.
- **Przebieg:** Odbiorca gromadzi różne indeksy i sprawdza kompletność.
- **Oczekiwany wynik:** 0,1,1 nie kończy importu; potrzeba również 2.
- **Błąd lub granica:** Brak ostatniej części kończy się timeout i sprzątaniem, nie wiecznym buforem.

**Kiedy zastosować:** Dla fragmentów pliku, partii lub dużego dokumentu. Nie buforuj bez limitu niekompletnych
sekwencji od dowolnej liczby nadawców.

**Koszt i pułapka:** Oczekiwanie bez deadline powoduje nieograniczone bufory; liczba elementów i rozmiar muszą
być walidowane.

**Rozwinięcie i ślad wykonania:** Sekwencja powinna określać ID całości, indeks części oraz sposób rozpoznania
kompletności. Odbiór numerów 0, 1, 1 nie daje trzech różnych fragmentów. Sprawdzaj zakres indeksu i limit
rozmiaru, a nie tylko licznik odebranych wiadomości.

**Ćwiczenie sprawdzające:** Zgub ostatni fragment i zaprojektuj cleanup po terminie.

## Message Expiration / TTL

**Intuicja:** Niektóre operacje tracą sens po określonym czasie: rezerwacja wygasła albo oferta jest
nieaktualna. Expiration nadaje wiadomości termin użyteczności. Broker może ograniczać jej retencję, ale
odbiorca powinien również sprawdzić regułę domenową przed wykonaniem skutku, zwłaszcza po opóźnieniu lub
replay.

**Problem i mechanizm:** Komunikat traci użyteczność po określonym terminie, który odbiorca i transport
interpretują jawnie.

**Role i współpraca:** Producent ustala expiresAt, transport stosuje własne TTL, konsument interpretuje termin
w kontrakcie biznesowym. Te dwie granice czasu nie muszą być identyczne.

**Przykład:** Oferta transportu z validUntil jest odrzucana po terminie nawet jeśli broker ją jeszcze
dostarczy.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (!clock.instant().isBefore(message.expiresAt())) {
    expiredMessages.record(message.id());
    return;
}
handler.apply(message.payload());
```

**Konkretny przypadek użycia:** Wygasająca oferta

- **Wejście:** Komunikat oferty ma validUntil 12:05, odbiorca dostaje go o 12:06.
- **Przebieg:** Przed skutkiem sprawdza domenowy termin.
- **Oczekiwany wynik:** Nie wykonuje już nieaktualnej propozycji.
- **Błąd lub granica:** Broker TTL i termin domenowy mogą mieć różne zachowanie; poprawne dostarczenie nie
  odnawia oferty.

**Kiedy zastosować:** Dla pracy o ograniczonej przydatności. Jeśli operacja jest obowiązkowa niezależnie od
opóźnienia, wygaszenie wiadomości może oznaczać utratę wymaganej pracy.

**Koszt i pułapka:** Broker TTL i termin domenowy nie zawsze są tożsame; zegary i opóźnienie mają znaczenie.

**Rozwinięcie i ślad wykonania:** Odbiorca sprawdza termin przed wykonaniem skutku, także gdy transport
przekazał wiadomość poprawnie. Expired jest wynikiem domenowym innym niż błąd infrastruktury. Zdefiniuj, czy
dokładnie w validUntil operacja jeszcze obowiązuje i z jakiego źródła pochodzi porównywany czas.

**Ćwiczenie sprawdzające:** Przetestuj granicę terminu i różnicę między expired a failed.

## Format Indicator / Schema Version

**Intuicja:** Odbiorca musi wiedzieć, jak interpretować bajty: ten sam payload może znaczyć coś innego dla
różnych formatów i wersji. Format Indicator przenosi jawny wybór dekodera. Nie jest gwarancją zgodności
semantycznej, ale zapobiega zgadywaniu formatu na podstawie przypadkowego układu danych.

**Problem i mechanizm:** Koperta określa format lub wersję potrzebną do prawidłowej interpretacji payloadu.

**Role i współpraca:** Koperta opisuje contentType i schemaVersion, rejestr wybiera dekoder, walidator
sprawdza rozpoznany kontrakt. Nieznana wersja wymaga kontrolowanej ścieżki.

**Przykład:** contentType=application/json i schemaVersion=2 wybierają dekoder InvoiceIssued.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Decoder decoder = decoders.require(
        envelope.contentType(), envelope.schemaVersion());
DomainMessage message = decoder.decode(envelope.payload());
validator.check(message);
```

**Konkretny przypadek użycia:** Ewolucja payloadu faktury

- **Wejście:** Koperta ma JSON i schemaVersion=2.
- **Przebieg:** Rejestr wybiera decoder v2, potem waliduje znaczenie pól.
- **Oczekiwany wynik:** Wersja określa sposób interpretacji kwoty i statusu.
- **Błąd lub granica:** Nieznane v3 nie może być cicho czytane jak v1.

**Kiedy zastosować:** Gdy transport niesie różne wersje albo reprezentacje. Nie przyjmuj nieznanego formatu
jak domyślnej wersji, jeśli może zmienić znaczenie danych.

**Koszt i pułapka:** Wersja w nagłówku nie zapewnia kompatybilności; nieznany format wymaga odrzucenia lub
kwarantanny.

**Rozwinięcie i ślad wykonania:** Dekoder najpierw rozpoznaje format i wersję, potem waliduje treść. Nieznana
wersja nie powinna zostać odczytana jak najstarsza przez przypadkowy default. Obsługa dwóch wersji wymaga
testu ich znaczenia, a nie wyłącznie sprawdzenia, że obie mieszczą się w parserze JSON.

**Ćwiczenie sprawdzające:** Dodaj opcjonalne pole i sprawdź starszy dekoder.

## Message Router / Content-based Router

**Intuicja:** Odbiorca zależy czasem od treści, np. kraju dostawy albo magazynu. Content-based Router skupia
tę decyzję w jednym etapie. Nadawca publikuje zamiar, a router wybiera kanał według jawnych reguł, zamiast
wymagać od wszystkich producentów znajomości całej topologii.

**Problem i mechanizm:** Router wybiera dalszy kanał na podstawie jawnych reguł, często treści komunikatu.

**Role i współpraca:** Router czyta uzgodnione pole, tablica reguł wybiera cel, ścieżka błędu obsługuje brak
dopasowania. Cel nie powinien być dowolnym adresem podanym przez niezaufanego klienta.

**Przykład:** country=PL kieruje przesyłkę do LocalShipping, pozostałe do InternationalShipping.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
String channel = switch (message.country()) {
    case "PL" -> "domestic-shipping";
    case "DE", "CZ" -> "regional-shipping";
    default -> "international-review";
};
publisher.send(channel, message);
```

**Konkretny przypadek użycia:** Routing przesyłek

- **Wejście:** Kraj PL kieruje do kanału domestic, DE do regional, nieznany kraj do review.
- **Przebieg:** Router stosuje jawną tabelę i zachowuje identyfikację wiadomości.
- **Oczekiwany wynik:** Brak dopasowania ma widoczną ścieżkę zamiast utraty.
- **Błąd lub granica:** Częściowa wysyłka przy retry wymaga trwałego planu albo idempotencji.

**Kiedy zastosować:** Gdy routing jest wspólną regułą integracji. Główne decyzje domenowe nadal powinny mieć
właściciela zamiast bez kontroli rozrastać się w routerze.

**Koszt i pułapka:** Reguły mogą stać się centralnym zbiorem logiki domenowej; trzeba mieć ścieżkę dla braku
dopasowania.

**Rozwinięcie i ślad wykonania:** FulfillmentPipeline najpierw waliduje wszystkie magazyny, a następnie
wyznacza kanały według pola warehouse. Nieznany magazyn kończy się jawnym błędem zamiast zniknięciem pozycji.
W realnym routerze utrwal wynik częściowej publikacji albo zapewnij idempotencję ponowienia całości.

**Ćwiczenie sprawdzające:** Dodaj nieznany kraj i sprawdź brak cichego zagubienia.

## Message Filter

**Intuicja:** Odbiorca nie zawsze potrzebuje każdej poprawnej wiadomości. Message Filter przepuszcza wybrany
podzbiór i świadomie pomija resztę. To decyzja o zainteresowaniu, odrębna od walidacji: niepoprawny komunikat
wymaga diagnostyki, a nie zniknięcia pod etykietą „nie pasuje”.

**Problem i mechanizm:** Odbiorca lub pośredni etap odrzuca komunikaty niespełniające warunku jego
zainteresowania.

**Role i współpraca:** Predykat określa zainteresowanie, filtr decyduje o przekazaniu dalej, metryki
odróżniają filtered od invalid. Kolejny handler otrzymuje tylko dopuszczony podzbiór.

**Przykład:** Analytics przepuszcza wyłącznie OrderPaid, resztę świadomie pomija i liczy.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
validator.check(message);
if (!interestedIn.test(message)) {
    metrics.increment("filtered");
    return;
}
next.handle(message);
```

**Konkretny przypadek użycia:** Filtr analityki

- **Wejście:** Strumień ma 100 OrderPaid i 30 innych poprawnych eventów.
- **Przebieg:** Walidacja poprzedza filtr zainteresowania.
- **Oczekiwany wynik:** Analytics otrzymuje 100, a 30 jest świadomie policzone jako filtered.
- **Błąd lub granica:** Uszkodzony payload trafia do invalid, nie jest maskowany jako zwykły brak
  zainteresowania.

**Kiedy zastosować:** Gdy konsument ma wąski zakres danych w szerszym strumieniu. Ustal, czy pomijanie jest
zamierzone i odwracalne przez replay, czy oznacza trwałe odrzucenie.

**Koszt i pułapka:** Pomijanie po ack może być nieodwracalne; filtr nie powinien maskować nieobsługiwanego
schematu.

**Rozwinięcie i ślad wykonania:** Filtr pomijający poprawny, nieinteresujący event wykonuje zamierzoną
decyzję. Niepoprawny event powinien trafić do innej ścieżki, bo ciche pominięcie utrudnia naprawę. Liczniki
accepted, filtered i invalid pozwalają odróżnić brak zainteresowania od utraty danych.

**Ćwiczenie sprawdzające:** Oddziel poprawne odfiltrowanie od odrzucenia uszkodzonego payloadu.

## Dynamic Router

**Intuicja:** Lista partnerów i ich możliwości może zmieniać się w czasie działania. Dynamic Router korzysta z
aktualizowanej wiedzy, zamiast mieć wszystkie trasy zapisane w kodzie. Daje to elastyczność, lecz rodzi
pytanie, czy ponowiona wiadomość ma użyć nowych reguł, czy kontynuować wcześniej wybraną trasę.

**Problem i mechanizm:** Docelowy kanał wybierany jest z aktualizowanej wiedzy o odbiorcach zamiast statycznej
listy.

**Role i współpraca:** Registry przechowuje możliwości, router wybiera cel, zapis planu może utrwalić wybór
dla rozmowy. Wersja konfiguracji pomaga odtworzyć decyzję.

**Przykład:** Partnerzy rejestrują obsługiwane regiony, a router przekazuje ofertę do aktualnego dostawcy
regionu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
RoutingRules rules = registry.currentRules();
Destination chosen = rules.choose(message.region());
conversation.storeRoute(message.correlationId(), chosen, rules.version());
publisher.send(chosen, message);
```

**Konkretny przypadek użycia:** Dynamiczni przewoźnicy

- **Wejście:** Partner P1 kończy obsługę regionu, P2 przejmuje ją w regułach v8.
- **Przebieg:** Router wybiera trasę i zapisuje jej wersję dla rozpoczętej rozmowy.
- **Oczekiwany wynik:** Nowe rozmowy używają P2, stare mają określoną politykę kontynuacji.
- **Błąd lub granica:** Retry nie powinno przypadkiem zlecić tej samej wysyłki dwóm partnerom.

**Kiedy zastosować:** Gdy odbiorcy dołączają lub zmieniają zakres obsługi. Dla stałej, małej listy celów
statyczne reguły są prostsze operacyjnie.

**Koszt i pułapka:** Stan routingu może być stary; potrzebna jest wersja reguł i kontrola zmian.

**Rozwinięcie i ślad wykonania:** Router odczytuje wersję aktualnych reguł i wybiera odbiorcę. Zmiana reguł w
połowie retry może skierować tę samą intencję do dwóch partnerów. Zapisanie wybranego odbiorcy albo wersji
planu pomaga zachować spójność rozpoczętej rozmowy.

**Ćwiczenie sprawdzające:** Usuń odbiorcę w trakcie procesu i określ zachowanie wiadomości w locie.

## Recipient List

**Intuicja:** Zlecenie wyceny może dotyczyć kilku konkretnych partnerów, ale nie wszystkich subskrybentów
kanału. Recipient List wylicza adresatów dla danej wiadomości i dostarcza im kopie. Reguła wyboru jest częścią
procesu, a częściowy sukces wysyłki wymaga osobnego śledzenia.

**Problem i mechanizm:** Jedna wiadomość trafia do wyliczonej dla niej listy konkretnych odbiorców.

**Role i współpraca:** Selektor tworzy listę, nadawca rozsyła, status dostaw wskazuje wykonane kopie. Dalszy
Aggregator może zbierać wyniki od tych adresatów.

**Przykład:** Zlecenie wyceny jest wysyłane tylko do trzech przewoźników obsługujących kraj i wagę.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<Partner> recipients = partners.supporting(country, weight);
for (Partner partner : recipients) {
    deliveryPlan.add(message.id(), partner.id());
}
relay.sendPending(deliveryPlan);
```

**Konkretny przypadek użycia:** Lista adresatów wyceny

- **Wejście:** Przesyłka 5 kg do PL pasuje do 3 z 10 partnerów.
- **Przebieg:** Selektor tworzy listę, plan śledzi dostawę do każdego wybranego adresata.
- **Oczekiwany wynik:** Pytanie dostają tylko kwalifikujący się partnerzy.
- **Błąd lub granica:** Awaria trzeciej wysyłki nie powinna bez kontroli duplikować pierwszych dwóch.

**Kiedy zastosować:** Gdy zbiór odbiorców zależy od właściwości żądania. Przy stałym gronie niezależnych
odbiorców Pub/Sub może lepiej oddzielić producenta od adresatów.

**Koszt i pułapka:** Różni się od stałego Pub/Sub; częściowa wysyłka i duplikaty wymagają jawnego stanu
procesu.

**Rozwinięcie i ślad wykonania:** Lista adresatów jest wynikiem reguły dla konkretnego żądania, nie globalną
subskrypcją. Dla każdego odbiorcy zachowaj stan przyjęcia i identyfikator dostawy. Gdy publikacja do trzeciego
partnera zawiedzie, retry nie powinno bez kontroli dublować pierwszych dwóch.

**Ćwiczenie sprawdzające:** Zapisz, którzy z trzech adresatów przyjęli zlecenie przed awarią.

## Splitter

**Intuicja:** Jedno zamówienie zawiera kilka pozycji, które mogą trafić do różnych wykonawców. Splitter
rozdziela złożoną wiadomość na elementy zachowujące związek z całością. Nie rozstrzyga jeszcze, czy części
należy zatwierdzić razem ani jak zinterpretować brak jednej odpowiedzi.

**Problem i mechanizm:** Złożony komunikat rozpada się na mniejsze jednostki przetwarzania z zachowaną
korelacją.

**Role i współpraca:** Wejściowa kolekcja jest całością, fragment ma orderId i własny lineId. Każdy fragment
może być kierowany dalej niezależnie, a wynik wraca z tymi samymi identyfikatorami.

**Przykład:** Order z pięcioma pozycjami daje pięć LineReservation z orderId i lineId.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<Reservation> pieces = order.lines().stream()
        .map(line -> new Reservation(order.id(), line.id(), line.quantity()))
        .toList();
pieces.forEach(router::send);
```

**Konkretny przypadek użycia:** Podział order42

- **Wejście:** Zamówienie ma line1 w north i line2 w south.
- **Przebieg:** Splitter tworzy dwa Reservation z orderId i lineId.
- **Oczekiwany wynik:** Każda część trafia do właściwego właściciela z zachowaną korelacją.
- **Błąd lub granica:** Sukces line1 nie jest jeszcze sukcesem całego zamówienia.

**Kiedy zastosować:** Dla pracy równoległej lub różnych właścicieli części. Określ maksymalny fan-out i sposób
zakończenia całego procesu, zanim rozesłane zostaną fragmenty.

**Koszt i pułapka:** Sukces części nie oznacza sukcesu całości; liczba elementów może zwiększyć fan-out.

**Rozwinięcie i ślad wykonania:** Order z l1 i l2 daje dwa Reservation z tym samym orderId oraz odrębnymi
lineId. Koperta musi zachować oba rodzaje korelacji. Wynik pojedynczej rezerwacji nie jest jeszcze wynikiem
zamówienia: to kontrakt agregatora lub koordynatora procesu.

**Ćwiczenie sprawdzające:** Odrzuć jedną pozycję i określ zakończenie całego zamówienia.

## Aggregator

**Intuicja:** Po rozdzieleniu pracy klient często oczekuje jednego wyniku. Aggregator przechowuje odpowiedzi
należące do tej samej rozmowy i rozpoznaje, kiedy ma ich wystarczająco dużo. Musi osobno zdefiniować
kompletność, sukces oraz koniec czekania: komplet odpowiedzi może zawierać odmowę biznesową.

**Problem i mechanizm:** Stanowy etap zbiera skorelowane wyniki według warunku kompletności i terminu.

**Role i współpraca:** CorrelationId wybiera rozmowę, zbiór oczekiwanych kluczy określa kompletność, mapa
wyników deduplikuje części, deadline ogranicza pamięć i czas.

**Przykład:** Trzy QuoteReply tworzą jedną ofertę po zebraniu wszystkich odpowiedzi lub upływie 200 ms.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
results.putIfAbsent(reply.lineId(), reply.reserved());
if (results.keySet().containsAll(expectedLineIds)) {
    boolean success = results.values().stream().allMatch(Boolean::booleanValue);
    finish(success);
}
```

**Konkretny przypadek użycia:** Agregacja rezerwacji

- **Wejście:** Oczekiwane są l1 i l2, przychodzą l1=true, l1=true, l2=false.
- **Przebieg:** Mapa jest kluczowana po pozycji, a kompletność i sukces liczone osobno.
- **Oczekiwany wynik:** Wynik jest kompletny, lecz allReserved=false.
- **Błąd lub granica:** Duplikat l1 nie może zastąpić l2 ani podwyższać licznika kompletności.

**Kiedy zastosować:** Gdy częściowe wyniki mają zostać złożone w całość. Nie licz surowych dostarczeń,
ponieważ dwa duplikaty nie zastępują dwóch różnych odpowiedzi.

**Koszt i pułapka:** Stan i bufory wymagają trwałości lub świadomej utraty; duplikaty nie mogą podbić licznika
kompletności.

**Rozwinięcie i ślad wykonania:** Aggregator przechowuje mapę wyników po lineId oraz oczekiwany zbiór pozycji.
Duplikat l1 nie zwiększa liczby różnych odpowiedzi. Kompletność to warunek zbioru, sukces to dodatkowo
pozytywne wyniki; zamówienie może być kompletne i jednocześnie odrzucone biznesowo.

**Ćwiczenie sprawdzające:** Dostarcz dwa razy tę samą odpowiedź i pomiń jedną inną.

## Resequencer

**Intuicja:** Wiadomości wysłane w kolejności mogą dotrzeć inną drogą lub zakończyć przetwarzanie w innym
porządku. Resequencer buforuje te zbyt wczesne i wydaje dopiero ciąg kolejnych numerów. Odbiorca za nim widzi
uzgodnioną kolejność, ale brak pojedynczej części może zatrzymać cały dalszy postęp.

**Problem i mechanizm:** Porządkuje wiadomości z jednej sekwencji przed przekazaniem dalej.

**Role i współpraca:** Bufor trzyma wersje, nextExpected wskazuje następny numer, polityka luki ustala limit
czekania i rozmiaru. Kolejność obowiązuje w zakresie jednej sekwencji.

**Przykład:** Statusy z wersjami 3,1,2 buforują 3 aż nadejdą 1 i 2.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
buffer.putIfAbsent(message.sequence(), message);
while (buffer.containsKey(nextExpected)) {
    downstream.handle(buffer.remove(nextExpected));
    nextExpected++;
}
```

**Konkretny przypadek użycia:** Odtworzenie porządku

- **Wejście:** Statusy jednej rozmowy docierają w kolejności 3,1,2.
- **Przebieg:** Resequencer buforuje 3, wydaje 1, potem 2 i 3.
- **Oczekiwany wynik:** Odbiorca widzi uzgodnioną sekwencję.
- **Błąd lub granica:** Brak 2 wymaga limitu czekania i pamięci; nie wolno buforować bez końca.

**Kiedy zastosować:** Gdy późniejsze operacje wymagają określonego porządku. Jeśli działania są przemienne,
rezygnacja z odtwarzania kolejności może dać prostszy i wydajniejszy proces.

**Koszt i pułapka:** Nie da się czekać bez ograniczeń na brakujący numer; potrzebna jest polityka luki,
pamięci i timeoutu.

**Rozwinięcie i ślad wykonania:** Po otrzymaniu wersji 3 najpierw czekamy na 1 i 2 albo wykonujemy określoną
politykę luki. Bufor ma limit na rozmowę i całkowitą liczbę rozmów. Bez tego pojedyncza zgubiona wiadomość
pozwoliłaby stale gromadzić kolejne wersje w pamięci.

**Ćwiczenie sprawdzające:** Zgub wersję 2 i opisz, co dzieje się z wersją 3.

## Scatter–Gather

**Intuicja:** Zapytanie do kilku przewoźników można wysłać równolegle i porównać odpowiedzi. Scatter–Gather
łączy rozesłanie z agregacją w jeden wzorzec rozmowy. Wynik zależy od reguły końca: odpowiedzieli wszyscy,
minął termin albo zebrano wystarczający podzbiór.

**Problem i mechanizm:** Żądanie rozsyła się równolegle, a odpowiedzi agreguje w jednym ograniczonym czasowo
procesie.

**Role i współpraca:** Scatter wybiera odbiorców i wysyła, Gather koreluje odpowiedzi oraz wybiera wynik.
Jeden deadline powinien objąć całą rozmowę, a nie osobno każdego partnera.

**Przykład:** Zapytaj czterech przewoźników i wybierz najtańszą ofertę spośród odpowiedzi przed deadline.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Conversation quotes = conversations.start(partners.ids(), deadline);
for (Partner partner : partners) {
    partner.requestQuote(quotes.id(), shipment);
}
return quotes.bestReceivedBefore(deadline);
```

**Konkretny przypadek użycia:** Równoległa wycena

- **Wejście:** Czterech przewoźników ma wspólny deadline 200 ms.
- **Przebieg:** Scatter wysyła, Gather wybiera najlepszą poprawną odpowiedź otrzymaną do terminu.
- **Oczekiwany wynik:** Klient dostaje wynik według jawnej reguły częściowego zbioru.
- **Błąd lub granica:** Najtańsza otrzymana oferta nie musi być najtańszą istniejącą; brakująca mogła być
  lepsza.

**Kiedy zastosować:** Dla porównywania ofert i równoległych odczytów. Przy kosztownym fan-out policz łączne
obciążenie; szybsza odpowiedź klienta może oznaczać znacznie więcej pracy systemu.

**Koszt i pułapka:** Fan-out podnosi obciążenie i szansę częściowej awarii; kryterium minimum może zależeć od
niepełnego zbioru.

**Rozwinięcie i ślad wykonania:** Rozesłanie i zbieranie muszą dzielić wspólny deadline. Brak jednej
odpowiedzi może oznaczać wynik częściowy, błąd całej operacji albo wybór najlepszej dostępnej oferty. Decyzja
powinna być widoczna dla klienta, ponieważ „najtańsza otrzymana” nie znaczy „najtańsza istniejąca”.

**Ćwiczenie sprawdzające:** Porównaj czekanie na wszystkich z kończeniem po trzech odpowiedziach.

## Routing Slip

**Intuicja:** Proces nie zawsze potrzebuje centralnego routera decydującego po każdym kroku. Routing Slip
dołącza do wiadomości plan kolejnych etapów. Bieżący wykonawca realizuje swoją część i kieruje dalej według
planu, co pozwala złożyć różne ścieżki przy wspólnych komponentach.

**Problem i mechanizm:** Wiadomość niesie plan kolejnych etapów, które przekazują ją dalej bez centralnego
routera każdego kroku.

**Role i współpraca:** Slip zawiera zaufane identyfikatory etapów i postęp, handler wykonuje krok, transport
przekazuje kolejną wiadomość. Plan i wykonanie mają własne wersje.

**Przykład:** Dokument niesie trasę validate → enrich → sign, a zakończony etap przesuwa wskaźnik.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
String step = slip.currentStep();
handlers.require(step).apply(message.payload());
RoutingSlip advanced = slip.advance();
outbox.append(message.withSlip(advanced));
```

**Konkretny przypadek użycia:** Plan dokumentu

- **Wejście:** Dokument ma trasę validate → enrich → sign.
- **Przebieg:** Każdy etap wykonuje pracę i przekazuje wersjonowany plan z przesuniętym postępem.
- **Oczekiwany wynik:** Ten sam komponent obsłuży różne legalne trasy.
- **Błąd lub granica:** Klient nie może wstawić dowolnego wewnętrznego URL; utrata przekazania wymaga trwałego
  zamiaru.

**Kiedy zastosować:** Dla przewidywalnych tras różniących się zestawem etapów. Dla wielu rozgałęzień, timerów
i kompensacji jawny Process Manager może być czytelniejszy.

**Koszt i pułapka:** Trasa wymaga walidacji, kontroli uprawnień i wersji; klient nie może dowolnie wskazywać
wewnętrznych adresów.

**Rozwinięcie i ślad wykonania:** Każdy krok czyta zaufany identyfikator kolejnego etapu i aktualizuje postęp
dopiero zgodnie z zasadą trwałości. Nie wykonuj dowolnego URL wpisanego do wiadomości przez klienta.
Zakończenie kroku bez przekazania dalej wymaga odtwarzalnego zamiaru wysyłki, np. przez Outbox.

**Ćwiczenie sprawdzające:** Dodaj etap w nowej wersji planu i obsłuż stare wiadomości.

## Process Manager / Workflow

**Intuicja:** Proces rezerwacji może czekać na odpowiedź magazynu, później płatność i termin dostawy. Process
Manager pamięta, co już zaszło i na co czeka, zamiast liczyć na lokalny stos wywołań. Zdarzenie, timeout oraz
restart są wtedy wejściami do jawnej maszyny procesu.

**Problem i mechanizm:** Stanowy koordynator pamięta postęp wieloetapowego procesu i wysyła kolejne polecenia.

**Role i współpraca:** Stan procesu przechowuje etap, korelację i deadline. Koordynator tworzy kolejne
polecenia, a trwały log lub store pozwala wznowić go po awarii.

**Przykład:** Fulfillment przechodzi RESERVED → PAID → SHIPPED z trwałymi timerami i identyfikacją kroków.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
switch (process.state()) {
    case WAITING_FOR_STOCK -> process.onStockResult(event);
    case WAITING_FOR_PAYMENT -> process.onPaymentResult(event);
    case COMPLETED -> process.ignoreDuplicate(event.id());
}
processStore.saveWithOutbox(process);
```

**Konkretny przypadek użycia:** Proces realizacji

- **Wejście:** Order42 czeka na rezerwację, płatność i wysyłkę przez kilka minut.
- **Przebieg:** Process Manager utrwala stan i timery, koreluje wyniki kroków.
- **Oczekiwany wynik:** Restart pozwala wznowić proces od znanego postępu.
- **Błąd lub granica:** Brak odpowiedzi płatności to wynik nieznany, nie automatyczne potwierdzenie braku
  obciążenia.

**Kiedy zastosować:** Dla wieloetapowych, długich rozmów. Prosty przepływ bez oczekiwania i naprawy może nie
potrzebować własnego silnika workflow.

**Koszt i pułapka:** Własny workflow engine szybko wymaga retry, deduplikacji, timerów i narzędzi naprawy.

**Rozwinięcie i ślad wykonania:** Koordynator ma ID procesu, aktualny etap, oczekiwane odpowiedzi i terminy.
Timeout jest kolejnym zdarzeniem procesu, a nie automatycznym dowodem niepowodzenia partnera. Po restarcie
odtwarza stan i ponawia tylko działania bezpieczne dzięki identyfikacji kroków.

**Ćwiczenie sprawdzające:** Zrestartuj koordynator po zapłacie przed wysłaniem polecenia wysyłki.

## Message Translator / Messaging Mapper

**Intuicja:** Partner może podawać kwotę jako tekst, a domena wymagać jednostek minimalnych i osobnej waluty.
Translator zamienia reprezentację zgodnie z semantyką, nie tylko nazwami pól. Messaging Mapper łączy podobną
odpowiedzialność z granicą pomiędzy obiektem aplikacji a komunikatem transportowym.

**Problem i mechanizm:** Tłumaczy reprezentację danych lub model obiektu na kontrakt wiadomości i odwrotnie.

**Role i współpraca:** Wejściowy DTO jest kontraktem partnera, translator sprawdza jednostki i zakres,
wyjściowy komunikat ma kontrakt wewnętrzny. Błędy tłumaczenia wymagają jawnego odrzucenia.

**Przykład:** Partner amount="12.50" EUR staje się wewnętrznym moneyCents=1250 i currency=EUR.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
BigDecimal amount = new BigDecimal(partner.amount());
long cents = amount.movePointRight(2).longValueExact();
return new PaymentMessage(partner.id(), cents, "EUR");
// Skala 2 jest tutaj jawnym założeniem przykładu dla EUR.
```

**Konkretny przypadek użycia:** Kwota partnera

- **Wejście:** Partner podaje 12.50 EUR, domena operuje w centach.
- **Przebieg:** Translator sprawdza skalę i przelicza dokładnie na 1250.
- **Oczekiwany wynik:** Jednostka jest jawna i nie przecieka format partnera.
- **Błąd lub granica:** 12.501 nie może zostać cicho obcięte, jeśli kontrakt wymaga dokładnych centów.

**Kiedy zastosować:** Gdy systemy używają różnych modeli danych. Nie maskuj utraty precyzji domyślnym
obcięciem albo nieznanego statusu przypadkowym wariantem.

**Koszt i pułapka:** Waluty, precyzja i daty wymagają semantycznej walidacji; samo rename pola nie wystarcza.

**Rozwinięcie i ślad wykonania:** Tłumaczenie 12.50 EUR na 1250 jednostek minimalnych musi ustalić skalę
waluty i zabronić cichego obcięcia 12.501. Odbiorca powinien rozróżniać błąd reprezentacji od nieobsługiwanej
wartości. Adapter nie może arbitralnie zmienić znaczenia pola tylko po to, by przeszło mapowanie typów.

**Ćwiczenie sprawdzające:** Sprawdź trzy miejsca po przecinku i nieobsługiwaną walutę.

## Envelope Wrapper

**Intuicja:** Dane biznesowe i informacje potrzebne do transportu mają inne zadania. Envelope Wrapper
opakowuje treść w kopertę z metadanymi, np. identyfikatorem, formatem i korelacją. Pozwala narzędziom
pośrednim obsługiwać przepływ bez interpretowania każdego pola domenowego.

**Problem i mechanizm:** Dane aplikacji są opakowane w nagłówki potrzebne do transportu i zarządzania.

**Role i współpraca:** Envelope przenosi nagłówki i payload, middleware odczytuje metadane, handler domenowy
interpretuje treść. Zaufanie do nagłówków zależy od źródła, nie od miejsca zapisania.

**Przykład:** Payload OrderPlaced otrzymuje messageId, traceId, schemaVersion i typ treści.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record Envelope<T>(
        String messageId,
        String correlationId,
        int schemaVersion,
        T payload) { }
var envelope = new Envelope<>(id, conversationId, 1, orderPlaced);
```

**Konkretny przypadek użycia:** Koperta OrderPlaced

- **Wejście:** Payload jest faktem zamówienia, nagłówki niosą messageId, correlationId i schemaVersion.
- **Przebieg:** Transport obsługuje metadane bez znajomości szczegółów pozycji.
- **Oczekiwany wynik:** Domena i obsługa dostawy mają osobne kontrakty.
- **Błąd lub granica:** TenantId skopiowany od klienta nie staje się zaufany przez umieszczenie w nagłówku.

**Kiedy zastosować:** Gdy wiele etapów potrzebuje wspólnych metadanych transportu. Określ, które pola są
niezmienne, podpisane i nadawane wyłącznie przez zaufane komponenty.

**Koszt i pułapka:** Nie wszystkie nagłówki można ufać od klienta; trzeba oddzielić metadane transportowe od
domenowych.

**Rozwinięcie i ślad wykonania:** Koperta ma własny lifecycle: transport może dodać metadane dostarczenia,
zachowując niezmienny payload biznesowy. Podpis i autoryzacja muszą wskazywać, które elementy obejmują.
Skopiowany od klienta tenantId nie staje się zaufany przez umieszczenie go w nagłówku.

**Ćwiczenie sprawdzające:** Wskaż nagłówki nadawane przez zaufanego producenta i kopiowane z zewnątrz.

## Content Enricher

**Intuicja:** Wiadomość może mieć tylko identyfikator klienta, a kolejny etap potrzebuje także regionu.
Content Enricher pobiera brakującą informację i tworzy wzbogaconą reprezentację. Ważne jest, czy chodzi o
bieżący profil, czy dane obowiązujące w momencie zdarzenia, bo te wartości mogą się różnić.

**Problem i mechanizm:** Etap uzupełnia komunikat o informacje potrzebne kolejnemu odbiorcy.

**Role i współpraca:** Enricher zna dodatkowe źródło, klucz powiązania i regułę czasu. Wynik powinien
odróżniać kompletne wzbogacenie od braku danych lub timeoutu.

**Przykład:** OrderPlaced z customerId otrzymuje region dostawy od profilu klienta.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
CustomerProfile profile = profiles.load(message.customerId(), deadline);
EnrichedOrder enriched = new EnrichedOrder(
        message.orderId(), message.total(), profile.region());
next.send(enriched);
```

**Konkretny przypadek użycia:** Wzbogacenie adresu

- **Wejście:** Zdarzenie zawiera customerId, wysyłka potrzebuje adresu z chwili zakupu.
- **Przebieg:** Enricher pobiera właściwy snapshot zamiast przypadkowo bieżącego profilu.
- **Oczekiwany wynik:** Zmiana adresu po zakupie nie przekierowuje starego zamówienia.
- **Błąd lub granica:** Brak snapshotu lub timeout wymaga jawnego wyniku, nie wydania pozornie kompletnej
  wiadomości.

**Kiedy zastosować:** Gdy odbiorca wymaga dodatkowego kontekstu, którego producent nie powinien znać. Unikaj
ukrywania krytycznego snapshotu adresu pod odczytem aktualnego profilu.

**Koszt i pułapka:** Odczyt aktualnego profilu może nie odzwierciedlać momentu zamówienia; dodatkowe I/O
wprowadza timeout.

**Rozwinięcie i ślad wykonania:** Enricher decyduje, czy dokłada bieżący profil, czy snapshot z momentu
zamówienia. W adresie dostawy ta różnica zmienia skutek biznesowy. Jeśli dodatkowe źródło zawiedzie, zdefiniuj
jawny błąd, retry lub dopuszczalną degradację zamiast wydawać niekompletną wiadomość jako pełną.

**Ćwiczenie sprawdzające:** Zdecyduj, czy adres jest snapshotem zamówienia, czy bieżącą wartością.

## Content Filter

**Intuicja:** Odbiorca analityczny nie potrzebuje całego adresu i email klienta, jeśli ma liczyć sprzedaż per
kraj. Content Filter ogranicza treść do niezbędnych danych. To odrębne zadanie od Message Filter, który
decyduje o przepuszczeniu całej wiadomości.

**Problem i mechanizm:** Usuwa zbędne albo niedozwolone pola przed dalszym przekazaniem.

**Role i współpraca:** Wejście zawiera bogatszy model, mapper tworzy dozwolony podzbiór, odbiorca otrzymuje
kontrakt dopasowany do celu. Allowlista pól jest odporniejsza na niechciane rozszerzenie źródła.

**Przykład:** Event dla Analytics zawiera kraj i kwotę, ale nie adres email ani pełny adres klienta.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record SalesFact(String country, long totalCents) { }
SalesFact output = new SalesFact(
        order.deliveryCountry(), order.totalCents());
analytics.publish(output); // Nie kopiujemy email ani pełnego adresu.
```

**Konkretny przypadek użycia:** Minimalny fakt sprzedaży

- **Wejście:** Analytics potrzebuje kraju i kwoty, a model źródłowy ma email i pełny adres.
- **Przebieg:** Content Filter buduje dozwolony DTO z allowlisty.
- **Oczekiwany wynik:** Odbiorca nie dostaje zbędnych danych osobowych.
- **Błąd lub granica:** Usuwanie jednego znanego pola nie chroni przed nowym polem PII dodanym do źródła.

**Kiedy zastosować:** Dla minimalizacji danych i wąskich kontraktów. Nie używaj usuwania pól jako zamiennika
autoryzacji dostępu do samego kanału.

**Koszt i pułapka:** Usunięcie danych musi zachować potrzeby odbiorcy; filtr nie zastępuje kontroli dostępu do
źródła.

**Rozwinięcie i ślad wykonania:** Redukcja treści powinna być allowlistą potrzebnych pól dla danego odbiorcy.
Usuwanie pojedynczego znanego pola PII nie chroni, gdy producent doda nowe. Osobny DTO integracyjny i test
dokładnego kształtu wiadomości utrzymują granicę danych także po ewolucji modelu źródłowego.

**Ćwiczenie sprawdzające:** Zaprojektuj minimalny payload do liczenia sprzedaży per kraj.

## Claim Check

**Intuicja:** Broker wiadomości nie zawsze jest dobrym miejscem dla wielkiego pliku. Claim Check przechowuje
duży obiekt osobno, a komunikat niesie kontrolowany odnośnik. Odbiorca odbiera bilet do danych, a nie sam
payload, co zmniejsza transport, lecz wprowadza zależność dostępności i retencji dodatkowego store.

**Problem i mechanizm:** Duży payload przechowuje się poza brokerem, a komunikat niesie kontrolowany odnośnik.

**Role i współpraca:** Object store utrzymuje treść, claim identyfikuje konkretną wersję i parametry, odbiorca
weryfikuje integralność oraz uprawnienie. Cleanup musi respektować okno odtworzenia wiadomości.

**Przykład:** Film trafia do object store; TranscodeRequested zawiera objectId, checksum i rozmiar.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
BlobRef ref = blobs.put(payload);
publisher.send(new FileReady(ref.id(), ref.size(), ref.checksum()));

byte[] loaded = blobs.get(event.blobId());
checksums.verify(loaded, event.checksum());
```

**Konkretny przypadek użycia:** Duży materiał wideo

- **Wejście:** Upload ma 2 GB, broker powinien przenieść mały komunikat.
- **Przebieg:** Blob trafia do store, event zawiera objectId, rozmiar i checksum.
- **Oczekiwany wynik:** Worker pobiera konkretny obiekt i weryfikuje integralność.
- **Błąd lub granica:** Usunięcie blobu przed końcem okna replay czyni komunikat nieodtwarzalnym.

**Kiedy zastosować:** Dla dużych dokumentów i blobów. Gdy treść jest mała, dodatkowy odczyt i zarządzanie
lifecycle mogą być droższe niż zwykły komunikat.

**Koszt i pułapka:** Retencja blobu musi pokrywać replay; odnośnik nie może omijać autoryzacji ani wskazywać
dowolnego URL.

**Rozwinięcie i ślad wykonania:** Claim powinien wskazywać konkretny obiekt z rozmiarem i sumą kontrolną.
Odbiorca weryfikuje te dane przed użyciem, a cleanup nie może usunąć blobu w dopuszczalnym oknie replay. Jeśli
retencje się różnią, potrzebna jest jawna ścieżka odtworzenia lub trwałego błędu.

**Ćwiczenie sprawdzające:** Usuń blob przed replay i zaprojektuj wykrycie oraz naprawę braku.

## Normalizer / Canonical Data Model

**Intuicja:** Jeśli każdy odbiorca osobno obsługuje formaty wszystkich partnerów, liczba mapowań rośnie razem
z obiema stronami. Normalizer przekształca wejścia do jednego uzgodnionego modelu granicy. Canonical Data
Model nazywa tę wspólną reprezentację; jej zakres powinien pozostać ograniczony do potrzeb integracji.

**Problem i mechanizm:** Różne formaty wejściowe trafiają do uzgodnionej reprezentacji integracyjnej przez
dedykowane translatory.

**Role i współpraca:** Translator wybiera parser dla partnera, normalizer wydaje wspólną postać, konsumenci
znają tylko ten kontrakt. Jednostki i znaczenie pól muszą być spójne.

**Przykład:** Dwóch partnerów podaje wagę w lb i kg, a kanał wewnętrzny zawsze używa gramów.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
PartnerDecoder decoder = decoders.forPartner(partnerId);
CanonicalShipment shipment = decoder.toCanonical(rawPayload);
// Każdy wynik ma masę w gramach i ten sam kontrakt adresu.
shipping.handle(shipment);
```

**Konkretny przypadek użycia:** Waga trzech partnerów

- **Wejście:** Partner A wysyła kg, B lb, C gramy.
- **Przebieg:** Dedykowane translatory wydają wspólny model masy w gramach.
- **Oczekiwany wynik:** Downstream nie zna wszystkich formatów partnerów.
- **Błąd lub granica:** Model kanoniczny powinien mieć granicę, zamiast stać się encją całej firmy.

**Kiedy zastosować:** Gdy wiele źródeł zasila podobne procesy. Uniwersalny model całej firmy zwykle tworzy
nowe sprzężenia zamiast rozwiązać istniejące.

**Koszt i pułapka:** Uniwersalny model dla całej firmy bywa zbyt szeroki; model kanoniczny powinien mieć
granicę integracji.

**Rozwinięcie i ślad wykonania:** Normalizer ma osobny translator dla każdego formatu wejściowego i jeden
kontrakt wyjścia w określonej granicy. Dzięki temu konsument nie zna wszystkich partnerów. Nie rozszerzaj
modelu kanonicznego o każde pole każdego systemu, bo powstanie nowy globalny model sprzęgający wszystkie
zmiany.

**Ćwiczenie sprawdzające:** Dodaj trzeci format bez zmian konsumenta i sprawdź jednostki.

## Messaging Gateway / Channel Adapter

**Intuicja:** Kod aplikacji nie powinien wszędzie znać szczegółów sesji, nagłówków i wyjątków brokera.
Messaging Gateway daje domenowo zrozumiałe operacje nad transportem, a Channel Adapter łączy go z konkretnym
źródłem lub odbiornikiem. Granica ma ukrywać techniczne detale, ale zachować istotne gwarancje sukcesu i
błędu.

**Problem i mechanizm:** Gateway chowa techniczne API brokera, adapter łączy zewnętrzne źródło z systemem
wiadomości.

**Role i współpraca:** Port opisuje publish, adapter mapuje event na kopertę i używa SDK. Poziom potwierdzenia
powinien być częścią kontraktu, jeśli wpływa na poprawność.

**Przykład:** Orders.publishPlaced(event) mapuje domenę na kopertę; adapter plikowy emituje wiadomości
importu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
interface OrderEvents {
    PublishReceipt publish(OrderPlaced event);
}
PublishReceipt receipt = orderEvents.publish(event);
// Kontrakt PublishReceipt wyjaśnia punkt uznanego potwierdzenia.
```

**Konkretny przypadek użycia:** Port publikacji

- **Wejście:** Use case chce publish OrderPlaced i znać znaczenie sukcesu.
- **Przebieg:** Gateway mapuje event na kopertę i uzgadnia punkt potwierdzenia z transportem.
- **Oczekiwany wynik:** Rdzeń nie importuje API sesji brokera.
- **Błąd lub granica:** Void ukrywające różnicę enqueue w pamięci i trwałego ack może złamać poprawność.

**Kiedy zastosować:** Dla izolacji transportu od use case. Nie redukuj każdego wyniku do void, jeśli klient
musi rozróżniać lokalne przyjęcie i trwałe potwierdzenie brokera.

**Koszt i pułapka:** Ukrycie API nie usuwa semantyki ack, retry i transakcji; kontrakt portu musi je ujawnić
tam, gdzie są istotne.

**Rozwinięcie i ślad wykonania:** Port publish powinien ujawniać potrzebną semantykę sukcesu: zaplanowanie
lokalne, trwały enqueue albo potwierdzenie brokera. Adapter mapuje wyjątki techniczne na uzgodnione kategorie.
Ukrycie szczegółów SDK nie może prowadzić do ukrycia istotnego ryzyka podwójnej publikacji.

**Ćwiczenie sprawdzające:** Zdefiniuj, czy sukces publish oznacza enqueue lokalne czy potwierdzenie brokera.

## Polling Consumer / Event-driven Consumer

**Intuicja:** Konsument może sam pytać o pracę albo otrzymywać wywołanie od mechanizmu transportu. Polling
daje kontrolę chwili i wielkości pobrania, Event-driven Consumer reaguje na sygnał dostępności. Żaden wariant
nie ustala samodzielnie momentu ack i granicy transakcji handlera.

**Problem i mechanizm:** Konsument sam pobiera pracę albo dostaje wywołania od infrastruktury przy pojawieniu
się danych.

**Role i współpraca:** Poller lub listener odbiera, handler wykonuje przypadek użycia, infrastruktura
potwierdza według wyniku. Limit in-flight ogranicza pracę jeszcze nieukończoną.

**Przykład:** Worker polluje co 100 ms; alternatywnie listener dostaje wiadomość i potwierdza po commicie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<Message> batch = queue.poll(20, Duration.ofMillis(100));
for (Message message : batch) {
    handler.apply(message);
    queue.ack(message);
}
```

**Konkretny przypadek użycia:** Dwie formy odbioru

- **Wejście:** Polling pobiera po 20, listener dostaje callback przy dostępności.
- **Przebieg:** Oba warianty stosują limit in-flight i ack po uzgodnionym commicie.
- **Oczekiwany wynik:** Wybrany model ma jawny shutdown i obsługę błędu.
- **Błąd lub granica:** Callback sam nie zapewnia backpressure ani trwałości przyjęcia.

**Kiedy zastosować:** Wybieraj według możliwości transportu, latency i kontroli obciążenia. Callback nie jest
dowodem braku kolejki ani automatycznego backpressure.

**Koszt i pułapka:** Polling zużywa zasoby przy pustej kolejce, callback wymaga kontroli współbieżności i
backpressure.

**Rozwinięcie i ślad wykonania:** W pollingu odbiorca kontroluje moment pobrania i rozmiar porcji. W wariancie
callbackowym musi ograniczyć równoległe wywołania i nie potwierdzić przed commitem. Oba modele potrzebują
określonego shutdown: koniec pobierania nie oznacza zakończenia już przyjętych wiadomości.

**Ćwiczenie sprawdzające:** Porównaj opóźnienie, shutdown i maksymalną liczbę niepotwierdzonych wiadomości.

## Competing Consumers / Message Dispatcher

**Intuicja:** Wielu workerów może dzielić jedną logiczną kolejkę, aby zwiększyć wydajność. Competing Consumers
rozdziela różne dostarczenia między nich. Message Dispatcher może dalej wybierać lokalny handler. Trzeba
osobno określić klucz porządku, bo równoległe przetwarzanie nie zachowa automatycznie kolejności wszystkich
zdarzeń.

**Problem i mechanizm:** Wiele workerów dzieli pracę z jednego logicznego kanału; dispatcher może lokalnie
kierować ją do handlerów.

**Role i współpraca:** Grupa jest logicznym odbiorcą, instancje wykonawcami, partycje lub przydziały
jednostkami własności. Rebalance może wymusić ponowne dostarczenie pracy.

**Przykład:** Trzy instancje generują faktury z jednej kolejki; klucz partycji zachowuje wymaganą kolejność
per konto.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
jedna subskrypcja Billing:
    partycja 0 -> worker A
    partycja 1 -> worker B
    partycja 2 -> worker C

Inna subskrypcja Search ma własny postęp tego samego strumienia.
```

**Konkretny przypadek użycia:** Grupa trzech workerów

- **Wejście:** Sześć kont trafia na 3 partycje po accountId.
- **Przebieg:** Grupa dzieli partycje, dispatcher wybiera lokalny handler.
- **Oczekiwany wynik:** Konta różnych partycji mogą robić postęp równolegle.
- **Błąd lub granica:** Jeden hot account nadal ma granicę sekwencji; więcej workerów niż partycji nie musi
  pomagać.

**Kiedy zastosować:** Dla skalowania niezależnych zadań. Jeden wymagający porządku hot key nadal może mieć
jednego aktywnego właściciela.

**Koszt i pułapka:** Więcej konsumentów niż partycji może nie zwiększyć równoległości; rebalance może
spowodować replay.

**Rozwinięcie i ślad wykonania:** Równoległość zależy od liczby niezależnych kluczy lub partycji. Jeden gorący
accountId może nadal być obsługiwany sekwencyjnie przez jednego właściciela. Skalowanie liczby workerów nie
usuwa tej granicy, jeśli poprawność wymaga porządku dla danego konta.

**Ćwiczenie sprawdzające:** Podziel sześć kont na trzy partycje i zachowaj kolejność dla jednego konta.

## Selective Consumer

**Intuicja:** Nie każdy worker musi odbierać dowolny komunikat. Selective Consumer wybiera podzbiór według
uzgodnionych właściwości, np. priorytetu lub regionu. Może ograniczyć późniejsze filtrowanie, ale przy złej
polityce pozostawi nieobsługiwane albo wiecznie odkładane klasy wiadomości.

**Problem i mechanizm:** Odbiorca jawnie wybiera podzbiór komunikatów według właściwości lub kryteriów
transportu.

**Role i współpraca:** Selektor opisuje podzbiór, transport lub odbiorca stosuje go przy pobraniu,
konfiguracja zapewnia obsługę reszty. Każda klasa potrzebuje limitu i właściciela postępu.

**Przykład:** Worker premium pobiera wyłącznie priority=high, a osobny worker obsługuje pozostałe.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Subscription premium = broker.subscribe(channel, header("priority").is("high"));
Subscription normal = broker.subscribe(channel, header("priority").isNot("high"));
// Obie klasy mają własną pojemność i obserwowany czas oczekiwania.
```

**Konkretny przypadek użycia:** Praca premium i standard

- **Wejście:** Selekcja kieruje high do szybkiej puli, resztę do zwykłej.
- **Przebieg:** Konfiguracja zapewnia obsługę obu zakresów i limit per klient.
- **Oczekiwany wynik:** Wyższy priorytet nie oznacza braku postępu zwykłej pracy.
- **Błąd lub granica:** Nieograniczony premium może zagłodzić eksport, jeśli nie ma wag lub aging.

**Kiedy zastosować:** Gdy możliwości transportu pozwalają sensownie rozdzielić klasy pracy. Zapewnij
sprawiedliwość zamiast pozwalać klientom bez ograniczeń deklarować najwyższy priorytet.

**Koszt i pułapka:** Selekcja może zagłodzić inne klasy; możliwości i koszt filtrów zależą od brokera.

**Rozwinięcie i ślad wykonania:** Selektor premium może poprawić latency tej klasy, ale trzeba zagwarantować
postęp zwykłej pracy. Zastosuj osobny limit, wagę lub aging zamiast nieskończonego pierwszeństwa. Zmiana
priorytetu komunikatu powinna być kontrolowana, żeby klient nie nadawał sobie stale najwyższej klasy.

**Ćwiczenie sprawdzające:** Zaprojektuj limit opóźnienia dla niskiego priorytetu.

## Durable Subscriber

**Intuicja:** Odbiorca może być niedostępny, kiedy producent publikuje zdarzenia. Durable Subscriber zachowuje
tożsamość subskrypcji i postęp, aby po powrocie można było kontynuować. Działa jednak tylko w zakresie danych
nadal dostępnych w retencji i kontraktu wybranego transportu.

**Problem i mechanizm:** Postęp subskrypcji jest zachowany podczas nieobecności odbiorcy, w granicach retencji
transportu.

**Role i współpraca:** Subskrypcja identyfikuje odbiorcę logicznego, offset lub ack zapisuje postęp, broker
zachowuje nieprzetworzone dane zgodnie z polityką.

**Przykład:** Billing wraca po godzinie i kontynuuje od utrwalonego offsetu.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Billing zatrzymuje się po pozycji 120
broker nadal przechowuje 121..180
Billing wraca z tym samym ID subskrypcji
odczyt wznawia się według utrwalonego postępu i retencji
```

**Konkretny przypadek użycia:** Powrót konsumenta

- **Wejście:** Billing kończy na pozycji 120, wraca po godzinie, dostępne jest 121–180.
- **Przebieg:** Ta sama trwała subskrypcja wznawia według zapisanego postępu.
- **Oczekiwany wynik:** Odbiorca nadrabia zaległość bez zależności od chwilowej dostępności przy publikacji.
- **Błąd lub granica:** Miesiąc przerwy przy retencji 7 dni wymaga rebuild albo kontrolowanego błędu.

**Kiedy zastosować:** Dla reakcji, które nie mogą znikać tylko dlatego, że odbiorca chwilowo nie działa. Długi
downtime może wymagać pełnej odbudowy poza krótką retencją kolejki.

**Koszt i pułapka:** Durable nie oznacza nieskończonej retencji ani dokładnie jednego skutku; długi downtime
może wymagać rebuild.

**Rozwinięcie i ślad wykonania:** Durable subskrypcja przechowuje punkt wznowienia, lecz dane muszą nadal
istnieć w retencji. Gdy offset wskazuje zbyt stary fragment, zwykłe ponowienie nie odtworzy historii. Określ
wtedy rebuild, snapshot albo kontrolowany błąd wymagający działania operatora.

**Ćwiczenie sprawdzające:** Porównaj godzinę i miesiąc przerwy przy retencji siedmiu dni.

## Idempotent Receiver / Inbox

**Intuicja:** Po utracie ack transport może ponowić wiadomość, której efekt już zapisano. Idempotent Receiver
rozpoznaje taki duplikat i nie tworzy drugiego lokalnego skutku. Inbox przechowuje marker wraz z efektem w
jednej transakcji, aby awaria nie zostawiła markera blokującego niewykonaną pracę.

**Problem i mechanizm:** Marker przetworzenia i lokalny efekt są utrwalane razem, aby duplikat nie tworzył
kolejnego skutku.

**Role i współpraca:** EventId identyfikuje fakt, tabela processed marker, transakcja wspólny zapis.
Zewnętrzny email lub HTTP potrzebuje własnej granicy idempotencji.

**Przykład:** Inbox zapisuje eventId i zlecenie wysyłki w jednym snapshotcie; druga kopia eventu niczego nie
dodaje.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
transaction.run(() -> {
    if (processed.contains(event.id())) return;
    shipments.insert(event.orderId());
    processed.insert(event.id());
});
```

**Konkretny przypadek użycia:** Duplikat eventu wysyłki

- **Wejście:** E1 przychodzi dwa razy po crash relay.
- **Przebieg:** Inbox zapisuje processed E1 i lokalne zlecenie w jednym commicie.
- **Oczekiwany wynik:** Powstaje jedno zlecenie wysyłki.
- **Błąd lub granica:** Marker zapisany osobno przed efektem mógłby na zawsze zablokować niewykonaną pracę.

**Kiedy zastosować:** Gdy dostarczenia mogą się powtarzać. Nie zakładaj, że zwykły HashSet przed zewnętrznym
wywołaniem daje trwałe exactly-once.

**Koszt i pułapka:** Marker zapisany przed efektem lub w innej transakcji gubi pracę. Zewnętrzny efekt wymaga
własnej idempotencji.

**Rozwinięcie i ślad wykonania:** Inbox zapisuje eventId i zlecenie w jednym snapshotcie. Jeśli przerwiemy
przed commitem, przy kolejnym podejściu nie ma markera blokującego wykonanie. Wysłanie prawdziwego HTTP wymaga
następnej granicy idempotencji: lokalny zapis markera nie uczyni zewnętrznego efektu częścią transakcji.

**Ćwiczenie sprawdzające:** Wywołaj awarię przed commitem i ponów tę samą wiadomość.

## Transactional Client

**Intuicja:** Odbiór i wysłanie kolejnych wiadomości może wymagać wspólnego zatwierdzenia w obrębie
transportu. Transactional Client korzysta z transakcyjnego API, aby uniknąć połowicznego wyniku w tym
zakresie. Trzeba wiedzieć, jakie zasoby naprawdę należą do transakcji, zamiast zakładać, że obejmuje cały
system.

**Problem i mechanizm:** Operacje na wiadomościach wiąże się z transakcją zgodnie z gwarancjami transportu.

**Role i współpraca:** Sesja transportu grupuje operacje, commit potwierdza ich wspólny wynik. Niezależna baza
i dowolne HTTP nie dołączają do niej tylko przez obecność tej samej metody Java.

**Przykład:** Odbiór oraz wysłanie dwóch wiadomości odbywają się w jednej transakcji sesji brokera.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
brokerTransaction.run(session -> {
    Message input = session.receive("orders");
    session.send("shipping", map(input));
    session.ack(input);
});
// Ten zakres sam nie obejmuje obcej bazy SQL.
```

**Konkretny przypadek użycia:** Transakcja brokera

- **Wejście:** Odbiór A i publikacja B mają być atomowe w uzgodnionej sesji transportu.
- **Przebieg:** Transactional Client grupuje te operacje zgodnie z kontraktem narzędzia.
- **Oczekiwany wynik:** Awaria kończy je wspólną decyzją w tym zakresie.
- **Błąd lub granica:** Obca baza SQL i HTTP nie dołączają automatycznie do tej samej transakcji.

**Kiedy zastosować:** Gdy broker oferuje potrzebny zakres atomowości. Dla zmiany DB i publikacji bez wspólnego
protokołu rozważ Outbox.

**Koszt i pułapka:** Transakcja brokera zwykle nie obejmuje dowolnej zewnętrznej DB lub HTTP; potrzebny jest
outbox albo jawny protokół.

**Rozwinięcie i ślad wykonania:** Odbiór oraz wysyłka w jednej transakcji transportu mogą być atomowe
wyłącznie w jej zdefiniowanym zakresie. Zapis w cudzej bazie nie dołącza automatycznie do tej transakcji.
Zaznacz zasoby na diagramie, a potem wprowadź awarię między ich commitami, aby sprawdzić potrzebę Outbox.

**Ćwiczenie sprawdzające:** Wskaż dokładnie zasoby objęte transakcją i skutek crash po DB commit.

## Guaranteed Delivery / Store-and-forward

**Intuicja:** Komunikat nie powinien znikać tylko dlatego, że pośrednik zatrzymał proces po przyjęciu danych.
Store-and-forward utrwala odpowiedzialność na kolejnych etapach dostawy. „Guaranteed” wymaga jednak dokładnego
modelu awarii, konfiguracji replik i zasad potwierdzenia; nie oznacza nieskończonej odporności ani jednego
skutku biznesowego.

**Problem i mechanizm:** Transport utrwala wiadomości i przekazuje je po odzyskaniu dostępności zgodnie z
kontraktem potwierdzeń.

**Role i współpraca:** Nadawca czeka na określony ack, transport przechowuje, konsument potwierdza uzgodniony
postęp. Utrata ack pozostawia wynik nieznany i może wywołać retry.

**Przykład:** Producent czeka na ack trwałego zapisu, a broker zachowuje event do czasu postępu odbiorcy.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
producent -> trwałe przyjęcie transportu -> ack producenta
transport -> odbiorca -> lokalny commit -> ack odbiorcy

Brak ack może oznaczać utratę potwierdzenia, nie brak zapisu.
```

**Konkretny przypadek użycia:** Potwierdzenie trwałego przyjęcia

- **Wejście:** Producent traci połączenie przed otrzymaniem ack.
- **Przebieg:** Store-and-forward utrzymuje wiadomość według kontraktu, producent może ponowić stabilne ID.
- **Oczekiwany wynik:** Odbiorca ma szansę wykonać pracę po odzyskaniu dostępności.
- **Błąd lub granica:** Brak ack nie dowodzi braku zapisu; konfiguracja trwałości i retencji określa okno
  utraty.

**Kiedy zastosować:** Gdy praca ma przetrwać chwilową niedostępność stron. Sprawdź, co faktycznie potwierdza
SDK: pamięć procesu, trwały zapis czy replikację.

**Koszt i pułapka:** Nazwa nie daje nieskończonej trwałości; konfiguracja replik, dysków i retencji określa
okno utraty.

**Rozwinięcie i ślad wykonania:** Nadawca po utracie ack nie wie, czy trwały zapis nastąpił, więc musi móc
ponowić z tym samym ID. Replikacja ogranicza ryzyko utraty zgodnie z konfiguracją, a nie nazwą wzorca.
Potwierdzenie pamięci procesu i potwierdzenie trwałego quorum są odmiennymi punktami uznania sukcesu.

**Ćwiczenie sprawdzające:** Opisz awarię producenta przed ack i powód możliwego duplikatu po retry.

## Dead Letter Channel / Dead Letter Queue

**Intuicja:** Wiadomość, która zawsze zawodzi, nie powinna blokować całej kolejki bez końca. Dead Letter
Channel oddziela ją po określonej polityce prób i zachowuje informacje potrzebne do naprawy. Jest ścieżką
operacyjną z właścicielem, nie miejscem, gdzie problemy mają przestać być widoczne.

**Problem i mechanizm:** Wiadomości po wyczerpaniu określonej polityki prób trafiają do kontrolowanej ścieżki
naprawy.

**Role i współpraca:** Polityka prób wyznacza koniec automatycznej obsługi, DLQ przechowuje oryginał i
przyczynę, redrive wznawia po naprawie z zachowaniem właściwej tożsamości.

**Przykład:** Event nieprzetworzony po pięciu próbach trafia z oryginalnym ID i przyczyną do DLQ.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
try {
    handler.apply(message);
} catch (TransientFailure failure) {
    if (attempt >= maxAttempts) dlq.store(message, failure, attempt);
    else retryLater(message, attempt + 1);
}
```

**Konkretny przypadek użycia:** Wyczerpane próby

- **Wejście:** Event E42 pięć razy trafia na przejściowy błąd.
- **Przebieg:** Po limicie zapisujemy oryginał, liczbę prób i przyczynę w DLQ.
- **Oczekiwany wynik:** Naprawa ma dane potrzebne do kontrolowanego redrive.
- **Błąd lub granica:** Zmiana eventId podczas odtworzenia może ominąć wcześniejszą deduplikację.

**Kiedy zastosować:** Dla kontrolowanego końca retry. Alarm, retencja i bezpieczne odtwarzanie są częścią
rozwiązania; sama dodatkowa kolejka nie kończy procesu biznesowego.

**Koszt i pułapka:** DLQ bez alarmu, retencji i bezpiecznego redrive staje się magazynem utraconej pracy.

**Rozwinięcie i ślad wykonania:** DeadLetter zachowuje oryginalną wiadomość, liczbę prób i przyczynę. Naprawa
powinna wskazać właściciela oraz sposób redrive z zachowaniem ID. Zmiana ID przy każdym odtworzeniu może
ominąć Inbox i stworzyć ponowny skutek, mimo że transport uznaje wiadomość za nową.

**Ćwiczenie sprawdzające:** Napraw payload i wykonaj redrive z zachowaniem klucza deduplikacji.

## Invalid Message Channel / Quarantine

**Intuicja:** Nieznana wersja albo niemożliwa liczba sztuk nie naprawi się przez ponowienie za sekundę.
Invalid Message Channel kieruje takie dane do kwarantanny od razu. Pozwala odróżnić niepoprawny kontrakt od
przejściowej awarii infrastruktury i dobrać inną procedurę działania.

**Problem i mechanizm:** Niepoprawne schematycznie lub semantycznie dane oddziela się od błędów przejściowych.

**Role i współpraca:** Walidator rozpoznaje dane, kwarantanna zachowuje bezpieczny ślad, właściciel kontraktu
decyduje o poprawce. Dane wrażliwe nie powinny bez kontroli trafiać do ogólnego logu błędów.

**Przykład:** Event z ujemną liczbą sztuk trafia do kwarantanny bez 100 prób wywołania tej samej walidacji.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
ValidationResult result = validator.check(envelope);
if (!result.valid()) {
    quarantine.store(envelope, result.safeReasons());
    return;
}
handler.apply(result.decodedMessage());
```

**Konkretny przypadek użycia:** Nieznany schemat

- **Wejście:** Konsument zna v1/v2, przychodzi v9 albo ujemna ilość.
- **Przebieg:** Walidator kieruje dane do kwarantanny bez bezsensownego retry.
- **Oczekiwany wynik:** Błąd kontraktu jest widoczny i ma właściciela naprawy.
- **Błąd lub granica:** Nie logujemy bez kontroli całego payloadu z PII; awaria walidatora wymaga odrębnej
  diagnozy.

**Kiedy zastosować:** Dla błędów trwałych wejścia. Błąd programisty w walidatorze nie powinien być
automatycznie uznany za winę producenta.

**Koszt i pułapka:** Trzeba chronić PII w odrzuconym payloadzie i mieć właściciela naprawy.

**Rozwinięcie i ślad wykonania:** Walidacja schematu jest błędem trwałym, więc FulfillmentPipeline nie
powtarza jej dziesięć razy jak niedostępności bazy. Oddziel kwarantannę od DLQ po retry, nawet jeśli
technicznie dzielą store. Pozwala to dobrać inny alarm, retencję oraz procedurę naprawy.

**Ćwiczenie sprawdzające:** Oddziel nieznaną wersję schematu od timeout bazy.

## Messaging Bridge / Message Bus

**Intuicja:** Dwie platformy transportowe mogą wymagać wymiany wiadomości bez przepisywania wszystkich
producentów. Messaging Bridge łączy ich kanały i tłumaczy wymagane metadane. Message Bus opisuje szerszy
wspólny szkielet komunikacji; nie jest po prostu dowolną globalną klasą do wywoływania metod.

**Problem i mechanizm:** Bridge łączy dwa systemy wiadomości; bus oferuje wspólny szkielet komunikacji i
kontrakty routingu.

**Role i współpraca:** Most odbiera ze źródła, publikuje w celu i potwierdza źródło po uzgodnionym przyjęciu.
Stabilne ID i ochrona przed pętlą zachowują sens retry.

**Przykład:** Most JMS → Kafka przenosi ID i nagłówki oraz potwierdza źródło dopiero po zapisie celu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Message message = source.receive();
destination.publishPreservingId(message);
source.ack(message);
// Crash po publish przed ack oznacza możliwy duplikat w celu.
```

**Konkretny przypadek użycia:** Most JMS–Kafka

- **Wejście:** Źródło i cel mają niezależne potwierdzenia.
- **Przebieg:** Most publikuje z zachowaniem ID, dopiero potem potwierdza źródło.
- **Oczekiwany wynik:** Praca jest przekazywana bez odwrócenia kolejności prowadzącej do utraty.
- **Błąd lub granica:** Crash po celu przed ack źródła daje duplikat i wymaga ochrony odbiorcy oraz
  zapobiegania pętlom.

**Kiedy zastosować:** Przy migracji brokerów i łączeniu obszarów integracji. Uwzględnij różnice kolejności,
rozmiaru wiadomości i zakresu transakcji obu stron.

**Koszt i pułapka:** Pętle, inna kolejność i semantyka potwierdzeń wymagają kontroli; bus może skupiać zbyt
wiele transformacji.

**Rozwinięcie i ślad wykonania:** Most odczytuje źródło, publikuje cel i dopiero potem potwierdza źródło.
Awaria między publikacją i ack daje duplikat, a odwrócenie kolejności daje utratę. Stabilny identyfikator,
reguła zapobiegania pętlom i deduplikacja są więc częścią kontraktu mostu, nie dodatkiem diagnostycznym.

**Ćwiczenie sprawdzające:** Zasymuluj crash po zapisie celu przed ack źródła.

## Wire Tap / Message History

**Intuicja:** Diagnoza przepływu bywa potrzebna bez zmiany jego wyniku biznesowego. Wire Tap tworzy boczną
kopię, a Message History zachowuje ślad etapów. Pozwalają sprawdzić, gdzie dotarła wiadomość, lecz nie powinny
nieświadomie zamienić opcjonalnej telemetrii w blokującą zależność krytycznego przetwarzania.

**Problem i mechanizm:** Boczna kopia lub ślad etapów pozwala obserwować przepływ bez zmiany jego wyniku
biznesowego.

**Role i współpraca:** Główny handler wykonuje pracę, tap dostarcza kopię diagnostyczną, historia wiąże etapy
z ID. Retencja i minimalizacja danych obejmują także te boczne ścieżki.

**Przykład:** Diagnostyczna subskrypcja zapisuje ID i nazwy etapów OrderPlaced z maskowaniem wrażliwych
danych.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
diagnostics.tryCopy(message.id(), message.type(), currentStage);
businessHandler.apply(message);
// Błąd opcjonalnego tap nie powinien udawać błędu biznesowego.
```

**Konkretny przypadek użycia:** Boczna diagnostyka

- **Wejście:** Chcemy śledzić etapy o42 bez blokowania głównego handlera.
- **Przebieg:** Wire Tap kopiuje bezpieczne ID i nazwy etapów do diagnostyki.
- **Oczekiwany wynik:** Brak kolektora nie udaje błędu biznesowego procesu.
- **Błąd lub granica:** Obowiązkowy audyt ma silniejszy kontrakt niż opcjonalna kopia telemetryczna.

**Kiedy zastosować:** Dla obserwacji i analizy integracji. Gdy wymagany jest trwały audyt, potrzebny jest
kontrakt silniejszy niż best-effort kopia telemetryczna.

**Koszt i pułapka:** Telemetria nie powinna blokować głównego procesu; kopie zwiększają ekspozycję danych i
retencję.

**Rozwinięcie i ślad wykonania:** Wire Tap kopiuje obserwację, ale nie powinien stać się drugim wymaganym
commitem krytycznej ścieżki. Jeśli audyt musi być niezawodny, potrzebuje trwalszego modelu niż best-effort
telemetry. Message History może zwiększać kopertę, więc ogranicz długość i unikaj dopisywania wrażliwych
payloadów każdego etapu.

**Ćwiczenie sprawdzające:** Wyłącz kolektor i sprawdź, czy przetwarzanie nadal działa.

## Message Store / Replay

**Intuicja:** Po naprawie błędu projekcji może być potrzebna historia, a nie tylko aktualne rekordy. Message
Store zachowuje wiadomości do analizy i odtworzenia. Replay powinien mieć określony cel: zbudowanie widoku nie
może przypadkowo ponowić płatności albo wysłać wszystkich historycznych powiadomień.

**Problem i mechanizm:** Komunikaty lub ich wybrane kopie zachowuje się do audytu, diagnostyki i odtwarzania
projekcji.

**Role i współpraca:** Archiwum przechowuje kontrakty, replayer odczytuje od checkpointu, handler odbudowy
tworzy dane pochodne bez niedozwolonych skutków. Retencja i stare schematy ograniczają zakres możliwej
odbudowy.

**Przykład:** Archiwum OrderPlaced umożliwia zbudowanie nowej wyszukiwarki od znanego checkpointu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
for (Message event : archive.after(checkpoint)) {
    readModel.applyIdempotently(event);
    checkpoints.advance(event.position());
}
// Handler odbudowy nie wysyła maili ani nie pobiera opłat.
```

**Konkretny przypadek użycia:** Odbudowa wyszukiwarki

- **Wejście:** Nowy indeks ma przetworzyć miesiąc OrderPlaced.
- **Przebieg:** Replayer czyta archiwum od checkpointu i aktualizuje wyłącznie read model.
- **Oczekiwany wynik:** Powstaje odtworzony widok bez powtarzania efektów biznesowych.
- **Błąd lub granica:** Handler replay nie może znów wysłać historycznych maili ani pobrać płatności.

**Kiedy zastosować:** Gdy historia ma rzeczywistą wartość operacyjną. Nie myl dowolnego archiwum z Event
Sourcing, gdzie historia jest formalnym źródłem prawdy stanu.

**Koszt i pułapka:** Replay nie może ponownie wysłać maili ani płatności; retencja musi uwzględniać dane
osobowe i stare schematy.

**Rozwinięcie i ślad wykonania:** Replay ma odrębny cel wykonania: odbudowa projekcji nie powinna uruchamiać
ponownie reakcji skutkujących mailem lub płatnością. Wersjonowanie dekoderów i checkpoint pozwalają
kontrolować postęp. Retencja samego transportu może być zbyt krótka, jeśli odbudowa wymaga starszych danych
niż dostępne w topicu.

**Ćwiczenie sprawdzające:** Oddziel odbudowę read modelu od wykonywania skutków ubocznych.
