# Studia przypadków w Markdown

[Moduł](../README.md) · [Mapa przykładów](../LABORATORIA.md) · [Indeks](../INDEKS.md)

Każdy dokument łączy konkretny problem z rolami, kodem, śladem wykonania, niezmiennikiem, kosztem i awarią.
Pełne modele oraz asercje znajdują się w tych samych plikach MD, w rozwijanych sekcjach. Nie ma osobnego
projektu do uruchomienia.

| Studium | Problem i oczekiwany wynik |
| --- | --- |
| [1. Domena i persystencja](01-domain-and-persistence.md) | Decyzja agregatu i atomowy zapis Unit of Work; konflikt nie zapisuje połowy zmian |
| [2. Messaging](02-messaging.md) | Splitter, Router i Aggregator; liczymy różne odpowiedzi, nie liczbę dostarczeń |
| [3. Retry i cache](03-resilience-and-cache.md) | Budżet obejmuje próby i sen; TTL określa wiek, LRU ofiarę limitu pamięci |
| [4. API](04-api.md) | Stary ETag, scope tenanta oraz idempotentne przyjęcie długiej operacji |
| [5. Współbieżność](05-concurrency.md) | Loan zasobu, ograniczona skrzynka i jeden load dla wielu oczekujących |
| [6. Dane rozproszone](06-distributed-data.md) | CRDT, fencing i deterministyczny przydział klucza |

## Jak czytać

1. Ustal właściciela stanu i dane wejściowe historii.
2. Prześledź wywołanie do momentu publikacji zmiany.
3. Porównaj sukces z awarią w najkrótszym niebezpiecznym przeplocie.
4. Otwórz blok modelu, jeśli potrzebujesz całej implementacji i komentarzy.
5. Użyj asercji jako zapisu oczekiwań, nie deklaracji automatycznej weryfikacji.

Modele są niezależnymi przykładami in-memory. Nie tworzą wspólnej aplikacji ani produkcyjnej biblioteki.
Fragmenty odwołujące się do otoczenia mają jawny kontekst; nie należy przypisywać im gwarancji prawdziwej
sieci lub bazy bez osobnej weryfikacji.
