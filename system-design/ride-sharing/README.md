# Ride Sharing — System Design

Kompleksowy projekt architektury systemu typu Uber/Bolt/Lyft. Dokument obejmuje wymagania funkcjonalne i niefunkcjonalne, architekturę wysokiego poziomu, mikroserwisy, model danych, matching, lokalizację w czasie rzeczywistym, płatności, skalowanie, bezpieczeństwo, observability oraz plan implementacji.

---

## Spis treści

1. [Cel systemu](#1-cel-systemu)
2. [Główne funkcjonalności](#2-główne-funkcjonalności)
3. [Wymagania niefunkcjonalne](#3-wymagania-niefunkcjonalne)
4. [High-Level Architecture](#4-high-level-architecture)
5. [Główne mikroserwisy](#5-główne-mikroserwisy)
6. [Location Service](#6-location-service)
7. [Geospatial Indexing](#7-geospatial-indexing)
8. [Ride Service](#8-ride-service)
9. [Matching Service](#9-matching-service)
10. [Propozycja przejazdu dla kierowcy](#10-propozycja-przejazdu-dla-kierowcy)
11. [Real-time Communication](#11-real-time-communication)
12. [Pricing Service](#12-pricing-service)
13. [ETA / Routing Service](#13-eta--routing-service)
14. [Payment Service](#14-payment-service)
15. [Event-Driven Architecture](#15-event-driven-architecture)
16. [Bazy danych](#16-bazy-danych)
17. [Sharding](#17-sharding)
18. [API Design](#18-api-design)
19. [Flow: zamówienie przejazdu](#19-flow-zamówienie-przejazdu)
20. [Flow: zakończenie przejazdu](#20-flow-zakończenie-przejazdu)
21. [Idempotencja](#21-idempotencja)
22. [Race conditions](#22-race-conditions)
23. [Cache](#23-cache)
24. [Powiadomienia](#24-powiadomienia)
25. [Fraud Detection](#25-fraud-detection)
26. [Safety Features](#26-safety-features)
27. [Observability](#27-observability)
28. [Skalowanie lokalizacji](#28-skalowanie-lokalizacji)
29. [Skalowanie matchingu](#29-skalowanie-matchingu)
30. [Multi-region Architecture](#30-multi-region-architecture)
31. [Consistency Model](#31-consistency-model)
32. [Failure Scenarios](#32-failure-scenarios)
33. [Security](#33-security)
34. [Privacy](#34-privacy)
35. [Data Model](#35-data-model)
36. [Technologie](#36-technologie)
37. [ML / Intelligence Layer](#37-ml--intelligence-layer)
38. [MVP](#38-mvp)
39. [Najważniejsze decyzje architektoniczne](#39-najważniejsze-decyzje-architektoniczne)
40. [Architektura docelowa](#40-architektura-docelowa)
41. [Największe ryzyka techniczne](#41-największe-ryzyka-techniczne)
42. [Plan implementacji](#42-plan-implementacji)
43. [Rekomendacja techniczna](#43-rekomendacja-techniczna)

---

## 1. Cel systemu

System ma umożliwiać pasażerom zamawianie przejazdów, kierowcom przyjmowanie zleceń, a platformie zarządzanie trasą, ceną, płatnością, bezpieczeństwem i statusem przejazdu w czasie rzeczywistym.

Najważniejszy problem techniczny to nie samo CRUD-owe zamówienie przejazdu, ale **real-time matching, lokalizacja, odporność na awarie, niskie opóźnienia i spójność stanu przejazdu**.

---

## 2. Główne funkcjonalności

### Aplikacja pasażera

Pasażer powinien móc:

- założyć konto i zalogować się,
- podać punkt odbioru i punkt docelowy,
- zobaczyć szacowaną cenę i czas przyjazdu kierowcy,
- zamówić przejazd,
- śledzić kierowcę na mapie,
- anulować przejazd,
- zapłacić kartą, portfelem lub gotówką, jeżeli system to dopuszcza,
- ocenić kierowcę,
- zgłosić problem.

### Aplikacja kierowcy

Kierowca powinien móc:

- przejść onboarding i weryfikację,
- ustawić status online/offline,
- wysyłać swoją lokalizację,
- otrzymywać propozycje przejazdów,
- zaakceptować albo odrzucić przejazd,
- nawigować do pasażera i celu,
- rozpocząć i zakończyć kurs,
- widzieć zarobki,
- otrzymywać wypłaty.

### Panel administracyjny

Admin/operator powinien móc:

- zarządzać użytkownikami i kierowcami,
- obsługiwać reklamacje,
- monitorować aktywne przejazdy,
- konfigurować ceny,
- blokować konta,
- analizować fraud,
- przeglądać historię płatności i przejazdów.

---

## 3. Wymagania niefunkcjonalne

### Latencja

Najbardziej wrażliwe operacje:

| Operacja | Docelowa latencja |
|---|---:|
| Aktualizacja lokalizacji kierowcy | 100–500 ms |
| Wyszukanie kierowców w pobliżu | < 300 ms |
| Dopasowanie kierowcy | 1–5 s |
| Aktualizacja statusu przejazdu | < 500 ms |
| Potwierdzenie płatności | 1–5 s |

### Dostępność

System powinien mieć wysoką dostępność, szczególnie dla:

- lokalizacji,
- zamawiania przejazdu,
- matching engine,
- statusu przejazdu,
- płatności.

Docelowo: **99.9%–99.99% availability** dla krytycznych usług.

### Skalowalność

System powinien obsługiwać:

- miliony użytkowników,
- setki tysięcy aktywnych kierowców,
- bardzo częste aktualizacje lokalizacji,
- lokalne piki popytu, np. koncerty, lotniska, deszcz, godziny szczytu.

### Spójność

Nie każdy komponent wymaga tej samej spójności.

| Obszar | Typ spójności |
|---|---|
| Status przejazdu | silna / transakcyjna |
| Płatność | silna |
| Lokalizacja kierowcy | eventual consistency |
| ETA | eventual consistency |
| Historia przejazdów | eventual consistency |
| Powiadomienia | at-least-once delivery |

---

## 4. High-Level Architecture

```text
+-------------------+        +-------------------+
| Passenger Mobile  |        | Driver Mobile     |
+---------+---------+        +---------+---------+
          |                            |
          | HTTPS / WebSocket / gRPC   |
          v                            v
+------------------------------------------------+
|                 API Gateway                    |
+------------------------------------------------+
          |
          v
+------------------------------------------------+
|             Authentication Service             |
+------------------------------------------------+

+-------------------+     +----------------------+
| User Service      |     | Driver Service       |
+-------------------+     +----------------------+

+-------------------+     +----------------------+
| Ride Service      |<--->| Matching Service     |
+-------------------+     +----------------------+

+-------------------+     +----------------------+
| Location Service  |<--->| Geo Index / Redis    |
+-------------------+     +----------------------+

+-------------------+     +----------------------+
| Pricing Service   |     | ETA / Routing Service|
+-------------------+     +----------------------+

+-------------------+     +----------------------+
| Payment Service   |     | Notification Service |
+-------------------+     +----------------------+

+------------------------------------------------+
| Kafka / Pulsar / Event Bus                     |
+------------------------------------------------+

+-------------------+     +----------------------+
| PostgreSQL/MySQL  |     | Cassandra/DynamoDB   |
+-------------------+     +----------------------+

+-------------------+     +----------------------+
| Data Warehouse    |     | Monitoring / Logging |
+-------------------+     +----------------------+
```

---

## 5. Główne mikroserwisy

### 5.1 API Gateway

Odpowiada za:

- routing requestów,
- rate limiting,
- autoryzację,
- request tracing,
- walidację tokenów,
- wersjonowanie API,
- ochronę przed nadużyciami.

Może być oparte o:

- Kong,
- Envoy,
- NGINX,
- AWS API Gateway,
- GCP API Gateway.

### 5.2 Authentication Service

Odpowiada za:

- logowanie,
- rejestrację,
- refresh tokeny,
- OAuth,
- MFA,
- role użytkowników.

Role:

```text
PASSENGER
DRIVER
ADMIN
SUPPORT
FLEET_MANAGER
```

Token JWT powinien zawierać minimalne informacje:

```json
{
  "user_id": "u_123",
  "role": "DRIVER",
  "session_id": "s_456",
  "exp": 1730000000
}
```

Nie trzymałbym w JWT danych takich jak rating, status kierowcy czy balance, bo to dane zmienne.

### 5.3 User Service

Przechowuje dane pasażerów:

- profil,
- numer telefonu,
- e-mail,
- zapisane adresy,
- zapisane metody płatności,
- preferencje,
- rating pasażera.

Przykładowy model:

```sql
users (
  id UUID PRIMARY KEY,
  phone_number VARCHAR UNIQUE NOT NULL,
  email VARCHAR UNIQUE,
  full_name VARCHAR,
  status VARCHAR,
  rating DECIMAL(3,2),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

### 5.4 Driver Service

Zarządza kierowcami:

- profilem kierowcy,
- dokumentami,
- statusem weryfikacji,
- pojazdem,
- statusem online/offline,
- ratingiem,
- flotą.

Modele:

```sql
drivers (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  verification_status VARCHAR,
  status VARCHAR,
  rating DECIMAL(3,2),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)

vehicles (
  id UUID PRIMARY KEY,
  driver_id UUID NOT NULL,
  make VARCHAR,
  model VARCHAR,
  plate_number VARCHAR,
  color VARCHAR,
  vehicle_type VARCHAR,
  year INT
)
```

Status kierowcy:

```text
OFFLINE
ONLINE
AVAILABLE
OFFERED_RIDE
ACCEPTED_RIDE
ARRIVING
ON_TRIP
SUSPENDED
```

Ważne: status kierowcy powinien być kontrolowany również przez Ride Service/Matching Service, nie tylko przez aplikację kierowcy. Kierowca nie może samowolnie przełączyć się na `AVAILABLE`, jeżeli ma aktywny przejazd.

---

## 6. Location Service

To jeden z najważniejszych komponentów.

### Zadania

Location Service obsługuje:

- aktualizacje lokalizacji kierowców,
- lokalizację pasażera podczas przejazdu,
- wyszukiwanie kierowców w pobliżu,
- przechowywanie krótkoterminowego stanu lokalizacji,
- publikowanie eventów lokalizacyjnych.

### Dane lokalizacyjne

Kierowca wysyła lokalizację np. co 1–5 sekund, zależnie od statusu.

```json
{
  "driver_id": "d_123",
  "lat": 52.2297,
  "lng": 21.0122,
  "heading": 180,
  "speed": 42,
  "accuracy": 8,
  "timestamp": "2026-06-05T12:00:00Z"
}
```

### Przechowywanie lokalizacji

Nie należy zapisywać każdej lokalizacji do relacyjnej bazy danych. To byłoby kosztowne i wolne.

Lepsza architektura:

```text
Driver App
   |
   v
Location Ingestion API
   |
   +--> Redis / Redis Cluster / Aerospike - aktualna lokalizacja
   |
   +--> Kafka - strumień lokalizacji
   |
   +--> Data Lake - historia do analityki
```

### Geo-index

Do szybkiego wyszukiwania kierowców w pobliżu można użyć:

- Redis GEO,
- H3,
- S2 Geometry,
- Geohash,
- Elasticsearch geo queries,
- PostGIS dla mniej dynamicznych danych.

Dla systemu ride-sharing wybrałbym **H3 albo S2 + Redis/Aerospike**, ponieważ lokalizacje są bardzo dynamiczne.

---

## 7. Geospatial Indexing

### Problem

Musimy szybko znaleźć kierowców w promieniu np. 1–5 km od pasażera.

Naiwne podejście:

```sql
SELECT * FROM drivers
WHERE distance(driver_location, passenger_location) < 3000;
```

To nie skaluje się dobrze przy setkach tysięcy kierowców aktualizujących lokalizację co kilka sekund.

### Lepsze podejście: H3 / Geohash

Mapę dzielimy na komórki.

```text
Warszawa
 ├── cell_abc123
 │    ├── driver_1
 │    ├── driver_2
 │    └── driver_3
 ├── cell_def456
 │    ├── driver_4
 │    └── driver_5
```

Dla pasażera:

1. Obliczamy jego komórkę.
2. Pobieramy kierowców z tej komórki.
3. Jeżeli za mało wyników, pobieramy sąsiednie komórki.
4. Liczymy dokładną odległość i ETA.
5. Sortujemy kandydatów.

Przykład struktury w Redis:

```text
geo:cell:h3_891e2040c37ffff -> SET(driver_123, driver_456)
driver:location:driver_123 -> HASH(lat, lng, heading, speed, timestamp)
driver:status:driver_123 -> AVAILABLE
```

---

## 8. Ride Service

Ride Service jest właścicielem stanu przejazdu.

### Statusy przejazdu

```text
REQUESTED
MATCHING
DRIVER_ASSIGNED
DRIVER_ARRIVING
DRIVER_ARRIVED
IN_PROGRESS
COMPLETED
CANCELLED_BY_PASSENGER
CANCELLED_BY_DRIVER
EXPIRED
FAILED
```

### State machine

```text
REQUESTED
   |
   v
MATCHING
   |
   v
DRIVER_ASSIGNED
   |
   v
DRIVER_ARRIVING
   |
   v
DRIVER_ARRIVED
   |
   v
IN_PROGRESS
   |
   v
COMPLETED
```

Anulowanie może wystąpić na kilku etapach, ale powinno mieć reguły:

```text
REQUESTED -> CANCELLED_BY_PASSENGER
MATCHING -> CANCELLED_BY_PASSENGER
DRIVER_ASSIGNED -> CANCELLED_BY_PASSENGER / CANCELLED_BY_DRIVER
DRIVER_ARRIVING -> CANCELLED_BY_PASSENGER / CANCELLED_BY_DRIVER
DRIVER_ARRIVED -> CANCELLED_BY_PASSENGER
IN_PROGRESS -> raczej nie anulujemy, tylko kończymy awaryjnie
```

### Tabela rides

```sql
rides (
  id UUID PRIMARY KEY,
  passenger_id UUID NOT NULL,
  driver_id UUID,
  vehicle_id UUID,
  pickup_lat DECIMAL(10,7),
  pickup_lng DECIMAL(10,7),
  dropoff_lat DECIMAL(10,7),
  dropoff_lng DECIMAL(10,7),
  status VARCHAR NOT NULL,
  estimated_price DECIMAL(10,2),
  final_price DECIMAL(10,2),
  currency VARCHAR(3),
  requested_at TIMESTAMP,
  accepted_at TIMESTAMP,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  cancelled_at TIMESTAMP,
  cancellation_reason VARCHAR,
  version INT NOT NULL
)
```

Kolumna `version` jest ważna do optimistic lockingu.

---

## 9. Matching Service

To kluczowy komponent biznesowy.

### Cel

Dla danego pasażera znaleźć najlepszego kierowcę.

Nie zawsze najbliższy kierowca jest najlepszy. Trzeba brać pod uwagę:

- ETA do pasażera,
- dystans,
- rating kierowcy,
- typ pojazdu,
- dostępność,
- prawdopodobieństwo akceptacji,
- aktualny kierunek jazdy,
- historię anulowań,
- balans podaży i popytu,
- preferencje pasażera,
- wymagania, np. większy samochód, fotelik, zwierzęta.

### Podstawowy algorytm

```text
1. Passenger requests ride.
2. Ride Service creates ride with status REQUESTED.
3. Ride Service emits RideRequested event.
4. Matching Service consumes event.
5. Matching Service finds nearby available drivers.
6. Matching Service ranks drivers.
7. Matching Service sends offer to best driver.
8. Driver has e.g. 10 seconds to accept.
9. If accepted:
   - assign driver to ride,
   - lock driver,
   - update ride status.
10. If rejected/timeout:
   - offer to next driver.
11. If no drivers:
   - expand search radius,
   - retry,
   - eventually expire ride.
```

### Ranking kandydatów

Przykładowa funkcja scoringowa:

```text
score =
  w1 * normalized_eta
+ w2 * normalized_distance
- w3 * driver_rating
+ w4 * cancellation_penalty
- w5 * acceptance_probability
+ w6 * supply_demand_penalty
```

Niższy score = lepszy kandydat.

Przykład:

```text
driver_A:
  ETA = 3 min
  rating = 4.7
  acceptance probability = 0.6

driver_B:
  ETA = 4 min
  rating = 4.95
  acceptance probability = 0.95
```

Czasem lepszy będzie `driver_B`, mimo że jest dalej.

### Lock kierowcy

Problem: ten sam kierowca może zostać przypisany do dwóch przejazdów.

Rozwiązanie:

- distributed lock,
- compare-and-set w Redis,
- transakcja w bazie,
- optimistic locking.

Przykład Redis:

```text
SET lock:driver:driver_123 ride_456 NX EX 15
```

Jeżeli operacja się uda, kierowca jest tymczasowo zarezerwowany.

Ale sam Redis lock nie wystarczy jako źródło prawdy. Finalne przypisanie musi zostać zapisane transakcyjnie w Ride Service/Driver Service.

---

## 10. Propozycja przejazdu dla kierowcy

Matching Service wysyła ofertę:

```json
{
  "ride_id": "r_123",
  "pickup": {
    "lat": 52.2297,
    "lng": 21.0122
  },
  "estimated_distance_to_pickup": 1.4,
  "estimated_pickup_eta_minutes": 5,
  "estimated_trip_duration_minutes": 18,
  "estimated_earnings": 32.50,
  "expires_at": "2026-06-05T12:01:10Z"
}
```

Driver App odpowiada:

```json
{
  "ride_id": "r_123",
  "driver_id": "d_456",
  "decision": "ACCEPT"
}
```

Accept powinien być idempotentny, bo aplikacja może wysłać request ponownie.

---

## 11. Real-time Communication

### Kanały komunikacji

Do komunikacji w czasie rzeczywistym:

- WebSocket,
- MQTT,
- gRPC streaming,
- Server-Sent Events — raczej tylko dla prostszych przypadków.

Dla aplikacji mobilnej praktyczny wybór to:

```text
WebSocket / MQTT dla real-time
Push notifications jako fallback
HTTPS dla request-response
```

### Przepływ lokalizacji kierowcy

```text
Driver App
  -> Location Service
  -> Redis Geo Index
  -> Kafka topic: driver.location.updated
  -> Real-time Gateway
  -> Passenger App
```

### Real-time Gateway

Nie powinien zawierać logiki biznesowej. Jego zadania:

- utrzymanie połączeń WebSocket,
- autoryzacja połączeń,
- subskrypcja eventów,
- pushowanie eventów do klientów,
- reconnect handling,
- heartbeat.

Przykładowe kanały:

```text
passenger:{passenger_id}:ride:{ride_id}
driver:{driver_id}
ride:{ride_id}
city:{city_id}:surge
```

---

## 12. Pricing Service

Pricing Service liczy cenę przejazdu.

### Składniki ceny

```text
final_price =
  base_fare
+ distance_rate * distance_km
+ time_rate * duration_minutes
+ surge_multiplier
+ tolls
+ airport_fee
+ cancellation_fee
- discounts
```

### Szacowana cena

Przed zamówieniem system wylicza:

```json
{
  "estimated_price_min": 28.00,
  "estimated_price_max": 34.00,
  "currency": "PLN",
  "surge_multiplier": 1.2,
  "estimated_duration_minutes": 22,
  "estimated_distance_km": 8.4
}
```

### Dynamic pricing / surge

Surge może zależeć od:

- liczby aktywnych pasażerów,
- liczby dostępnych kierowców,
- historycznego popytu,
- pogody,
- eventów,
- lokalizacji,
- pory dnia.

Przykład:

```text
demand_supply_ratio = active_requests / available_drivers

if ratio < 1.0 -> surge = 1.0
if ratio 1.0 - 1.5 -> surge = 1.2
if ratio 1.5 - 2.0 -> surge = 1.5
if ratio > 2.0 -> surge = 2.0+
```

Surge powinien mieć ograniczenia, żeby uniknąć skrajnych cen i problemów regulacyjnych.

---

## 13. ETA / Routing Service

Routing Service odpowiada za:

- ETA kierowcy do pasażera,
- ETA przejazdu do celu,
- długość trasy,
- alternatywne trasy,
- uwzględnienie korków,
- korektę ceny po trasie.

Możliwe źródła:

- Google Maps Platform,
- Mapbox,
- HERE,
- własny routing oparty o OSRM/Valhalla,
- hybryda: zewnętrzny provider + cache + fallback.

### Cache

Nie warto pytać zewnętrznego providera o każdą trasę bez cache.

Cache key może wyglądać tak:

```text
route:{origin_h3}:{destination_h3}:{time_bucket}
```

`time_bucket` np. co 5 minut.

---

## 14. Payment Service

Payment Service musi być bardzo ostrożnie zaprojektowany. To obszar, gdzie błędy są kosztowne.

### Zadania

- autoryzacja płatności,
- capture po zakończeniu przejazdu,
- refundy,
- wypłaty dla kierowców,
- faktury,
- obsługa chargebacków,
- promocje,
- portfel użytkownika.

### Przepływ płatności kartą

```text
1. Passenger requests ride.
2. Payment Service creates payment authorization / hold.
3. Ride starts.
4. Ride completes.
5. Pricing Service calculates final price.
6. Payment Service captures amount.
7. Receipt is generated.
8. Driver balance is updated.
```

### Payment state machine

```text
PAYMENT_PENDING
AUTHORIZED
AUTHORIZATION_FAILED
CAPTURE_PENDING
CAPTURED
CAPTURE_FAILED
REFUND_PENDING
REFUNDED
PARTIALLY_REFUNDED
```

### Idempotency

Każda operacja płatnicza musi mieć idempotency key:

```text
payment_capture:{ride_id}:{attempt_number}
```

Bez tego można przypadkowo obciążyć pasażera dwa razy.

---

## 15. Event-Driven Architecture

System powinien być oparty o eventy, bo wiele procesów dzieje się asynchronicznie.

### Event bus

Możliwe technologie:

- Kafka,
- Pulsar,
- RabbitMQ,
- Google Pub/Sub,
- AWS SNS/SQS.

Dla dużego ride-sharingu wybrałbym **Kafka albo Pulsar**.

### Przykładowe eventy

```text
RideRequested
RideMatched
DriverOfferedRide
DriverAcceptedRide
DriverRejectedRide
DriverArrived
RideStarted
RideCompleted
RideCancelled
PaymentAuthorized
PaymentCaptured
PaymentFailed
DriverLocationUpdated
SurgeUpdated
NotificationRequested
```

### Przykład eventu

```json
{
  "event_id": "evt_123",
  "event_type": "RideRequested",
  "ride_id": "ride_456",
  "passenger_id": "user_789",
  "pickup": {
    "lat": 52.2297,
    "lng": 21.0122
  },
  "dropoff": {
    "lat": 52.4064,
    "lng": 16.9252
  },
  "created_at": "2026-06-05T12:00:00Z"
}
```

### Outbox Pattern

Żeby uniknąć sytuacji:

```text
zapisaliśmy ride w bazie, ale nie wysłaliśmy eventu
```

używamy Outbox Pattern.

```text
1. Transakcja DB:
   - INSERT ride
   - INSERT outbox_event
2. Outbox publisher publikuje event do Kafka.
3. Consumerzy przetwarzają event.
```

Tabela:

```sql
outbox_events (
  id UUID PRIMARY KEY,
  aggregate_type VARCHAR,
  aggregate_id UUID,
  event_type VARCHAR,
  payload JSONB,
  status VARCHAR,
  created_at TIMESTAMP,
  published_at TIMESTAMP
)
```

---

## 16. Bazy danych

### Podział danych

| Typ danych | Proponowana baza |
|---|---|
| Użytkownicy | PostgreSQL / MySQL |
| Kierowcy | PostgreSQL / MySQL |
| Przejazdy | PostgreSQL / MySQL, sharding |
| Lokalizacja live | Redis / Aerospike |
| Historia lokalizacji | Cassandra / Bigtable / S3 |
| Eventy | Kafka |
| Analityka | BigQuery / Snowflake / Redshift |
| Wyszukiwanie supportowe | Elasticsearch / OpenSearch |
| Cache | Redis |

### Ride DB

Dla przejazdów relacyjna baza jest dobrym wyborem, bo mamy:

- transakcje,
- stan przejazdu,
- płatności,
- historię,
- raportowanie,
- potrzebę spójności.

Ale przy dużej skali potrzebny będzie sharding.

---

## 17. Sharding

### Sharding po `city_id`

Dobre, bo większość przejazdów jest lokalna.

```text
rides_warsaw
rides_krakow
rides_london
rides_paris
```

Plusy:

- lokalność danych,
- łatwiejsze skalowanie miast,
- matching też działa per miasto.

Minusy:

- nierówny rozkład ruchu,
- bardzo duże miasta wymagają dalszego podziału.

### Sharding po `ride_id`

Dobre dla równomiernego rozkładu.

Minus: trudniejsze zapytania per miasto.

### Hybryda

Najlepsze praktycznie:

```text
partition_key = city_id + hash(ride_id)
```

---

## 18. API Design

### Passenger API

#### Estimate price

```http
POST /v1/rides/estimate
Authorization: Bearer <token>
```

Request:

```json
{
  "pickup": {
    "lat": 52.2297,
    "lng": 21.0122
  },
  "dropoff": {
    "lat": 52.4064,
    "lng": 16.9252
  },
  "vehicle_type": "standard"
}
```

Response:

```json
{
  "estimate_id": "est_123",
  "estimated_price": {
    "min": 28.00,
    "max": 34.00,
    "currency": "PLN"
  },
  "estimated_duration_minutes": 22,
  "estimated_distance_km": 8.4,
  "surge_multiplier": 1.2,
  "expires_at": "2026-06-05T12:05:00Z"
}
```

#### Request ride

```http
POST /v1/rides
```

Request:

```json
{
  "estimate_id": "est_123",
  "pickup": {
    "lat": 52.2297,
    "lng": 21.0122,
    "address": "Warszawa Centralna"
  },
  "dropoff": {
    "lat": 52.4064,
    "lng": 16.9252,
    "address": "Poznań Główny"
  },
  "payment_method_id": "pm_123",
  "vehicle_type": "standard"
}
```

Response:

```json
{
  "ride_id": "ride_123",
  "status": "MATCHING"
}
```

#### Get ride

```http
GET /v1/rides/{ride_id}
```

Response:

```json
{
  "ride_id": "ride_123",
  "status": "DRIVER_ASSIGNED",
  "driver": {
    "id": "driver_456",
    "name": "Adam",
    "rating": 4.91
  },
  "vehicle": {
    "make": "Toyota",
    "model": "Corolla",
    "plate_number": "WA12345",
    "color": "Black"
  },
  "eta_minutes": 4
}
```

#### Cancel ride

```http
POST /v1/rides/{ride_id}/cancel
```

Request:

```json
{
  "reason": "Changed plans"
}
```

### Driver API

#### Update availability

```http
POST /v1/drivers/me/availability
```

Request:

```json
{
  "status": "AVAILABLE"
}
```

#### Update location

```http
POST /v1/drivers/me/location
```

Request:

```json
{
  "lat": 52.2297,
  "lng": 21.0122,
  "heading": 90,
  "speed": 35,
  "accuracy": 6,
  "timestamp": "2026-06-05T12:00:00Z"
}
```

#### Accept ride

```http
POST /v1/driver/rides/{ride_id}/accept
Idempotency-Key: accept-ride_123-driver_456
```

#### Reject ride

```http
POST /v1/driver/rides/{ride_id}/reject
```

#### Start ride

```http
POST /v1/driver/rides/{ride_id}/start
```

#### Complete ride

```http
POST /v1/driver/rides/{ride_id}/complete
```

---

## 19. Flow: zamówienie przejazdu

```text
Passenger App
   |
   | POST /rides
   v
API Gateway
   |
   v
Ride Service
   |
   | 1. Validate passenger
   | 2. Validate estimate
   | 3. Create ride: REQUESTED
   | 4. Emit RideRequested
   v
Kafka
   |
   v
Matching Service
   |
   | 5. Find nearby drivers
   | 6. Rank candidates
   | 7. Lock selected driver
   | 8. Send offer
   v
Driver App
   |
   | 9. Accept
   v
Matching Service
   |
   | 10. Confirm assignment
   v
Ride Service
   |
   | 11. Update ride: DRIVER_ASSIGNED
   | 12. Emit RideMatched
   v
Passenger App receives update via WebSocket
```

---

## 20. Flow: zakończenie przejazdu

```text
Driver App
   |
   | POST /rides/{id}/complete
   v
Ride Service
   |
   | Validate ride state
   | Update ride to COMPLETED
   | Emit RideCompleted
   v
Pricing Service
   |
   | Calculate final fare
   v
Payment Service
   |
   | Capture payment
   | Emit PaymentCaptured
   v
Notification Service
   |
   | Send receipt
   v
Driver Wallet Service
   |
   | Update driver balance
```

Tu trzeba uważać: `COMPLETED` i `PAYMENT_CAPTURED` to nie to samo. Przejazd może być zakończony, ale płatność może jeszcze wisieć w stanie `CAPTURE_PENDING` albo `CAPTURE_FAILED`.

---

## 21. Idempotencja

Idempotencja jest obowiązkowa dla:

- zamawiania przejazdu,
- akceptowania przejazdu,
- anulowania przejazdu,
- rozpoczęcia przejazdu,
- zakończenia przejazdu,
- capture płatności,
- refundów.

Przykład:

```http
POST /v1/rides
Idempotency-Key: passenger_123_20260605_120000
```

Tabela:

```sql
idempotency_keys (
  key VARCHAR PRIMARY KEY,
  user_id UUID,
  request_hash VARCHAR,
  response JSONB,
  status VARCHAR,
  created_at TIMESTAMP,
  expires_at TIMESTAMP
)
```

Jeżeli klient powtórzy request z tym samym kluczem, system zwraca tę samą odpowiedź.

---

## 22. Race conditions

### Problem 1: Dwóch pasażerów dostaje tego samego kierowcę

Rozwiązanie:

```text
Atomic lock driver_id
+
transactional ride assignment
+
driver status transition AVAILABLE -> OFFERED_RIDE -> ACCEPTED_RIDE
```

### Problem 2: Kierowca akceptuje po timeout

Rozwiązanie:

- oferta ma `expires_at`,
- accept sprawdza aktualny stan oferty,
- po timeout offer jest nieważny,
- API zwraca `OFFER_EXPIRED`.

### Problem 3: Pasażer anuluje w momencie akceptacji kierowcy

Rozwiązanie:

- state machine,
- optimistic locking,
- event ordering per `ride_id`,
- wersjonowanie encji.

Przykład:

```sql
UPDATE rides
SET status = 'DRIVER_ASSIGNED',
    driver_id = :driver_id,
    version = version + 1
WHERE id = :ride_id
  AND status = 'MATCHING'
  AND version = :expected_version;
```

Jeżeli update zwróci 0 rows, stan się zmienił i operacja nie może zostać wykonana.

---

## 23. Cache

Cache jest potrzebny, ale trzeba uważać, żeby nie cache’ować źródła prawdy.

### Co cache’ować

- profile kierowców,
- dane pojazdów,
- ETA,
- wyniki routingowe,
- konfigurację cen,
- surge per obszar,
- aktualne lokalizacje.

### Czego nie traktować jako źródła prawdy

- salda użytkownika,
- stanu płatności,
- finalnego statusu przejazdu,
- rozliczeń kierowców.

---

## 24. Powiadomienia

Notification Service obsługuje:

- push notifications,
- SMS,
- e-mail,
- in-app notifications.

Przykłady:

```text
Driver assigned
Driver arrived
Ride started
Ride completed
Payment failed
Receipt generated
```

Powiadomienia powinny być asynchroniczne.

```text
Ride Service -> Kafka -> Notification Service -> FCM/APNS/SMS/Email
```

Dostarczanie powiadomień powinno być `at-least-once`, a aplikacja kliencka powinna radzić sobie z duplikatami.

---

## 25. Fraud Detection

Fraud jest istotny w ride-sharingu.

### Przykładowe nadużycia

- fałszywe przejazdy,
- kierowca i pasażer współpracują, żeby wyciągać promocje,
- chargeback fraud,
- GPS spoofing,
- tworzenie wielu kont,
- nienaturalne anulowania,
- zawyżanie trasy.

### Sygnały fraudowe

- wiele kont z jednego urządzenia,
- nietypowe wzorce tras,
- zbyt częste anulowania,
- lokalizacja kierowcy skacząca nierealistycznie,
- payment failure rate,
- bardzo krótkie przejazdy z promocją,
- powtarzalne pary pasażer-kierowca.

### Architektura fraud detection

```text
Events -> Kafka -> Stream Processing -> Risk Score -> Action
```

Możliwe akcje:

```text
ALLOW
REVIEW
BLOCK_PROMO
REQUIRE_VERIFICATION
TEMPORARILY_SUSPEND
```

---

## 26. Safety Features

Dla realnego systemu to bardzo ważne.

Funkcje bezpieczeństwa:

- przycisk SOS,
- udostępnianie trasy,
- maskowanie numerów telefonów,
- czat w aplikacji,
- nagrywanie audio tam, gdzie legalne,
- weryfikacja kierowcy,
- weryfikacja dokumentów,
- wykrywanie nietypowych postojów,
- wykrywanie zboczenia z trasy,
- support 24/7.

System może emitować event:

```text
SafetyIncidentDetected
```

np. gdy:

```text
driver deviates > 500m from route
vehicle stopped unexpectedly for > 10 minutes
passenger pressed SOS
```

---

## 27. Observability

System musi być dobrze obserwowalny.

### Metryki biznesowe

- liczba zamówień,
- liczba ukończonych przejazdów,
- cancellation rate,
- acceptance rate,
- średnie ETA,
- średni czas matchingu,
- surge multiplier,
- payment failure rate,
- liczba aktywnych kierowców,
- liczba aktywnych pasażerów.

### Metryki techniczne

- API latency p50/p95/p99,
- error rate,
- Kafka lag,
- WebSocket connection count,
- Redis memory usage,
- DB lock contention,
- payment provider latency,
- location update throughput.

### Narzędzia

- Prometheus,
- Grafana,
- OpenTelemetry,
- Jaeger,
- Loki,
- ELK/OpenSearch,
- Datadog.

---

## 28. Skalowanie lokalizacji

Załóżmy:

```text
100 000 aktywnych kierowców
lokalizacja co 3 sekundy
```

To daje:

```text
~33 000 location updates / second
```

Przy 1 milionie aktywnych kierowców:

```text
~333 000 updates / second
```

To jest powód, dla którego Location Service musi być osobnym, mocno zoptymalizowanym komponentem.

### Optymalizacje

- batching,
- kompresja payloadów,
- adaptive update interval,
- ignorowanie małych zmian pozycji,
- edge ingestion per region,
- partycjonowanie po `city_id`,
- zapis tylko aktualnej lokalizacji do Redis,
- historia lokalizacji przez Kafka do storage asynchronicznie.

---

## 29. Skalowanie matchingu

Matching powinien być wykonywany lokalnie per region/miasto.

```text
matching-worker-warsaw-1
matching-worker-warsaw-2
matching-worker-krakow-1
matching-worker-london-1
```

Kafka topic może być partycjonowany po `city_id` albo `ride_id`.

```text
ride.requested topic
partition key = city_id
```

Dzięki temu eventy dla danego miasta trafiają do właściwego klastra workerów.

---

## 30. Multi-region Architecture

Dla systemu globalnego:

```text
Europe Region
  - Warsaw
  - Berlin
  - Paris

US Region
  - New York
  - San Francisco

Asia Region
  - Singapore
  - Tokyo
```

### Zasada

Przejazd jest lokalny, więc większość danych operacyjnych powinna żyć w regionie miasta.

Globalne mogą być:

- konto użytkownika,
- billing summary,
- globalny risk score,
- konfiguracje,
- data warehouse.

Lokalne powinny być:

- matching,
- lokalizacja,
- aktywne przejazdy,
- real-time gateway.

---

## 31. Consistency Model

| Komponent | Spójność |
|---|---|
| Ride lifecycle | strong consistency |
| Payment | strong consistency |
| Driver assignment | strong consistency |
| Location | eventual consistency |
| ETA | eventual consistency |
| Notifications | eventual consistency |
| Analytics | eventual consistency |
| Fraud scoring | near-real-time |

To jest rozsądny kompromis. Próba zapewnienia silnej spójności dla lokalizacji zabiłaby skalowalność.

---

## 32. Failure Scenarios

### Matching Service pada

Rozwiązanie:

- eventy są w Kafka,
- inny worker przejmuje partycję,
- oferty mają timeout,
- Ride Service może oznaczyć ride jako `MATCHING_FAILED` po czasie.

### Redis z lokalizacją pada

Rozwiązanie:

- Redis Cluster,
- replica,
- fallback do ostatniej znanej lokalizacji,
- kierowcy re-pushują lokalizację,
- Matching może tymczasowo ograniczyć nowe ride requesty w regionie.

### Payment provider nie odpowiada

Rozwiązanie:

- retry z backoff,
- circuit breaker,
- stan `CAPTURE_PENDING`,
- reconciliation job,
- alternatywny payment provider.

### WebSocket rozłączony

Rozwiązanie:

- push notification fallback,
- polling `GET /rides/{id}`,
- event replay od ostatniego znanego numeru sekwencji.

### Driver App traci internet

Rozwiązanie:

- heartbeat,
- jeżeli brak lokalizacji np. 30 sekund, oznacz jako `LOCATION_STALE`,
- jeżeli brak kontaktu dłużej, support/fallback/anulowanie,
- pasażer powinien widzieć komunikat.

---

## 33. Security

### API security

- TLS wszędzie,
- OAuth2/JWT,
- refresh token rotation,
- rate limiting,
- device fingerprinting,
- IP reputation,
- podpisywanie krytycznych requestów z aplikacji mobilnej,
- ochrona przed replay attack.

### Dane wrażliwe

- szyfrowanie PII w bazie,
- tokenizacja kart płatniczych,
- brak przechowywania pełnych danych kart,
- ograniczony dostęp supportu do danych,
- audyt dostępu.

### Uprawnienia

Przykład:

```text
Passenger can read only own rides.
Driver can read only assigned ride.
Support can read ride after ticket assignment.
Admin actions are audited.
```

---

## 34. Privacy

Ride-sharing przetwarza bardzo wrażliwe dane lokalizacyjne.

Zasady:

- minimalizacja danych,
- krótkie TTL dla lokalizacji live,
- agregacja danych do analityki,
- pseudonimizacja,
- osobne storage dla PII,
- audyt dostępu,
- możliwość usunięcia konta zgodnie z lokalnymi regulacjami.

Przykład TTL:

```text
live driver location in Redis: 30–120 seconds
ride route detailed points: e.g. 30–90 days
aggregated analytics: long-term
```

---

## 35. Data Model

### rides

```sql
CREATE TABLE rides (
  id UUID PRIMARY KEY,
  passenger_id UUID NOT NULL,
  driver_id UUID,
  vehicle_id UUID,
  city_id VARCHAR NOT NULL,
  status VARCHAR NOT NULL,
  pickup_lat DECIMAL(10,7) NOT NULL,
  pickup_lng DECIMAL(10,7) NOT NULL,
  dropoff_lat DECIMAL(10,7) NOT NULL,
  dropoff_lng DECIMAL(10,7) NOT NULL,
  estimated_distance_km DECIMAL(8,2),
  actual_distance_km DECIMAL(8,2),
  estimated_duration_minutes INT,
  actual_duration_minutes INT,
  estimated_price DECIMAL(10,2),
  final_price DECIMAL(10,2),
  currency VARCHAR(3),
  requested_at TIMESTAMP NOT NULL,
  accepted_at TIMESTAMP,
  driver_arrived_at TIMESTAMP,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  cancelled_at TIMESTAMP,
  cancellation_reason VARCHAR,
  version INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### ride_events

```sql
CREATE TABLE ride_events (
  id UUID PRIMARY KEY,
  ride_id UUID NOT NULL,
  event_type VARCHAR NOT NULL,
  actor_type VARCHAR,
  actor_id UUID,
  metadata JSONB,
  created_at TIMESTAMP NOT NULL
);
```

### driver_locations_current

W Redis/Aerospike, niekoniecznie SQL:

```json
{
  "driver_id": "driver_123",
  "city_id": "warsaw",
  "lat": 52.2297,
  "lng": 21.0122,
  "h3_cell": "891e2040c37ffff",
  "heading": 180,
  "speed": 42,
  "updated_at": "2026-06-05T12:00:00Z"
}
```

### payments

```sql
CREATE TABLE payments (
  id UUID PRIMARY KEY,
  ride_id UUID NOT NULL,
  passenger_id UUID NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  currency VARCHAR(3) NOT NULL,
  status VARCHAR NOT NULL,
  provider VARCHAR NOT NULL,
  provider_payment_id VARCHAR,
  idempotency_key VARCHAR UNIQUE,
  authorized_at TIMESTAMP,
  captured_at TIMESTAMP,
  failed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

---

## 36. Technologie

### Backend

Sensowny stack:

```text
Java/Kotlin + Spring Boot
Go dla Location/Matching
Node.js/NestJS dla mniej krytycznych serwisów
Python dla ML/Fraud/Pricing eksperymentalnego
```

Dla bardzo wydajnych serwisów real-time wybrałbym raczej **Go lub Java/Kotlin** niż Node.js.

### Storage

```text
PostgreSQL / MySQL - core transactional data
Redis Cluster - cache, geo, locks
Kafka - event streaming
Cassandra / Bigtable - time-series location history
S3/GCS - raw event archive
Elasticsearch/OpenSearch - support search
BigQuery/Snowflake - analytics
```

### Infrastructure

```text
Kubernetes
Terraform
Istio/Linkerd optionally
Prometheus + Grafana
OpenTelemetry
ArgoCD
Cloudflare/Akamai for edge
```

---

## 37. ML / Intelligence Layer

Na początku można użyć prostych reguł. ML ma sens później.

### Potencjalne modele ML

- predykcja ETA,
- predykcja popytu,
- predykcja akceptacji kierowcy,
- fraud detection,
- dynamic pricing,
- driver positioning recommendations,
- cancellation prediction.

Przykład:

```text
Input:
  city_id
  time_of_day
  weekday
  weather
  active_drivers
  active_requests
  historical_demand
  events_nearby

Output:
  demand forecast per H3 cell
```

To może pomóc kierowcom ustawiać się tam, gdzie zaraz będzie popyt.

---

## 38. MVP

Nie budowałbym od razu pełnego Ubera. MVP może być znacznie prostsze.

### MVP scope

- rejestracja pasażera i kierowcy,
- status kierowcy online/offline,
- lokalizacja kierowcy,
- zamawianie przejazdu,
- podstawowe matching po najbliższym kierowcy,
- prosta cena,
- status przejazdu,
- płatność przez jednego providera,
- powiadomienia push,
- panel admina w podstawowej wersji.

### MVP architecture

```text
Mobile Apps
   |
API Gateway
   |
Modular Monolith / Few Services
   |
PostgreSQL + Redis + WebSocket + Payment Provider
```

Na start można użyć **modular monolith**, a dopiero potem wydzielać:

1. Location Service,
2. Matching Service,
3. Payment Service,
4. Notification Service,
5. Pricing Service.

Zbyt wczesne mikroserwisy mogą spowolnić projekt.

---

## 39. Najważniejsze decyzje architektoniczne

### Decyzja 1: Monolit czy mikroserwisy?

Dla MVP:

```text
Modular monolith
```

Dla skali produkcyjnej:

```text
Microservices
```

Praktyczna ścieżka:

```text
Modular Monolith
 -> wydzielenie Location Service
 -> wydzielenie Matching Service
 -> wydzielenie Payment Service
 -> event-driven architecture
```

### Decyzja 2: Redis GEO czy H3?

Dla MVP:

```text
Redis GEO
```

Dla większej skali:

```text
H3/S2 + Redis/Aerospike
```

### Decyzja 3: Polling czy WebSocket?

Dla MVP można częściowo użyć pollingu, ale docelowo:

```text
WebSocket/MQTT + push fallback
```

### Decyzja 4: Zewnętrzne mapy czy własny routing?

Dla MVP:

```text
Google Maps / Mapbox
```

Dla dużej skali i kontroli kosztów:

```text
hybryda z własnym cache i ewentualnie OSRM/Valhalla
```

---

## 40. Architektura docelowa

```text
Clients:
  - Passenger iOS/Android
  - Driver iOS/Android
  - Admin Web

Edge:
  - CDN/WAF
  - API Gateway
  - Real-time Gateway

Core Services:
  - Auth Service
  - User Service
  - Driver Service
  - Ride Service
  - Matching Service
  - Location Service
  - Pricing Service
  - Routing/ETA Service
  - Payment Service
  - Notification Service
  - Fraud/Risk Service
  - Support Service

Data:
  - PostgreSQL/MySQL for transactional data
  - Redis/Aerospike for live state
  - Kafka/Pulsar for events
  - Cassandra/Bigtable for location history
  - Elasticsearch/OpenSearch for search
  - Data Lake/Warehouse for analytics

Operations:
  - Monitoring
  - Logging
  - Tracing
  - Alerting
  - Admin tools
```

---

## 41. Największe ryzyka techniczne

### 1. Matching

Najtrudniejsze jest sprawiedliwe i szybkie dopasowanie kierowcy. Proste „najbliższy wygrywa” będzie działało tylko na początku.

### 2. Lokalizacja

Duża liczba aktualizacji lokalizacji może łatwo przeciążyć backend, jeżeli każdą aktualizację potraktujemy jak zwykły request CRUD.

### 3. Spójność stanu przejazdu

Race condition między anulowaniem, akceptacją i timeoutem to klasyczny problem w takim systemie.

### 4. Płatności

Trzeba projektować pod retry, idempotencję, reconciliation i błędy providera.

### 5. Koszty map

Google Maps/Mapbox mogą stać się bardzo drogie przy dużym ruchu, więc cache i agregacja są ważne.

---

## 42. Plan implementacji

### Etap 1 — MVP

- Auth,
- User/Driver,
- prosty Ride Service,
- Redis GEO,
- prosty Matching Service,
- WebSocket dla statusu przejazdu,
- integracja z mapami,
- podstawowe płatności.

### Etap 2 — Stabilizacja

- idempotency keys,
- outbox pattern,
- Kafka,
- monitoring,
- retry/circuit breaker,
- lepsza state machine,
- admin panel,
- support tools.

### Etap 3 — Skalowanie

- osobny Location Service,
- H3/S2 indexing,
- sharding per city,
- real-time gateway cluster,
- pricing service,
- fraud service,
- data warehouse.

### Etap 4 — Optymalizacja

- ML ETA,
- ML matching,
- demand prediction,
- driver positioning,
- dynamic pricing,
- multi-region active-active dla wybranych komponentów.

---

## 43. Rekomendacja techniczna

Dla sensownego startu nie budowałbym od razu ekstremalnie rozproszonego systemu. Najlepsza architektura początkowa:

```text
Backend:
  Modular Monolith + wydzielony Location/Realtime module

Database:
  PostgreSQL

Cache/Live location:
  Redis

Async:
  Kafka albo na start RabbitMQ/SQS

Realtime:
  WebSocket Gateway

Maps:
  Google Maps albo Mapbox

Payments:
  Stripe/Adyen/Przelewy24 zależnie od rynku

Infrastructure:
  Kubernetes lub prostszy managed container service
```

Docelowo wydzieliłbym osobno:

```text
Location Service
Matching Service
Ride Service
Payment Service
Notification Service
Pricing Service
```

Najważniejsze zasady projektowe:

1. **Ride Service jest właścicielem prawdy o stanie przejazdu.**
2. **Location Service nie zapisuje każdej lokalizacji do relacyjnej bazy.**
3. **Matching musi mieć atomic lock na kierowcę.**
4. **Payment Service musi być idempotentny.**
5. **Eventy powinny być publikowane przez Outbox Pattern.**
6. **WebSocket służy do real-time, ale HTTP API pozostaje źródłem odczytu stanu.**
7. **Na start prosty ranking, później ML.**

To jest architektura, którą da się zacząć jako MVP, a potem rozsądnie skalować bez przepisywania całego systemu od zera.
