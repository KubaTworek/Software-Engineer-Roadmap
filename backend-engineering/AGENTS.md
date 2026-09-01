# AGENTS.md — backend-engineering

## Czym jest ten moduł

`backend-engineering` jest wykonywalnym kompendium wiedzy dla Java Backend
Engineera. Nie jest jedną aplikacją, kursem do zaliczenia ani katalogiem
frameworków. Łączy krótkie laboratoria, kontrprzykłady, symulacje, testy
gwarancji oraz kilka samodzielnych aplikacji demonstracyjnych.

Podział na `stage_1`, `stage_2` i `stage_3` określa poziom kontekstu:

- `stage_1` — mechanizm i lokalna poprawność: Java, JVM, Spring, dane i sieć;
- `stage_2` — granice komponentów: domena, API, integracje, delivery i security;
- `stage_3` — zachowanie całego systemu pod skalą, awarią i w środowisku cloud;

Numer etapu nie oznacza statusu ukończenia ani ważności tematu. To samo pojęcie
może wracać na kolejnej granicy, np. lock JVM → blokada PostgreSQL → lease →
fencing → test historii.

## Co agent powinien chronić

Najważniejsza jest wartość merytoryczna i dydaktyczna. Przed zmianą przeczytaj:

1. [`README.md`](README.md),
2. [`EDITORIAL_STANDARD.md`](EDITORIAL_STANDARD.md),
3. najbliższy `README.md` w modyfikowanym katalogu,
4. lokalny `AGENTS.md`, jeżeli niższy katalog kiedyś go otrzyma.

Każde laboratorium powinno umożliwiać rozpoznanie sześciu elementów:

`problem → niezmiennik → naiwny przykład → poprawne rozwiązanie → test → ograniczenia produkcyjne`

Nie rozbudowuj przykładu tylko po to, aby przypominał aplikację produkcyjną.
Minimalny model jest właściwy, jeśli jednoznacznie pokazuje mechanizm i nazywa
granice gwarancji. Z drugiej strony nie przedstawiaj fake'a, H2 ani symulacji
in-memory jako dowodu zachowania PostgreSQL, Redis, Kafki lub sieci.

## Zasady kodu i dokumentacji

- Utrzymuj zgodność pakietu ze ścieżką od `src/main/java` lub `src/test/java`.
- Preferuj nazwy ujawniające rolę edukacyjną: `Naive*`, `Correct*`, `*Simulation`
  albo nazwę adaptera na granicy produkcyjnej.
- Celowo błędny przykład musi być nazwany jako kontrprzykład, opisany w README i
  objęty testem pokazującym naruszenie gwarancji.
- Komentarz ma wyjaśniać nieoczywistą gwarancję, failure mode albo kompromis;
  nie powinien przepisywać kodu innymi słowami.
- README aktualizuj razem ze zmianą kontraktu, przykładu, komendy uruchomienia lub
  granicy produkcyjnej.
- Powtórzenie między etapami jest uzasadnione tylko wtedy, gdy wnosi nowy
  niezmiennik, failure mode, poziom infrastruktury albo koszt operacyjny.
- Nie dodawaj frameworka, zależności ani osobnej aplikacji bez konkretnego
  pytania, na które istniejący kod nie potrafi odpowiedzieć.

## Pełnoprawne obszary roadmapy

Reactive Streams, GraphQL, gRPC, WebSocket i silniki wyszukiwania są częścią
głównego kompendium. Umieszczaj je przy problemie, który rozwijają:

- Reactive Streams w Stage 1A — współbieżność i backpressure;
- silniki wyszukiwania w Stage 1D — model danych i odtwarzalny indeks;
- GraphQL i gRPC w Stage 2A — API oraz integracje;
- WebSocket w Stage 2B — komunikacja zdarzeniowa i lifecycle połączenia.

Każde laboratorium w tych obszarach powinno:

- wskazywać problem uzasadniający wybór technologii;
- podawać wymagane fundamenty z etapów 1–3;
- porównywać rozwiązanie z prostszą alternatywą;
- zawierać kryteria „kiedy użyć” i „kiedy nie użyć”;
- oddzielać semantykę protokołu lub modelu od API konkretnego frameworka;
- otrzymać oznaczenie zakresu właściwe dla swojego etapu.

Nie kopiuj fundamentów między blokami. Backpressure, deadline, idempotencja,
ewolucja kontraktu, bezpieczeństwo i obserwowalność powinny być linkowane z
miejsca źródłowego, a nowe laboratorium ma pokazywać nową granicę gwarancji.

## Testy jako dowód

- Nazwa testu opisuje zachowanie lub niezmiennik.
- Każdy test powinien dowodzić gwarancji, reprodukować kontrprzykład albo
  dokumentować istotną granicę.
- Nie zwiększaj liczby testów bez zwiększenia siły dowodu.
- Testy z fake'em i symulacje pozostają szybkie i deterministyczne.
- Kontrakty prawdziwych silników należą do profilu `infrastructure-tests`.
- Benchmark najpierw potwierdza równoważność wyników, a dopiero potem mierzy koszt.

## Budowanie i weryfikacja

Podstawowy build uruchamiaj z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Testy PostgreSQL, Redis i Kafki wymagają Dockera:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify
```

Po zmianach dokumentacji uruchom także:

```powershell
.\scripts\update-material-cards.ps1 -Check
```

Przed zakończeniem sprawdź `git diff --check` i linki w zmienionych README.
Samodzielne laboratoria Maven mogą mieć własny `pom.xml`; użyj wtedy wrappera z
tego katalogu oraz `-f <ścieżka-do-pom.xml>`.

## Granice zmian

- Zachowuj istniejące zmiany użytkownika i ograniczaj edycję do zleconego obszaru.
- Nie zmieniaj wspólnej wersji Javy 21 lub Spring Boot 4.0.3 bez jawnego
  uzasadnienia i aktualizacji głównego README.
- Nie zapisuj sekretów, lokalnych plików środowiska ani artefaktów `target`.
- Nie wykonuj commitów, pushowania, merge ani przepisywania historii bez
  wyraźnego polecenia użytkownika.
