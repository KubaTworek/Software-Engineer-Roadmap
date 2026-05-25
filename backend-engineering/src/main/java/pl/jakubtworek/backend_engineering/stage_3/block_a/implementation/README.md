# System Design: implementacja i testy

## Cel dokumentu

Ten dokument opisuje teoretyczne podstawy implementacji i testowania skalowalnych oraz odpornych systemów. Punktem wyjścia jest praktyczna checklista obejmująca stateless API, autoscaling, cache-aside, Redis, ochronę przed cache stampede, rate limiting, timeouty, retry, circuit breaker, graceful degradation, emergency levers, kolejki oraz zestaw testów obciążeniowych i awaryjnych.

Nie jest to instrukcja „kliknij tu, ustaw tam”. Celem jest zrozumienie, dlaczego poszczególne elementy są potrzebne, jakie ryzyka redukują i kiedy mogą same stać się źródłem problemów. W system designie implementacja nie polega na mechanicznym dodaniu popularnych wzorców. Polega na świadomym ustawieniu granic systemu: gdzie przechowujemy stan, jak kontrolujemy ruch, jak chronimy zależności, jak obsługujemy przeciążenie, kiedy degradujemy funkcje i jak potwierdzamy, że system zachowuje się zgodnie z przewidywaniami.

Najważniejsza zasada brzmi: odporność musi być zaprojektowana przed awarią, a skalowalność musi być potwierdzona testem. System, który działa tylko w warunkach nominalnych, nie jest jeszcze systemem odpornym. System, którego pojemność jest tylko deklaracją, nie jest jeszcze systemem skalowalnym. Dopiero połączenie właściwych mechanizmów implementacyjnych z testami baseline, step, spike, soak, cache-off, dependency-failure i retry-storm pozwala sensownie mówić o gotowości produkcyjnej.

## Stateless API jako fundament implementacji

Pierwszym warunkiem poziomego skalowania API jest stateless design. Oznacza to, że pojedyncza replika aplikacji nie powinna przechowywać krytycznego stanu w pamięci procesu ani zakładać, że użytkownik będzie stale obsługiwany przez tę samą instancję. Sesje w RAM, lokalne pliki jako trwały magazyn, lokalne kolejki bez mechanizmu przejęcia pracy czy zależność od konkretnej repliki utrudniają autoscaling, rolling deploymenty i recovery po awarii.

Stateless API nie oznacza braku stanu w systemie. Oznacza, że stan jest przeniesiony do jawnych backing services: bazy danych, Redisa, object storage, systemu kolejkowego albo zewnętrznego identity providera. Dzięki temu repliki aplikacji mogą być dodawane, usuwane i odtwarzane bez utraty informacji biznesowych. To upraszcza skalowanie poziome, ale przesuwa ciężar odpowiedzialności na zależności. Baza danych, cache i kolejki muszą mieć własne limity, metryki, timeouty i strategie degradacji.

W praktyce stateless design wymaga dyscypliny. Pliki lokalne powinny być traktowane jako ephemeral, czyli tymczasowe. Jeżeli proces zapisuje coś lokalnie, powinno to być możliwe do utraty bez wpływu na poprawność systemu. Sesje użytkowników powinny być reprezentowane przez tokeny lub przechowywane w zewnętrznym magazynie. Operacje długotrwałe powinny być odseparowane od konkretnej repliki, na przykład przez kolejkę albo trwały rekord zadania. Dopiero wtedy load balancer może swobodnie rozkładać ruch, a autoscaler może bezpiecznie zmieniać liczbę instancji.

Typową pułapką jest częściowo stateless API. Aplikacja z zewnątrz wygląda jak skalowalna, ale zawiera drobne założenia o lokalnym stanie: tymczasowe uploady, lokalny cache bez strategii odtworzenia, lokalne locki używane do koordynacji globalnej albo sesje w pamięci. Takie detale często nie przeszkadzają przy małym ruchu, ale ujawniają się podczas rolling update’u, awarii noda albo nagłego scale-outu.

## Autoscaling i granice bezpieczeństwa

Autoscaling jest mechanizmem regulacji liczby instancji, ale nie jest magicznym rozwiązaniem problemów pojemnościowych. Dobrze skonfigurowany autoscaling zwiększa capacity wtedy, gdy system tego potrzebuje, i zmniejsza je wtedy, gdy ruch spada. Źle skonfigurowany autoscaling może reagować zbyt późno, skalować po złej metryce albo przeciążyć zależności, które nie skalują się razem z API.

Minimalna liczba replik chroni przed zimnym startem, utrzymuje podstawową gotowość systemu i zmniejsza ryzyko, że pierwsza fala ruchu trafi w nieprzygotowaną infrastrukturę. Maksymalna liczba replik pełni rolę bezpiecznika. Bez górnego limitu autoscaler może próbować ratować API przez tworzenie kolejnych instancji, ale każda z nich będzie otwierać połączenia do bazy danych, Redisa albo zewnętrznych API. W efekcie system może szybciej doprowadzić do przeciążenia własnych zależności.

Dobór metryki skalowania musi odpowiadać naturze workloadu. Dla CPU-bound API skalowanie po CPU jest często rozsądne, ponieważ głównym ograniczeniem jest czas procesora. Dla IO-bound endpointów samo CPU bywa mylące. Endpoint może zużywać niewiele procesora, ale spędzać większość czasu na oczekiwaniu na bazę, payment API albo search service. W takim przypadku lepszymi metrykami są in-flight requests, request concurrency, queue depth, pool wait, dependency p95 latency albo metryki custom związane z realnym bottleneckiem.

Autoscaling powinien być analizowany razem z limitami backing services. Jeżeli baza danych obsługuje bezpiecznie określoną liczbę połączeń i QPS, to maksymalna liczba replik API powinna być ustawiona tak, aby nie przekroczyć tego limitu. W przeciwnym razie skala aplikacji stanie się narzędziem przeciążania bazy. Podobnie w usługach serverless lub containerowych trzeba kontrolować maksymalną liczbę instancji, ponieważ brak limitu może spowodować gwałtowny wzrost połączeń do DB lub zewnętrznych systemów.

Ważną pułapką jest traktowanie autoscalingu jako substytutu optymalizacji. Jeżeli pojedynczy request jest zbyt drogi, dodanie replik może jedynie przesunąć problem. Jeżeli zapytania do bazy są nieefektywne, autoscaling API nie usunie wolnych indeksów, blokad ani zbyt dużej liczby zapytań na request. Autoscaling działa najlepiej wtedy, gdy system ma już znane i kontrolowane koszty jednostkowe.

## Cache-aside i strategia invalidacji

Cache-aside jest jednym z najprostszych i najczęściej stosowanych wzorców cache’owania. Aplikacja najpierw próbuje pobrać dane z cache. Przy missie pobiera dane ze źródła prawdy, zapisuje je do cache i zwraca odpowiedź. Wzorzec ten jest atrakcyjny, ponieważ nie wymaga, aby cache był jedynym miejscem dostępu do danych. Źródło prawdy pozostaje w bazie danych lub innym trwałym magazynie, a cache pełni rolę przyspieszacza i odciążenia.

Najtrudniejszym elementem cache-aside nie jest sam odczyt, lecz invalidacja po zapisie. Jeżeli dane w źródle prawdy zostaną zmienione, cache może stać się nieaktualny. Trzeba świadomie zdecydować, czy po zapisie cache ma być usunięty, nadpisany nową wartością, czy pozostawiony do wygaśnięcia przez TTL. Każde podejście ma koszt. Usunięcie cache jest proste, ale następny odczyt zapłaci koszt missa. Nadpisanie cache zmniejsza miss, ale zwiększa złożoność i ryzyko niespójności. Poleganie wyłącznie na TTL jest najprostsze, ale może serwować nieświeże dane przez zbyt długi czas.

TTL powinien zależeć od klasy danych. Dane wolnozmienne, referencyjne albo publiczne mogą mieć dłuższy TTL. Dane użytkownika, ceny, stany zamówień i uprawnienia wymagają większej ostrożności. Nie istnieje jeden dobry TTL dla całego systemu. Różne klasy danych mają różny koszt nieświeżości i różny koszt missa. Dobrze zaprojektowany cache rozróżnia te klasy zamiast stosować jedną globalną wartość.

Cache jest skuteczny tylko wtedy, gdy realnie zmniejsza obciążenie źródła prawdy. Dlatego trzeba mierzyć hit ratio, miss ratio, latency cache, obciążenie bazy na ścieżce miss oraz wpływ cache na p95 endpointu. Wysoki hit ratio globalnie może ukrywać problem, jeżeli missy dotyczą najdroższych zapytań. Z kolei niski hit ratio nie zawsze jest katastrofą, jeśli cache chroni tylko kilka bardzo kosztownych, ale rzadkich operacji. Interpretacja cache zawsze musi być powiązana z konkretną ścieżką requestu.

## Redis, maxmemory i polityki eviction

Redis używany jako cache wymaga świadomego zarządzania pamięcią. Ustawienie `maxmemory` jest decyzją architektoniczną, nie tylko parametrem technicznym. Jeżeli limit jest za niski, Redis będzie zbyt często usuwał klucze, hit ratio spadnie, a baza danych otrzyma więcej ruchu. Jeżeli limit jest za wysoki i nie zostawiono zapasu na system operacyjny, replikację lub persistence, można doprowadzić do presji pamięciowej i niestabilności.

Polityka eviction decyduje, które klucze zostaną usunięte, gdy Redis osiągnie limit pamięci. Dla typowego cache najczęściej rozsądnym punktem startowym jest allkeys-lru, ponieważ usuwa klucze najmniej niedawno używane niezależnie od tego, czy mają TTL. Jeżeli system ma stabilne hot keys, które są często odczytywane przez dłuższy czas, allkeys-lfu może dać lepsze efekty, ponieważ opiera się na częstotliwości dostępu. Polityki volatile mają sens tylko wtedy, gdy konsekwentnie ustawiane są TTL-e. noeviction jest bezpieczne dla Redis jako store, ale słabe jako klasyczny cache, bo przy pełnej pamięci nowe zapisy zaczną kończyć się błędem.

`mem_not_counted_for_evict` jest ważne, ponieważ część pamięci używanej przez Redis może nie podlegać mechanizmowi eviction. Jeżeli nie zostawi się zapasu, system może wyglądać dobrze na poziomie `maxmemory`, a jednocześnie zużywać więcej pamięci niż zakładano. Dlatego planowanie pamięci Redisa powinno obejmować nie tylko rozmiar danych, ale również bufory, replikację, persistence i rezerwę systemową.

W praktyce Redis powinien być monitorowany przez hit ratio, evicted keys, expired keys, used memory, latency, liczbę klientów oraz pamięć nieuwzględnianą w eviction. Ciągłe evictions zwykle oznaczają, że working set nie mieści się w pamięci albo że TTL i polityka eviction nie pasują do wzorca dostępu. Nie należy ignorować evictions tylko dlatego, że cache „może usuwać dane”. Może, ale jeżeli robi to zbyt często, przestaje pełnić funkcję ochronną.

## Ochrona przed cache stampede

Cache stampede występuje wtedy, gdy popularny klucz wygasa, a wiele równoległych requestów jednocześnie wykonuje miss i próbuje odświeżyć tę samą wartość ze źródła prawdy. To jedna z bardziej zdradliwych awarii, ponieważ cache, który miał chronić bazę, nagle przepuszcza do niej skokowy ruch dokładnie w najgorszym momencie. Efektem mogą być timeouty, retry, przeciążenie DB i lawinowa degradacja systemu.

Single-flight lub request coalescing polega na tym, że dla jednego klucza tylko jedno odświeżenie jest wykonywane w danym czasie, a pozostałe requesty czekają na wynik albo dostają wartość zastępczą. W jednej instancji aplikacji można to zrealizować lokalnym lockiem per key, ale w systemie rozproszonym potrzebny jest mechanizm działający między replikami albo na warstwie gateway/CDN/cache. Sama lokalna ochrona może ograniczyć problem, ale nie usuwa go całkowicie, jeżeli system ma wiele replik.

Stale-while-revalidate pozwala zwrócić nieco nieświeżą wartość, a odświeżenie wykonać w tle. To podejście jest bardzo użyteczne dla danych, które mogą być przez krótki czas nieaktualne. Zamiast blokować wszystkich użytkowników na odświeżeniu, system zachowuje niską latencję i chroni źródło prawdy. Nie nadaje się jednak do danych, w których świeżość jest krytyczna, na przykład niektórych operacji finansowych, uprawnień albo stanów transakcji.

TTL jitter polega na dodaniu losowego odchylenia do czasu wygaśnięcia kluczy. Bez jittera tysiące kluczy zapisanych w tym samym czasie mogą wygasnąć równocześnie, powodując falę missów. Jitter rozprasza te wygaśnięcia w czasie. Jest to prosty mechanizm, ale często bardzo skuteczny, szczególnie przy masowym cache’owaniu podobnych obiektów.

## Rate limiting jako kontrola wejścia

Rate limiting kontroluje, ile requestów może wykonać dana tożsamość w określonym czasie. Może to być adres IP, API key, użytkownik, tenant albo partner B2B. Jego rola nie ogranicza się do bezpieczeństwa. Rate limiting chroni system przed nadmiernym ruchem, wymusza fair use, ogranicza blast radius błędnie działającego klienta i daje przewidywalność kosztów.

Dobry układ często łączy dwa poziomy limitowania. Na brzegu systemu stosuje się prosty limiter per IP, który chroni przed najbardziej oczywistym nadużyciem, scrapingiem albo brute force. Głębiej, w gatewayu lub aplikacji, stosuje się limiter per API key, user lub tenant, który odpowiada rzeczywistej relacji biznesowej. Limit per IP sam w sobie jest niewystarczający, ponieważ NAT, proxy i sieci firmowe mogą ukrywać wielu użytkowników za jednym adresem. Limit biznesowy powinien więc opierać się na stabilniejszej tożsamości niż IP.

Po przekroczeniu limitu API powinno zwrócić 429 Too Many Requests oraz, gdy to możliwe, Retry-After. Taki kontrakt pozwala klientowi zachować się poprawnie i nie ponawiać requestów natychmiast. Brak Retry-After może prowadzić do chaotycznych retry po stronie klientów, a to zwiększa ryzyko dodatkowego obciążenia.

Rate limiting nie powinien być traktowany jako narzędzie wyłącznie karzące klientów. Jest też elementem ochrony systemu. W czasie incydentu można obniżyć limity dla kosztownych endpointów, ograniczyć ruch niskiego priorytetu albo chronić krytyczne ścieżki biznesowe. Wtedy limiter staje się jednym z operational levers.

## Timeouty, retry i circuit breaker

Każde zdalne wywołanie powinno mieć timeout. Brak timeoutu oznacza, że aplikacja może czekać zbyt długo na zależność, blokując wątki, połączenia i pamięć. Connection timeout ogranicza czas zestawiania połączenia. Request timeout ogranicza czas oczekiwania na odpowiedź. Łączny budżet retry ogranicza czas całej operacji. Bez tych granic awaria zależności może rozlać się na system wywołujący.

Retry ma sens tylko dla błędów przejściowych i operacji idempotentnych albo zabezpieczonych idempotency key. Jeżeli operacja tworzy płatność, zamówienie albo wysyła wiadomość, bezpieczne ponowienie wymaga mechanizmu zapobiegającego duplikatom. Retry bez idempotencji może naprawić chwilowy błąd techniczny kosztem błędu biznesowego, który jest trudniejszy do odwrócenia.

Exponential backoff zmniejsza agresywność kolejnych prób, a jitter rozprasza retry w czasie. Bez jittera wiele klientów może ponawiać requesty w tych samych momentach, tworząc fale obciążenia. Retry powinny mieć maksymalną liczbę prób i maksymalny czas łączny. Powinny też występować świadomie w jednej warstwie, a nie przypadkowo w gatewayu, aplikacji, kliencie HTTP i bibliotece SDK jednocześnie. Retry w wielu warstwach prowadzą do retry storm, czyli niekontrolowanego zwielokrotnienia ruchu podczas awarii.

Circuit breaker chroni system przed ciągłym wywoływaniem zależności, która jest już niedostępna lub przeciążona. W stanie closed requesty przechodzą normalnie. Po przekroczeniu progu błędów breaker przechodzi do open i odrzuca wywołania fail-fast. Po pewnym czasie przechodzi do half-open, dopuszczając ograniczone próby sprawdzenia, czy zależność wróciła do zdrowia. Jeżeli próba się powiedzie, breaker wraca do closed. Jeżeli nie, ponownie otwiera obwód.

Timeout, retry i circuit breaker powinny być projektowane razem. Timeout bez retry może powodować zbyt wiele błędów przy krótkich zakłóceniach. Retry bez timeoutu może wisieć zbyt długo. Retry bez circuit breakera może przeciążać chorą zależność. Circuit breaker bez fallbacku może chronić system technicznie, ale nadal dawać użytkownikowi twardy błąd. Dopiero połączenie tych mechanizmów z graceful degradation daje pełniejszą odporność.

## Graceful degradation i emergency levers

Graceful degradation oznacza, że system potrafi zachować najważniejsze funkcje mimo awarii lub przeciążenia mniej krytycznych zależności. Nie każda funkcja ma taką samą wagę biznesową. Checkout, logowanie, płatność albo zapis zamówienia są zwykle ważniejsze niż rekomendacje, personalizacja, enrichment, analityka synchroniczna czy poboczne integracje. Jeżeli system nie rozróżnia tych funkcji, awaria opcjonalnej zależności może zatrzymać krytyczną ścieżkę.

Emergency levers to gotowe, przetestowane przełączniki operacyjne, które pozwalają szybko zmienić zachowanie systemu bez wdrażania nowego kodu. Mogą obejmować wyłączenie rekomendacji, ograniczenie ciężkich endpointów, włączenie trybu read-only, obniżenie concurrency do zależności, wyłączenie enrichmentów albo odrzucanie ruchu niskiego priorytetu. Ich wartość polega na tym, że podczas incydentu skracają czas reakcji.

Przełączniki awaryjne muszą być testowane. Nieprzetestowany emergency lever może nie działać, działać tylko częściowo albo mieć nieoczekiwane skutki uboczne. Warto też mieć jasny opis, kiedy wolno go użyć, kto podejmuje decyzję, jak monitorować efekt i jak wrócić do normalnego trybu. Degradacja nie powinna być improwizowana w środku incydentu.

Graceful degradation wymaga również projektowania odpowiedzi użytkownika. Jeżeli rekomendacje są niedostępne, strona produktu może nadal działać bez nich. Jeżeli enrichment profilu nie działa, użytkownik może otrzymać podstawową wersję danych. Jeżeli część systemu jest w trybie read-only, interfejs powinien to jasno komunikować. Techniczna odporność bez sensownego zachowania produktu może nadal oznaczać słabe doświadczenie użytkownika.

## Fail-fast i kolejki

Fail-fast oznacza szybkie odrzucenie operacji, której system nie może teraz bezpiecznie obsłużyć, zamiast pozwalania jej czekać bez końca. W systemach przeciążonych oczekiwanie jest często gorsze niż szybki błąd, ponieważ zajmuje zasoby i pogłębia kolejkę. Fail-fast może być realizowany przez timeouty, circuit breaker, rate limiting, bounded queues albo limity concurrency.

Kolejki są użyteczne wtedy, gdy asynchroniczność jest akceptowalna biznesowo. Pozwalają oddzielić przyjęcie żądania od wykonania pracy, wygładzić skoki ruchu i niezależnie skalować workerów. Nie powinny być jednak używane jako sposób ukrywania problemów. Kolejka bez DLQ, alarmów, kontroli wieku wiadomości i idempotentnych workerów jest antywzorcem. Może tylko przesunąć awarię w czasie, zamiast ją rozwiązać.

Najważniejszą metryką kolejki nie zawsze jest queue depth. Czasem ważniejszy jest oldest message age, czyli wiek najstarszej nieobsłużonej wiadomości. Jeżeli wiadomości mają deadline biznesowy, to przekroczenie wieku jest realną awarią nawet wtedy, gdy sama długość kolejki nie wygląda dramatycznie. Consumer lag pokazuje, czy konsumenci nadążają za producentami. DLQ volume pokazuje, czy praca trwale kończy się błędem. Success/failure rate workerów pokazuje jakość przetwarzania.

Kolejki wymagają idempotencji. Worker może otrzymać tę samą wiadomość więcej niż raz, zwłaszcza przy retry, timeoutach albo błędach potwierdzenia. Jeżeli operacja nie jest idempotentna, duplikaty mogą powodować błędy biznesowe. Dlatego projektując kolejkę, trzeba od razu zaplanować deduplikację, idempotency key, obsługę poison messages i strategię DLQ.

## Baseline test

Baseline test służy do ustalenia normalnego zachowania systemu pod stabilnym, referencyjnym obciążeniem. Jego celem nie jest znalezienie maksymalnego RPS, ale stworzenie punktu odniesienia. Bez baseline trudno powiedzieć, czy późniejszy wzrost latency, CPU, DB QPS albo miss ratio jest istotny. Baseline jest również podstawą do progów alarmowych typu „p95 większe niż dwukrotność normalnej wartości”.

Podczas baseline testu należy zapisywać p50, p95, p99, CPU, pamięć, GC, DB QPS, latency bazy, pool wait, cache hit ratio, dependency latency, error rate i podstawowe metryki kolejek. Ważne jest, aby test trwał wystarczająco długo, by system osiągnął stabilny stan. Zbyt krótki test może pokazać tylko fazę rozgrzewki, cold start albo chwilową anomalię.

Baseline test powinien być powtarzalny. Jeżeli każda kolejna wersja systemu ma inny baseline, zespół może wykrywać regresje wydajnościowe wcześniej, zanim trafią do produkcji. Warto porównywać koszt CPU per request, liczbę zapytań do bazy per endpoint, hit ratio i p95 zależności. Czasem najważniejszą regresją nie jest wzrost latency, ale wzrost kosztu jednostkowego, który dopiero przy większym ruchu stanie się problemem.

## Step test

Step test polega na stopniowym zwiększaniu RPS i obserwowaniu, kiedy system przestaje skalować się liniowo. Najważniejszym sygnałem jest „kolano” krzywej p95 lub p99 latency, czyli punkt, w którym niewielki wzrost ruchu powoduje nieproporcjonalny wzrost opóźnień. Ten punkt zwykle oznacza nasycenie któregoś zasobu: CPU, puli połączeń, bazy danych, zewnętrznego API, kolejki albo cache.

Step test jest najlepszym narzędziem do weryfikacji modelu pojemności. Jeżeli model przewiduje, że pierwszy nasyci się payment pool, to w okolicy przewidywanego RPS powinny rosnąć pool utilization, pool wait, dependency p95 i timeouts. Jeżeli zamiast tego pierwsze rośnie CPU API, model trzeba poprawić. Dobry step test nie tylko mówi „system wytrzymuje X RPS”, ale również „wiemy, dlaczego przy Y RPS zaczyna się psuć”.

Podczas step testu nie należy patrzeć wyłącznie na RPS i error rate. System może formalnie odpowiadać bez błędów, ale już łamać SLO przez wzrost p95/p99. Może też maskować problem przez retry albo fallbacki. Dlatego trzeba równolegle obserwować retry count, retry success ratio, circuit breaker state, fallback count i dependency latency. W przeciwnym razie można uznać test za udany, mimo że system działa dzięki kosztownej degradacji.

## Spike test

Spike test sprawdza reakcję systemu na nagły skok ruchu. Różni się od step testu tym, że nie daje systemowi komfortowego czasu na stopniową adaptację. W realnym świecie ruch często nie rośnie liniowo. Kampania marketingowa, publikacja linku, awaria klienta, boty albo zdarzenie sezonowe mogą spowodować gwałtowny wzrost obciążenia.

Podczas spike testu trzeba obserwować autoscaling, cold start, rate limiting, cache stampede, dependency latency i błędy. System nie musi przyjąć całego ruchu. Ważniejsze jest, aby zachował się kontrolowanie: albo obsłużył skok, albo odrzucił nadmiar przez limitery, albo zdegradował funkcje mniej krytyczne. Najgorszy wynik to kaskadowa awaria, w której API tworzy coraz więcej instancji, zależności zaczynają timeoutować, retry wzmacniają ruch, a użytkownicy dostają wolne błędy zamiast szybkiego odrzucenia.

Spike test dobrze ujawnia problemy z cold startem i minimalną liczbą instancji. Jeżeli system ma zbyt niskie min replicas, pierwsza fala ruchu może zapłacić koszt uruchamiania nowych instancji. Jeżeli max replicas jest zbyt wysokie, skok ruchu może przeciążyć bazę. Jeżeli cache ma wiele kluczy z takim samym TTL, spike może zbiec się z masowym wygaśnięciem i spowodować stampede.

## Soak test

Soak test polega na długotrwałym utrzymywaniu obciążenia, zwykle przez wiele godzin. Jego celem nie jest znalezienie natychmiastowego limitu, lecz wykrycie problemów narastających w czasie. Wiele awarii nie pojawia się w krótkim teście: wycieki pamięci, narastające kolejki, stopniowy wzrost GC, degradacja connection pool, dryf latency, fragmentacja zasobów albo kumulujące się retry.

Podczas soak testu szczególnie ważne są trendy. Stabilny p95 przez pierwsze 20 minut nie wystarcza, jeżeli po trzech godzinach latency rośnie dwukrotnie. Stała liczba błędów może być mniej groźna niż powoli rosnący oldest message age, który za kilka godzin złamie deadline biznesowy. Pamięć, GC, liczba wątków, liczba połączeń, queue depth, consumer lag i retry count powinny pozostawać w kontrolowanych granicach.

Soak test wymaga cierpliwości, ale jest jednym z najbardziej praktycznych testów przed produkcją. Krótkie benchmarki często wyglądają dobrze, bo system nie zdąży wejść w stan problematyczny. Długotrwałe obciążenie ujawnia, czy mechanizmy zwalniania zasobów, odnawiania połączeń, czyszczenia cache, obsługi kolejek i retry są stabilne.

## Cache-off i miss-ratio-up

Test cache-off lub miss-ratio-up odpowiada na pytanie, co stanie się, gdy cache przestanie chronić źródło prawdy. To scenariusz, który warto traktować poważnie, bo cache może maskować brak pojemności bazy. System może działać świetnie przy hit ratio 95%, ale załamać się, gdy hit ratio spadnie do 70% albo gdy popularne klucze wygasną równocześnie.

Cache-off jest brutalnym testem, w którym cache zostaje wyłączony lub pominięty. Miss-ratio-up jest bardziej kontrolowanym wariantem, w którym sztucznie zwiększa się liczbę missów. W obu przypadkach należy obserwować DB QPS, DB latency, slow queries, lock wait, pool wait, CPU bazy, error rate i p95 API. Celem nie zawsze jest udowodnienie, że baza wytrzyma pełny ruch bez cache. Czasem celem jest poznanie, jak szybko system degraduje się po utracie cache i jakie zabezpieczenia muszą zadziałać.

Taki test pomaga również dobrać TTL, warm-up cache i mechanizmy ochrony przed stampede. Jeżeli wyłączenie cache natychmiast przeciąża bazę, trzeba wiedzieć, czy system ma rate limiting, fallback, stale values, read-only mode albo inne dźwignie awaryjne. Cache nie powinien być jedyną cienką linią obrony przed awarią bazy.

## Dependency-failure test

Dependency-failure test polega na kontrolowanym wprowadzeniu błędów, timeoutów lub wzrostu latencji do zależności takich jak payment, search, recommendations, external API albo database replica. Celem jest sprawdzenie, czy system potrafi ograniczyć wpływ awarii zależności na całość. Test powinien obejmować timeouty, retry, circuit breaker, fallbacki, graceful degradation i emergency levers.

Najważniejsze pytanie brzmi: czy awaria zależności powoduje awarię całego systemu, czy tylko degradację funkcji, która naprawdę tej zależności potrzebuje. Jeżeli recommendations API nie działa, strona produktu powinna nadal działać bez rekomendacji. Jeżeli payment API ma wysoką latencję, checkout może wymagać bardziej ostrożnego zachowania, ale API nie powinno trzymać wątków bez końca. Jeżeli search jest niedostępny, system może pokazać komunikat lub uproszczony fallback.

Podczas testu trzeba obserwować dependency p95/p99, timeout count, retry count, breaker-open rate, fallback count, 5xx, p95 API i wykorzystanie zasobów. Jeżeli retry rosną szybciej niż ruch bazowy, istnieje ryzyko retry amplification. Jeżeli breaker się nie otwiera mimo wysokiego failure rate, progi są zbyt tolerancyjne. Jeżeli breaker otwiera się zbyt często przy drobnych zakłóceniach, progi są zbyt agresywne.

## Retry-storm test

Retry-storm test sprawdza, czy system nie wzmacnia awarii przez niekontrolowane ponawianie requestów. To szczególnie ważne w architekturach wielowarstwowych, gdzie retry mogą istnieć w kliencie, gatewayu, aplikacji, service mesh, SDK i bibliotece HTTP. Każda warstwa może wyglądać rozsądnie lokalnie, ale razem mogą stworzyć eksplozję ruchu.

Kontrolowany transient failure pozwala sprawdzić, czy retry mają exponential backoff, jitter, limit prób i limit czasu. Jitter powinien rozpraszać ponowienia w czasie. Retry powinny być wykonywane tylko dla błędów przejściowych i operacji idempotentnych. W wielu przypadkach retry powinny znajdować się w jednej wybranej warstwie, a nie wszędzie.

Metryką kluczową jest retry amplification, czyli stosunek całkowitej liczby wywołań do ruchu bazowego użytkowników. Jeżeli przy 100 requestach użytkowników zależność otrzymuje 300 lub 500 requestów z powodu retry, system może sam pogłębiać awarię. Retry success ratio mówi, czy ponawianie prób ma sens. Jeżeli większość retry kończy się błędem, lepiej szybciej przejść do fallbacku, fail-fast albo circuit breakera.

## Kryterium gotowości produkcyjnej

System można uznać za sensownie przygotowany dopiero wtedy, gdy implementacja i testy wzajemnie się potwierdzają. API powinno być stateless, ale trzeba to sprawdzić przez rolling update, awarię repliki i zmianę liczby instancji. Autoscaling powinien mieć min i max, ale trzeba sprawdzić spike testem, czy reaguje poprawnie i nie zabija zależności. Cache powinien mieć TTL i invalidację, ale trzeba sprawdzić cache-off i stampede scenarios. Retry powinny mieć backoff i jitter, ale trzeba sprawdzić retry-storm. Circuit breaker powinien istnieć, ale trzeba sprawdzić dependency-failure.

Najkrótsze kryterium gotowości brzmi: dla każdej krytycznej ścieżki wiemy, gdzie jest stan, jak skalujemy, czym ograniczamy ruch, jak chronimy zależności, co dzieje się po timeoutach, kiedy retry są bezpieczne, kiedy breaker się otwiera, jak degradujemy funkcje opcjonalne i jakimi testami potwierdziliśmy te założenia.

Checklisty są użyteczne, ale same nie gwarantują odporności. Mogą prowadzić do fałszywego poczucia bezpieczeństwa, jeżeli elementy są odhaczane bez zrozumienia. Prawdziwa wartość checklisty polega na tym, że wymusza pytania. Czy API naprawdę jest stateless? Czy max replicas chroni bazę? Czy cache ma plan invalidacji? Czy kolejka ma DLQ i alarm na oldest message age? Czy retry są idempotentne? Czy emergency levers były testowane? Czy step test pokazał ten sam bottleneck, który przewidywał model?

Dojrzały system nie jest systemem, który nigdy się nie psuje. Jest systemem, który psuje się w sposób ograniczony, obserwowalny i przewidywalny. Implementacja dostarcza mechanizmów kontroli, a testy pokazują, czy te mechanizmy działają pod presją. Dopiero wtedy można mówić o realnej skalowalności i odporności, a nie tylko o deklaracjach architektonicznych.
