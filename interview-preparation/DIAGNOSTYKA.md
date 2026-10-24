# Czytanie dowodów diagnostycznych

[Spis rozdziałów](README.md) · [Indeks pytań](INDEKS.md)

Poniższe plany, dumpy i liczby są **syntetycznymi materiałami edukacyjnymi**. Nie pochodzą z pomiaru tego repozytorium ani z produkcji. Pokazują interpretację, a nie obietnicę uzyskania określonego czasu. Fragmenty są skrócone i mogą pomijać pola konkretnej wersji narzędzia.

- [Plan SQL](#diag-01-plan-sql-przed-i-po-indeksie)
- [Thread dump i właściciel monitora](#diag-02-thread-dump-blocked-i-właściciel-monitora)
- [Histogram latency](#diag-03-histogram-latency-i-granice-percentyla)
- [Rozkład czasu requestu](#diag-04-szybki-sql-i-wolny-request)
- [JFR, GC i retencja](#diag-05-jfr-gc-i-pamięć)

## DIAG-01. Plan SQL przed i po indeksie

Założenie ćwiczenia: milion zamówień, dziesięć pasuje do tenanta 7 i statusu PENDING. Potrzebujemy ich ID, nie pełnego grafu encji. Na realnej bazie parametry, statystyki, cache i plan mogą dać inne wyniki.

<!-- example:DIAG-01-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM orders
WHERE tenant_id = 7 AND status = 'PENDING';
~~~

**Przed zmianą — syntetyczny plan:**

<!-- example:DIAG-01-2 kind:synthetic-diagnostic -->
**Kontekst:** Syntetyczny, skrócony materiał diagnostyczny; dane ilustracyjne, nie wynik pomiaru repozytorium.

~~~text
Seq Scan on orders  (cost=0.00..27500.00 rows=10 width=8)
                     (actual time=0.102..73.210 rows=10 loops=1)
  Filter: ((tenant_id = 7) AND (status = 'PENDING'::text))
  Rows Removed by Filter: 999990
  Buffers: shared hit=12500
Planning Time: 0.140 ms
Execution Time: 73.250 ms
~~~

Odczyt planu zaczynam od pracy: silnik przejrzał milion wierszy, odrzucił 999 990 i zwrócił dziesięć. Estymacja rows=10 jest zgodna z obserwacją, więc ta próbka nie wskazuje błędnej oceny liczności. Cost jest jednostką modelu optymalizatora, nie czasem w milisekundach. Shared hit oznacza trafienie w shared buffers PostgreSQL, nie koniecznie brak całej pracy I/O na innych poziomach.

Indeks po (tenant_id, status) może ograniczyć skan dla takiego filtra. **Po zmianie — syntetyczny plan:**

<!-- example:DIAG-01-3 kind:synthetic-diagnostic -->
**Kontekst:** Syntetyczny, skrócony materiał diagnostyczny; dane ilustracyjne, nie wynik pomiaru repozytorium.

~~~text
Index Scan using idx_orders_tenant_status on orders
  (cost=0.42..44.63 rows=10 width=8)
  (actual time=0.030..0.090 rows=10 loops=1)
  Index Cond: ((tenant_id = 7) AND (status = 'PENDING'::text))
  Buffers: shared hit=13
Planning Time: 0.160 ms
Execution Time: 0.120 ms
~~~

W tej parze mniejsza liczba odwiedzanych stron i brak filtrowania prawie całej tabeli pasują do hipotezy, że indeks pomógł selektywnemu odczytowi. Nie dowodzą przyspieszenia każdego zapytania ani poprawy zapisów. Przed uznaniem zmiany za dobrą porównuję rzeczywiste parametry największych tenantów, indeks size, czas INSERT/UPDATE, stan cache oraz reprezentatywną próbkę wykonań.

Gdy węzeł ma loops=1000, actual rows/time odnoszą się do odpowiednio raportowanych średnich na iterację; analizuję je wraz z liczbą wywołań. Nie sumuję bezmyślnie inclusive times rodzica i dziecka. EXPLAIN ANALYZE wykonuje zapytanie — dla modyfikacji może wykonać realny skutek. [Semantyka EXPLAIN PostgreSQL](https://www.postgresql.org/docs/current/using-explain.html).

**Sprawdź rozumienie:** czy samo Seq Scan dowodzi złego planu?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Dla małej tabeli albo odczytu większości wierszy może być tańszy niż dostęp przez indeks. Potrzebne są selectivity, liczność, buforowanie i pomiar konkretnego workloadu.

</details>

## DIAG-02. Thread dump: BLOCKED i właściciel monitora

Skrócony, syntetyczny dump w stylu HotSpot dla platform threads:

<!-- example:DIAG-02-1 kind:synthetic-diagnostic -->
**Kontekst:** Syntetyczny, skrócony materiał diagnostyczny; dane ilustracyjne, nie wynik pomiaru repozytorium.

~~~text
"http-worker-17" #117 prio=5 tid=0x... nid=0x117 waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
    at demo.ReportCache.get(ReportCache.java:28)
    - waiting to lock <0x0000000700010010> (a demo.ReportCache)

"refresh-worker-1" #31 prio=5 tid=0x... nid=0x31 runnable
   java.lang.Thread.State: RUNNABLE
    at sun.nio.ch.SocketDispatcher.read0(Native Method)
    at demo.PriceClient.fetch(PriceClient.java:54)
    at demo.ReportCache.refresh(ReportCache.java:45)
    - locked <0x0000000700010010> (a demo.ReportCache)
~~~

Ten sam identyfikator monitora łączy oczekującego workera z właścicielem. Właściciel wykonuje odczyt sieciowy pod blokadą cache. To hipoteza przyczyny contention: powolne HTTP wydłuża sekcję krytyczną i zatrzymuje odczyty. RUNNABLE w dumpie Java nie dowodzi, że wątek zużywa właśnie CPU; natywne I/O może być opisane w ten sposób.

Z pojedynczej próbki nie wynika deadlock. Pobieram kolejne dumpy i sprawdzam, czy właściciel robi postęp; cykl oczekiwania wymaga wskazania zależności wracającej do początku. Koreluję czas locka z metryką klienta HTTP i profilem wall-clock/JFR.

Przeniesienie HTTP poza lock skraca sekcję, lecz może dopuścić równoległe odświeżenia i zastosowanie starego wyniku. Poprawka potrzebuje np. single-flight i sprawdzenia generacji przed podmianą snapshotu. Samo skasowanie synchronized usuwa również ochronę danych. Narzędzia i format obserwacji virtual threads różnią się od tego przykładu.

## DIAG-03. Histogram latency i granice percentyla

Syntetyczna próbka obejmuje **1000 zakończonych requestów**, a liczności są skumulowane:

| Górna granica czasu | Liczba requestów do tej granicy | Udział |
| --- | ---: | ---: |
| 100 ms | 940 | 94% |
| 200 ms | 980 | 98% |
| 1000 ms | 995 | 99,5% |
| 5000 ms | 1000 | 100% |

Empiryczny p50 mieści się do 100 ms, p95 w (100, 200] ms, a p99 w (200, 1000] ms. Bucket nie podaje dokładnego rozkładu wewnątrz przedziału: nie wiemy, czy p99 wynosi 210, 600 czy 990 ms. Interpolacja estymuje go przy dodatkowych założeniach. Dokładny rachunek wymagałby pojedynczych obserwacji albo dokładniejszego histogramu.

Jeśli dashboard pokazuje średnią z p99 replik, nie otrzymujemy globalnego p99. Repliki mogą mieć różny ruch i inne ogony. Agreguję kompatybilne bucket counts dla tego samego okresu/etykiet i dopiero obliczam kwantyl. Porównanie musi też obejmować timeouty, odmowy i requesty nadal aktywne; statystyka samych sukcesów może „poprawić się”, gdy wolne próby zaczęły odpadać.

**Sprawdź rozumienie:** jak zmienić buckety dla SLO 300 ms?

<details>
<summary>Kierunek odpowiedzi</summary>

Dodać granicę 300 ms, aby bez interpolacji policzyć udział requestów mieszczących się w progu. SLO dotyczy zdefiniowanych dobrych operacji; trzeba zachować spójną politykę wliczania błędów i timeoutów.

</details>

## DIAG-04. Szybki SQL i wolny request

Syntetyczny trace rozkłada **rozłączne odcinki** ścieżki jednego requestu:

| Odcinek | Czas |
| --- | ---: |
| kolejka wejściowa | 40 ms |
| oczekiwanie na connection | 1600 ms |
| wykonanie SQL i odczyt wyniku | 50 ms |
| zależność HTTP | 120 ms |
| serializacja | 90 ms |
| pozostała ścieżka proxy/transport | 100 ms |
| łącznie | 2000 ms |

Optymalizacja samego SQL o połowę oszczędziłaby 25 ms, jeśli pozostałe warunki się nie zmienią. Główną hipotezą jest acquisition, ale przyczyną mogą być długie transakcje, wyciek, zły routing puli lub niedostępne nowe connection. Niskie CPU aplikacji pasuje do oczekiwania, lecz nie wybiera jednej z tych przyczyn.

W prawdziwym trace spany bywają zagnieżdżone lub równoległe. Nie sumuję inclusive parent z dziećmi ani czasów dwóch gałęzi fan-out, jak gdyby były sekwencyjne. Brak spana kolejki nie oznacza czasu równego zero — może oznaczać brak instrumentacji.

## DIAG-05. JFR, GC i pamięć

Syntetyczne podsumowanie trzech **porównywalnych** okien pracy:

| Okno | Live set po kolekcji starej generacji | Retencja cache | p99 requestu |
| --- | ---: | ---: | ---: |
| A | 200 MiB | 10 tys. wpisów | 120 ms |
| B | 350 MiB | 60 tys. wpisów | 300 ms |
| C | 500 MiB | 110 tys. wpisów | 900 ms |

To korelacja sugerująca rosnącą retencję, nie dowód winy cache. W heap dumpie szukam dominatora i ścieżki do GC roots; w JFR — alokacji, czasu życia, GC i blokad. Sampling CPU, próbki alokacji i czasy ścienne opisują inne zjawiska. Pojedyncza próbka alokacji nie jest dokładną liczbą wszystkich obiektów.

Jeżeli cache świadomie rośnie do limitu, może następnie osiągnąć plateau. Jeżeli indeksuje unikalne request ID bez eviction, trend może trwać do OOM. Wyższy allocation rate przy stałym live set opisuje inny problem niż rosnący live set. Porównanie kolektorów ma sens przy tym samym workloadzie i limitach, z rozdzieleniem pauz oraz pracy współbieżnej. [Mechanizmy JFR w JDK 21](https://docs.oracle.com/en/java/javase/21/jfapi/flight-recorder-api-programmers-guide.pdf).

Żadna z tabel nie dowodzi, że sama zmiana Xmx lub kolektora naprawi przyczynę. Poprawka powinna mieć hipotezę, pomiar przed/po i test dłuższej pracy, a OOMKill kontenera wymaga także analizy pamięci poza heapem.
