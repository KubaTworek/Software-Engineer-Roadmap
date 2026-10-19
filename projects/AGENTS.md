# AGENTS.md — moduł `projects`

## Czym jest ten moduł

Katalog `projects` zawiera trzy przekrojowe projekty edukacyjno-portfolio. Każdy z nich odpowiada jednemu etapowi modułu [`backend-engineering`](../backend-engineering/README.md) i służy do zastosowania wiedzy z tego etapu w spójnym problemie biznesowym.

`backend-engineering` wyjaśnia mechanizmy w izolowanych laboratoriach. `projects` łączy te mechanizmy w działające systemy, w których decyzje techniczne mają konsekwencje dla domeny, architektury, testów i eksploatacji. Projekty nie są checklistą technologii ani zbiorem niezależnych przykładów CRUD.

## Przypisanie projektów do etapów

| Projekt | Odpowiada | Domena i główny poziom problemu |
| --- | --- | --- |
| [`project-1`](project-1/README.md) — Booking & Capacity Platform | `backend-engineering` Stage 1 | Fundamenty poprawnego backendu: Java i współbieżność, JVM i wydajność, Spring, SQL/NoSQL, transakcje, refaktoryzacja oraz testowalność. Centralną gwarancją domenową jest brak oversellingu ograniczonego zasobu. |
| [`project-2`](project-2/README.md) — Marketplace Order Fulfillment Platform | `backend-engineering` Stage 2 | Architektura i integracje: granice modułów, DDD, use case'y, kontrakty HTTP i zdarzeń, outbox, Kafka, idempotencja, eventual consistency, delivery i podstawy operacyjne. Centralnym problemem jest poprawna realizacja zamówienia przechodzącego przez kilka bounded contextów. |
| [`project-3`](project-3/README.md) — Microservices Ticketing Platform | `backend-engineering` Stage 3 | Zachowanie systemu rozproszonego pod obciążeniem i podczas awarii: skalowanie, resilience, koordynacja, obserwowalność, testy obciążeniowe, cloud, IaC, DR i koszt. Centralnym problemem jest obronienie działania platformy ticketingowej za pomocą mierzalnych gwarancji i dowodów operacyjnych. |

Progresję należy rozumieć następująco:

```text
project-1: poprawność pojedynczej aplikacji i danych
    -> project-2: poprawność granic modułów, kontraktów i przepływów asynchronicznych
        -> project-3: poprawność oraz operowalność systemu rozproszonego pod obciążeniem i awarią
```

## Granice zakresu

- Każdy projekt rozwija przede wszystkim zagadnienia przypisanego mu etapu.
- Projekt może korzystać z fundamentów wcześniejszych etapów, ale nie powinien ponownie przedstawiać ich jako swojego głównego celu. `project-2` zakłada poprawne transakcje z Stage 1 i dodaje granice modułów oraz integracji. `project-3` zakłada fundamenty Stage 1–2 i dodaje skalę, częściowe awarie oraz operacje produkcyjne.
- Technologia sama nie określa projektu. O umiejscowieniu zagadnienia decyduje problem i gwarancja, którą przykład ma udowodnić.
- Nie przenoś tematów pomiędzy projektami tylko po to, aby wyrównać ich rozmiar. Zakres ma wynikać z etapów roadmapy.
- Projekty są niezależnymi aplikacjami. Nie dodawaj zależności kodowej pomiędzy `project-1`, `project-2` i `project-3` ani wspólnego modelu domenowego współdzielonego przez te projekty.

## Jak rozwijać projekty

Przed zmianą w projekcie:

1. Przeczytaj jego `README.md` oraz dokumentację decyzji architektonicznych.
2. Przeczytaj README odpowiedniego stage'a i bloków w `backend-engineering` związanych ze zmianą.
3. Nazwij problem, niezmiennik lub kontrakt, który ma zostać pokazany.
4. Ustal, czy zmiana rzeczywiście należy do poziomu danego projektu.

Podczas implementacji:

- buduj spójny przepływ biznesowy zamiast dokładać odizolowaną demonstrację frameworka;
- adaptuj wiedzę z `backend-engineering` do domeny projektu — nie kopiuj laboratorium bez kontekstu i decyzji architektonicznej;
- utrzymuj kierunek zależności i własność danych właściwe dla architektury danego projektu;
- pokazuj rozwiązanie wraz z ograniczeniami i scenariuszem awarii, a nie tylko happy path;
- ważne decyzje architektoniczne, kompromisy i odrzucone alternatywy zapisuj jako ADR;
- przy zmianie kontraktu, sposobu uruchomienia lub architektury aktualizuj odpowiedni `README.md` w tym samym zadaniu;
- nie deklaruj gwarancji, której nie potwierdza kod, test, pomiar albo wykonywalny scenariusz.

## Oczekiwania wobec testów

Test ma dowodzić konkretnej właściwości właściwej dla poziomu projektu:

- w `project-1`: niezmienników domenowych, współbieżności, transakcji, zachowania bazy, wydajności i testowalności;
- w `project-2`: reguł zależności, granic transakcji, zgodności kontraktów, idempotencji oraz zachowania integracji i procesów asynchronicznych;
- w `project-3`: reakcji na częściowe awarie, przeciążenie i degradację, propagacji telemetrii, SLO, recovery oraz decyzji infrastrukturalnych.

Nie zwiększaj liczby testów dla samego pokrycia. Każdy test powinien potwierdzać gwarancję, odtwarzać istotny błąd albo chronić kontrakt. Testy zależne od prawdziwej infrastruktury powinny być jawnie wydzielone i mieć udokumentowany sposób uruchomienia.

## Dokumentacja i Definition of Done

Zmiana jest kompletna, gdy:

- kod przedstawia zagadnienie w kontekście domeny projektu;
- testy potwierdzają deklarowaną gwarancję lub kontrprzykład;
- README prowadzi od problemu i ograniczeń do uruchomienia właściwego scenariusza;
- decyzje o istotnych kompromisach mają ADR;
- projekt nadal buduje się i uruchamia niezależnie od pozostałych projektów;
- nie wprowadzono przypadkowego zakresu należącego do innego stage'a.

Polecenia weryfikacyjne dobieraj do builda i infrastruktury opisanych w README danego projektu. Nie zakładaj, że jedno polecenie z katalogu `projects` weryfikuje wszystkie trzy aplikacje.

## Zakres obowiązywania instrukcji

Ten plik obowiązuje cały katalog `projects`. Jeżeli podkatalog otrzyma własny `AGENTS.md`, może doprecyzować instrukcje dla konkretnej aplikacji, ale nie może zmienić przypisania `project-1` → Stage 1, `project-2` → Stage 2 i `project-3` → Stage 3.
