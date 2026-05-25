# Tracing z OpenTelemetry w aplikacji Java

## Cel dokumentu

Ten dokument opisuje teoretyczne podstawy projektowania tracingu z użyciem OpenTelemetry w aplikacji Java na przykładzie usługi `checkout-api`. Nie jest to instrukcja mechanicznego dodania kilku bibliotek do projektu, tylko opis sposobu myślenia o śladach rozproszonych, spanach, propagacji kontekstu, semantyce atrybutów, samplingu i korelacji z metrykami oraz logami. Kod aplikacyjny może się różnić w zależności od frameworka, ale zasady pozostają takie same: trace ma opowiadać spójną historię jednego requestu przechodzącego przez granice procesu, zależności sieciowe i istotne kroki domenowe.

W kontekście `checkout-api` najważniejszym przypadkiem użycia jest endpoint `POST /orders/:id/pay`. Taki request zwykle przechodzi przez kilka potencjalnie wolnych lub zawodnych etapów: wejście HTTP, odczyt z cache Redis, ewentualny odczyt z PostgreSQL, logikę domenową płatności i wywołanie zewnętrznego providera płatności. Tracing ma umożliwić odpowiedź na pytanie, gdzie dokładnie został zużyty czas i czy problem wynika z aplikacji, cache, bazy danych, sieci, zewnętrznego API albo niefortunnej kombinacji tych elementów.

## Trace, span i kontekst śladu

W OpenTelemetry trace jest zbiorem spanów, które razem opisują przebieg jednej operacji rozproszonej. Span reprezentuje pojedynczy odcinek pracy: obsługę żądania HTTP, komendę Redis, zapytanie do PostgreSQL, wywołanie providera płatności albo fragment logiki domenowej. Sam trace nie jest więc jednym logiem ani pojedynczym rekordem. Jest strukturą czasową pokazującą kolejność, zagnieżdżenie i czas trwania poszczególnych etapów.

Najważniejszym elementem, który spina spany w jedną całość, jest kontekst śladu. Zawiera on przede wszystkim `Trace ID`, `Span ID`, `Trace Flags` i `Trace State`. `Trace ID` identyfikuje cały ślad, a `Span ID` identyfikuje konkretny odcinek pracy. Dzięki temu Redis, PostgreSQL, klient HTTP i kod domenowy mogą wytwarzać własne spany, ale nadal pozostawać częścią tej samej historii requestu. Bez prawidłowej propagacji kontekstu otrzymalibyśmy wiele niepowiązanych fragmentów, które trudno analizować podczas incydentu.

W praktyce oznacza to, że aplikacja nie powinna tworzyć losowych identyfikatorów śladu w każdym miejscu. Kontekst powinien być przyjęty na wejściu, kontynuowany wewnątrz procesu i wstrzyknięty do wywołań wychodzących. W HTTP standardowym mechanizmem jest W3C Trace Context, czyli przede wszystkim nagłówek `traceparent` oraz opcjonalny `tracestate`. `traceparent` niesie podstawową pozycję requestu w śladzie, a `tracestate` pozwala przenieść dodatkowy, vendor-specific kontekst. Te nagłówki nie są miejscem na dane użytkownika, dane osobowe ani informacje biznesowe. Ich rolą jest wyłącznie propagacja kontekstu tracingowego.

## Rola spanów w diagnostyce

Dobrze zaprojektowany trace nie polega na tym, że aplikacja tworzy jak najwięcej spanów. Nadmiar spanów może utrudnić analizę tak samo jak ich brak. Celem jest odzwierciedlenie istotnych hipotez diagnostycznych. Jeżeli podczas incydentu zespół chce wiedzieć, czy spowolnienie pochodzi z Redis, PostgreSQL, zewnętrznego providera płatności albo logiki domenowej, każdy z tych etapów powinien mieć osobny span.

Dla `checkout-api` minimalny sensowny podział wygląda następująco. Pierwszy span typu `SERVER` opisuje wejściowe żądanie HTTP `POST /orders/:id/pay`. Następnie span domenowy, na przykład `charge order`, obejmuje główną operację biznesową. Wewnątrz niej znajdują się spany typu `CLIENT` dla Redis, PostgreSQL i providera płatności. Redis może mieć span `GET` z atrybutami `db.system.name=redis` i `db.operation.name=GET`. PostgreSQL może mieć span `SELECT orders` z `db.system.name=postgresql` i `db.query.summary=SELECT orders`. Zewnętrzny provider płatności może mieć span HTTP typu `CLIENT`, opisujący metodę, host i status odpowiedzi.

Taki podział nie jest przypadkowy. Kiedy rośnie p99 dla endpointu płatności, trace powinien natychmiast wskazać, który odcinek zjada czas. Jeśli większość czasu jest w Redis, badamy cache i sieć. Jeśli w PostgreSQL, badamy zapytania, indeksy, pool połączeń i locki. Jeśli w providerze płatności, analizujemy upstream i timeouty. Jeśli w spanie domenowym poza zależnościami, problem może leżeć w samej aplikacji, serializacji, walidacji, kolejkowaniu albo kodzie biznesowym.

## Nazwy spanów i semantyka atrybutów

Nazwy spanów muszą być stabilne i niskokardynalne. To jedna z najważniejszych zasad tracingu. Span dla endpointu HTTP powinien używać template’u trasy, na przykład `POST /orders/:id/pay`, a nie surowej ścieżki `POST /orders/123/pay`. Surowe identyfikatory w nazwach spanów prowadzą do eksplozji unikalnych wartości i utrudniają agregację w backendzie tracingowym. To samo dotyczy zapytań do bazy danych. Pełny SQL zwykle nie powinien być nazwą spana, bo może zawierać dane wrażliwe i tworzyć ogromną liczbę wariantów. Lepszym wyborem jest niskokardynalne `db.query.summary`, na przykład `SELECT orders`.

Atrybuty spanów powinny opisywać to, co jest potrzebne do filtrowania, grupowania i diagnozy. Dla HTTP istotne są między innymi metoda, route template i status odpowiedzi. Dla Redis ważne są system bazy, operacja, namespace oraz adres serwera. Dla PostgreSQL istotne są system, podsumowanie zapytania, host i port. Dla wywołań zewnętrznych potrzebujemy metody HTTP, adresu hosta i statusu odpowiedzi. Atrybuty domenowe również mogą być użyteczne, ale wymagają większej ostrożności. `app.order.id` może pomóc w debugowaniu, ale w wielu organizacjach będzie zbyt wysokokardynalny albo potencjalnie wrażliwy. Często lepiej zapisać typ przepływu, kanał płatności, wersję eksperymentu albo klasę operacji niż konkretny identyfikator użytkownika lub zamówienia.

Warto traktować nazwy spanów i atrybuty jak kontrakt telemetryczny. Jeśli jeden zespół używa `db.system.name=postgresql`, drugi `db.system=postgres`, a trzeci `database=pg`, backend observability będzie miał problem z sensownym filtrowaniem. Dlatego w kodzie warto centralizować nazwy atrybutów i spanów w klasach stałych albo fabrykach spanów. Nie chodzi o estetykę kodu, tylko o stabilność danych operacyjnych.

## Auto-instrumentation i manual instrumentation

OpenTelemetry pozwala korzystać zarówno z automatycznej, jak i ręcznej instrumentacji. W aplikacjach Java często zaczyna się od OpenTelemetry Java Agent, który potrafi automatycznie instrumentować popularne frameworki HTTP, klientów HTTP, JDBC, biblioteki bazodanowe i inne komponenty. To daje szybki baseline bez przepisywania kodu aplikacji. Baseline ten jest szczególnie wartościowy na granicach systemu: request HTTP wchodzący, request HTTP wychodzący, zapytania JDBC, Redis, Kafka i podobne zależności.

Sama auto-instrumentation zwykle nie wystarcza do pełnego zrozumienia przepływu domenowego. Agent może pokazać, że wykonano zapytanie do PostgreSQL i wywołanie HTTP, ale nie zawsze powie, że całość była częścią operacji `charge order`, że cache miss wymusił odczyt z bazy, albo że dana ścieżka była specyficznym wariantem procesu płatności. Właśnie tutaj pojawia się manual instrumentation. Ręczne spany powinny opisywać krytyczne etapy domenowe oraz miejsca, gdzie zespół naprawdę chce testować hipotezy diagnostyczne.

Najzdrowszy model jest hybrydowy. Auto-instrumentation zapewnia szerokie pokrycie technicznych zależności, a manual instrumentation dodaje semantykę biznesową i normalizuje miejsca, w których domyślna instrumentacja jest zbyt ogólna. Nie należy jednak dublować spanów bez potrzeby. Jeśli agent już tworzy poprawny span HTTP klienta, ręczny span o tym samym zakresie może tylko zaśmiecić trace. Manual span ma sens wtedy, gdy dodaje istotną informację lub porządkuje granicę, której automatyczna instrumentacja nie rozumie.

## Propagacja kontekstu między usługami

Tracing rozproszony działa tylko wtedy, gdy kontekst przechodzi przez granice procesu. W HTTP oznacza to wstrzyknięcie nagłówków zgodnych z W3C Trace Context do wywołań wychodzących i odczytanie ich na wejściu w następnej usłudze. W typowej aplikacji Java instrumentowany klient HTTP zrobi to automatycznie. Jeśli jednak korzystamy z niestandardowego klienta, SDK providera albo własnej warstwy komunikacji, propagację trzeba wykonać ręcznie.

Obok `traceparent` często pojawia się `x-request-id`. Trzeba rozróżniać te dwa mechanizmy. `traceparent` jest standardowym nośnikiem kontekstu tracingowego, a `x-request-id` jest aplikacyjnym identyfikatorem wejścia, użytecznym w supportcie, logach i komunikacji z innymi zespołami. `request_id` nie zastępuje `trace_id`, a `trace_id` nie zawsze zastępuje `request_id`. Najlepsza praktyka polega na tym, by oba identyfikatory były obecne w logach, ale w metrykach nie stawały się labelami.

Ważne jest również, aby nie traktować `tracestate` jako dowolnego miejsca na dane aplikacyjne. Jest to mechanizm dla dodatkowego kontekstu tracingowego, często vendor-specific. Nie powinien zawierać PII, sekretów, tokenów, adresów email, identyfikatorów kart ani innych informacji wrażliwych. W praktyce nagłówki tracingowe muszą być traktowane jak część powierzchni bezpieczeństwa systemu.

## Błędy i status spanów

Span powinien odzwierciedlać nie tylko czas trwania operacji, ale również jej wynik. Gdy operacja kończy się błędem, span powinien mieć status błędu i zarejestrowany wyjątek. To jest szczególnie ważne przy tail samplingu, ponieważ Collector może zachowywać wszystkie trace’y błędne, nawet jeśli normalnie przechowuje tylko próbkę ruchu. Jeśli kod łapie wyjątek i zamienia go na odpowiedź HTTP bez oznaczenia spana jako błędnego, backend tracingowy może potraktować problem jako zwykły sukces.

Nie każdy status HTTP 4xx musi oznaczać błąd systemowy. Błąd walidacji wejścia albo brak autoryzacji może być oczekiwanym wynikiem biznesowym. Natomiast 5xx, timeout zależności, wyjątek w kodzie, błąd połączenia z bazą czy niedostępność providera płatności powinny być oznaczane jako problemy operacyjne. Zespół powinien mieć spójną politykę: które wyniki są błędami aplikacyjnymi, które są błędami użytkownika, a które powinny wpływać na alerting i sampling.

W atrybutach błędu lepiej przechowywać klasę błędu, na przykład `TimeoutException` albo `PaymentProviderUnavailable`, niż pełną wiadomość wyjątku jako wymiar do grupowania. Wiadomości wyjątków bywają długie, zmienne i mogą zawierać dane wrażliwe. Trace powinien pomagać w diagnozie, ale nie może stawać się przypadkowym kanałem wycieku danych.

## Sampling: head sampling i tail sampling

Nie każda organizacja może przechowywać każdy trace. W systemach o dużym ruchu koszt przechowywania wszystkich spanów szybko staje się znaczący. Sampling jest więc częścią projektu tracingu, a nie późniejszą optymalizacją. Najprostszy wariant to head sampling, w którym decyzja zapada na początku śladu. Praktycznym wyborem w OpenTelemetry jest konfiguracja w stylu `ParentBased(root=TraceIdRatioBased)`, gdzie root trace jest próbkowany według prawdopodobieństwa, a decyzja rodzica jest respektowana w kolejnych usługach. To podejście jest tanie, przewidywalne i łatwe do wdrożenia.

Head sampling ma jednak ograniczenie: decyzja zapada zanim wiemy, czy request będzie wolny albo błędny. Można więc przypadkowo odrzucić najciekawsze trace’y. Tail sampling rozwiązuje ten problem inaczej. Decyzja zapada dopiero wtedy, gdy Collector zobaczy większą część albo całość trace’u. Dzięki temu można zachować wszystkie błędne trace’y, wszystkie powyżej jednej sekundy, większy procent ruchu z nowej wersji albo konkretną próbkę baseline. Jest to diagnostycznie silniejsze, ale wymaga bardziej zaawansowanej infrastruktury.

Przy tail samplingu trzeba pamiętać, że wszystkie spany jednego trace’u powinny trafić do tego samego miejsca decyzyjnego. W większej skali oznacza to potrzebę trace-ID-aware load balancing albo dwuwarstwowej architektury Collectorów. Jeśli spany jednego śladu zostaną rozrzucone losowo między różne Collectory, decyzja samplingowa może być niepełna albo niespójna.

## Exemplars jako pomost między metrykami i trace’ami

Metryki i trace’y odpowiadają na różne pytania. Metryka mówi, że p99 dla `POST /orders/:id/pay` wzrosło albo że error rate przekroczył próg. Trace pokazuje konkretny przebieg requestu i pozwala zobaczyć, gdzie czas został zużyty. Exemplars są pomostem między tymi światami. Pozwalają dołączyć do punktu metrycznego kontekst, najczęściej `trace_id` i `span_id`, bez wrzucania tych identyfikatorów do zwykłych labeli Prometheusa.

To rozróżnienie jest bardzo ważne. `trace_id` jako label metryki zniszczyłby kardynalność, bo prawie każdy request miałby inną wartość. Exemplar pozwala przejść z wykresu do konkretnego trace’u bez tworzenia nowego szeregu czasowego dla każdego requestu. W praktyce podczas incydentu operator widzi wzrost p99 na histogramie latency, klika exemplar z problematycznego okresu i trafia do trace’u pokazującego, że większość czasu została spędzona na przykład w PostgreSQL albo u providera płatności.

## Korelacja tracingu z logami i metrykami

Tracing jest najwartościowszy wtedy, gdy nie jest izolowanym sygnałem. `trace_id` i `span_id` powinny trafiać do logów strukturalnych, dzięki czemu z trace’u można przejść do logów konkretnego requestu. Metryki powinny mieć niskokardynalne labele, takie jak `service`, `route`, `method`, `status_code`, `db_system` albo `provider`, a przejście do konkretnego requestu powinno odbywać się przez exemplars. Te trzy sygnały mają różne role: metryki wykrywają objaw i skalę problemu, trace lokalizuje wąskie gardło, a logi dostarczają kontekstu zdarzeń.

Nie należy próbować zastąpić jednego sygnału drugim. Logi ze wszystkimi szczegółami nie zastąpią percentyli latency. Metryki nie pokażą dokładnego przebiegu pojedynczego requestu. Trace bez dobrych metryk może być tylko losowym przykładem bez informacji, czy problem jest powszechny. Dobre observability polega na świadomym połączeniu tych sygnałów, a nie na maksymalnym zbieraniu wszystkiego.

## Projekt klas Java dla tracingu

W warstwie kodu warto oddzielić instrumentację od logiki biznesowej. Klasa w rodzaju `CheckoutSpanFactory` centralizuje tworzenie spanów i pilnuje stabilnych nazw oraz atrybutów. `TraceHeaderPropagator` odpowiada za wstrzykiwanie kontekstu do nagłówków wychodzących. `SpanErrorHandler` narzuca spójną politykę oznaczania błędów. `TraceContextSnapshot` umożliwia pobranie aktualnego `trace_id` i `span_id` do logów lub exemplarów. Wrappery takie jak `TracedRedisClient`, `TracedOrderRepository` i `TracedPaymentProviderClient` pokazują, jak opakować zależności, aby każdy istotny downstream miał własny span.

Taka struktura ma przewagę nad rozproszonym wywoływaniem `tracer.spanBuilder(...)` w wielu miejscach kodu. Jeśli programiści tworzą spany ad hoc, szybko pojawią się niespójne nazwy, różne atrybuty dla tego samego pojęcia i przypadkowe dane wysokokardynalne. Fabryka spanów działa jak kontrakt telemetryczny. Wymusza, że Redis zawsze wygląda jak Redis, PostgreSQL zawsze jak PostgreSQL, a endpoint płatności zawsze jest identyfikowany przez route template, a nie przez raw URL.

Nie oznacza to, że każda metoda w systemie powinna mieć wrapper tracingowy. Instrumentujemy te miejsca, które pomagają odpowiadać na pytania operacyjne. Jeśli metoda wykonuje czystą transformację danych trwającą mikrosekundy, span może nie wnieść wartości. Jeśli metoda reprezentuje krok domenowy, długotrwałą operację, komunikację z zależnością albo decyzję wpływającą na przepływ requestu, span ma większy sens.

## Pułapki projektowe

Najczęstszą pułapką jest umieszczanie identyfikatorów użytkowych w nazwach spanów lub w atrybutach, które później służą do grupowania. `POST /orders/123/pay` wygląda kusząco podczas lokalnego debugowania, ale w produkcji jest gorsze niż `POST /orders/:id/pay`. Podobnie pełny SQL może chwilowo pomóc, ale często zawiera wartości parametrów, dane wrażliwe i ogromną liczbę wariantów. Bezpieczniejszym standardem jest query summary oraz ewentualnie osobne, starannie dobrane atrybuty.

Druga pułapka to dublowanie auto-instrumentation. Jeśli Java Agent tworzy już span JDBC, a aplikacja tworzy drugi identyczny span wokół tego samego zapytania, trace staje się trudniejszy do czytania. Manual span powinien dodawać informację domenową albo porządkować semantykę, a nie bezmyślnie powielać techniczny span. Trzecia pułapka to brak spójnej obsługi błędów. Trace, w którym zależność rzuciła wyjątek, ale span nie ma statusu błędu, może zostać pominięty przez politykę samplingową i źle zinterpretowany przez operatora.

Czwarta pułapka to traktowanie samplingu jako ustawienia kosmetycznego. Sampling decyduje, jakie dowody będą dostępne podczas incydentu. Zbyt agresywny head sampling może odrzucać rzadkie błędy. Źle skonfigurowany tail sampling może być kosztowny albo niespójny. Dlatego polityka samplingowa powinna wynikać z celów operacyjnych: zachować błędy, zachować wolne requesty, zachować reprezentatywny baseline i zwiększać próbkę dla nowych wersji lub ryzykownych ścieżek.

## Kryterium gotowości

Tracing dla `checkout-api` można uznać za sensownie zaprojektowany dopiero wtedy, gdy pojedynczy trace pokazuje pełną ścieżkę requestu przez wejście HTTP, cache, bazę danych, providera płatności i logikę domenową. Spany powinny mieć stabilne nazwy, niskokardynalne atrybuty i poprawny parent-child relationship. Kontekst powinien przechodzić do wywołań wychodzących przez standardowe nagłówki, a `trace_id` i `span_id` powinny być dostępne w logach oraz exemplarach.

Drugim kryterium jest użyteczność podczas incydentu. Jeśli p99 endpointu płatności rośnie, operator powinien móc przejść z metryki do exemplaru, z exemplaru do trace’u, a z trace’u do logów. Trace powinien pokazać, czy czas zjadł Redis, PostgreSQL, provider płatności czy kod domenowy. Jeśli zespół nadal musi zgadywać, który downstream jest winny, tracing jest obecny technicznie, ale nie spełnia swojej funkcji diagnostycznej.

## Podsumowanie

Tracing z OpenTelemetry nie polega na dodaniu kolejnego narzędzia do stosu observability. Jego celem jest zbudowanie zrozumiałej, spójnej historii requestu w systemie rozproszonym. W aplikacji Java najlepszym punktem startowym jest zwykle auto-instrumentation, ale dojrzały system wymaga również ręcznych spanów dla istotnych operacji domenowych i zależności. Nazwy spanów, atrybuty, propagacja kontekstu, obsługa błędów i sampling muszą być projektowane świadomie, bo to one decydują, czy trace będzie dowodem, czy tylko dekoracją w narzędziu observability.

Dobrze zaprojektowany trace dla `POST /orders/:id/pay` powinien prowadzić od objawu użytkownika do konkretnego bottlenecku. Jeśli metryka pokazuje wzrost p99, exemplar prowadzi do trace’u, trace wskazuje wolny span PostgreSQL, a logi potwierdzają szczegóły requestu, zespół nie diagnozuje już intuicją. Diagnozuje dowodami. To jest praktyczna różnica między samym zbieraniem telemetrycznych danych a prawdziwą observability.
