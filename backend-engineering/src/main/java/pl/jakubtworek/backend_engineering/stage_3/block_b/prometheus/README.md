# Metryki Prometheus w aplikacji Java

## Cel dokumentu

Ten dokument opisuje teoretyczne podstawy projektowania metryk Prometheus dla aplikacji backendowej napisanej w Javie, na przykładzie usługi `checkout-api`. Nie jest to instrukcja „jak dodać licznik w kodzie”, lecz opis sposobu myślenia o metrykach jako o kontrakcie obserwowalności systemu. Klasy Java zaproponowane dla tego konceptu mają sens tylko wtedy, gdy stoją za nimi jasne decyzje: co mierzymy, po co to mierzymy, jakich etykiet używamy, jak unikamy eksplozji kardynalności i jak później interpretujemy dane w PromQL, Grafanie oraz alertingu.

Metryki są jednym z trzech podstawowych sygnałów observability obok logów i trace’ów. Ich rola jest jednak inna. Metryki nie powinny służyć do wyjaśniania każdego pojedynczego requestu. Od tego są trace’y i logi. Metryki mają pokazać stan systemu w czasie, wykryć trend, wskazać objaw użytkownika i pomóc odpowiedzieć na pytania: czy system zwolnił, od kiedy, jak bardzo, na których trasach, przy jakim ruchu i czy problem przekłada się na błędy albo nasycenie zasobów. Dobrze zaprojektowane metryki są więc pierwszą warstwą detekcji, a nie kompletną warstwą diagnostyczną.

## Golden signals jako punkt wyjścia

Najbardziej praktycznym punktem wyjścia dla systemów user-facing są cztery sygnały znane z Google SRE: latency, traffic, errors i saturation. W przypadku `checkout-api` oznacza to, że nie zaczynamy od mierzenia wszystkiego, co jest technicznie możliwe. Zaczynamy od tego, co mówi, czy użytkownik realnie odczuwa problem.

Latency opisuje czas obsługi requestów. W praktyce nie wystarcza średnia, ponieważ średnia potrafi ukryć problemy ogona rozkładu. Dla użytkowników często ważniejsze jest to, że część requestów trwa kilka sekund, niż to, że średnia wygląda akceptowalnie. Dlatego latency należy mierzyć histogramami i analizować przez percentyle, na przykład p95 i p99. Szczególnie istotne jest rozdzielanie latencji requestów zakończonych sukcesem i błędem. Szybkie błędy HTTP 500 mogą sztucznie poprawiać jedną zbiorczą metrykę latency, mimo że system w praktyce jest w złym stanie.

Traffic opisuje wolumen pracy systemu. W API najczęściej będzie to liczba requestów na sekundę, czyli RPS liczony z countera przez `rate()`. Bez kontekstu ruchu sama latencja bywa myląca. Wzrost p99 przy kilkukrotnym wzroście RPS mówi coś innego niż wzrost p99 przy stabilnym ruchu. Traffic pozwala też odróżnić problem wydajnościowy od problemu z popytem, kampanią marketingową, retry stormem albo zmianą zachowania klienta.

Errors opisują niepowodzenia. W HTTP najczęściej będą to kody 5xx, czasem także 429, timeouty, anulowania requestów albo błędy zależności zewnętrznych. Sam fakt, że system jest wolny, nie oznacza jeszcze, że się psuje. Z drugiej strony wzrost błędów bez dużego wzrostu latency może wskazywać na szybkie odrzucanie requestów, circuit breaker, problemy walidacyjne albo awarię downstreamu.

Saturation opisuje stopień nasycenia zasobów. W aplikacji Java i systemie backendowym mogą to być inflight requests, kolejka oczekujących requestów, pending requests w poolu połączeń, użycie CPU, presja GC, zajętość wątków, kolejki komunikatów albo liczba oczekujących połączeń do bazy. Saturation często jest sygnałem ostrzegawczym wcześniejszym niż błędy. System może jeszcze odpowiadać 200, ale jego kolejki i pule mogą być już blisko załamania.

## Prometheus jako model pracy z metrykami

Prometheus wymusza dyscyplinę projektowania metryk, ponieważ każda metryka i każda kombinacja labeli tworzy szereg czasowy. Ta cecha jest potężna, ale niebezpieczna. Jeżeli do labeli trafi `user_id`, `request_id`, `trace_id`, adres e-mail, surowy URL albo pełne zapytanie SQL, liczba szeregów czasowych może urosnąć w sposób niekontrolowany. To nie jest tylko problem estetyczny. Wysoka kardynalność zwiększa koszt przechowywania, obciążenie zapytań, opóźnienia dashboardów, a czasem prowadzi do realnej degradacji całego systemu monitoringu.

Dlatego metryki powinny używać labeli o ograniczonym zbiorze wartości. Dobre przykłady to `service`, `route`, `method`, `status_code`, `status_class`, `operation`, `result`, `db_system`, `provider` i `pool`. Złe przykłady to `user_id`, `email`, `payment_id`, `order_id`, `request_id`, `trace_id`, `raw_path`, `url`, `sql` i `exception_message`. W praktyce oznacza to, że trasa HTTP powinna być zapisywana jako template, na przykład `/orders/:id/pay`, a nie jako konkretna ścieżka `/orders/123/pay`. Tak samo operacja bazy powinna być zapisana jako `SELECT`, `INSERT` albo krótki kontrolowany opis, a nie pełne zapytanie SQL.

Nazwy metryk powinny być jednoznaczne, stabilne i zawierać jednostkę, jeśli metryka ma jednostkę. Dobre nazwy to `checkout_http_requests_total`, `checkout_http_request_duration_seconds`, `checkout_http_inflight_requests`, `checkout_cache_requests_total` albo `checkout_payment_provider_duration_seconds`. Sama nazwa `latency` albo `requests` jest zbyt niejednoznaczna, zwłaszcza gdy metryki są konsumowane poza wygodnym dashboardem, na przykład w alertach, regułach PromQL, skryptach lub eksportach.

## Typy metryk i ich znaczenie

Counter jest metryką monotoniczną. Rośnie od startu procesu i nie powinien maleć, poza restartem procesu. Nadaje się do zliczania requestów, błędów, timeoutów, retry, cache hitów i cache missów. Counterów nie interpretuje się bezpośrednio przez ich aktualną wartość, bo sama liczba od startu procesu zwykle niewiele mówi. Najczęściej używa się `rate()` do określenia tempa zmian lub `increase()` do policzenia liczby zdarzeń w oknie czasu.

Gauge reprezentuje wartość chwilową, która może rosnąć i maleć. Nadaje się do inflight requests, pending requests w poolu połączeń, queue depth, liczby aktywnych workerów albo aktualnej wielkości kolejki. Gauge jest dobry do obserwowania saturacji, ale trzeba uważać, by nie traktować każdej wartości chwilowej jako bezpośredniej przyczyny problemu. Wysoki gauge jest często punktem startowym dochodzenia, a nie gotową diagnozą.

Histogram służy do opisu rozkładu wartości, najczęściej czasu trwania operacji. To podstawowy typ metryki dla latency. W Prometheusie histogramy pozwalają liczyć percentyle przez `histogram_quantile()`, agregować dane ponad instancjami i analizować tail latency. W aplikacjach Java, szczególnie przy użyciu Micrometera, histogramy często są tworzone przez `Timer`, o ile włączono publikowanie histogramów percentylowych albo skonfigurowano odpowiednie bucket boundaries. Istotne jest to, że p95 i p99 powinny wynikać z histogramów, a nie ze średnich ani z lokalnych, nieagregowalnych wyliczeń po stronie procesu.

Summary bywa kuszące, ale w kontekście Prometheusa i systemów rozproszonych jest mniej wygodne do agregacji niż histogram. Jeśli trzeba agregować latency ponad wieloma instancjami, regionami lub klastrami, histogram jest zwykle bezpieczniejszym wyborem. Dodatkowo coraz większe znaczenie mają native histograms, które mogą upraszczać część problemów klasycznych histogramów, ale ich użycie zależy od wersji Prometheusa, klienta, biblioteki i ścieżki eksportu.

## Sens proponowanych klas Java

Zaproponowane klasy Java nie są tylko opakowaniem na wywołania Micrometera. Ich główną rolą jest utrwalenie kontraktu telemetrycznego w kodzie aplikacji. Klasa `MetricNames` centralizuje nazwy metryk, dzięki czemu zespół nie tworzy przypadkowo kilku wariantów tej samej metryki. Klasa `MetricLabels` centralizuje dozwolone nazwy labeli, co zmniejsza ryzyko niespójności i ułatwia review kodu. Enumy takie jak `StatusClass` i `CacheResult` ograniczają zbiór wartości labeli, a więc chronią przed przypadkową eksplozją kardynalności.

`RouteTemplate` jest celowo osobnym typem, ponieważ surowa ścieżka HTTP jest jednym z najczęstszych źródeł błędów w metrykach. Programista powinien świadomie przekazać template trasy, a nie dowolny string. To ma znaczenie nie tylko dla Prometheusa, ale też dla dashboardów, alertów i porównywania danych między deployami. Jeśli raz używamy `/orders/:id/pay`, raz `/orders/{id}/pay`, a raz surowej ścieżki, tracimy spójność obserwacji.

`MetricCardinalityGuard` jest obroną drugiej linii. Nie zastąpi kultury inżynierskiej ani review, ale może wyłapać najbardziej oczywiste błędy, takie jak próba dodania `trace_id`, `request_id`, `email` albo `sql` jako labela. Taki guard jest szczególnie przydatny w większych zespołach, gdzie metryki dodają różne osoby o różnym doświadczeniu operacyjnym.

Recordery, takie jak `HttpMetricsRecorder`, `CacheMetricsRecorder`, `DatabaseMetricsRecorder` i `PaymentProviderMetricsRecorder`, rozdzielają domeny obserwacji. HTTP recorder mierzy objaw użytkownika. Cache recorder odpowiada na pytanie, czy cache pomaga, nie trafia lub generuje błędy. Database recorder pokazuje koszt zależności bazodanowych i problemy z pulą połączeń. Payment provider recorder pozwala odróżnić problem własnej aplikacji od problemu zewnętrznego dostawcy płatności. To rozdzielenie jest ważne, bo dobra diagnostyka wymaga przejścia od objawu do zależności, a nie jednej płaskiej listy metryk.

## HTTP metrics jako warstwa objawu użytkownika

Najważniejszą metryką HTTP jest liczba requestów, najczęściej jako `checkout_http_requests_total`. Z niej można wyliczyć RPS oraz error rate. Label `route` powinien zawierać template trasy, `method` metodę HTTP, a `status_code` kod odpowiedzi. Dla wewnętrznych API można używać pełnego status code, ponieważ zbiór wartości jest ograniczony. Dla zewnętrznych zależności często lepszy jest `status_class`, na przykład `2xx`, `4xx`, `5xx`, ponieważ ogranicza kardynalność i upraszcza analizę.

Drugą kluczową metryką HTTP jest `checkout_http_request_duration_seconds`, najlepiej jako histogram. To ona pozwala policzyć p50, p95 i p99 dla endpointów. W praktyce warto analizować latency sukcesów i błędów osobno. Dla użytkownika wolne sukcesy są problemem doświadczenia, ale wolne błędy są często jeszcze gorszym sygnałem operacyjnym, bo oznaczają, że system długo zużywa zasoby, a i tak nie dostarcza wyniku.

Trzecią ważną metryką jest `checkout_http_inflight_requests`. To gauge pokazujący liczbę requestów aktualnie obsługiwanych przez aplikację. Jeżeli latency rośnie razem z inflight requests, system może być przeciążony albo czekać na downstream. Jeżeli inflight requests rośnie, a RPS nie rośnie proporcjonalnie, może to oznaczać kumulację wolnych requestów, blokadę wątków, wolną bazę, wyczerpanie puli połączeń albo problem z zewnętrznym API.

## Cache metrics jako kontrola efektów ubocznych

Cache bywa traktowany jako oczywiste przyspieszenie, ale operacyjnie może też stać się źródłem awarii. Dlatego `checkout_cache_requests_total` powinno rozróżniać co najmniej operację i wynik: `hit`, `miss`, `error`, `timeout`. Cache hit ratio jest użyteczne, ale samo w sobie nie wystarcza. Spadek hit ratio może zwiększyć obciążenie bazy. Wzrost timeoutów Redis może spowodować kaskadowe opóźnienia. Cache, który formalnie działa, ale odpowiada wolno, może być gorszy niż szybki miss z dobrze zabezpieczoną ścieżką fallback.

W labelach cache nie powinno być kluczy cache ani identyfikatorów encji. Klucz cache jest zwykle wysokokardynalny i często zawiera dane biznesowe. Do metryk wystarczy operacja i wynik. Szczegóły pojedynczego przypadku powinny trafić do trace’a albo logu strukturalnego, nie do Prometheusa.

## DB metrics i rozróżnienie wolnej operacji od zatkanej puli

Metryki bazodanowe powinny rozdzielać czas wykonania operacji od problemów z uzyskaniem połączenia. `checkout_db_client_operation_duration_seconds` mierzy czas operacji na bazie lub kliencie DB. Labelami mogą być `db_system`, na przykład `postgresql` lub `redis`, oraz `operation`, na przykład `SELECT`, `INSERT`, `UPDATE`, `GET` albo `SET`. Nie należy używać pełnego SQL jako labela. Pełny SQL może zawierać dane wrażliwe, a dodatkowo niemal zawsze prowadzi do wysokiej kardynalności.

Osobno warto mierzyć `db_client_connection_pending_requests` oraz `db_client_connection_timeouts_total`. To są metryki szczególnie ważne przy diagnozie saturacji. Jeżeli p95 HTTP rośnie, a pending requests w poolu również rosną, problemem może nie być sama baza, lecz wyczerpanie puli połączeń albo blokowanie po stronie klienta. Jeżeli rosną timeouty zdobycia połączenia, aplikacja może nawet nie dochodzić do wykonania SQL. Bez tych metryk zespół łatwo wpada w uproszczoną diagnozę „baza muli”, choć rzeczywisty problem jest w konfiguracji poola, liczbie workerów, transakcjach albo zmianie profilu ruchu.

## Metryki zewnętrznych providerów

Dla `checkout-api` zewnętrzny provider płatności jest krytyczną zależnością. Metryki takie jak `checkout_payment_provider_requests_total` i `checkout_payment_provider_duration_seconds` pozwalają oddzielić degradację własnej usługi od degradacji upstreamu. W labelach powinien pojawić się `provider` oraz `status_class`, ale nie `payment_id`, `customer_id`, numer karty, token płatniczy ani komunikat błędu dostawcy.

W praktyce to rozdzielenie ma znaczenie incydentowe. Jeżeli p99 endpointu `/orders/:id/pay` rośnie, a jednocześnie rośnie p99 `checkout_payment_provider_duration_seconds` dla konkretnego providera, pierwsza hipoteza jest inna niż wtedy, gdy provider działa stabilnie, ale rosną pending requests w poolu bazy. Metryki nie dają pełnego dowodu przyczynowego, ale bardzo szybko zawężają obszar dochodzenia.

## PromQL jako język interpretacji

Metryki są użyteczne dopiero wtedy, gdy istnieją dobre zapytania. Countery najczęściej analizuje się przez `rate()` w oknie czasu, na przykład pięciu minut. `increase()` jest przydatne, gdy chcemy policzyć liczbę zdarzeń w konkretnym oknie, na przykład ile timeoutów połączenia wystąpiło przez ostatnie trzydzieści minut. Histogramy analizuje się przez `histogram_quantile()`, pamiętając, że dla klasycznych histogramów podczas agregacji trzeba zachować label `le`.

Dla error rate poprawny wzorzec polega na osobnym zagregowaniu licznika i mianownika, a dopiero potem wykonaniu dzielenia. Nie należy liczyć średniej z lokalnych ratio, bo instancje o małym ruchu mogą nieproporcjonalnie wpłynąć na wynik. To samo dotyczy wielu metryk proporcji, takich jak cache hit ratio. Najpierw sumujemy hit count, osobno sumujemy całkowity request count, a dopiero potem dzielimy.

PromQL powinien być traktowany jak część kodu operacyjnego systemu. Zapytania używane w dashboardach, alertach i runbookach muszą być spójne. Jeśli dashboard liczy p95 inaczej niż alert, a runbook używa jeszcze trzeciej wersji zapytania, zespół w trakcie incydentu traci czas na uzgadnianie, która liczba jest prawdziwa.

## Recording rules jako warstwa stabilizacji

Recording rules służą do precomputingu kosztownych lub często używanych zapytań. W praktyce są przydatne dla RPS, error ratio, p95, p99 i agregacji ponad instancjami lub podami. Dobrze nazwane recording rules tworzą stabilną warstwę pośrednią między surowymi metrykami a dashboardami oraz alertami.

Warto traktować recording rules jako część publicznego interfejsu observability. Jeżeli wiele dashboardów używa tej samej reguły, zmiana implementacji zapytania może być wykonana w jednym miejscu. Jeżeli każdy dashboard zawiera własne długie PromQL, utrzymanie staje się trudniejsze, a wyniki mogą się rozjechać. Dobre nazewnictwo reguł powinno komunikować poziom agregacji, nazwę metryki i operację, na przykład `route:checkout_http_requests:rate5m` albo `route:checkout_http_request_duration_seconds:p95_rate5m`.

## Exemplars i granica między metrykami a trace’ami

Jednym z najczęstszych błędów projektowych jest próba włożenia `trace_id` do labeli Prometheusa. Intencja jest zrozumiała: chcemy kliknąć z metryki do konkretnego requestu. Technicznie jest to jednak zły kierunek, ponieważ `trace_id` ma bardzo wysoką kardynalność. Właściwym mechanizmem są exemplars.

Exemplar łączy punkt metryczny, najczęściej z histogramu, z kontekstem konkretnego trace’a. Dzięki temu na wykresie latency można przejść od piku p99 do przykładowego trace’a, który reprezentuje realny wolny request. To daje najlepsze z obu światów: metryki pozostają agregatami o kontrolowanej kardynalności, a trace pozwala zejść do pojedynczej ścieżki wykonania. W Grafanie jest to bardzo naturalny model pracy: najpierw widzimy trend metryczny, potem exemplar, potem trace, a z trace’a przechodzimy do logów.

Exemplars nie zwalniają z obowiązku dobrego projektowania metryk. Jeżeli histogram jest źle nazwany, ma złe labelki albo miesza różne klasy operacji, exemplar pokaże pojedynczy przypadek, ale nie naprawi błędnej agregacji. Najpierw trzeba mieć poprawne metryki, potem można budować wygodną nawigację między metrykami, trace’ami i logami.

## Alerting oparty na objawach

Metryki Prometheus są często podstawą alertingu, ale nie każda metryka powinna generować page. Alerty dla ludzi powinny być symptom-based i actionable. Dla `checkout-api` dobry alert to na przykład wzrost p95 lub p99 dla krytycznej trasy, wysoki error rate 5xx, długotrwały wzrost pending requests w poolu albo poważna degradacja providera płatności. Słaby alert to pojedynczy czerwony komponent bez wpływu na użytkownika i bez jasnego działania.

Alert powinien zawierać routing labels, opis, próg, okno czasu i link do runbooka. Sama informacja „latency high” jest za słaba. Lepszy alert mówi, dla jakiej usługi i trasy p95 przekroczył próg, przez ile czasu, przy jakim error rate oraz gdzie zacząć diagnostykę. W praktyce alert i dashboard powinny używać tych samych recording rules, żeby osoba reagująca na incydent widziała spójny obraz.

## Done criterion dla metryk

Metryki dla `checkout-api` można uznać za sensownie zaprojektowane dopiero wtedy, gdy odpowiadają na konkretne pytania operacyjne. Czy wiemy, jaki jest RPS per route? Czy umiemy policzyć error rate 5xx per route? Czy mamy p95 i p99 liczone z histogramów, a nie ze średnich? Czy umiemy odróżnić sukcesy od błędów? Czy widzimy inflight requests i pending requests? Czy umiemy sprawdzić cache hit ratio i timeouty cache? Czy mamy osobne metryki dla PostgreSQL, Redis i providera płatności? Czy labels są ograniczone i nie zawierają identyfikatorów użytkowników, requestów ani trace’ów? Czy zapytania PromQL są spójne między dashboardami, alertami i runbookami? Czy drogie zapytania są przeniesione do recording rules? Czy exemplars pozwalają przejść z piku latency do trace’a?

Jeżeli odpowiedź na większość tych pytań brzmi „nie”, system prawdopodobnie ma jakieś metryki, ale nie ma jeszcze metryk gotowych do obsługi incydentu. To ważne rozróżnienie. Zbieranie danych nie jest tym samym co observability. Observability zaczyna się wtedy, gdy zespół potrafi na podstawie metryk szybko sformułować hipotezę, zawęzić obszar problemu i przejść do trace’ów oraz logów po dowód na poziomie konkretnego requestu.

## Podsumowanie

Metryki Prometheus w aplikacji Java powinny być projektowane jako stabilny kontrakt operacyjny, a nie jako przypadkowe liczniki dodawane przy okazji implementacji funkcji. Dobre metryki zaczynają się od golden signals: latency, traffic, errors i saturation. Następnie wymagają świadomego doboru typów metryk: counterów dla zdarzeń, gauge’y dla wartości chwilowych i histogramów dla rozkładów. Kluczowa jest dyscyplina labeli, ponieważ niekontrolowana kardynalność potrafi zniszczyć użyteczność Prometheusa.

W `checkout-api` metryki powinny najpierw pokazywać objaw użytkownika na warstwie HTTP, a potem pozwalać zawęzić problem do cache, bazy danych, puli połączeń albo zewnętrznego providera płatności. PromQL, recording rules i exemplars tworzą nad tym warstwę interpretacji. PromQL pozwala liczyć RPS, error ratio i percentyle. Recording rules stabilizują i przyspieszają najważniejsze zapytania. Exemplars łączą agregaty metryczne z konkretnymi trace’ami bez niszczenia kardynalności.

Najkrótsza praktyczna zasada brzmi: metryki mają powiedzieć, że użytkownik cierpi, gdzie cierpi i jak bardzo; trace’y oraz logi mają wyjaśnić, dlaczego. Jeżeli metryki próbują przejąć rolę trace’ów przez labelowanie `trace_id` albo `request_id`, projekt zaczyna iść w złą stronę. Jeśli jednak metryki pozostają agregatami o kontrolowanej kardynalności, a exemplars prowadzą do trace’ów, system observability staje się spójny i użyteczny podczas realnych incydentów.
