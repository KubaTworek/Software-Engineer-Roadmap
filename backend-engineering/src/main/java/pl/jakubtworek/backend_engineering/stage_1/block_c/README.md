# Zaawansowany Spring i mikroserwisy — teoria, architektura i pułapki projektowe

## Executive Summary

Ten dokument omawia zaawansowane zagadnienia Spring Framework, Spring Boot oraz architektury mikroserwisowej z naciskiem na teorię, mechanizmy działania i decyzje projektowe. Celem nie jest przedstawienie katalogu adnotacji ani gotowych fragmentów kodu do kopiowania, lecz zbudowanie solidnego modelu mentalnego: jak działa kontener IoC, kiedy powstają beany, jak Spring tworzy proxy, dlaczego transakcje czasem nie działają mimo obecności `@Transactional`, gdzie kończy się wygoda Spring Boota, a zaczyna świadoma architektura systemu.

Spring jest przede wszystkim kontenerem zarządzającym zależnościami i cyklem życia obiektów. Jego siła polega na odwróceniu kontroli, konfiguracji deklaratywnej, automatyzacji typowych wzorców infrastrukturalnych oraz integracji z ogromnym ekosystemem bibliotek. Jednocześnie ta wygoda ma koszt: wiele zachowań odbywa się pośrednio, przez refleksję, proxy, auto-konfigurację, interceptory i kontekst aplikacji. Programista pracujący na poziomie senior powinien rozumieć nie tylko to, że dana adnotacja „działa”, ale również kiedy, dlaczego i w jakich warunkach przestaje działać.

Mikroserwisy nie są prostym sposobem na podzielenie dużej aplikacji na kilka mniejszych. Są modelem organizacji systemu rozproszonego, w którym zyskujemy niezależność wdrożeń, autonomię zespołów i skalowanie wybranych komponentów, ale płacimy za to złożonością komunikacji, obserwowalności, odporności, spójności danych, wersjonowania kontraktów i bezpieczeństwa. Dlatego mikroserwisy powinny być odpowiedzią na konkretne problemy organizacyjne i techniczne, a nie domyślnym stylem architektury.

Najważniejsza zasada brzmi: Spring upraszcza implementację infrastruktury, ale nie zwalnia z rozumienia architektury. `@Transactional` nie zastępuje myślenia o granicach transakcji. `@Async` nie rozwiązuje automatycznie problemu równoległości. `@Retryable` nie czyni systemu odpornym. Gateway nie gwarantuje bezpieczeństwa. Service discovery nie rozwiązuje problemów domenowych. Każde narzędzie musi być użyte w świadomym kontekście.

## Spring jako kontener IoC

Podstawą Springa jest IoC, czyli Inversion of Control. W klasycznym kodzie obiekt sam tworzy swoje zależności lub sam decyduje, skąd je pobrać. W Springu odpowiedzialność ta zostaje przeniesiona do kontenera. Obiekt deklaruje, czego potrzebuje, a kontener dostarcza odpowiednie zależności. Dzięki temu klasy aplikacyjne są mniej powiązane z konkretnym sposobem tworzenia obiektów, łatwiejsze do testowania i bardziej konfigurowalne.

Dependency Injection jest praktyczną formą odwrócenia kontroli. Najczęściej preferowanym wariantem jest wstrzykiwanie przez konstruktor, ponieważ pozwala tworzyć obiekty w stanie kompletnym, ułatwia testowanie, wspiera niemutowalność zależności i jasno pokazuje wymagania klasy. Wstrzykiwanie przez pole jest wygodne, ale ukrywa zależności, utrudnia testy bez kontenera i może prowadzić do klas o zbyt wielu odpowiedzialnościach. Wstrzykiwanie przez setter ma sens głównie dla zależności opcjonalnych lub konfigurowanych po utworzeniu obiektu.

Kontener Springa nie jest tylko fabryką obiektów. Zarządza cyklem życia beanów, ich zakresem, zależnościami, inicjalizacją, niszczeniem, post-processowaniem oraz integracją z mechanizmami takimi jak AOP, transakcje, walidacja, bezpieczeństwo i zdarzenia aplikacyjne. W praktyce oznacza to, że obiekt zarządzany przez Springa może zachowywać się inaczej niż zwykły obiekt utworzony operatorem `new`.

## ApplicationContext i cykl życia aplikacji

`ApplicationContext` jest centralnym elementem aplikacji Spring. Odpowiada za skanowanie komponentów, rejestrowanie definicji beanów, tworzenie obiektów, rozwiązywanie zależności, publikowanie zdarzeń, obsługę zasobów, internacjonalizację i integrację z infrastrukturą. W aplikacji Spring Boot większość tego procesu jest ukryta za prostym wywołaniem `SpringApplication.run`, ale pod spodem dzieje się wiele etapów.

Najpierw Spring identyfikuje konfigurację aplikacji. Może ona pochodzić z klas oznaczonych `@Configuration`, komponentów wykrytych przez component scanning, auto-konfiguracji Spring Boota, plików właściwości, profili, warunków `@Conditional` oraz konfiguracji zewnętrznej. Następnie tworzone są definicje beanów. Definicja beana nie jest jeszcze obiektem; jest opisem tego, jak obiekt ma zostać utworzony.

Po zbudowaniu definicji kontener rozwiązuje zależności i tworzy singletony, o ile nie są leniwe. W tym procesie działają różne post-processory, które mogą modyfikować definicje beanów lub same obiekty. To właśnie dzięki takim mechanizmom Spring może tworzyć proxy, obsługiwać adnotacje, integrować transakcje i wykonywać dodatkową logikę wokół metod.

Zrozumienie cyklu życia beana jest ważne, ponieważ wiele błędów wynika z niewłaściwych założeń co do momentu inicjalizacji. Nie każda zależność jest dostępna w konstruktorze w taki sam sposób jak po pełnej inicjalizacji kontekstu. Niektóre mechanizmy, takie jak proxy AOP, działają dopiero po utworzeniu obiektu i opakowaniu go przez kontener. Wywołanie metody na `this` wewnątrz tej samej klasy omija proxy, co ma krytyczne znaczenie dla transakcji, bezpieczeństwa i aspektów.

## Bean, scope i odpowiedzialność obiektów

Bean to obiekt zarządzany przez kontener Spring. Najczęściej spotykanym zakresem jest singleton, czyli jedna instancja beana na kontekst aplikacji. Warto podkreślić, że singleton Springa nie oznacza globalnego singletona JVM. Oznacza jedną instancję w ramach konkretnego `ApplicationContext`. W aplikacji webowej większość komponentów serwisowych, repozytoriów i kontrolerów to singletony.

Singletony powinny być projektowane jako obiekty bezstanowe albo posiadające wyłącznie stan bezpieczny współbieżnie. Ponieważ ten sam bean może obsługiwać wiele żądań jednocześnie, przechowywanie w nim danych konkretnego requestu w polach instancji jest poważnym błędem. Stan użytkownika, requestu lub transakcji powinien być przekazywany jako argument, przechowywany w odpowiednim kontekście lub zarządzany przez właściwy scope.

Spring obsługuje również inne zakresy, takie jak prototype, request, session czy application. Scope prototype oznacza, że kontener tworzy nową instancję przy każdym pobraniu beana, ale nie zarządza pełnym cyklem życia takiego obiektu w takim samym sensie jak singletona. Scope request i session mają sens w aplikacjach webowych, ale powinny być używane ostrożnie, ponieważ zwiększają powiązanie kodu z warstwą HTTP i utrudniają testowanie.

Jednym z częstych problemów jest wstrzyknięcie beana prototype do singletona. Jeśli singleton dostanie prototype w konstruktorze, otrzyma jedną instancję utworzoną przy inicjalizacji singletona, a nie nową instancję przy każdym użyciu. Aby uzyskać dynamiczne pobieranie instancji, trzeba użyć na przykład `ObjectProvider`, metody lookup albo innego jawnego mechanizmu. Jest to przykład sytuacji, w której znajomość deklaracji scope nie wystarcza; trzeba rozumieć moment rozwiązywania zależności.

## Auto-konfiguracja Spring Boota

Spring Boot upraszcza konfigurację aplikacji, stosując zasadę convention over configuration. Na podstawie klas obecnych na classpath, właściwości konfiguracyjnych i warunków auto-konfiguracja decyduje, które beany utworzyć. Dzięki temu można bardzo szybko uruchomić aplikację webową, połączenie z bazą danych, security, actuator, klienta HTTP czy mechanizmy obserwowalności.

Auto-konfiguracja jest jednocześnie jedną z najczęstszych przyczyn nieporozumień. Programista widzi, że coś działa, ale nie wie, który bean został utworzony, dlaczego został utworzony i jak go nadpisać. Spring Boot zwykle tworzy beany warunkowo, na przykład tylko wtedy, gdy nie istnieje już bean danego typu. Oznacza to, że własna konfiguracja użytkownika może zastąpić domyślną, ale tylko jeśli spełnione są określone warunki.

W pracy seniora ważna jest umiejętność czytania raportu auto-konfiguracji i rozumienia mechanizmów `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty` oraz profili. Nie chodzi o zapamiętanie wszystkich auto-konfiguracji, lecz o umiejętność diagnostyki. Gdy aplikacja zachowuje się inaczej niż oczekiwano, trzeba umieć odpowiedzieć, czy problem wynika z konfiguracji, kolejności beanów, warunków aktywacji, profili, classpath czy nadpisania domyślnego komponentu.

Spring Boot jest bardzo dobrym punktem startowym, ale nie powinien być traktowany jako czarna skrzynka. W małych projektach można długo korzystać z domyślnych ustawień. W systemach produkcyjnych trzeba jednak świadomie zarządzać konfiguracją, bezpieczeństwem, connection poolami, timeoutami, serializacją, limitami, health checkami i obserwowalnością.

## Spring AOP i proxy

Spring AOP opiera się głównie na proxy. Oznacza to, że Spring tworzy obiekt pośredniczący, który otacza właściwy bean i przechwytuje wywołania metod. Dzięki temu można dodać logikę przekrojową, taką jak transakcje, bezpieczeństwo, logowanie, metryki, retry czy cache, bez ręcznego umieszczania jej w każdej metodzie biznesowej.

Istnieją dwa podstawowe mechanizmy proxy: proxy interfejsowe oparte na JDK dynamic proxies oraz proxy klasowe oparte na CGLIB. Proxy JDK działa przez interfejsy, natomiast CGLIB tworzy podklasę klasy docelowej. Różnica ta ma konsekwencje. Metody finalne nie mogą być nadpisane przez CGLIB, a metody prywatne nie są przechwytywane przez standardowy mechanizm AOP. To oznacza, że nie każda metoda oznaczona adnotacją będzie faktycznie objęta aspektem.

Najważniejsza pułapka to self-invocation. Jeżeli metoda jednej klasy wywołuje inną metodę tej samej klasy przez `this`, wywołanie omija proxy. Wtedy `@Transactional`, `@Async`, `@Cacheable` lub inne aspekty mogą nie zadziałać. Dla programisty początkującego wygląda to jak błąd Springa, ale w rzeczywistości wynika z modelu proxy. Wywołanie musi przejść przez proxy, aby aspekt miał szansę je przechwycić.

AOP jest dobrym narzędziem do logiki przekrojowej, ale nie powinno ukrywać kluczowej logiki domenowej. Jeżeli zachowanie biznesowe systemu zależy od wielu aspektów rozsianych po konfiguracji, kod staje się trudny do rozumienia. Aspekty powinny być używane tam, gdzie ich semantyka jest dobrze znana i naturalna: transakcje, bezpieczeństwo, metryki, tracing, cache, retry. Nadużycie AOP prowadzi do architektury, w której rzeczywiste wykonanie kodu jest trudne do przewidzenia.

## Transakcje w Springu

Transakcje w Springu są zwykle obsługiwane deklaratywnie przez `@Transactional`. Mechanizm ten jest oparty na AOP i proxy. Gdy wywołanie metody przechodzi przez proxy transakcyjne, Spring rozpoczyna, dołącza do istniejącej albo odpowiednio zarządza transakcją zgodnie z konfiguracją. Po zakończeniu metody transakcja jest zatwierdzana lub wycofywana zależnie od wyniku i rodzaju wyjątku.

Najważniejsze jest zrozumienie, że `@Transactional` nie jest magiczną właściwością metody jako takiej. Działa tylko wtedy, gdy wywołanie przechodzi przez mechanizm proxy. Nie zadziała na prywatnej metodzie, nie zadziała przy self-invocation, może nie zadziałać zgodnie z oczekiwaniem na metodzie finalnej i wymaga poprawnie skonfigurowanego menedżera transakcji. Z tego powodu granice transakcji powinny być projektowane świadomie, najczęściej na poziomie serwisu aplikacyjnego, a nie przypadkowo rozrzucane po metodach pomocniczych.

Domyślna semantyka rollbacku również jest źródłem błędów. Spring domyślnie wycofuje transakcję dla wyjątków niekontrolowanych, czyli `RuntimeException` i `Error`, ale nie dla checked exceptions, chyba że zostanie to jawnie skonfigurowane. Programista może więc oczekiwać rollbacku po wyjątku biznesowym, a w praktyce transakcja zostanie zatwierdzona, jeżeli wyjątek jest checked i nie ustawiono `rollbackFor`.

Izolacja transakcji to kolejny obszar wymagający zrozumienia. Poziomy izolacji wpływają na zjawiska takie jak dirty read, non-repeatable read i phantom read. Nie należy bezrefleksyjnie ustawiać najwyższej izolacji, ponieważ może to znacząco pogorszyć współbieżność i zwiększyć blokowanie w bazie. Wybór izolacji powinien wynikać z wymagań spójności danych i charakterystyki obciążenia.

## Propagacja transakcji

Propagacja określa, co ma się stać, gdy metoda transakcyjna zostanie wywołana w kontekście istniejącej transakcji. Najczęściej używanym trybem jest `REQUIRED`, który dołącza do istniejącej transakcji lub tworzy nową, jeśli żadna nie istnieje. Jest to rozsądne domyślne zachowanie dla wielu operacji aplikacyjnych.

`REQUIRES_NEW` zawsze tworzy nową transakcję, zawieszając istniejącą. Może być użyteczne na przykład do zapisu audytu niezależnie od wyniku głównej operacji. Trzeba jednak uważać, ponieważ niezależna transakcja może zatwierdzić dane nawet wtedy, gdy główna transakcja zostanie wycofana. To bywa pożądane, ale może też prowadzić do niespójności, jeśli użyte bez zrozumienia.

`NESTED` wykorzystuje savepointy, jeśli są wspierane przez technologię transakcyjną. Pozwala wycofać część pracy bez wycofywania całej transakcji. Nie jest jednak tym samym co niezależna transakcja. Inne tryby, takie jak `SUPPORTS`, `MANDATORY`, `NOT_SUPPORTED` czy `NEVER`, mają bardziej specjalistyczne zastosowania i powinny być stosowane wtedy, gdy semantyka metody naprawdę tego wymaga.

Propagacja transakcji powinna być projektowana na poziomie przypadków użycia. Błędem jest traktowanie jej jako technicznego przełącznika naprawiającego przypadkowe problemy. Jeżeli kod wymaga wielu zagnieżdżonych trybów propagacji, warto sprawdzić, czy granice odpowiedzialności serwisów są właściwie ustawione.

## Spring Data i warstwa dostępu do danych

Spring Data upraszcza implementację repozytoriów, generując zapytania na podstawie nazw metod, dostarczając gotowe interfejsy i integrując się z JPA, JDBC, MongoDB oraz innymi technologiami. Ta wygoda jest duża, ale może prowadzić do ukrytych problemów wydajnościowych i projektowych.

W przypadku JPA trzeba rozumieć kontekst persystencji, stan encji, lazy loading, dirty checking, flush i relację między transakcją a sesją. Encja pobrana w transakcji może być zarządzana, a jej zmiany mogą zostać zapisane bez jawnego wywołania `save`, ponieważ mechanizm dirty checking wykryje modyfikacje. Dla jednych programistów jest to wygoda, dla innych źródło zaskoczeń. W obu przypadkach trzeba wiedzieć, kiedy flush następuje i jakie zapytania faktycznie trafiają do bazy.

Problem N+1 jest klasyczną pułapką. Kod może wyglądać niewinnie, ale przez lazy loading generować jedno zapytanie główne i wiele dodatkowych zapytań dla powiązanych encji. Rozwiązaniem może być fetch join, entity graph, odpowiednio zaprojektowane DTO, batch fetching albo zmiana modelu odczytu. Nie ma jednego uniwersalnego rozwiązania, ponieważ wybór zależy od przypadku użycia i ilości danych.

Repozytoria nie powinny przejmować logiki biznesowej. Ich zadaniem jest dostęp do danych, a nie implementacja reguł domenowych. Jednocześnie nie należy ślepo tworzyć repozytorium dla każdej encji, jeśli model domenowy wymaga operacji na agregatach. W architekturze inspirowanej DDD repozytorium powinno zwykle dotyczyć agregatu, a nie każdej tabeli.

## Granice warstw i odpowiedzialność serwisów

Typowa aplikacja Spring składa się z kontrolerów, serwisów, repozytoriów, DTO, encji i konfiguracji. Sama obecność tych warstw nie gwarantuje dobrej architektury. Ważne jest, aby każda warstwa miała jasną odpowiedzialność. Kontroler powinien obsługiwać protokół HTTP, walidację wejścia na poziomie API i mapowanie odpowiedzi. Serwis aplikacyjny powinien koordynować przypadek użycia, transakcję i współpracę komponentów. Domena powinna zawierać reguły biznesowe. Repozytorium powinno ukrywać szczegóły dostępu do danych.

Częsty antywzorzec to anemiczny model domenowy, w którym encje są tylko strukturami danych, a cała logika znajduje się w ogromnych serwisach. Nie zawsze jest to katastrofa, szczególnie w prostych aplikacjach CRUD, ale w złożonej domenie prowadzi do trudnego w utrzymaniu kodu. Innym antywzorcem jest przenoszenie logiki biznesowej do kontrolerów, co utrudnia testowanie i ponowne użycie.

Dobra granica transakcji zwykle pokrywa się z przypadkiem użycia. Metoda serwisu aplikacyjnego reprezentuje operację, która powinna być atomowa z punktu widzenia systemu. Wewnątrz niej można korzystać z repozytoriów, domeny, walidacji i integracji. Nie wszystkie integracje zewnętrzne powinny jednak odbywać się wewnątrz transakcji bazodanowej, ponieważ długie transakcje zwiększają blokowanie i ryzyko problemów. Często lepsze są wzorce outbox, zdarzenia domenowe lub asynchroniczna integracja.

## Spring Security

Spring Security jest rozbudowanym frameworkiem do uwierzytelniania i autoryzacji. Jego mechanizm opiera się na łańcuchu filtrów, kontekście bezpieczeństwa, providerach uwierzytelniania, managerach autoryzacji i integracji z warstwą web oraz metodami. W nowoczesnych aplikacjach często używa się OAuth2, OpenID Connect, JWT, sesji, integracji z identity providerem albo kombinacji tych mechanizmów.

Najważniejsze rozróżnienie to authentication i authorization. Authentication odpowiada na pytanie, kim jest użytkownik lub klient systemu. Authorization odpowiada na pytanie, czy ten podmiot ma prawo wykonać daną operację. Poprawne uwierzytelnienie nie oznacza automatycznie dostępu do zasobu.

Bezpieczeństwo nie powinno opierać się wyłącznie na ukryciu endpointu za gatewayem. Każdy serwis powinien mieć jasno określony model zaufania. W mikroserwisach szczególnie ważne jest bezpieczeństwo komunikacji między usługami, propagacja tożsamości, ograniczanie uprawnień tokenów, walidacja audience i issuer, zarządzanie sekretami oraz ochrona endpointów administracyjnych. Actuator, metryki, health checki i dokumentacja API muszą być skonfigurowane świadomie, ponieważ mogą ujawniać wrażliwe informacje.

Autoryzacja na poziomie metod jest wygodna, ale podobnie jak transakcje opiera się na proxy i ma podobne pułapki. Należy też uważać, aby reguły bezpieczeństwa nie były rozproszone w sposób utrudniający audyt. W krytycznych systemach warto centralizować polityki, dokumentować role i testować scenariusze dostępu równie starannie jak logikę biznesową.

## REST, kontrakty i wersjonowanie API

REST w praktyce oznacza projektowanie zasobów, metod HTTP, kodów statusu, reprezentacji i zasad komunikacji klient-serwer. Wiele API nazywanych REST-owymi jest w rzeczywistości RPC po HTTP. Nie zawsze jest to problem, ale warto świadomie rozumieć różnicę. Dobre API powinno być spójne, przewidywalne, stabilne i dobrze udokumentowane.

Kontrakt API jest zobowiązaniem wobec klientów. Zmiana pola, kodu błędu, semantyki statusu lub formatu daty może zepsuć integrację. Dlatego mikroserwisy wymagają dyscypliny wersjonowania. Można wersjonować przez ścieżkę, nagłówki, typ media albo ewolucję kompatybilną wstecz. Najbezpieczniejszą strategią jest projektowanie zmian tak, aby były addytywne: dodawanie pól zamiast usuwania, tolerowanie nieznanych pól, unikanie zmiany znaczenia istniejących elementów.

Consumer-driven contract testing pomaga utrzymać zgodność między usługą a jej klientami. Zamiast testować tylko serwer w izolacji, sprawdzamy oczekiwania konsumentów wobec kontraktu. Jest to szczególnie ważne w mikroserwisach, gdzie niezależne wdrożenia zwiększają ryzyko niekompatybilnych zmian.

## Mikroserwisy jako system rozproszony

Mikroserwisy są stylem architektury, w którym system składa się z niezależnie wdrażalnych usług, zwykle odpowiadających za określone zdolności biznesowe. Dobrze zaprojektowany mikroserwis ma własną odpowiedzialność, własne dane lub jasno określony model dostępu do danych, niezależny cykl wdrożenia i kontrakt komunikacyjny.

Największym błędem jest dzielenie systemu według warstw technicznych zamiast granic biznesowych. Mikroserwis „users-controller”, „orders-service” i „database-service” niekoniecznie oznaczają dobrą architekturę. Lepszym punktem wyjścia są bounded contexts, agregaty, procesy biznesowe i granice odpowiedzialności zespołów. Mikroserwis powinien minimalizować potrzebę synchronicznej koordynacji z innymi usługami, bo każda taka koordynacja zwiększa sprzężenie.

System mikroserwisowy jest systemem rozproszonym, a systemy rozproszone zawodzą częściowo. Jedna usługa może działać, druga może być wolna, trzecia może zwracać błędy, czwarta może mieć nieaktualne dane, a sieć może opóźniać lub gubić komunikację. Projektowanie mikroserwisów wymaga akceptacji faktu, że awarie są normalnym stanem, a nie wyjątkiem.

## Komunikacja synchroniczna i asynchroniczna

Komunikacja synchroniczna, najczęściej HTTP lub gRPC, jest prosta koncepcyjnie. Klient wysyła żądanie i czeka na odpowiedź. Dobrze nadaje się do zapytań, prostych operacji i przypadków, w których odpowiedź jest potrzebna natychmiast. Jej wadą jest silniejsze sprzężenie czasowe. Jeśli usługa zależna jest wolna lub niedostępna, klient również cierpi, chyba że zastosowano timeouty, fallbacki i izolację.

Komunikacja asynchroniczna przez kolejki lub broker zdarzeń zmniejsza sprzężenie czasowe. Producent publikuje komunikat, a konsument przetwarza go niezależnie. Ten model dobrze wspiera odporność, buforowanie obciążenia i integrację między bounded contexts. Jednocześnie wprowadza złożoność: idempotencję, kolejność zdarzeń, powtórzenia, semantykę dostarczenia, dead letter queue, monitoring lagów i obsługę błędów.

Nie należy traktować asynchroniczności jako automatycznie lepszej. Jest lepsza wtedy, gdy proces biznesowy toleruje opóźnienie i eventual consistency. Jeśli użytkownik musi natychmiast zobaczyć wynik, komunikacja synchroniczna może być prostsza. W praktyce dojrzałe systemy łączą oba modele: synchroniczny odczyt tam, gdzie potrzebna jest odpowiedź, oraz asynchroniczne zdarzenia tam, gdzie liczy się luźniejsze sprzężenie i odporność.

## Spójność danych i transakcje rozproszone

W mikroserwisach jednym z najtrudniejszych tematów jest spójność danych. W monolicie często można objąć wiele operacji jedną transakcją bazodanową. W mikroserwisach każdy serwis powinien posiadać własne dane, a bezpośrednie współdzielenie bazy między usługami jest zwykle antywzorcem. To oznacza, że klasyczne transakcje ACID nie obejmują całego procesu biznesowego między wieloma usługami.

Transakcje rozproszone istnieją, ale często są kosztowne, trudne operacyjnie i nie pasują do niezależności mikroserwisów. Zamiast nich stosuje się wzorce takie jak Saga, Outbox, Inbox, kompensacje i eventual consistency. Saga rozbija proces na serię lokalnych transakcji i akcji kompensacyjnych. Outbox zapewnia, że zapis stanu i publikacja zdarzenia są powiązane przez lokalną transakcję w jednej bazie, a osobny mechanizm publikuje zdarzenie do brokera.

Eventual consistency oznacza, że system nie jest spójny natychmiast, ale dąży do spójności po czasie. Jest to naturalne w systemach rozproszonych, ale wymaga świadomego projektowania UX, procesów biznesowych i obsługi błędów. Nie każdy proces toleruje eventual consistency. Tam, gdzie wymagana jest natychmiastowa silna spójność, mikroserwisowy podział może być błędny lub wymagać zmiany granic usług.

## Odporność: timeout, retry, circuit breaker i bulkhead

Odporność systemu nie polega na tym, że błędy nie występują. Polega na tym, że błędy są izolowane, kontrolowane i nie powodują kaskadowej awarii całego systemu. W mikroserwisach podstawowymi narzędziami są timeouty, retry, circuit breaker, bulkhead, rate limiting i fallbacki.

Timeout jest obowiązkowy przy każdej komunikacji sieciowej. Brak timeoutu oznacza ryzyko nieograniczonego oczekiwania i wyczerpania puli wątków lub połączeń. Retry może pomóc przy błędach przejściowych, ale może też pogorszyć awarię, jeśli zwiększa ruch do już przeciążonej usługi. Retry powinien mieć limit, backoff, jitter i powinien być stosowany tylko dla operacji idempotentnych albo bezpiecznie zaprojektowanych pod powtórzenia.

Circuit breaker chroni system przed ciągłym wywoływaniem usługi, która prawdopodobnie nie działa. Po przekroczeniu progu błędów obwód zostaje otwarty i żądania są szybko odrzucane lub kierowane do fallbacku. Po czasie circuit breaker przechodzi w stan półotwarty i sprawdza, czy usługa wróciła do zdrowia. Bulkhead izoluje zasoby, aby awaria jednej zależności nie zużyła wszystkich wątków, połączeń lub kolejek.

Hystrix był historycznie popularnym narzędziem do odporności, ale w nowoczesnych projektach częściej spotyka się Resilience4j. Ważniejsze od nazwy biblioteki jest jednak zrozumienie wzorców. Źle skonfigurowany retry, circuit breaker bez sensownych progów albo fallback ukrywający realny problem mogą pogorszyć system zamiast go chronić.

## Obserwowalność: logi, metryki, tracing

W mikroserwisach nie da się skutecznie diagnozować problemów bez obserwowalności. Logi pokazują zdarzenia, metryki pokazują trendy i liczby, a tracing pokazuje przepływ żądania przez wiele usług. Te trzy filary uzupełniają się i żaden z nich nie zastępuje pozostałych.

Logi powinny być strukturalne, korelowalne i użyteczne diagnostycznie. W systemie rozproszonym konieczny jest correlation id lub trace id, który pozwala połączyć wpisy z wielu usług. Logowanie nie powinno ujawniać danych wrażliwych, tokenów, haseł ani pełnych danych osobowych. Nadmiar logów jest problemem zarówno kosztowym, jak i diagnostycznym.

Metryki powinny obejmować co najmniej liczbę żądań, latencję, błędy, percentyle, wykorzystanie zasobów, kolejki, pule połączeń, GC i zależności zewnętrzne. Dla usług HTTP standardem myślenia są golden signals: latency, traffic, errors i saturation. Tracing rozproszony, często oparty o OpenTelemetry, pozwala zobaczyć, która usługa, zapytanie lub zależność odpowiada za opóźnienie danego żądania.

Obserwowalność powinna być projektowana od początku. Dodanie jej po wystąpieniu awarii jest trudne, bo właśnie wtedy brakuje danych potrzebnych do diagnozy. Senior powinien traktować metryki, tracing i logi jako część kontraktu produkcyjnego aplikacji, a nie dodatek administracyjny.

## API Gateway i edge service

API Gateway jest punktem wejścia do systemu. Może odpowiadać za routing, TLS termination, rate limiting, uwierzytelnianie, agregację odpowiedzi, transformację nagłówków, CORS, wersjonowanie lub ochronę przed częścią ruchu. Nie powinien jednak stać się miejscem implementacji logiki biznesowej. Gateway z nadmiarem logiki szybko staje się nowym monolitem na brzegu systemu.

W mikroserwisach warto rozróżniać gateway techniczny od backend-for-frontend. Gateway techniczny obsługuje wspólne mechanizmy infrastrukturalne. Backend-for-frontend może dostosowywać API do konkretnego klienta, na przykład aplikacji mobilnej lub panelu webowego. Mieszanie tych odpowiedzialności prowadzi do trudnego w utrzymaniu komponentu.

Gateway nie zwalnia usług wewnętrznych z walidacji i bezpieczeństwa. Jeżeli zakładamy, że ruch wewnętrzny jest zawsze zaufany, ryzykujemy lateral movement po przełamaniu jednego komponentu. W dojrzałych systemach stosuje się zasadę zero trust, ograniczone uprawnienia, walidację tokenów i kontrolę dostępu także między usługami.

## Service discovery, konfiguracja i deployment

W dynamicznym środowisku instancje usług pojawiają się i znikają. Service discovery pozwala klientom odnaleźć dostępne instancje usługi bez ręcznego konfigurowania adresów. Może być realizowane przez platformę, taką jak Kubernetes, przez osobny rejestr usług albo przez mechanizmy load balancera.

Konfiguracja aplikacji powinna być zewnętrzna względem artefaktu. Ten sam obraz aplikacji powinien móc działać w różnych środowiskach z inną konfiguracją. Jednocześnie konfiguracja musi być kontrolowana, wersjonowana i bezpieczna. Sekrety nie powinny znajdować się w repozytorium kodu ani w logach. Zmiany konfiguracji powinny być traktowane jak zmiany produkcyjne, ponieważ mogą zepsuć system równie skutecznie jak błędny kod.

Deployment mikroserwisów wymaga automatyzacji. Ręczne wdrożenia wielu usług są podatne na błędy. Należy stosować CI/CD, health checki, readiness i liveness probes, strategie rolling update, canary lub blue-green, migracje baz danych zgodne z kompatybilnością wsteczną oraz szybki rollback. W systemie rozproszonym samo uruchomienie procesu nie oznacza gotowości usługi do obsługi ruchu.

## Testowanie aplikacji Spring

Testowanie w Springu powinno mieć kilka poziomów. Testy jednostkowe sprawdzają logikę klas bez uruchamiania całego kontekstu. Są szybkie, precyzyjne i powinny stanowić podstawę dla logiki domenowej. Testy integracyjne ze Springiem sprawdzają konfigurację, transakcje, repozytoria, serializację, security i współpracę komponentów. Testy kontraktowe sprawdzają zgodność API między usługami. Testy end-to-end powinny być ograniczone do kluczowych ścieżek, ponieważ są kosztowne i kruche.

`@SpringBootTest` jest wygodne, ale często nadużywane. Uruchomienie całego kontekstu dla każdego prostego testu spowalnia suite i utrudnia diagnozę. Lepsze jest używanie test slices, takich jak testy warstwy web, repozytoriów lub konfiguracji, jeśli pełny kontekst nie jest potrzebny. Test powinien uruchamiać tylko tyle infrastruktury, ile jest konieczne do sprawdzenia danego zachowania.

W testach transakcji należy pamiętać, że testy integracyjne Springa często domyślnie wycofują transakcję po zakończeniu testu. To jest wygodne, ale może ukrywać problemy występujące przy commit, flush, constraintach bazodanowych lub zdarzeniach po transakcji. W testach mikroserwisów ważne są również Testcontainers, WireMock, kontrakty i kontrolowanie zachowania zależności zewnętrznych.

## Typowe pułapki Springa

Pierwsza pułapka to wiara, że adnotacja zawsze działa. `@Transactional`, `@Async`, `@Cacheable`, `@PreAuthorize` i wiele innych mechanizmów działa przez proxy lub interceptory. Jeśli wywołanie nie przechodzi przez odpowiedni mechanizm, adnotacja nie ma efektu. To szczególnie często dotyczy wywołań wewnątrz tej samej klasy.

Druga pułapka to nieświadomy stan w singletonach. Bean singletonowy obsługuje wielu użytkowników i wiele wątków. Przechowywanie danych requestu w polu serwisu prowadzi do błędów współbieżności i wycieków danych między żądaniami.

Trzecia pułapka to zbyt szerokie transakcje. Jeżeli transakcja obejmuje zewnętrzne wywołania HTTP, długie obliczenia albo oczekiwanie na inne zasoby, zwiększa blokowanie w bazie i ryzyko awarii. Transakcja powinna być możliwie krótka i obejmować spójny przypadek użycia.

Czwarta pułapka to traktowanie auto-konfiguracji jako magii. Wygoda Spring Boota działa dobrze, dopóki zachowanie jest zgodne z oczekiwaniami. Przy problemach produkcyjnych trzeba wiedzieć, jakie beany istnieją, skąd pochodzą i dlaczego dana konfiguracja została aktywowana.

Piąta pułapka to mikroserwisy bez niezależności danych i wdrożeń. Jeżeli kilka usług musi być zawsze wdrażanych razem, korzysta z tej samej bazy i wymaga synchronicznych wywołań dla każdej operacji, system ma koszty mikroserwisów bez ich korzyści.

## Top 10 tematów dla programisty Senior

Programista senior powinien umieć wyjaśnić, jak Spring tworzy i inicjalizuje beany oraz czym różni się definicja beana od instancji obiektu. Powinien rozumieć różnice między constructor injection, field injection i setter injection oraz umieć uzasadnić preferencję dla konstruktora. Powinien znać mechanizm proxy Spring AOP, w tym self-invocation, ograniczenia metod finalnych i prywatnych oraz różnice między proxy JDK i CGLIB.

Powinien umieć zaprojektować granice transakcji, wyjaśnić propagację, izolację i rollback oraz wskazać, dlaczego `@Transactional` czasem nie działa. Powinien rozumieć JPA nie tylko na poziomie repozytoriów, ale również kontekstu persystencji, lazy loading, N+1, dirty checking i flush. Powinien umieć projektować API, wersjonowanie kontraktów i testy kontraktowe.

W mikroserwisach powinien znać konsekwencje systemów rozproszonych: częściowe awarie, timeouty, retry, circuit breaker, idempotencję, eventual consistency, sagę i outbox. Powinien umieć zaprojektować obserwowalność: logi strukturalne, metryki, tracing i korelację żądań. Powinien rozumieć Spring Security, różnicę między authentication i authorization oraz ryzyka związane z tokenami, gatewayem i komunikacją service-to-service. Wreszcie powinien umieć powiedzieć, kiedy mikroserwisy są złym wyborem.

## Checklist wdrożenia produkcyjnego

Przed wdrożeniem aplikacji Spring do produkcji należy upewnić się, że konfiguracja jest zewnętrzna i kontrolowana, a sekrety nie znajdują się w kodzie ani logach. Aplikacja powinna mieć poprawnie skonfigurowane health checki, readiness i liveness. Powinna udostępniać metryki, logi strukturalne i tracing. Powinna mieć timeouty dla wszystkich klientów zewnętrznych, limity połączeń, rozsądne pule wątków i mechanizmy odporności tam, gdzie są potrzebne.

Bezpieczeństwo powinno obejmować uwierzytelnianie, autoryzację, walidację wejścia, ochronę endpointów administracyjnych, bezpieczne nagłówki, kontrolę CORS, zarządzanie tokenami i ograniczony dostęp między usługami. Baza danych powinna mieć migracje wersjonowane i kompatybilne z procesem wdrożenia. API powinno być zgodne z kontraktami i kompatybilne wstecz, jeśli istnieją niezależni klienci.

Dla mikroserwisów należy dodatkowo sprawdzić, czy usługa ma jasną odpowiedzialność, czy nie współdzieli bazy w sposób łamiący autonomię, czy obsługuje powtórzenia komunikatów, czy operacje są idempotentne tam, gdzie to wymagane, czy istnieje dead letter queue dla komunikacji asynchronicznej oraz czy awaria zależności nie powoduje kaskadowej awarii całego systemu.

## Podsumowanie

Spring i mikroserwisy są potężnymi narzędziami, ale ich skuteczne użycie wymaga zrozumienia mechanizmów ukrytych pod wygodnymi abstrakcjami. Kontener IoC, cykl życia beanów, AOP, proxy, transakcje, Spring Data, Security i auto-konfiguracja tworzą spójny ekosystem, który może znacząco przyspieszyć budowę aplikacji. Ten sam ekosystem może jednak prowadzić do trudnych błędów, jeśli jest traktowany jak magia.

Mikroserwisy zwiększają niezależność i skalowalność organizacyjną, ale wprowadzają złożoność systemów rozproszonych. Wymagają odporności, obserwowalności, spójnego modelu bezpieczeństwa, dojrzałego CI/CD, wersjonowania kontraktów i świadomego zarządzania danymi. Nie są naturalnym następcą monolitu w każdym projekcie. Często dobrze zaprojektowany modularny monolit jest lepszym wyborem niż przedwczesny system mikroserwisowy.

Najważniejsza umiejętność seniora polega na świadomym wyborze poziomu złożoności. Spring powinien upraszczać kod, a nie ukrywać brak architektury. Mikroserwisy powinny rozwiązywać realne problemy niezależności i skalowania, a nie tworzyć rozproszony monolit. Dobre decyzje techniczne wynikają z rozumienia mechanizmów, wymagań biznesowych i kosztów operacyjnych.
