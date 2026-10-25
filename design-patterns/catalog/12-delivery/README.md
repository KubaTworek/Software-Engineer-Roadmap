# Wdrożenia, migracje i operacje

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#operations)

Wzorce dostarczania i eksploatacji. **12 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [graceful shutdown skrzynki poleceń](../../labs/05-concurrency.md). Studium w
Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Wdrożenie zmienia działający system, w którym pozostają stare requesty, dane i
wersje kontraktów. Rozdziel publikację artefaktu, włączenie funkcji, migrację danych oraz odzyskanie po
awarii. Każdy mechanizm potrzebuje punktu obserwacji i warunku zatrzymania. Poniższe fragmenty są schematami
polityk operacyjnych, bez wykonywania wdrożeń i zmian w zewnętrznej infrastrukturze.

## Spis kart

- [Blue–Green Deployment](#bluegreen-deployment)
- [Canary Deployment / Progressive Delivery](#canary-deployment--progressive-delivery)
- [Rolling Deployment / Recreate](#rolling-deployment--recreate)
- [Feature Toggle / Release Toggle / Kill Switch](#feature-toggle--release-toggle--kill-switch)
- [Shadow Traffic / Mirroring / Dark Launch](#shadow-traffic--mirroring--dark-launch)
- [Health Endpoint Monitoring / Liveness–Readiness](#health-endpoint-monitoring--livenessreadiness)
- [Graceful Shutdown / Connection Draining](#graceful-shutdown--connection-draining)
-
  [Externalized Configuration / Immutable Infrastructure](#externalized-configuration--immutable-infrastructure)
-
  [Infrastructure as Code / GitOps / Reconciliation Loop](#infrastructure-as-code--gitops--reconciliation-loop)
-
  [Correlation Context / Distributed Tracing / Structured Logging](#correlation-context--distributed-tracing--structured-logging)
- [Checkpoint / Resumable Backfill / High-water Mark](#checkpoint--resumable-backfill--high-water-mark)
- [Backup–Restore / Pilot Light / Warm Standby](#backuprestore--pilot-light--warm-standby)

## Blue–Green Deployment

**Intuicja:** Nowej wersji nie trzeba uruchamiać dopiero po wyłączeniu starej. Blue–Green utrzymuje dwa
przygotowane środowiska i przełącza ruch między nimi. Ułatwia szybkie cofnięcie kierowania requestów, ale oba
środowiska nadal mogą korzystać z danych zmienionych przez nową wersję.

**Problem i mechanizm:** Dwie zgodne pule pozwalają przełączyć ruch z przygotowanej nowej wersji i zachować
poprzednią.

**Role i współpraca:** Blue obsługuje bieżący ruch, Green jest przygotowane i zweryfikowane, router zmienia
kierunek. Schema i kontrakty muszą pozwalać na wymagany okres współistnienia.

**Przykład:** Green przechodzi smoke test, load balancer przełącza ruch, Blue pozostaje gotowe przez okres
obserwacji.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
1. uruchom Green z kompatybilnym schematem
2. wykonaj smoke i kontrolę gotowości
3. przełącz nowy ruch Blue -> Green
4. obserwuj; w razie regresji przywróć routing
5. osobno oceń dane zapisane już przez Green
```

**Konkretny przypadek użycia:** Przełączenie wersji checkout

- **Wejście:** Blue obsługuje ruch, green ma nową wersję i przeszedł sprawdzenia gotowości.
- **Przebieg:** Router przełącza ruch na green; metryki błędów decydują o pozostaniu lub powrocie.
- **Oczekiwany wynik:** Nowe żądania trafiają do green, a blue pozostaje dostępny przez okno odwrotu.
- **Błąd lub granica:** Rollback aplikacji nie cofnie niekompatybilnej migracji bazy; obie wersje muszą
  rozumieć dane.

**Kiedy zastosować:** Dla szybkiego przełączenia i obserwowalnego rollback routingu. Koszt dwóch środowisk i
zgodność danych są częścią decyzji.

**Koszt i pułapka:** Podwojony koszt i kompatybilność schematu; rollback routingu nie cofa zapisanych danych.

**Rozwinięcie i ślad wykonania:** Przed zmianą ruchu sprawdź zgodność Green z bieżącą bazą i kontraktami
odbiorców. Blue po przełączeniu może nadal kończyć stare połączenia. Wyłączenie Green nie cofa commitów, więc
starsza wersja musi umieć odczytać nowy stan albo rollback wymaga dodatkowej migracji.

**Ćwiczenie sprawdzające:** Zaprojektuj rollback po commicie nowego pola w bazie.

## Canary Deployment / Progressive Delivery

**Intuicja:** Błąd nowej wersji może być trudny do wykrycia bez prawdziwego ruchu. Canary kieruje do niej mały
zakres i zwiększa go dopiero po ocenie wyników. Progressive Delivery obejmuje taki sterowany postęp, zamiast
uznawać samo uruchomienie procesu za sukces wdrożenia.

**Problem i mechanizm:** Nowa wersja obsługuje stopniowo rosnącą część ruchu z oceną metryk i warunkami
zatrzymania.

**Role i współpraca:** Kohorta wybiera ruch, metryki porównują wynik, gate zezwala na następny etap lub
zatrzymuje rollout. Próba musi być wystarczająca i porównywalna.

**Przykład:** 1% → 5% → 25% → 100% pod warunkiem braku regresji błędów i p99 dla porównywalnego ruchu.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
udział nowej wersji: 1% -> 5% -> 25% -> 100%
przed każdym krokiem:
    sprawdź błędy, latency i właściwy niezmiennik biznesowy
    oceń wielkość oraz profil próby
    zwiększ, zatrzymaj albo wycofaj ruch
```

**Konkretny przypadek użycia:** Stopniowe wydanie nowej wyceny

- **Wejście:** Nowa wersja dostaje początkowo 1% klientów, potem 10% i 100%.
- **Przebieg:** Każdy etap porównuje błędy, opóźnienia i poprawność kwot w porównywalnych kohortach.
- **Oczekiwany wynik:** Wzrost błędów zatrzymuje rollout przed objęciem wszystkich klientów.
- **Błąd lub granica:** Zbyt mała próbka nie potwierdza bezpieczeństwa; metryka globalna może ukryć problem
  kohorty.

**Kiedy zastosować:** Dla zmian, których skutki chcesz ograniczyć i obserwować etapami. Małe 1% może nie
ujawnić rzadkiego błędu albo specyficznego dużego tenanta.

**Koszt i pułapka:** Mała próba może ukryć rzadkie błędy; potrzebny podział per tenant lub request i ochrona
spójności sesji.

**Rozwinięcie i ślad wykonania:** Wybierz stabilną grupę tenantów lub requestów i porównuj ją z kontrolą o
podobnym profilu. Mała liczba żądań nie daje wiarygodnej obserwacji rzadkiego błędu. Zdefiniuj warunek
zwiększenia udziału, zatrzymania i cofnięcia zamiast traktować upływ pięciu minut jako wystarczający sukces.

**Ćwiczenie sprawdzające:** Wybierz próg zatrzymania oraz minimalny rozmiar próby.

## Rolling Deployment / Recreate

**Intuicja:** Rolling wymienia instancje porcjami, zachowując część zdolności obsługi. Recreate najpierw
zatrzymuje stary wariant, a potem uruchamia nowy, dopuszczając przerwę. Różnica dotyczy również wymagań
kompatybilności: w rolling obie wersje mogą przetwarzać dane jednocześnie.

**Problem i mechanizm:** Instancje wymienia się partiami albo zatrzymuje wszystkie przed uruchomieniem nowych.

**Role i współpraca:** Platforma steruje liczbą nowych i dostępnych replik, readiness dopuszcza ruch, draining
kończy stare operacje. Limity uwzględniają chwilowy surge.

**Przykład:** Rolling utrzymuje stare i nowe wersje jednocześnie przy limicie surge; Recreate dopuszcza
przerwę.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Rolling, 4 repliki:
    uruchom nową -> sprawdź readiness -> wycofaj jedną starą
    powtarzaj przy utrzymaniu minimum dostępnych
Recreate:
    zatrzymaj stare -> uruchom nowe -> odtwórz dostępność
```

**Konkretny przypadek użycia:** Aktualizacja czterech replik

- **Wejście:** Deployment ma cztery instancje; co najmniej trzy muszą obsługiwać ruch.
- **Przebieg:** Rolling wymienia po jednej po sprawdzeniu gotowości, recreate najpierw zatrzymuje wszystkie.
- **Oczekiwany wynik:** Rolling utrzymuje obsługę przy zgodnych wersjach; recreate świadomie wprowadza
  przerwę.
- **Błąd lub granica:** Jednoczesne stare i nowe kontrakty muszą być kompatybilne; sama liczba podów nie
  mierzy wydajności.

**Kiedy zastosować:** Według dopuszczalnej przerwy i zgodności wersji. Policz dodatkowe połączenia do DB i
odbiorców kolejki w okresie wymiany.

**Koszt i pułapka:** Mieszane wersje muszą współpracować; duży surge może wyczerpać limit DB.

**Rozwinięcie i ślad wykonania:** Rolling oznacza współistnienie wersji podczas wymiany, dlatego
kompatybilność dotyczy komunikacji i danych, a nie tylko startu procesu. Recreate upraszcza część tego
przejścia kosztem przerwy. Zanim wybierzesz wariant, policz chwilową liczbę instancji, workerów i połączeń do
wspólnych zasobów.

**Ćwiczenie sprawdzające:** Policz maksymalną liczbę połączeń przy 10 replikach i surge=2.

## Feature Toggle / Release Toggle / Kill Switch

**Intuicja:** Wysłanie kodu i udostępnienie funkcji nie muszą nastąpić jednocześnie. Feature Toggle pozwala
wybierać zachowanie w runtime dla określonego zakresu. Kill Switch szybko blokuje wybraną funkcję podczas
problemu, ale nie cofa operacji, które wcześniej przyjęto i wykonano.

**Problem i mechanizm:** Włączenie zachowania oddziela się od wdrożenia artefaktu z jawnym właścicielem i
terminem usunięcia flagi.

**Role i współpraca:** Źródło konfiguracji dostarcza flagę, punkt decyzji wybiera wariant, właściciel planuje
jej usunięcie. Brak konfiguracji ma ustalony default.

**Przykład:** Nowy kalkulator ceny jest aktywny dla pracowników, a kill switch wyłącza integrację
rekomendacji.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (flags.enabled("new-pricing", tenantId)) {
    return newPricing.calculate(cart);
}
return oldPricing.calculate(cart);
```

**Konkretny przypadek użycia:** Wyłączenie wadliwego rekomendatora

- **Wejście:** Kod rekomendacji jest wdrożony, aktywacja zależy od flagi przypisanej do klienta.
- **Przebieg:** Flaga uruchamia funkcję dla grupy; kill switch kieruje kolejne wywołania na prosty wariant.
- **Oczekiwany wynik:** Można ograniczyć ekspozycję i wyłączyć wadliwą funkcję bez nowego deploymentu.
- **Błąd lub granica:** Wyłączenie flagi nie cofa wcześniej zapisanych rekomendacji ani skutków biznesowych.

**Kiedy zastosować:** Dla etapowego udostępniania i kontroli operacyjnej. Długowieczne kombinacje flag
zwiększają liczbę zachowań wymagających testu.

**Koszt i pułapka:** Kombinacje flag zwiększają stan testów; flagi nie mogą omijać niezmienników ani
autoryzacji.

**Rozwinięcie i ślad wykonania:** Flaga ma właściciela, domyślną wartość przy awarii konfiguracji i termin
usunięcia. Kill switch może zatrzymać nowe wywołania integracji, ale nie anuluje już przyjętych płatności.
Testuj oba warianty oraz zmianę flagi w trakcie długiego procesu, jeśli kontrakt na to pozwala.

**Ćwiczenie sprawdzające:** Zapisz domyślne zachowanie przy awarii dostawcy konfiguracji.

## Shadow Traffic / Mirroring / Dark Launch

**Intuicja:** Można sprawdzić nowe rozwiązanie na kopii ruchu, zanim jego odpowiedź wpłynie na klienta. Shadow
Traffic wysyła obserwowaną pracę do dodatkowej ścieżki i porównuje wynik. Musi izolować skutki: kopia żądania
płatności nie może naprawdę obciążyć klienta drugi raz.

**Problem i mechanizm:** Kopia bezpiecznego ruchu sprawdza nowy wariant bez użycia jego odpowiedzi dla
klienta.

**Role i współpraca:** Primary dostarcza używany wynik, shadow działa w bezpiecznym zakresie, komparator
ocenia semantykę. Dark Launch udostępnia komponent technicznie przed pełnym użyciem.

**Przykład:** Odczyty katalogu są odtwarzane do nowej wyszukiwarki, a różnice wyników trafiają do raportu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Result primary = current.search(query);
shadow.trySubmit(() -> compare(primary, candidate.search(query)));
return primary; // Wynik shadow nie steruje odpowiedzią klienta.
```

**Konkretny przypadek użycia:** Cień wyszukiwarki

- **Wejście:** Stary silnik odpowiada klientowi; kopia zapytania trafia do nowego indeksu.
- **Przebieg:** Porównanie wyników i czasu odbywa się poza krytyczną ścieżką odpowiedzi.
- **Oczekiwany wynik:** Nowy silnik jest oceniony na rzeczywistym rozkładzie zapytań.
- **Błąd lub granica:** Kopia requestu nie może ponownie pobierać płatności ani wysyłać powiadomień; trzeba
  ograniczyć skutki uboczne.

**Kiedy zastosować:** Dla walidacji jakości i wydajności na reprezentatywnych danych. Ustal zasady danych
osobowych i zakaz zewnętrznych skutków kopii.

**Koszt i pułapka:** Kopia zapisu może uruchomić płatność lub email; środowisko shadow wymaga izolowanych
skutków i danych.

**Rozwinięcie i ślad wykonania:** Shadow powinien dostawać bezpieczny, ograniczony ruch z kontrolą skutków.
Nie porównuj dosłownie losowych ID i czasu, tylko semantykę wyniku. Różnica nie musi oznaczać błędu nowego
wariantu: oba mogą czytać różne wersje danych, co trzeba uwzględnić w interpretacji raportu.

**Ćwiczenie sprawdzające:** Wskaż, jak zablokować wszystkie zewnętrzne skutki uboczne.

## Health Endpoint Monitoring / Liveness–Readiness

**Intuicja:** Żywy proces może chwilowo nie być gotowy na requesty, np. podczas ładowania indeksu. Liveness
pyta o zdolność procesu do życia, readiness o przyjmowanie nowej pracy. Pomieszanie tych celów może powodować
masowe restarty podczas awarii wspólnej zależności.

**Problem i mechanizm:** Oddziela się sygnał życia procesu od gotowości do przyjmowania ruchu.

**Role i współpraca:** Probe jest małym kontraktem obserwacji, platforma podejmuje inną akcję dla niegotowości
niż dla martwego procesu. Zależności mają własny timeout kontroli.

**Przykład:** Brak DB może obniżyć readiness API, ale liveness nadal pozostaje poprawne i nie uruchamia
restart loop.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
/live  -> proces potrafi robić postęp
/ready -> instancja może przyjąć nową pracę

brak DB: możliwy brak readiness
restart każdego procesu: nie jest automatyczną naprawą DB
```

**Konkretny przypadek użycia:** Gotowość usługi podczas startu

- **Wejście:** Proces działa, ale indeks lokalny jest jeszcze ładowany.
- **Przebieg:** Liveness potwierdza działanie procesu, readiness nie wpuszcza ruchu do czasu przygotowania.
- **Oczekiwany wynik:** Nieobsługiwane jeszcze żądania trafiają do gotowych replik.
- **Błąd lub granica:** Awaria wspólnej DB nie powinna automatycznie powodować restartów wszystkich procesów
  przez liveness.

**Kiedy zastosować:** Dla platform zarządzających routingiem i restartami. Health check nie dowodzi
poprawności ceny ani zakończenia procesu biznesowego.

**Koszt i pułapka:** Testowanie wszystkich zależności w liveness może wywołać kaskadę restartów; health nie
dowodzi poprawności biznesowej.

**Rozwinięcie i ślad wykonania:** Liveness pyta o zdolność procesu do dalszego działania, readiness o
dopuszczenie nowej pracy. Gdy zależna baza padnie, masowe restarty zwykle nie naprawią bazy. Probe powinny
mieć mały koszt i własny timeout, aby monitoring sam nie obciążał systemu podczas awarii.

**Ćwiczenie sprawdzające:** Zasymuluj awarię DB i określ zachowanie obu probe.

## Graceful Shutdown / Connection Draining

**Intuicja:** Zabicie procesu natychmiast po odebraniu pracy może zostawić niepewny wynik. Graceful Shutdown
najpierw kończy nowe przyjęcia, a później daje przyjętym operacjom ograniczony czas na zakończenie. Connection
Draining odsuwa ruch od wycofywanej instancji, ale nie cofa jej wcześniejszych efektów.

**Problem i mechanizm:** Instancja przestaje przyjmować nową pracę, kończy dopuszczoną w limicie i oddaje
niewykonaną.

**Role i współpraca:** Readiness lub router blokuje nowe wejście, worker opróżnia zaakceptowany zakres,
timeout zamykania ogranicza oczekiwanie. Ack pozostaje po trwałym skutku.

**Przykład:** Readiness spada, serwer zamyka przyjęcia, worker kończy transakcję i dopiero potwierdza
wiadomość.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
readiness.disable();
mailbox.close(); // Koniec nowych przyjęć.
boolean drained = mailbox.awaitTermination(Duration.ofSeconds(5));
if (!drained) reportIncompleteShutdown();
```

**Konkretny przypadek użycia:** Zamykanie workera HTTP

- **Wejście:** Instancja ma 20 aktywnych żądań i dostaje sygnał zatrzymania.
- **Przebieg:** Przestaje być gotowa, wycofuje nowe połączenia i daje aktywnym operacjom ograniczony czas.
- **Oczekiwany wynik:** Zakończone operacje oddają wyniki przed zamknięciem zasobów.
- **Błąd lub granica:** Po upływie limitu część operacji może być przerwana; ponowienia nadal wymagają
  idempotencji.

**Kiedy zastosować:** Dla rolloutów i planowanych zatrzymań. Operacja niekończąca się kooperatywnie wymaga
osobnej polityki, nie nieskończonego czekania w shutdown.

**Koszt i pułapka:** Zbyt krótki termin ubicia daje duplikaty; zbyt długi opóźnia rollout. Potwierdzenie przed
commitem gubi pracę.

**Rozwinięcie i ślad wykonania:** Najpierw wycofaj instancję z nowych przyjęć, potem zakończ lub oddaj
wcześniej przyjętą pracę. BoundedMailbox.close zatrzymuje nowe komendy i opróżnia kolejkę, a awaitTermination
daje właścicielowi kontrolę czekania. W konsumentach granica ack musi pozostać po trwałym efekcie także
podczas zamykania.

**Ćwiczenie sprawdzające:** Zatrzymaj worker między efektem a ack i opisz zachowanie replay.

## Externalized Configuration / Immutable Infrastructure

**Intuicja:** Artefakt różniący się między środowiskami utrudnia stwierdzenie, co naprawdę przetestowano.
Externalized Configuration oddziela kod od adresów i ustawień, a Immutable Infrastructure ogranicza ręczne
poprawianie działających instancji. Nowa konfiguracja również jest zmianą wymagającą kontroli i możliwości
odtworzenia.

**Problem i mechanizm:** Artefakt pozostaje ten sam między środowiskami, a konfiguracja i sekrety są podawane
kontrolowanym kanałem.

**Role i współpraca:** Artefakt ma wersję, konfiguracja osobną walidowaną tożsamość, sekrety zaufany kanał
dostarczenia. Instancja zgłasza gotowość dopiero po sprawdzeniu wymagań.

**Przykład:** Ten sam JAR trafia na staging i produkcję; adresy oraz credentiale pochodzą z konfiguracji
runtime.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
AppConfig config = configSource.load();
validator.requirePositive(config.requestTimeout());
validator.requireEndpoint(config.inventoryUrl());
Application app = new Application(config, secretsProvider);
```

**Konkretny przypadek użycia:** Ten sam obraz w dwóch środowiskach

- **Wejście:** Staging i produkcja używają tego samego obrazu, lecz innych adresów usług.
- **Przebieg:** Konfiguracja jest dostarczana przy starcie i walidowana; zmiana obrazu tworzy nową instancję.
- **Oczekiwany wynik:** Wersja kodu nie zależy od ręcznych zmian na serwerze.
- **Błąd lub granica:** Błędna konfiguracja nadal może zatrzymać wydanie; sekret nie powinien trafić do obrazu
  ani repozytorium.

**Kiedy zastosować:** Dla powtarzalnych wdrożeń. Nie loguj pełnej konfiguracji, jeśli zawiera credentiale, i
nie myl niezmiennego obrazu z niezmiennymi danymi aplikacji.

**Koszt i pułapka:** Zmiana konfiguracji również jest wdrożeniem; sekrety nie mogą trafić do logów i
repozytorium.

**Rozwinięcie i ślad wykonania:** Konfiguracja powinna być walidowana przed gotowością instancji, zwłaszcza
limity czasu, pule i wymagane adresy. Sekret ma własny kanał dostarczenia i rotacji, a nie domyślną wartość w
repozytorium. Zachowaj identyfikator wersji konfiguracji do diagnostyki, ale nie loguj jej wrażliwej treści.

**Ćwiczenie sprawdzające:** Zaprojektuj walidację konfiguracji przy starcie i rotację bez zmiany kodu.

## Infrastructure as Code / GitOps / Reconciliation Loop

**Intuicja:** Ręcznie utrzymywany system może różnić się od opisu i kolejnych instalacji. IaC zapisuje
infrastrukturę jako wersjonowane deklaracje, GitOps wiąże stosowanie ich z repozytorium, a reconciliation
porównuje stan docelowy z rzeczywistym. Pętla musi bezpiecznie ponawiać częściowo wykonane działania.

**Problem i mechanizm:** Stan docelowy infrastruktury jest wersjonowany i porównywany z rzeczywistym przez
kontrolowany proces.

**Role i współpraca:** Deklaracja opisuje cel, kontroler obserwuje i koryguje, proces review zatwierdza
zmianę. W incydencie może być potrzebne wstrzymanie automatycznej korekty.

**Przykład:** Deklaracja deploymentu określa 3 repliki; kontroler usuwa drift liczby instancji.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
desired replicas: 3
observed replicas: 2
reconcile: utwórz brakującą instancję
po częściowej awarii: obserwuj ponownie i wykonaj tylko brakujący krok
```

**Konkretny przypadek użycia:** Odtwarzanie konfiguracji klastra

- **Wejście:** Git deklaruje trzy repliki i limit zasobów; ktoś ręcznie ustawił jedną.
- **Przebieg:** Kontroler porównuje stan oczekiwany z obserwowanym i ponawia uzgadnianie.
- **Oczekiwany wynik:** Klaster wraca do trzech replik albo pokazuje widoczny błąd uzgadniania.
- **Błąd lub granica:** Nieudana operacja dostawcy może zostawić stan częściowy; deklaracja nie daje
  atomowości całej infrastruktury.

**Kiedy zastosować:** Dla powtarzalności i kontroli drift. Błędna deklaracja propagowana automatycznie także
jest awarią; automatyzacja nie zastępuje oceny skutków.

**Koszt i pułapka:** IaC i GitOps są praktykami operacyjnymi, nie wzorcami GoF; błędna deklaracja może
automatycznie propagować awarię.

**Rozwinięcie i ślad wykonania:** Kontroler porównuje stan deklarowany z obserwowanym i wykonuje działania, aż
różnica zniknie. Powtarzalność operacji jest konieczna, bo awaria może nastąpić po częściowym skutku. Ręczna
zmiana produkcji może zostać nadpisana przez reconciliation; procedura incydentu powinna wiedzieć, jak
zatrzymać ten proces.

**Ćwiczenie sprawdzające:** Określ proces review i zatrzymania błędnej rekoncyliacji.

## Correlation Context / Distributed Tracing / Structured Logging

**Intuicja:** Request może przejść przez kilka usług i kolejkę, więc sam lokalny log nie wyjaśnia całości
czasu. Correlation Context łączy etapy, tracing pokazuje ich relacje, structured logging daje pola do zapytań.
To sygnały diagnostyczne, nie automatycznie trwały audyt decyzji biznesowej.

**Problem i mechanizm:** Przepływ ma propagowany kontekst i ustrukturyzowane sygnały do odtworzenia czasu oraz
przyczyn.

**Role i współpraca:** TraceId wiąże ślad, span opisuje etap, businessId pozwala szukać operacji. Async
potrzebuje jawnej propagacji i właściwego powiązania kontekstu.

**Przykład:** traceId łączy HTTP, outbox i konsumenta; orderId jest polem logu, nie wysoko-kardynalną etykietą
każdej metryki.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
logger.info("Order confirmed", fields(
        "orderId", order.id(),
        "traceId", trace.currentId(),
        "durationMs", elapsedMillis));
```

**Konkretny przypadek użycia:** Śledzenie zamówienia przez trzy usługi

- **Wejście:** Żądanie HTTP uruchamia płatność, a wiadomość uruchamia wysyłkę.
- **Przebieg:** Trace context przechodzi przez dozwolone nagłówki i metadane eventu; logi zawierają
  identyfikatory.
- **Oczekiwany wynik:** Można powiązać fragmenty jednego przepływu i wskazać odcinek opóźnienia.
- **Błąd lub granica:** Próbkowanie oznacza brak części śladów; identyfikator korelacji nie zapewnia
  idempotencji ani kolejności.

**Kiedy zastosować:** Dla diagnozowania przepływów i opóźnień. Nie używaj dowolnie unikalnych ID jako etykiet
wszystkich metryk, bo cardinality ma koszt.

**Koszt i pułapka:** Tracing może być próbkowany; korelacja nie zastępuje trwałego audytu. Nagłówki z zewnątrz
wymagają walidacji.

**Rozwinięcie i ślad wykonania:** Trace wiąże etapy techniczne, a orderId pozwala szukać konkretnego procesu
biznesowego. Zbyt wiele unikalnych etykiet metryk zwiększa koszt cardinality. Ustal propagację przez async i
granice danych osobowych; brak spanu przy próbkowaniu nie dowodzi braku wykonania operacji.

**Ćwiczenie sprawdzające:** Prześledź jedną operację przez async i wskaż brakujący span.

## Checkpoint / Resumable Backfill / High-water Mark

**Intuicja:** Długi backfill może przerwać się po godzinach pracy. Checkpoint zapisuje stabilny postęp, a
High-water Mark wyznacza skończony zakres wejścia. Wznowienie musi tolerować granicę, na której efekt porcji
już się zapisał, ale checkpoint jeszcze nie zdążył, więc część zostanie wykonana ponownie.

**Problem i mechanizm:** Długa operacja ma stabilny postęp i może wznowić bez pominięcia oraz szkodliwych
powtórzeń.

**Role i współpraca:** Kursor określa zakres, worker stosuje idempotentny zapis, checkpoint utrwala postęp.
Zmiany nowsze niż początkowy snapshot wymagają osobnego przejęcia.

**Przykład:** Backfill czyta zakresy ID do zapamiętanego high-water mark, zapisuje porcję i checkpoint atomowo
lub idempotentnie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Batch batch = source.after(checkpoint, highWaterMark, batchSize);
transaction.run(() -> {
    target.upsertWithVersion(batch.records());
    checkpoints.advance(batch.lastKey());
});
```

**Konkretny przypadek użycia:** Backfill dziesięciu milionów rekordów

- **Wejście:** Zadanie czyta stabilny klucz rosnący, checkpoint wskazuje id=5000.
- **Przebieg:** Przetwarza porcję, utrwala wynik i bezpieczny checkpoint; restart wznawia od potwierdzonej
  granicy.
- **Oczekiwany wynik:** Awaria powtarza najwyżej kontrolowany fragment, a idempotentny zapis nie dubluje
  skutku.
- **Błąd lub granica:** Checkpoint zapisany przed wynikiem pomija dane; zmieniający się zbiór wymaga jawnego
  watermark i rekoncyliacji.

**Kiedy zastosować:** Dla migracji i dużych operacji batch. Nie używaj przesuwającego się OFFSET jako
gwarancji braku pominięć na zmienianym zbiorze.

**Koszt i pułapka:** OFFSET przy zmiennych danych może pomijać rekordy; checkpoint bez efektu w tej samej
granicy wymaga deduplikacji.

**Rozwinięcie i ślad wykonania:** Porcja powinna mieć stabilny zakres i warunek nieprzesłonięcia nowszego
zapisu. Crash po zapisie przed checkpointem powoduje powtórzenie porcji, więc zapis musi być idempotentny.
High-water mark odcina skończony zbiór wejścia, ale nowe zmiany wymagają osobnej fazy przejęcia albo feedu.

**Ćwiczenie sprawdzające:** Przerwij po zapisie porcji przed checkpointem i uruchom ponownie.

## Backup–Restore / Pilot Light / Warm Standby

**Intuicja:** Samo posiadanie kopii nie mówi, jak szybko system wróci i ile danych straci. Backup–Restore,
Pilot Light i Warm Standby wybierają różne ilości wcześniej przygotowanej infrastruktury. Recovery musi
kończyć się użytecznym, sprawdzonym stanem biznesowym, nie tylko startem procesów.

**Problem i mechanizm:** Wariant recovery dobiera się do dopuszczalnej utraty danych i czasu odtworzenia.

**Role i współpraca:** Backup zachowuje dane, plan odtworzenia infrastrukturę i zależności, RPO określa
dopuszczalną utratę, RTO czas powrotu. Test restore weryfikuje założenia.

**Przykład:** Warm standby utrzymuje minimalną gotową infrastrukturę i repliki, backup-restore odtwarza je z
kopii.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Backup–Restore: odtwórz dane i zasoby po awarii
Pilot Light: część podstaw już działa, resztę uruchom
Warm Standby: mniejsza gotowa instalacja przejmuje i skaluje ruch
test: sprawdź rekordy kontrolne oraz proces po odzyskaniu
```

**Konkretny przypadek użycia:** Odtworzenie regionu

- **Wejście:** Kopia danych jest wykonywana co 15 minut; region główny przestaje działać.
- **Przebieg:** Zespół uruchamia zapasową infrastrukturę, odtwarza dane i weryfikuje gotowość przed ruchem.
- **Oczekiwany wynik:** Ćwiczenie mierzy faktyczne RPO i RTO dla wybranej strategii.
- **Błąd lub granica:** Sama obecność backupu nie dowodzi odtwarzalności; brak klucza szyfrującego może
  uniemożliwić odzyskanie danych.

**Kiedy zastosować:** Według kosztu i wymaganej ciągłości działania. Replika może powielić błędne usunięcie,
więc nie zastępuje niezależnej polityki backupu.

**Koszt i pułapka:** Backup bez testu restore nie dowodzi recovery; regiony mogą współdzielić ten sam błąd lub
usunięcie.

**Rozwinięcie i ślad wykonania:** Test recovery zaczyna się od dostępnego backupu i odtwarza usługi do punktu
użytecznego biznesowo. Zmierz czas i sprawdź spójność kontrolnych danych, nie tylko start procesów. Usunięte
dane mogą występować w kopii; procedura musi uwzględniać wymagane naprawy po restore.

**Ćwiczenie sprawdzające:** Zdefiniuj RPO/RTO i wykonaj scenariusz odtworzenia z odczytem kontrolnych
rekordów.
