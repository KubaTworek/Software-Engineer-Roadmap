# Clean Architecture i Hexagonal Architecture – porty, adaptery oraz Dependency Rule

## Wprowadzenie

Clean Architecture oraz Hexagonal Architecture to podejścia projektowe, których głównym celem jest oddzielenie logiki biznesowej od szczegółów technicznych. W praktyce oznacza to, że domena systemu nie powinna zależeć od frameworków, baz danych, protokołów komunikacyjnych, brokerów wiadomości ani interfejsów użytkownika. Technologie są ważne, ale powinny być traktowane jako szczegóły implementacyjne, a nie jako fundament modelu biznesowego.

W systemach e-commerce taka separacja ma szczególne znaczenie. Procesy takie jak składanie zamówienia, naliczanie ceny, obsługa płatności, wystawianie faktur czy planowanie wysyłki są regułami biznesowymi. Nie powinny być uzależnione od tego, czy aplikacja używa Springa, JPA, Kafki, REST API, GraphQL czy konkretnej bazy danych. Technologie mogą się zmieniać, ale zasady biznesowe powinny pozostać stabilne.

Clean Architecture i Hexagonal Architecture pomagają osiągnąć tę stabilność przez wprowadzenie wyraźnych granic między warstwami systemu. Najważniejszą zasadą jest Dependency Rule, czyli reguła kierunku zależności. Kod znajdujący się bliżej centrum systemu nie powinien znać kodu znajdującego się na zewnątrz. Innymi słowy: domena nie zależy od infrastruktury, ale infrastruktura zależy od domeny.

## Dependency Rule

Dependency Rule mówi, że zależności w kodzie powinny zawsze wskazywać do wnętrza systemu. Najbardziej wewnętrzną warstwą jest domena, czyli model biznesowy. Na zewnątrz znajdują się use case’y, porty, adaptery, frameworki, baza danych, kolejki komunikatów i interfejsy użytkownika.

Oznacza to, że encje domenowe nie powinny importować klas ze Springa, JPA, Hibernate, Jacksona, Kafki ani żadnego innego narzędzia infrastrukturalnego. Jeżeli encja domenowa zawiera adnotacje typu `@Entity`, `@Table`, `@JsonProperty` lub `@Autowired`, oznacza to, że domena zaczyna zależeć od szczegółów technicznych. Taka zależność utrudnia testowanie, migrację technologii oraz ewolucję modelu biznesowego.

Reguła zależności nie oznacza, że frameworki są zakazane. Oznacza jedynie, że powinny znajdować się na zewnątrz architektury. Framework może uruchamiać aplikację, obsługiwać HTTP, zapisywać dane do bazy lub publikować komunikaty, ale nie powinien definiować kształtu domeny.

W dobrze zaprojektowanym systemie domena jest niezależna. Można ją przetestować bez bazy danych, bez serwera HTTP, bez kontenera Springa i bez brokera wiadomości. To bardzo silne kryterium jakości architektury.

## Warstwy architektury

Clean Architecture zwykle przedstawia system jako kilka warstw ułożonych koncentrycznie. W centrum znajdują się encje domenowe, dalej przypadki użycia, następnie adaptery interfejsów, a na zewnątrz frameworki i narzędzia.

Najbardziej wewnętrzna warstwa to domena. Zawiera encje, Value Objects, agregaty, zdarzenia domenowe oraz reguły biznesowe. To tutaj powinny znajdować się najważniejsze zasady systemu. Domena nie powinna wiedzieć, w jaki sposób jest wywoływana ani gdzie są przechowywane dane.

Kolejna warstwa to application layer, czyli warstwa przypadków użycia. To tutaj znajdują się serwisy aplikacyjne, komendy, wyniki operacji oraz porty. Application service orkiestruje przypadek użycia: przyjmuje komendę, ładuje agregat, wywołuje metodę domenową, zapisuje wynik i publikuje zdarzenia. Nie powinien jednak zawierać właściwej logiki biznesowej. Jego rolą jest koordynacja, a nie podejmowanie decyzji domenowych.

Następnie znajdują się adaptery. Adaptery tłumaczą świat zewnętrzny na wewnętrzny i odwrotnie. Przykładowo kontroler REST zamienia żądanie HTTP na komendę aplikacyjną, a adapter repozytorium zamienia agregat domenowy na encję bazodanową. Adapter wiadomości zamienia zdarzenie domenowe na komunikat publikowany do Kafki. Adaptery są miejscem, w którym infrastruktura spotyka się z aplikacją.

Najbardziej zewnętrzna warstwa to frameworki i narzędzia. Należą do niej Spring, baza danych, Kafka, RabbitMQ, system plików, biblioteki HTTP oraz inne technologie. Są one potrzebne, ale powinny pozostawać wymienne.

## Hexagonal Architecture

Hexagonal Architecture, znana również jako Ports and Adapters Architecture, opisuje system jako rdzeń aplikacji otoczony portami i adapterami. Rdzeniem jest domena oraz use case’y. Porty są kontraktami, przez które świat zewnętrzny komunikuje się z aplikacją albo przez które aplikacja komunikuje się ze światem zewnętrznym. Adaptery są konkretnymi implementacjami tych portów.

W tym podejściu aplikacja nie jest budowana „pod REST API” ani „pod bazę danych”. REST, baza danych i broker wiadomości są jedynie adapterami. Można je wymienić, nie zmieniając logiki biznesowej.

Przykładowo use case `PlaceOrderUseCase` może być wywołany przez kontroler REST, konsumenta wiadomości, CLI albo test jednostkowy. Sam use case nie powinien wiedzieć, skąd przyszło żądanie. Otrzymuje prostą komendę aplikacyjną, wykonuje logikę i zwraca wynik.

Podobnie zapis zamówienia odbywa się przez port `OrderRepository`. Warstwa aplikacyjna zna interfejs repozytorium, ale nie zna jego implementacji. Implementacja może używać JPA, JDBC, MongoDB, pliku lub pamięci. Z punktu widzenia use case’a nie ma to znaczenia.

## Porty wejściowe

Porty wejściowe definiują, co aplikacja potrafi zrobić. Są to kontrakty reprezentujące przypadki użycia systemu.

Przykładami portów wejściowych są `PlaceOrderUseCase`, `CancelOrderUseCase`, `MarkOrderAsPaidUseCase` i `ScheduleShipmentUseCase`. Port wejściowy nie powinien przyjmować obiektów infrastrukturalnych takich jak `HttpRequest`, `ResponseEntity`, `KafkaRecord` czy `ServletRequest`. Powinien operować na prostych komendach, zapytaniach i wynikach aplikacyjnych.

Dzięki temu ten sam przypadek użycia może być wykorzystany przez różne adaptery wejściowe. Na przykład złożenie zamówienia może zostać uruchomione przez REST API, panel administracyjny, import pliku CSV albo zdarzenie z innego systemu.

## Porty wyjściowe

Porty wyjściowe opisują to, czego aplikacja potrzebuje od świata zewnętrznego.

Przykładami portów wyjściowych są `OrderRepository`, `PaymentGateway`, `EventPublisher`, `EmailSender` i `InventoryAvailabilityChecker`. Use case zależy od interfejsów, nie od konkretnych bibliotek. To bardzo ważne, ponieważ pozwala testować logikę aplikacyjną przy użyciu fałszywych implementacji portów. W testach można użyć `InMemoryOrderRepository` zamiast prawdziwej bazy danych albo `FakeEventPublisher` zamiast Kafki.

Port wyjściowy powinien być definiowany przez potrzeby aplikacji, a nie przez możliwości narzędzia. Nie należy kopiować API frameworka do portu. Jeżeli port repozytorium wygląda jak uproszczona wersja Spring Data, prawdopodobnie granica architektoniczna została źle zaprojektowana.

## Adaptery wejściowe

Adapter wejściowy przyjmuje żądanie ze świata zewnętrznego i zamienia je na wywołanie portu wejściowego. Przykładem jest kontroler REST. Jego zadaniem jest odebrać żądanie HTTP, zwalidować format transportowy, zamienić DTO na komendę aplikacyjną, wywołać use case i zamienić wynik na odpowiedź HTTP.

Kontroler nie powinien zawierać logiki biznesowej. Jeżeli w kontrolerze pojawiają się decyzje typu „czy zamówienie można anulować”, „czy klient może zapłacić” albo „czy przesyłka może zostać wysłana”, oznacza to, że logika biznesowa wyciekła poza domenę lub use case.

Adapterem wejściowym może być także konsument wiadomości. W takim przypadku adapter odczytuje komunikat z brokera, mapuje go na komendę aplikacyjną i wywołuje odpowiedni use case. Sam broker nie powinien być widoczny w logice aplikacyjnej.

## Adaptery wyjściowe

Adapter wyjściowy implementuje port wyjściowy za pomocą konkretnej technologii.

Przykładowo `JpaOrderRepositoryAdapter` implementuje port `OrderRepository` i używa JPA do zapisu danych. `KafkaOrderEventPublisherAdapter` implementuje port `OrderEventPublisher` i używa Kafki do publikacji komunikatów. `StripePaymentGatewayAdapter` może implementować port `PaymentGateway` i komunikować się z zewnętrznym operatorem płatności.

Adapter wyjściowy często wykonuje mapowanie między modelem domenowym a modelem infrastrukturalnym. To bardzo istotne. Encja domenowa `Order` nie musi być tym samym co encja JPA `OrderJpaEntity`. Model domenowy powinien odpowiadać regułom biznesowym, a model persystencji strukturze zapisu danych. Próba połączenia tych dwóch modeli często prowadzi do kompromisów, które z czasem osłabiają domenę.

## DTO, komendy i modele domenowe

W czystej architekturze należy odróżniać kilka typów obiektów. DTO transportowe należą do adapterów. Reprezentują format komunikacji z zewnętrznym światem, na przykład JSON w REST API albo payload wiadomości Kafka. Nie powinny przenikać do domeny.

Komendy i wyniki aplikacyjne należą do application layer. Reprezentują intencję wykonania przypadku użycia i wynik tej operacji. Są niezależne od HTTP, Kafki czy bazy danych.

Modele domenowe należą do domeny. Reprezentują pojęcia biznesowe i zawierają logikę oraz invariants.

Mieszanie tych modeli prowadzi do sprzężenia warstw. Jeżeli domena zwraca bezpośrednio DTO HTTP, oznacza to, że zna protokół transportowy. Jeżeli use case przyjmuje encję JPA, oznacza to, że zna persystencję. Oba przypadki łamią dependency rule.

## Repozytorium jako port

W Clean/Hexagonal Architecture repozytorium powinno być portem, a nie szczegółem infrastruktury widocznym w domenie. Interfejs repozytorium definiuje operacje potrzebne aplikacji, na przykład zapis i odczyt agregatu. Implementacja repozytorium znajduje się w adapterze infrastrukturalnym.

To odwraca typową zależność. Zamiast tego, żeby logika biznesowa zależała od bazy danych, baza danych zależy od kontraktu zdefiniowanego przez aplikację. Dzięki temu można zmienić sposób persystencji bez naruszania use case’ów i domeny.

Repozytorium nie powinno być używane jako ogólny mechanizm raportowania. Skomplikowane zapytania, dashboardy i listy administracyjne lepiej obsługiwać przez osobne modele odczytu, zgodnie z podejściem CQRS.

## Event Publisher jako port

Podobnie jak repozytorium, publikacja zdarzeń również powinna być ukryta za portem. Application service może potrzebować opublikować zdarzenie `OrderPlaced`, ale nie powinien wiedzieć, czy trafi ono do Kafki, RabbitMQ, bazy outbox, webhooka czy lokalnego dispatchera.

Port `EventPublisher` reprezentuje potrzebę biznesowo-aplikacyjną: poinformowanie świata o tym, że coś się wydarzyło. Adapter decyduje, jak technicznie zostanie to zrealizowane.

To rozdzielenie jest szczególnie ważne, ponieważ infrastruktura messagingowa często się zmienia. Organizacja może zacząć od prostych eventów wewnątrz modularnego monolitu, później przejść na outbox pattern, a następnie na Kafkę. Przy dobrze zaprojektowanych portach logika domenowa nie musi się zmieniać.

## Testowalność

Jednym z najważniejszych praktycznych efektów Clean Architecture jest łatwiejsze testowanie.

Domena powinna być testowana bez infrastruktury. Test agregatu nie powinien wymagać uruchomienia bazy danych, kontenera Springa ani brokera wiadomości. Wystarczy utworzyć obiekt domenowy, wywołać jego metodę i sprawdzić stan lub zdarzenia domenowe.

Use case powinien być testowany z użyciem fałszywych implementacji portów. Zamiast prawdziwego repozytorium można użyć implementacji in-memory. Zamiast prawdziwego publishera wiadomości można użyć fake publishera zapisującego eventy do listy. Dzięki temu testy są szybkie, deterministyczne i niezależne od środowiska.

Adaptery testuje się osobno. Adapter JPA można testować jako integrację z bazą danych. Adapter REST można testować jako warstwę web. Adapter Kafka można testować z brokerem testowym lub kontraktowo. Nie należy mieszać tych testów z testami domeny.

Jeżeli przetestowanie podstawowego przypadku użycia wymaga uruchomienia całej aplikacji, serwera HTTP i bazy danych, to zwykle oznacza, że warstwy są zbyt mocno sprzężone.

## Kryterium poprawnej architektury

System zgodny z Clean/Hexagonal Architecture spełnia kilka praktycznych warunków.

Domena nie importuje frameworków. Use case’e nie przyjmują obiektów transportowych. Repozytoria i publishery są portami, a nie bezpośrednimi implementacjami infrastruktury. Adaptery mapują dane między światem zewnętrznym a wewnętrznym. Testy domeny i use case’ów działają w pamięci. Kierunek zależności jest zawsze zgodny z zasadą: adaptery zależą od aplikacji, aplikacja zależy od domeny, domena nie zależy od nikogo z zewnątrz.

Najprostsze kryterium brzmi: jeżeli można usunąć Springa, bazę danych i Kafkę, a logika biznesowa nadal kompiluje się i przechodzi testy jednostkowe, to architektura jest dobrze odseparowana.

## Typowe naruszenia

Najczęstszym naruszeniem jest umieszczanie adnotacji infrastrukturalnych w domenie. Encja domenowa z adnotacjami JPA bardzo często zaczyna być projektowana pod bazę danych zamiast pod biznes. Podobny problem występuje, gdy klasy domenowe zawierają adnotacje JSON służące do serializacji HTTP.

Drugim częstym błędem jest używanie obiektów frameworka w use case’ach. Application service nie powinien zwracać `ResponseEntity`, przyjmować `HttpRequest` ani znać statusów HTTP. To należy do adaptera web.

Trzecim błędem jest przenoszenie logiki biznesowej do adapterów. Kontroler, consumer wiadomości albo adapter repozytorium nie powinny podejmować decyzji domenowych. Ich zadaniem jest tłumaczenie i delegowanie.

Czwartym błędem jest projektowanie portów pod narzędzia. Port nie powinien wyglądać jak kopia API Kafki, JPA albo klienta HTTP. Port powinien wyrażać język aplikacji.

## Clean Architecture w modularnym monolicie i mikroserwisach

Clean/Hexagonal Architecture można stosować zarówno w modularnym monolicie, jak i w mikroserwisach. Nie jest ona zależna od modelu deploymentu.

W modularnym monolicie każdy moduł może posiadać własną domenę, warstwę aplikacyjną, porty i adaptery. Dzięki temu moduły są logicznie odseparowane, mimo że działają w jednym procesie. Jeżeli w przyszłości jeden moduł zostanie wydzielony jako mikroserwis, jego granice są już przygotowane.

W mikroserwisach clean architecture pomaga utrzymać niezależność domeny mimo dużej ilości infrastruktury. Mikroserwis zwykle ma wiele adapterów: REST, messaging, baza danych, observability, integracje zewnętrzne. Bez wyraźnych granic technologia szybko zaczyna dominować nad modelem biznesowym.

Dlatego Clean Architecture nie jest alternatywą dla DDD, lecz jego naturalnym uzupełnieniem. DDD mówi, jak modelować domenę, a Clean/Hexagonal Architecture pomaga chronić ten model przed infrastrukturą.

## Podsumowanie

Clean Architecture i Hexagonal Architecture pomagają budować systemy, w których logika biznesowa pozostaje niezależna od technologii. Najważniejszą zasadą jest Dependency Rule: zależności kodu powinny zawsze wskazywać do wnętrza systemu. Domena nie zna frameworków, use case’e nie znają HTTP ani bazy danych, a adaptery odpowiadają za tłumaczenie między światem zewnętrznym a modelem aplikacji.

Porty definiują potrzeby aplikacji, a adaptery dostarczają ich techniczne implementacje. Dzięki temu system można łatwiej testować, rozwijać i migrować między technologiami. Frameworki, bazy danych i brokery wiadomości są ważne, ale powinny pozostać wymiennymi detalami.

W dobrze zaprojektowanym systemie e-commerce domena sprzedaży, płatności czy wysyłki powinna być zrozumiała i testowalna bez uruchamiania całej infrastruktury. To właśnie ta niezależność jest największą praktyczną wartością Clean/Hexagonal Architecture.
