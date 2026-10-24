# AGENTS.md

## Cel repozytorium

To repozytorium jest praktyczną roadmapą rozwoju Java Backend Engineera od poziomu Mid do Senior i Top Senior. Łączy pięć rodzajów materiałów:

1. małe, skoncentrowane przykłady techniczne,
2. większe projekty portfolio wykorzystujące poznane zagadnienia,
3. niezależne implementacje popularnych problemów High Level System Design,
4. ćwiczenia Low Level Design oparte na programowaniu obiektowym w Javie,
5. dokumentacyjne pytania i odpowiedzi do powtórek oraz rozmów technicznych.

Nie traktuj całego repozytorium jak jednej aplikacji. Poszczególne katalogi mogą mieć własny build, konfigurację, zależności i wymagania infrastrukturalne.

## Struktura repozytorium

### `backend-engineering/`

Moduł edukacyjny podzielony na kolejne etapy rozwoju. Zawiera małe laboratoria, przykłady, eksperymenty, testy i dokumentację konkretnych zagadnień backendowych.

- `stage_1/` — fundamenty poziomu Mid:
  - Java Concurrency,
  - poprawność czasu, strefy, deadline i zadania okresowe,
  - JVM, GC, JIT, profilowanie i benchmarki,
  - Spring pod maską,
  - relacyjne bazy danych, wydajność i NoSQL,
  - clean code, refaktoryzacja i testowalność,
  - networking aplikacyjny: DNS, TCP/TLS, HTTP, pooling i timeouty.
- `stage_2/` — przejście Mid+ → Senior:
  - DDD i granice domenowe,
  - Clean Architecture,
  - projektowanie i kompatybilna ewolucja publicznego API,
  - modularny monolit i mikroserwisy,
  - integracje synchroniczne i asynchroniczne,
  - eventy, messaging i odporność na awarie,
  - CDC, odbudowa projekcji, rekoncyliacja i bezpieczny backfill,
  - praktyczne podstawy DevOps,
  - progressive delivery i operacje podczas incydentu,
  - Application Security i Secure SDLC.
- `stage_3/` — poziom Senior:
  - System Design,
  - skalowanie i systemy rozproszone,
  - testowanie historii, niezmienników i liniowalności systemów rozproszonych,
  - niezawodność,
  - observability,
  - cloud i decyzje infrastrukturalne.

Kod w tym module często celowo demonstruje pojedynczy problem, błędne rozwiązanie albo porównanie kilku podejść. Nie zamieniaj automatycznie laboratorium w rozbudowaną aplikację produkcyjną. Zachowaj dydaktyczny cel przykładu i przeczytaj najbliższy `README.md` przed zmianą.

### `projects/`

Zawiera większe projekty portfolio oparte na wiedzy z kolejnych etapów `backend-engineering`.

- `project-1/` — **Booking & Capacity Platform**. Projekt odpowiada głównie etapowi 1 i ćwiczy współbieżność, transakcje, poprawność rezerwacji, wydajność bazy, JVM, cache i testowalność.
- `project-2/` — **Marketplace Order Fulfillment Platform**. Projekt odpowiada głównie etapowi 2. Jest modularnym monolitem wykorzystującym DDD, granice modułów, eventy, Kafka, Outbox Pattern, idempotencję, retry, DLQ i eventual consistency.
- `project-3/` — **Microservices Ticketing Platform**. Projekt odpowiada głównie etapowi 3. Ćwiczy mikroserwisy, niezależność danych, skalowanie, resilience, observability, infrastrukturę oraz wdrożenia cloud.

Projekty mają demonstrować zastosowanie zagadnień w spójnym kontekście biznesowym. Nie kopiuj do nich przykładów z `backend-engineering` bez dopasowania do domeny i architektury danego projektu.

### `system-design/`

Zawiera niezależne implementacje popularnych zadań System Design. Obecnie są to:

- `chat-system/`,
- `ecommerce/`,
- `file-storage/`,
- `metrics-logging-system/`,
- `news-feed/`,
- `notification-system/`,
- `payment-system/`,
- `rate-limiter/`,
- `ride-sharing/`,
- `search-autocomplete/`,
- `url-shortener/`,
- `video-streaming/`.

Każdy system jest osobnym studium przypadku. Może mieć własną bazę danych, komunikację, kontenery, migracje, testy i model skalowania. Nie twórz zależności pomiędzy tymi systemami i nie zakładaj, że wspólnie tworzą jeden produkt.

### `low-level-design/`

Niezależne ćwiczenia projektowania klas, ich odpowiedzialności i współpracy
w czystej Javie. Punktem wyjścia są reguły domenowe, niezmienniki, stan w pamięci
i testy jednostkowe. Plan zadań opisuje `low-level-design/README.md`, procedurę
pracy `low-level-design/PROCEDURE.md`, a szczegółowe zasady `low-level-design/AGENTS.md`.
Ścieżka obejmuje 12 głównych tematów i rozgrzewkę. Bankomat jest wariantem
wydawania reszty, auta i sale korzystają ze wspólnego kalendarza zasobów,
a historia edytora zastępuje workflow w ścieżce głównej. Materiały archive/
są opcjonalne i nie są zależnością aktywnych tematów.
Nie przenoś tutaj wymagań infrastrukturalnych studiów High Level Design.
Priorytetem LLD są struktury danych, algorytmy, złożoność, pamięć i efektywne
użycie czystej Javy. Reguły biznesowe są kontekstem poprawności; nie rozbudowuj
ich kosztem rdzenia technicznego. Dobieraj tablice, bitsety, kopce, kolejki,
listy wiązane i indeksy do operacji, z jawnym kosztem ich utrzymania.
Ten obszar ma jawnie edukacyjny cel. Przy zmianach stosuj
`low-level-design/EDUCATIONAL_STANDARD.md`: objaśniaj po polsku decyzje,
niezmienniki, kontrprzykłady i dowody w testach. Czytelnik ma móc odtworzyć
rozumowanie i przenieść wiedzę do kolejnego zadania, z zachowaniem jego reguł.

### `interview-preparation/`

Dokumentacyjny zbiór pytań i odpowiedzi po polsku, uporządkowany tematycznie
i bez powtórzeń znaczeniowych. Wyjaśnia cel, mechanizm, ograniczenia i awarie;
zadania rozwija przez konkretne przepływy, kontrprzykłady i przykłady kodu,
SQL oraz konfiguracji, bez tworzenia kolejnych aplikacji. Zachowuj istotne
warunki wariantów pytań oraz rozróżniaj
gwarancje API, szczegóły implementacji i różnice wersji. Przy pytaniach
o doświadczenie nie wymyślaj historii zawodowych. Moduł nie ma buildu aplikacji;
sprawdzaj oznaczone przykłady skryptem scripts/check-examples.ps1 w tym module
oraz dokumentację i odsyłacze zgodnie z jego README. Każdy blok wymaga jawnego
kontekstu: kompilowany fragment, kontrprzykład, szkic lub materiał opisowy.

## Zasady pracy

- Przed zmianą przeczytaj główny `README.md` oraz README najbliższe modyfikowanemu modułowi.
- Ogranicz zmianę do obszaru wskazanego przez użytkownika. Nie refaktoryzuj przy okazji niezwiązanych modułów.
- Zachowuj istniejące zmiany użytkownika i nie nadpisuj ich bez wyraźnej zgody.
- Najpierw ustal, czy pracujesz nad laboratorium, projektem portfolio czy studium System Design; te obszary mają różne cele.
- Nie zmieniaj wersji Javy, Spring Boota, narzędzia budowania ani głównych zależności bez uzasadnienia wynikającego z zadania.
- Nie dodawaj zależności, jeśli rozwiązanie jest już dostępne w projekcie lub standardowej bibliotece.
- Nie zapisuj w Git artefaktów generowanych, plików IDE, logów, uploadów, sekretów ani lokalnych plików `.env`.
- Nie wykonuj commitów, pushowania, merge ani przepisywania historii bez wyraźnego polecenia użytkownika.

## Architektura i dokumentacja

- Szanuj granice istniejących modułów i pakietów.
- Logika domenowa nie powinna trafiać do kontrolerów.
- Nie udostępniaj szczegółów warstwy persystencji poza jej granicami bez potrzeby.
- Przy operacjach rozproszonych jawnie uwzględniaj idempotencję, timeouty, retry, kolejność zdarzeń i granice transakcji.
- Gdy zmiana wpływa na API, konfigurację, uruchomienie lub architekturę, zaktualizuj odpowiedni `README.md`.
- Istotne decyzje architektoniczne dokumentuj jako ADR, jeśli dany projekt korzysta z ADR-ów.
- Wyraźnie odróżniaj funkcjonalność zaimplementowaną od planowanej.

## Weryfikacja zmian

Repozytorium nie ma jednego wspólnego buildu. Uruchamiaj komendy z katalogu zmodyfikowanego projektu lub modułu.

Typowe komendy:

```shell
# Maven
mvn --batch-mode --no-transfer-progress verify

# Maven Wrapper na Windows
mvnw.cmd verify

# Gradle Wrapper na Windows
gradlew.bat test
```

Przed zakończeniem zadania:

1. uruchom testy zmodyfikowanego modułu,
2. wykonaj build odpowiedni dla tego modułu,
3. sprawdź diff pod kątem przypadkowych i wygenerowanych plików,
4. podaj użytkownikowi uruchomione komendy i ich wyniki,
5. jeśli pełna weryfikacja nie była możliwa, opisz dokładną przyczynę.
