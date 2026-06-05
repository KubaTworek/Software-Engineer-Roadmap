# System Design — E-commerce Platform

## 1. Cel dokumentu

Ten dokument opisuje kompleksowy projekt architektury systemu e-commerce. Obejmuje wymagania funkcjonalne i niefunkcjonalne, architekturę wysokiego poziomu, podział domenowy, przepływy systemowe, projekt API, model danych, obsługę płatności, inventory, cache, event-driven architecture, bezpieczeństwo, observability, deployment oraz rekomendowany roadmap rozwoju.

Dokument zakłada platformę e-commerce typu B2C lub B2B z możliwością dalszego skalowania w kierunku marketplace.

---

## 2. Założenia biznesowe

System ma obsługiwać:

- przeglądanie katalogu produktów,
- wyszukiwanie i filtrowanie produktów,
- zarządzanie koszykiem,
- składanie zamówień,
- płatności online,
- obsługę promocji, kuponów i rabatów,
- rezerwację oraz aktualizację stanów magazynowych,
- obsługę dostaw,
- panel użytkownika,
- panel administratora,
- powiadomienia e-mail, SMS lub push,
- integracje z systemami płatności, magazynem, kurierami i fakturowaniem.

Rekomendowanym podejściem początkowym jest **modular monolith**, zaprojektowany z wyraźnymi granicami domenowymi. Dzięki temu można szybciej dostarczyć MVP, a później wydzielać wybrane moduły do osobnych serwisów, kiedy pojawi się realna potrzeba skalowania.

---

## 3. Wymagania funkcjonalne

### 3.1 Użytkownik

Użytkownik może:

- założyć konto,
- zalogować się,
- przeglądać produkty,
- wyszukiwać produkty,
- filtrować po kategorii, cenie, marce, dostępności i ocenach,
- dodać produkt do koszyka,
- zmienić ilość produktu w koszyku,
- złożyć zamówienie,
- opłacić zamówienie,
- śledzić status zamówienia,
- anulować zamówienie, jeśli status na to pozwala,
- pobrać fakturę lub potwierdzenie zamówienia,
- wystawić opinię.

### 3.2 Administrator

Administrator może:

- zarządzać produktami,
- zarządzać kategoriami,
- zarządzać stanami magazynowymi,
- zarządzać promocjami,
- zarządzać zamówieniami,
- obsługiwać zwroty i reklamacje,
- widzieć raporty sprzedażowe,
- zarządzać użytkownikami.

### 3.3 System

System musi:

- synchronizować płatności,
- obsługiwać webhooki od operatorów płatności,
- aktualizować statusy zamówień,
- rezerwować i zwalniać stany magazynowe,
- generować dokumenty sprzedaży,
- wysyłać powiadomienia,
- indeksować produkty w wyszukiwarce,
- obsługiwać błędy integracji zewnętrznych.

---

## 4. Wymagania niefunkcjonalne

### 4.1 Skalowalność

Najbardziej obciążone elementy:

- katalog produktów,
- wyszukiwarka,
- koszyk,
- checkout,
- promocje,
- płatności,
- inventory.

Katalog i wyszukiwarka będą miały dużo więcej odczytów niż zapisów. Checkout i płatności będą miały mniejszy ruch, ale większą krytyczność.

### 4.2 Dostępność

Rekomendowany priorytet dostępności:

- katalog: bardzo wysoka dostępność,
- koszyk: wysoka dostępność,
- checkout: wysoka dostępność,
- płatności: wysoka spójność i odporność na duplikaty,
- panel admina: niższy priorytet niż ścieżka zakupowa.

### 4.3 Spójność danych

Silna spójność jest potrzebna dla:

- płatności,
- zamówień,
- rezerwacji stanów magazynowych,
- finalizacji checkoutu.

Spójność eventual consistency wystarczy dla:

- wyszukiwarki,
- rekomendacji,
- raportów,
- widoków produktowych,
- powiadomień,
- analityki.

### 4.4 Bezpieczeństwo

System musi obsługiwać:

- uwierzytelnianie,
- autoryzację ról,
- ochronę danych osobowych,
- szyfrowanie danych wrażliwych,
- zabezpieczenie webhooków,
- rate limiting,
- ochronę przed fraudem,
- audyt akcji administratorów.

---

## 5. Architektura wysokiego poziomu

```text
                ┌─────────────────────┐
                │     Web / Mobile     │
                └──────────┬──────────┘
                           │
                    ┌──────▼──────┐
                    │ API Gateway │
                    └──────┬──────┘
                           │
 ┌─────────────────────────┼─────────────────────────┐
 │                         │                         │
 ▼                         ▼                         ▼
Auth Service        Catalog Service            Cart Service
 │                         │                         │
 ▼                         ▼                         ▼
User DB              Product DB / Search       Redis / Cart DB

 ┌─────────────────────────┼─────────────────────────┐
 │                         │                         │
 ▼                         ▼                         ▼
Order Service        Payment Service           Inventory Service
 │                         │                         │
 ▼                         ▼                         ▼
Order DB             Payment Provider          Inventory DB

 ┌─────────────────────────┼─────────────────────────┐
 │                         │                         │
 ▼                         ▼                         ▼
Promotion Service    Notification Service      Shipping Service
 │                         │                         │
 ▼                         ▼                         ▼
Promo DB             Email/SMS/Push            Courier APIs

                           │
                           ▼
                    Message Broker
                Kafka / RabbitMQ / SQS
```

---

## 6. Podział domenowy

### 6.1 Auth / Identity

Odpowiada za:

- rejestrację,
- logowanie,
- refresh tokeny,
- role,
- reset hasła,
- MFA, jeżeli potrzebne,
- sesje użytkowników.

Przykładowy model danych:

```text
users
- id
- email
- password_hash
- phone
- status
- created_at
- updated_at

roles
- id
- name

user_roles
- user_id
- role_id
```

Rekomendacja: hasła przechowywać jako hash, np. Argon2id lub bcrypt. Nie przechowywać danych kart płatniczych.

---

### 6.2 Catalog Service

Odpowiada za:

- produkty,
- warianty produktów,
- kategorie,
- marki,
- ceny bazowe,
- zdjęcia,
- atrybuty,
- widoczność produktu.

Przykładowy model danych:

```text
products
- id
- sku
- name
- slug
- description
- brand_id
- category_id
- status
- created_at
- updated_at

product_variants
- id
- product_id
- sku
- name
- price
- currency
- attributes_json
- status

categories
- id
- parent_id
- name
- slug

product_images
- id
- product_id
- url
- sort_order
```

Katalog powinien mieć osobny model zapisu i odczytu. Relacyjna baza może być źródłem prawdy, a Elasticsearch/OpenSearch może obsługiwać wyszukiwanie.

---

### 6.3 Search Service

Odpowiada za:

- full-text search,
- filtrowanie,
- sortowanie,
- facety,
- sugestie,
- autocomplete.

Rekomendowana technologia:

```text
OpenSearch / Elasticsearch
```

Przykładowy dokument w indeksie:

```json
{
  "product_id": "p_123",
  "name": "Nike Air Max",
  "description": "Buty sportowe...",
  "category": "Shoes",
  "brand": "Nike",
  "price": 499.99,
  "currency": "PLN",
  "attributes": {
    "size": ["42", "43"],
    "color": ["black"]
  },
  "available": true,
  "rating": 4.7
}
```

Aktualizacja indeksu powinna być asynchroniczna:

```text
ProductUpdated event → Message Broker → Search Indexer → OpenSearch
```

---

### 6.4 Cart Service

Koszyk jest stanem tymczasowym i nie musi mieć takiej samej trwałości jak zamówienie.

Możliwe podejścia:

- Redis dla koszyka anonimowego i szybkiego dostępu,
- PostgreSQL/DynamoDB dla trwałego koszyka użytkownika,
- hybryda: Redis + okresowa persystencja.

Model:

```text
carts
- id
- user_id nullable
- session_id nullable
- status
- created_at
- updated_at

cart_items
- id
- cart_id
- product_variant_id
- quantity
- unit_price_snapshot
- added_at
```

Cena w koszyku może się zmienić. Przy checkout trzeba ponownie przeliczyć ceny, promocje i dostępność.

---

### 6.5 Checkout Service

Checkout koordynuje proces składania zamówienia.

Odpowiada za:

- walidację koszyka,
- przeliczenie cen,
- naliczenie promocji,
- sprawdzenie dostępności,
- rezerwację stocku,
- utworzenie zamówienia,
- rozpoczęcie płatności.

Przykładowy flow:

```text
1. User clicks "Buy"
2. Checkout validates cart
3. Checkout checks inventory
4. Checkout calculates final price
5. Checkout reserves inventory
6. Checkout creates order with status PENDING_PAYMENT
7. Checkout creates payment intent
8. User pays
9. Payment webhook confirms payment
10. Order becomes PAID
11. Fulfillment process starts
```

Checkout nie powinien ufać cenom z frontendu.

---

### 6.6 Order Service

Order Service jest jednym z najważniejszych elementów systemu.

Statusy zamówień:

```text
CREATED
PENDING_PAYMENT
PAID
PAYMENT_FAILED
PROCESSING
SHIPPED
DELIVERED
CANCELLED
RETURNED
REFUNDED
```

Model:

```text
orders
- id
- user_id
- order_number
- status
- currency
- subtotal_amount
- discount_amount
- shipping_amount
- total_amount
- billing_address_id
- shipping_address_id
- created_at
- updated_at

order_items
- id
- order_id
- product_id
- product_variant_id
- sku
- product_name_snapshot
- quantity
- unit_price
- discount_amount
- total_amount

order_status_history
- id
- order_id
- old_status
- new_status
- changed_by
- reason
- created_at
```

Zamówienie powinno przechowywać snapshot danych produktu, ceny i adresu. Nie można polegać wyłącznie na aktualnych danych z katalogu, bo produkt może później zmienić nazwę, cenę albo zostać usunięty.

---

### 6.7 Payment Service

Odpowiada za:

- tworzenie płatności,
- obsługę webhooków,
- idempotencję,
- refundy,
- mapowanie statusów operatora płatności na statusy wewnętrzne.

Nie należy samodzielnie przechowywać danych kart. Operator płatności powinien obsługiwać tokenizację.

Model:

```text
payments
- id
- order_id
- provider
- provider_payment_id
- status
- amount
- currency
- idempotency_key
- created_at
- updated_at

payment_events
- id
- payment_id
- provider_event_id
- event_type
- payload_json
- received_at
```

Kluczowe zasady:

- każdy webhook musi być weryfikowany podpisem,
- każdy webhook musi być idempotentny,
- płatność nie może zostać zaksięgowana dwa razy,
- `provider_event_id` powinien być unikalny.

---

### 6.8 Inventory Service

Inventory Service zarządza stanami magazynowymi.

Model:

```text
inventory_items
- id
- product_variant_id
- warehouse_id
- available_quantity
- reserved_quantity
- sold_quantity
- updated_at

inventory_reservations
- id
- order_id
- product_variant_id
- quantity
- status
- expires_at
- created_at
```

Statusy rezerwacji:

```text
ACTIVE
CONFIRMED
RELEASED
EXPIRED
```

Przykład:

```text
available_quantity = 10
reserved_quantity = 2
sellable_quantity = available_quantity - reserved_quantity = 8
```

Checkout powinien tworzyć rezerwację na określony czas, np. 15 minut. Jeśli płatność nie zostanie zakończona, rezerwacja wygasa.

---

### 6.9 Promotion Service

Odpowiada za:

- kupony,
- rabaty procentowe,
- rabaty kwotowe,
- promocje na kategorie,
- promocje na produkty,
- darmową dostawę,
- limity użyć,
- reguły typu “kup 2, zapłać za 1”.

Model:

```text
promotions
- id
- name
- type
- status
- starts_at
- ends_at
- priority
- rules_json
- effects_json

coupons
- id
- code
- promotion_id
- max_uses
- used_count
- user_limit
```

Promocje powinny być liczone po stronie backendu, najlepiej w jednym miejscu. To ogranicza błędy i nadużycia.

---

### 6.10 Shipping Service

Odpowiada za:

- metody dostawy,
- kalkulację kosztów dostawy,
- integrację z kurierami,
- tworzenie etykiet,
- tracking.

Model:

```text
shipments
- id
- order_id
- provider
- tracking_number
- status
- shipping_method
- shipped_at
- delivered_at
```

Statusy:

```text
PENDING
LABEL_CREATED
PICKED_UP
IN_TRANSIT
DELIVERED
FAILED
RETURNED
```

---

### 6.11 Notification Service

Odpowiada za:

- e-maile transakcyjne,
- SMS-y,
- powiadomienia push,
- szablony wiadomości,
- retry w przypadku błędów.

Przykładowe eventy:

```text
OrderCreated
PaymentSucceeded
PaymentFailed
OrderShipped
OrderDelivered
RefundCreated
```

Notification Service powinien działać asynchronicznie. Brak wysłania e-maila nie może blokować zamówienia.

---

### 6.12 Admin Panel

Panel administracyjny powinien korzystać z tych samych API, ale mieć osobną warstwę autoryzacji.

Funkcje:

- CRUD produktów,
- zarządzanie zamówieniami,
- zarządzanie promocjami,
- zarządzanie użytkownikami,
- raporty,
- ręczne zwroty,
- historia zmian.

Każda krytyczna akcja admina powinna być audytowana:

```text
admin_audit_logs
- id
- admin_user_id
- action
- entity_type
- entity_id
- old_value_json
- new_value_json
- ip_address
- created_at
```

---

## 7. Główne flow systemowe

### 7.1 Przeglądanie produktu

```text
User → Frontend → API Gateway → Catalog Service
                              → Inventory Service
                              → Review Service
                              → Pricing/Promotion Service
```

Dla wydajności można używać widoku produktowego zdenormalizowanego:

```text
Product Read Model
- product data
- price
- availability
- rating
- images
```

Taki widok może być aktualizowany eventami.

---

### 7.2 Dodanie do koszyka

```text
User → Cart API
Cart Service:
  1. sprawdza, czy produkt istnieje
  2. sprawdza, czy wariant jest aktywny
  3. opcjonalnie sprawdza dostępność
  4. zapisuje pozycję w koszyku
```

Na tym etapie nie należy jeszcze finalnie rezerwować stocku, bo użytkownicy często porzucają koszyki.

---

### 7.3 Checkout

```text
User → Checkout API

Checkout Service:
  1. Pobiera koszyk
  2. Waliduje produkty
  3. Pobiera aktualne ceny
  4. Przelicza promocje
  5. Sprawdza inventory
  6. Rezerwuje inventory
  7. Tworzy order
  8. Tworzy payment intent
  9. Zwraca URL/session płatności
```

To powinno być idempotentne. Jeśli użytkownik kliknie “Kupuję” dwa razy, system nie może utworzyć dwóch opłacanych zamówień.

---

### 7.4 Płatność

```text
Payment Provider → Webhook → Payment Service
Payment Service:
  1. Weryfikuje podpis webhooka
  2. Sprawdza provider_event_id
  3. Aktualizuje payment status
  4. Publikuje PaymentSucceeded / PaymentFailed
```

Następnie:

```text
PaymentSucceeded → Order Service → status PAID
PaymentSucceeded → Inventory Service → confirm reservation
PaymentSucceeded → Notification Service → send confirmation
PaymentSucceeded → Fulfillment Service → start processing
```

---

### 7.5 Anulowanie zamówienia

```text
User/Admin → Order Service

Jeżeli status pozwala:
  1. Order status = CANCELLED
  2. Payment refund, jeśli płatność była pobrana
  3. Inventory reservation release
  4. Notification
```

Nie każde zamówienie można anulować. Przykładowo, po przekazaniu do kuriera anulowanie może już wymagać procesu zwrotu.

---

## 8. API Design

### 8.1 Public API

#### Produkty

```http
GET /api/products
GET /api/products/{slug}
GET /api/categories
GET /api/search?q=nike&category=shoes&price_min=100&price_max=500
```

#### Koszyk

```http
GET /api/cart
POST /api/cart/items
PATCH /api/cart/items/{itemId}
DELETE /api/cart/items/{itemId}
```

Body:

```json
{
  "productVariantId": "var_123",
  "quantity": 2
}
```

#### Checkout

```http
POST /api/checkout
```

Body:

```json
{
  "cartId": "cart_123",
  "shippingAddressId": "addr_1",
  "billingAddressId": "addr_2",
  "shippingMethodId": "courier_standard",
  "couponCode": "SUMMER20",
  "idempotencyKey": "uuid-generated-by-client"
}
```

Response:

```json
{
  "orderId": "ord_123",
  "paymentUrl": "https://payment-provider/checkout/session/abc"
}
```

#### Zamówienia

```http
GET /api/orders
GET /api/orders/{orderId}
POST /api/orders/{orderId}/cancel
```

#### Płatności

```http
POST /api/payments/webhook/{provider}
```

Webhook musi być niedostępny logicznie dla zwykłych użytkowników i weryfikowany podpisem.

---

## 9. Event-driven architecture

Rekomendowany message broker:

- Kafka, jeśli potrzebujesz dużej przepustowości i event streamingu,
- RabbitMQ, jeśli zależy Ci na prostszej kolejce z routingiem,
- AWS SQS/SNS, jeśli system jest na AWS i chcesz prostszej obsługi operacyjnej.

Przykładowe eventy:

```text
ProductCreated
ProductUpdated
ProductDeleted
CartCheckedOut
OrderCreated
OrderPaid
OrderCancelled
PaymentSucceeded
PaymentFailed
InventoryReserved
InventoryReservationExpired
ShipmentCreated
ShipmentDelivered
RefundIssued
```

Przykład eventu:

```json
{
  "eventId": "evt_123",
  "eventType": "OrderPaid",
  "occurredAt": "2026-06-05T10:00:00Z",
  "version": 1,
  "payload": {
    "orderId": "ord_123",
    "userId": "usr_456",
    "totalAmount": 299.99,
    "currency": "PLN"
  }
}
```

Warto zastosować **Outbox Pattern**, żeby nie zgubić eventów.

---

## 10. Outbox Pattern

Problem:

```text
Order Service zapisuje zamówienie w DB,
ale publikacja eventu do brokera się nie udaje.
```

Rozwiązanie:

W tej samej transakcji zapisujesz zamówienie i event do tabeli `outbox_events`.

```text
orders
outbox_events
```

Potem osobny worker publikuje eventy do brokera.

Model:

```text
outbox_events
- id
- aggregate_type
- aggregate_id
- event_type
- payload_json
- status
- created_at
- published_at
```

Dzięki temu nie tracisz eventów między bazą a brokerem.

---

## 11. Idempotencja

Idempotencja jest krytyczna w:

- checkout,
- płatnościach,
- webhookach,
- tworzeniu zamówień,
- refundach,
- rezerwacji inventory.

Przykłady:

```text
POST /checkout z tym samym idempotencyKey
→ zwraca to samo orderId zamiast tworzyć nowe zamówienie
```

Tabela:

```text
idempotency_keys
- key
- user_id
- request_hash
- response_json
- status
- created_at
```

Dla webhooków:

```text
payment_events.provider_event_id UNIQUE
```

---

## 12. Bazy danych

### 12.1 Rekomendowany zestaw

```text
PostgreSQL        główne dane transakcyjne
Redis             cache, sesje, koszyk, rate limiting
OpenSearch        wyszukiwanie produktów
Object Storage    zdjęcia produktów, faktury, dokumenty
Kafka/RabbitMQ    komunikacja asynchroniczna
Data Warehouse    raporty i analityka
```

### 12.2 Które dane gdzie?

| Obszar | Technologia |
|---|---|
| Użytkownicy | PostgreSQL |
| Produkty | PostgreSQL |
| Koszyk | Redis + PostgreSQL opcjonalnie |
| Zamówienia | PostgreSQL |
| Płatności | PostgreSQL |
| Inventory | PostgreSQL |
| Search | OpenSearch |
| Sesje | Redis |
| Zdjęcia | S3/GCS/Azure Blob |
| Eventy | Kafka/RabbitMQ/SQS |
| Raporty | BigQuery/Snowflake/Redshift/PostgreSQL read replica |

---

## 13. Cache

Cache powinien być używany ostrożnie. Najlepsze miejsca:

- lista kategorii,
- szczegóły produktu,
- rekomendacje,
- konfiguracja promocji,
- sesje,
- koszyk,
- rate limiting.

Przykład cache key:

```text
product:{product_id}:details
category:{category_id}:tree
search:query_hash:{hash}
cart:{user_id}
```

Strategie:

- TTL dla danych często odczytywanych,
- invalidacja przez eventy,
- cache aside pattern,
- stale-while-revalidate dla katalogu.

Nie należy cache’ować finalnej ceny checkoutu bez ponownej walidacji.

---

## 14. Skalowanie

### 14.1 Najpierw modular monolith

Na początku:

```text
Jedna aplikacja backendowa
Jedna baza PostgreSQL
Redis
OpenSearch
Kolejka
```

Wewnątrz aplikacji moduły:

```text
Auth
Catalog
Cart
Checkout
Order
Payment
Inventory
Promotion
Shipping
Notification
Admin
```

To jest prostsze, tańsze i szybsze w rozwoju.

### 14.2 Później mikroserwisy

Wydzielać dopiero wtedy, gdy pojawi się realny problem:

- Catalog/Search ma dużo ruchu,
- Order/Payment wymaga niezależnej niezawodności,
- Inventory wymaga osobnego modelu skalowania,
- Notification generuje dużo zadań asynchronicznych,
- Admin rozwija się niezależnie.

Kandydaci do wydzielenia:

```text
Catalog Service
Search Service
Order Service
Payment Service
Inventory Service
Notification Service
```

---

## 15. Spójność i transakcje

Najtrudniejszy problem: checkout dotyka kilku domen:

- koszyk,
- ceny,
- promocje,
- inventory,
- order,
- payment.

Nie należy robić jednej wielkiej rozproszonej transakcji. Lepsze podejście to **Saga Pattern**.

Przykład sagi checkoutu:

```text
1. ValidateCart
2. ReserveInventory
3. CreateOrder
4. CreatePaymentIntent
5. WaitForPayment
6. ConfirmInventory
7. MarkOrderAsPaid
8. StartFulfillment
```

Kroki kompensujące:

```text
PaymentFailed → ReleaseInventory
OrderCancelled → ReleaseInventory
PaymentCapturedButOrderFailed → RefundPayment
```

---

## 16. Security Design

### 16.1 Uwierzytelnianie

- JWT access token z krótkim TTL,
- refresh token rotowany,
- sesje przechowywane po stronie backendu lub token blacklist w Redisie,
- MFA dla adminów.

### 16.2 Autoryzacja

Role:

```text
CUSTOMER
SUPPORT_AGENT
WAREHOUSE_MANAGER
ADMIN
SUPER_ADMIN
```

Zasady:

- użytkownik widzi tylko swoje zamówienia,
- admin ma uprawnienia według roli,
- akcje finansowe wymagają silniejszych uprawnień,
- operacje admina są audytowane.

### 16.3 Ochrona API

- rate limiting,
- CSRF protection, jeśli używane są cookies,
- CORS ograniczony do znanych domen,
- input validation,
- output encoding,
- ochrona przed brute force,
- blokady po wielu nieudanych logowaniach.

### 16.4 Dane wrażliwe

- hasła tylko jako hash,
- tokeny szyfrowane lub trzymane w secret managerze,
- dane płatnicze poza systemem, u operatora,
- logi bez danych wrażliwych,
- szyfrowanie danych w spoczynku i w tranzycie.

---

## 17. Observability

System powinien mieć:

- logi strukturalne,
- metryki,
- tracing,
- alerty,
- dashboardy biznesowe i techniczne.

### 17.1 Przykładowe metryki techniczne

```text
http_request_duration
http_5xx_rate
db_query_duration
redis_latency
queue_lag
payment_webhook_failures
checkout_failure_rate
search_latency
```

### 17.2 Przykładowe metryki biznesowe

```text
conversion_rate
cart_abandonment_rate
orders_per_hour
gross_merchandise_value
average_order_value
payment_success_rate
refund_rate
out_of_stock_rate
```

### 17.3 Trace ID

Każde żądanie powinno mieć `correlation_id`, przekazywane przez:

```text
API Gateway → services → message broker → workers
```

---

## 18. Failure scenarios

### 18.1 Płatność się udała, ale webhook dotarł dwa razy

Rozwiązanie:

```text
provider_event_id UNIQUE
```

Drugi webhook zostaje przyjęty, ale nie wykonuje ponownie skutków ubocznych.

### 18.2 Użytkownik kliknął checkout dwa razy

Rozwiązanie:

```text
idempotencyKey
```

Zwracamy istniejące zamówienie lub istniejącą sesję płatności.

### 18.3 Brak stocku po kliknięciu “Kup”

Rozwiązanie:

- ponowna walidacja stocku przy checkout,
- komunikat do użytkownika,
- aktualizacja koszyka.

### 18.4 Payment provider nie odpowiada

Rozwiązanie:

- retry z backoffem,
- status `PENDING_PAYMENT`,
- możliwość wznowienia płatności,
- job sprawdzający status płatności po stronie providera.

### 18.5 Search index jest opóźniony

Akceptowalne, jeśli produkt został zaktualizowany, ale wyszukiwarka pokaże starą wersję przez kilka sekund.

Źródłem prawdy pozostaje baza katalogu.

### 18.6 Notification Service nie działa

Nie blokuje zamówienia. Event zostaje w kolejce i jest ponawiany.

---

## 19. Deployment

### 19.1 Proponowana infrastruktura

```text
Frontend: CDN + static hosting
Backend: Kubernetes / ECS / Cloud Run
Database: managed PostgreSQL
Cache: managed Redis
Search: OpenSearch
Queue: Kafka / RabbitMQ / SQS
Storage: S3-compatible object storage
Secrets: Secret Manager / Vault
Monitoring: Prometheus + Grafana / Datadog
Logs: ELK / Loki / Cloud Logging
Tracing: OpenTelemetry
```

### 19.2 Środowiska

```text
local
dev
staging
production
```

### 19.3 CI/CD

Pipeline:

```text
1. lint
2. tests
3. build
4. security scan
5. migration check
6. deploy to staging
7. smoke tests
8. deploy to production
```

Deployment:

- blue-green albo rolling deployment,
- feature flags,
- automatyczny rollback przy błędach,
- migracje DB kompatybilne wstecznie.

---

## 20. Frontend Architecture

Frontend może być zrobiony jako:

```text
Next.js / React
```

Moduły:

```text
Home
Product Listing Page
Product Detail Page
Cart
Checkout
User Account
Orders
Admin Panel
```

Rekomendacje:

- SSR/SSG dla stron produktowych i kategorii,
- CSR dla koszyka i panelu użytkownika,
- CDN dla assetów,
- image optimization,
- lazy loading,
- schema.org dla SEO,
- obsługa canonical URLs.

---

## 21. SEO

Dla e-commerce SEO jest krytyczne.

System powinien obsługiwać:

- przyjazne URL-e,
- server-side rendering,
- sitemap.xml,
- robots.txt,
- canonical tags,
- structured data,
- metadane produktów,
- przekierowania 301 dla usuniętych produktów,
- strony kategorii indeksowalne,
- filtrowanie z kontrolą indeksacji.

Przykładowe URL-e:

```text
/products/nike-air-max-black
/category/buty-sportowe
/brand/nike
```

---

## 22. Rekomendowany model startowy

Najrozsądniejszy wariant startowy:

```text
Frontend:
- Next.js

Backend:
- Modular monolith
- NestJS / Spring Boot / Django / Laravel / .NET

Database:
- PostgreSQL

Cache:
- Redis

Search:
- OpenSearch

Queue:
- RabbitMQ albo SQS

Storage:
- S3-compatible storage

Payments:
- Stripe / PayU / Przelewy24 / Adyen, zależnie od rynku

Monitoring:
- OpenTelemetry + Grafana/Datadog
```

---

## 23. Docelowa architektura logiczna

```text
                           ┌───────────────────────┐
                           │        Frontend        │
                           │   Web / Mobile / PWA   │
                           └───────────┬───────────┘
                                       │
                           ┌───────────▼───────────┐
                           │      API Gateway       │
                           │ Auth, Rate Limit, WAF  │
                           └───────────┬───────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
        ▼                              ▼                              ▼
┌───────────────┐              ┌───────────────┐              ┌───────────────┐
│ Catalog       │              │ Cart          │              │ Checkout      │
│ Module        │              │ Module        │              │ Module        │
└───────┬───────┘              └───────┬───────┘              └───────┬───────┘
        │                              │                              │
        ▼                              ▼                              ▼
┌───────────────┐              ┌───────────────┐              ┌───────────────┐
│ Product DB    │              │ Redis         │              │ Order Module  │
└───────────────┘              └───────────────┘              └───────┬───────┘
                                                                        │
        ┌──────────────────────────────┬────────────────────────────────┘
        │                              │
        ▼                              ▼
┌───────────────┐              ┌───────────────┐
│ Payment       │              │ Inventory     │
│ Module        │              │ Module        │
└───────┬───────┘              └───────┬───────┘
        │                              │
        ▼                              ▼
┌───────────────┐              ┌───────────────┐
│ Provider API  │              │ Inventory DB  │
└───────────────┘              └───────────────┘

        ┌─────────────────────────────────────────────────────────────┐
        │                     Message Broker                          │
        └───────────────┬───────────────────────┬─────────────────────┘
                        │                       │
                        ▼                       ▼
              ┌─────────────────┐      ┌─────────────────┐
              │ Notifications   │      │ Search Indexer   │
              └─────────────────┘      └─────────────────┘
```

---

## 24. Najważniejsze decyzje architektoniczne

### Decyzja 1: Modular monolith zamiast mikroserwisów na start

Uzasadnienie:

- szybszy development,
- mniej kosztów operacyjnych,
- prostszy deployment,
- łatwiejsze transakcje,
- mniejsze ryzyko przedwczesnej komplikacji.

Granice domenowe trzeba jednak zaprojektować tak, jakby moduły mogły zostać później wydzielone.

### Decyzja 2: PostgreSQL jako główna baza transakcyjna

Uzasadnienie:

- dobre transakcje,
- spójność,
- relacje między zamówieniami, użytkownikami i płatnościami,
- dojrzałość,
- łatwe raportowanie na początku.

### Decyzja 3: OpenSearch dla wyszukiwania

Relacyjna baza nie jest najlepszym narzędziem do zaawansowanego wyszukiwania produktowego, facetingu i autocomplete.

### Decyzja 4: Redis dla cache i koszyka

Koszyk jest często odczytywany i modyfikowany, ale nie jest tak krytyczny jak zamówienie. Redis dobrze pasuje do tej roli, o ile finalny checkout zawsze wykonuje ponowną walidację.

### Decyzja 5: Eventy dla procesów pobocznych

Nie warto blokować checkoutu przez:

- e-mail,
- indeksację search,
- analitykę,
- synchronizację raportów.

To powinno działać asynchronicznie.

---

## 25. Minimalny MVP

Na MVP wystarczy:

```text
Frontend:
- lista produktów
- szczegóły produktu
- koszyk
- checkout
- konto użytkownika
- lista zamówień

Backend:
- Auth
- Catalog
- Cart
- Order
- Payment
- Inventory
- Admin basic

Integracje:
- jeden provider płatności
- jedna metoda dostawy
- e-mail transakcyjny
```

Nie robić od razu:

- marketplace,
- wielu magazynów,
- zaawansowanego systemu rekomendacji,
- rozbudowanego loyalty programu,
- mikroserwisów dla wszystkiego,
- własnego systemu płatności,
- pełnego ERP.

---

## 26. Ryzyka projektowe

Największe ryzyka:

1. **Błędna obsługa stocku**  
   Może prowadzić do sprzedaży produktów, których nie ma.

2. **Brak idempotencji płatności**  
   Może prowadzić do podwójnych zamówień lub błędnych statusów.

3. **Zbyt wczesne mikroserwisy**  
   Spowolnią development i podniosą koszt utrzymania.

4. **Niespójne ceny między koszykiem a checkoutem**  
   Cena musi być zawsze finalnie liczona po stronie backendu.

5. **Słaba obsługa webhooków**  
   Webhooki od operatorów płatności są krytyczne i muszą być odporne na duplikaty, opóźnienia i błędy.

6. **Brak audit logów w adminie**  
   Przy e-commerce to poważny problem operacyjny i bezpieczeństwa.

---

## 27. Rekomendowany roadmap

### Etap 1 — MVP

- Auth
- Catalog
- Cart
- Checkout
- Orders
- Payments
- Basic Admin
- Basic Inventory
- E-mail confirmations

### Etap 2 — Stabilizacja

- OpenSearch
- Redis cache
- idempotency keys
- outbox pattern
- monitoring
- audit logs
- retry dla integracji

### Etap 3 — Skalowanie

- wydzielenie Search Indexera,
- wydzielenie Notification Service,
- read replicas,
- CDN,
- lepsze cache’owanie katalogu,
- worker do inventory expiration.

### Etap 4 — Zaawansowane funkcje

- promocje zaawansowane,
- zwroty,
- faktury,
- integracja ERP/WMS,
- rekomendacje,
- marketplace,
- wiele magazynów,
- dynamic pricing,
- loyalty program.

---

## 28. Podsumowanie rekomendowanej architektury

Najlepszy praktyczny design:

```text
Start:
Modular Monolith + PostgreSQL + Redis + OpenSearch + Queue

Docelowo:
Wybrane domeny jako osobne serwisy:
- Catalog/Search
- Order
- Payment
- Inventory
- Notification
```

Najważniejsze zasady:

- checkout musi być idempotentny,
- płatności muszą być odporne na duplikaty,
- stock musi być rezerwowany i zwalniany,
- ceny muszą być przeliczane po stronie backendu,
- wyszukiwarka nie jest źródłem prawdy,
- eventy muszą być publikowane przez Outbox Pattern,
- admin actions muszą być audytowane,
- system nie powinien zaczynać jako przesadnie rozproszony.

---

## 29. Rekomendowany stos technologiczny

Przykładowy stack dla realnego projektu:

```text
Frontend:
- Next.js
- React
- TypeScript

Backend:
- NestJS / Spring Boot / .NET / Django
- REST API lub GraphQL dla wybranych use-case’ów

Database:
- PostgreSQL

Cache:
- Redis

Search:
- OpenSearch / Elasticsearch

Queue:
- RabbitMQ / Kafka / AWS SQS

Storage:
- S3-compatible object storage

Payments:
- Stripe / PayU / Przelewy24 / Adyen

Infrastructure:
- Docker
- Kubernetes / ECS / Cloud Run
- Terraform

Observability:
- OpenTelemetry
- Prometheus
- Grafana
- Loki / ELK / Cloud Logging
```

---

## 30. Finalna rekomendacja

Dla większości zespołów najlepszą decyzją będzie rozpoczęcie od dobrze zaprojektowanego modular monolithu z wyraźnymi granicami domenowymi.

Nie należy zaczynać od pełnej architektury mikroserwisowej, jeśli nie ma jeszcze realnych problemów ze skalowaniem, zespołami lub niezależnym deploymentem. Najpierw warto zbudować solidny, spójny rdzeń domenowy: katalog, koszyk, checkout, zamówienia, płatności i inventory.

Najbardziej krytyczne technicznie obszary to:

- idempotencja checkoutu,
- poprawna obsługa webhooków płatności,
- rezerwacje stocku,
- spójność statusów zamówień,
- bezpieczeństwo panelu administracyjnego,
- audyt operacji,
- odporność na błędy integracji zewnętrznych.

Taka architektura jest wystarczająco prosta na MVP, ale jednocześnie przygotowana pod dalszy rozwój i skalowanie.
