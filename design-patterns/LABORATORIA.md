# Przykłady i scenariusze poprawności — mapa Markdown

[README](README.md) · [Indeks](INDEKS.md) · [Studia przypadków](labs/README.md)

Wszystkie przykłady, komentarze i asercje są częścią dokumentów. Krótkie mechanizmy czytaj w kartach katalogu.
Dłuższe modele mają rozwijane bloki z pełnymi definicjami, a scenariusze w konwencji JUnit pokazują oczekiwany
wynik i zachowanie po błędzie. Nie ma osobnych klas do otwarcia, zależności JUnit ani poleceń uruchamiania
Maven.

## 1. GoF: zobacz, co faktycznie się zmienia

- [Kreacyjne](examples/01-creational.md): produkt przez Factory Method, rodzina przez Abstract Factory,
  składanie Buildera, niezależna kopia Prototype i zakres Singletona.
- [Strukturalne](examples/02-structural.md): Adapter, Bridge, Composite, Decorator, Facade, Flyweight i Proxy;
  porównuj intencję, mimo podobnego delegowania.
- [Behawioralne](examples/03-behavioral.md): łańcuch, komenda i undo, interpreter, iterator, koordynator,
  snapshot, subskrypcja, stan, strategia, szablon i visitor.

Każda karta GoF zawiera również krótki kod i scenariusz poprawności. Duże bloki w powyższych dokumentach są
opcjonalnym pogłębieniem, z własnymi importami.

## 2. Java i model: jawne niezmienniki

[Wartości, Result, Specification, DI i zakres zasobu](examples/04-java.md) pokazują mechanizmy bez frameworka.
Porównaj Money różnych walut, niepoprawne wejście parsera i wyjątek w środku pracy AutoCloseable. Pełny
przypadek zamówienia oraz port repozytorium rozwija [studium domeny](labs/01-domain-and-persistence.md).

## 3. Outbox i Inbox: dwukrotna dostawa, jeden lokalny skutek

[Model dostawy](examples/05-delivery.md) opisuje historię: lokalny commit zamówienia i outboxu, publikacja,
awaria przed oznaczeniem wysłania, replay, a następnie deduplikacja Inbox. Przypisanie snapshotu modeluje
lokalną atomowość, nie trwałość po restarcie. Zewnętrzny efekt wymaga własnej idempotencji lub outboxu.

## 4. Saga: kompensacja ma własny wynik

W [tym samym modelu](examples/05-delivery.md) prześledź `reserve → charge → błąd ship → refund → release`.
Błąd kompensacji nie może ukryć pierwotnej przyczyny ani pominąć pozostałych działań. Wynik timeout
zewnętrznego kroku może być nieznany i wymaga sprawdzenia statusu.

## 5. Odporność: testuj czas bez sleep

[Małe modele ochrony](examples/06-resilience.md) przedstawiają Circuit Breaker, Bulkhead i token bucket.
[Pełniejszy retry](labs/03-resilience-and-cache.md) dodaje wspólny budżet, backoff, jitter i kontrolowany
zegar. Scenariusze określają czas w danych; nie są benchmarkiem ani testem żywego partnera sieciowego.

## 6. Dane: stare odczyty i odbudowa

[Cache, kontrola wersji i historia](examples/07-data.md) pokazują stale value, odrzucenie lost update oraz
odbudowę projekcji. [Rozbudowane studium](labs/06-distributed-data.md) przedstawia zbieżny merge, prawo zapisu
z fencing i zmianę właściciela klucza.

## 7. Rozbudowane przykłady przekrojowe

| Studium | Główna historia i kryterium poprawności |
| --- | --- |
| [Domena i persystencja](labs/01-domain-and-persistence.md) | Potwierdź zamówienie 3600 groszy (36 PLN); odrzuć niski limit bez zmiany stanu i konflikt bez częściowego commitu |
| [Messaging](labs/02-messaging.md) | Rozdziel o1 na l1/l2; duplikat l1 nie zastępuje l2, a deadline finalizuje brak |
| [Retry i cache](labs/03-resilience-and-cache.md) | Zmieszczone próby i sen dzielą jeden budżet; TTL i LRU ograniczają różne własności kopii |
| [API](labs/04-api.md) | v0 nie nadpisze v1; drugi tenant nie odczyta d1; ponowienie eksportu wraca do tego samego uchwytu |
| [Współbieżność](labs/05-concurrency.md) | Zasób wraca raz, pełna skrzynka odrzuca pracę, cancel jednego klienta nie anuluje wspólnego load |
| [Dane rozproszone](labs/06-distributed-data.md) | Merge nie podwaja replay, token 7 przegrywa z 8, dodany D przejmuje tylko swoje nowe zwycięskie klucze |

## Granica interpretacji

Dokumenty zawierają 20 rozwiniętych modeli i 13 zestawów scenariuszy przeniesionych do Markdown. Asercje służą
jako precyzyjny zapis oczekiwań; nie są testami uruchamianymi w CI tego modułu. Zewnętrzne porty w szkicach
wymagają własnych implementacji, a gwarancje DB, brokera i klastra trzeba sprawdzić w ich środowisku.
