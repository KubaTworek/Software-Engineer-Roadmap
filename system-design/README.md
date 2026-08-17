# System Design — kompletne notatki do przygotowania

> Cel dokumentu: zbudować praktyczną bazę do rozmów System Design na poziomie Software Engineer / Senior Software Engineer.  
> Najważniejsza zasada: **nie projektujesz systemu przez dobieranie technologii, tylko przez rozwiązywanie konkretnych wymagań i świadome zarządzanie trade-offami.**

---

## Spis treści

1. [Requirements](#1-requirements)
2. [Capacity estimation](#2-capacity-estimation)
3. [API design](#3-api-design)
4. [Data modeling](#4-data-modeling)
5. [SQL vs NoSQL](#5-sql-vs-nosql)
6. [High-level architecture](#6-high-level-architecture)
7. [Stateless services i horizontal scaling](#7-stateless-services-i-horizontal-scaling)
8. [Load balancing](#8-load-balancing)
9. [Caching](#9-caching)
10. [Database scaling](#10-database-scaling)
11. [Replication](#11-replication)
12. [CAP theorem](#12-cap-theorem)
13. [Consistency models](#13-consistency-models)
14. [Transactions](#14-transactions)
15. [Distributed transactions i Saga](#15-distributed-transactions-i-saga)
16. [Message queues i event streaming](#16-message-queues-i-event-streaming)
17. [Kafka](#17-kafka)
18. [Delivery semantics](#18-delivery-semantics)
19. [Idempotency](#19-idempotency)
20. [Transactional Outbox](#20-transactional-outbox)
21. [Inbox i deduplication](#21-inbox-i-deduplication)
22. [Retry](#22-retry)
23. [Dead Letter Queue](#23-dead-letter-queue)
24. [Timeouts](#24-timeouts)
25. [Circuit Breaker](#25-circuit-breaker)
26. [Bulkhead](#26-bulkhead)
27. [Rate limiting](#27-rate-limiting)
28. [Backpressure](#28-backpressure)
29. [Hotspots](#29-hotspots)
30. [Concurrency](#30-concurrency)
31. [Distributed locks](#31-distributed-locks)
32. [Unique IDs](#32-unique-ids)
33. [Pagination](#33-pagination)
34. [Search](#34-search)
35. [Object Storage](#35-object-storage)
36. [CDN](#36-cdn)
37. [Multi-region](#37-multi-region)
38. [Disaster Recovery](#38-disaster-recovery)
39. [Observability](#39-observability)
40. [SLI / SLO / SLA](#40-sli--slo--sla)
41. [Security](#41-security)
42. [Single Point of Failure](#42-single-point-of-failure)
43. [Graceful degradation](#43-graceful-degradation)
44. [Failure scenarios](#44-failure-scenarios)
45. [Trade-offs](#45-trade-offs)
46. [Jak prowadzić rozmowę System Design](#46-jak-prowadzić-rozmowę-system-design)
47. [Pytania kontrolne](#47-pytania-kontrolne)
48. [Najważniejsze patterny](#48-najważniejsze-patterny)
49. [Technologie, które warto umieć osadzić w architekturze](#49-technologie-które-warto-umieć-osadzić-w-architekturze)
50. [Mental model do każdego System Design](#50-mental-model-do-każdego-system-design)

---

# 1. Requirements

System Design powinien zaczynać się od ustalenia **co właściwie projektujemy**.

Bez tego każda decyzja architektoniczna jest zgadywaniem.

## Functional requirements

Opisują funkcje systemu.

Przykład dla systemu płatności:

- użytkownik może zainicjować płatność,
- system może pobrać środki,
- można wykonać refund,
- merchant może sprawdzić status płatności,
- PSP wysyła webhooki ze zmianą statusu.

Nie próbuj projektować wszystkiego. Na rozmowie warto jawnie ograniczyć scope.

Przykład:

> Skupię się na card payments, capture i refund. Chargebacki i settlement pozostawię poza podstawowym scope.

## Non-functional requirements

To często właśnie one determinują architekturę.

Typowe:

- **latency** — np. p95 < 200 ms,
- **availability** — np. 99.99%,
- **durability** — czy możemy utracić dane,
- **consistency** — czy użytkownik musi od razu widzieć najnowszy stan,
- **scalability** — ilu użytkowników / requestów obsługujemy,
- **security** — np. PCI DSS, PII, GDPR,
- **fault tolerance** — jak system zachowuje się przy awarii.

## Dlaczego requirements są tak ważne?

Ten sam system może mieć zupełnie inną architekturę zależnie od wymagań.

Przykład:

- licznik lajków może być eventual consistent,
- saldo bankowe nie powinno być traktowane tak samo,
- wyszukiwarka może zaakceptować sekundowe opóźnienie indeksowania,
- payment authorization wymaga znacznie silniejszych gwarancji.

## Co powiedzieć na rozmowie

Przed rysowaniem architektury zadaj kilka pytań:

- Ilu użytkowników obsługujemy?
- Jaki jest peak traffic?
- Czy system jest read-heavy czy write-heavy?
- Jak krytyczna jest dostępność?
- Czy możemy zaakceptować eventual consistency?
- Czy system działa w jednym regionie czy globalnie?
- Jakie operacje są krytyczne biznesowo?

---

# 2. Capacity estimation

Estymacje nie mają być idealne. Mają pokazać, czy architektura jest proporcjonalna do problemu.

## RPS

Załóż:

```text
10 mln DAU
20 requestów / użytkownika / dzień
```

Daje to:

```text
200 mln requestów / dzień
```

Średni RPS:

```text
200 000 000 / 86 400 ≈ 2300 RPS
```

Peak często przyjmujemy jako np. 5–10x średnią:

```text
~20k RPS peak
```

## Storage

Jeżeli zapisujemy:

```text
10 mln rekordów dziennie
1 KB / rekord
```

to:

```text
10 GB / dzień
~3.65 TB / rok
```

Do tego:

- indeksy,
- replikacja,
- backupy,
- logi.

Realne zużycie może być kilkukrotnie większe.

## Bandwidth

Jeżeli:

```text
20k RPS
średnia odpowiedź = 50 KB
```

to około:

```text
1 GB/s outbound
```

To może od razu wskazać potrzebę CDN, cache albo kompresji.

## Co warto policzyć

Najczęściej:

- DAU / MAU,
- average RPS,
- peak RPS,
- read/write ratio,
- storage / dzień,
- storage / rok,
- network bandwidth,
- liczba eventów / sekundę.

Nie licz wszystkiego dla zasady. Licz to, co może wpłynąć na design.

---

# 3. API design

API definiuje kontrakt między klientem a systemem.

Przykład:

```http
POST /v1/payments
GET /v1/payments/{paymentId}
POST /v1/payments/{paymentId}/capture
POST /v1/payments/{paymentId}/refunds
```

## Request

```json
{
  "merchantId": "m_123",
  "amount": 10000,
  "currency": "PLN"
}
```

Kwoty finansowe najlepiej przechowywać jako integer najmniejszej jednostki:

```text
10000 = 100.00 PLN
```

Nie używaj `double` dla pieniędzy.

## Idempotency

Dla endpointów tworzących zasoby:

```http
Idempotency-Key: 3f784...
```

Dzięki temu retry nie tworzy dwóch płatności.

## Pagination

Dla dużych kolekcji:

```http
GET /payments?limit=50&cursor=abc
```

preferuj cursor pagination.

## API versioning

Typowo:

```text
/v1/payments
/v2/payments
```

lub wersjonowanie nagłówkiem.

## API design powinien odpowiadać domenie

Nie projektuj endpointów jako bezpośrednie CRUD nad tabelami.

Lepsze:

```http
POST /orders/{id}/cancel
```

niż:

```http
PATCH /orders/{id}
{
  "status": "CANCELLED"
}
```

Pierwsze rozwiązanie lepiej reprezentuje operację biznesową.

---

# 4. Data modeling

Najpierw określ encje i access patterns.

Przykład e-commerce:

```text
User
Product
Order
OrderItem
Payment
Inventory
```

## Najważniejsze pytanie

Nie:

> Jakie tabele powinienem mieć?

Tylko:

> Jak system będzie czytał i zapisywał dane?

Przykład:

- pobierz order po `orderId`,
- pobierz historię orderów użytkownika,
- znajdź wszystkie płatności w statusie `PENDING`,
- pobierz produkt po `productId`.

Na tej podstawie projektujesz:

- primary keys,
- indeksy,
- partycjonowanie.

## Indeksy

Jeżeli często wykonujesz:

```sql
SELECT *
FROM payments
WHERE merchant_id = ?
  AND created_at > ?
```

warto rozważyć indeks:

```text
(merchant_id, created_at)
```

## Denormalizacja

W systemach distributed często świadomie duplikujemy dane.

Przykład:

Order może przechowywać:

```text
productName
priceAtPurchase
```

zamiast zawsze pobierać aktualny Product.

Powód:

- historyczna cena musi pozostać niezmienna,
- mniej zależności runtime,
- szybsze odczyty.

---

# 5. SQL vs NoSQL

To nie jest pytanie:

> Która baza jest lepsza?

Tylko:

> Która baza lepiej pasuje do access patternów i wymaganych gwarancji?

## SQL

Przykłady:

- PostgreSQL,
- MySQL.

Zalety:

- ACID,
- transakcje,
- constraints,
- joins,
- dojrzały ekosystem,
- elastyczne zapytania.

Dobre zastosowania:

- payments,
- orders,
- accounting,
- user accounts,
- inventory.

## NoSQL

### Key-value

Przykład:

```text
DynamoDB
Redis
```

Dobre gdy dostęp wygląda głównie:

```text
key -> value
```

### Wide-column

Przykład:

```text
Cassandra
```

Dobre dla bardzo dużego write throughput i znanych access patternów.

### Document

Przykład:

```text
MongoDB
```

Dobre dla elastycznych dokumentów i modeli agregatowych.

## Kiedy NoSQL ma sens?

Np.:

```text
100k+ writes/s
global distribution
proste access patterns
łatwe horizontal partitioning
```

Nie wybieraj NoSQL tylko dlatego, że „system ma być duży”.

PostgreSQL potrafi obsłużyć bardzo duże systemy.

---

# 6. High-level architecture

Pierwszy diagram powinien być prosty.

```text
Client
   |
API Gateway / Load Balancer
   |
Application Service
   |
+---------------------------+
| Database                  |
| Cache                     |
| Message Broker            |
| Object Storage            |
+---------------------------+
```

Nie rysuj od razu 30 mikroserwisów.

## Cel HLD

Pokazać:

- główny request flow,
- storage,
- komunikację sync / async,
- główne granice systemu.

Przykład order flow:

```text
Client
  |
Order API
  |
Order DB
  |
Outbox
  |
Kafka
  |
+--------------+--------------+
|              |              |
Payment     Inventory     Notification
```

Dopiero potem możesz wejść głębiej w jeden komponent.

---

# 7. Stateless services i horizontal scaling

Stateless service nie przechowuje lokalnie stanu potrzebnego do obsługi kolejnego requestu.

Dzięki temu:

```text
Request 1 -> instance A
Request 2 -> instance C
```

i system nadal działa poprawnie.

## Dlaczego to ważne?

Pozwala łatwo skalować:

```text
         Load Balancer
        /      |      \
     API-1   API-2   API-3
```

Jeżeli traffic wzrasta:

```text
3 instances -> 30 instances
```

## Gdzie przechowywać state?

W zewnętrznych systemach:

- DB,
- Redis,
- object storage,
- distributed cache.

## Problem local session

Jeżeli session użytkownika jest tylko w RAM:

```text
User -> API-1
```

kolejny request do API-2 może nie znać session.

Rozwiązania:

- JWT,
- shared session store,
- sticky sessions — raczej rozwiązanie pomocnicze niż idealne.

---

# 8. Load balancing

Load balancer rozdziela traffic między instancje.

```text
Client
   |
Load Balancer
 /    |    \
A     B     C
```

## Algorytmy

### Round Robin

Każdy request trafia kolejno do następnej instancji.

Dobre gdy instancje są podobne.

### Weighted Round Robin

Mocniejsze instancje dostają więcej trafficu.

### Least Connections

Request trafia do instancji mającej najmniej aktywnych połączeń.

### Consistent Hashing

Przydatne, gdy chcemy stabilnie kierować dany klucz do konkretnego noda.

## L4 vs L7

### L4

Routing na poziomie:

- IP,
- TCP,
- UDP.

Szybszy, mniej świadomy aplikacji.

### L7

Rozumie HTTP.

Może routować:

```text
/api/payments -> payments service
/api/orders   -> orders service
```

## Health checks

Load balancer powinien usuwać unhealthy instances z routingu.

---

# 9. Caching

Cache redukuje:

- latency,
- load na DB,
- koszt obliczeń.

```text
Client
  |
Service
  |
Cache
  |
Database
```

## Cache Aside

Najpopularniejszy model.

Read:

```text
1. read cache
2. cache miss
3. read DB
4. write cache
5. return
```

Pseudo:

```java
value = cache.get(key);

if (value == null) {
    value = database.get(key);
    cache.put(key, value);
}

return value;
```

## Write Through

Write trafia do cache, a cache zapisuje DB.

Plus:

- cache aktualny.

Minus:

- większa write latency.

## Write Behind

Cache zapisuje dane do DB asynchronicznie.

Plus:

- szybkie write.

Minus:

- ryzyko utraty danych.

## TTL

Cache nie powinien zwykle żyć wiecznie.

```text
TTL = 5 min
```

## Cache invalidation

Najtrudniejszy problem.

Opcje:

- TTL,
- delete cache po write,
- event-driven invalidation.

## Cache stampede

Popularny key wygasa.

Nagle:

```text
10000 requestów
```

uderza do DB.

Rozwiązania:

- distributed lock / single flight,
- soft TTL,
- probabilistic refresh,
- jitter TTL.

## Hot keys

Jeden key może wygenerować ogromną część ruchu.

Np.:

```text
homepage:world-cup-final
```

Rozwiązania:

- replication,
- local cache,
- key splitting.

---

# 10. Database scaling

Najpierw skaluj prostymi metodami.

## 1. Better queries

Często większy efekt da:

- indeks,
- poprawa query,
- batch,
- eliminacja N+1,

niż nowa baza.

## 2. Vertical scaling

Więcej:

- CPU,
- RAM,
- IOPS.

Ma limit, ale jest prosty.

## 3. Read replicas

```text
                 -> Replica 1
Primary Database -> Replica 2
                 -> Replica 3
```

Writes:

```text
Primary
```

Reads:

```text
Replicas
```

Idealne dla read-heavy systems.

Problem:

```text
replication lag
```

Po zapisie użytkownik może przez chwilę dostać stary stan z repliki.

## 4. Sharding

Dane dzielimy pomiędzy DB nodes.

```text
hash(userId) % N
```

Przykład:

```text
Shard A: users 0-999999
Shard B: users 1000000-1999999
```

## Problemy shardingu

- cross-shard query,
- distributed transaction,
- rebalancing,
- hot shard,
- resharding.

Dlatego shardingu nie wprowadzaj za wcześnie.

---

# 11. Replication

Replication tworzy kopie danych.

Cel:

- availability,
- read scaling,
- disaster recovery.

## Leader-Follower

```text
Writes
  |
Leader
 /   \
F1   F2
```

Writes trafiają do leadera.

Followers replikują zmiany.

## Synchronous replication

Leader potwierdza write dopiero po zapisaniu przez follower.

Plus:

- lepsza durability.

Minus:

- większa latency.

## Asynchronous replication

Leader odpowiada wcześniej.

Plus:

- niższa latency.

Minus:

- failover może spowodować utratę ostatnich danych.

## Multi-leader

Kilka regionów może przyjmować writes.

Problem:

```text
conflicts
```

Potrzebne:

- conflict resolution,
- ownership,
- CRDT,
- last-write-wins,
- application-level merge.

## Leaderless

Np. Dynamo-style systems.

Klient zapisuje do kilku nodów.

Pojęcia:

```text
N = replication factor
W = write quorum
R = read quorum
```

---

# 12. CAP theorem

CAP mówi o zachowaniu distributed system podczas network partition.

Mamy:

- **Consistency**
- **Availability**
- **Partition tolerance**

Partition tolerance w systemie rozproszonym jest praktycznie koniecznością.

W czasie partition wybierasz:

```text
CP
```

albo:

```text
AP
```

## CP

System może odrzucić część requestów, żeby nie zwrócić niespójnych danych.

Przykład:

- coordination system,
- część financial systems.

## AP

System nadal odpowiada, ale dane mogą się chwilowo różnić.

Przykład:

- social feed,
- część systemów shopping cart.

## Częsty błąd

Nie mów:

> PostgreSQL jest CA.

CAP dotyczy konkretnego zachowania rozproszonego systemu podczas partition, nie etykietowania każdej technologii trzema literami.

---

# 13. Consistency models

Consistency mówi, jakie gwarancje widoczności danych otrzymuje klient.

## Strong consistency

Po zakończeniu write każdy kolejny read widzi nową wartość.

Dobre dla:

- balance,
- stock,
- permissions.

## Eventual consistency

Jeżeli nie ma nowych writes, wszystkie repliki ostatecznie się zbiegną.

Dobre dla:

- likes,
- recommendations,
- analytics,
- search index.

## Read-your-writes

Użytkownik po własnym write widzi własną zmianę.

Przykład:

```text
User zmienia avatar
-> od razu widzi nowy avatar
```

nawet jeżeli inni użytkownicy zobaczą go sekundę później.

## Monotonic reads

Jeżeli zobaczyłeś wersję 10, nie powinieneś później zobaczyć wersji 8.

## Kluczowa zasada

Strong consistency jest kosztowna.

Stosuj ją tam, gdzie wymaga tego biznes.

---

# 14. Transactions

Transakcja grupuje operacje w logiczną całość.

## ACID

### Atomicity

Wszystko albo nic.

### Consistency

Dane pozostają zgodne z constraints.

### Isolation

Równoległe transakcje nie powinny niepoprawnie na siebie wpływać.

### Durability

Commitowane dane przetrwają awarię.

## Isolation levels

### Read Uncommitted

Możliwe dirty reads.

### Read Committed

Nie czytamy niecommitowanych danych.

### Repeatable Read

Ten sam rekord odczytany dwa razy daje ten sam wynik.

### Serializable

Najsilniejsza izolacja.

Zachowuje się tak, jakby transakcje wykonywały się sekwencyjnie.

Koszt:

- contention,
- aborts,
- mniejszy throughput.

## Optimistic locking

Przechowujemy:

```text
version
```

Update:

```sql
UPDATE product
SET stock = 9,
    version = 8
WHERE id = ?
  AND version = 7;
```

Jeżeli update zmieni 0 rows:

```text
conflict
```

## Pessimistic locking

Blokujemy rekord:

```sql
SELECT ...
FOR UPDATE
```

Dobre gdy konfliktów jest dużo.

---

# 15. Distributed transactions i Saga

Problem:

```text
Order Service
Payment Service
Inventory Service
```

Każdy ma własną DB.

Nie możemy łatwo zrobić jednej lokalnej transakcji ACID.

## Two-Phase Commit

### Phase 1

```text
PREPARE
```

### Phase 2

```text
COMMIT
```

Problemy:

- coordinator,
- blocking,
- latency,
- słaba odporność,
- tight coupling.

Dlatego w mikroserwisach często preferujemy Saga.

## Saga

Proces składa się z lokalnych transakcji.

```text
Create Order
   ↓
Reserve Inventory
   ↓
Charge Payment
   ↓
Confirm Order
```

Jeżeli payment fail:

```text
Release Inventory
Cancel Order
```

To są **compensating transactions**.

## Choreography

Serwisy reagują na eventy.

```text
OrderCreated
   ↓
InventoryReserved
   ↓
PaymentCompleted
```

Plus:

- luźne coupling.

Minus:

- trudno śledzić proces,
- logika workflow rozproszona.

## Orchestration

Jeden orchestrator steruje procesem.

```text
Order Saga Orchestrator
```

Plus:

- łatwiejsza kontrola workflow.

Minus:

- orchestrator staje się ważnym komponentem.

---

# 16. Message queues i event streaming

Async communication pozwala oddzielić producenta od konsumenta.

```text
Producer
   |
Broker
   |
Consumer
```

## Queue

Typowy model:

```text
task -> jeden consumer
```

Przykłady:

- RabbitMQ,
- SQS.

Use case:

- image processing,
- sending emails,
- background jobs.

## Event stream

Event pozostaje w logu.

```text
Kafka Topic
```

Wiele consumer groups może czytać ten sam event.

Przykład:

```text
PaymentCompleted
   |
   +-> Accounting
   +-> Analytics
   +-> Email
   +-> Fraud
```

## Zalety async

- decoupling,
- buffering,
- odporność na chwilowe awarie consumerów,
- independent scaling.

## Wady

- eventual consistency,
- trudniejsze debugging,
- duplicates,
- ordering,
- retry complexity.

---

# 17. Kafka

Kafka to distributed append-only log.

## Topic

Logiczny strumień eventów.

```text
payments
orders
user-events
```

## Partition

Topic dzieli się na partitions.

```text
Topic: payments

P0
P1
P2
```

Każda partition to uporządkowany log.

## Ordering

Kafka gwarantuje ordering:

```text
w obrębie jednej partition
```

Nie całego topicu.

Dlatego klucz jest ważny.

```text
key = paymentId
```

Wszystkie eventy jednej płatności trafią do jednej partition.

## Consumer Group

Jeżeli:

```text
3 partitions
3 consumers
```

każdy consumer może obsługiwać jedną partition.

Jeżeli:

```text
3 partitions
10 consumers
```

7 consumerów pozostanie bez pracy.

To ważne przy skalowaniu.

## Offset

Consumer pamięta pozycję w logu:

```text
offset = 14242
```

## Retention

Kafka nie usuwa eventu tylko dlatego, że został przeczytany.

Może przechowywać go np.:

```text
7 days
30 days
```

To umożliwia replay.

---

# 18. Delivery semantics

## At-most-once

```text
0 lub 1 raz
```

Możemy utracić wiadomość.

Brak duplicate.

## At-least-once

```text
1 lub więcej razy
```

Możliwe duplicate.

Najczęściej spotykany model.

Wymaga:

```text
idempotent consumer
```

## Exactly-once

Brzmi idealnie, ale jest bardziej złożone niż sugeruje nazwa.

W praktyce często budujemy efekt exactly-once poprzez:

```text
at-least-once delivery
+
idempotency
+
transactional state updates
```

## Przykład

Event:

```text
PaymentCaptured
amount = 100 PLN
```

Jeżeli consumer wykona:

```text
balance += 100
```

dwa razy, mamy błąd.

Dlatego event powinien mieć:

```text
eventId
```

a consumer powinien wykrywać duplicate.

---

# 19. Idempotency

Operacja idempotentna może zostać wykonana wielokrotnie z tym samym efektem.

## Payment example

Klient:

```http
POST /payments
Idempotency-Key: abc123
```

Server zapisuje:

```text
abc123 -> payment_987
```

Retry:

```http
POST /payments
Idempotency-Key: abc123
```

zwraca istniejący rezultat.

## Co przechowywać?

Typowo:

```text
idempotency_key
request_hash
status
response
created_at
```

Request hash chroni przed sytuacją:

```text
ten sam key
ale inny request
```

## Gdzie idempotency jest krytyczne?

- payments,
- refunds,
- order creation,
- webhook handling,
- external integrations.

---

# 20. Transactional Outbox

Problem dual-write:

```text
1. INSERT payment
2. publish Kafka event
```

Co jeśli:

```text
DB commit succeeds
Kafka publish fails?
```

Dane w DB istnieją, ale event nigdy nie powstał.

## Outbox

W tej samej DB transaction:

```sql
BEGIN;

INSERT INTO payments (...);

INSERT INTO outbox_events (...);

COMMIT;
```

Teraz mamy atomicity.

Worker:

```text
Outbox Table
    |
Publisher
    |
Kafka
```

## Publikacja

Możliwe strategie:

- polling,
- CDC / Debezium.

## Ważne

Outbox zwykle daje:

```text
at-least-once
```

Publisher może wysłać event dwa razy.

Consumer powinien być idempotentny.

---

# 21. Inbox i deduplication

Inbox to analogiczny pattern po stronie consumera.

Consumer odbiera:

```text
eventId = evt_123
```

W transakcji:

```text
1. sprawdza processed_events
2. zapisuje business state
3. zapisuje evt_123 jako processed
```

Jeżeli event przyjdzie ponownie:

```text
evt_123 already processed
-> ignore
```

## Dlaczego to działa?

Business update i zapis deduplication muszą być atomiczne.

W przeciwnym przypadku możliwa sytuacja:

```text
business update OK
crash przed zapisaniem processed_event
```

po retry update zostanie wykonany drugi raz.

---

# 22. Retry

Retry pomaga dla transient failures.

Np.:

- timeout,
- connection reset,
- temporary 503,
- chwilowa niedostępność DB replica.

Nie pomaga dla:

```text
400 Bad Request
invalid card
permission denied
```

## Exponential backoff

```text
100 ms
200 ms
400 ms
800 ms
1600 ms
```

## Jitter

Jeżeli 10k klientów retry dokładnie po:

```text
1 sekundzie
```

powstaje retry storm.

Dodaj losowość:

```text
800-1200 ms
```

## Retry budget

Nie retry'uj bez końca.

Np.:

```text
maxAttempts = 3
```

Potem:

- fail,
- DLQ,
- manual recovery.

---

# 23. Dead Letter Queue

Po określonej liczbie nieudanych prób event trafia do DLQ.

```text
Main Queue
   |
retry
   |
retry
   |
DLQ
```

## DLQ nie jest śmietnikiem

Powinieneś mieć:

- monitoring liczby wiadomości,
- alert,
- możliwość inspection,
- narzędzie do replay,
- powód failure.

Przykład metadata:

```json
{
  "eventId": "evt123",
  "error": "Invalid customer mapping",
  "attempt": 5
}
```

## Poison message

Message, który zawsze powoduje crash/failure.

Bez DLQ może blokować processing.

---

# 24. Timeouts

Każde network call może zawisnąć.

Dlatego:

```text
Service A
  |
  | timeout 500 ms
  v
Service B
```

## Bez timeout

Thread czeka.

Przy dużej liczbie requestów:

```text
thread pool exhausted
```

System A pada, mimo że problem był w B.

To przykład cascading failure.

## Timeout hierarchy

Jeżeli cały request ma:

```text
2 s deadline
```

nie ustawiaj:

```text
Payment timeout = 3 s
Inventory timeout = 3 s
```

Każda zależność powinna mieć mniejszy budżet.

---

# 25. Circuit Breaker

Circuit Breaker zapobiega ciągłemu wołaniu niedziałającej usługi.

## CLOSED

Requesty normalnie przechodzą.

## OPEN

Po dużej liczbie failure:

```text
requests fail fast
```

Nie wołamy downstreamu.

## HALF_OPEN

Po cooldown próbujemy kilka requestów.

Jeżeli działają:

```text
CLOSED
```

Jeżeli nie:

```text
OPEN
```

## Korzyść

Chroni:

- threads,
- connections,
- downstream,
- latency całego systemu.

---

# 26. Bulkhead

Nazwa pochodzi od przegród na statku.

Awaria jednego obszaru nie zatapia całego systemu.

Przykład:

```text
Payment Client Pool: 50 threads
Recommendation Client Pool: 20 threads
```

Jeżeli recommendation API zawiesi się i zużyje wszystkie swoje threads, payment nadal działa.

Można izolować:

- thread pools,
- connection pools,
- queues,
- pods,
- resource quotas.

---

# 27. Rate limiting

Rate limiter kontroluje liczbę requestów.

Przykład:

```text
100 requests / minute / user
```

## Fixed Window

```text
12:00-12:01 -> max 100
```

Problem granicy:

```text
100 requestów o 12:00:59
100 requestów o 12:01:01
```

czyli 200 w 2 sekundy.

## Sliding Window

Dokładniejszy, ale droższy.

## Token Bucket

Bucket ma tokeny.

Request zużywa token.

Tokeny są regularnie dodawane.

Pozwala na burst.

## Leaky Bucket

Requesty wypływają stałym tempem.

Wygładza traffic.

## Gdzie limitować?

- API Gateway,
- per user,
- per merchant,
- per token,
- per IP,
- globalnie.

---

# 28. Backpressure

Backpressure powstaje, gdy producer generuje dane szybciej niż consumer może je obsłużyć.

```text
Producer: 100k/s
Consumer: 20k/s
```

Queue rośnie:

```text
1 mln
10 mln
100 mln
```

## Strategie

### Scale consumers

Więcej consumerów.

### Batching

Consumer przetwarza:

```text
100 records / batch
```

### Rate limiting

Spowolnij producer.

### Load shedding

Odrzuć mniej ważną pracę.

### Bounded queue

Nie pozwól kolejce rosnąć w nieskończoność.

## Kafka

Monitoruj:

```text
consumer lag
```

Rosnący lag oznacza, że consumer nie nadąża.

---

# 29. Hotspots

Nawet dobrze rozproszony system może mieć nierówny ruch.

Przykład:

```text
Taylor Swift account
World Cup final
Black Friday product
```

## Hot partition

Jeżeli partition key:

```text
celebrityUserId
```

to cały ruch może trafić do jednego shardu.

## Rozwiązania

- key salting,
- random suffix,
- replication,
- local cache,
- specjalny path dla hot entities,
- read fanout,
- precomputation.

## Przykład key splitting

Zamiast:

```text
views:video123
```

użyj:

```text
views:video123:0
views:video123:1
...
views:video123:99
```

potem agreguj.

---

# 30. Concurrency

Concurrency pojawia się, gdy wiele operacji modyfikuje ten sam state.

Przykład:

```text
stock = 1
```

Dwa requesty:

```text
A reads 1
B reads 1
A writes 0
B writes 0
```

Oba zakupy zaakceptowane.

## Atomic update

Lepsze:

```sql
UPDATE inventory
SET stock = stock - 1
WHERE product_id = ?
  AND stock > 0;
```

Sprawdź affected rows.

## Optimistic locking

```text
version
```

Dobre gdy konflikty są rzadkie.

## Pessimistic locking

Dobre gdy contention jest wysokie i operacja krytyczna.

## Serializacja przez queue

Można wysyłać operacje dla tego samego entity do jednej Kafka partition.

Dzięki ordering możemy przetwarzać je sekwencyjnie.

---

# 31. Distributed locks

Distributed lock próbuje zagwarantować, że tylko jeden node wykonuje daną operację.

Przykłady technologii:

- Redis,
- ZooKeeper,
- etcd.

## Problem

Distributed locks są znacznie trudniejsze niż local mutex.

Musisz myśleć o:

- network partition,
- lock expiration,
- process pause,
- clock,
- stale lock owner.

## Fencing token

Lepszy system locków może zwracać monotonically increasing token:

```text
lock 41
lock 42
lock 43
```

Storage odrzuca operację ze starym tokenem.

## Najważniejsza zasada

Najpierw sprawdź, czy problem można rozwiązać przez:

- DB constraint,
- optimistic locking,
- atomic update,
- idempotency,
- partition ordering.

Distributed lock powinien być ostatecznością.

---

# 32. Unique IDs

ID powinno być:

- unikalne,
- skalowalne,
- możliwie efektywne indeksowo.

## Auto-increment

```text
1
2
3
```

Plus:

- kompaktowe,
- dobre dla B-tree.

Minus:

- centralny generator,
- łatwe zgadywanie liczby rekordów.

## UUIDv4

Losowe.

Plus:

- generowanie bez centralnego serwera.

Minus:

- 128 bit,
- random insertion do indeksu,
- gorsza locality.

## UUIDv7

Time ordered.

Łączy:

- distributed generation,
- lepszą locality.

Dobre rozwiązanie w nowoczesnych systemach.

## Snowflake

Typowo:

```text
timestamp + machineId + sequence
```

Daje:

- global uniqueness,
- sortable IDs.

---

# 33. Pagination

## Offset pagination

```sql
LIMIT 20 OFFSET 1000000
```

Problem:

DB może potrzebować przejść przez ogromną liczbę rekordów.

Dodatkowo przy nowych insertach strony mogą się przesuwać.

## Cursor pagination

```sql
WHERE created_at < ?
ORDER BY created_at DESC
LIMIT 20
```

Cursor zawiera ostatnią pozycję.

Plus:

- stała wydajność,
- lepsza stabilność.

## Composite cursor

Jeżeli `created_at` nie jest unique:

```text
(created_at, id)
```

Przykład:

```sql
WHERE (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT 20
```

---

# 34. Search

Relacyjna baza może obsłużyć proste search, ale zaawansowane wyszukiwanie często wymaga dedykowanego silnika.

Przykład:

- Elasticsearch,
- OpenSearch.

## Funkcje

- inverted index,
- tokenization,
- stemming,
- fuzzy matching,
- relevance scoring,
- autocomplete.

## Architektura

```text
Product DB
   |
Outbox / CDC
   |
Kafka
   |
Indexer
   |
OpenSearch
```

## Source of truth

Search index zwykle NIE powinien być source of truth.

Source of truth:

```text
Product DB
```

Search jest projection.

## Consistency

Index może mieć opóźnienie:

```text
1-5 sekund
```

Dla search zazwyczaj jest to akceptowalne.

---

# 35. Object Storage

Blobów nie warto zwykle trzymać bezpośrednio w relacyjnej DB.

Przykłady:

- images,
- videos,
- backups,
- documents.

Technologie:

- S3,
- GCS,
- Azure Blob Storage.

## Presigned URL

Zamiast:

```text
Client -> Backend -> S3
```

możemy:

```text
1. Client -> Backend: request upload
2. Backend -> Client: presigned URL
3. Client -> S3: upload directly
```

Korzyści:

- backend nie przenosi dużych plików,
- mniejsze koszty,
- lepsza skalowalność.

## Metadata

DB:

```text
file_id
owner_id
object_key
content_type
size
status
```

Blob:

```text
Object Storage
```

---

# 36. CDN

CDN przechowuje content blisko użytkownika.

```text
User Warsaw
   |
CDN Warsaw / Frankfurt
   |
Origin US
```

## Idealne dla

- images,
- CSS,
- JS,
- downloads,
- video segments.

## Cache key

Może uwzględniać:

- URL,
- query params,
- headers.

Źle zaprojektowany cache key może drastycznie obniżyć hit ratio.

## Invalidation

Opcje:

- versioned URLs,
- TTL,
- purge API.

Najlepszy wzorzec dla static assets:

```text
app.93ad8.js
```

Nowa wersja = nowy URL.

---

# 37. Multi-region

Globalny system może działać w wielu regionach.

## Active-Passive

```text
EU -> ACTIVE
US -> PASSIVE
```

W przypadku awarii:

```text
failover
```

Plus:

- prostsza consistency.

Minus:

- gorsze wykorzystanie zasobów,
- failover trwa.

## Active-Active

Oba regiony obsługują traffic.

```text
EU users -> EU
US users -> US
```

Plus:

- niska latency,
- wysoka availability.

Minus:

- trudne data consistency,
- conflicts,
- routing,
- global coordination.

## Home region

Częsty pattern:

```text
user123 -> eu-west
```

Writes tego użytkownika trafiają do home region.

Ułatwia consistency.

---

# 38. Disaster Recovery

Disaster Recovery odpowiada na pytanie:

> Co robimy, gdy tracimy cały region, bazę albo dużą część infrastruktury?

## RPO

Recovery Point Objective.

Ile danych możemy utracić?

```text
RPO = 0
```

oznacza praktycznie brak akceptowanej utraty.

```text
RPO = 15 min
```

oznacza możliwość utraty ostatnich 15 minut.

## RTO

Recovery Time Objective.

Jak długo system może być niedostępny?

```text
RTO = 30 min
```

## Backup to nie DR

Backup jest potrzebny, ale trzeba jeszcze mieć:

- procedurę restore,
- infrastrukturę,
- runbook,
- testy restore.

Backup, którego nigdy nie odtworzono testowo, jest tylko nadzieją.

---

# 39. Observability

Observability odpowiada:

> Czy potrafimy zrozumieć, co dzieje się wewnątrz systemu na podstawie jego outputów?

## Metrics

Typowe:

```text
request_rate
error_rate
latency
CPU
memory
queue_depth
consumer_lag
DB connections
```

### Golden Signals

- latency,
- traffic,
- errors,
- saturation.

## Logs

Preferuj structured logging.

```json
{
  "timestamp": "...",
  "level": "ERROR",
  "service": "payment",
  "paymentId": "pay_123",
  "traceId": "abc",
  "error": "PSP timeout"
}
```

## Traces

Distributed tracing pokazuje cały request:

```text
API Gateway
   |
Order Service
   |
Payment Service
   |
Stripe
```

Wszystko spięte przez:

```text
traceId
spanId
```

## Correlation ID

Powinien przechodzić między usługami.

Bardzo ułatwia debugging.

---

# 40. SLI / SLO / SLA

## SLI

Service Level Indicator.

To metryka.

Np.:

```text
procent requestów < 300 ms
```

## SLO

Cel dla SLI.

Np.:

```text
99.9% requestów < 300 ms
```

## SLA

Formalna umowa z klientem.

Np.:

```text
99.9% monthly uptime
```

z karami finansowymi.

## Error budget

Jeżeli SLO:

```text
99.9%
```

to error budget:

```text
0.1%
```

Możesz „wydać” ten budżet na:

- deployment,
- eksperymenty,
- awarie.

---

# 41. Security

Security powinno być elementem designu, nie dodatkiem na końcu.

## Authentication

Kim jesteś?

Np.:

- OAuth2,
- OIDC,
- session,
- API key.

## Authorization

Co możesz zrobić?

Modele:

- RBAC,
- ABAC,
- ACL.

## Encryption in transit

```text
TLS
```

## Encryption at rest

DB / disk / object storage encryption.

## Secrets

Nie przechowuj:

```text
passwordów
API keys
private keys
```

w kodzie.

Używaj:

- Secret Manager,
- Vault,
- KMS.

## Least privilege

Service powinien mieć tylko permissions potrzebne do działania.

## PII

Minimalizuj przechowywanie danych osobowych.

## Audit log

Dla krytycznych operacji:

```text
who
what
when
where
```

Audit log powinien być trudny do manipulacji.

---

# 42. Single Point of Failure

SPOF to komponent, którego awaria powoduje awarię całego systemu.

Przykład:

```text
API cluster
   |
ONE DATABASE
```

Jeżeli DB padnie:

```text
system down
```

## Eliminacja SPOF

### Application

Wiele instances.

### Database

Replication + failover.

### Broker

Cluster.

### Cache

Replication / cluster.

### Region

Multi-zone lub multi-region.

## Ważne

Redundancja sama w sobie nie wystarcza.

Musisz mieć automatyczny albo dobrze przygotowany failover.

---

# 43. Graceful degradation

Nie każda funkcja ma taką samą wagę.

Przykład e-commerce:

```text
Checkout
```

jest krytyczny.

```text
Recommendations
```

nie.

Jeżeli recommendation service pada:

```text
checkout powinien nadal działać
```

## Możliwe fallbacki

- cached data,
- default response,
- stale data,
- feature disable,
- partial response.

Przykład:

```json
{
  "product": {...},
  "recommendations": []
}
```

zamiast:

```text
500 Internal Server Error
```

---

# 44. Failure scenarios

Dobry System Design zawiera analizę failure modes.

## Database failure

Pytania:

- czy mamy replica?
- jak wykrywamy awarię?
- jak wygląda failover?
- czy stracimy writes?

## Cache failure

System powinien zwykle działać bez cache.

Problem:

```text
cache down
-> cały traffic idzie do DB
-> DB down
```

To cache stampede na poziomie całego klastra.

Rozwiązania:

- rate limiting,
- gradual recovery,
- local cache,
- DB protection.

## Kafka failure

- czy producer retry?
- ile możemy buforować?
- czy możemy zapisywać lokalnie?
- czy event może zostać opublikowany później?

## Consumer failure

Kafka może przejąć partition przez innego consumer.

## Region failure

- DNS / traffic routing,
- replica,
- recovery plan.

## 10x traffic

Sprawdź kolejno:

```text
load balancer
API
cache
DB
broker
consumers
external services
```

Gdzie jest pierwszy bottleneck?

---

# 45. Trade-offs

System Design to głównie zarządzanie trade-offami.

Nie istnieje rozwiązanie:

```text
najtańsze
najszybsze
najbardziej spójne
najbardziej dostępne
najprostsze
```

jednocześnie.

## Przykład cache

Plus:

```text
lower latency
lower DB load
```

Minus:

```text
stale data
invalidation complexity
```

## Przykład async processing

Plus:

```text
decoupling
high throughput
```

Minus:

```text
eventual consistency
harder debugging
```

## Przykład replication

Plus:

```text
availability
read scaling
```

Minus:

```text
replication lag
cost
failover complexity
```

## Jak mówić na rozmowie

Nie:

> Użyję Kafki, bo Kafka jest skalowalna.

Lepiej:

> Potrzebuję odseparować write path od kilku niezależnych consumerów. Kafka pozwala mi zachować eventy, skalować consumer groups niezależnie i odtwarzać historię. Kosztem jest eventual consistency i konieczność radzenia sobie z duplicate oraz ordering.

---

# 46. Jak prowadzić rozmowę System Design

Dobra struktura:

```text
1. Clarify requirements
2. Estimate scale
3. Define APIs
4. Define data model
5. Draw high-level architecture
6. Identify bottlenecks
7. Deep dive
8. Reliability
9. Consistency
10. Observability
11. Security
12. Trade-offs
```

## 1. Requirements — 5 min

Nie spędzaj połowy rozmowy na pytaniach.

Ustal najważniejsze rzeczy.

## 2. HLD

Narysuj prosty system.

Nie optymalizuj za wcześnie.

## 3. Deep dive

Interviewer zwykle skieruje Cię do najciekawszej części.

Np.:

- feed generation,
- payment correctness,
- search indexing,
- video delivery,
- matching riders.

## 4. Failure analysis

To często pokazuje seniority.

Zapytaj:

> Co się stanie, jeśli ten komponent padnie?

## 5. Trade-offs

Kończ decyzje argumentem.

---

# 47. Pytania kontrolne

Przy każdym projekcie przejdź przez poniższe pytania.

## Requirements

- Co system musi robić?
- Co jest poza scope?
- Co jest krytyczne biznesowo?

## Scale

- Ile RPS?
- Jaki peak?
- Ile danych?
- Read-heavy czy write-heavy?

## Data

- Co jest source of truth?
- Jakie są access patterns?
- Gdzie potrzebujemy transactions?
- Gdzie potrzebujemy strong consistency?

## Reliability

- Co jeśli DB padnie?
- Co jeśli cache padnie?
- Co jeśli broker padnie?
- Co jeśli downstream timeoutuje?

## Async

- Czy message może być duplicate?
- Czy może być out-of-order?
- Czy consumer jest idempotentny?
- Co robimy z poison message?

## Scaling

- Czy można skalować stateless?
- Czy DB jest bottleneckiem?
- Czy potrzebujemy replicas?
- Czy naprawdę potrzebujemy shardingu?

## Security

- Authentication?
- Authorization?
- PII?
- Encryption?
- Audit?

## Operability

- Jak monitorujemy system?
- Jak debugujemy request?
- Jak wykrywamy problem?
- Jak robimy recovery?

---

# 48. Najważniejsze patterny

## Cache Aside

Problem:

```text
expensive reads
```

Rozwiązanie:

```text
Cache -> DB fallback
```

## Saga

Problem:

```text
distributed business transaction
```

Rozwiązanie:

```text
local transactions + compensation
```

## Transactional Outbox

Problem:

```text
DB + broker dual write
```

Rozwiązanie:

```text
DB state + outbox in one transaction
```

## Inbox

Problem:

```text
duplicate events
```

Rozwiązanie:

```text
processed event IDs
```

## Circuit Breaker

Problem:

```text
failing downstream
```

Rozwiązanie:

```text
fail fast
```

## Bulkhead

Problem:

```text
one dependency exhausts all resources
```

Rozwiązanie:

```text
resource isolation
```

## CQRS

Oddziel:

```text
Command Model
```

od:

```text
Query Model
```

Ma sens, jeśli read i write mają bardzo różne wymagania.

Nie używaj CQRS automatycznie.

## Event Sourcing

Source of truth to historia eventów.

```text
AccountOpened
MoneyDeposited
MoneyWithdrawn
```

Stan jest wynikiem replay.

Zalety:

- audit history,
- temporal queries,
- reconstruction.

Wady:

- bardzo duża złożoność,
- schema evolution,
- debugging,
- projections.

## CDC

Change Data Capture czyta log zmian DB.

Np.:

```text
Postgres WAL
   |
Debezium
   |
Kafka
```

Dobre do:

- search indexing,
- analytics,
- integration.

## Materialized View

Precompute'owany widok zoptymalizowany pod odczyt.

Np.:

```text
user_feed
merchant_daily_summary
```

---

# 49. Technologie, które warto umieć osadzić w architekturze

Celem nie jest nauczenie się wszystkich API.

Masz wiedzieć:

> Jaki problem ta technologia rozwiązuje?

## PostgreSQL

Dobre dla:

- transactional state,
- relational model,
- strong constraints,
- większości backendów.

## DynamoDB

Dobre dla:

- huge scale,
- predictable key-based access,
- managed horizontal scaling.

Wymaga projektowania tabeli pod access patterns.

## Cassandra

Dobre dla:

- bardzo wysokiego write throughput,
- multi-node distribution,
- time-series-like data.

## Redis

Use cases:

- cache,
- sessions,
- rate limiting,
- counters,
- ephemeral state.

Nie traktuj Redis jako trwałego systemu finansowego tylko dlatego, że jest szybki.

## Kafka

Use cases:

- event streaming,
- async integration,
- event backbone,
- CDC,
- replay.

## RabbitMQ

Use cases:

- task queue,
- routing,
- command-like messages.

## SQS

Managed queue.

Dobre gdy potrzebujesz prostej, niezawodnej kolejki bez zarządzania clusterem.

## Elasticsearch / OpenSearch

Dobre dla:

- full-text search,
- faceting,
- autocomplete,
- ranking.

## S3 / GCS

Dobre dla:

- blobs,
- files,
- backups,
- media.

## CDN

Dobre dla:

- global delivery,
- static assets,
- images,
- video.

## Kubernetes

Rozwiązuje problemy:

- scheduling containers,
- service discovery,
- autoscaling,
- rollout,
- self-healing.

Nie jest automatyczną odpowiedzią na każde pytanie System Design.

---

# 50. Mental model do każdego System Design

Najważniejsza sekwencja:

```text
REQUIREMENTS
     ↓
SCALE
     ↓
API
     ↓
DATA MODEL
     ↓
HIGH-LEVEL DESIGN
     ↓
DATA FLOW
     ↓
CONSISTENCY
     ↓
SCALABILITY
     ↓
RELIABILITY
     ↓
FAILURE MODES
     ↓
OBSERVABILITY
     ↓
SECURITY
     ↓
TRADE-OFFS
```

## Requirements

Co rozwiązujemy?

## Scale

Jak duży jest problem?

## API

Jak świat komunikuje się z systemem?

## Data Model

Co przechowujemy?

## HLD

Jakie mamy komponenty?

## Data Flow

Jak request / event przechodzi przez system?

## Consistency

Jak świeże i poprawne muszą być dane?

## Scalability

Co się stanie przy 10x traffic?

## Reliability

Jak przeżyjemy awarie?

## Failure Modes

Co dokładnie może pójść źle?

## Observability

Jak dowiemy się, że coś poszło źle?

## Security

Jak zabezpieczamy system i dane?

## Trade-offs

Dlaczego wybraliśmy właśnie takie rozwiązanie?

---

# Dodatek A — minimalny framework odpowiedzi na rozmowie

Możesz używać tego schematu prawie zawsze:

```text
1. Ustalmy functional requirements.
2. Ustalmy najważniejsze non-functional requirements.
3. Oszacujmy skalę.
4. Zdefiniujmy API.
5. Zdefiniujmy najważniejsze encje i access patterns.
6. Narysujmy prostą architekturę.
7. Przejdźmy przez główny request flow.
8. Zidentyfikujmy bottleneck.
9. Rozwiążmy skalowanie.
10. Omówmy consistency.
11. Omówmy failure scenarios.
12. Dodajmy monitoring i security.
13. Podsumujmy trade-offy.
```

---

# Dodatek B — jak rozpoznać dojrzałą odpowiedź

Słaba odpowiedź:

> Użyję Redis, Kafka, Cassandra i Kubernetes, bo są skalowalne.

Dojrzała odpowiedź:

> Początkowo użyłbym PostgreSQL jako source of truth, ponieważ workload wymaga transakcji i nie uzasadniliśmy jeszcze shardingu. Dla read-heavy endpointu dodałbym Redis w modelu cache-aside z TTL. Zmiany wymagające asynchronicznego fan-out publikowałbym przez transactional outbox do Kafki. Dzięki temu unikamy dual-write problemu, ale musimy zaakceptować at-least-once delivery i zrobić consumerów idempotentnych.

Najważniejsza różnica:

```text
technologia
```

jest konsekwencją:

```text
wymagania -> problem -> decyzja -> trade-off
```

---

# Dodatek C — priorytety nauki

Jeśli przygotowujesz się do rozmów System Design, ucz się w tej kolejności.

## Tier 1 — absolutna podstawa

1. Requirements
2. SQL / indexes
3. Replication
4. Caching
5. Load balancing
6. Horizontal scaling
7. Consistency
8. Transactions
9. Message queues
10. Idempotency
11. Retry / timeout
12. Failure handling

## Tier 2 — bardzo ważne

13. Kafka
14. Outbox
15. Saga
16. Sharding
17. Rate limiting
18. Observability
19. Search
20. Object storage + CDN
21. Multi-region

## Tier 3 — poziom bardziej senior

22. Backpressure
23. Hot partitions
24. Distributed locking
25. Leader election
26. CDC
27. CQRS
28. Event Sourcing
29. Disaster Recovery
30. SLO / error budgets

---

# Dodatek D — końcowa checklista

Przed zakończeniem dowolnego System Design sprawdź:

- [ ] Czy wymagania są jasno określone?
- [ ] Czy znam skalę?
- [ ] Czy zdefiniowałem główne API?
- [ ] Czy znam source of truth?
- [ ] Czy access patterns pasują do modelu danych?
- [ ] Czy określiłem consistency requirements?
- [ ] Czy system można skalować horyzontalnie?
- [ ] Czy wiem, gdzie jest bottleneck?
- [ ] Czy retry są bezpieczne?
- [ ] Czy operacje krytyczne są idempotentne?
- [ ] Czy eventy mogą być duplicate?
- [ ] Czy ordering ma znaczenie?
- [ ] Czy unikam dual-write problemu?
- [ ] Czy mam timeouty?
- [ ] Czy mam circuit breaker tam, gdzie potrzebny?
- [ ] Czy system przeżyje utratę jednej instancji?
- [ ] Czy system przeżyje awarię cache?
- [ ] Czy system przeżyje awarię DB?
- [ ] Czy wiem, jak wygląda failover?
- [ ] Czy mam metrics, logs i traces?
- [ ] Czy znam SLO?
- [ ] Czy security jest częścią designu?
- [ ] Czy potrafię wskazać główne trade-offy?

---

## Najważniejsza zasada na koniec

Na dobrym System Design prawie każda istotna decyzja powinna mieć strukturę:

```text
Problem
   ↓
Requirement
   ↓
Decision
   ↓
Benefit
   ↓
Trade-off
   ↓
Failure mode
```

Przykład:

```text
Problem:
DB jest przeciążona readami.

Requirement:
p95 latency < 100 ms.

Decision:
Redis cache-aside.

Benefit:
mniej readów do DB i niższa latency.

Trade-off:
możliwe stale data.

Failure mode:
cache outage może wywołać nagły wzrost trafficu do DB.

Mitigation:
TTL jitter, rate limiting, local cache i kontrolowany fallback.
```

Jeżeli będziesz myślał w ten sposób, przestajesz „rysować architekturę”, a zaczynasz faktycznie **projektować system**.
