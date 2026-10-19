# AGENTS.md

## Cel modułu

`system-design` jest zbiorem niezależnych, wykonywalnych studiów przypadku
popularnych problemów projektowania systemów. Każdy katalog opisuje osobny system
i ma łączyć trzy perspektywy:

1. rozumowanie projektowe: wymagania, skala, niezmienniki i trade-offy;
2. kod edukacyjny pokazujący najważniejsze mechanizmy danego systemu;
3. testy dowodzące wybranych gwarancji oraz ujawniające granice rozwiązania.

Nie traktuj katalogów jako mikroserwisów jednego produktu i nie twórz między nimi
zależności. Nie próbuj też implementować całej architektury narysowanej w README.
Kod powinien wykonywalnie wyjaśniać najtrudniejsze elementy projektu, a pozostałe
komponenty mogą pozostać opisanymi granicami produkcyjnymi.

## Mapa studiów przypadku

| Katalog | Główny problem projektowy |
| --- | --- |
| `chat-system/` | kolejność i trwałość wiadomości, realtime, presence, offline delivery oraz synchronizacja urządzeń |
| `ecommerce/` | checkout przecinający inventory, payment i order, spójność stanów oraz integracja przez zdarzenia |
| `file-storage/` | rozdzielenie metadanych od blobów, upload fragmentami, wersjonowanie, deduplikacja i bezpieczne udostępnianie |
| `metrics-logging-system/` | bardzo duży strumień telemetrii, kontrola cardinality, retencja, zapytania, alerty i multi-tenancy |
| `news-feed/` | fan-out on write kontra fan-out on read, ranking, celebrity problem i odbudowa feedu |
| `notification-system/` | wielokanałowe dostarczenie, preferencje, harmonogramowanie, retry, deduplikacja i status delivery |
| `payment-system/` | idempotencja pieniężna, ledger, webhooki PSP, reconciliation, refundy i audyt |
| `rate-limiter/` | atomowa decyzja online, algorytmy limitowania, rozproszony licznik i zachowanie przy awarii store |
| `ride-sharing/` | geospatial indexing, matching kierowcy, stan przejazdu, realtime location i granice regionalne |
| `search-autocomplete/` | generowanie kandydatów, ranking przy niskim latency, prywatność, moderacja oraz przebudowa indeksu |
| `url-shortener/` | generowanie kodów, bardzo szybki redirect, hot keys, cache, abuse prevention i asynchroniczne analytics |
| `video-streaming/` | upload i transkodowanie, packaging, object storage, CDN, adaptive bitrate oraz ochrona treści |

Przed zmianą przeczytaj główny `system-design/README.md`, README wybranego systemu
oraz jego konfigurację buildu. Jeżeli podkatalog ma własny `AGENTS.md`, jego
instrukcje są bardziej szczegółowe.

## Standard merytoryczny README

README systemu ma być dokumentem projektowym, a nie katalogiem technologii.
Najważniejsze informacje umieszczaj na początku, w następującej kolejności:

1. czego studium uczy i jaki problem odróżnia je od pozostałych;
2. najważniejszy niezmiennik lub SLO;
3. minimalny wykonywalny przepływ oraz komenda jego weryfikacji;
4. czego obecna implementacja świadomie nie gwarantuje;
5. mapa `mechanizm -> klasa -> test -> granica produkcyjna`.

Dalsza część README powinna obejmować, adekwatnie do problemu:

- functional requirements, non-functional requirements i jawny out-of-scope;
- capacity estimation, w tym peak traffic, read/write ratio, storage i bandwidth;
- API lub protokoły oraz zasady ich kompatybilnej ewolucji;
- model danych projektowany od access patterns i wskazanie source of truth;
- high-level architecture i jeden dokładnie opisany krytyczny data flow;
- wymagany model spójności, granice transakcji i delivery semantics;
- skalowanie, partycjonowanie, hot keys/partitions i backpressure;
- failure modes, recovery, degradację, retry, timeouty i idempotencję;
- observability: SLI/SLO, metryki, logi, trace'y, alert i runbook;
- security, abuse cases, PII, trust boundaries i model uprawnień;
- alternatywy, koszt operacyjny i kryteria „kiedy nie użyć”;
- uruchomienie, profile infrastrukturalne i znane ograniczenia.

Każda liczba dotycząca skali musi być założeniem albo wynikiem pomiaru. Nie
przedstawiaj przykładowych wartości jako danych potwierdzonych. Diagram musi być
zgodny z implementacją lub oznaczony jako architektura docelowa.

## Kod edukacyjny i komentarze

Kod może być bardziej szczegółowo komentowany niż kod produkcyjny. Komentarze
powinny wyjaśniać przede wszystkim:

- niezmiennik chroniony przez daną operację;
- granicę transakcji i moment, po którym efekt jest widoczny;
- możliwy race condition, failure window albo scenariusz duplikacji;
- model spójności, ordering i delivery semantics;
- znaczenie timeoutu, retry, backpressure i limitu zasobów;
- dlaczego prostsze rozwiązanie jest niewystarczające przy przyjętej skali;
- które zachowanie jest symulacją, a które zależy od prawdziwej infrastruktury;
- konsekwencje produkcyjne i świadome ograniczenia przykładu.

Nie komentuj podstawowej składni Javy, oczywistych getterów ani nazw mówiących
same za siebie. Komentarz nie może obiecywać gwarancji, której kod ani test nie
potwierdza. Preferuj małe klasy nazwane według odpowiedzialności zamiast jednego
dużego pliku mającego reprezentować cały system.

Jeżeli laboratorium porównuje rozwiązania, oznacz je w nazwie pakietu, klasy lub
README jako:

- `naive` — celowy kontrprzykład;
- `correct` — rozwiązanie chroniące opisany niezmiennik w granicach laboratorium;
- `simulation` — deterministyczny model zachowania systemu rozproszonego;
- `production boundary` — interfejs lub opis miejsca wymagającego prawdziwej infrastruktury.

## Testy jako dowód

Nie optymalizuj liczby testów ani samego coverage. Każdy test powinien odpowiadać
na pytanie: „jaką gwarancję utracimy, gdy ten test przestanie przechodzić?”.

W pierwszej kolejności testuj mechanizm charakterystyczny dla danego systemu,
szczególnie:

- invarianty i przejścia maszyny stanów;
- idempotencję i bezpieczny retry;
- konkurencyjne zapisy, duplikaty, reorder i partial failure;
- semantykę cache invalidation, TTL i fallbacku;
- granice tenantów, autoryzację i negatywne przypadki bezpieczeństwa;
- zachowanie po restarcie oraz odbudowę projekcji lub indeksu;
- kontrakty zależne od semantyki PostgreSQL, Redis, brokera albo object storage.

Fake lub H2 jest właściwy dla szybkiego testu logiki, ale nie może być dowodem
zachowania specyficznego dla infrastruktury. Takie gwarancje umieszczaj w osobnym
profilu Testcontainers. Zwykły test modułu powinien działać bez ręcznie
uruchomionego PostgreSQL, Redis, Kafki ani innego serwera; wymaganie infrastruktury
ma być jawne w nazwie profilu i README.

## Zasady zmian

- Ogranicz zmianę do jednego studium przypadku, chyba że zadanie dotyczy standardu całego modułu.
- Zachowuj istniejące zmiany użytkownika i nie formatuj masowo niezwiązanych plików.
- Nie kopiuj pełnych laboratoriów z `backend-engineering`; linkuj fundament, a tutaj pokazuj jego konsekwencję w konkretnym systemie.
- Nie dodawaj mikroserwisu tylko dlatego, że występuje na diagramie. Najpierw wskaż niezależną potrzebę skalowania, ownership albo izolacji awarii.
- Nie ukrywaj niedokończonej funkcji atrapą zwracającą sukces. Oznacz ją jako symulację lub niezaimplementowaną granicę.
- Nie zmieniaj Javy, Spring Boota, narzędzia budowania ani głównych zależności bez uzasadnienia i aktualizacji README.
- Sekrety, dane produkcyjne, wyniki buildu, uploady, logi i lokalne pliki `.env` nie należą do Git.
- Zmianę istotnego trade-offu, source of truth albo granicy serwisu opisz w ADR, jeżeli system używa ADR-ów.

## Weryfikacja

Każdy katalog jest osobnym buildem. Uruchamiaj komendę z katalogu wybranego
systemu. Minimalna weryfikacja dla projektu Maven:

```shell
mvn --batch-mode --no-transfer-progress test
mvn --batch-mode --no-transfer-progress verify
```

Jeżeli system ma profil infrastrukturalny lub Docker Compose, uruchom również
opisany w README kontrakt, ale oddziel jego wynik od szybkiej suite. Przed
zakończeniem:

1. potwierdź, że kod się kompiluje, a testy przechodzą bez ręcznie uruchomionych zależności, jeśli README nie mówi inaczej;
2. sprawdź krytyczny przepływ na wymaganej infrastrukturze;
3. porównaj README, diagramy, konfigurację i kod;
4. uruchom `git diff --check` i sprawdź, czy nie dodano `target/`, logów ani danych runtime;
5. podaj dokładne komendy, wyniki i nieweryfikowane granice.

Build kończący się sukcesem bez testów oznacza wyłącznie poprawną kompilację, nie
potwierdzenie projektu systemu.
