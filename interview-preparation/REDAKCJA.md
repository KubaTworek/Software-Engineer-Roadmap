# Redakcja i pokrycie materiału

[Spis rozdziałów](README.md)

Źródłem pytań był dostarczony plik „Wklejony tekst.txt”, zaczynający się od pytania o wzrost latency po migracji do virtual threads. Nie kopiujemy surowych list, aby nie powielać pytań, podpisów i przypadkowych notatek z mediów społecznościowych. Podział i odpowiedzi przygotowano na podstawie całego pliku.

## Co oznacza usunięcie duplikatu

Scalono pytania wymagające tego samego rozumowania: wzrost latency z 100 ms do 5 s oraz z 200 ms do 8 s mają wspólny tok diagnozy. Zachowano natomiast odrębne warunki: niskie CPU, wolne SQL, zablokowany monitor i degradacja po migracji do virtual threads są opisane w odpowiednich rozdziałach.

Definicja mechanizmu i projekt systemu korzystającego z mechanizmu mogą występować osobno. Lokalny token bucket, globalna quota i projekt gatewaya nie są tym samym zadaniem. LRU opisano raz; rozdział LLD odsyła do rozwiązania algorytmicznego.

## Gdzie trafiły grupy z notatek

| Grupa wejściowa / dodatkowy warunek | Docelowe odpowiedzi |
| --- | --- |
| Pierwsza lista 20 scenariuszy virtual threads | ASYNC-05–17 oraz JVM-09: limity, pinning, CPU, pamięć, ThreadLocal, Kafka, p99 i migracja |
| Java 8/17/21, obiekty, OOP, wyjątki, SOLID i wzorce | JAVA-01–20; różnice GC także JVM-07 |
| HashMap, kolizje, klucze, ConcurrentHashMap, iteratory i listy | COL-01–12; kontrakt atomowości także CON-06 |
| Streams, lambdy, map/flatMap, parallel, Spliterator i collectory | JAVA-13–14, COL-13–17 |
| JMM, volatile, atomiki, safe publication i initialization | CON-02–11, CON-19 |
| Callbacki, synchronized mimo wyścigu, stateless, ucieczka this | CON-07–13, CON-20 |
| ThreadLocal, request context, thread affinity, executory | CON-17–19, ASYNC-01–04 oraz ASYNC-12 |
| Kolejki, semafory, starvation, rejection i producer–consumer | CON-15–16, ASYNC-02–03 |
| OOM, heap, classloadery, GC, JFR, crash i brak postępu | JVM-01–10 |
| IoC/DI, beany, stereotypy, cykle i pełny start Boota | SPR-01–12 |
| Properties, profile, BOM, XML, SpringFactoriesLoader i starter web | SPR-01, SPR-08–10, SPR-13–14 |
| MVC, advice, Filter/Interceptor, Async, Cache i schedulery | SPR-15–21 |
| Dwie bazy i primary/secondary, szerokie pytanie o adnotacje | SPR-22–23 |
| Dziesięć konkretnych przypadków proxy/commit/async/izolacji | TX-01–12, TX-21; żaden warunek nie został zastąpiony samą definicją transakcji |
| EntityManager/SessionFactory, repozytoria, persist/merge i komunikacja z DB | TX-13–15 |
| Lazy/eager, N+1, L1/L2, pagination, cascade i orphanRemoval | TX-16–20 |
| SQL read/write tips, CTE, projekcje, batch, identity i sekwencje | DB-04–11 oraz TX-17–19 |
| ACID, PostgreSQL/MySQL MVCC, izolacja, locking | DB-01–03, TX-10–12 |
| JOIN, count, ranking doświadczenia, N-ta pensja i pusty dział | DB-12–15 |
| SQL/NoSQL/Redis/search, normalizacja, replicas, sharding i kolumny | DB-16–18 |
| REST, PUT/PATCH, POST search, filtry, statusy i kompatybilność | API-01–06 |
| Podwójny klik, retry płatności, równoczesne żądania | API-07, PAY-01–02 i TX-11 |
| Basic/OAuth2/JWT, SecurityFilterChain, CORS, sesje, hasła w AWS | API-08–14, API-18 |
| Ograniczenie „tylko 10K użytkowników” | API-15 — rozróżnienie uprawnień, sesji, współbieżności i RPS |
| HttpClient/RestTemplate/RestClient/WebClient/Feign, WebSocket i gRPC | API-16–17, ASYNC-15–16 |
| Kafka/RabbitMQ, partycje, grupy, Spring Kafka i pięciu odbiorców | MSG-01–04 |
| Crash w batchu, ordering klienta, duplikaty, DLQ, lag i hot partition | MSG-05–10 |
| XML → DB → Kafka, outbox, A → Kafka → B → REST C, exactly-once | MSG-11–13 |
| Schema Registry, wersjonowanie, push/pull | MSG-14–15 |
| Monolit, migracja, distributed monolith, database-per-service, saga/2PC | DIST-01–05, EXP-07 |
| Slow downstream, retry storm, breaker, Resilience4j, bulkhead, rate limits | DIST-06–10 |
| Redis outage, invalidacja po async commicie, TTL, LRU/LFU, stale cache | DIST-11–15 |
| Consistency, write flow, hashing, ZooKeeper, locks i clock skew | DIST-16–21 |
| Stateful scaling, gateway/LB/discovery, proxy i health detection | DIST-22–23 |
| Wolne API, niskie CPU, szybki SQL, różnice produkcji i skala | OPS-01–05 |
| Reconnect storm, DB niedostępna 30 sekund, recovery | OPS-06–07 |
| Trace ID, metryki/logi/trace, 99,99%, failover, Netflix i SPOF | OPS-08–11 |
| Blue-green/canary, rollback, zero downtime i resharding | OPS-12–13 |
| CI/CD, AWS deploy, pod logs, health security, EC2/S3 i limit obiektu | OPS-14–18 |
| Mock/Spy, coverage, SonarQube, Git i ochrona brancha | OPS-19–20 |
| Wszystkie listy HLD, w tym 25 zaawansowanych projektów | SD-01–32 oraz PAY-01–16 |
| Payment, ledger, loan management, fraud i 17 scenariuszy bankowych | PAY-01–16; quota bankowa SD-06, alerty SD-03, migracje OPS-13 |
| Wszystkie konkretne zadania DSA i ogólne pytanie o optymalizację DP | ALG-01–28 |
| Machine coding: parking, ATM, order book, limiter, TTL, wydatki, logger | LLD-01–08; cache ALG-04, scheduler ALG-14 |
| Projekt, kompromis, incydent, spór, performance i pierwsze 90 dni | EXP-01–07 |
| Dziesięć pytań o workflow AI, błędy, LLM, koszt, bezpieczeństwo i RAG | EXP-08–17 |

Identyfikatory są umieszczone w nagłówkach rozdziałów. Tabela jest mapą redakcyjną, nie automatycznym dowodem równoważności znaczeniowej pytań. Kontrola skryptem potwierdza poprawność odsyłaczy i strukturę dokumentacji; merytoryka wymaga przeglądu tekstu.

## Redakcja i samodzielna nauka

Powtarzające się wprowadzenia i rozwinięcia scalono m.in. w opisach transakcji, puli DB, circuit breakera, retry oraz diagnostyki pipeline’u. Nie zachowujemy minimalnej objętości dla samego wyniku liczbowego; zachowujemy założenia, przykład i konsekwencję. Odmienne granice gwarancji nadal mają własne odpowiedzi.

[Indeks](INDEKS.md) i spisy rozdziałów są odtwarzane z nagłówków przez [skrypt nawigacji](scripts/update-navigation.ps1). Bezpośrednie odsyłacze prowadzą do wybranych laboratoriów; nie oznaczają, że dane laboratorium realizuje pełny zakres wszystkich pytań rozdziału. Dopytania z rozwijanymi wskazówkami służą sprawdzeniu przeniesienia reguły do innego warunku.

[Materiały diagnostyczne](DIAGNOSTYKA.md) mają jawnie syntetyczne dane. [Weryfikacja przykładów](checks/README.md) rozróżnia kompilację JDK, testy wybranych kontraktów, kontrprzykłady i szkice bez wykonania. Test harness jest źródłem utrzymywanym w repozytorium, natomiast generowane klasy pozostają poza nim.

## Poprawione założenia z materiału

- Virtual threads nadal mogą czekać i zużywać zasoby; pinning przez synchronized w JDK 21 nie opisuje bez zmian JDK 24+.
- Ani domyślne 10 połączeń Hikari, ani konkretna formuła nie są z definicji błędne lub poprawne dla wszystkich usług. Rozstrzyga workload i pojemność DB.
- Self-invocation pomija nowe advice, ale kod może nadal działać we wcześniej otwartej transakcji. Public final wymaga rozróżnienia proxy JDK i CGLIB.
- LazyInitializationException jest wyjątkiem, nie „zawsze cichym” zachowaniem. Flush nie jest commitem.
- Wybór CTE, batch size, indeksu czy cache nie gwarantuje stałego procentu przyspieszenia.
- Eviction i świeżość to różne własności. Invalidacja po commicie usuwa ważny wyścig, ale nie wszystkie możliwe wyścigi odczytu i awarie publikacji.
- Kafka exactly-once nie obejmuje dowolnego zewnętrznego API. Scheduler także może uruchomić handler ponownie po niepewnym wyniku.
- Eventual consistency nie jest uniwersalnie lepsza od silnej spójności; kolejność i finalność trzeba dobrać do niezmiennika.
- Same miliony użytkowników nie wyznaczają RPS, liczby serwerów ani wyboru bazy. Nazwa dużej firmy nie dowodzi braku SPOF.
- Starsze opisy Boot auto-configuration, RestTemplate, Kafka/ZooKeeper i maksymalnego obiektu S3 wymagają kontekstu wersji/daty.

## Świadome granice

Nie odtworzono brakującego fragmentu kodu dziedziczenia ani nie wymyślono konkretnego zadania „DP z optymalizacją”. Odpowiedzi wyjaśniają sposób analizy i brakujące warunki. Nie zamieniono haseł „drzewa/grafy/LeetCode” w arbitralną listę nowych zadań.

Moduł zawiera 323 rozwinięte odpowiedzi w 17 rozdziałach. Każda odpowiedź łączy zwięzłe wprowadzenie z pogłębieniem: konkretnym przebiegiem, kontrprzykładem, modelem danych, kodem lub sposobem oceny wyniku. Forma zależy od tematu; przy pytaniach o doświadczenie są to schematy do uzupełnienia własnymi faktami.

Przykłady Java, SQL, HTTP i konfiguracji pokazują wybrane mechanizmy, a nie kolejne kompletne aplikacje. Kod wykorzystujący domenowe typy lub konfigurację Springa jest szkicem wymagającym wskazanego kontekstu. Samodzielne metody algorytmiczne mają opis założeń, złożoności i przypadków brzegowych. Istniejące [ćwiczenia LLD](../low-level-design/README.md) i [studia HLD](../system-design/README.md) pozostają samodzielnymi modułami.
