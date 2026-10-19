# Projekty portfolio

Katalog `projects` przekłada laboratoria z `backend-engineering` na trzy większe,
spójne konteksty biznesowe. Każdy projekt odpowiada jednemu etapowi roadmapy,
ale zakłada znajomość wcześniejszych etapów. Nie są to trzy moduły jednego systemu
i nie współdzielą modelu domenowego.

## Mapa projektów

| Projekt | Etap roadmapy | Główny problem | Najważniejsza gwarancja |
| --- | --- | --- | --- |
| [Project 1 — Booking & Capacity Platform](project-1/README.md) | Stage 1 | poprawność pojedynczej aplikacji, współbieżność, Spring, SQL/NoSQL i wydajność | liczba potwierdzonych rezerwacji nigdy nie przekracza pojemności zasobu |
| [Project 2 — Marketplace Order Fulfillment Platform](project-2/README.md) | Stage 2 | granice modułów, DDD, kontrakty, outbox, Kafka, CDC i delivery | zamówienie przechodzi przez moduły bez utraty zdarzeń i podwójnych efektów |
| [Project 3 — Microservices Ticketing Platform](project-3/README.md) | Stage 3 | częściowe awarie, przeciążenie, skalowanie, observability, cloud i DR | system zachowuje jawne niezmienniki i pozostaje diagnozowalny podczas awarii |

Progresja odpowiedzialności wygląda następująco:

```text
Project 1: poprawność kodu, transakcji i danych w pojedynczej aplikacji
    -> Project 2: poprawność granic modułów, kontraktów i przepływów asynchronicznych
        -> Project 3: poprawność i operowalność systemu rozproszonego pod obciążeniem
```

Powtórzenia są celowe tylko wtedy, gdy zmienia się granica gwarancji. Przykładowo
Project 1 pokazuje blokadę JVM i PostgreSQL, Project 2 idempotentny przepływ przez
outbox i brokera, a Project 3 lease, fencing i test całej historii operacji.

## Najkrótsza weryfikacja

Komendy należy uruchamiać z katalogu właściwego projektu.

```powershell
# Project 1 — szybka suite
cd project-1
.\mvnw.cmd --batch-mode --no-transfer-progress verify

# Project 2 — testy jednostkowe, architektury i infrastruktury
cd ..\project-2
.\gradlew.bat clean test integrationTest --no-daemon

# Project 3 — szybki reactor wielu usług
cd ..\project-3
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Docker jest wymagany przez profile infrastrukturalne, CDC oraz pełne scenariusze
systemowe. Szczegółowe komendy, profile i ograniczenia znajdują się w README
konkretnego projektu.

## Jak czytać projekty

1. Zacznij od tabeli gwarancji w README projektu.
2. Przejdź główny przepływ biznesowy i wskaż granice transakcji.
3. Uruchom test dowodzący niezmiennika, a następnie test kontrprzykładu lub awarii.
4. Przeczytaj ADR-y i sprawdź, czego rozwiązanie świadomie nie gwarantuje.
5. Dopiero potem uruchamiaj prawdziwą infrastrukturę, test obciążeniowy lub drill.

Kod ma charakter edukacyjny. Komentarze wyjaśniają mechanizmy właściwe dla danego
etapu, failure windows i kompromisy, ale nie powinny tłumaczyć podstawowej składni
Javy. Funkcjonalność planowana, szablon eksperymentu oraz potwierdzony wynik muszą
być oznaczone odmiennie.

## Granice

- Project 1 nie powinien stawać się mikroserwisem tylko po to, aby pokazać Stage 2.
- Project 2 pozostaje modularnym monolitem; infrastruktura ma dowodzić kontraktów,
  a nie ukrywać niepoprawne granice domenowe.
- Project 3 może używać mechanizmów wcześniejszych etapów, lecz skupia się na ich
  zachowaniu między procesami, podczas awarii i pod obciążeniem.
- Dane dostępowe w konfiguracji lokalnej są wyłącznie wartościami developerskimi.
  Sekrety produkcyjne nie należą do Git.

Instrukcje dla agentów znajdują się w [AGENTS.md](AGENTS.md).
