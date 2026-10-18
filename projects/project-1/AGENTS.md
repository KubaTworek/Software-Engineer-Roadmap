# AGENTS.md — Booking & Capacity Platform

## Rola projektu

`project-1` jest przekrojowym projektem edukacyjnym odpowiadającym całemu
Stage 1 modułu [`backend-engineering`](../../backend-engineering/README.md).
Łączy fundamenty w jednej domenie: organizator publikuje wydarzenie o ograniczonej
pojemności, a wielu klientów równolegle próbuje zarezerwować miejsca.

Centralny niezmiennik brzmi:

```text
0 <= availableCapacity <= totalCapacity
oraz jedno miejsce może zostać zwolnione najwyżej raz dla jednej rezerwacji.
```

Projekt ma pokazywać nie tylko działający happy path, lecz także dlaczego naiwne
rozwiązanie traci aktualizacje, gdzie kończy się gwarancja JVM, jak transakcja i
baza chronią dane oraz jak to zachowanie zmierzyć i przetestować.

## Zakres Stage 1

Zmiany powinny osadzać w domenie projektu następujące bloki:

- Block A: Java Memory Model, synchronizacja, executory, `CompletableFuture`,
  anulowanie, virtual threads, Reactive Streams, poprawność czasu i schedulery;
- Block B: JVM, GC/JIT, JFR, metodologia JMH, testy load/stress/spike/soak,
  open/closed model i poprawność workloadu;
- Block C: lifecycle Springa, proxy/AOP, transakcje, MVC, konfiguracja,
  autoryzacja i właściwy dobór testów;
- Block D: model relacyjny, constrainty, indeksy, plany wykonania, izolacja,
  locking, keyset pagination, Redis/Mongo oraz odtwarzalny read model wyszukiwania;
- Block E: characterization test, bezpieczna refaktoryzacja, jawne zależności od
  czasu i losowości oraz zachowanie semantyki podczas zmiany;
- Block F: DNS, TCP/TLS, pooling, connect/read/request timeout, keep-alive,
  przerwane połączenie i zwielokrotnienie retry pomiędzy warstwami.

Nie przenoś do projektu głównych tematów Stage 2–3, takich jak mikroserwisy,
Kafka, Kubernetes czy koordynacja między regionami. Można wspomnieć granicę, ale
laboratorium ma pozostać dowodem lokalnej poprawności jednej aplikacji.

## Standard kodu edukacyjnego

Komentarze są częścią materiału. Komentuj dokładnie:

- niezmiennik i scenariusz awarii;
- granicę transakcji, locka, pamięci lub protokołu;
- dlaczego wariant `naive` jest błędny i w jakich warunkach błąd wystąpi;
- gwarancję rozwiązania oraz to, czego ono nadal nie gwarantuje;
- ważne zachowanie Springa, Hibernate, JVM, SQL albo sieci;
- cel asercji w teście współbieżności, integracyjnym lub wydajnościowym.

Nie komentuj składni oczywistej z kodu, prostych getterów, przypisania pola ani
samego faktu wywołania metody. Komentarz ma wyjaśniać mechanizm Stage 1, a nie
tłumaczyć Javę linia po linii.

Klasy będące kontrprzykładem muszą mieć jawne oznaczenie `naive` lub `pitfall` w
nazwie/pakiecie i nie mogą być używane przez główny flow. Główna ścieżka
rezerwacji ma stosować atomowy update SQL, jawny `Clock`, trwałe wygasanie oraz
stabilne DTO API.

## Weryfikacja

```shell
mvnw.cmd --batch-mode --no-transfer-progress clean verify
mvnw.cmd --batch-mode --no-transfer-progress -Pjmh-smoke -Dtest=JmhSmokeTest test
mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify
mvnw.cmd --batch-mode --no-transfer-progress -Pmutation-testing org.pitest:pitest-maven:mutationCoverage
k6 run -e TEST_TYPE=smoke performance/k6/reservation-load.js
```

Pierwsze polecenie nie wymaga Dockera. Profil infrastrukturalny ma potwierdzać
wyłącznie zachowanie zależne od prawdziwego PostgreSQL/Redis/Mongo oraz złożenie
ich z prawdziwym HTTP i security. Profil mutacyjny mierzy siłę asercji rdzenia
domeny, a nie procent linii. Zielony test
współbieżności musi dowodzić niezmiennika (np. brak oversellingu), a nie tylko
tego, że wszystkie futures zakończyły się bez wyjątku. Scenariusz k6 wymaga
uruchomionej aplikacji w profilu `load-test`; mierzy przepływ całej usługi, nie
zastępuje JMH ani profilowania JVM.
