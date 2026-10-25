# Design Patterns — kompendium w Markdown

Moduł dokumentacyjny z **252 kartami w 18 kategoriach**. Cała wiedza o wzorcach, fragmenty kodu, rozwinięte
modele i scenariusze poprawności znajdują się w plikach Markdown. Głównym punktem nauki są karty: intuicja,
role, mechanizm, konkretny przypadek użycia, koszt, awaria i porównanie z prostszym rozwiązaniem.

## Punkt wejścia

- [Indeks alfabetyczny](INDEKS.md) — wszystkie karty, warianty i aliasy.
- [Katalog](catalog/01-creational/README.md) — zacznij od wzorców kreacyjnych lub wybierz kategorię z tabeli.
- [Przewodnik decyzji](PRZEWODNIK.md) — porównania wzorców i złożenie ich w proces.
- [Mapa przykładów](LABORATORIA.md) — dłuższe przykłady i scenariusze poprawności w Markdown.
- [Studia przypadków](labs/README.md) — sześć przepływów opisanych krok po kroku.
- [Materiały](MATERIALY.md) — książki, dokumentacja i bezpłatne źródła.
- [Zasady redakcyjne](AGENTS.md) — utrzymywanie samodzielnych opisów i przykładów.

## Zakres i forma materiału

Komplet **23 klasycznych GoF** ma implementacje i przykłady oczekiwanych zachowań osadzone bezpośrednio w
dokumentach. Pozostałe karty obejmują idiomy Javy, DDD, architekturę, persystencję, integrację, spójność,
odporność, skalowanie, API, wdrożenia, bezpieczeństwo i testowanie.

252 to liczba kart, nie wszystkich nazw: część porównuje pokrewne warianty. Ukośnik w tytule może oddzielać
aliasy lub odrębne rozwiązania, których różnice wyjaśnia treść. Nie istnieje jedna zamknięta lista wszystkich
wzorców oprogramowania; zakres tego kompendium określają indeks i kategorie.

**Status przykładów:** to materiał dokumentacyjny. Fragmenty Java odnoszą się do Java 21, jeśli nie zaznaczono
inaczej. Szkice zakładają opisane porty i typy; pełniejsze modele zawierają własne importy i definicje w
rozwijanych sekcjach MD. SQL wymaga dopasowania do silnika, HTTP opisuje kontrakt, pseudokod kolejność
decyzji. Scenariusze zapisane w konwencji JUnit pokazują oczekiwane wyniki, ale nie są automatycznie
wykonywane przez ten moduł. Nie ma plików źródłowych Java, buildu Maven, infrastruktury ani aplikacji do
uruchomienia.

Najpierw czytaj krótki mechanizm przy wzorcu. Rozwiń dłuższy model, gdy chcesz zobaczyć wszystkie typy,
kontrolę błędów i zależności. Dawne implementacje oraz asercje zachowano w Markdown, żeby żadna istotna
informacja nie wymagała otwarcia usuniętej klasy.

## Mapa modułu

| # | Kategoria | Karty | Rdzeń |
| --- | --- | ---: | --- |
| 1 | [GoF: kreacyjne](catalog/01-creational/README.md) | 5 | Factory Method, Abstract Factory, Builder, Prototype, Singleton |
| 2 | [GoF: strukturalne](catalog/02-structural/README.md) | 7 | Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy |
| 3 | [GoF: behawioralne](catalog/03-behavioral/README.md) | 11 | Chain, Command, Interpreter, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor |
| 4 | [Java i kompozycja kodu](catalog/04-java/README.md) | 15 | DI, immutability, Result, Null Object, pool, execute around, functional core |
| 5 | [DDD](catalog/05-domain/README.md) | 15 | Entity, Value Object, Aggregate, Repository, Specification, Context Map, ACL |
| 6 | [Architektura](catalog/06-architecture/README.md) | 18 | warstwy, hexagonal, clean/onion, modularny monolit, mikroserwisy, gateway, BFF, cells |
| 7 | [Persystencja i cache](catalog/07-persistence/README.md) | 20 | Data Mapper, Active Record, Unit of Work, locking, DTO, cache, migracje |
| 8 | [Messaging](catalog/08-messaging/README.md) | 38 | kanały, routing, Splitter/Aggregator, Scatter–Gather, Inbox, DLQ, replay |
| 9 | [Spójność i procesy](catalog/09-consistency/README.md) | 15 | Outbox, Saga, CQRS, Event Sourcing, CDC, TCC, 2PC, fencing, quorum, CRDT |
| 10 | [Odporność](catalog/10-resilience/README.md) | 16 | deadline, retry, Circuit Breaker, Bulkhead, limity, backpressure, hedging |
| 11 | [Skalowanie](catalog/11-scaling/README.md) | 17 | sharding, replikacja, hashing, fan-out, MapReduce, streamy, indeksy, CDN |
| 12 | [Wdrożenia i operacje](catalog/12-delivery/README.md) | 12 | blue–green, canary, flagi, shadow, health, shutdown, backfill, recovery |
| 13 | [Bezpieczeństwo](catalog/13-security/README.md) | 8 | policy enforcement, RBAC/ABAC, capability, tożsamość, klucze, audyt |
| 14 | [Testowanie](catalog/14-testing/README.md) | 11 | doubles, builder danych, kontrakty, modele, właściwości, symulacja, recovery |
| 15 | [Mapowanie i sesje](catalog/15-mapping/README.md) | 14 | relacje, dziedziczenie ORM, Query Object, tożsamość, session state, Registry |
| 16 | [API](catalog/16-api/README.md) | 12 | async request/reply, webhook, ETag, Problem Details, cursor, kompatybilność |
| 17 | [Współbieżność](catalog/17-concurrency/README.md) | 9 | Active Object, Balking, Copy-on-Write, snapshoty, lock striping, CAS, Fork–Join |
| 18 | [Style integracji i operacje EIP](catalog/18-integration-operations/README.md) | 9 | pliki/RPC/baza/messaging, activator, broker, Control Bus, Detour, Smart Proxy |

## Jak pracować z kartą

1. Nazwij problem bez używania nazwy wzorca.
2. Prześledź role i kod na danych konkretnego przypadku użycia.
3. Wskaż wejście, moment decyzji, wynik i niezmiennik.
4. Zmień jeden warunek: duplikat, brak danych, timeout, konflikt lub awarię.
5. Oceń prostszy wariant i koszt wprowadzenia dodatkowej struktury.

Przykłady nie są opisami wdrożeń konkretnych firm. Liczby są założeniami scenariusza, nie wynikami benchmarku.
Kod in-memory pokazuje granicę lokalnego stanu, nie gwarancję trwałego commitu, brokera lub klastra.

## Weryfikacja dokumentacji

Wymagany jest PowerShell 7. Z katalogu `design-patterns`:

```powershell
pwsh -File scripts/check-catalog.ps1
```

Kontrola sprawdza komplet 252 kart i 23 GoF, indeks, strukturę opisów, konkretne przypadki użycia, bloki kodu
oraz brak źródeł i artefaktów Java. Nie kompiluje fragmentów, nie wykonuje asercji JUnit i nie uruchamia SQL
ani infrastruktury.

Z korzenia repozytorium:

```powershell
pwsh -File scripts/check-documentation.ps1
git diff --check
```

Pierwsze polecenie sprawdza lokalne odsyłacze i kotwice. CI tego modułu wykonuje kontrole dokumentacji i nie
wymaga JDK ani Mavena. Weryfikację merytoryczną prowadź przez konkretny ślad wykonania i kontrprzykład, a
zachowanie wersjonowanych API porównuj z dokumentacją podaną w materiałach.

## Miejsce w roadmapie

[Backend engineering](../backend-engineering/README.md) zawiera wykonywalne laboratoria narzędzi,
[LLD](../low-level-design/README.md) ćwiczenia struktur i algorytmów,
[System Design](../system-design/README.md) większe studia, [projekty](../projects/README.md) zastosowania w
domenach, a [pytania i odpowiedzi](../interview-preparation/README.md) materiał do powtórek. To kompendium
opisuje wzorce i decyzje. Nie łączy buildów pozostałych części repozytorium.
