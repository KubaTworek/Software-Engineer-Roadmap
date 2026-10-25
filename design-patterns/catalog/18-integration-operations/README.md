# Style integracji i zarządzanie wiadomościami

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#eip)

Uzupełnienie katalogu EIP. **9 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [złożony proces routingu i zbierania odpowiedzi](../../labs/02-messaging.md).
Studium w Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Integracja obejmuje wybór sposobu wymiany, połączenie transportu z aplikacją
oraz narzędzia zarządzania przepływem. Niektóre karty opisują cały styl, inne pojedynczą rolę przy wiadomości.
Dlatego najpierw ustal poziom decyzji: protokół, endpoint, proces czy operacja diagnostyczna. Wzorce
zarządzania wymagają własnych uprawnień i nie są poleceniami wykonywanymi automatycznie przez ten materiał.

## Spis kart

-
  [File Transfer / Shared Database / Remote Procedure Invocation / Messaging](#file-transfer--shared-database--remote-procedure-invocation--messaging)
- [Message Endpoint / Service Activator](#message-endpoint--service-activator)
- [Composed Message Processor](#composed-message-processor)
- [Message Broker](#message-broker)
- [Control Bus](#control-bus)
- [Detour](#detour)
- [Smart Proxy](#smart-proxy)
- [Test Message](#test-message)
- [Channel Purger](#channel-purger)

## File Transfer / Shared Database / Remote Procedure Invocation / Messaging

**Intuicja:** Systemy mogą wymieniać pliki, współdzielić bazę, wywoływać się zdalnie lub przekazywać
wiadomości. Każdy styl inaczej sprzęga format, czas dostępności i odpowiedzialność. Dzienny CSV nie daje tego
samego latency co RPC, a wspólna tabela wiąże schemat obu aplikacji silniej niż uzgodniony event.

**Problem i mechanizm:** Styl integracji określa, czy strony wymieniają pliki, współdzielą dane, wywołują
zdalne operacje czy przekazują wiadomości.

**Role i współpraca:** Nadawca, odbiorca i medium wyznaczają punkt przekazania danych. Kontrakt mówi, kiedy
wynik jest przyjęty i jak znaleźć brakującą pracę.

**Przykład:** Partner wysyła dzienny CSV, dwa moduły czytają wspólny schemat, API wykonuje RPC, a event trafia
przez broker.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
File Transfer: A zapisuje plik, B importuje porcję
Shared Database: A i B korzystają z uzgodnionego wspólnego schematu
RPC: A czeka na odpowiedź operacji B
Messaging: A przekazuje komunikat, B ma własny postęp odbioru
```

**Konkretny przypadek użycia:** Wymiana danych ze starszym partnerem

- **Wejście:** Partner udostępnia nocny plik, wewnętrzny moduł potrzebuje szybkiej odpowiedzi, inne odbiorniki
  pracują asynchronicznie.
- **Przebieg:** Plik obsługuje transfer wsadowy, RPC bieżącą odpowiedź, messaging niezależną dostawę; shared
  DB wymaga uzgodnionego modelu.
- **Oczekiwany wynik:** Styl integracji odpowiada opóźnieniu, autonomii i wymaganej dostępności uczestników.
- **Błąd lub granica:** Wspólna tabela sprzęga schemat i transakcje, RPC sprzęga dostępność, a messaging
  wymaga obsługi duplikatów oraz opóźnień.

**Kiedy zastosować:** Według potrzeb biznesowych, nie samej popularności transportu. Jedna integracja może
świadomie łączyć kilka stylów dla różnych operacji.

**Koszt i pułapka:** Każdy styl ma inne sprzężenie czasowe i własność danych; wspólna baza wymaga ścisłej
kontroli kontraktu.

**Rozwinięcie i ślad wykonania:** Plik daje porcję i opóźnienie, wspólna baza silne sprzężenie schematu, RPC
sprzężenie dostępności, messaging własny postęp odbiorców. Zacznij od potrzeb procesu, a nie listy
technologii. Każdy styl musi określić, kiedy nadawca uznaje przekazanie odpowiedzialności i jak wykrywa
utraconą pracę.

**Ćwiczenie sprawdzające:** Porównaj cztery style dla partnera dostępnego tylko raz dziennie.

## Message Endpoint / Service Activator

**Intuicja:** Biblioteka brokera dostarcza kopertę, a aplikacja potrzebuje komendy domenowej. Message Endpoint
łączy te światy, a Service Activator mapuje wiadomość na wywołanie usługi. Rozdzielenie pozwala testować use
case bez brokera i nie zamykać całej domeny w callbacku listenera.

**Problem i mechanizm:** Endpoint łączy aplikację z transportem, a activator przekłada wiadomość na wywołanie
usługi.

**Role i współpraca:** Endpoint odczytuje techniczny format, mapper buduje komendę, application service
wykonuje reguły, ack respektuje wynik i granicę trwałości.

**Przykład:** Listener mapuje ReserveStock na Inventory.reserve i potwierdza dopiero po uzgodnionej granicy
trwałości.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
void onMessage(Envelope envelope) {
    ReserveStock command = mapper.toCommand(envelope);
    reservationService.execute(command);
    broker.ack(envelope.deliveryHandle());
}
```

**Konkretny przypadek użycia:** Adapter odbierający komendę wysyłki

- **Wejście:** Kanał zawiera ShipOrder z messageId i orderId.
- **Przebieg:** Message Endpoint tłumaczy protokół transportu, Service Activator wywołuje metodę aplikacyjną i
  mapuje wynik obsługi.
- **Oczekiwany wynik:** Domena dostaje jawne polecenie bez zależności od API brokera.
- **Błąd lub granica:** Potwierdzenie przed trwałym skutkiem grozi utratą pracy; po skutku może nastąpić
  ponowna dostawa.

**Kiedy zastosować:** Gdy transport ma być adapterem aplikacji. Potwierdzenie przed trwałym przyjęciem może
zgubić pracę po awarii.

**Koszt i pułapka:** Automatyczny ack przed zakończeniem usługi gubi pracę; wyjątki muszą być klasyfikowane.

**Rozwinięcie i ślad wykonania:** Endpoint odbiera techniczną kopertę, activator wywołuje właściwy use case, a
ack potwierdza uzgodnioną granicę przyjęcia. Umieszczenie całej domeny w listenerze utrudnia testy i
uruchomienie innym adapterem. Ten sam przypadek użycia powinien zachować reguły przy wejściu z HTTP albo
kolejki.

**Ćwiczenie sprawdzające:** Wskaż, kto kontroluje commit, ack i redelivery.

## Composed Message Processor

**Intuicja:** Złożona wiadomość często wymaga zarówno podziału, jak i różnych ścieżek oraz wspólnego wyniku.
Composed Message Processor łączy Splitter, Router i Aggregator w jeden jawny proces. Wspólne ID pozwala
zachować związek części, lecz pełny sukces nadal wymaga reguły kompletności i czasu.

**Problem i mechanizm:** Wzorzec łączy Splitter, routing i Aggregator w całościowy przepływ złożonej
wiadomości.

**Role i współpraca:** Splitter tworzy części, router wybiera właścicieli, agregator zbiera odpowiedzi. Każdy
etap ma własną granicę błędu, a całość właściciela końca procesu.

**Przykład:** Zamówienie jest dzielone per magazyn, fragmenty trafiają do właścicieli, wyniki tworzą zbiorczy
status.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
List<Part> parts = splitter.split(order);
Aggregator process = aggregators.start(order.id(), parts.ids(), deadline);
parts.forEach(router::send);
// Odpowiedzi wracają do process z orderId i partId.
```

**Konkretny przypadek użycia:** Przetworzenie wielopozycyjnego importu

- **Wejście:** Wiadomość zawiera 100 pozycji, z których 8 nie spełnia warunków.
- **Przebieg:** Splitter dzieli, filter odrzuca, router wybiera procesory, aggregator składa wynik po
  identyfikatorze korelacji.
- **Oczekiwany wynik:** Raport importu podaje wyniki zaakceptowanych i odrzuconych pozycji.
- **Błąd lub granica:** Aggregator musi rozstrzygnąć timeout, brakujące części i duplikaty; samo otrzymanie 92
  odpowiedzi nie opisuje losu wszystkich 100 pozycji.

**Kiedy zastosować:** Dla dokumentów i zamówień obsługiwanych częściowo przez różnych wykonawców. Nie kończ
projektu na strzałkach split–join bez polityki duplikatów i braków.

**Koszt i pułapka:** Wymaga pełnej reguły kompletności, timeoutu i częściowej porażki; samo split plus join
nie wystarcza.

**Rozwinięcie i ślad wykonania:** Splitter tworzy identyfikowane fragmenty, router wybiera kanały, agregator
ocenia kompletność i wynik. FulfillmentPipeline pokazuje te kroki na dwóch magazynach, w tym duplikat i
deadline. W procesie z trwałym transportem trzeba dodatkowo utrwalić stan rozmowy i zamiary wysyłki, żeby nie
zgubić postępu po restarcie.

**Ćwiczenie sprawdzające:** Dostarcz jeden wynik dwa razy, drugi po terminie i trzeci z błędem.

## Message Broker

**Intuicja:** Bez pośrednika każdy producent mógłby znać adres i chwilową dostępność każdego odbiorcy. Broker
zapewnia wspólny mechanizm przekazania, kanałów i ewentualnie routingu oraz retencji. Może odseparować strony
w czasie, ale sam staje się systemem stanowym o określonych limitach i trybach awarii.

**Problem i mechanizm:** Centralny komponent zarządza transportem i ewentualnie routingiem między nadawcami a
odbiorcami.

**Role i współpraca:** Producent publikuje, broker przyjmuje odpowiedzialność zgodnie z ack, konsument
potwierdza własny postęp. Gwarancje zależą od produktu i konfiguracji.

**Przykład:** Producent publikuje OrderPlaced do brokera, który dostarcza do subskrypcji zgodnie z regułami
kanału.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
producer -> broker/topic -> subscription -> consumer
               |
          retencja i routing według kontraktu

Ack producenta i commit konsumenta są różnymi zdarzeniami.
```

**Konkretny przypadek użycia:** Centralny routing zdarzeń zamówień

- **Wejście:** Billing i shipping konsumują różne zdarzenia z jednej przestrzeni komunikacji.
- **Przebieg:** Broker przyjmuje, przechowuje i kieruje wiadomości według kanałów i subskrypcji.
- **Oczekiwany wynik:** Nadawca nie musi znać adresów wszystkich konsumentów.
- **Błąd lub granica:** Broker nie zna pełnych reguł procesu biznesowego i może stać się wąskim gardłem;
  trwałość i ack wymagają jawnej konfiguracji.

**Kiedy zastosować:** Gdy pośrednictwo oraz przechowanie pracy mają wartość. Nie utożsamiaj klastra brokera z
brakiem utraty i dokładnie jednym skutkiem aplikacji.

**Koszt i pułapka:** Broker może stać się wąskim gardłem lub miejscem nadmiernej logiki; gwarancje zależą od
produktu i konfiguracji.

**Rozwinięcie i ślad wykonania:** Broker przyjmuje wiadomość w swoim określonym punkcie trwałości i dostarcza
ją według kontraktu kanału. Awaria jednego lidera może być obsłużona przez repliki, lecz utrata całego klastra
ma inne skutki. Producent musi rozumieć ack i niepewny wynik, a konsument redelivery oraz retencję.

**Ćwiczenie sprawdzające:** Odróżnij zatrzymanie lidera brokera od utraty całego klastra.

## Control Bus

**Intuicja:** Podczas incydentu może być potrzebne zatrzymanie wybranej grupy konsumentów bez ręcznej edycji
każdego procesu. Control Bus daje kontrolowany kanał zarządzania. Operacje sterujące mają własny kontrakt
wykonania i uprawnienia, bo wpływają na dostępność oraz postęp całej integracji.

**Problem i mechanizm:** Operacje zarządzania przepływem mają osobny, kontrolowany kanał zamiast ręcznej
zmiany wielu komponentów.

**Role i współpraca:** Narzędzie operatora wysyła polecenie, agent sterowania je waliduje, komponent wykonuje
i raportuje stan. Kanał zarządzania nie powinien zależeć od dokładnie tego samego przeciążenia co dane.

**Przykład:** Polecenie PauseConsumer zatrzymuje pobieranie nowych wiadomości wybranej grupy bez utraty
checkpointu.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
operator -> kontrolowany kanał: PauseConsumer(group=Billing)
agent -> walidacja uprawnienia i zakresu
consumer -> wstrzymaj pobieranie, zachowaj checkpoint
agent -> potwierdź osiągnięty stan, nie tylko przyjęcie polecenia
```

**Konkretny przypadek użycia:** Zdalne wstrzymanie importera

- **Wejście:** Administrator musi zatrzymać przyjmowanie nowych plików, pozostawiając obsługę bieżących.
- **Przebieg:** Oddzielny kanał zarządzania przesyła autoryzowaną komendę pause i zbiera stan wykonania.
- **Oczekiwany wynik:** Importer raportuje paused po zastosowaniu zmiany, bez mieszania sterowania z danymi
  klientów.
- **Błąd lub granica:** Wysłanie komendy nie dowodzi zastosowania; kanał sterowania wymaga własnej kontroli
  dostępu i zachowania podczas awarii.

**Kiedy zastosować:** Dla rozproszonych operacji administracyjnych. Pause oznacza koniec nowych przyjęć według
reguły, nie potwierdzenie niewykonanych wiadomości.

**Koszt i pułapka:** Kanał sterowania wymaga silniejszej autoryzacji i audytu; sam nie powinien zależeć od
zatkanego kanału danych.

**Rozwinięcie i ślad wykonania:** Kanał sterowania powinien mieć odrębną autoryzację i obserwowalne
potwierdzenie wykonania polecenia. Pause nie może potwierdzać niewykonanej pracy ani trwale gubić checkpointu.
Podczas incydentu warto wiedzieć, czy sterowanie działa niezależnie od przeciążonej kolejki biznesowej.

**Ćwiczenie sprawdzające:** Zaplanuj wznowienie grupy i weryfikację, że pauza nie potwierdziła niewykonanej
pracy.

## Detour

**Intuicja:** Czasem wybrana wiadomość ma przejść dodatkową kontrolę bez stałej przebudowy całej integracji.
Detour wstawia tymczasowy odcinek trasy, po którym komunikat wraca do zwykłego przepływu. Wymaga zachowania
tożsamości i jasnej reguły dla wiadomości będących w locie podczas wyłączenia objazdu.

**Problem i mechanizm:** Przepływ jest czasowo kierowany przez dodatkowy etap diagnostyczny lub kontrolny.

**Role i współpraca:** Reguła wybiera zakres, etap diagnostyczny wykonuje dodatkową kontrolę, miejsce powrotu
zapobiega zgubieniu i pętli. Usunięcie konfiguracji nie anuluje już rozpoczętego etapu.

**Przykład:** Wybrany import przechodzi przez walidator kwarantanny, a po analizie wraca na pierwotną trasę.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
normalnie: input -> processor
detour:    input -> dodatkowy validator -> processor

zachowaj messageId i korelację; validator nie wysyła ponownie na input
```

**Konkretny przypadek użycia:** Czasowe dołączenie walidatora faktur

- **Wejście:** Normalna trasa prowadzi prosto do księgowania; nowe reguły mają być oceniane przez tydzień.
- **Przebieg:** Detour kieruje wybrane komunikaty przez dodatkowy etap i wraca na normalną ścieżkę.
- **Oczekiwany wynik:** Można ocenić walidator na ograniczonym ruchu i usunąć etap po eksperymencie.
- **Błąd lub granica:** Zmiana trasy może naruszyć kolejność i dublować przetwarzanie; musi mieć jawny moment
  aktywacji oraz korelację.

**Kiedy zastosować:** Dla kontrolowanej diagnostyki i dodatkowej walidacji. Objazd sam zmienia latency i
zachowanie, więc nie jest całkowicie neutralnym obserwatorem.

**Koszt i pułapka:** Tymczasowy etap może zmienić latency i porządek; powrót wymaga zachowania ID oraz braku
pętli.

**Rozwinięcie i ślad wykonania:** Detour zmienia trasę wybranego przepływu, zachowując jego ID i miejsce
powrotu. Wiadomości już będące w dodatkowym etapie potrzebują reguły dokończenia po wyłączeniu detour. Testuj
brak pętli i dodatkowych skutków, ponieważ narzędzie diagnostyczne również zmienia system obserwowany.

**Ćwiczenie sprawdzające:** Określ, jak usunąć detour przy wiadomościach będących już w locie.

## Smart Proxy

**Intuicja:** Pośrednik request-reply może zmienić identyfikatory lub adres odpowiedzi, których oryginalny
klient nie zna. Smart Proxy pamięta mapowanie i odtwarza rozmowę w drodze powrotnej. Samo przekazanie payloadu
nie wystarczy, jeśli korelacja opierała się na metadanych zmienionych przez transport.

**Problem i mechanizm:** Pośrednik zachowuje korelację request-reply i przywraca metadane mimo zmian adresów
oraz ID po drodze.

**Role i współpraca:** Proxy zapisuje pierwotne replyTo i correlationId, partner używa własnego ID, odpowiedź
wraca przez odwrotne mapowanie. Wpis wymaga scope, limitu i terminu.

**Przykład:** Proxy zapisuje oryginalne replyTo i correlationId, a odpowiedź partnera mapuje z powrotem na
rozmowę klienta.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
String partnerId = ids.next();
correlations.put(partnerId, originalReplyTo, originalCorrelationId, expiresAt);
partner.send(request.withCorrelationId(partnerId));
// Na odpowiedzi proxy odtwarza oryginalny adres i korelację.
```

**Konkretny przypadek użycia:** Korelacja odpowiedzi przez bramę

- **Wejście:** Klient używa replyTo, którego zewnętrzny system nie rozumie.
- **Przebieg:** Smart Proxy zapamiętuje korelację, ustawia własny adres odpowiedzi i przywraca kontekst przy
  powrocie.
- **Oczekiwany wynik:** Odpowiedź wraca do właściwego klienta mimo różnic protokołu.
- **Błąd lub granica:** Restart proxy może zgubić mapowanie; trzeba określić trwałość, timeout, czyszczenie i
  obsługę spóźnionej odpowiedzi.

**Kiedy zastosować:** Dla pośrednictwa zmieniającego metadane rozmowy. Późna odpowiedź nie może trafić do
innego klienta po ponownym użyciu identyfikatora.

**Koszt i pułapka:** Stan korelacji wymaga timeoutu, limitu rozmiaru i ochrony przed pomieszaniem tenantów.

**Rozwinięcie i ślad wykonania:** Proxy zapisuje mapowanie oryginalnej rozmowy na identyfikator partnera i
odtwarza adres odpowiedzi. Wpis ma termin oraz scope tenanta. Po timeout odpowiedź nie może trafić do innego
klienta przez przypadkowe ponowne użycie identyfikatora; usunięcie stanu jest częścią bezpieczeństwa
korelacji.

**Ćwiczenie sprawdzające:** Obsłuż dwie równoległe rozmowy oraz odpowiedź po usunięciu wpisu korelacji.

## Test Message

**Intuicja:** Ping brokera dowodzi połączenia, ale nie tego, że decoder, handler i zapis działają razem. Test
Message przechodzi istotną ścieżkę z bezpieczną syntetyczną operacją. Wynik końca daje mocniejszą obserwację
niż gotowość pojedynczego komponentu, pod warunkiem że probe nie uruchamia realnych skutków.

**Problem i mechanizm:** Kontrolny komunikat sprawdza faktyczną ścieżkę transportu i przetwarzania bez
niechcianego efektu biznesowego.

**Role i współpraca:** Nadawca probe wybiera kontrolny identyfikator, ścieżka respektuje jego kontrakt,
obserwator mierzy wynik końca. Retencja i sprzątanie danych testowych są określone.

**Przykład:** Syntetyczny OrderProbe przechodzi przez wydzieloną bezpieczną ścieżkę i daje mierzalne
potwierdzenie końca.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
wyślij Probe(probeId, safeTenant)
przejdź walidację i uzgodnioną kontrolną ścieżkę przetwarzania
zapisz ProbeCompleted(probeId)
monitor sprawdza czas końca i brak rzeczywistych skutków biznesowych
```

**Konkretny przypadek użycia:** Sonda rzeczywistej ścieżki wiadomości

- **Wejście:** Health HTTP jest zielony, ale podejrzewamy blokadę kolejki.
- **Przebieg:** Test Message przechodzi przez kontrolowaną ścieżkę z identyfikatorem sondy i oczekiwanym
  potwierdzeniem.
- **Oczekiwany wynik:** Brak potwierdzenia w czasie ujawnia problem przepływu, którego nie pokazał procesowy
  healthcheck.
- **Błąd lub granica:** Sonda musi być rozpoznawalna i nie może wywołać realnej płatności; jej sukces nie
  mierzy wydajności pod pełnym obciążeniem.

**Kiedy zastosować:** Dla monitorowania konkretnego przepływu. Nie oznaczaj dowolnego prawdziwego zamówienia
flagą test, jeśli reszta systemu jej bezpiecznie nie respektuje.

**Koszt i pułapka:** Ping brokera nie dowodzi działania konsumenta; probe nie może naliczać przychodu ani
wysyłać klientom maili.

**Rozwinięcie i ślad wykonania:** Syntetyczna wiadomość powinna przejść istotne etapy i pozostawić bezpieczny,
mierzalny wynik. Samo połączenie TCP z brokerem nie sprawdza dekodera, use case i commitu odbiorcy. Oznaczenie
probe musi być egzekwowane, żeby nie uruchomić prawdziwej faktury lub powiadomienia.

**Ćwiczenie sprawdzające:** Wskaż etap, który może zawieść mimo poprawnego połączenia z brokerem.

## Channel Purger

**Intuicja:** Niektóre kanały, zwłaszcza jednorazowe testowe, trzeba świadomie opróżnić. Channel Purger
opisuje taką operację zarządzania, a nie automatyczną naprawę błędnego konsumenta. Usunięcie oczekujących
wiadomości może oznaczać utratę przyjętej pracy, więc zakres i możliwość odtworzenia muszą być jawne.

**Problem i mechanizm:** Wybrany kanał jest opróżniany w kontrolowanej procedurze z jawną utratą oczekujących
wiadomości.

**Role i współpraca:** Operator identyfikuje kanał, autoryzacja potwierdza prawo, procedura ocenia retencję i
archiwum oraz rejestruje wynik. Materiał nie wykonuje tej operacji.

**Przykład:** Jednorazowa kolejka testowa jest czyszczona po zakończeniu eksperymentu, z zapisaniem zakresu
operacji.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
przed ewentualnym purge:
    sprawdź środowisko, pełną nazwę kanału i liczbę wiadomości
    ustal zakres utraty, potrzebne archiwum i właściwą autoryzację
    zapisz decyzję oraz plan dalszego postępu procesu
brak uzgodnionej decyzji -> nie usuwaj wiadomości
```

**Konkretny przypadek użycia:** Sprzątnięcie kanału po migracji

- **Wejście:** Odseparowana kolejka starego formatu ma 200 potwierdzonych jako zbędne komunikatów.
- **Przebieg:** Operator ustala zakres, zatrzymuje dopływ i używa kontrolowanego purgera z rejestrem
  działania.
- **Oczekiwany wynik:** Usunięte są tylko wiadomości objęte decyzją, a wynik sprzątania jest widoczny.
- **Błąd lub granica:** Purge aktywnego kanału może bezpowrotnie usunąć poprawną pracę; potrzebne są
  weryfikowalne kryteria i ewentualny eksport przed operacją.

**Kiedy zastosować:** Tylko gdy usunięcie danego zakresu pracy jest zamierzone. Dla kanału biznesowego zwykle
najpierw trzeba zrozumieć przyczynę backlogu i plan naprawy.

**Koszt i pułapka:** Na kanale biznesowym purge jest destrukcyjny i wymaga właściwej autoryzacji; replay z
archiwum może nie odtworzyć wszystkich efektów.

**Rozwinięcie i ślad wykonania:** Najpierw identyfikuje się dokładny kanał, zakres i dane do zachowania;
dopiero potem rozważa usunięcie wiadomości w autoryzowanej procedurze. Jest to operacja utraty danych, a nie
naprawa konsumenta. Materiał opisuje decyzję projektową i nie wykonuje żadnego czyszczenia infrastruktury.

**Ćwiczenie sprawdzające:** Zaprojektuj procedurę rozpoznania złej kolejki przed jakimkolwiek czyszczeniem.
