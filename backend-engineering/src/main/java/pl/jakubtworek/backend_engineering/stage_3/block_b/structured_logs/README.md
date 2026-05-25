# Structured logging i observability w aplikacji Java

## Cel koncepcji

Ten dokument opisuje teoretyczny model klas Java przeznaczonych do obsługi logów strukturalnych w aplikacji backendowej. Chodzi nie tylko o wygodne wypisywanie komunikatów do logów, ale o zaprojektowanie stabilnego kontraktu telemetrycznego, który może być wykorzystywany przez narzędzia observability: systemy logowania, tracing, metryki, dashboardy, alerty i mechanizmy dochodzenia incydentowego.

Najważniejsze założenie jest proste: log nie powinien być przypadkowym tekstem zapisanym w momencie błędu. W systemie produkcyjnym log jest zdarzeniem opisującym coś, co wydarzyło się w aplikacji, w konkretnym kontekście requestu, usługi, środowiska, wersji wdrożenia i ścieżki wykonania. Jeżeli logi mają pomagać w diagnozowaniu problemów, ich pola muszą być stabilne, przewidywalne i możliwe do korelacji z trace’ami oraz metrykami.

W praktyce oznacza to, że samo użycie formatu JSON nie wystarcza. JSON jest jedynie sposobem serializacji danych. Log strukturalny wymaga czegoś więcej: powtarzalnego schematu, jednoznacznej semantyki pól i dyscypliny w nazewnictwie. Jeżeli jedna część aplikacji zapisuje `traceId`, druga `trace_id`, a trzecia `trace-id`, to system formalnie może emitować JSON, ale operacyjnie nadal produkuje niespójne, trudne do użycia logi. Model klas powinien temu przeciwdziałać.

## Główna idea projektu

Proponowany model zakłada rozdzielenie odpowiedzialności pomiędzy kilka niewielkich klas. Nie chodzi o stworzenie jednej dużej klasy typu `LogMessage`, do której programista ręcznie wkłada dowolne pola. Taki projekt szybko prowadzi do chaosu, ponieważ każdy zespół, moduł lub endpoint zaczyna definiować własny nieformalny standard logowania. Lepszym rozwiązaniem jest potraktowanie logów jako zdarzeń domenowych o stabilnym kształcie.

Centralnym elementem jest `StructuredLogEvent`, czyli reprezentacja pojedynczego zdarzenia telemetrycznego. Taka klasa przechowuje pola, które później mogą zostać zserializowane do JSON-a i wysłane do systemu logowania. Nie powinna jednak być używana bezpośrednio w każdym miejscu aplikacji jako worek na dowolne dane. Dlatego obok niej pojawiają się fabryki zdarzeń, takie jak `HttpLogEvents`, `CacheLogEvents`, `DatabaseLogEvents` czy `ExternalApiLogEvents`. Ich zadaniem jest nadawanie zdarzeniom spójnego kształtu zależnego od typu operacji.

Dzięki temu log zakończonego requestu HTTP zawsze ma podobne pola: metodę HTTP, szablon trasy, status odpowiedzi i czas trwania. Log operacji Redis zawsze ma informację o systemie bazodanowym, typie operacji, adresie serwera, trafieniu w cache i czasie wykonania. Log zapytania do bazy danych może zawierać nazwę systemu, rodzaj operacji oraz podsumowanie zapytania, ale nie powinien bezrefleksyjnie zapisywać pełnego SQL-a ani danych wrażliwych.

## Resource jako opis źródła telemetrii

Klasa `ServiceResource` opisuje źródło telemetrii. W praktyce są to informacje, które nie dotyczą pojedynczego zdarzenia, ale procesu, instancji lub wdrożenia emitującego dane. Typowymi przykładami są `service.name`, `service.version`, `deployment.environment.name` i `service.instance.id`.

To rozróżnienie jest ważne. Dane takie jak nazwa usługi czy środowisko nie powinny być traktowane jako przypadkowe atrybuty aplikacyjne. One definiują, skąd pochodzi log. Dzięki nim można odróżnić zdarzenia z produkcji od zdarzeń ze stagingu, porównać zachowanie dwóch wersji usługi po rolloutcie albo sprawdzić, czy problem dotyczy jednej instancji, czy całego klastra.

W teorii observability `Resource` pełni rolę wspólnego mianownika dla logów, metryk i trace’ów. Jeżeli logi używają `checkout-api`, metryki `checkout_service`, a trace’y `checkout`, to korelacja między sygnałami staje się trudniejsza, a czasem wręcz niemożliwa bez dodatkowych reguł mapowania. Dlatego `service.name` powinno być spójne w całym systemie telemetrycznym.

## CorrelationContext jako podstawa dochodzenia

Klasa `CorrelationContext` reprezentuje identyfikatory pozwalające połączyć pojedynczy log z requestem i trace’em. W tym modelu występują trzy pojęcia: `request_id`, `trace_id` i `span_id`.

`request_id` jest identyfikatorem aplikacyjnym. Bardzo często powstaje na wejściu requestu do systemu i jest używany przez support, aplikację frontendową, API gateway albo backend. Jego zaletą jest praktyczność: łatwo przekazać go w zgłoszeniu błędu lub znaleźć w logach.

`trace_id` ma szersze znaczenie. Identyfikuje całą rozproszoną ścieżkę wykonania requestu przez wiele komponentów. Jeden trace może obejmować wejście HTTP, odczyt z Redis, zapytanie do PostgreSQL, wywołanie zewnętrznego API i odpowiedź końcową. Dzięki temu można przejść od objawu, na przykład wysokiego p95 latency, do konkretnej ścieżki requestu.

`span_id` identyfikuje konkretny fragment tej ścieżki. Może to być span obsługujący request HTTP, span zapytania do bazy albo span wywołania cache. Jeżeli `span_id` występuje w logu, powinien istnieć także `trace_id`, ponieważ sam span bez trace’a traci większość sensu diagnostycznego. Z tego powodu model może walidować, że obecność `span_id` wymaga obecności `trace_id`.

## Severity i stabilne poziomy ważności

Poziom ważności logu nie powinien być dowolnym stringiem. Klasa `LogSeverity` reprezentuje kontrolowany zestaw poziomów, takich jak `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` i `FATAL`. Oprócz tekstowej reprezentacji warto przechowywać także numeryczny poziom ważności, ponieważ część systemów telemetrycznych operuje na wartościach liczbowych.

Nie należy jednak traktować severity jako jedynego kryterium alertowania. W dobrze zaprojektowanym systemie observability log `ERROR` nie zawsze oznacza incydent, a brak logów `ERROR` nie oznacza, że użytkownicy nie cierpią. Alertowanie powinno być oparte przede wszystkim na symptomach użytkownika, takich jak wzrost błędów, timeoutów lub latencji. Severity jest natomiast bardzo przydatne podczas filtrowania, eksploracji i korelacji zdarzeń w czasie dochodzenia.

## Event name jako kontrakt, nie opis literacki

Pole `event.name` jest jednym z najważniejszych elementów schematu. Powinno opisywać klasę zdarzenia, a nie być przypadkowym komunikatem tekstowym. Przykłady dobrych nazw to `http.request.completed`, `cache.lookup`, `db.query.failed` albo `external_api.request.completed`.

Takie nazwy są stabilne i dobrze nadają się do filtrowania, agregacji oraz budowania dashboardów. Programista może zmienić treść pola `body`, ale `event.name` powinien pozostać kontraktem. Jeżeli narzędzia downstream opierają reguły korelacji na `event.name`, to częste i niekontrolowane zmiany tego pola będą psuły observability.

Dlatego w modelu pojawia się klasa `EventNames`, która centralizuje nazwy zdarzeń. Nie jest to tylko wygoda programistyczna. To mechanizm ograniczający ryzyko rozjechania się schematu pomiędzy różnymi częściami kodu.

## StructuredLogEvent jako reprezentacja zdarzenia

`StructuredLogEvent` jest właściwym obiektem reprezentującym pojedynczy log. Zawiera pola wymagane przez schemat, takie jak timestamp, severity, event name, body, resource oraz kontekst korelacyjny. Może też zawierać atrybuty specyficzne dla danego rodzaju operacji.

Istotne jest, że `StructuredLogEvent` nie powinien zachęcać do całkowicie swobodnego dodawania pól. Oczywiście pewna elastyczność jest potrzebna, ponieważ różne zdarzenia wymagają różnych atrybutów. Jednak ta elastyczność powinna być kontrolowana przez fabryki zdarzeń lub buildery, a nie przez ręczne konstruowanie map w całej aplikacji.

Dobry model powinien walidować obecność pól obowiązkowych. Jeżeli zdarzenie nie ma `service.name`, `deployment.environment.name`, `event.name`, `severity_text` albo `body`, to najprawdopodobniej nie spełnia minimalnego standardu telemetrycznego. Walidacja na poziomie klasy pozwala wykrywać błędy wcześnie, zanim niespójne logi trafią do produkcyjnego pipeline’u.

## Fabryki zdarzeń dla HTTP, cache, bazy danych i zewnętrznych API

Fabryki zdarzeń są najważniejszym elementem praktycznej dyscypliny. `HttpLogEvents` wie, jakie pola powinien mieć log requestu HTTP. `CacheLogEvents` wie, jak opisać operację Redis. `DatabaseLogEvents` definiuje kształt logów bazodanowych. `ExternalApiLogEvents` standaryzuje zdarzenia dotyczące zewnętrznych zależności.

Ten podział ma sens, ponieważ różne typy operacji mają różne pytania diagnostyczne. Dla requestu HTTP chcemy wiedzieć, jaka była metoda, jaki był route, jaki status zwrócono i ile trwała obsługa. Dla Redis chcemy wiedzieć, czy cache miał hit czy miss, jaka była operacja i czy wystąpił timeout. Dla bazy danych interesuje nas system, rodzaj operacji, ogólne podsumowanie zapytania i czas wykonania. Dla zewnętrznego API szczególnie ważna jest nazwa zależności oraz jej status odpowiedzi.

Ważne jest także używanie pól niskiej kardynalności. Dla HTTP należy logować `http.route`, czyli szablon trasy, na przykład `/orders/:id/pay`, a nie surową ścieżkę `/orders/123/pay`. Surowa ścieżka może zawierać tysiące lub miliony wariantów i utrudniać grupowanie danych. W metrykach byłoby to szczególnie szkodliwe, ale w logach również powoduje problemy z analizą i filtrowaniem.

## Relacja między logami, metrykami i trace’ami

W observability pojedynczy sygnał rzadko wystarcza. Metryki są najlepsze do wykrywania objawów i mierzenia skali problemu. Trace’y pokazują, gdzie request spędził czas. Logi wyjaśniają, co konkretnie stało się w danym kroku. Model klas do logów strukturalnych powinien wspierać tę relację, a nie działać obok niej.

Dlatego każde zdarzenie powinno mieć pola umożliwiające korelację. `trace_id` pozwala przejść z logu do trace’a. `span_id` pozwala wskazać konkretny fragment ścieżki wykonania. `request_id` pomaga połączyć świat aplikacyjny, supportowy i infrastrukturalny. `service.name` oraz `deployment.environment.name` umożliwiają zawężenie problemu do konkretnej usługi i środowiska.

Dobrze zaprojektowany system powinien umożliwiać przepływ analizy w obie strony. Można zacząć od wykresu latencji, przejść do exemplaru, potem do trace’a, a następnie do logów danego spana. Można też zacząć od zgłoszenia użytkownika zawierającego `request_id`, znaleźć log wejściowy, przejść do `trace_id` i zobaczyć, który downstream odpowiadał za opóźnienie.

## Dlaczego nie logować wszystkiego

Naturalną pokusą jest dodawanie do logów jak największej liczby pól. To podejście jest ryzykowne. Nadmiar danych zwiększa koszt przechowywania, utrudnia wyszukiwanie, może ujawniać dane wrażliwe i zacierać faktycznie istotne informacje. Observability nie polega na bezrefleksyjnym zbieraniu wszystkiego, lecz na zbieraniu danych, które pomagają odpowiadać na pytania diagnostyczne.

Szczególnie ostrożnie należy traktować identyfikatory użytkowników, adresy e-mail, pełne adresy URL, surowe zapytania SQL, tokeny, nagłówki autoryzacyjne i payloady requestów. Część z tych danych może być przydatna w wyjątkowych sytuacjach, ale nie powinna trafiać do standardowego schematu logów bez jasnej polityki bezpieczeństwa, maskowania i retencji.

Z tego powodu w klasach fabrykujących zdarzenia lepiej operować na podsumowaniach niż na pełnych danych. `db.query.summary` jest bezpieczniejsze niż pełny SQL. `http.route` jest lepsze niż surowy URL. `error.type` jest stabilniejsze niż losowy stacktrace jako jedyne źródło prawdy. Stacktrace może być potrzebny, ale powinien uzupełniać strukturę, a nie ją zastępować.

## Przykład interpretacyjny: checkout-api

Dla przykładowej usługi `checkout-api` obsługującej endpoint `POST /orders/:id/pay` dobrze zaprojektowane logi powinny pozwalać odtworzyć historię pojedynczego requestu. Najpierw pojawia się zdarzenie HTTP informujące, że request został przyjęty i zakończony określonym statusem. W tym samym trace mogą pojawić się zdarzenia Redis pokazujące, czy udało się odczytać dane z cache. Następnie mogą wystąpić zdarzenia bazodanowe opisujące operacje na zamówieniu i płatności. Jeżeli aplikacja wywołuje zewnętrzny system płatności, osobne zdarzenie powinno opisać tę zależność.

Dzięki wspólnemu `trace_id` wszystkie te zdarzenia można połączyć w jedną ścieżkę. Dzięki `span_id` można ustalić, który fragment trace’a odpowiada danemu logowi. Dzięki `event.name` można filtrować klasy zdarzeń, a dzięki `duration_ms` porównywać czas wykonania poszczególnych etapów.

W przypadku incydentu typu „checkout jest wolny” nie trzeba wtedy zgadywać, czy problemem jest aplikacja, Redis, baza danych czy zewnętrzny operator płatności. Można przejść od metryki wysokiej latencji do konkretnego trace’a i zobaczyć, który span trwał najdłużej. Następnie można sprawdzić logi tego spana i znaleźć klasę błędu, timeout, miss cache’a albo nietypową odpowiedź downstreamu.

## Integracja z loggerem i serializacją

Model klas nie musi samodzielnie decydować, gdzie trafi log. Jego zadaniem jest przygotowanie stabilnego obiektu zdarzenia. Serializacja do JSON-a może być wykonana przez Jacksona, Gsona albo mechanizm wbudowany w używany framework. W aplikacji produkcyjnej taki obiekt najczęściej zostałby przekazany do SLF4J, Logbacka, Log4j2 albo eksportera OpenTelemetry.

To rozdzielenie jest korzystne. Klasy domenowe definiują semantykę zdarzeń, a infrastruktura logowania odpowiada za transport, formatowanie, buffering, sampling i wysyłkę. Dzięki temu zmiana narzędzia logującego nie musi oznaczać zmiany kontraktu telemetrycznego w całej aplikacji.

Warto jednak pamiętać, że serializacja mapy do JSON-a nie rozwiązuje problemu jakości danych. Kluczowa jest konsekwencja w tym, jakie pola są wymagane, jakie są opcjonalne i jakie wartości są dozwolone. Narzędzie logujące powinno być ostatnim etapem pipeline’u, nie miejscem, w którym dopiero wymyśla się strukturę zdarzenia.

## Granice modelu

Ten model nie zastępuje pełnego OpenTelemetry SDK. Nie tworzy automatycznie spanów, nie eksportuje metryk i nie propaguje nagłówków W3C Trace Context. Jego rolą jest uporządkowanie warstwy logów aplikacyjnych tak, by była kompatybilna z myśleniem observability.

W praktycznej implementacji warto połączyć taki model z instrumentacją OpenTelemetry. Trace i span powinny powstawać w rzeczywistym kontekście wykonania, a `trace_id` i `span_id` najlepiej pobierać z aktywnego spana, zamiast generować ręcznie. Podobnie `request_id` może pochodzić z gatewaya, middleware’u HTTP albo filtra aplikacyjnego.

Nie należy też zakładać, że wszystkie semantic conventions są równie stabilne w każdej wersji bibliotek. Część konwencji, szczególnie wokół niektórych systemów bazodanowych lub cache’y, może zmieniać się wraz z rozwojem OpenTelemetry. Dlatego nazwy pól powinny być świadomie dobrane i okresowo weryfikowane względem wersji używanego SDK oraz wymagań organizacji.

## Kryterium dobrze zaprojektowanego rozwiązania

Dobrze zaprojektowany model logów strukturalnych powinien spełniać kilka warunków. Po pierwsze, każdy log powinien mieć stabilny schemat i dać się przetwarzać maszynowo bez zgadywania znaczenia pól. Po drugie, zdarzenia powinny zawierać resource opisujący usługę, wersję, środowisko i instancję. Po trzecie, logi requestowe powinny mieć korelację przez `request_id`, `trace_id` i `span_id`. Po czwarte, nazwy zdarzeń powinny być kontrolowane i spójne. Po piąte, klasy zdarzeń powinny wymuszać pola właściwe dla danego typu operacji.

Najważniejszym testem nie jest jednak elegancja kodu. Testem jest pytanie, czy w czasie incydentu można szybko odpowiedzieć: który route jest wolny, od kiedy, dla jakiej wersji usługi, na którym downstreamie request traci czas i jaki konkretny log potwierdza hipotezę. Jeżeli model klas pomaga przejść od objawu do dowodu, to wspiera observability. Jeżeli tylko opakowuje `logger.info()` w dodatkową abstrakcję, ale nie wymusza spójności danych, to jest głównie kosmetyką.

## Podsumowanie

Koncepcja klas dla structured logging w Javie powinna być traktowana jako część architektury observability, a nie jako pomocniczy kod do formatowania komunikatów. `ServiceResource` definiuje źródło telemetrii, `CorrelationContext` pozwala połączyć logi z requestem i trace’em, `LogSeverity` normalizuje poziomy ważności, `EventNames` stabilizuje nazwy zdarzeń, `StructuredLogEvent` reprezentuje pojedynczy log, a fabryki domenowe wymuszają spójny kształt zdarzeń HTTP, cache, bazodanowych i zewnętrznych.

Takie podejście przesuwa zespół z poziomu „mamy jakieś logi” na poziom „mamy dane diagnostyczne”. Różnica jest zasadnicza. Pierwsze pomaga czasem znaleźć problem po długim grep’owaniu. Drugie pozwala systematycznie przechodzić od metryki, przez trace, do konkretnego logu i na tej podstawie podejmować decyzje operacyjne.
