# Przepływ przypadku użycia w DDD, Clean Architecture i CQRS

## Wprowadzenie

Przepływ przypadku użycia, czyli use-case flow, opisuje drogę od intencji użytkownika lub systemu zewnętrznego do wykonania zmiany biznesowej w domenie. W architekturze opartej o Domain-Driven Design oraz Clean/Hexagonal Architecture przepływ ten powinien być świadomie rozdzielony na kilka odpowiedzialności. Komenda reprezentuje intencję, serwis aplikacyjny orkiestruje przypadek użycia, agregat wykonuje logikę biznesową, repozytorium zapisuje stan, a zdarzenia domenowe informują resztę systemu o tym, co się wydarzyło.

Na przykładzie operacji `PlaceOrder` można pokazać, jak powinien wyglądać poprawny przepływ w systemie e-commerce. Klient chce złożyć zamówienie. Do systemu trafia komenda zawierająca dane zamówienia. Warstwa aplikacyjna przetwarza tę komendę, tworzy lub ładuje agregat `Order`, wywołuje na nim metodę domenową, zapisuje wynik przez repozytorium i publikuje zdarzenie `OrderPlaced`. Inne części systemu, takie jak Billing lub Fulfillment, mogą następnie zareagować na to zdarzenie.

Najważniejsze jest to, że każda warstwa ma własną odpowiedzialność. Serwis aplikacyjny nie powinien podejmować decyzji biznesowych. Agregat nie powinien znać bazy danych ani brokera wiadomości. Repozytorium nie powinno zawierać reguł biznesowych. Publisher nie powinien być bezpośrednio zależny od domeny w sposób naruszający dependency rule. Dzięki takiemu podziałowi system pozostaje czytelny, testowalny i odporny na zmiany technologiczne.

## Ogólny przepływ przypadku użycia

Typowy przepływ przypadku użycia `PlaceOrder` wygląda następująco. Najpierw klient lub adapter wejściowy tworzy komendę `PlaceOrderCommand`. Komenda nie jest encją domenową i nie zawiera logiki biznesowej. Jest prostym obiektem wejściowym opisującym intencję wykonania operacji.

Następnie komenda trafia do serwisu aplikacyjnego. Serwis aplikacyjny jest granicą przypadku użycia. Jego zadaniem jest koordynacja procesu: przygotowanie danych, utworzenie agregatu, wywołanie metody domenowej, zapis agregatu oraz przekazanie zdarzeń do publikacji. Serwis aplikacyjny powinien być cienki. Nie powinien sprawdzać reguł takich jak poprawność sumy zamówienia, możliwość anulowania zamówienia czy przejście między statusami. Takie decyzje należą do domeny.

Agregat `Order` realizuje właściwą logikę biznesową. To on wie, kiedy zamówienie może zostać złożone, jakie warunki muszą być spełnione oraz jakie zdarzenia domenowe powinny zostać wygenerowane. Jeżeli suma pozycji nie zgadza się z oczekiwaną kwotą, agregat powinien odrzucić operację. Jeżeli zamówienie nie zawiera żadnych pozycji, również nie powinno zostać złożone. Dzięki temu invariants są chronione w jednym miejscu.

Po wykonaniu logiki domenowej serwis aplikacyjny zapisuje agregat przez repozytorium. Repozytorium jest portem, czyli abstrakcją persystencji. Warstwa aplikacyjna nie wie, czy pod spodem działa JPA, JDBC, MongoDB, Event Store czy implementacja in-memory. Z jej perspektywy repozytorium zapisuje agregat.

Następnie serwis aplikacyjny pobiera zdarzenia domenowe z agregatu i przekazuje je do publishera. W prostych systemach publisher może publikować zdarzenia bezpośrednio, ale w systemach produkcyjnych znacznie bezpieczniejszym rozwiązaniem jest transactional outbox. W tym podejściu zdarzenia są zapisywane do tabeli outbox w tej samej transakcji co agregat, a dopiero później osobny proces wysyła je do brokera wiadomości.

## Komenda jako reprezentacja intencji

Komenda jest obiektem reprezentującym intencję wykonania operacji. W przypadku `PlaceOrderCommand` oznacza to: „klient chce złożyć zamówienie z określonymi produktami, ilościami i oczekiwaną sumą”.

Komenda nie jest modelem domenowym. Nie powinna zawierać zachowania biznesowego, nie powinna pilnować invariantów i nie powinna zależeć od frameworków. Jej rola jest transportowa wewnątrz application layer. Może powstać na podstawie żądania HTTP, komunikatu z kolejki, formularza w panelu administracyjnym albo testu jednostkowego.

W dobrze zaprojektowanej architekturze adapter wejściowy mapuje format zewnętrzny na komendę. Kontroler REST może przyjąć JSON, ale nie przekazuje go bezpośrednio do domeny. Najpierw tworzy komendę aplikacyjną. Dzięki temu use case nie zna HTTP, JSON ani żadnego szczegółu transportowego.

Komenda powinna być możliwie prosta. Jeżeli pojawia się potrzeba umieszczenia w niej skomplikowanej logiki, zwykle oznacza to, że część odpowiedzialności została źle umieszczona. Walidacja techniczna formatu może odbywać się w adapterze, ale walidacja biznesowa powinna należeć do domeny.

## Serwis aplikacyjny jako orkiestrator

Serwis aplikacyjny jest miejscem, w którym wykonywany jest konkretny przypadek użycia. W przypadku `PlaceOrderApplicationService` serwis przyjmuje komendę, tworzy identyfikatory, buduje agregat, przekazuje dane do metod domenowych, zapisuje agregat i rejestruje zdarzenia.

Rola serwisu aplikacyjnego polega na orkiestracji, a nie na modelowaniu biznesu. To bardzo ważne rozróżnienie. Serwis aplikacyjny może decydować o kolejności kroków, ale nie powinien decydować o tym, czy biznesowo można złożyć zamówienie. Taka decyzja powinna należeć do agregatu `Order`.

Dobry serwis aplikacyjny zwykle wygląda jak scenariusz:
przyjmij komendę, załaduj dane, wywołaj metodę domenową, zapisz wynik, opublikuj zdarzenia. Jeżeli w serwisie pojawia się dużo instrukcji warunkowych opisujących reguły biznesowe, istnieje ryzyko powstania anemicznego modelu domenowego.

Serwis aplikacyjny jest także naturalnym miejscem do zarządzania transakcją. W systemie opartym o agregaty transakcja powinna obejmować zapis jednego agregatu oraz zapis powiązanych wpisów outbox. Nie powinna natomiast obejmować wielu bounded contexts ani synchronicznie wywoływać kilku innych serwisów w ramach jednej operacji biznesowej.

## Agregat jako centrum decyzji biznesowych

Agregat jest miejscem, w którym wykonywana jest właściwa logika biznesowa. W przykładzie `Order` odpowiada za dodawanie pozycji, obliczanie sumy, sprawdzanie invariantów i zmianę statusu na `PLACED`.

To agregat powinien sprawdzić, czy zamówienie ma co najmniej jedną pozycję. To agregat powinien sprawdzić, czy oczekiwana suma zgadza się z sumą wyliczoną z pozycji. To agregat powinien zarejestrować zdarzenie `OrderPlaced`, ponieważ to on wie, że doszło do istotnej zmiany biznesowej.

Agregat nie powinien znać repozytorium, bazy danych, brokera wiadomości ani transakcji. Nie powinien wywoływać `save`, `publish` ani żadnych operacji infrastrukturalnych. Jego odpowiedzialność kończy się na utrzymaniu poprawnego stanu i zarejestrowaniu faktu biznesowego.

Dzięki temu agregat można bardzo łatwo testować. Test nie wymaga bazy danych ani Springa. Wystarczy utworzyć obiekt, wywołać metodę `place` i sprawdzić, czy status został zmieniony oraz czy powstało zdarzenie `OrderPlaced`.

## Zdarzenia domenowe

Zdarzenie domenowe opisuje coś, co już się wydarzyło w domenie. `OrderPlaced` oznacza, że zamówienie zostało złożone. Nie jest to polecenie ani prośba o wykonanie operacji. Jest to fakt.

To rozróżnienie jest ważne. Komenda mówi: „złóż zamówienie”. Zdarzenie mówi: „zamówienie zostało złożone”. Komenda może się nie udać, jeśli reguły biznesowe nie są spełnione. Zdarzenie powinno powstać dopiero po udanej zmianie stanu.

Zdarzenia domenowe pozwalają oddzielić agregat od reszty systemu. Agregat nie musi wiedzieć, że po złożeniu zamówienia Billing utworzy fakturę, Fulfillment zaplanuje wysyłkę, a moduł powiadomień wyśle e-mail do klienta. Agregat jedynie rejestruje fakt biznesowy. Inne elementy systemu mogą na ten fakt zareagować.

W praktyce agregat często przechowuje listę nieopublikowanych zdarzeń. Po zapisaniu agregatu serwis aplikacyjny pobiera te zdarzenia i przekazuje je do publishera. Następnie lista zdarzeń w agregacie jest czyszczona. Ważne jest, aby zdarzenia nie zostały utracone, dlatego w systemach produkcyjnych zwykle stosuje się outbox pattern.

## Repozytorium jako port zapisu

Repozytorium w tym przepływie odpowiada za zapis i odczyt agregatu. Nie jest to ogólna warstwa CRUD ani miejsce do wykonywania raportów. Repozytorium pracuje na agregatach i ukrywa szczegóły persystencji.

W warstwie aplikacyjnej wywołanie wygląda prosto: `orderRepository.save(order)`. Serwis aplikacyjny nie wie, czy implementacja zapisuje dane przez JPA, JDBC, dokumenty JSON, event store czy pamięć. To jest zgodne z Clean Architecture. Application layer zależy od portu, a infrastruktura dostarcza adapter.

Repozytorium nie powinno zwracać encji JPA ani struktur bazodanowych. Powinno zwracać agregaty domenowe. Jeżeli model bazodanowy różni się od modelu domenowego, adapter persystencji powinien wykonać mapowanie. To chroni domenę przed wymaganiami technicznymi bazy danych.

## Transactional Outbox

Jednym z najtrudniejszych problemów w architekturze event-driven jest atomowość między zapisem danych a publikacją zdarzenia. Wyobraźmy sobie sytuację, w której agregat `Order` został zapisany do bazy, ale aplikacja przestała działać przed wysłaniem eventu `OrderPlaced` do brokera. W takim przypadku zamówienie istnieje, ale Billing i Fulfillment nigdy się o nim nie dowiedzą.

Możliwa jest też odwrotna sytuacja: event został wysłany, ale zapis agregatu nie został zatwierdzony. Wtedy inne moduły reagują na zdarzenie, które nie ma trwałego odzwierciedlenia w stanie systemu.

Transactional Outbox rozwiązuje ten problem przez zapis zdarzenia do specjalnej tabeli outbox w tej samej transakcji co zapis agregatu. Serwis aplikacyjny nie wysyła eventu bezpośrednio do brokera. Zamiast tego publisher zapisuje wiadomość do outboxa. Jeżeli transakcja się powiedzie, zarówno agregat, jak i wiadomość outbox są trwale zapisane. Jeżeli transakcja się nie powiedzie, nie zostanie zapisane ani jedno, ani drugie.

Następnie osobny proces, często nazywany outbox relay, cyklicznie odczytuje nieopublikowane wiadomości z outboxa i wysyła je do brokera. Po udanym wysłaniu oznacza wiadomość jako opublikowaną. Dzięki temu system jest znacznie bardziej odporny na awarie.

Outbox nie eliminuje całej złożoności. Wymaga idempotentności po stronie konsumentów, ponieważ wiadomość może zostać wysłana więcej niż raz. Jednak jest jednym z najważniejszych wzorców pozwalających bezpiecznie łączyć transakcyjny zapis danych z asynchroniczną komunikacją.

## Kolejność operacji

Poprawna kolejność operacji w przypadku `PlaceOrder` powinna wyglądać następująco. Najpierw adapter wejściowy tworzy komendę. Następnie serwis aplikacyjny rozpoczyna transakcję. Wewnątrz transakcji tworzony jest agregat, dodawane są pozycje zamówienia, a następnie wywoływana jest metoda `place`. Agregat sprawdza reguły biznesowe, zmienia stan i rejestruje zdarzenie `OrderPlaced`.

Po tym serwis aplikacyjny zapisuje agregat przez repozytorium. Następnie przekazuje zdarzenia do publishera, który w przypadku outboxa zapisuje je do tabeli outbox. Dopiero po zapisaniu agregatu i wpisów outbox transakcja zostaje zatwierdzona. Później niezależny proces publikuje wiadomości do brokera.

To oznacza, że publikacja do brokera nie musi odbywać się w tej samej chwili co złożenie zamówienia. System może być chwilowo niespójny, ale jest trwale spójny logicznie. Inne moduły otrzymają zdarzenie z niewielkim opóźnieniem.

## CQRS i read model

W opisanym przepływie agregat `Order` jest modelem zapisu. Jego zadaniem jest ochrona reguł biznesowych i poprawna zmiana stanu. Nie musi być wygodny do odczytu, raportowania ani budowania widoków administracyjnych.

CQRS zakłada rozdzielenie modelu zapisu od modelu odczytu. Model zapisu jest bogaty domenowo, oparty o agregaty i invariants. Model odczytu jest zoptymalizowany pod zapytania. Może być denormalizowany, uproszczony i dostosowany do potrzeb interfejsu użytkownika.

Po zdarzeniu `OrderPlaced` można zaktualizować projekcję `orders_view`, która zawiera dane wygodne do odczytu: identyfikator zamówienia, klienta, status, sumę, walutę i datę złożenia. Taka projekcja nie musi być agregatem i nie powinna być używana do podejmowania decyzji biznesowych. Jej zadaniem jest szybkie odpowiadanie na pytania.

Read model może być chwilowo niespójny z write model. Jest to naturalna konsekwencja event-driven architecture. Po złożeniu zamówienia użytkownik może przez krótki moment nie widzieć zaktualizowanego widoku. W wielu systemach jest to akceptowalne, o ile operacje krytyczne biznesowo są realizowane na modelu zapisu.

## Rola adapterów

Adapter wejściowy, na przykład kontroler REST, nie powinien znać szczegółów domeny. Jego zadaniem jest przetłumaczenie żądania HTTP na komendę. Adapter nie powinien wykonywać logiki biznesowej ani bezpośrednio manipulować agregatem.

Adapter wyjściowy repozytorium zapisuje agregat do bazy. Może używać JPA, JDBC albo innej technologii, ale ta technologia nie powinna przenikać do domeny. Jeżeli używany jest JPA, encja JPA może być osobnym modelem infrastrukturalnym, mapowanym na agregat domenowy.

Adapter outbox zapisuje zdarzenia do tabeli outbox. Adapter messagingowy publikuje wiadomości do brokera. Każdy z tych adapterów realizuje szczegół techniczny, ale nie zmienia reguł biznesowych.

Dzięki temu można wymienić REST na gRPC, JPA na JDBC albo Kafkę na RabbitMQ bez przepisywania domeny.

## Idempotentność i retry

W systemie opartym o outbox i brokera wiadomości trzeba założyć, że wiadomości mogą zostać dostarczone więcej niż raz. Outbox relay może wysłać wiadomość, ale nie zdążyć oznaczyć jej jako opublikowanej. Po restarcie wyśle ją ponownie. Broker również może dostarczyć komunikat wielokrotnie.

Dlatego konsumenci zdarzeń muszą być idempotentni. Jeżeli Billing otrzyma dwa razy `OrderPlaced`, nie powinien utworzyć dwóch faktur dla tego samego zamówienia. Jeżeli Fulfillment otrzyma dwa razy ten sam event, nie powinien zaplanować dwóch wysyłek.

Idempotentność można osiągnąć przez przechowywanie identyfikatorów przetworzonych eventów, stosowanie unikalnych constraintów w bazie danych albo projektowanie operacji jako naturalnie idempotentnych. Jest to nieodłączny element architektury event-driven.

## Testowanie przepływu

Przepływ `PlaceOrder` można testować na kilku poziomach. Najpierw warto testować sam agregat. Test agregatu sprawdza, czy zamówienie bez pozycji nie może zostać złożone, czy błędna suma jest odrzucana oraz czy poprawne zamówienie generuje zdarzenie `OrderPlaced`.

Następnie testuje się serwis aplikacyjny. W takim teście można użyć fałszywego repozytorium, fałszywego publishera i fałszywego transaction managera. Dzięki temu test nie wymaga bazy danych, Springa ani brokera wiadomości. Sprawdza się, czy serwis poprawnie orkiestruje przypadek użycia.

Osobno testuje się adaptery. Adapter JPA można testować z testową bazą danych. Outbox relay można testować z fake brokerem. Kontroler REST można testować jako adapter wejściowy, sprawdzając mapowanie HTTP na komendę. Ten podział sprawia, że testy są szybsze, bardziej precyzyjne i mniej kruche.

## Typowe błędy

Najczęstszym błędem jest umieszczanie logiki biznesowej w serwisie aplikacyjnym. Jeżeli `PlaceOrderApplicationService` samodzielnie sprawdza wszystkie reguły, przelicza total i decyduje o statusach, agregat staje się anemiczny. Wtedy model domenowy przestaje chronić invariants.

Drugim błędem jest publikowanie zdarzeń bezpośrednio do brokera w tej samej metodzie, w której zapisywany jest agregat. Bez outboxa łatwo utracić event albo opublikować event dla danych, które nie zostały zapisane.

Trzecim błędem jest traktowanie read modelu jako źródła prawdy. Projekcja CQRS jest wygodna do odczytu, ale nie powinna podejmować decyzji biznesowych. Źródłem prawdy dla operacji zapisu powinien być agregat.

Czwartym błędem jest przekazywanie obiektów infrastrukturalnych przez wszystkie warstwy. Jeżeli domena zna JSON, HTTP, JPA albo Kafkę, architektura traci separację.

## Podsumowanie

Przepływ przypadku użycia w dobrze zaprojektowanym systemie powinien być prosty, ale rygorystycznie podzielony na odpowiedzialności. Komenda opisuje intencję. Serwis aplikacyjny orkiestruje scenariusz. Agregat wykonuje logikę biznesową i chroni invariants. Repozytorium zapisuje agregat. Publisher rejestruje zdarzenia, najlepiej przez transactional outbox. Osobny proces publikuje komunikaty do brokera. Read model CQRS jest aktualizowany asynchronicznie na podstawie zdarzeń.
 
Takie podejście pozwala budować system, który jest odporny na zmiany technologiczne, łatwy do testowania i zgodny z zasadami DDD oraz Clean Architecture. Najważniejsza zasada brzmi: decyzje biznesowe należą do domeny, a infrastruktura jedynie umożliwia ich trwałe wykonanie i komunikację z resztą systemu.
