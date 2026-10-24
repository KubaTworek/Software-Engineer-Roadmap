# Systemy rozproszone i odporność

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#11-systemy-rozproszone-i-odporność)

<!-- question-toc:start -->
## Spis pytań

- [DIST-01. Monolit, modularny monolit czy mikroserwisy? Jak uniknąć distributed monolith?](#dist-01-monolit-modularny-monolit-czy-mikroserwisy-jak-uniknąć-distributed-monolith)
- [DIST-02. Database-per-service: jak zapytać o dane dwóch usług?](#dist-02-database-per-service-jak-zapytać-o-dane-dwóch-usług)
- [DIST-03. Czym różnią się saga i 2PC? Jak przebiega transakcja rozproszona?](#dist-03-czym-różnią-się-saga-i-2pc-jak-przebiega-transakcja-rozproszona)
- [DIST-04. Choreografia czy orkiestracja sagi? Czy potrzebna jest Kafka?](#dist-04-choreografia-czy-orkiestracja-sagi-czy-potrzebna-jest-kafka)
- [DIST-05. Co oznacza timeout wywołania w połowie transakcji?](#dist-05-co-oznacza-timeout-wywołania-w-połowie-transakcji)
- [DIST-06. Jak łączyć timeout, retry, circuit breaker i bulkhead?](#dist-06-jak-łączyć-timeout-retry-circuit-breaker-i-bulkhead)
- [DIST-07. Jak działa circuit breaker i dlaczego sam nie zatrzyma przeciążenia?](#dist-07-jak-działa-circuit-breaker-i-dlaczego-sam-nie-zatrzyma-przeciążenia)
- [DIST-08. Jak ponawiać bez retry storm i podwójnych efektów?](#dist-08-jak-ponawiać-bez-retry-storm-i-podwójnych-efektów)
- [DIST-09. Jak działa rate limiting i czym różni się od limitu współbieżności?](#dist-09-jak-działa-rate-limiting-i-czym-różni-się-od-limitu-współbieżności)
- [DIST-10. Jak projektować load shedding, backpressure i odporność na skok 10×?](#dist-10-jak-projektować-load-shedding-backpressure-i-odporność-na-skok-10)
- [DIST-11. Cache-aside, write-through czy write-behind?](#dist-11-cache-aside-write-through-czy-write-behind)
- [DIST-12. Kiedy unieważnić Redis po asynchronicznym update DB?](#dist-12-kiedy-unieważnić-redis-po-asynchronicznym-update-db)
- [DIST-13. Jak odróżnić TTL, eviction i błąd świeżości? Jak Redis usuwa wygasłe klucze?](#dist-13-jak-odróżnić-ttl-eviction-i-błąd-świeżości-jak-redis-usuwa-wygasłe-klucze)
- [DIST-14. Co robić po awarii Redis albo gdy cache działa, a DB nadal ma 100% obciążenia?](#dist-14-co-robić-po-awarii-redis-albo-gdy-cache-działa-a-db-nadal-ma-100-obciążenia)
- [DIST-15. Jak zapobiegać cache stampede i hot keys?](#dist-15-jak-zapobiegać-cache-stampede-i-hot-keys)
- [DIST-16. Kiedy silna spójność, a kiedy eventual consistency?](#dist-16-kiedy-silna-spójność-a-kiedy-eventual-consistency)
- [DIST-17. Co dzieje się wewnątrz rozproszonej bazy podczas zapisu?](#dist-17-co-dzieje-się-wewnątrz-rozproszonej-bazy-podczas-zapisu)
- [DIST-18. Jak consistent hashing ogranicza przemieszczanie danych?](#dist-18-jak-consistent-hashing-ogranicza-przemieszczanie-danych)
- [DIST-19. Jak działają leader election, ZooKeeper i fencing?](#dist-19-jak-działają-leader-election-zookeeper-i-fencing)
- [DIST-20. Jak działa distributed lock i kiedy go unikać?](#dist-20-jak-działa-distributed-lock-i-kiedy-go-unikać)
- [DIST-21. Jak radzić sobie z clock skew między węzłami?](#dist-21-jak-radzić-sobie-z-clock-skew-między-węzłami)
- [DIST-22. Co zmienia poziome skalowanie usługi stateful?](#dist-22-co-zmienia-poziome-skalowanie-usługi-stateful)
- [DIST-23. Czym różnią się forward proxy, reverse proxy, load balancer, gateway i discovery?](#dist-23-czym-różnią-się-forward-proxy-reverse-proxy-load-balancer-gateway-i-discovery)
<!-- question-toc:end -->

## DIST-01. Monolit, modularny monolit czy mikroserwisy? Jak uniknąć distributed monolith?

Monolit wdraża się jako całość, modularny monolit dodatkowo pilnuje wewnętrznych granic. Mikroserwisy umożliwiają niezależne wdrożenia i skalowanie, ale dodają sieć, rozproszone dane, operacje i awarie częściowe. Nie są automatycznym awansem architektury.

Distributed monolith ma wiele deployów, lecz wymaga skoordynowanych wydań, wspólnej bazy i łańcuchów synchronicznych zależności. Wyznaczam ownership i kontrakty, ograniczam cross-service chatter. Migrację robię stopniowo przez strangler/branch by abstraction, z testami i jedną jasną odpowiedzialnością za zapis.

Przykładem złej ekstrakcji jest przeniesienie modułu faktur do usługi, która nadal musi synchronicznie pytać pięć endpointów starego monolitu i bezpośrednio zmienia jego tabele. Zespół dostał latency i awarie sieci, ale nie niezależność zmian.

Najpierw opisuję granicę danych i przypadków użycia, potem organizację deploya. W modularnym monolicie można wymusić brak importów do wnętrza innych modułów i komunikację przez publiczne porty. Jeśli problemem jest wspólna odpowiedzialność, samo rozdzielenie procesów go nie naprawi. Wyodrębnienie powinno mieć konkretną korzyść: niezależną skalę, lifecycle, ownership lub izolację awarii.

## DIST-02. Database-per-service: jak zapytać o dane dwóch usług?

Właściciel usługi kontroluje zapis i schemat swoich danych. Dla prostego odczytu można zastosować API composition; dla częstych złożonych zapytań buduję projekcję z eventów/CDC. Zapytania analityczne trafiają do dedykowanego magazynu.

Zdalne JOIN przez bezpośredni dostęp do cudzych tabel wiąże lifecycle usług. Projekcja daje opóźnienie i wymaga rebuild/reconciliation, a kompozycja zależy od dostępności i czasu obu API. Wybieram według potrzebnej świeżości i kosztu, jawnie komunikując stan częściowy.

Strona szczegółów zamówienia może skomponować odpowiedź z orders i delivery, tolerując brak estymaty dostawy. Raport wymagający milionów JOIN między tymi danymi lepiej oprzeć na projekcji/analityce, zamiast wykonywać miliony HTTP requestów.

Dla projekcji zapisuję wersję zdarzenia, obsługuję usunięcia i buduję możliwość odbudowy z checkpointem. Wyświetlany wynik może zawierać asOf/freshness. Operacja wymagająca aktualnej walidacji, np. anulowania wysłanego zamówienia, nie może polegać wyłącznie na starej projekcji. Granica odczytu i granica decyzji biznesowej nie muszą być tym samym systemem.

## DIST-03. Czym różnią się saga i 2PC? Jak przebiega transakcja rozproszona?

2PC koordynuje prepare i commit/abort uczestników; może utrzymywać zasoby i czekać na rozstrzygnięcie po awarii koordynatora. Saga składa lokalne transakcje z trwałym stanem procesu oraz kompensacjami. Nie daje automatycznie izolacji między wszystkimi krokami.

Saga wymaga idempotencji kroków, timeoutów, retry, obsługi wyniku nieznanego i rekoncyliacji. Kompensacja to nowa operacja biznesowa, nie magiczne cofnięcie czasu. Wybór wynika z niezmienników i obsługi wspólnych protokołów, a nie z zakazu 2PC w każdej sytuacji.

W 2PC uczestnik po prepare zobowiązuje się czekać na rozstrzygnięcie; utrata koordynatora może pozostawić transakcję in-doubt i zasoby zajęte. Recovery nie polega na dowolnym „uznaniu timeoutu za rollback”, bo część uczestników mogła już dostać decyzję commit.

Saga dopuszcza widoczne stany pośrednie, np. stock zarezerwowany, płatność oczekuje. Trzeba określić, co wolno użytkownikowi zrobić w tym czasie, oraz jak zabezpieczyć równoległe procesy. Kompensacja rabatu i refund mogą mieć inne kwoty/warunki niż początkowy krok. To koszt biznesowej semantyki, a nie wyłącznie technika komunikatów.

## DIST-04. Choreografia czy orkiestracja sagi? Czy potrzebna jest Kafka?

Choreografia pozwala usługom reagować na zdarzenia, a orkiestracja utrzymuje jawną maszynę stanów procesu i wysyła polecenia. Pierwsza ogranicza centralny koordynator, druga ułatwia śledzenie złożonego przepływu, timeoutów i kompensacji.

Sagę można zbudować na HTTP, innym brokerze albo trwałych jobach; Kafka nie jest wymagana. Potrzebna jest trwałość decyzji i bezpieczne wznowienie. Przy porzuconym kroku stan nie może zniknąć razem z pamięcią procesu.

Dla prostej reakcji „zamówienie potwierdzone → wyślij email” choreografia jest czytelna. Dla kilkunastu kroków z terminami, rozgałęzieniami i ręczną interwencją jawny orkiestrator ułatwia odpowiedź „na czym proces utknął”. Nie musi być globalnym singletonem: instancje procesu można partycjonować.

Trwały stan sagi zawiera ID, etap, wersję, deadline i wynik prób. Odebranie spóźnionego success po uruchomionej kompensacji wymaga dozwolonego przejścia, nie przypadkowej kolejności callbacków. Kafka, HTTP i job table są transportem; protokół procesu pozostaje odpowiedzialnością aplikacji.

## DIST-05. Co oznacza timeout wywołania w połowie transakcji?

Oznacza brak odpowiedzi w budżecie klienta, nie pewność braku wykonania. Serwer mógł zatwierdzić operację, nadal działać albo ją odrzucić. Rollback lokalnej DB nie wycofuje zdalnego skutku.

Używam identyfikatora operacji, endpointu statusu, idempotencji i rekoncyliacji. Nie trzymam locka DB na czas nieograniczonego HTTP. Dla skutku nieodwracalnego nie uruchamiam od razu przeciwnego działania, dopóki nie rozstrzygnę niepewnego wyniku.

Po timeoutcie createPayment możliwe są przynajmniej trzy stany: nie dotarło, wykonuje się, wykonano bez dostarczenia odpowiedzi. Klient nie potrafi ich rozróżnić na podstawie własnego zegara. Dlatego status UNKNOWN/PENDING jest poprawnym wynikiem, jeśli opisuje realną niepewność.

<!-- example:DIST-05-1 kind:http-contract -->
**Kontekst:** Przykład kontraktu HTTP; wymaga odpowiadającego API, nie jest automatycznie wysyłanym żądaniem.

~~~http
GET /payments/by-operation/op-123
~~~

To przykład kontraktu statusu. Gdy serwer nie wspiera sprawdzania ani idempotencji, automatyczny retry nieodwracalnej operacji może być niedopuszczalny. Lokalna kompensacja przed ustaleniem wyniku może uwolnić środki, które zdalnie zostały już wydane. Reconciliation domyka takie przypadki.

## DIST-06. Jak łączyć timeout, retry, circuit breaker i bulkhead?

Timeout ogranicza oczekiwanie, retry pomaga przy przejściowym błędzie, circuit breaker zatrzymuje wywołania do niesprawnej zależności, bulkhead ogranicza zajmowane zasoby. Każdy mechanizm rozwiązuje inny fragment problemu i wymaga wspólnego deadline.

Daję osobny budżet/limit zależności, krótką kontrolowaną kolejkę i bezpieczną degradację. Resilience4j jest biblioteką realizującą takie wzorce, a nie gwarancją odporności samą obecnością zależności. Kolejność dekoratorów wpływa na liczenie prób, błędów i zajętych pozwoleń.

Budżet 500 ms nie powinien składać się z pięciu prób po 500 ms i dodatkowej kolejki. Rozdzielam go na acquisition, próbę, ewentualne ponowienie i margines odpowiedzi. Bulkhead chroni konkretną zależność, żeby wolny operator A nie zajmował zasobów dla operatora B.

Test awarii ma opóźnienie, timeout, szybkie błędy i recovery, bo te przypadki inaczej obciążają system. Mierzę aktywne wywołania po timeoutach oraz liczbę prób na jedną operację klienta. Biblioteka resilience jest narzędziem wykonania polityki, nie źródłem poprawnego budżetu.

## DIST-07. Jak działa circuit breaker i dlaczego sam nie zatrzyma przeciążenia?

Circuit breaker przechodzi przez CLOSED (pomiar), OPEN (odmowa) i HALF_OPEN (ograniczone próby odzyskania). Okno, minimalna próbka i klasyfikacja błędów decydują o reakcji. Próg 50% z małą próbką może reagować na przypadek, a zbyt długie okno — zbyt wolno na nagłą awarię. Oczekiwana odmowa biznesowa nie musi oznaczać awarii zależności.

Breaker nie usuwa już uruchomionej pracy i nie zatrzyma pierwszej fali, zanim zbierze dane. Timeout ogranicza czekanie, bulkhead liczbę zajętych zasobów, a admission/queue policy dopuszczenie nadmiaru.

Half-open musi ograniczać próby także w skali wielu replik; ich lokalne breakery nie stanowią jednego regulatora globalnego. Fallback zachowuje semantykę, np. jawnie oznaczony stary odczyt, nigdy fikcyjny sukces płatności. Sprawdzam też, czy awaryjna ścieżka nie przeciąża kolejnej zależności.

## DIST-08. Jak ponawiać bez retry storm i podwójnych efektów?

Retry dotyczy sklasyfikowanego błędu przejściowego i mieści się we wspólnym deadline oraz budżecie prób. Stosuję ograniczoną liczbę prób, exponential backoff, jitter i respektuję Retry-After. Jitter rozkłada czas prób; nie zmniejsza sam ich liczby.

Trzy próby klienta × trzy gatewaya × trzy serwisu mogą dać 27 wywołań operatora dla jednego zamiaru. Wyznaczam właściciela polityki i mierzę próby na operację, aby poszczególne warstwy nie mnożyły ich niezależnie.

Błąd walidacji nie odzyska się przez retry, a timeout płatności może oznaczać wykonany skutek: potrzebne jest to samo ID i status/idempotencja. Po anulowaniu kończę zbędne próby, chyba że odpowiedzialność przejął trwały proces. Test recovery sprawdza stopniowe nadrobienie pracy po powrocie zależności, nie tylko zmianę stanu circuit breakera.

## DIST-09. Jak działa rate limiting i czym różni się od limitu współbieżności?

Token bucket uzupełnia tokeny w czasie i pozwala na kontrolowany burst. Sliding log jest dokładniejszy, lecz przechowuje zdarzenia; sliding counter przybliża okno mniejszym kosztem. Fixed window dopuszcza skok na granicy okien, a leaky bucket wygładza wypuszczanie pracy.

Limit RPS nie ogranicza liczby wolnych operacji w toku. Rozdzielam limity użytkownika, tenanta, endpointu i kosztu. Operacja sprawdzenia oraz zużycia budżetu musi być atomowa. Dla wielu węzłów potrzebny jest wspólny stan albo przydział lokalnych budżetów.

Fixed window 100/min może dopuścić 100 requestów pod koniec minuty i kolejnych 100 na początku następnej. Token bucket pozwala określić burst niezależnie od średniego tempa. Sliding log dokładnie odwzorowuje okno kosztem rekordów czasu.

W Redis sprawdzenie, refill i decrement powinny być jedną atomową operacją, np. skryptem, a nie serią GET/SET z aplikacji. W klastrze trzeba uwzględnić współlokację kluczy. Gdy decyzja limitera jest krytyczna, opisuję jego awarię i odtwarzanie; cache wyniku limitu na wielu gatewayach może dopuścić większy burst niż planowano.

## DIST-10. Jak projektować load shedding, backpressure i odporność na skok 10×?

Przyjmuję tylko pracę mieszczącą się w budżecie zasobów i deadline. Ograniczone kolejki buforują krótkie wahania; nadmiar odrzucam kontrolowanie, priorytetyzując krytyczne operacje. Backpressure sygnalizuje producentowi, żeby zwolnił; load shedding porzuca/odmawia pracy.

Autoscaling pomaga z opóźnieniem i tylko przy skalowalnym bottlenecku. Mierzę queue age, in-flight, saturation i odrzucenia. Nie przyjmuję zadania asynchronicznie ze statusem sukcesu, jeśli nie mam trwałego miejsca i odpowiedzialności za jego wykonanie.

Ograniczona kolejka powinna mieścić pracę, która ma szansę zakończyć się przed deadline. Jeśli przyjęte żądanie jest już zbyt stare, wykonanie go dodatkowo obciąża system po tym, jak klient zrezygnował. Przy kolejkach asynchronicznych zamiast deadline requestu istotny jest termin biznesowy.

Priorytety nie mogą całkowicie zagłodzić zwykłego ruchu; potrzebne są budżety i ewentualne aging. Load shedding powinien oszczędzić zasoby przed kosztowną autoryzacją/parsowaniem, w granicach bezpieczeństwa. Alarm na odrzucenia jest potrzebny, ale szybka kontrolowana odmowa może być prawidłową ochroną SLO dla pozostałych.

## DIST-11. Cache-aside, write-through czy write-behind?

Strategię cache dobieram do danych i operacji:

| Strategia | Ścieżka | Kluczowy tryb awarii |
| --- | --- | --- |
| Cache-aside | miss → źródło → kopia | stampede i wyścig odczytu z invalidacją |
| Write-through | zapis przez warstwę aktualizującą źródło/kopię | częściowy wynik dwóch niezależnych zapisów |
| Write-behind | potwierdzenie i odroczony trwały zapis | utrata/kolejność zmian bez trwałego bufora |

Żadna nazwa nie ustanawia atomowości dwóch systemów. Definiuję źródło prawdy, kolejność, retry, TTL, wersje i dokończenie po crashu. W write-behind chwila odpowiedzi klientowi jest świadomą zmianą gwarancji trwałości.

Opis produktu może tolerować sekundy nieświeżości, dostępne środki wymagają innej ścieżki. Dane księgowe nie mogą zależeć wyłącznie od ulotnej kopii. Pomiar obejmuje hit ratio, wiek danych i koszt miss, bo duży cache bez trafień dodaje tylko pamięć oraz nowy punkt awarii.

## DIST-12. Kiedy unieważnić Redis po asynchronicznym update DB?

Invalidacja przed commitem dopuszcza odtworzenie starej kopii:

| Krok | Writer | GET |
| --- | --- | --- |
| 1 | usuwa cache | |
| 2 | DB nadal ma wersję 7 | czyta wersję 7 |
| 3 | | zapisuje wersję 7 w cache |
| 4 | zatwierdza wersję 8 | cache pozostaje stary |

**Podstawowa poprawka:** commit DB poprzedza invalidację, a zamiar aktualizacji cache wychodzi przez outbox/CDC, by crash między commitem i publikacją nie zgubił zmiany. Dla zwykłego odczytu ustalam dopuszczalny lag oraz TTL.

Pozostaje drugi wyścig: GET odczytał 7 przed commitem, ale wkłada ją po invalidacji wersji 8. Przy wymaganiu silniejszej ochrony protokół musi atomowo pamiętać znaną wersję/tombstone i odrzucać starsze wstawienie, także na miss. Sam numer version w JSON bez porównania niczego nie wymusza. Zdarzenie invalidacji może też dojść z opóźnieniem, więc nie obiecuję natychmiastowej globalnej świeżości. Krytyczną decyzję kieruję do źródła prawdy albo stosuję pełny protokół o wymaganej spójności.

<!-- follow-up:DIST-12 -->
**Sprawdź rozumienie:** Dlaczego samo delete cache po commicie nadal pozwala wrócić starej wartości?

<details>
<summary>Kierunek odpowiedzi</summary>

GET mógł pobrać starą wersję przed commitem i wykonać put po invalidacji. Potrzebne jest atomowe porównanie wersji/tombstone w całym protokole albo krytyczny odczyt ze źródła prawdy; TTL tylko ogranicza czas nieświeżości.

</details>

## DIST-13. Jak odróżnić TTL, eviction i błąd świeżości? Jak Redis usuwa wygasłe klucze?

TTL określa ważność w czasie, eviction usuwa wpisy pod presją pamięci według polityki, np. LRU/LFU. Redis wykrywa wygaśnięcie przy dostępie i aktywnie sprawdza część kluczy; fizyczne odzyskanie pamięci nie musi nastąpić idealnie w chwili TTL.

Stary wynik to głównie problem kontraktu invalidacji/wersji, nie samego wyboru LRU. LRU przybliża niedawne użycie, LFU częstotliwość, a brak limitu prowadzi do niekontrolowanego wzrostu. [Wygaśnięcia Redis](https://redis.io/docs/latest/commands/expire/), [polityki eviction](https://redis.io/docs/latest/develop/reference/eviction/).

Wygaśnięcie wpisu to reguła logiczna, a odzyskanie pamięci może nastąpić później. Eviction usuwa nawet nadal ważny wpis, jeśli polityka pamięci na to pozwala. To wpływa na miss rate, ale samo w sobie nie powinno tworzyć niepoprawnego autorytatywnego wyniku.

<!-- example:DIST-13-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
TTL: „po 60 s nie używaj tej kopii”
Eviction: „usuń kopię wcześniej, bo potrzebujemy pamięci”
Invalidacja: „stan źródła się zmienił — ta kopia jest nieaktualna”
~~~

Kontrakt powinien rozdzielać te trzy zdarzenia. Klucz bez TTL nie musi być wieczny, jeśli obejmuje go eviction; TTL bez maxmemory nadal może dopuścić zbyt dużą liczbę wpisów w krótkim czasie.

## DIST-14. Co robić po awarii Redis albo gdy cache działa, a DB nadal ma 100% obciążenia?

Sprawdzam hit ratio per operacja, klucze, TTL, evictions, writes i koszt zapytań nieobjętych cache. Wysokie globalne hit ratio może ukrywać jedno bardzo drogie zapytanie. Nie zakładam, że cache odciąża zapis ani unique reads.

Przy awarii stosuję krótkie timeouty, breaker, ograniczony fallback do DB, single-flight i ewentualnie bezpieczny stale response. Bez limitu fallback może zabić DB. Gdy Redis przechowuje sesje lub stan autorytatywny, strategia musi uwzględnić wymagania poprawności, nie tylko dostępność.

Failover cache może obniżyć hit ratio niemal do zera i skierować cały ruch do DB. Jeśli DB była wymiarowana na 10% miss, pełny fallback oznacza wielokrotny wzrost obciążenia. Potrzebne są limit, kolejka/odmowa i stopniowe rozgrzewanie.

Jeśli Redis przechowuje deduplikację płatności lub sesje, utrata danych zmienia poprawność, nie tylko wydajność. Taki stan wymaga innej polityki trwałości i odzyskania niż kopia katalogu. Przy utrzymującym się CPU DB sprawdzam również klucze przypadkowo zawierające request ID, przez co każde żądanie jest nowym miss.

## DIST-15. Jak zapobiegać cache stampede i hot keys?

Single-flight łączy równoczesne odczyty tego samego brakującego klucza; jitter TTL rozkłada wygaśnięcia. Wcześniejsze odświeżanie, stale-while-revalidate i ograniczony negatywny cache mogą zmniejszyć szczyty, jeśli domena toleruje takie wyniki.

Hot key nadal może przeciążyć jeden węzeł. Pomaga lokalny cache z kontrolowaną świeżością lub rozproszenie odczytu, lecz nie dowolny podział licznika wymagającego atomowości. Współdzielona blokada odświeżania też wymaga timeoutu i zachowania po crashu właściciela.

Single-flight powinien łączyć tylko równoważne odczyty, np. tego samego klucza i zakresu uprawnień. Zbyt szerokie łączenie grozi udostępnieniem wyniku innego użytkownika. Błąd wspólnego odczytu trzeba usunąć z rejestru, żeby nie utrwalić porażki na zawsze.

Stale-while-revalidate działa tylko dla danych, które wolno chwilowo pokazać jako stare. Dane o zablokowanym dostępie albo usunięciu mogą wymagać twardszej invalidacji. Test przeciążenia powinien wymusić jednoczesne wygaśnięcie popularnego klucza i potwierdzić ograniczoną liczbę odczytów DB, a nie jedynie poprawność pojedynczego get.

## DIST-16. Kiedy silna spójność, a kiedy eventual consistency?

Nie preferuję eventual consistency bezwarunkowo „bo skala”. Silna spójność jest potrzebna np. do ochrony salda lub pojedynczej rezerwacji; projekcja feedu czy wyszukiwania może tolerować opóźnienie. W czasie partition określony model nie może zawsze zapewnić jednocześnie liniowalności i dostępności każdej operacji.

Definiuję świeżość, read-your-writes i rozwiązywanie konfliktów. Eventual consistency wymaga faktycznej propagacji/naprawy po ustaniu błędów, a nie samego oczekiwania. Kosztem bywają stany PENDING, wersje oraz bardziej złożone UX i operacje.

Spójność jest własnością konkretnego odczytu/zapisu. Można utrzymywać silną kontrolę rezerwacji i eventual consistency wyszukiwarki w tym samym produkcie. Nie należy więc deklarować całego systemu „CP albo AP” bez wskazania operacji i modelu partition.

Dla projekcji klient musi rozumieć PENDING i maksymalny oczekiwany lag. Dla silnej decyzji trzeba znieść koszt komunikacji lub odmówić przy braku quorum. Reconciliation wykrywa przypadki, których propagacja nie naprawiła. Nazwa eventual nie daje gwarancji zbieżności, jeśli utraciliśmy zdarzenie i nie mamy drogi odtworzenia.

## DIST-17. Co dzieje się wewnątrz rozproszonej bazy podczas zapisu?

Router ustala shard/właściciela, serwer waliduje operację, zapisuje log i stan według protokołu silnika oraz replikuje do wymaganych węzłów. Potwierdzenie następuje po warunku wynikającym z konfiguracji trwałości i konsensusu/quorum. Nie każdy produkt ma taki sam model lidera.

Crash przed odpowiedzią daje klientowi niepewny wynik. Retry potrzebuje stabilnej tożsamości operacji. Quorum `R+W>N` samo nie jest uniwersalnym dowodem liniowalności: znaczenie mają wersje, konkurencyjne zapisy i protokół wyboru lidera.

W systemie liderowym klient może trafić do niewłaściwego węzła, zostać przekierowany do lidera shardu, a ten zapisze log i czeka na wymagane repliki. Potwierdzenie po lokalnym zapisie ma inną ochronę przed utratą węzła niż potwierdzenie po quorum.

Awaria po rozesłaniu do replik, przed odpowiedzią pozostawia wynik nieznany klientowi. Nowy lider musi respektować zatwierdzony porządek, a stary zostać odcięty od zapisów. Konkretny silnik określa, jak działa recovery, konflikty i read consistency; nie wolno obiecywać liniowalności tylko na podstawie „mamy trzy repliki”.

## DIST-18. Jak consistent hashing ogranicza przemieszczanie danych?

Klucze i węzły są mapowane do wspólnej przestrzeni; klucz przypisuje się np. następnemu węzłowi na pierścieniu. Zmiana członkostwa przenosi część zakresów zamiast niemal wszystkich kluczy, jak przy prostym modulo liczby serwerów. Virtual nodes pomagają wyrównać podział.

Nie rozwiązuje hot keys, migracji w toku ani replikacji. Potrzebne są wersja mapy, przekazanie ownershipu i ochrona przed obsługą przez dwóch właścicieli. Węzeł wirtualny hashowania nie ma związku z virtual thread w JVM.

Dla prostego modulo hash(key) % 4 → % 5 zmienia się właściciel dużej części kluczy. Consistent hashing po dodaniu węzła przejmuje przede wszystkim część sąsiednich zakresów. Węzły wirtualne zmniejszają nierówny podział, ale nie wyrównają popularności jednego klucza.

W okresie migracji router musi wiedzieć, czy czytać starą czy nową kopię i gdzie wolno zapisywać. Wersja mapy oraz forwarding pomagają uniknąć „brak klucza” podczas przełączenia. Plan obejmuje transfer, dogonienie zmian, walidację i usunięcie starej kopii dopiero po bezpiecznym ownership switch.

## DIST-19. Jak działają leader election, ZooKeeper i fencing?

W przepisie ZooKeeper uczestnicy tworzą ephemeral sequential znodes, a najmniejszy numer wygrywa; obserwowanie poprzednika ogranicza herd effect. Sesja i utrata połączenia wymagają obsługi niepewności. To wybór lidera aplikacji, odrębny od wewnętrznego protokołu klastra ZooKeeper.

Stary lider może nadal działać po pauzie/partition, więc zasób musi odrzucać stare fencing tokens/epoki. Sam lease nie zatrzymuje starego procesu. Kafka od 4.0 używa wyłącznie KRaft, więc ZooKeeper nie jest uniwersalnym opisem współczesnej Kafka. [ZooKeeper recipes](https://zookeeper.apache.org/doc/current/recipes.html), [Kafka KRaft](https://kafka.apache.org/40/getting-started/zk2kraft/).

Ephemeral node znika po utracie sesji, nie natychmiast po każdej utraconej paczce. Klient z niepewnym połączeniem nie powinien traktować braku odpowiedzi jako dowodu, że nadal ma bezwarunkową władzę. Zewnętrzny zasób musi rozpoznawać epokę/fencing token.

Obserwowanie poprzednika ogranicza jednoczesne budzenie wszystkich kandydatów. Trzeba też obsłużyć wynik nieznany przy tworzeniu sequential node: operacja mogła dojść, choć odpowiedź zginęła. Własny protokół wyboru lidera to często większy koszt niż użycie sprawdzonej biblioteki i odpowiedniego magazynu koordynacyjnego.

<!-- follow-up:DIST-19 -->
**Sprawdź rozumienie:** Lease lidera wygasł podczas długiego GC. Co zatrzyma jego opóźniony zapis po powrocie?

<details>
<summary>Kierunek odpowiedzi</summary>

Fencing/epoka sprawdzana przez zasób docelowy. Sam timeout lease nie zatrzymuje starego procesu. Nowy lider oraz idempotencja nie są substytutem odrzucenia starszego właściciela.

</details>

## DIST-20. Jak działa distributed lock i kiedy go unikać?

Koordynator przydziela wyłączność zwykle na czas lease. Klient musi bezpiecznie odnawiać, rozróżniać właściciela przy zwalnianiu i obsługiwać wygaśnięcie podczas GC/pauzy sieci. Krytyczny zasób powinien kontrolować fencing lub wersję.

Jeśli reguła dotyczy jednej DB, constraint, atomowy UPDATE lub row lock często daje prostszy dowód poprawności. Lock Redis bez uwzględnienia failover i opóźnionego właściciela nie gwarantuje pojedynczego efektu biznesowego. Lock nie zastępuje idempotencji i rekoncyliacji.

Przykład lease: worker A otrzymuje epokę 10, zatrzymuje się na GC, worker B przejmuje z epoką 11. Po powrocie A nadal może wysłać opóźniony zapis. Tylko sprawdzenie epoki przez zasób docelowy pozwala go odrzucić; samo wygaśnięcie klucza locka nie zatrzymuje kodu A.

Lock powinien mieć identyfikator właściciela, a release nie usuwać cudzego nowszego lease. Nawet poprawna wyłączność nie zapobiega ponownemu efektowi po crashu między zapisem a potwierdzeniem. Dlatego łączę ją z idempotencją i trwałym stanem, gdy wymagany jest pojedynczy rezultat biznesowy.

## DIST-21. Jak radzić sobie z clock skew między węzłami?

Synchronizuję zegary, monitoruję odchylenia i nie opieram globalnej kolejności ani wyłączności wyłącznie na wall clock. Do lokalnego pomiaru odstępów używam czasu monotonicznego; do kolejności zdarzeń wersji, sekwencji lub logicznych zegarów odpowiednich dla protokołu.

Last-write-wins według niezsynchronizowanego czasu może zgubić prawidłową aktualizację. Deadline między usługami wymaga znanego założenia o zegarach lub przekazywania pozostałego budżetu. Czas przechowuję z jednoznaczną semantyką, oddzielając Instant od lokalnej daty biznesowej.

Czas zdarzenia, przyjęcia do systemu i przetworzenia mają inne znaczenia. Urządzenie offline może wysłać wczorajsze dane dzisiaj, a zegar klienta może być błędny. Sortowanie po jednym timestampie nie tworzy automatycznie poprawnego porządku przyczynowego.

<!-- example:DIST-21-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
long started = System.nanoTime();
// operacja
long elapsedNanos = System.nanoTime() - started;
~~~

To lokalny pomiar odstępu, którego nie wysyłam jako globalnego timestampu. Przy replikacji wersje agregatu lub zegary logiczne rozwiązują inne pytanie niż „która była godzina”. Test zegara obejmuje cofnięcie, skok i opóźniony event, nie tylko stały offset.

## DIST-22. Co zmienia poziome skalowanie usługi stateful?

Kolejne instancje nie współdzielą automatycznie sesji, map, schedulerów i locków JVM. Potrzebny jest zewnętrzny stan albo partycjonowanie z jawnym ownershipem i migracją. Sticky sessions pomagają routować, ale nie gwarantują odzyskania stanu po awarii.

Stateless API upraszcza skalowanie, lecz bottleneck może pozostać w DB i wspólnej zależności. W planie uwzględniam replikację, rebalance, ciepły start cache i przejęcie po crashu. Każde dodatkowe podwojenie instancji mnoży lokalne pule i limity.

Stan sesji w lokalnej mapie znika po śmierci poda i nie jest widoczny dla sąsiedniej repliki. Sticky routing może maskować brak współdzielenia, dopóki klient nie trafi po failover gdzie indziej. Dla stateful ownerów trzeba zaplanować transfer i odtworzenie, a nie jedynie więcej replik Deployment.

Po scale-out nowe instancje równocześnie rozgrzewają cache i otwierają pule. Może to przeciążyć DB mimo niższego CPU każdego poda. Testuję cold start, utratę właściciela i rebalancing. Dla stateless API nadal ważna jest trwałość pracy, której przyjęcie już potwierdzono użytkownikowi.

## DIST-23. Czym różnią się forward proxy, reverse proxy, load balancer, gateway i discovery?

Forward proxy pośredniczy po stronie klientów; reverse proxy reprezentuje serwery. Load balancer rozdziela ruch, gateway dodaje polityki API, a discovery odnajduje adresy zdrowych instancji. Jedno narzędzie może pełnić kilka ról, lecz pojęcia pozostają różne.

Przepływ może obejmować DNS/TLS, uwierzytelnienie, limit, routing, LB i upstream z deadline; kolejność zależy od konfiguracji. Discovery wykorzystuje rejestr/heartbeat albo DNS, np. Service w Kubernetes. Zdrowie oceniam przez sondy aktywne/pasywne, pamiętając o opóźnieniu wykrycia i fałszywych alarmach.

Aktywna sonda odpytuje endpoint, pasywna obserwuje błędy rzeczywistego ruchu. Obie mają opóźnienie i fałszywe sygnały: jedna wolna odpowiedź nie musi oznaczać śmierci węzła, a lekki health endpoint może działać mimo zatkanej puli biznesowej.

Gateway powinien usuwać lub nadpisywać niezaufane nagłówki tożsamości, jeśli downstream ma im ufać. Discovery ma cache, TTL i stan nieświeży, więc serwis może jeszcze przez chwilę próbować martwego adresu. Deadline, retry i polityka zdrowia muszą współpracować; sama rejestracja w discovery nie jest gwarancją gotowości.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [implementation](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_a/implementation/README.md)
- [failure semantics](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/failure_semantics/README.md)
- [consistency](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/nosql/consistency/README.md)
<!-- labs:end -->
