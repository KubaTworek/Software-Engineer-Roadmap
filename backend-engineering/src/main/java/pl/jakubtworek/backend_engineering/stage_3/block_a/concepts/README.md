# System Design: skalowanie i odporność

## Cel dokumentu

Ten dokument opisuje teoretyczne podstawy skalowania i odporności systemów rozproszonych na przykładzie zestawu klas Java przygotowanych dla takich pojęć jak modelowanie pojemności, autoscaling, cache, Redis, rate limiting, circuit breaker, retry, timeouty, przełączniki operacyjne oraz scenariusze testów obciążeniowych. Kod nie powinien być traktowany jako gotowy framework produkcyjny. Jego wartość polega raczej na tym, że porządkuje najważniejsze mechanizmy system designu w postaci prostych, nazwanych abstrakcji.

W projektowaniu skalowalnych systemów najważniejsze nie jest samo rozpoznawanie popularnych wzorców architektonicznych. Znacznie ważniejsza jest umiejętność przewidzenia, który komponent jako pierwszy osiągnie limit, dlaczego właśnie on stanie się bottleneckiem oraz jak potwierdzić tę hipotezę metrykami i testem obciążeniowym. W praktyce oznacza to przejście od ogólnych haseł typu „dodaj cache”, „skaluj horyzontalnie” albo „użyj retry” do mierzalnego modelu: jaka jest przepustowość, jaka jest latencja, ile mamy równoległości, gdzie znajduje się limit połączeń, jaki jest koszt pojedynczego requestu i jak system zachowa się po przekroczeniu bezpiecznego zakresu pracy.

Przygotowane klasy Java są więc sposobem opakowania teorii w pojęcia, które można łatwo nazwać i omówić. `CapacityModel` reprezentuje podstawowe wzory pojemnościowe. `CapacityReport` pokazuje, jak opisać limit konkretnego komponentu. `AutoscalingConfig` rozdziela minimalną i maksymalną liczbę replik oraz metrykę skalowania. Klasy związane z cache pokazują ideę cache-aside, TTL, eviction policy i ochrony przed cache stampede. Rate limitery pokazują różnicę między token bucket a sliding window. Circuit breaker, retry policy i timeout budget reprezentują podstawowy zestaw mechanizmów odpornościowych dla zależności zdalnych. Operational levers opisują natomiast dźwignie awaryjne, które pozwalają szybko degradować system bez wdrażania nowego kodu.

## Myślenie pojemnościowe

Punktem wyjścia dla skalowania jest model pojemności. System nie staje się skalowalny dlatego, że działa w Kubernetesie, ma load balancer albo używa Redisa. System jest skalowalny wtedy, gdy jego główne ograniczenia są znane, mierzalne i możliwe do przesuwania bez pełnego przepisania architektury. Dlatego pierwszym pytaniem nie powinno być „jakiego narzędzia użyć”, lecz „który zasób zostanie wyczerpany jako pierwszy”.

Najprostszy model zaczyna się od prawa Little’a, które w kontekście usług sieciowych można zapisać jako zależność między przepustowością, latencją i równoległością. Jeżeli system obsługuje określoną liczbę requestów na sekundę, a każdy request trwa średnio określony czas, to w każdej chwili w systemie musi znajdować się pewna liczba requestów równoległych. To pozwala szybko oszacować, czy problemem będzie CPU, pula połączeń, kolejka, liczba workerów czy limit zależności zewnętrznej.

W klasie `CapacityModel` ta idea jest reprezentowana przez metody liczące concurrency, throughput ograniczony przez CPU, throughput ograniczony przez pulę połączeń oraz liczbę wymaganych replik. Takie wzory są uproszczeniem, ale właśnie o to chodzi na pierwszym etapie projektowania. Model ma wskazać prawdopodobny bottleneck i rząd wielkości limitu. Dopiero później model trzeba zweryfikować testem typu step test, obserwując, czy p95 i p99 latency rosną zgodnie z przewidywaniami.

W praktyce każdy krytyczny komponent powinien dać się opisać przez kilka pytań. Jaki jest wzór pojemności dla tego komponentu? Jaki parametr trzeba zmierzyć, żeby wzór miał sens? Jaki jest aktualny limit? Która metryka potwierdzi, że komponent faktycznie jest nasycony? `CapacityReport` jest prostą reprezentacją takiego myślenia. Nie chodzi o formalizm sam dla siebie, ale o wymuszenie dyscypliny: zamiast mówić „baza może być problemem”, trzeba powiedzieć „baza stanie się problemem przy około X RPS, ponieważ pula połączeń ma rozmiar Y, średnia latencja zapytania wynosi Z, a metryką potwierdzającą będzie rosnący pool wait oraz p95 zapytań”.

## Skalowanie pionowe i poziome

Skalowanie pionowe, czyli zwiększanie zasobów pojedynczej maszyny lub instancji, jest najprostszą formą kupowania czasu. Ma sens wtedy, gdy system jest jeszcze mały, komponent trudno podzielić, koszt refaktoryzacji jest większy niż koszt mocniejszej maszyny, a głównym celem jest szybka poprawa pojemności. Skalowanie pionowe jest często najbardziej racjonalnym pierwszym krokiem, zwłaszcza gdy problem dotyczy jednej bazy danych, jednego procesu albo jednego serwera aplikacyjnego. Nie należy go traktować jako porażki architektonicznej. Jest to po prostu prosty trade-off: mniej złożoności operacyjnej w zamian za twardy limit pojedynczej maszyny.

Skalowanie poziome polega na dodawaniu kolejnych replik usługi. Daje większą elastyczność, lepszą odporność na awarie pojedynczych instancji i umożliwia stopniowe zwiększanie capacity. W zamian wymaga jednak, aby aplikacja była możliwie stateless. Jeżeli request może trafić do dowolnej repliki, to replika nie może przechowywać krytycznego stanu wyłącznie w pamięci lokalnej. Sesje, pliki, kolejki i dane biznesowe muszą zostać wyniesione do backing services, takich jak baza danych, cache, object storage albo system kolejkowy.

W klasach Java ta różnica pojawia się pośrednio w `AutoscalingConfig` i `ScalingMetric`. Sama liczba replik nie wystarczy. Trzeba jeszcze wiedzieć, według jakiej metryki system ma się skalować. Dla workloadów CPU-bound skalowanie po CPU może być wystarczające. Dla workloadów IO-bound często jest mylące, ponieważ proces może mieć niskie zużycie CPU, a jednocześnie cierpieć na przeciążoną bazę danych, długi czas oczekiwania na pulę połączeń albo wysoką latencję zależności zewnętrznej. Dlatego lepszymi sygnałami bywają concurrency, in-flight requests, queue depth, dependency p95 latency albo pool wait.

Ważnym elementem autoscalingu są limity minimalne i maksymalne. Minimalna liczba replik zmniejsza wpływ cold startu i pomaga utrzymać SLO przy niskim, ale wrażliwym ruchu. Maksymalna liczba replik jest bezpiecznikiem. Bez niej autoscaler może próbować ratować przeciążoną aplikację przez tworzenie coraz większej liczby instancji, które następnie przeciążą bazę danych, Redisa albo zewnętrzne API. Dobry autoscaling nie polega więc na bezwarunkowym dodawaniu replik, lecz na kontrolowanym zwiększaniu capacity w granicach, które są bezpieczne dla całego systemu.

## Stateless API jako warunek skalowania

Stateless API oznacza, że pojedyncza replika aplikacji jest wymienna z punktu widzenia requestu. Jeżeli jedna instancja przestanie działać, request może zostać obsłużony przez inną bez utraty krytycznego stanu. W praktyce oznacza to brak sesji trzymanych wyłącznie w RAM procesu, brak istotnych plików lokalnych, brak założenia, że użytkownik zawsze wróci do tej samej repliki, oraz brak krytycznych zadań wykonywanych tylko przez konkretną instancję bez mechanizmu przejęcia pracy.

Stateless design nie oznacza, że system nie ma stanu. Oznacza, że stan jest przeniesiony do jawnych zależności: bazy danych, cache, object storage, systemu kolejkowego albo zewnętrznego identity providera. To właśnie te zależności stają się później głównymi kandydatami na bottlenecki. Dlatego skalowanie API bez analizy backing services jest niepełne. Można dodać więcej replik aplikacji, ale jeżeli każda replika otwiera wiele połączeń do tej samej bazy, to prawdziwy limit zostanie osiągnięty w warstwie danych.

W praktycznym projekcie stateless API powinno być połączone z kontrolą concurrency. Nie wystarczy pozwolić każdej replice obsługiwać dowolną liczbę requestów. Trzeba ograniczać liczbę równoległych operacji do zależności, stosować timeouty, dbać o rozmiary pul i monitorować kolejki oczekiwania. W przeciwnym razie skalowanie poziome może zwiększyć nie tylko capacity, ale również siłę, z jaką aplikacja przeciąża własne zależności.

## Cache jako narzędzie redukcji kosztu odczytu

Cache jest jednym z najczęściej używanych narzędzi skalowania, ale bywa też jednym z najczęściej nadużywanych. Jego podstawowa rola polega na zmniejszeniu latencji, kosztu i obciążenia źródła prawdy dla danych, które są często czytane, relatywnie rzadko zmieniane albo drogie do obliczenia. Cache nie powinien być dodawany automatycznie. Najpierw trzeba zrozumieć, jaki problem rozwiązuje: czy zmniejsza liczbę zapytań do bazy, czy redukuje p95 latency, czy chroni zewnętrzne API, czy pozwala przetrwać częściową awarię zależności.

Najprostszym i często domyślnym wzorcem jest cache-aside. Aplikacja najpierw próbuje odczytać dane z cache. Jeżeli nastąpi miss, pobiera dane ze źródła prawdy, zapisuje je do cache i zwraca odpowiedź. Przy zapisie do źródła prawdy trzeba natomiast podjąć decyzję, czy cache ma zostać unieważniony, odświeżony czy pozostawiony do wygaśnięcia przez TTL. Klasa `CacheAsideService` pokazuje tę logikę w wersji koncepcyjnej.

TTL jest jednym z najważniejszych parametrów cache. Zbyt krótki TTL powoduje dużą liczbę missów i mniejszą korzyść z cache. Zbyt długi TTL zwiększa ryzyko serwowania nieaktualnych danych. Dobór TTL powinien zależeć od wymagań świeżości oraz kosztu missa. Dane konfiguracyjne, listy referencyjne albo dane publiczne mogą mieć dłuższy TTL. Dane użytkownika, stany zamówień albo ceny wymagają ostrożniejszego podejścia, bo koszt niespójności może być większy niż zysk z cache.

Eviction policy opisuje, które klucze zostaną usunięte, gdy cache osiągnie limit pamięci. `ALL_KEYS_LRU` jest rozsądnym domyślnym wyborem dla klasycznego cache, w którym niewielki zbiór gorących danych generuje większość ruchu. `ALL_KEYS_LFU` może być lepszy, gdy hot keys są stabilne i często używane przez dłuższy czas. `VOLATILE_TTL` ma sens tylko wtedy, gdy konsekwentnie ustawiane są TTL-e. `NO_EVICTION` chroni przed utratą danych, ale w klasycznym cache może prowadzić do błędów zapisu, gdy pamięć się skończy. Dlatego wybór eviction policy powinien wynikać z semantyki cache, a nie z przypadkowej konfiguracji.

## Redis i planowanie pamięci

Redis często pełni rolę cache, ale jego zachowanie pod presją pamięci zależy od konfiguracji. Samo uruchomienie Redisa nie wystarczy. Trzeba świadomie ustawić `maxmemory`, dobrać eviction policy oraz zostawić zapas na pamięć używaną przez system operacyjny, replikację i persistence. Klasa `RedisMemoryPlan` pokazuje uproszczony model: dostępna pamięć powinna zostać pomniejszona o pamięć nieuwzględnianą w mechanizmie eviction oraz o rezerwę systemową.

Największym błędem jest traktowanie limitu pamięci cache jako wartości czysto technicznej. W rzeczywistości jest to decyzja architektoniczna. Jeżeli cache jest zbyt mały, hit ratio będzie niskie i źródło prawdy pozostanie przeciążone. Jeżeli cache jest zbyt duży, koszt infrastruktury wzrośnie, a przy złej konfiguracji można doprowadzić do presji pamięciowej lub niestabilności. Właściwy rozmiar cache powinien wynikać z obserwacji working setu, hit ratio, liczby evictions, latency Redisa i wpływu cache missów na bazę danych.

Najważniejsze metryki cache to liczba trafień, liczba missów, hit ratio, liczba evictions, liczba expired keys, zużycie pamięci, latency oraz liczba połączeń. Samo wysokie hit ratio nie zawsze oznacza sukces. Jeżeli missy dotyczą najdroższych zapytań albo hot keys wygasają jednocześnie, system nadal może być podatny na przeciążenie. Dlatego cache trzeba analizować nie tylko globalnie, ale również z perspektywy najbardziej kosztownych ścieżek requestów.

## Cache stampede

Cache stampede występuje wtedy, gdy popularny klucz wygasa, a wiele równoległych requestów jednocześnie trafia w miss i próbuje odświeżyć dane ze źródła prawdy. Problem jest groźny, ponieważ cache, który miał chronić bazę lub zewnętrzne API, nagle przestaje pełnić tę funkcję dokładnie w momencie największego zapotrzebowania. Efektem może być skok latency, przeciążenie bazy, lawina retry i kaskadowa degradacja systemu.

Jednym z mechanizmów obronnych jest single-flight, czyli dopuszczenie tylko jednego odświeżenia dla danego klucza w danym momencie. Pozostałe requesty czekają na wynik pierwszego odświeżenia albo otrzymują poprzednią wartość. W `CacheAsideService` znajduje się prosta lokalna wersja tej idei. W systemie rozproszonym lokalny lock per JVM nie wystarczy, ale pokazuje zasadę. W produkcji można używać request coalescingu, rozproszonych blokad, koordynacji w cache albo mechanizmów na poziomie CDN lub gatewaya.

Drugim mechanizmem jest stale-while-revalidate. Zamiast blokować użytkownika na czas odświeżenia, system może zwrócić ostatnią znaną wartość, nawet jeżeli formalnie jest już nieświeża, a odświeżenie wykonać w tle. To rozwiązanie jest szczególnie użyteczne dla danych, które nie muszą być absolutnie świeże przy każdym odczycie. Trzecim mechanizmem jest TTL jitter, czyli losowe rozproszenie czasu wygaśnięcia kluczy. Dzięki temu tysiące podobnych kluczy nie wygasają w tej samej sekundzie.

## Rate limiting

Rate limiting służy do kontrolowania liczby requestów wykonywanych przez określoną tożsamość w określonym czasie. Tą tożsamością może być adres IP, API key, użytkownik, tenant, klient B2B albo inny identyfikator biznesowy. Rate limiting nie jest wyłącznie mechanizmem bezpieczeństwa. Jest również narzędziem ochrony pojemności systemu, egzekwowania fair use i ograniczania blast radius w przypadku błędnie działającego klienta.

Limit per IP jest przydatny na brzegu systemu, zwłaszcza dla publicznych endpointów, logowania, ochrony przed brute force albo prostym scrapingiem. Ma jednak poważne ograniczenie: wiele rzeczywistych użytkowników może znajdować się za tym samym NAT-em, proxy albo siecią firmową. Wtedy limit per IP może fałszywie karać wielu użytkowników naraz. Limit per API key, user lub tenant jest zwykle lepszy dla systemów biznesowych, bo odpowiada faktycznemu klientowi lub planowi taryfowemu.

`TokenBucketRateLimiter` reprezentuje token bucket. W tym algorytmie bucket ma określoną pojemność, a tokeny są uzupełniane z określoną szybkością. Request może przejść, jeżeli dostępny jest co najmniej jeden token. Ten algorytm dobrze obsługuje krótkie bursty, zachowując średni limit w dłuższym czasie. `SlidingWindowRateLimiter` reprezentuje sliding window, w którym liczona jest liczba requestów w ostatnim przesuwającym się przedziale czasu. Sliding window ogranicza problem nagłych skoków na granicy okna, ale wymaga więcej stanu niż prosty fixed window.

Gdy request zostanie odrzucony przez limiter, API powinno zwrócić informację, że klient przekroczył limit. W praktyce oznacza to zwykle HTTP 429 oraz opcjonalny nagłówek `Retry-After`. Klasa `RateLimitDecision` modeluje tę decyzję przez pole `allowed` i czas, po którym klient może spróbować ponownie. To rozdzielenie jest istotne, bo limiter nie powinien sam znać szczegółów HTTP. Powinien tylko podjąć decyzję domenową, a warstwa transportowa powinna przetłumaczyć ją na odpowiedź protokołu.

## Timeouty

Timeout jest jednym z najprostszych i najważniejszych mechanizmów odpornościowych. Bez timeoutu request do zależności zewnętrznej może wisieć zbyt długo, zajmując wątek, połączenie, slot w puli albo pamięć. W skrajnym przypadku brak timeoutów powoduje, że awaria jednej zależności zamienia się w awarię całej aplikacji. Dlatego każde zdalne wywołanie powinno mieć jawny connection timeout, request timeout oraz budżet czasu dla ewentualnych retry.

`TimeoutBudget` reprezentuje tę ideę. Connection timeout ogranicza czas zestawiania połączenia. Request timeout ogranicza czas oczekiwania na odpowiedź. Total retry budget ogranicza łączny czas, jaki operacja może spędzić na ponawianiu prób. Bez takiego budżetu retry mogą wydłużyć request ponad sensowne granice, pogarszając doświadczenie użytkownika i zwiększając obciążenie systemu.

Timeouty powinny być dobierane świadomie względem SLO. Jeżeli endpoint ma odpowiadać w 300 ms, to zależność zewnętrzna nie może mieć domyślnego timeoutu 30 sekund. Timeout powinien być krótszy niż maksymalny akceptowalny czas odpowiedzi i powinien zostawiać miejsce na fallback albo kontrolowaną degradację. Jednocześnie zbyt agresywne timeouty mogą sztucznie zwiększać liczbę błędów, zwłaszcza przy zależnościach o naturalnie zmiennej latencji. Dlatego timeouty trzeba stroić na podstawie percentyli, a nie tylko średniej.

## Retry, backoff i jitter

Retry jest użyteczne tylko wtedy, gdy błąd jest przejściowy i operacja może zostać bezpiecznie powtórzona. Jeżeli operacja ma efekt uboczny, na przykład tworzy płatność, wysyła wiadomość albo składa zamówienie, retry bez idempotency key może doprowadzić do duplikacji. Dlatego reguła jest prosta: retry dla operacji idempotentnych albo dla operacji zabezpieczonych tokenem idempotencyjnym.

`RetryPolicy` pokazuje retry z exponential backoff i jitter. Exponential backoff zwiększa odstęp między kolejnymi próbami, dzięki czemu system nie uderza natychmiast ponownie w przeciążoną zależność. Jitter dodaje losowość, aby wiele klientów nie ponawiało requestów dokładnie w tym samym momencie. Bez jittera retry mogą zsynchronizować się i utworzyć kolejną falę obciążenia.

Największym ryzykiem retry jest retry storm. Jeżeli wiele warstw systemu ma własne retry, pojedynczy request użytkownika może zostać zwielokrotniony wielokrotnie. Gateway ponawia request do API, API ponawia request do serwisu, serwis ponawia request do bazy lub zewnętrznego API. W efekcie chwilowa awaria zamienia się w lawinę dodatkowego ruchu. Dlatego retry powinny być ograniczone liczbą prób, budżetem czasu, typem błędu oraz miejscem w architekturze. Zwykle lepiej mieć retry w jednej świadomej warstwie niż przypadkowo w każdej.

## Circuit breaker

Circuit breaker chroni system przed ciągłym wywoływaniem zależności, która prawdopodobnie jest niedostępna lub przeciążona. W stanie closed requesty przechodzą normalnie. Po przekroczeniu progu błędów breaker przechodzi do stanu open i zaczyna fail-fast, czyli odrzuca wywołania bez próby kontaktu z zależnością. Po określonym czasie przechodzi do half-open i dopuszcza ograniczoną liczbę prób testowych. Jeżeli próba się powiedzie, breaker wraca do closed. Jeżeli się nie powiedzie, wraca do open.

`CircuitBreaker` implementuje tę ideę w uproszczonej formie. Nie jest to produkcyjna implementacja z oknami statystycznymi i pełnym monitoringiem, ale dobrze pokazuje sens wzorca. Najważniejsza korzyść circuit breakera polega na ograniczeniu kaskadowej awarii. Jeżeli zależność jest chora, aplikacja nie powinna generować nieograniczonego ruchu, który tylko pogarsza jej stan. Powinna szybko przełączyć się w tryb błędu kontrolowanego, fallbacku albo degradacji.

Dobór progów circuit breakera jest trudny i zależy od charakteru ruchu. Zbyt czuły breaker będzie otwierał się przy krótkich, nieszkodliwych zakłóceniach. Zbyt tolerancyjny breaker zareaguje dopiero wtedy, gdy system już jest przeciążony. Dlatego progi powinny bazować na failure rate, timeout rate, minimalnej liczbie próbek i czasie obserwacji. Ważniejsze od konkretnej liczby jest jednak to, aby stan breakera był mierzalny i widoczny w observability. Trzeba wiedzieć, kiedy breaker się otwiera, dla jakiej zależności i z jakiego powodu.

## Graceful degradation i emergency levers

Odporność systemu nie oznacza, że wszystko zawsze działa w pełnym zakresie. Dojrzały system potrafi celowo ograniczyć funkcjonalność, aby zachować najważniejsze ścieżki biznesowe. Graceful degradation polega na tym, że funkcje mniej krytyczne mogą zostać wyłączone, spowolnione lub zastąpione prostszą wersją, gdy zależności są przeciążone albo niedostępne.

`OperationalLever` reprezentuje takie przełączniki operacyjne. Przykładem może być wyłączenie rekomendacji, wyłączenie personalizacji, tryb read-only, ograniczenie kosztownych endpointów, redukcja concurrency do zależności, wyłączenie enrichmentów albo odrzucanie ruchu niskiego priorytetu. Takie mechanizmy powinny być przygotowane przed incydentem. Przełącznik awaryjny, którego nikt nigdy nie testował, jest tylko złudzeniem bezpieczeństwa.

Najważniejsza zasada brzmi: zależności krytyczne i opcjonalne powinny być rozróżnione. Checkout może być krytyczny, rekomendacje zwykle nie. Logowanie może być krytyczne, enrichment profilu użytkownika zwykle nie. Gdy system zaczyna się degradować, powinien chronić najważniejszą funkcję, nawet kosztem ograniczenia mniej ważnych elementów doświadczenia użytkownika.

## Testy obciążeniowe jako weryfikacja teorii

Model pojemności jest hipotezą, a nie dowodem. Dopiero test obciążeniowy pokazuje, czy przewidywania są poprawne. `LoadTestScenario` i `LoadTestType` opisują różne rodzaje testów, które odpowiadają na różne pytania. Baseline test pozwala ustalić normalne zachowanie systemu. Step test pokazuje, przy jakim poziomie ruchu latency zaczyna rosnąć nieliniowo. Spike test sprawdza reakcję na nagły skok ruchu. Soak test szuka problemów narastających w czasie, takich jak wycieki pamięci, backlogi, rosnące kolejki i dryf latencji.

Szczególnie ważne są testy negatywne. Cache-off albo miss-ratio-up pokazuje, czy baza danych przeżyje spadek skuteczności cache. Dependency-failure sprawdza, czy timeouty, circuit breakery i fallbacki rzeczywiście działają. Retry-storm testuje, czy retry nie wzmacniają awarii. Bez takich testów system może wyglądać dobrze w scenariuszu nominalnym, ale załamać się przy pierwszym poważnym zakłóceniu.

Dobry test powinien porównywać obserwacje z modelem. Jeżeli model przewiduje, że pierwsza nasyci się pula połączeń do bazy, to podczas testu powinny rosnąć pool wait, p95 zapytań i liczba requestów oczekujących na połączenie. Jeżeli zamiast tego pierwsze nasyca się CPU aplikacji, model był niepełny. To nie jest porażka. To jest główny sens testu: poprawić rozumienie systemu.

## Observability i metryki

Skalowanie i odporność są niemożliwe bez obserwowalności. Każdy mechanizm opisany w tym dokumencie wymaga metryk. Autoscaling wymaga metryki skalowania i potwierdzenia, że metryka odpowiada realnemu bottleneckowi. Cache wymaga hit ratio, missów, evictions i latency. Rate limiting wymaga liczby odrzuconych requestów według tożsamości i endpointu. Retry wymagają liczby prób, przyczyn ponowień i skuteczności kolejnych prób. Circuit breaker wymaga stanu, liczby przejść między stanami i liczby requestów odrzuconych fail-fast.

Szczególnie ważne są percentyle latencji. Średnia latencja często ukrywa problem, który użytkownicy już odczuwają. p95 i p99 pokazują ogon rozkładu, czyli requesty najwolniejsze i najbardziej problematyczne. W systemach rozproszonych właśnie ogon latencji często decyduje o jakości doświadczenia użytkownika. Dlatego kryterium nasycenia systemu powinno opierać się nie tylko na CPU lub RPS, ale również na zachowaniu p95 i p99.

Metryki powinny być powiązane z decyzjami operacyjnymi. Jeżeli nie wiadomo, jaka akcja wynika z danej metryki, to metryka może być ciekawa, ale niekoniecznie użyteczna. Rosnący pool wait może oznaczać potrzebę ograniczenia concurrency, zwiększenia puli, optymalizacji zapytań albo dodania cache. Rosnące evictions w Redisie mogą oznaczać zbyt mały cache, złą politykę eviction albo zbyt długi TTL. Wysoki retry rate może wskazywać na niestabilność zależności albo zbyt agresywne timeouty. Sama obserwacja nie wystarcza; trzeba mieć plan reakcji.

## Kryterium dobrze zaprojektowanego systemu

System jest dobrze przygotowany do skalowania i awarii wtedy, gdy dla każdej krytycznej ścieżki można odpowiedzieć na kilka pytań. Jaki komponent stanie się pierwszym bottleneckiem przy rosnącym ruchu? Przy jakim RPS lub concurrency to nastąpi? Jaka metryka to potwierdzi? Co stanie się po przesunięciu tego limitu? Który komponent będzie następny? Jak system zachowa się przy awarii zależności? Czy requesty będą czekać bez końca, czy zadziałają timeouty? Czy retry pomogą, czy wywołają burzę? Czy circuit breaker odetnie chorą zależność? Czy istnieją przełączniki operacyjne pozwalające zachować najważniejszą funkcję?

Przygotowane klasy Java są mapą tych pytań. Nie rozwiązują problemu same z siebie, ale pomagają nazwać właściwe obszary projektowania. `CapacityModel` przypomina, że capacity trzeba policzyć. `AutoscalingConfig` przypomina, że skalowanie wymaga właściwej metryki i limitów. `CachePolicy` przypomina, że cache wymaga TTL, eviction i ochrony przed stampede. `RateLimiter` przypomina, że system musi kontrolować wejściowy ruch. `TimeoutBudget`, `RetryPolicy` i `CircuitBreaker` przypominają, że zależności zdalne muszą mieć granice. `OperationalLever` przypomina, że incydenty wymagają prostych dźwigni. `LoadTestScenario` przypomina, że teoria musi zostać sprawdzona.

Najkrótsze kryterium dojrzałości brzmi: dla każdej krytycznej ścieżki systemu potrafimy powiedzieć, przy jakim obciążeniu pierwszy padnie konkretny komponent, dlaczego właśnie on, jak to zmierzymy, co zrobimy po przekroczeniu limitu oraz jak system zdegraduje się bez kaskadowej awarii. Bez tej odpowiedzi architektura może wyglądać nowocześnie, ale jej odporność pozostaje nieudowodniona.
