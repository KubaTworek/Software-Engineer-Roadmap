# Observability dla inżynierów oprogramowania

## Wprowadzenie

Observability, czyli obserwowalność systemu, jest praktyką projektowania oprogramowania w taki sposób, aby z zewnątrz dało się zrozumieć jego stan wewnętrzny. Nie chodzi wyłącznie o zbieranie logów, metryk i trace’ów. Samo posiadanie dużej ilości telemetrii nie oznacza jeszcze, że system jest obserwowalny. System jest obserwowalny wtedy, gdy inżynier potrafi na podstawie dostępnych danych odpowiedzieć na nowe, wcześniej nieprzewidziane pytania dotyczące działania produkcji.

Najważniejsza zmiana sposobu myślenia polega na odejściu od ogólnych diagnoz typu „system jest wolny” albo „baza danych muli”. Takie stwierdzenia są zbyt nieprecyzyjne, aby prowadziły do skutecznego działania. Zamiast nich należy szukać dowodów: gdzie system zwolnił, w jakim zakresie, dla jakich użytkowników, na których endpointach, w jakiej wersji usługi, w którym regionie, od kiedy i czy problem dotyczy całego ruchu, czy tylko wybranego segmentu.

W praktyce observability jest sposobem prowadzenia dochodzenia produkcyjnego. Inżynier nie powinien opierać się głównie na intuicji, lecz na hipotezach, które można potwierdzić lub obalić za pomocą danych. Metryki pomagają zauważyć, że dzieje się coś niepokojącego. Trace’y pomagają zlokalizować, w którym fragmencie przepływu request traci czas. Logi dostarczają kontekstu konkretnego zdarzenia. Runbooki skracają drogę od wykrycia problemu do pierwszej bezpiecznej mitigacji.

## Observability a monitoring

Monitoring i observability są ze sobą powiązane, ale nie są tym samym. Monitoring odpowiada przede wszystkim na pytania, które wcześniej przewidzieliśmy. Definiujemy metryki, progi, dashboardy i alerty, a następnie sprawdzamy, czy system mieści się w oczekiwanym zakresie. To podejście jest bardzo wartościowe, szczególnie dla znanych klas awarii, ale ma ograniczenia. Jeśli problem jest nowy, nietypowy albo wynika z interakcji wielu komponentów, statyczny zestaw dashboardów może nie wystarczyć.

Observability idzie krok dalej. Jej celem jest umożliwienie zadawania nowych pytań bez konieczności wcześniejszego przewidzenia konkretnego dashboardu. Dobrze zaprojektowana telemetria pozwala analizować produkcję przez wiele wymiarów: usługę, endpoint, wersję wdrożenia, region, typ klienta, status odpowiedzi, zależność downstream, wynik cache’a czy identyfikator trace’a. Dzięki temu inżynier może zawężać problem stopniowo, zamiast przeglądać dziesiątki wykresów w nadziei, że któryś z nich przypadkiem pokaże przyczynę.

Nie oznacza to, że monitoring jest przestarzały. Przeciwnie, monitoring pozostaje podstawą wykrywania problemów. Różnica polega na tym, że w dojrzałym systemie monitoring jest wejściem do procesu diagnostycznego, a nie jego końcem. Alert powinien powiedzieć, że użytkownicy realnie odczuwają problem. Observability powinna pomóc ustalić, dlaczego tak się dzieje.

## Dowody zamiast zgadywania

W środowisku produkcyjnym intuicja bywa użyteczna, ale jest niewystarczająca. Doświadczeni inżynierowie często mają dobre przeczucia, jednak każde przeczucie powinno zostać potraktowane jako hipoteza. Hipoteza wymaga dowodu. Jeśli zespół twierdzi, że winna jest baza danych, powinien być w stanie pokazać trace’e, metryki i logi potwierdzające wzrost czasu zapytań, timeouty, wyczerpanie puli połączeń albo blokady. Jeśli podejrzenie dotyczy Redis, należy sprawdzić hit ratio, latency operacji cache’a, timeouty oraz ewentualny wtórny wzrost obciążenia bazy.

Dobre pytanie operacyjne nie brzmi „czy system jest wolny?”, ale „który request, na którym route, w której usłudze, w którym oknie czasu i na którym downstreamie traci najwięcej czasu?”. Taka forma pytania wymusza konkret. Pozwala odróżnić problem globalny od lokalnego, problem aplikacyjny od infrastrukturalnego, a chwilowy szum od realnego incydentu.

Właśnie dlatego observability wymaga spójnych identyfikatorów i wspólnej semantyki danych. Jeżeli metryki, trace’y i logi opisują ten sam request innymi nazwami pól, w innych formatach i bez wspólnych identyfikatorów, korelacja będzie trudna albo niemożliwa. Wtedy organizacja posiada telemetrię, ale nie posiada sprawnego mechanizmu dochodzeniowego.

## Trzy podstawowe sygnały: metryki, trace’y i logi

W praktyce observability najczęściej opiera się na trzech głównych typach sygnałów: metrykach, trace’ach i logach. Każdy z nich ma inną rolę i żaden nie zastępuje pozostałych.

Metryki są najlepsze do wykrywania zmian w czasie. Dają szybki obraz trendów, wolumenu ruchu, błędów, opóźnień i nasycenia zasobów. Są agregowane, tanie do przechowywania i dobrze nadają się do alertowania. Ich słabością jest jednak utrata szczegółów. Metryka może pokazać, że p95 latency wzrosło, ale sama z siebie nie wyjaśnia, który konkretny request był problematyczny ani gdzie dokładnie stracił czas.

Trace’y opisują przepływ pojedynczego requestu przez system rozproszony. Pokazują kolejne spany: wejście HTTP, wywołanie cache’a, zapytanie do bazy danych, komunikację z zewnętrznym API czy publikację komunikatu do kolejki. Trace jest szczególnie wartościowy wtedy, gdy system składa się z wielu usług i zależności. Pozwala zobaczyć, który fragment ścieżki odpowiada za największy udział w całkowitej latencji.

Logi dostarczają szczegółowego kontekstu zdarzeń. Dobrze zaprojektowany log nie jest luźnym stringiem zapisanym przez aplikację, ale ustrukturyzowanym zdarzeniem o stabilnym schemacie. Powinien zawierać pola takie jak identyfikator requestu, trace_id, span_id, nazwa usługi, środowisko, poziom istotności, nazwa zdarzenia oraz dane domenowe potrzebne do zrozumienia sytuacji. Logi są szczególnie przydatne do wyjaśniania, co dokładnie zaszło w konkretnym przypadku.

Największą wartość te trzy sygnały dają dopiero razem. Metryka informuje, że problem wystąpił. Exemplar albo inny mechanizm korelacji prowadzi z punktu na wykresie do konkretnego trace’a. Trace pokazuje, gdzie request tracił czas. Logi wyjaśniają, co wydarzyło się wewnątrz poszczególnych spanów.

## Golden signals w praktyce SRE

W podejściu Site Reliability Engineering szczególnie ważne są cztery podstawowe sygnały, często określane jako golden signals: latency, traffic, errors i saturation. Są one praktycznym minimum dla systemów obsługujących użytkowników.

Latency oznacza czas obsługi żądań. Nie należy analizować jej wyłącznie za pomocą średnich, ponieważ średnia łatwo ukrywa problemy dotykające części użytkowników. Znacznie bardziej użyteczne są percentyle, szczególnie p95 i p99. Warto również rozdzielać latency requestów zakończonych sukcesem od latency requestów zakończonych błędem. Błąd zwrócony bardzo szybko i sukces zwrócony bardzo wolno są operacyjnie różnymi zjawiskami.

Traffic opisuje ilość pracy wykonywanej przez system. W przypadku API będzie to zwykle liczba requestów na sekundę, liczba operacji domenowych albo liczba komunikatów przetwarzanych przez kolejkę. Analiza latency bez kontekstu ruchu może prowadzić do błędnych wniosków. Ten sam poziom opóźnień przy normalnym ruchu i przy nagłym wzroście obciążenia może mieć zupełnie inne przyczyny.

Errors opisują odsetek lub liczbę operacji zakończonych niepowodzeniem. Mogą to być błędy HTTP 5xx, timeouty, błędy zależności downstream, odrzucone requesty, anulowane operacje lub błędy walidowane na poziomie domeny. Istotne jest, aby błędy klasyfikować w sposób użyteczny operacyjnie. Nie każdy status 4xx powinien oznaczać awarię systemu, ale wzrost timeoutów lub błędów 5xx zwykle wymaga uwagi.

Saturation oznacza stopień nasycenia zasobów. Może dotyczyć CPU, pamięci, garbage collectora, puli połączeń, kolejek, liczby requestów w toku, limitów rate limiting, liczby workerów albo pojemności zależności zewnętrznych. Saturation często ujawnia przeciążenie, zanim system zacznie masowo zwracać błędy.

Dojrzały alerting powinien koncentrować się przede wszystkim na symptomach użytkownika, a nie na każdej możliwej przyczynie technicznej. Alert typu „użytkownicy widzą wzrost p95 latency i błędów na płatnościach” jest zwykle bardziej wartościowy niż alert typu „CPU na jednym podzie przekroczyło próg”. Przyczyny techniczne są ważne w diagnostyce, ale pagingi powinny być ograniczone do sytuacji wymagających realnej reakcji.

## Logi strukturalne

Logi strukturalne są jednym z fundamentów observability. Nie wystarczy jednak „logować w JSON-ie”. JSON jest tylko formatem zapisu. Jeżeli pola są przypadkowe, zmieniają nazwy między usługami albo zawierają niespójne typy danych, logi nadal będą trudne do użycia. Prawdziwie strukturalne logowanie wymaga stabilnego schematu i uzgodnionej semantyki.

Każdy istotny log powinien zawierać podstawowe pola techniczne, takie jak service.name, deployment.environment.name, trace_id, span_id, request_id, severity_text oraz event.name. Dzięki nim można połączyć zdarzenie z usługą, środowiskiem, trace’em i konkretnym requestem. Oprócz tego log powinien zawierać pola domenowe, które pomagają wyjaśnić, co się wydarzyło. W przypadku checkout-api mogą to być na przykład order_id, payment_provider, payment_status, cache_result albo dependency_name.

Trzeba jednak zachować ostrożność. Logi nie powinny zawierać sekretów, tokenów, haseł, danych kart płatniczych ani niepotrzebnych danych osobowych. W observability chodzi o zdolność diagnozowania systemu, a nie o gromadzenie wszystkiego, co da się zapisać. Nadmiar danych zwiększa koszt, ryzyko bezpieczeństwa i trudność analizy.

Dobre logi są zdarzeniami, a nie komentarzami. Zamiast pisać `payment failed for some reason`, lepiej zapisać zdarzenie `payment.failed` z polami opisującymi klasę błędu, zależność, kod odpowiedzi, czas trwania i identyfikatory korelacyjne. Taki log można filtrować, agregować i łączyć z trace’ami.

## Metryki i histogramy

Metryki są podstawą detekcji problemów i alertowania. W systemach opartych o Prometheus szczególnie ważna jest dyscyplina nazewnictwa, typów metryk i etykiet. Nazwy powinny jasno opisywać mierzone zjawisko, jednostki powinny być widoczne w nazwie, a liczniki powinny mieć sufiks `_total`. Czas trwania operacji powinien być wyrażany w sekundach, co ułatwia spójność zapytań i dashboardów.

Do mierzenia latency nie należy używać średniej jako głównego wskaźnika jakości. Średnia może wyglądać dobrze, nawet jeśli znacząca grupa użytkowników doświadcza bardzo wolnych odpowiedzi. Z tego powodu do analizy opóźnień używa się histogramów i percentyli. Histogram pozwala policzyć p95 lub p99 za pomocą funkcji takich jak `histogram_quantile`, zachowując przy tym agregowalność danych.

Równie ważna jest kontrola kardynalności. Etykiety metryk powinny mieć ograniczoną liczbę możliwych wartości. Dobrymi etykietami są na przykład service, route, method, status_code, environment albo dependency. Złymi etykietami są user_id, email, request_id, trace_id, pełny URL, surowe zapytanie SQL albo dowolny identyfikator generujący ogromną liczbę wartości. Wysoka kardynalność może gwałtownie zwiększyć koszt monitoringu i obniżyć stabilność systemu metryk.

W praktyce warto definiować recording rules dla zapytań często używanych w dashboardach i alertach. Pozwala to uniknąć wielokrotnego wykonywania kosztownych obliczeń, szczególnie dla percentyli i agregacji po wielu etykietach. Metryki powinny być projektowane tak, aby wspierały konkretne pytania operacyjne, a nie tylko tworzyły dużą liczbę wykresów.

## Trace’y i propagacja kontekstu

Tracing jest szczególnie ważny w systemach rozproszonych, gdzie pojedyncza operacja użytkownika przechodzi przez wiele komponentów. Trace reprezentuje całą ścieżkę requestu, a span opisuje pojedynczy fragment pracy, na przykład obsługę endpointu HTTP, zapytanie do bazy, operację Redis albo wywołanie zewnętrznego API.

Aby tracing był użyteczny, kontekst musi być propagowany między usługami. Standard W3C Trace Context definiuje nagłówki `traceparent` i `tracestate`, które pozwalają przenosić identyfikator trace’a i informacje o spanach przez granice procesów i usług. Bez poprawnej propagacji każdy komponent może tworzyć własny, odseparowany trace, co niszczy możliwość prześledzenia całej operacji użytkownika.

W trace’ach ważne są spójne nazwy spanów i zgodność z konwencjami semantycznymi. Span dla endpointu HTTP powinien jasno wskazywać metodę i route, ale nie powinien zawierać surowego URL-a z identyfikatorami użytkownika lub zamówienia. Span dla bazy danych powinien wskazywać typ bazy, operację i nazwę logicznej zależności, ale nie powinien bezrefleksyjnie zapisywać pełnych zapytań zawierających dane wrażliwe.

Trace powinien pomagać odpowiedzieć na pytanie, który hop zużywa czas. Jeśli endpoint `POST /orders/:id/pay` ma p95 latency powyżej oczekiwań, trace powinien pokazać, czy czas znika w aplikacji, w Redis, w PostgreSQL, w zewnętrznym operatorze płatności, czy w oczekiwaniu na pulę połączeń. Bez takiego rozbicia zespół pozostaje na poziomie domysłów.

## Exemplars i korelacja sygnałów

Jednym z praktycznych problemów observability jest przejście od agregatu do konkretu. Metryka pokazuje, że w danym oknie czasu p99 latency wzrosło, ale nie wskazuje automatycznie konkretnego requestu. Trace opisuje pojedynczy request, ale nie daje pełnego obrazu trendu. Exemplars łączą te dwa światy.

Exemplar to odniesienie z punktu metrycznego do konkretnego przykładu, najczęściej trace’a. Dzięki temu inżynier może kliknąć opóźniony punkt na wykresie i przejść do trace’a reprezentującego request z tego obszaru rozkładu. To pozwala zachować niską kardynalność metryk, ponieważ trace_id nie musi być etykietą metryki. Jednocześnie możliwe staje się szybkie przejście od objawu do konkretnego dowodu.

Korelacja sygnałów powinna działać w obie strony. Z metryki powinno dać się przejść do trace’a, z trace’a do logów, a z logów z powrotem do trace’a lub danych domenowych. Warunkiem jest konsekwentne używanie wspólnych identyfikatorów, takich jak trace_id, span_id, request_id, service.name i deployment.environment.name.

## Alertowanie zorientowane na użytkownika

Dobre alerty nie powinny informować o każdym technicznym odchyleniu. Powinny informować o sytuacjach, które wymagają reakcji człowieka lub automatycznej mitigacji. Nadmiar alertów prowadzi do zmęczenia, ignorowania powiadomień i spadku zaufania do monitoringu.

Zasada symptom-based alerting oznacza, że alert powinien dotyczyć objawu widocznego dla użytkownika lub istotnego ryzyka dla niezawodności. Przykładem dobrego alertu jest wzrost p95 latency dla płatności, wzrost error rate na endpointach checkoutu albo przekroczenie budżetu błędów. Przykładem słabszego alertu jest pojedynczy restart poda, chwilowy skok CPU albo techniczny warning, który nie ma wpływu na użytkowników i nie wymaga działania.

Każdy alert powinien być actionable, czyli powinien prowadzić do konkretnego działania. Powinien mieć opis, jasne kryterium wyzwolenia, informację o zakresie problemu, etykiety routingowe oraz link do runbooka. Alert bez runbooka bardzo często kończy się improwizacją. Improwizacja może być skuteczna u doświadczonych osób, ale jest trudna do skalowania i nie daje powtarzalności.

## Runbooki jako część observability

Runbook jest instrukcją postępowania w określonej klasie incydentu. Nie powinien być traktowany jako dokumentacja drugiej kategorii. W praktyce runbook jest elementem systemu operacyjnego zespołu. Pomaga skrócić czas od wykrycia problemu do pierwszej rozsądnej mitigacji.

Dobry runbook nie musi opisywać każdego możliwego scenariusza. Powinien natomiast zawierać pierwsze pytania diagnostyczne, linki do dashboardów, przykładowe zapytania, kryteria eskalacji, bezpieczne działania mitigacyjne oraz warunki uznania incydentu za opanowany. Dla systemu checkout-api sensowne są osobne runbooki dla skoku latency, awarii bazy danych i awarii Redis.

Runbook dla latency spike powinien prowadzić od objawu użytkownika do zawężenia problemu: które route, która wersja, który region, które percentyle, czy wzrósł ruch, czy pojawiły się błędy, czy problem dotyczy downstreamu, czy system jest nasycony. Runbook dla awarii bazy powinien skupiać się na połączeniach, timeoutach, błędach zapytań, lockach, saturacji i ewentualnych działaniach ograniczających ruch. Runbook dla Redis powinien dodatkowo uwzględniać hit ratio, timeouty cache’a oraz ryzyko wtórnego przeciążenia bazy danych.

Runbook musi być ćwiczony. Dokument, którego nikt nigdy nie użył, może okazać się nieaktualny dokładnie wtedy, gdy będzie najbardziej potrzebny. Dlatego częścią dojrzałej praktyki observability są okresowe testy, przeglądy po incydentach i aktualizacje instrukcji.

## Przykład: checkout-api i endpoint płatności

Załóżmy, że system `checkout-api` obsługuje endpoint `POST /orders/:id/pay`. Request przychodzi przez HTTP, następnie aplikacja sprawdza dane zamówienia, korzysta z Redis jako cache’a, komunikuje się z PostgreSQL, wywołuje zewnętrzne API operatora płatności i zapisuje wynik operacji.

W słabo obserwowalnym systemie inżynier widzi jedynie, że użytkownicy zgłaszają wolne płatności. Być może istnieje dashboard CPU, ogólny wykres requestów i logi tekstowe. Taki zestaw danych zwykle prowadzi do zgadywania. Ktoś podejrzewa bazę, ktoś inny Redis, a jeszcze ktoś operatora płatności. Bez korelacji dowodów trudno szybko rozstrzygnąć spór.

W dobrze obserwowalnym systemie pierwszym sygnałem jest alert oparty na symptomie użytkownika, na przykład wzrost p95 latency dla `POST /orders/:id/pay` w środowisku produkcyjnym. Alert zawiera link do dashboardu i runbooka. Dashboard pokazuje latency, traffic, errors i saturation. Z punktu na wykresie można przejść do exemplaru, a następnie do trace’a. Trace pokazuje, że większość czasu request spędza w wywołaniu zewnętrznego API płatności albo w oczekiwaniu na połączenie do PostgreSQL. Logi powiązane z trace’em pokazują szczegóły błędu, klasę timeoutu, identyfikator zależności i wersję wdrożenia.

Taki proces nie gwarantuje natychmiastowego rozwiązania każdej awarii, ale znacząco zmniejsza obszar niepewności. Zespół nie musi debatować abstrakcyjnie, czy „baza muli”. Może pokazać konkretny dowód: metrykę, trace i log wskazujące, gdzie request traci czas.

## Kryterium gotowości observability

System można uznać za sensownie przygotowany do dochodzeń incydentowych dopiero wtedy, gdy spełnia kilka warunków. Każdy ważny request powinien mieć identyfikatory korelacyjne obecne w logach i trace’ach. Logi powinny mieć stabilny schemat. Metryki powinny obejmować request count, latency histogram, error rate i liczbę requestów w toku dla istotnych tras. Etykiety metryk powinny mieć ograniczoną kardynalność. Percentyle powinny być liczone z histogramów, a nie ze średnich.

Trace’y powinny obejmować wejście do API oraz istotne zależności, takie jak Redis, PostgreSQL i zewnętrzne API. Spany powinny używać spójnych nazw i konwencji semantycznych. Z metryki powinno dać się przejść do trace’a, z trace’a do logów, a alert powinien prowadzić do runbooka. Runbooki powinny istnieć dla najważniejszych klas incydentów i powinny być przynajmniej raz przećwiczone.

Jeśli tych elementów brakuje, system może mieć monitoring, ale niekoniecznie ma observability. Różnica ujawnia się szczególnie podczas incydentu. Monitoring pokaże czerwony wykres. Observability pozwoli zrozumieć, dlaczego wykres zrobił się czerwony i jakie działanie ma największą szansę poprawić sytuację użytkowników.

## Uwagi o stabilności standardów i implementacji

Observability opiera się na standardach i narzędziach, ale ich dojrzałość nie zawsze jest jednakowa. OpenTelemetry dostarcza wspólny model dla trace’ów, metryk, logów i konwencji semantycznych, jednak poszczególne obszary mogą mieć różny poziom stabilności. Niektóre konwencje są stabilne, inne pozostają w rozwoju. Implementacje SDK dla różnych języków również mogą różnić się zakresem wsparcia.

Dlatego nie należy zakładać, że każda biblioteka automatycznie wygeneruje identyczny zestaw pól i metryk. Nazwy spanów, atrybuty, metryki bazodanowe czy instrumentacja Redis powinny być sprawdzane w konkretnej wersji SDK i biblioteki. To szczególnie ważne wtedy, gdy organizacja buduje wspólne dashboardy, alerty i reguły zależne od konkretnych nazw pól.

Podobnie w Prometheusie trzeba świadomie wybrać sposób pracy z histogramami. Classic histograms i native histograms mają inne konsekwencje operacyjne. Native histograms mogą być bardzo użyteczne, jeśli są wspierane przez używane biblioteki i stos obserwacyjny, ale decyzja powinna być świadoma, a nie przypadkowa.

## Najczęstsze błędy

Jednym z najczęstszych błędów jest traktowanie observability jako problemu narzędziowego. Zakup lub wdrożenie platformy nie wystarczy, jeśli aplikacja nie emituje sensownych danych. Narzędzie może przechowywać i wizualizować sygnały, ale nie naprawi złej semantyki logów, nieograniczonej kardynalności metryk ani braku propagacji trace context.

Drugim błędem jest nadmierna wiara w dashboardy. Dashboard jest przydatny wtedy, gdy wspiera decyzję operacyjną. Zbyt wiele wykresów bez jasnego pytania prowadzi do chaosu poznawczego. Lepszy jest mniejszy zestaw wykresów odpowiadających na konkretne pytania: czy użytkownicy cierpią, gdzie problem występuje, jak duży jest wpływ, czy rośnie liczba błędów, czy system jest nasycony i która zależność odpowiada za opóźnienie.

Trzecim błędem jest logowanie zbyt wielu danych bez schematu i bez kontroli bezpieczeństwa. Takie logi są kosztowne, trudne w analizie i mogą tworzyć ryzyko wycieku danych. Dobre logowanie wymaga selekcji. Należy logować to, co pomaga zrozumieć stan systemu i przebieg zdarzenia, a nie wszystko, co jest technicznie dostępne.

Czwartym błędem jest umieszczanie identyfikatorów o wysokiej kardynalności w etykietach metryk. Trace_id, user_id, request_id czy pełny URL nie powinny trafiać do labels w Prometheusie. Takie dane należą do trace’ów lub logów, a korelację z metrykami należy realizować przez exemplars albo inne mechanizmy linkowania.

## Podsumowanie

Observability nie jest kolejną nazwą dla monitoringu. Jest praktyką projektowania systemu i telemetrii tak, aby inżynierowie mogli prowadzić dochodzenia produkcyjne na podstawie dowodów. Jej celem nie jest gromadzenie jak największej ilości danych, lecz tworzenie danych, które pozwalają szybko i wiarygodnie odpowiedzieć na pytania operacyjne.

Dojrzałe podejście łączy kilka elementów. Metryki wykrywają objawy i pokazują skalę problemu. Trace’y lokalizują miejsce, w którym request traci czas. Logi wyjaśniają szczegóły konkretnego zdarzenia. Exemplars łączą agregaty metryczne z pojedynczymi trace’ami. Alerty koncentrują się na wpływie na użytkownika. Runbooki zamieniają diagnozę w powtarzalne działanie.

Najważniejsze kryterium jest praktyczne: zespół powinien umieć przejść od stwierdzenia „system jest wolny” do dowodu pokazującego, że konkretny endpoint, w konkretnym czasie, dla konkretnej wersji usługi, traci czas na konkretnej zależności. Dopiero wtedy observability przestaje być hasłem, a zaczyna być realną zdolnością operacyjną zespołu.
