# Architektura nowoczesnego systemu e-commerce oparta o DDD, Clean Architecture i modularność

## Wprowadzenie

Projektowanie systemów e-commerce należy do najbardziej wymagających obszarów architektury backendowej. Nawet pozornie prosty sklep internetowy szybko staje się złożonym organizmem biznesowym, w którym pojawiają się różne procesy domenowe: sprzedaż, płatności, magazyn, dostawy, promocje, zwroty, fakturowanie czy integracje z zewnętrznymi operatorami.

Wraz ze wzrostem skali biznesu rosną również wymagania dotyczące:
- niezawodności,
- elastyczności wdrożeń,
- autonomii zespołów,
- odporności na zmiany,
- skalowalności infrastruktury.

W praktyce największym problemem nie jest sama implementacja funkcjonalności, lecz utrzymanie spójności modelu biznesowego przy jednoczesnym zachowaniu rozsądnego poziomu złożoności technicznej.

Właśnie dlatego współczesne systemy coraz częściej projektuje się w oparciu o:
- Domain-Driven Design (DDD),
- Clean Architecture,
- Hexagonal Architecture,
- modular monolith,
- CQRS,
- event-driven architecture.

Niniejszy dokument przedstawia teoretyczne podstawy projektowania systemu e-commerce z wykorzystaniem tych podejść oraz analizuje kompromisy architektoniczne między monolitem a mikroserwisami.

---

# Problem architektoniczny systemów e-commerce

Większość systemów e-commerce rozpoczyna życie jako klasyczny monolit. Jest to rozwiązanie rozsądne:
- prostszy deployment,
- jedna baza danych,
- łatwiejszy debugging,
- niski koszt operacyjny,
- szybkie dostarczanie funkcjonalności.

Problemy pojawiają się wraz ze wzrostem organizacji oraz złożoności biznesowej:
- wiele zespołów pracuje równolegle,
- domena biznesowa rośnie,
- pojawiają się konflikty modeli danych,
- deployment staje się ryzykowny,
- zmiany w jednym obszarze wpływają na cały system,
- wzrasta coupling między modułami.

W tym momencie organizacje często próbują przejść bezpośrednio do mikroserwisów. Bardzo często prowadzi to jednak do gwałtownego wzrostu złożoności infrastrukturalnej:
- problemy sieciowe,
- eventual consistency,
- distributed transactions,
- monitoring,
- tracing,
- retry policies,
- versioning API,
- obsługa błędów integracyjnych.

Dlatego coraz częściej stosuje się podejście pośrednie — modular monolith.

---

# Monolit, modular monolith i mikroserwisy

## Klasyczny monolit

Klasyczny monolit to jedna aplikacja posiadająca:
- pojedynczy deployment,
- jedną bazę danych,
- wspólny model danych,
- komunikację w pamięci procesu.

Największą zaletą monolitu jest prostota.

Największym problemem jest brak wyraźnych granic architektonicznych. W praktyce logika biznesowa zaczyna „przeciekać” między modułami.

Typowe symptomy:
- współdzielone encje,
- zapytania SQL między modułami,
- zależności cykliczne,
- trudność wydzielania funkcjonalności.

---

## Modular monolith

Modular monolith zachowuje pojedynczy deployment, ale wewnętrznie system jest podzielony na silnie izolowane moduły domenowe.

Każdy moduł:
- posiada własną logikę biznesową,
- posiada własny model domenowy,
- komunikuje się przez jawne kontrakty,
- nie zna implementacji innych modułów.

To podejście pozwala osiągnąć większość korzyści mikroserwisów bez kosztów systemów rozproszonych.

Najważniejsze cechy modular monolith:
- wysoka spójność wewnątrz modułu,
- niskie sprzężenie między modułami,
- jawne granice domenowe,
- prostsza operacyjność niż mikroserwisy.

---

## Mikroserwisy

Mikroserwisy rozszerzają modularność na poziom deploymentu.

Każdy serwis:
- działa jako osobny proces,
- posiada własną bazę danych,
- jest wdrażany niezależnie,
- komunikuje się przez sieć.

Mikroserwisy rozwiązują problemy organizacyjne dużych firm, ale jednocześnie zwiększają złożoność techniczną.

Koszty mikroserwisów:
- observability,
- service discovery,
- retry policies,
- message brokers,
- eventual consistency,
- distributed tracing,
- DevOps,
- CI/CD,
- contract versioning.

Mikroserwisy mają sens głównie wtedy, gdy:
- organizacja posiada wiele niezależnych zespołów,
- istnieją różne wymagania skalowania,
- deployment monolitu stał się bottleneckiem,
- domena biznesowa jest bardzo rozległa.

---

# Domain-Driven Design

DDD nie jest frameworkiem ani strukturą katalogów. Jest sposobem modelowania złożonego biznesu.

Najważniejszym założeniem DDD jest koncentracja na domenie biznesowej zamiast na technologii.

DDD pomaga:
- definiować granice systemu,
- modelować procesy biznesowe,
- redukować coupling,
- tworzyć wspólny język między biznesem a programistami.

---

# Bounded Context

Bounded Context definiuje granice modelu domenowego.

W systemie e-commerce można wyróżnić przykładowo:
- Sales Context,
- Billing Context,
- Shipping Context,
- Inventory Context,
- Customer Context.

Każdy kontekst:
- posiada własny język,
- posiada własne reguły biznesowe,
- posiada własne encje,
- posiada własny model danych.

To samo pojęcie może mieć różne znaczenie w różnych kontekstach.

Przykład:
- `Order` w kontekście sprzedaży oznacza zamówienie klienta,
- `Order` w kontekście wysyłki może oznaczać paczkę do realizacji.

---

# Context Map

Context Map opisuje relacje między bounded contexts.

Najczęściej spotykane relacje:
- Customer/Supplier,
- Conformist,
- Shared Kernel,
- Published Language,
- Anti-Corruption Layer.

Szczególnie istotny jest Anti-Corruption Layer, który chroni model domenowy przed zewnętrznymi modelami i nie pozwala „zanieczyścić” domeny obcymi pojęciami.

---

# Tactical DDD

## Encje

Encja posiada:
- tożsamość,
- cykl życia,
- zachowanie biznesowe.

Encja nie powinna być jedynie rekordem z bazy danych.

Dobra encja:
- pilnuje invariants,
- zawiera logikę biznesową,
- ukrywa szczegóły implementacyjne.

---

## Value Objects

Value Object opisuje wartość bez tożsamości.

Przykłady:
- Money,
- Address,
- Email,
- Quantity.

Value Objects powinny być:
- niemutowalne,
- porównywane po wartości,
- pozbawione efektów ubocznych.

---

## Agregaty

Agregat jest granicą spójności transakcyjnej.

Aggregate Root:
- kontroluje modyfikacje,
- pilnuje invariants,
- publikuje zdarzenia domenowe.

Najczęstszym błędem jest tworzenie zbyt dużych agregatów.

Skutki:
- lock contention,
- konflikty transakcyjne,
- słaba skalowalność.

Dobre agregaty powinny być możliwie małe.

---

## Repozytoria

Repozytorium abstrahuje persystencję danych.

Domena nie powinna znać:
- SQL,
- JPA,
- Hibernate,
- Spring Data,
- ORM.

Repozytorium jest portem architektury.

---

## Domain Events

Domain Event opisuje fakt biznesowy, który już się wydarzył.

Przykłady:
- OrderPlaced,
- PaymentAuthorized,
- ShipmentCreated.

Zdarzenia:
- zmniejszają coupling,
- wspierają integrację asynchroniczną,
- umożliwiają eventual consistency,
- zwiększają autonomię modułów.

---

# Clean Architecture i Hexagonal Architecture

## Dependency Rule

Najważniejsza zasada Clean Architecture:

> Zależności mogą wskazywać wyłącznie do środka.

Oznacza to, że:
- domena nie zna frameworków,
- domena nie zna infrastruktury,
- domena nie zna HTTP,
- domena nie zna bazy danych.

Framework powinien być detalem implementacyjnym.

---

## Porty i adaptery

Port definiuje kontrakt.

Adapter implementuje szczegóły technologiczne.

Przykłady portów:
- OrderRepository,
- PaymentGateway,
- EventPublisher.

Przykłady adapterów:
- JpaOrderRepository,
- StripePaymentAdapter,
- KafkaEventPublisher.

Dzięki temu domena pozostaje niezależna technologicznie.

---

# CQRS

CQRS rozdziela:
- model zapisu,
- model odczytu.

## Write Model

Model zapisu:
- używa agregatów,
- pilnuje reguł biznesowych,
- publikuje zdarzenia.

## Read Model

Model odczytu:
- jest zoptymalizowany pod query,
- może być denormalizowany,
- może używać osobnej bazy danych.

CQRS pozwala:
- skalować odczyt niezależnie,
- upraszczać zapytania,
- oddzielać logikę domenową od raportowania.

---

# Event-Driven Architecture

W architekturze event-driven moduły komunikują się przez zdarzenia.

Przykładowy przepływ:
1. Sales publikuje `OrderPlaced`.
2. Billing tworzy płatność.
3. Inventory rezerwuje produkty.
4. Shipping tworzy wysyłkę.

Korzyści:
- niższy coupling,
- większa skalowalność,
- niezależność modułów,
- łatwiejsza ewolucja systemu.

Kosztem jest eventual consistency.

---

# Eventual Consistency

W systemach rozproszonych nie można zakładać natychmiastowej spójności danych.

Zmiany propagowane są asynchronicznie i system osiąga spójność po czasie.

To fundamentalna różnica między monolitem a mikroserwisami.

---

# Saga Pattern

Saga zarządza długimi procesami biznesowymi rozproszonymi między serwisami.

Przykład:
1. Utworzenie zamówienia.
2. Autoryzacja płatności.
3. Rezerwacja magazynu.
4. Utworzenie wysyłki.

Jeżeli jeden krok się nie powiedzie:
- wykonywane są akcje kompensacyjne.

Saga zastępuje distributed transactions.

---

# Outbox Pattern

Outbox Pattern rozwiązuje problem atomowości:
- zapis danych,
- publikacja zdarzenia.

Bez outbox może dojść do sytuacji:
- dane zapisane,
- event nieopublikowany.

Outbox zapisuje zdarzenie w tej samej transakcji co dane domenowe.

Następnie osobny proces:
- odczytuje outbox,
- publikuje event,
- oznacza rekord jako przetworzony.

To jeden z kluczowych wzorców nowoczesnych systemów event-driven.

---

# Idempotentność i retry

Komunikacja rozproszona wymaga retry.

Retry oznacza jednak możliwość wielokrotnego przetworzenia komunikatu.

Dlatego operacje muszą być idempotentne.

Przykład:
- wielokrotne odebranie `PaymentAuthorized` nie może utworzyć wielu płatności.

Idempotentność jest absolutnie kluczowa w systemach rozproszonych.

---

# Shared Database vs Database per Service

## Shared Database

### Zalety
- prostota,
- lokalne transakcje,
- łatwe query.

### Wady
- silny coupling,
- brak autonomii,
- trudna ewolucja schematu.

---

## Database per Service

### Zalety
- izolacja modeli,
- autonomia deploymentu,
- niezależność zespołów.

### Wady
- eventual consistency,
- integracja event-driven,
- brak JOIN między serwisami.

W praktyce database per service jest standardem mikroserwisów.

---

# Testowanie architektury DDD

## Testy agregatów

Najważniejsze testy dotyczą logiki domenowej.

Testujemy:
- invariants,
- reguły biznesowe,
- publikację zdarzeń.

Bez frameworków i bez bazy danych.

---

## Testy serwisów aplikacyjnych

Serwisy aplikacyjne testuje się:
- z fake repositories,
- z fake event publisherami.

Celem jest weryfikacja orchestracji przypadku użycia.

---

## Testy adapterów

Adaptery infrastrukturalne testuje się osobno:
- integracja z bazą,
- Kafka,
- REST,
- external APIs.

To jedyne miejsce, gdzie infrastruktura powinna być bezpośrednio testowana.

---

# Migracja z modular monolith do mikroserwisów

Najlepszą strategią jest rozpoczęcie od modular monolith.

Dopiero później:
- identyfikuje się granice,
- obserwuje bottlenecks,
- wydziela moduły.

---

# Kryteria wydzielania mikroserwisu

Mikroserwis ma sens gdy:
- moduł ma własny lifecycle,
- wymaga osobnego skalowania,
- posiada autonomiczny zespół,
- deployment powoduje konflikty,
- model domenowy jest stabilny.

Nie należy wydzielać mikroserwisów:
- zbyt wcześnie,
- bez wyraźnych bounded contexts,
- wyłącznie ze względów marketingowych.

---

# Strategia migracji

Typowa migracja:
1. Budowa modular monolith.
2. Wprowadzenie jawnych kontraktów.
3. Izolacja modułów.
4. Wprowadzenie eventów domenowych.
5. Oddzielenie baz danych.
6. Ekstrakcja wybranego modułu.
7. Przeniesienie komunikacji na sieć.

Najważniejsze jest to, aby granice logiczne istniały zanim pojawią się granice deploymentowe.

---

# Organizacja kodu

Dobra architektura organizuje kod wokół domeny, a nie wokół frameworka.

## Zła organizacja

```text
controllers/
services/
repositories/
entities/
```

## Dobra organizacja

```text
sales/
shipping/
billing/
inventory/
```

Każdy moduł zawiera:

- domenę,
- aplikację,
- infrastrukturę,
- adaptery.

---

# Najważniejsze zasady praktyczne
1. Framework nie może definiować architektury.
2. Domena musi być niezależna technologicznie.
3. Granice biznesowe są ważniejsze niż techniczne.
4. Mikroserwisy są kosztowne.
5. Eventual consistency jest nieunikniona.
6. Agregaty powinny być małe.
7. Komunikacja musi być jawna.
8. Integracja wymaga idempotentności.
9. CQRS upraszcza skalowanie odczytu.
10. Modular monolith jest bardzo dobrym punktem startowym.

---

# Podsumowanie

Nowoczesna architektura e-commerce nie polega na ślepym wyborze mikroserwisów, lecz na świadomym modelowaniu domeny biznesowej i granic odpowiedzialności.

Domain-Driven Design dostarcza języka oraz narzędzi do modelowania biznesu, natomiast Clean Architecture pozwala zachować niezależność domeny od technologii.

W praktyce modular monolith bardzo często okazuje się najlepszym kompromisem między prostotą operacyjną a wysoką jakością architektury.

Mikroserwisy powinny być efektem ewolucji systemu, a nie punktem startowym.

Najbardziej trwałe systemy to nie te o najbardziej skomplikowanej infrastrukturze, lecz te, które posiadają:

- dobrze zdefiniowane bounded contexts,
- silną izolację domeny,
- jawne kontrakty komunikacyjne,
- kontrolowaną złożoność.