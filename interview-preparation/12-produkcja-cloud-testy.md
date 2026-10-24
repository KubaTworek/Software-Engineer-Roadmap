# Produkcja, cloud i testowanie

[Spis rozdziałów](README.md)

<!-- question-toc:start -->
## Spis pytań

- [OPS-01. Jak diagnozować API, które zwolniło z 100–200 ms do kilku sekund?](#ops-01-jak-diagnozować-api-które-zwolniło-z-100200-ms-do-kilku-sekund)
- [OPS-02. SQL trwa 50 ms, odpowiedź 2 s, CPU i pamięć są normalne. Gdzie szukać?](#ops-02-sql-trwa-50-ms-odpowiedź-2-s-cpu-i-pamięć-są-normalne-gdzie-szukać)
- [OPS-03. Działa lokalnie lub na stagingu, a produkcja zwraca 500. Co porównać?](#ops-03-działa-lokalnie-lub-na-stagingu-a-produkcja-zwraca-500-co-porównać)
- [OPS-04. System działa dla 100 użytkowników, ale nie dla 5000 lub 100 000. Jak znaleźć granicę skali?](#ops-04-system-działa-dla-100-użytkowników-ale-nie-dla-5000-lub-100-000-jak-znaleźć-granicę-skali)
- [OPS-05. Jak oszacować 10K RPS, 100K żądań/minutę albo milion transakcji na godzinę?](#ops-05-jak-oszacować-10k-rps-100k-żądańminutę-albo-milion-transakcji-na-godzinę)
- [OPS-06. Jak chronić backend po powrocie z 10-minutowej awarii przed reconnect storm?](#ops-06-jak-chronić-backend-po-powrocie-z-10-minutowej-awarii-przed-reconnect-storm)
- [OPS-07. Co robić, gdy DB jest niedostępna przez 30 sekund?](#ops-07-co-robić-gdy-db-jest-niedostępna-przez-30-sekund)
- [OPS-08. Jak śledzić jeden request przez sześć lub piętnaście usług?](#ops-08-jak-śledzić-jeden-request-przez-sześć-lub-piętnaście-usług)
- [OPS-09. Jak zaprojektować observability dużej platformy Java?](#ops-09-jak-zaprojektować-observability-dużej-platformy-java)
- [OPS-10. Co oznacza 99,99% dostępności i jak projektować failover?](#ops-10-co-oznacza-9999-dostępności-i-jak-projektować-failover)
- [OPS-11. Jak Netflix ograniczał single points of failure i czego nie wolno z tego wywnioskować?](#ops-11-jak-netflix-ograniczał-single-points-of-failure-i-czego-nie-wolno-z-tego-wywnioskować)
- [OPS-12. Blue-green, canary czy rolling update? Jak bezpiecznie cofnąć regresję?](#ops-12-blue-green-canary-czy-rolling-update-jak-bezpiecznie-cofnąć-regresję)
- [OPS-13. Jak zmieniać schemat bez downtime, także przy sharding migration?](#ops-13-jak-zmieniać-schemat-bez-downtime-także-przy-sharding-migration)
- [OPS-14. Jak wygląda pipeline od Docker image do aplikacji na AWS?](#ops-14-jak-wygląda-pipeline-od-docker-image-do-aplikacji-na-aws)
- [OPS-15. Pipeline padł w połowie. Gdzie sprawdzić stan i logi?](#ops-15-pipeline-padł-w-połowie-gdzie-sprawdzić-stan-i-logi)
- [OPS-16. Jak działają Kubernetes, health checks i self-healing? Jak zabezpieczyć health URL?](#ops-16-jak-działają-kubernetes-health-checks-i-self-healing-jak-zabezpieczyć-health-url)
- [OPS-17. Jak EC2 uzyskuje dostęp do S3 bez zapisanych kluczy?](#ops-17-jak-ec2-uzyskuje-dostęp-do-s3-bez-zapisanych-kluczy)
- [OPS-18. Jaki jest maksymalny rozmiar pojedynczego obiektu S3?](#ops-18-jaki-jest-maksymalny-rozmiar-pojedynczego-obiektu-s3)
- [OPS-19. Mockito `@Mock` czy `@Spy`? Jak rozumieć coverage i SonarQube?](#ops-19-mockito-mock-czy-spy-jak-rozumieć-coverage-i-sonarqube)
- [OPS-20. Jakie strategie branchowania i branch protection dobrać?](#ops-20-jakie-strategie-branchowania-i-branch-protection-dobrać)
<!-- question-toc:end -->

## OPS-01. Jak diagnozować API, które zwolniło z 100–200 ms do kilku sekund?

Potwierdzam zakres: endpoint, region, tenant, wersja, percentyle i początek problemu. Rozbijam czas na kolejki, security, połączenia, SQL, downstream, serializację i sieć. Szukam korelacji z ruchem, deployem, danymi, cache i zmianą zależności, także gdy nie było wdrożenia aplikacji.

Stawiam jedną hipotezę na podstawie trace/profilu, wprowadzam bezpieczną zmianę i porównuję wynik. Długie czasy bez błędów w logach często oznaczają oczekiwanie. Najpierw ograniczam wpływ incydentu, zamiast wykonywać na produkcji nieograniczony eksperyment.

Pierwsze porównanie robię dla tej samej operacji i porównywalnego ruchu: globalna średnia może spaść, gdy wolny endpoint zaczął timeoutować i wypada z zestawienia sukcesów. Rozdzielam request time od czasu w serwisie i odczytuję histogramy, nie tylko pojedyncze logi.

Hipoteza musi wskazywać dowód, np. wzrósł acquisition time DB bez zmiany czasu SQL. Mitigation może wtedy ograniczyć concurrency albo zatrzymać background job, zanim zakończę analizę. Po poprawce sprawdzam p99, błędy i backlog oraz możliwe skutki dla danych. „Po restarcie szybciej” to obserwacja, nie kompletna przyczyna.

## OPS-02. SQL trwa 50 ms, odpowiedź 2 s, CPU i pamięć są normalne. Gdzie szukać?

Sprawdzam acquisition time puli, lock waits, DNS, connect/TLS, odczyt socketu, kolejkę executora, zewnętrzne wywołania, serializację i czas po stronie proxy/klienta. Pomiar samego wykonania SQL może nie obejmować czekania na połączenie i przesłania wyniku.

Normalne średnie CPU nie wyklucza pojedynczego gorącego rdzenia ani throttlingu. Tracing pokazuje ścieżkę, profil wall-clock/thread dumps oczekiwanie. Nie zwiększam puli na podstawie samego niskiego CPU; zasób blokujący może znajdować się poza procesem.

Thread dump pokazujący większość wątków w oczekiwaniu na HTTP i normalny CPU wskazuje inny kierunek niż profile CPU pełne JSON serialization. Podobnie 50 ms SQL może być mierzone dopiero po 1500 ms czekania na connection, więc serwis raportuje „szybką bazę”.

Sprawdzam rozkład czasu w trace i instrumentację brakujących odcinków. Użycie connection powinno mieć początek i koniec obejmujące całą transakcję. Dla sieci oddzielam DNS, connect/TLS, write i read. Uśredniona metryka całej puli lub hosta może ukryć jedną instancję i jeden gorący zasób.

[Przeczytaj przykładowy materiał diagnostyczny](DIAGNOSTYKA.md#diag-04-szybki-sql-i-wolny-request).

## OPS-03. Działa lokalnie lub na stagingu, a produkcja zwraca 500. Co porównać?

Sprawdzam wersję obrazu, runtime, właściwości/profil, uprawnienia, DNS/TLS, limity, schemat bazy, wielkość danych i dostępność zależności. Odtwarzam konkretny request i koreluję błąd bez ujawniania danych użytkownika. „Ten sam kod” nie oznacza tego samego środowiska.

Nie włączam publicznego debug output i nie kopiuję produkcyjnych sekretów do lokalnego repo. Dla LazyInitializationException sprawdzam granice sesji/OSIV; dla 500 z filtra konfigurację security. Naprawę potwierdzam na reprezentatywnym scenariuszu i kontrolowanym rolloucie.

Tworzę krótką macierz różnic: obraz/digest, JDK, profile, schema, dane, IAM, sieć i limity. Kolejne sprawdzenie powinno eliminować konkretną hipotezę. Jeśli production ma dużo większą liczbę pozycji zamówienia, problem może dotyczyć pamięci lub N+1, choć konfiguracja jest identyczna.

<!-- example:OPS-03-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
kubectl describe pod <pod>
kubectl logs <pod> -c <container> --previous
~~~

To przykładowe polecenia do uruchomienia we właściwym kontekście klastra. Sprawdzam też, czy request trafia do oczekiwanej wersji. Hotfix „dodaj catch i zwróć pustą listę” może zamaskować awarię i uszkodzić decyzję klienta.

## OPS-04. System działa dla 100 użytkowników, ale nie dla 5000 lub 100 000. Jak znaleźć granicę skali?

Liczba użytkowników nie wystarczy: ustalam aktywność, RPS, czas żądania, rozmiar payloadu i mix operacji. Stopniowo zwiększam obciążenie, obserwując moment wzrostu kolejki, p99, błędów i saturation konkretnego zasobu. Test zawiera też spike i długi soak.

Model otwarty pomaga wykryć degradację, którą zamknięta liczba klientów może ukryć przez spadek wysyłanego RPS. Sprawdzam generator i coordinated omission. Po znalezieniu bottlenecku zmieniam jeden element i mierzę ponownie.

Test closed-loop z ustaloną liczbą klientów może sam ograniczać RPS, gdy usługa zwalnia, ukrywając przeciążenie. Test open-loop utrzymuje zadany napływ, ale wymaga wydajnego generatora i dokładnego raportowania odrzuconych prób. Oba modele odpowiadają na inne pytania.

Planuję warm-up, narastanie, peak, spike i recovery, z kryteriami przerwania. Wynik obejmuje throughput ukończony w SLO, nie tylko przyjęty do kolejki. Test danych reprezentuje rozkład tenantów i hot keys. Dodanie instancji pomaga dopiero, gdy wąskie gardło leży w skalowanej warstwie, a nie we wspólnej DB.

## OPS-05. Jak oszacować 10K RPS, 100K żądań/minutę albo milion transakcji na godzinę?

100K/min to około 1667/s, a milion/h około 278/s średnio; szczyt może być wielokrotnie większy. Przy stabilnych warunkach in-flight jest w przybliżeniu iloczynem napływu i czasu obsługi. Liczę też fan-out, operacje DB i rozmiar transferu.

Skalowanie pionowe daje więcej zasobów pojedynczej instancji, poziome więcej instancji i koszty koordynacji. Potrzebne są headroom, limit zależności i test granicy. Milion użytkowników lub milion połączeń nie jest tożsame z milionem aktywnych requestów na sekundę.

Dla 1667 requestów/s i średniego czasu 120 ms stabilny system ma około 200 żądań w toku. Gdy czas rośnie do 2 s przy tym samym napływie, to już około 3334. Każde trzyma pamięć i może fan-outować do kilku zależności.

Następnie przeliczam ruch na realną pracę: dwa SQL/request to inny koszt niż dziesięć aktualizacji i zapis pliku. Dodaję peak factor i zapas po utracie strefy. Te szacunki wyznaczają hipotezę testu, nie zastępują pomiaru. Wszystkie jednostki zapisuję jawnie, żeby nie pomylić requestów na minutę z sekundą.

## OPS-06. Jak chronić backend po powrocie z 10-minutowej awarii przed reconnect storm?

Klienci stosują exponential backoff z jitter i rosnący odstęp reconnect; serwer ogranicza tempo uwierzytelnienia i aktywną pracę. Wznawianie sesji, bezpieczny cache i kolejka oczekiwania mogą ograniczyć koszt, ale mają własny kontrakt bezpieczeństwa.

Uruchamiam ruch stopniowo, pilnuję połączeń DB i retry budget, wysyłam Retry-After oraz odrzucam nadmiar. Nie polegam wyłącznie na autoscalingu auth, bo wspólna baza nadal może być bottleneckiem. Testuję również ponowną awarię w trakcie recovery.

Serwer może wrócić zdrowy przy małej próbce, a zaraz potem paść od zsynchronizowanych klientów. Rozkład reconnect musi więc istnieć również po stronie aplikacji mobilnych, SDK i socketów, nie tylko w retry serwisowym.

Przy auth ograniczam kosztowne odczyty i odświeżenia sesji, ale nie omijam weryfikacji ważności tokena dla wygody. Recovery powinno mieć stopniowy udział ruchu, limit tworzenia połączeń i chronione krytyczne ścieżki. Testuję falę wielu klientów po dłuższej przerwie oraz zachowanie, gdy część dostaje 429/503 i próbuje ponownie.

## OPS-07. Co robić, gdy DB jest niedostępna przez 30 sekund?

Ograniczam czas oczekiwania i liczbę prób, szybko kończę operacje bez bezpiecznej alternatywy. Odczyt może użyć oznaczonego starego cache, jeśli domena pozwala. Zapis przyjmuję asynchronicznie tylko do trwałego bufora z jasnym statusem, bez obiecywania commitu.

Po powrocie uruchamiam ograniczony ramp-up i backlog, żeby nie wywołać drugiej awarii. Operacje o wyniku nieznanym rekoncyliuję. Restartowanie wszystkich podów z powodu wspólnej niedostępności DB może dodatkowo utrudnić odzyskanie usługi.

Dla odczytu katalogu mogę zwrócić stary snapshot z oznaczoną świeżością. Dla potwierdzenia wypłaty nie mogę zastąpić nieosiągalnej DB domyślnym sukcesem. To przykład, dlaczego strategia awarii zależy od semantyki endpointu.

Jeśli przyjmuję trwałą komendę do brokera, odpowiedź mówi „oczekuje”, a nie „zapisano w core DB”. Limit backlogu i możliwość anulowania są jawne. Po powrocie DB workerzy nadganiają z kontrolowaną concurrency, a operacje o niepewnym wyniku przechodzą przez deduplikację i sprawdzenie statusu. Samo ponowne połączenie nie kończy incydentu.

## OPS-08. Jak śledzić jeden request przez sześć lub piętnaście usług?

Trace składa się ze spanów, a kontekst propaguje się przez nagłówki HTTP/gRPC oraz metadane wiadomości. Instrumentacja OpenTelemetry i integracja executora łączą pracę także po przejściu async. Correlation ID w logach pomaga wyszukać zdarzenia, ale nie zastępuje struktury spanów.

Kontroluję sampling, zaufanie do wejściowych nagłówków i brak PII w baggage. Dla batcha lub wielu przyczyn przydają się links. Opóźnienie komunikatu i czas handlera mierzę oddzielnie. [Propagacja OpenTelemetry](https://opentelemetry.io/docs/concepts/context-propagation/).

Nagłówek traceparent przekazuje trace/span context zgodnie ze standardem, a correlation ID jest często dodatkową identyfikacją biznesowego żądania. Przy komunikacie opóźnionym o godzinę span przetwarzania może mieć link do przyczyny zamiast udawać nieprzerwaną synchroniczną operację.

<!-- example:OPS-08-1 kind:http-contract -->
**Kontekst:** Przykład kontraktu HTTP; wymaga odpowiadającego API, nie jest automatycznie wysyłanym żądaniem.

~~~http
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
~~~

To przykładowa poprawna postać, nie identyfikator do ponownego używania. Nie przyjmuję dowolnego baggage jako zaufanej roli/tenanta. Test instrumentacji obejmuje executor, HTTP client, broker i handler błędu, ponieważ zgubienie kontekstu w jednej granicy urywa całą analizę.

<!-- follow-up:OPS-08 -->
**Sprawdź rozumienie:** Czy tenant lub rola przesłane w trace baggage mogą służyć do autoryzacji?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Kontekst diagnostyczny jest danymi do korelacji, a nie zaufanym credential. Tożsamość i uprawnienia pochodzą z walidowanego kontekstu bezpieczeństwa; baggage trzeba też ograniczać pod kątem rozmiaru i PII.

</details>

## OPS-09. Jak zaprojektować observability dużej platformy Java?

Metryki opisują ruch, błędy, latency i saturation; trace pokazuje ścieżkę; strukturalne logi szczegóły zdarzenia. Dodaję metryki biznesowe, np. zamówienia PENDING i wiek outbox, żeby widzieć poprawność końcową, nie tylko HTTP 200.

Alarmuję na symptomy i tempo spalania error budget z runbookiem. Ograniczam kardynalność: user ID nie jest etykietą każdej metryki. Koszt retencji, sampling, redakcja sekretów i własna odporność pipeline observability są częścią projektu.

Histogramy latency można agregować sensownie przez buckety, natomiast średnia z lokalnych p99 nie jest globalnym p99. Liczba etykiet także mnoży serie: endpoint × status × tenant × user szybko tworzy niekontrolowany koszt.

Dla procesu zamówień śledzę osobno HTTP success, wiek PENDING, brakujące płatności i outbox lag. Alert „CPU >80%” może być informacją capacity, ale alert użytkowy powinien odnosić się do objawu, np. braku terminowych potwierdzeń. Runbook wskazuje bezpieczne działania i kryterium wycofania, a nie tylko link do dashboardu.

[Przeczytaj przykładowy materiał diagnostyczny](DIAGNOSTYKA.md#diag-03-histogram-latency-i-granice-percentyla).

## OPS-10. Co oznacza 99,99% dostępności i jak projektować failover?

Najpierw definiuję SLI: co jest dobrym requestem i dla jakiego okresu. Przy modelu czasu 99,99% zostawia około 4,32 minuty na 30 dni; SLO requestowe liczy błędne żądania, więc nie musi odpowiadać takiej samej liczbie minut. SLA to zobowiązanie, SLO cel.

Projektuję redundancję domen awarii, zapas po utracie instancji/strefy, failover i degradację. Backup nie jest HA, a replikacja nie zastępuje backupu. RPO/RTO mierzę testem odtworzenia, z fencing starego primary i sprawdzeniem integralności.

Przy SLI requestowym 99,99% z miliona żądań dopuszcza 100 nieudanych według zdefiniowanej klasyfikacji. Trzeba określić timeout, odpowiedzi częściowe i planowane przerwy. Bez tego różne zespoły raportują różną dostępność tego samego produktu.

Dostępność zależności w łańcuchu nie sumuje się intuicyjnie. Wspólne domeny awarii mogą unieważnić prosty model niezależnych komponentów. Test DR obejmuje utratę węzła/regionu, promocję repliki, fencing starego writera i biznesowe sprawdzenie danych. Replika w tej samej strefie oraz nieprzetestowany backup nie stanowią wystarczającego dowodu.

<!-- follow-up:OPS-10 -->
**Sprawdź rozumienie:** Działa replika w innym regionie. Czy backup i restore drill są nadal potrzebne?

<details>
<summary>Kierunek odpowiedzi</summary>

Tak. Replika może powielić błąd logiczny lub DELETE. Backup umożliwia odtworzenie historii, a drill weryfikuje RPO/RTO i integralność. HA, DR i odzyskanie po błędzie danych to różne scenariusze.

</details>

## OPS-11. Jak Netflix ograniczał single points of failure i czego nie wolno z tego wywnioskować?

W opisie migracji z 2016 r. Netflix wskazuje przejście od pionowych pojedynczych punktów awarii do rozproszonych, poziomo skalowanych systemów w chmurze i zmianę modelu operacyjnego. To historyczny przykład kosztownej transformacji, nie dowód braku wszystkich SPOF dziś. [Opis Netflix](https://about.netflix.com/en/news/completing-the-netflix-cloud-migration).

We własnym systemie mapuję domeny awarii, wspólne zależności, control plane i procedury odzyskania. Redundancja aplikacji nie pomoże przy jednym magazynie danych albo błędzie rozesłanym do wszystkich instancji. Testuję awarie i ograniczam blast radius.

Najważniejsza lekcja z dużej migracji to zmiana granic odpowiedzialności i sposobu obsługi awarii, a nie samo „użyli chmury”. Przeniesienie tej samej pojedynczej bazy na VM nadal może pozostawić jeden punkt zapisu i jeden niesprawdzony recovery plan.

We własnej analizie SPOF uwzględniam DNS, certyfikaty, secret store, registry obrazów i control plane wdrożeń. Dwie repliki mogą współdzielić wadliwą konfigurację lub region. Testuję awarie pojedyncze oraz skorelowane, stosuję małe etapy rollout i utrzymuję drogę cofnięcia. Publiczny opis historyczny nie jest pełną aktualną mapą cudzej infrastruktury.

## OPS-12. Blue-green, canary czy rolling update? Jak bezpiecznie cofnąć regresję?

Blue-green przełącza ruch między środowiskami, canary stopniowo zwiększa udział nowej wersji, a rolling wymienia instancje. Każdy wariant wymaga obserwacji critical journeys, p99, błędów i danych, nie tylko statusu deploya.

Przy regresji zatrzymuję promocję i przełączam na znany artefakt, jeżeli pozostaje zgodny z aktualnym schematem i komunikatami. Odbudowa starego kodu nie cofa migracji danych. Feature flag może ograniczyć skutek szybciej niż pełny rollback; musi mieć bezpieczny default.

Canary potrzebuje dostatecznej próbki i porównywalnego ruchu; brak błędów przy dwóch requestach nie dowodzi jakości. Blue-green upraszcza przełączenie, ale stara wersja musi nadal rozumieć nowy schemat i dane. Rolling wymaga współistnienia wersji.

Przy regresji zatrzymuję rollout, identyfikuję digest i przełączam ruch według procedury. Nie wykonuję automatycznie destrukcyjnego down-migration. Feature flag pomaga odciąć nową funkcję, lecz jej stan i dostawca mogą same być zależnością awarii. Test recovery obejmuje stan utworzony przez nowy kod i aktywne procesy w toku.

## OPS-13. Jak zmieniać schemat bez downtime, także przy sharding migration?

Stosuję expand/contract: najpierw kompatybilny schemat, potem kod obsługujący oba warianty, kontrolowany backfill, weryfikacja i dopiero usunięcie starego pola. Mierzę locki, lag, postęp i zgodność obu wersji podczas rolloutu.

Przy reshardingu kopiuję dane, doganiam zmiany, porównuję i przełączam ownership przez wersjonowany routing z ochroną przed podwójnym zapisem. Consistent hashing ogranicza zakres przesunięć, ale nie wykonuje migracji. Dual write bez protokołu naprawy może rozjechać kopie.

Backfill musi być wznawialny, ograniczony i odporny na równoległe nowe zapisy. Jeżeli nowa kolumna jest wypełniana ze starej, proces nie powinien nadpisać późniejszej poprawnej aktualizacji starszym odczytem. Wersja lub warunkowy update pomaga rozstrzygnąć ten przeplot.

Faza contract następuje dopiero po potwierdzeniu braku starych czytelników/writerów i gotowości rollbacku. Przy sharding migration dodatkowo mierzę kompletność kopii i lag CDC. Shadow reads porównują wynik, lecz nie mogą podwajać nieodwracalnych skutków. Plan zawiera kryteria przerwania i powrotu przed przełączeniem ownershipu.

## OPS-14. Jak wygląda pipeline od Docker image do aplikacji na AWS?

CI pobiera konkretną rewizję, uruchamia testy, buduje niezmienny obraz, skanuje i publikuje go do registry, np. ECR. CD wdraża digest do ECS lub EKS, dostarcza konfigurację/tożsamość i czeka na health/readiness oraz analizę metryk.

Nie buduję innego artefaktu dla każdego środowiska, jeśli wystarczy konfiguracja runtime. Pipeline ma kontrolowane uprawnienia, audyt i rollback. Na pytanie o własne narzędzia podaję tylko faktycznie używane usługi i własną rolę; przykładowy przepływ nie jest historią doświadczenia.

Obraz powinien identyfikować tę samą zawartość na każdym etapie, dlatego deploy używa digestu, a nie zmiennego latest. Konfiguracja środowiska i tożsamość są dostarczane oddzielnie. Test po deployu sprawdza critical journey, nie tylko start kontenera.

<!-- example:OPS-14-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
commit → testy → build obrazu → skany → registry/digest
       → rollout → readiness → smoke/metryki → promocja
~~~

To przykładowy przebieg, niezależny od jednego produktu CI. Migracje mają własne kompatybilne etapy i uprawnienia. Pipeline używa krótkotrwałej tożsamości tam, gdzie jest wspierana, zamiast kopiować stałe klucze do obrazu.

## OPS-15. Pipeline padł w połowie. Gdzie sprawdzić stan i logi?

Zaczynam od failed step, rewizji/digestu i faktycznego stanu środowiska. Czerwony pipeline nie oznacza braku deploya: obraz mógł trafić do registry, migracja wykonać część pracy, a nowy rollout przyjąć część ruchu. Porównuję desired i actual state przed decyzją o rerun.

Logi CI/CD wyjaśniają kroki automatyzacji. W Kubernetes rollout status pokazuje postęp Deployment, describe/events problemy scheduling, pull i probes, a logs — zachowanie właściwego kontenera, także poprzedniej instancji po restarcie. Sprawdzam namespace i kontekst klastra, by nie diagnozować innego środowiska. Centralne logi i trace uzupełniają ten obraz o wpływ na użytkowników.

Wznawianie jest bezpieczne tylko dla kroków o znanym stanie i idempotencji. Nie buduję przypadkowo innego obrazu i nie powtarzam ślepo częściowej migracji. Recovery kończy się potwierdzeniem critical journey oraz zgodności danych, nie samym zielonym statusem pipeline.

## OPS-16. Jak działają Kubernetes, health checks i self-healing? Jak zabezpieczyć health URL?

Kontrolery uzgadniają stan deklarowany z rzeczywistym. Readiness decyduje o ruchu, liveness o restarcie, startup chroni długi rozruch. Pod niegotowy nie dostaje zwykłego ruchu Service, ale Kubernetes nie naprawia sam błędów danych. [Semantyka probes](https://kubernetes.io/docs/concepts/workloads/pods/probes/).

W Boot używam Actuator i odpowiednich grup zdrowia, bez ujawniania szczegółów publicznie. Dostęp ograniczam na management port, sieci i security, pozostawiając sondom możliwość działania. Sam inny port nie stanowi autoryzacji. Ustalam resources, graceful shutdown i czas opróżnienia requestów.

Liveness zależna od wspólnej DB może jednocześnie restartować wszystkie pody podczas awarii DB, zwiększając liczbę reconnectów. Readiness może czasowo odciąć ruch, ale też wymaga namysłu, czy odebranie wszystkich instancji jest właściwą reakcją na częściową zależność.

<!-- example:OPS-16-1 kind:yaml-fragment -->
**Kontekst:** Fragment konfiguracji YAML; wymaga wskazanego zasobu/serwera; bez testu wdrożenia.

~~~yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
~~~

To fragment PodSpec wymagający konfiguracji Actuator i dobrania progów. Startup probe powinien chronić realistyczny długi start. Health endpoint nie powinien publicznie ujawniać nazw baz, wyjątków ani sekretów, a sieć musi dopuścić zaufane sondy.

## OPS-17. Jak EC2 uzyskuje dostęp do S3 bez zapisanych kluczy?

Rola IAM przypisana przez instance profile dostarcza aplikacji na EC2 tymczasowych credentials odnawianych przez wspierany provider chain SDK. Nie trzeba umieszczać długoterminowych kluczy w kodzie ani properties. [Role EC2](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2.html).

Następnie ograniczam akcje i zasoby: GetObject dla wybranego prefixu różni się od ListBucket całego bucketa. Dostęp zależy też od bucket policy, ewentualnego KMS i sieci; poprawna rola nie naprawi niedozwolonej ścieżki.

Kontener powinien mieć tożsamość zadania/poda odpowiednią do platformy. W ECS task role i execution role służą innym celom; w EKS aplikacja nie powinna przypadkowo korzystać z szerokich uprawnień hosta. Testuję dozwolony odczyt, odmowę obcego prefixu/akcji i rotację credentials. Brak zapisanych kluczy rozwiązuje ich dystrybucję, ale nie zastępuje minimalnych uprawnień i ochrony dostępu do samej tożsamości.

## OPS-18. Jaki jest maksymalny rozmiar pojedynczego obiektu S3?

Według dokumentacji sprawdzonej 24.10.2026 limit multipart object wynosi 48,8 TiB, opisywany też jako około 50 TB. Nie powtarzam dawnego limitu 5 TiB bez kontekstu czasu. Obiekt, pojedynczy request i część multipart mają różne limity. [Limity multipart S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html).

Multipart pozwala wysyłać części i ponawiać je osobno; planuję ich rozmiar, limit liczby, checksum i sprzątanie niedokończonych uploadów. ETag nie jest uniwersalnym zamiennikiem MD5 całego obiektu. W projekcie waliduję aktualny limit także w użytym SDK i interfejsie.

Rozmiar części multipart dobieram tak, aby liczba części nie przekroczyła limitu dla planowanego pliku. Zbyt małe części przy ogromnym obiekcie mogą wyczerpać ich dopuszczalną liczbę; zbyt duże podnoszą koszt ponowienia. Niedokończone uploady potrzebują cleanup/lifecycle.

Aplikacyjne API może mieć dużo mniejszy limit niż S3: reverse proxy, gateway i pojedynczy PUT mają własne ograniczenia. Duże pliki warto wysyłać bezpośrednio do storage kontrolowanym uprawnieniem, a backend utrzymuje manifest i status. Checksum weryfikuje integralność; ukończenie wszystkich części powinno poprzedzać publikację zasobu jako gotowego.

## OPS-19. Mockito `@Mock` czy `@Spy`? Jak rozumieć coverage i SonarQube?

Mock zastępuje zależność kontrolowanym dublem. Spy domyślnie korzysta z rzeczywistego obiektu, dlatego nieostrożny stubbing może uruchomić metodę z I/O lub zmianą stanu. Dla prostej logiki mały fake bywa czytelniejszy niż częściowo zamockowany serwis z wieloma zależnościami.

Coverage, np. raport JaCoCo, pokazuje wykonane instrukcje/gałęzie, nie siłę asercji. 100% nie pomoże, jeśli test sprawdza tylko brak wyjątku. SonarQube zbiera analizę i raporty, ale nie jest dowodem poprawności domeny.

Dobieram dowód do kontraktu: test DB dla commit/rollback, negatywny HTTP dla dostępu do cudzych danych, kontrolowany przeplot dla salda nieujemnego i liczby zaakceptowanych pobrań. Mock weryfikujący kolejność save nie odtworzy locków bazy. Mutation testing może wykazać słabe asercje, lecz nadal wymaga poprawnie zdefiniowanego zachowania.

## OPS-20. Jakie strategie branchowania i branch protection dobrać?

Trunk-based i krótkie branche zmniejszają rozjazd, GitHub Flow wspiera pracę przez PR, a release branches bywają potrzebne przy kilku utrzymywanych wersjach. GitFlow dodaje etapy i koszt synchronizacji; wybór zależy od sposobu wydawania, nie samego hostingu.

W ruleset/branch protection ustawiam wymagane review, właściwe status checks, zakaz force push i kontrolowane wyjątki. Nazwy checków oraz reguły muszą odpowiadać pipeline. [Protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).

Długowieczny branch rozwija osobny świat: im więcej zmian, tym droższa integracja i późniejszy feedback. Krótkie branche i małe PR pomagają, gdy feature flags lub kompatybilne kroki pozwalają wydawać niepełną funkcję bez aktywowania jej klientom.

Branch protection powinno wiązać konkretne checks z właściwym workflow i źródłem. Skip całego joba albo nieaktualna nazwa statusu może dawać inną ochronę niż oczekiwano. Merge queue wymaga sprawdzania zestawu zmian, który faktycznie ma trafić na gałąź. Wyjątki administracyjne i recovery są jawne, aby reguły nie były obchodzone rutynowo.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [kubernetes](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/kubernetes/README.md)
- [progressive delivery](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/progressive_delivery/README.md)
- [tracing](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_3/block_b/tracing/README.md)
<!-- labs:end -->
