# Stage 3C — cloud architecture i disaster recovery

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `temat-zaawansowany`
> - **Uczy:** Stage 3C — cloud architecture i disaster recovery.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Stage 3C — cloud architecture i disaster recovery” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Model weryfikuje nazwany niezmiennik; nie implementuje produkcyjnego protokołu rozproszonego ani infrastruktury dostawcy.
<!-- material-card:end -->

## Blok C – Architektura Cloud na Google Cloud Platform



## Wprowadzenie

Ten dokument łączy działającą aplikację cloud-native z wykonywalnym laboratorium decyzji operacyjnych. Nie wymaga konta GCP: kod modeluje kontrakty, które przed produkcją powinny zostać odwzorowane w wybranym narzędziu IaC, monitoringu i runbookach. Głównym celem jest projektowanie systemów skalowalnych, odpornych na awarie, możliwych do odtworzenia oraz efektywnych kosztowo.

## Uruchomienie laboratorium

`block_c` jest samodzielnym modułem Maven wymagającym Javy 21. Nie jest budowany przez nadrzędny `backend-engineering/pom.xml`.

```shell
cd backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_c
mvn --batch-mode --no-transfer-progress verify
```

Do uruchomienia aplikacji potrzebne są PostgreSQL i Redis oraz następujące zmienne:

| Zmienna | Znaczenie | Przykład |
|---|---|---|
| `DB_JDBC_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/cloud_architecture` |
| `DB_USER` | użytkownik bazy | `app` |
| `DB_PASSWORD` | hasło pobierane docelowo z Secret Manager | `local-password` |
| `DB_POOL_SIZE` | maksymalna pula połączeń jednej instancji | `5` |
| `SPRING_DATA_REDIS_HOST` | adres Redis/Memorystore | `localhost` |
| `SPRING_DATA_REDIS_PORT` | port Redis | `6379` |
| `EXTERNAL_API_BASE_URL` | bazowy URL zależności HTTP | `https://api.example.com` |
| `EXTERNAL_API_TIMEOUT` | connect i read timeout | `3s` |

Flyway tworzy schemat przy starcie, a Hibernate działa w trybie `validate`. Obraz można zbudować poleceniem `docker build -t cloud-architecture-lab .`. Endpoint `/health` sprawdza wyłącznie proces, natomiast `/ready` odpytuje PostgreSQL i Redis.

Najważniejsze zaimplementowane przepływy:

- cache-aside produktów z kontrolowaną degradacją przy awarii Redis,
- atomowy fixed-window rate limiter oparty na skrypcie Lua,
- idempotencja requestów ze stanami `PROCESSING` i `COMPLETED` oraz fingerprintem payloadu,
- Transactional Outbox z retry, backoffem, limitem prób i `FOR UPDATE SKIP LOCKED`,
- idempotentny worker używający trwałego znacznika i klucza downstream,
- migracje Flyway, readiness oraz ustrukturyzowane logowanie requestów.
- mierzalny plan disaster recovery z RPO/RTO, backupem, restore drill i regionalnym failoverem,
- wykrywanie driftu infrastruktury oraz walidacja keyless workload identity i minimalnego IAM,
- decyzje degradacji dla Cloud SQL, Redis i Pub/Sub oraz bezpieczny rollback aplikacji i migracji.

## Wykonywalne laboratorium disaster recovery

Pakiet `operations` oddziela trzy problemy, które bywają błędnie wrzucane do jednego worka:

| Problem | Pytanie | Mechanizm |
| --- | --- | --- |
| High Availability | Czy usługa przetrwa awarię instancji lub strefy bez ręcznej interwencji? | redundancja, health checks, automatyczny zonal failover |
| Disaster Recovery | Jak odtworzyć system po utracie regionu lub uszkodzeniu danych? | drugi region, backup, restore, runbook, ćwiczenia |
| Business Continuity | Które funkcje mogą działać w trybie ograniczonym? | jawna degradacja, priorytety ruchu, komunikacja incydentu |

HA zmniejsza liczbę incydentów wymagających DR, ale go nie zastępuje. Regionalna instancja Cloud SQL może chronić przed awarią strefy, lecz nie musi chronić przed błędnym `DELETE`, wadliwą migracją albo utratą całego regionu. Replika propaguje również część błędów logicznych; dlatego potrzebny jest niezależny backup i sprawdzony restore.

### RPO i RTO jako kontrakt

Referencyjny plan znajduje się w `src/main/resources/operations/reference-dr-plan.properties`:

- `RPO = 5 min` — podczas zaakceptowanego scenariusza disaster można utracić najwyżej pięć minut trwałych danych,
- `RTO = 30 min` — krytyczna ścieżka ma wrócić w ciągu trzydziestu minut,
- backup/PITR musi tworzyć punkty odtworzenia co najwyżej co pięć minut,
- kopia musi przeżyć awarię regionu podstawowego,
- estymowany traffic switch i warmup muszą mieścić się w RTO,
- restore drill odbywa się co 30 dni, a walidator odrzuca okres dłuższy niż 90 dni.

`DisasterRecoveryPlanValidator` nie pozwala uznać planu za gotowy, gdy między innymi:

- region recovery jest tym samym regionem co primary,
- interwał backupu przekracza RPO,
- czas failoveru przekracza RTO,
- brakuje PITR, kopii cross-region, odtwarzalnego IaC lub workload identity,
- runbook nie zawiera fencing/freeze writes, promocji secondary, zmiany ruchu i weryfikacji.

Wartości są przykładem wymagania biznesowego, nie uniwersalną rekomendacją. Krótsze RPO/RTO zwiększają koszt i złożoność; powinny wynikać z analizy wpływu, a nie ambicji technicznej.

### Backup i test odtworzenia

Samo `backup enabled` nie jest dowodem odzyskiwalności. `RestoreDrillEvaluator` ocenia wynik ćwiczenia na podstawie:

- wieku punktu odtworzenia względem RPO,
- czasu odtworzenia względem RTO,
- izolacji kopii od regionu podstawowego,
- wersji schematu,
- liczby rekordów i checksumy danych biznesowych,
- smoke testu aplikacji podłączonej do odtworzonej bazy.

Restore powinien odbywać się do izolowanego środowiska. Dopiero po weryfikacji danych i krytycznych ścieżek można rozważać promocję. Ćwiczenie musi zapisać realny czas, użyty snapshot, wynik integralności i odstępstwa od runbooka.

### Regionalny failover

Minimalna kolejność z referencyjnego runbooka:

1. wyznacz incident commandera i jawnie ogłoś disaster,
2. zatrzymaj lub odgrodź zapisy starego primary, aby uniknąć split-brain,
3. zmierz replication lag i porównaj go z RPO,
4. promuj bazę w regionie recovery,
5. odtwórz stateless compute, IAM, sekrety i routing z IaC,
6. przełącz ruch i weryfikuj critical journeys, nie tylko `/health`,
7. utrzymuj stary region odgrodzony do czasu zaplanowanego failbacku.

Automatyczny zonal failover jest mechanizmem HA. Regionalny failover jest decyzją o dużym blast radius i zwykle powinien mieć jawne kryteria, właściciela oraz checkpoint przed zmianą kierunku zapisu.

## Scenariusze utraty zależności

`FailureScenarioPlanner` dokumentuje różne reakcje zamiast stosowania jednego globalnego „retry”:

| Awaria | Tryb | Decyzja |
| --- | --- | --- |
| Redis | degraded | odczyty produktu omijają cache; operacje zależne od idempotencji fail closed; cache jest odbudowywany, nie odtwarzany z backupu |
| Pub/Sub | degraded | transakcja nadal zapisuje Outbox; relay zwalnia retry; po powrocie broker jest zasilany idempotentnym replayem |
| Cloud SQL | unavailable | zapisy fail closed, worker staje, następuje ocena laga i kontrolowana promocja secondary |
| region primary | unavailable | fencing starego primary, rekonstrukcja z IaC, promocja danych, traffic switch i test ścieżek biznesowych |

Redis pełni tu zarówno rolę cache, jak i magazynu idempotencji/rate limitingu. Dlatego „po prostu omiń Redis” jest poprawne dla cache, lecz nie dla funkcji wpływających na poprawność zapisu.

## IaC i wykrywanie driftu

`ReferenceOperationalArchitecture` jest niewielkim, niezależnym od providera modelem desired state. `InfrastructureDriftDetector` porównuje z nim observed state i wykrywa:

- brak zasobu zarządzanego przez IaC,
- zasób utworzony ręcznie poza desired state,
- zmianę typu lub zarządzanego atrybutu, np. publiczny ingress albo domyślne service account.

`RecoveryInfrastructureValidator` dodatkowo łączy dwa kontrakty: sprawdza, czy regiony zadeklarowane w planie DR rzeczywiście mają primary z HA/PITR oraz bazę recovery wskazującą właściwe źródło. Dzięki temu RTO nie może opierać się wyłącznie na zasobie zapisanym w runbooku, ale nieobecnym w desired state.

W realnym pipeline odpowiednikiem jest `terraform plan -detailed-exitcode`, plan Pulumi lub inny deterministyczny diff uruchamiany cyklicznie i przed wdrożeniem. Drift nie powinien być automatycznie „naprawiany” bez oceny: najpierw trzeba rozstrzygnąć, czy zmiana konsolowa była incydentem, awaryjną interwencją czy nowym desired state wymagającym review.

Stan IaC również jest daną krytyczną. Wymaga zdalnego backendu, wersjonowania, blokady, szyfrowania, ograniczonego IAM i procedury odzyskania. Sekrety nie powinny trafiać do repozytorium ani jawnego planu.

## Workload identity i minimalne IAM

`IamPolicyValidator` sprawdza cztery antywzorce:

- eksportowany, długowieczny klucz service account,
- brak uprawnienia niezbędnego workloadowi,
- uprawnienie wykraczające poza deklarowaną potrzebę,
- współdzielenie jednej tożsamości przez różne workloady.

Referencyjnie API, relay Outboxa i worker mają oddzielne service accounts. API nie potrzebuje publikowania do Pub/Sub, relay nie potrzebuje konsumowania subskrypcji, a worker nie potrzebuje zmiany polityki IAM. Produkcyjnie zestaw niskopoziomowych permissions powinien zostać zamknięty w reviewowane custom roles albo świadomie dobranych rolach predefiniowanych.

## Rollback aplikacji i migracji

`RollbackPlanner` rozróżnia rollback obrazu od cofania danych:

- po migracji `EXPAND` lub kompatybilnym `BACKFILL` poprzedni digest aplikacji może zostać wdrożony przy pozostawieniu rozszerzonego schematu,
- po `CONTRACT`, który usunął kolumnę lub zmienił znaczenie danych, automatyczny rollback starej aplikacji jest odrzucany,
- destrukcyjna down migration nie powinna być domyślnym elementem rollbacku; bezpieczniejszy jest forward fix albo restore do izolowanej bazy,
- każda decyzja kończy się weryfikacją health, error rate i krytycznej operacji biznesowej.

To jest praktyczny powód stosowania expand → migrate/backfill → contract w osobnych wdrożeniach oraz promowania obrazu po digest, a nie po mutowalnym tagu.

## Testy nowych kontraktów

| Test | Co udowadnia |
| --- | --- |
| `DisasterRecoveryPlanValidatorTest` | poprawna konfiguracja przechodzi, a plan mylący HA z DR jest odrzucany |
| `RestoreDrillEvaluatorTest` | backup jest wartościowy dopiero po spełnieniu RPO, RTO i kontroli integralności |
| `FailureScenarioPlannerTest` | każda zależność ma inną semantykę degradacji i odzyskiwania |
| `RollbackPlannerTest` | rollback obrazu jest oddzielony od niebezpiecznego rollbacku schematu |
| `InfrastructureGovernanceTest` | drift, statyczne klucze, wspólne identity i nadmiarowe IAM są wykrywane |

## Gwarancje i świadome uproszczenia laboratorium

| Obszar | Co gwarantuje kod | Czego nadal nie gwarantuje |
| --- | --- | --- |
| zapis zamówienia + Outbox | oba rekordy powstają w jednej transakcji DB | publikacji dokładnie raz; crash po publish przed oznaczeniem tworzy duplikat |
| publisher Outbox | `SKIP LOCKED` rozdziela rekordy między instancje, retry ma backoff i limit | krótkiej transakcji — przykład trzyma blokady podczas wywołania adaptera; przy większej skali lepszy jest claim/lease |
| worker | trwały znacznik deduplikuje ponowne dostarczenie, a downstream dostaje stabilny klucz | atomowości lokalnego commitu z efektem zewnętrznym; downstream również musi być idempotentny |
| idempotencja HTTP | atomowy claim Redis, fingerprint requestu, zapis i odczyt odpowiedzi, compare-and-set właściciela | atomowego commitu Redis z dowolnym efektem biznesowym; utracony wynik wymaga rekoncyliacji lub trwałej idempotencji domenowej |
| cache produktu | awaria lub zepsuty wpis degraduje się do źródła prawdy i jest logowany | ochrony bazy przed falą missów podczas długiej awarii Redis; potrzebne są limity i metryki |
| readiness | odróżnia żywy proces od instancji zdolnej obsłużyć ścieżki zależne od DB i Redis | osobnych polityk dla endpointów, dla których Redis jest jedynie opcjonalnym cache |
| logi HTTP | monotoniczny pomiar czasu, request ID i niskokardynalny route template | kompletnej korelacji trace/span, eksportu, samplingu i polityki retencji |

Redis jest w tym laboratorium jednocześnie opcjonalnym cache i krytycznym magazynem limitów oraz idempotencji. Dlatego awaria cache nie przerywa odczytu produktu, ale `/ready` uznaje Redis za zależność krytyczną dla całej instancji. W większej architekturze warto rozdzielić workloady lub readiness według klasy obsługiwanego ruchu.

Lokalny `PubSubPublisher` jest adapterem demonstracyjnym zapisującym zdarzenie w logu. Nie wysyła danych do GCP i dzięki temu testy oraz lokalny build nie wymagają konta chmurowego. Wdrożenie produkcyjne powinno podmienić ten adapter na implementację Google Cloud Pub/Sub z uwierzytelnianiem przez dedykowane service account; semantyka Outbox i idempotentnego konsumenta pozostaje taka sama.

Architektura opisana w tym bloku bazuje na założeniach zgodnych z dobrymi praktykami Google Cloud, wzorcami cloud-native oraz zasadami znanymi z Well-Architected Framework. Chociaż raport odnosi się do konkretnych usług GCP, takich jak Cloud Run, Cloud SQL, Memorystore, Pub/Sub czy Secret Manager, jego główna wartość polega na pokazaniu sposobu myślenia o systemie. Backend nie powinien być traktowany jako pojedyncza aplikacja uruchomiona na serwerze, ale jako zestaw współpracujących komponentów, z których każdy pełni jasno określoną rolę i może być skalowany, monitorowany oraz zabezpieczany niezależnie.

## Założenia architektury cloud-native

Podstawową zasadą architektury cloud-native jest bezstanowość warstwy aplikacyjnej. Oznacza to, że pojedyncza instancja aplikacji nie powinna przechowywać lokalnie informacji wymaganych do obsłużenia kolejnych żądań użytkownika. Stan powinien zostać przeniesiony do wyspecjalizowanych usług zewnętrznych, takich jak relacyjna baza danych, cache, kolejka komunikatów lub magazyn obiektowy. Dzięki temu instancje aplikacji można swobodnie tworzyć i usuwać, a system może reagować na zmiany obciążenia bez ryzyka utraty danych.

W praktyce bezstanowość jest jednym z warunków skutecznego autoskalowania. Jeżeli aplikacja nie jest powiązana z lokalnym stanem konkretnej maszyny lub kontenera, platforma może uruchamiać dodatkowe instancje wtedy, gdy ruch rośnie, oraz usuwać je, gdy zapotrzebowanie spada. Taki model jest szczególnie dobrze dopasowany do Cloud Run, gdzie kontenery są skalowane automatycznie na podstawie liczby żądań i konfiguracji współbieżności. Architekt nie musi projektować infrastruktury pod maksymalny przewidywany ruch przez cały czas, lecz może pozwolić platformie reagować dynamicznie.

Drugim ważnym założeniem jest projektowanie systemu z myślą o awariach. W środowisku rozproszonym nie należy zakładać, że wszystkie komponenty zawsze będą dostępne. Baza danych może chwilowo odpowiadać wolniej, usługa zewnętrzna może przekroczyć limit czasu, a kolejka może dostarczyć komunikat ponownie. Dlatego aplikacja powinna obsługiwać timeouty, ponowienia, idempotencję i kontrolowane błędy. Odporność systemu nie wynika wyłącznie z infrastruktury, lecz z połączenia właściwego kodu aplikacyjnego, konfiguracji platformy i obserwowalności.

## Warstwa aplikacyjna i usługi stateless

Warstwa aplikacyjna powinna być projektowana jako zbiór usług stateless. Każda usługa przyjmuje żądanie, przetwarza je, komunikuje się z zależnościami zewnętrznymi i zwraca odpowiedź, ale nie zakłada, że kolejne żądanie trafi do tej samej instancji. Taki model upraszcza skalowanie poziome, ponieważ zwiększenie przepustowości systemu polega na uruchomieniu większej liczby identycznych instancji.

Istotnym elementem takiej architektury są mechanizmy health check i readiness check. Health check odpowiada na pytanie, czy aplikacja jako proces działa poprawnie, natomiast readiness check określa, czy instancja jest gotowa do przyjmowania ruchu. Różnica jest ważna, ponieważ aplikacja może być uruchomiona, ale jeszcze niegotowa, na przykład z powodu braku połączenia z bazą danych, niewczytanych sekretów lub trwającej inicjalizacji. Poprawne rozdzielenie tych mechanizmów pozwala platformie kierować ruch wyłącznie do instancji faktycznie zdolnych do obsługi żądań.

Konteneryzacja aplikacji, najczęściej przez Dockerfile, pełni w tym modelu rolę standaryzacji środowiska uruchomieniowego. Obraz kontenera powinien być możliwie mały, deterministyczny i bezpieczny. W praktyce oznacza to unikanie zbędnych pakietów, korzystanie z wieloetapowego budowania obrazu oraz uruchamianie procesu aplikacji w sposób przewidywalny. Kontener nie powinien być traktowany jako mała maszyna wirtualna, lecz jako przenośny artefakt aplikacyjny.

## Cloud Run jako środowisko uruchomieniowe

Cloud Run jest naturalnym wyborem dla wielu backendów HTTP, ponieważ łączy prostotę wdrażania kontenerów z automatycznym skalowaniem i modelem płatności zależnym od użycia. W architekturze opartej na Cloud Run szczególne znaczenie mają parametry autoskalowania: minimalna liczba instancji, maksymalna liczba instancji oraz współbieżność, czyli liczba żądań obsługiwanych równocześnie przez jedną instancję.

Minimalna liczba instancji wpływa głównie na ograniczenie cold startów i zapewnienie podstawowej gotowości usługi. Zbyt wysoka wartość może jednak generować niepotrzebny koszt, zwłaszcza gdy ruch jest nieregularny. Maksymalna liczba instancji pełni funkcję bezpiecznika kosztowego i technicznego. Chroni system przed niekontrolowanym wzrostem liczby kontenerów, ale jednocześnie może stać się ograniczeniem przepustowości. Współbieżność wymaga szczególnie świadomego dobrania, ponieważ zbyt wysoka wartość może przeciążać pojedynczą instancję, a zbyt niska prowadzić do nadmiernego skalowania i wyższych kosztów.

Cloud Run dobrze wspiera model usług stateless, ale nie rozwiązuje automatycznie wszystkich problemów architektonicznych. Aplikacja nadal musi prawidłowo obsługiwać limity czasu, sygnały zakończenia procesu, bezpieczne zamykanie połączeń i błędy zależności. Platforma może uruchomić więcej instancji, lecz jeżeli baza danych, cache lub zewnętrzne API nie są przygotowane na większy ruch, autoskalowanie warstwy aplikacyjnej może jedynie przenieść problem w inne miejsce.

## Warstwa danych: Cloud SQL, pooling i repliki odczytowe

Relacyjna baza danych pozostaje jednym z najważniejszych elementów backendu. W GCP typowym wyborem jest Cloud SQL lub, przy większych wymaganiach skalowalności i dostępności, AlloyDB. Ponieważ baza danych jest komponentem stanowym, jej skalowanie i optymalizacja wymagają większej ostrożności niż w przypadku warstwy aplikacyjnej.

Jednym z najczęstszych problemów w architekturach kontenerowych jest nadmierna liczba połączeń do bazy danych. Gdy Cloud Run dynamicznie zwiększa liczbę instancji, każda z nich może otwierać własną pulę połączeń. Bez kontroli prowadzi to do szybkiego wyczerpania limitów bazy danych. Dlatego pooling połączeń powinien być traktowany jako element architektury, a nie jedynie detal implementacyjny. Rozmiar puli musi uwzględniać maksymalną liczbę instancji, współbieżność oraz faktyczne zapotrzebowanie aplikacji na operacje bazodanowe.

Read-replicas są przydatne, gdy system generuje dużo zapytań odczytowych, które nie muszą zawsze trafiać do instancji głównej. Pozwalają one odciążyć bazę primary i poprawić skalowalność odczytów. Nie są jednak rozwiązaniem uniwersalnym. Repliki mogą mieć opóźnienie względem głównej bazy, dlatego nie powinny być używane w miejscach, gdzie wymagana jest natychmiastowa spójność po zapisie. Dobre wykorzystanie replik wymaga rozumienia wzorców dostępu do danych i świadomego rozdzielenia zapytań odczytowych od zapisów.

Optymalizacja bazy danych zaczyna się zwykle od indeksów, analizy zapytań i obserwacji realnego obciążenia. Skalowanie pionowe bazy, dokładanie replik lub przejście na droższy wariant usługi powinno być poprzedzone sprawdzeniem, czy problem nie wynika z nieefektywnych zapytań, braku indeksów lub nadmiernej liczby operacji wykonywanych synchronicznie w ścieżce żądania użytkownika.

## Cache-aside i Memorystore

Cache jest jednym z najważniejszych narzędzi ograniczania opóźnień i kosztów, ale wymaga dyscypliny projektowej. Wzorzec cache-aside polega na tym, że aplikacja najpierw próbuje odczytać dane z cache, a dopiero w przypadku braku trafienia pobiera je z bazy danych i zapisuje w cache na przyszłość. W GCP taką rolę może pełnić Memorystore, na przykład w wariancie Redis.

Zaletą cache-aside jest prostota i kontrola po stronie aplikacji. Programista jasno określa, które dane są cache'owane, przez jaki czas i w jaki sposób są odświeżane. Wadą jest konieczność poprawnego zarządzania inwalidacją. Jeżeli dane w bazie zostaną zmienione, a cache nadal przechowuje starą wartość, użytkownik może otrzymać nieaktualną odpowiedź. Dlatego TTL, usuwanie kluczy po zapisie oraz projektowanie kluczy cache powinny być traktowane jako część modelu danych.

Cache nie powinien być używany bezrefleksyjnie. Największą wartość daje tam, gdzie dane są często odczytywane, stosunkowo rzadko zmieniane i kosztowne do pobrania lub przeliczenia. W przypadku danych silnie dynamicznych źle zaprojektowany cache może zwiększyć złożoność systemu bardziej, niż poprawić jego wydajność. Trzeba też pamiętać, że cache jest zależnością zewnętrzną. Aplikacja powinna zachować kontrolowane działanie również wtedy, gdy cache jest chwilowo niedostępny.

## Przetwarzanie asynchroniczne i Pub/Sub

Nie wszystkie operacje powinny być wykonywane synchronicznie w czasie obsługi żądania użytkownika. Wysyłka wiadomości e-mail, generowanie raportów, aktualizacja indeksów wyszukiwania, przetwarzanie obrazów lub integracje z wolniejszymi systemami zewnętrznymi często lepiej nadają się do przetwarzania asynchronicznego. W GCP typowym mechanizmem komunikacji zdarzeniowej jest Pub/Sub.

Pub/Sub pozwala oddzielić producenta zdarzenia od konsumenta. Usługa obsługująca żądanie może opublikować komunikat i szybko zwrócić odpowiedź użytkownikowi, a dalsze przetwarzanie wykona osobny worker. Taki podział zwiększa odporność systemu, ponieważ chwilowe spowolnienie jednego komponentu nie musi blokować całej ścieżki użytkownika. Ułatwia też skalowanie, ponieważ konsumenci komunikatów mogą być skalowani niezależnie od API.

Architektura asynchroniczna wymaga jednak poprawnej obsługi powtórzeń i idempotencji. Komunikat może zostać dostarczony więcej niż raz, a konsument może przerwać działanie w połowie operacji. Dlatego operacje wykonywane przez workery powinny być projektowane tak, aby ich ponowne uruchomienie nie prowadziło do błędnego stanu, podwójnych płatności, wielokrotnych e-maili lub niespójnych zapisów. Klucze idempotencyjne i jawne śledzenie statusu przetwarzania są w takim modelu nie dodatkiem, lecz wymogiem poprawności.

Przykładowy `OrderService` realizuje Transactional Outbox: zapisuje zamówienie i rekord `outbox_events` w jednej transakcji. `OrderOutboxPublisher` niezależnie pobiera nieopublikowane rekordy, wysyła je do Pub/Sub i oznacza czas publikacji. Mechanizm ma semantykę at-least-once — awaria po wysłaniu komunikatu, ale przed oznaczeniem rekordu, może spowodować ponowną publikację. Konsument nadal musi więc deduplikować komunikaty. W instalacji z wieloma publisherami należy dodatkowo zastosować bezpieczne przejmowanie rekordów, na przykład blokadę `FOR UPDATE SKIP LOCKED` albo jawny lease.

`OrderWorker` zapisuje trwały znacznik przetworzenia i przekazuje do zależności downstream deterministyczny klucz `order-created:<orderId>`. Samo lokalne sprawdzenie znacznika nie wystarczy do zapewnienia exactly-once: proces może zakończyć się po wykonaniu zewnętrznego efektu, ale przed lokalnym commitem. Z tego powodu odbiorca efektu, na przykład serwis faktur, również musi respektować klucz idempotencyjny.

## Idempotencja, limity i kontrola przeciążenia

Idempotencja oznacza, że wielokrotne wykonanie tej samej operacji daje taki sam efekt jak wykonanie jej raz. W systemach chmurowych jest to szczególnie ważne, ponieważ ponowienia żądań, timeouty i dostarczanie komunikatów więcej niż raz są normalnym elementem działania infrastruktury rozproszonej. Dla operacji zmieniających stan, takich jak tworzenie zamówienia, płatność lub rezerwacja zasobu, warto stosować klucze idempotencyjne przekazywane przez klienta albo generowane na poziomie systemu.

Rate limiting chroni system przed nadmiernym ruchem, nadużyciami oraz przypadkowymi pętlami po stronie klientów. Nie chodzi wyłącznie o bezpieczeństwo, ale także o stabilność i koszty. Bez limitów pojedynczy klient lub błędna integracja może doprowadzić do gwałtownego wzrostu liczby instancji, zapytań do bazy i kosztów infrastruktury. Dobrze zaprojektowany limit powinien być zależny od typu użytkownika, rodzaju operacji i kosztu technicznego danego żądania.

Kontrola przeciążenia powinna być projektowana warstwowo. Cloud Run może ograniczyć maksymalną liczbę instancji, API Gateway lub load balancer mogą egzekwować reguły ruchu, aplikacja może stosować własne limity biznesowe, a baza danych i cache powinny mieć jasno określone granice obciążenia. Brak takich ograniczeń często ujawnia się dopiero podczas awarii lub nagłego wzrostu popularności usługi.

## Obserwowalność: logi, metryki i latency

System cloud-native powinien być projektowany tak, aby jego działanie można było zrozumieć na podstawie danych operacyjnych. Obserwowalność nie polega jedynie na zapisywaniu logów, lecz na możliwości odpowiedzi na pytania: co się dzieje, gdzie występuje opóźnienie, która zależność zawodzi, jaki jest wpływ błędu na użytkowników i ile kosztuje obsługa danego ruchu.

Logowanie strukturalne jest znacznie bardziej użyteczne niż zwykłe komunikaty tekstowe. Log powinien zawierać informacje takie jak identyfikator żądania, użytkownik lub tenant, nazwa operacji, kod odpowiedzi, czas trwania, identyfikator komunikatu lub klucz idempotencyjny. Dzięki temu logi można filtrować, agregować i korelować z metrykami. W środowisku rozproszonym szczególnie ważne jest śledzenie przepływu jednego żądania przez wiele komponentów.

Metryki latency powinny być analizowane nie tylko przez średnią, ale także przez percentyle, na przykład p95 lub p99. Średnia potrafi ukrywać problemy dotykające mniejszą, ale istotną grupę użytkowników. Oprócz opóźnień warto monitorować liczbę błędów, nasycenie zasobów, wykorzystanie połączeń do bazy danych, trafienia cache, liczbę komunikatów w kolejce oraz koszt. Dopiero połączenie tych danych pozwala ocenić, czy system jest technicznie zdrowy i ekonomicznie uzasadniony.

## Bezpieczeństwo i zarządzanie sekretami

Bezpieczeństwo w architekturze GCP powinno opierać się na zasadzie najmniejszych uprawnień. Usługi powinny korzystać z dedykowanych kont serwisowych, które mają tylko te role, które są niezbędne do wykonania konkretnej pracy. Nadawanie szerokich uprawnień na wszelki wypadek jest wygodne w krótkim terminie, ale zwiększa ryzyko naruszenia bezpieczeństwa i utrudnia audyt.

Sekrety, takie jak hasła, tokeny API czy dane dostępowe, nie powinny być przechowywane w kodzie źródłowym, obrazach kontenerów ani zwykłych zmiennych konfiguracyjnych bez kontroli dostępu. Do tego celu należy stosować usługę Secret Manager. Aplikacja powinna odczytywać sekrety w sposób kontrolowany, a dostęp do nich powinien być ograniczony do konkretnych usług. Rotacja sekretów powinna być możliwa bez przebudowy całego systemu.

Ważnym elementem bezpieczeństwa jest również konfiguracja sieciowa. Prywatny dostęp do Cloud SQL, użycie VPC, ograniczanie publicznej ekspozycji usług oraz odpowiednie reguły ingress i egress zmniejszają powierzchnię ataku. CDN może dodatkowo poprawić wydajność i ograniczyć obciążenie backendu przy treściach statycznych lub często odczytywanych zasobach, ale nie zastępuje kontroli dostępu ani walidacji po stronie aplikacji.

## FinOps i optymalizacja kosztów

Architektura chmurowa powinna być oceniana nie tylko przez pryzmat dostępności i wydajności, ale także kosztu dostarczenia wartości biznesowej. Model płatności za użycie jest korzystny wtedy, gdy system rzeczywiście skaluje się zgodnie z ruchem i nie utrzymuje niepotrzebnego zapasu. Źle dobrane minimalne instancje, nadmierne pule połączeń, zbyt duże bazy danych, nieużywane repliki lub brak limitów autoskalowania mogą prowadzić do kosztów nieproporcjonalnych do korzyści.

FinOps nie oznacza prostego cięcia kosztów. Chodzi o świadome zarządzanie relacją między kosztem, wydajnością, niezawodnością i wartością biznesową. Tani system, który nie obsługuje ruchu lub często zawodzi, nie jest dobrze zoptymalizowany. Podobnie system technicznie poprawny, ale stale utrzymujący nadmiarowe zasoby bez uzasadnienia, wymaga dostrojenia.

W praktyce należy regularnie analizować wykorzystanie zasobów, trendy kosztowe i miejsca marnotrawstwa. Szczególną uwagę warto zwracać na zasoby działające bez ruchu, usługi przewymiarowane, dane przechowywane bez polityki retencji, niewykorzystywane adresy IP, nadmiarowe środowiska testowe i zbyt agresywne ustawienia skalowania minimalnego. Architektura powinna być projektowana tak, aby koszt rósł proporcjonalnie do realnego użycia, a nie do pesymistycznych założeń projektowych.

## Typowy przepływ żądania w proponowanej architekturze

W typowym scenariuszu użytkownik wysyła żądanie do usługi HTTP działającej na Cloud Run. Platforma kieruje ruch do dostępnej instancji albo uruchamia nową, jeżeli aktualna przepustowość jest niewystarczająca. Aplikacja wykonuje walidację, sprawdza limity, odczytuje potrzebne dane z cache lub bazy danych, a następnie zwraca odpowiedź. Jeżeli operacja wymaga dłuższego przetwarzania, aplikacja publikuje komunikat do Pub/Sub, a osobny worker realizuje zadanie asynchronicznie.

W tym przepływie każda warstwa ma jasno określoną odpowiedzialność. Cloud Run odpowiada za uruchamianie i skalowanie kontenerów, Cloud SQL za trwałe dane relacyjne, Memorystore za szybki dostęp do często odczytywanych danych, Pub/Sub za komunikację asynchroniczną, Secret Manager za sekrety, a narzędzia obserwowalności za wgląd w działanie systemu. Dobrze zaprojektowana aplikacja nie ukrywa tych zależności, lecz świadomie je kontroluje.

## Najważniejsze kryteria poprawnej architektury

Poprawna architektura cloud na GCP powinna być skalowalna, ale skalowalność nie może oznaczać nieograniczonego wzrostu kosztów. Powinna być odporna, ale odporność nie może polegać wyłącznie na nadmiarowości. Powinna być szybka, ale optymalizacja wydajności nie powinna prowadzić do niekontrolowanej złożoności. Powinna być bezpieczna, ale bezpieczeństwo musi być częścią codziennego procesu, a nie osobną warstwą dodaną na końcu.

W praktyce dobrą architekturę można rozpoznać po tym, że jej komponenty są jasno rozdzielone, usługi są stateless, zależności są monitorowane, błędy są obsługiwane jawnie, koszty są mierzone, a skalowanie odbywa się na podstawie realnego ruchu. Jeżeli system działa poprawnie technicznie, ale stale generuje koszt bez proporcjonalnej wartości, nie jest jeszcze dobrze zaprojektowany. Jeżeli system jest tani, ale nie ma obserwowalności, idempotencji i odporności na awarie, również nie spełnia standardów architektury produkcyjnej.

## Podsumowanie

Nowoczesny backend na Google Cloud Platform powinien być budowany jako system rozproszony oparty na usługach zarządzanych, automatycznym skalowaniu i świadomym zarządzaniu stanem. Cloud Run pozwala uprościć uruchamianie usług kontenerowych, ale wymaga poprawnego projektowania aplikacji stateless. Cloud SQL zapewnia wygodną bazę relacyjną, ale wymaga kontroli połączeń, indeksów i świadomego użycia replik. Memorystore może znacząco poprawić wydajność, ale tylko wtedy, gdy cache jest poprawnie inwalidowany. Pub/Sub ułatwia przetwarzanie asynchroniczne, lecz wymaga idempotencji i kontroli ponowień.

Najważniejszy wniosek jest praktyczny: dobra architektura cloud nie polega na użyciu jak największej liczby usług chmurowych, ale na właściwym dobraniu odpowiedzialności między aplikacją a platformą. Usługi zarządzane zmniejszają ciężar operacyjny, autoskalowanie pozwala dopasować zasoby do ruchu, a obserwowalność i FinOps pozwalają utrzymać system pod kontrolą. Projektując backend w GCP, należy stale równoważyć niezawodność, wydajność, bezpieczeństwo i koszt, ponieważ dopiero ich połączenie tworzy architekturę gotową do realnego użycia produkcyjnego.
