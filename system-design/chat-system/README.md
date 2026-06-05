# Chat System — System Design

Kompleksowy projekt systemu czatu obsługującego rozmowy 1:1, rozmowy grupowe, wiadomości w czasie rzeczywistym, historię konwersacji, presence, potwierdzenia odczytu, załączniki, wyszukiwanie i powiadomienia push.

---

## Spis treści

1. [Cel systemu](#1-cel-systemu)
2. [Założenia produktowe](#2-założenia-produktowe)
3. [Wymagania niefunkcjonalne](#3-wymagania-niefunkcjonalne)
4. [Szacowanie skali](#4-szacowanie-skali)
5. [Architektura wysokiego poziomu](#5-architektura-wysokiego-poziomu)
6. [Główne komponenty](#6-główne-komponenty)
7. [Data model](#7-data-model)
8. [API Design](#8-api-design)
9. [WebSocket Protocol](#9-websocket-protocol)
10. [Przepływ wysyłania wiadomości](#10-przepływ-wysyłania-wiadomości)
11. [Idempotencja i retry](#11-idempotencja-i-retry)
12. [Ordering wiadomości](#12-ordering-wiadomości)
13. [Gwarancje dostarczenia](#13-gwarancje-dostarczenia)
14. [Synchronizacja po reconnect](#14-synchronizacja-po-reconnect)
15. [Conversation list i unread count](#15-conversation-list-i-unread-count)
16. [Presence i typing indicator](#16-presence-i-typing-indicator)
17. [Powiadomienia push](#17-powiadomienia-push)
18. [Załączniki](#18-załączniki)
19. [Wyszukiwanie wiadomości](#19-wyszukiwanie-wiadomości)
20. [Bazy danych](#20-bazy-danych)
21. [Partycjonowanie wiadomości](#21-partycjonowanie-wiadomości)
22. [Cache](#22-cache)
23. [Event-driven architecture](#23-event-driven-architecture)
24. [Outbox pattern](#24-outbox-pattern)
25. [Rate limiting i anti-spam](#25-rate-limiting-i-anti-spam)
26. [Moderacja](#26-moderacja)
27. [Bezpieczeństwo](#27-bezpieczeństwo)
28. [Multi-device support](#28-multi-device-support)
29. [Obsługa offline](#29-obsługa-offline)
30. [Edycja i usuwanie wiadomości](#30-edycja-i-usuwanie-wiadomości)
31. [Fan-out strategy](#31-fan-out-strategy)
32. [WebSocket scaling](#32-websocket-scaling)
33. [Consistency model](#33-consistency-model)
34. [Disaster recovery](#34-disaster-recovery)
35. [Observability](#35-observability)
36. [Deployment](#36-deployment)
37. [CI/CD](#37-cicd)
38. [Testowanie](#38-testowanie)
39. [Edge cases](#39-edge-cases)
40. [Rekomendowany MVP](#40-rekomendowany-mvp)
41. [Docelowa wersja skalowalna](#41-docelowa-wersja-skalowalna)
42. [Kompromisy projektowe](#42-kompromisy-projektowe)
43. [Bounded contexts](#43-bounded-contexts)
44. [Kolejność implementacji](#44-kolejność-implementacji)
45. [Największe ryzyka techniczne](#45-największe-ryzyka-techniczne)
46. [Podsumowanie](#46-podsumowanie)

---

## 1. Cel systemu

Projektujemy system podobny funkcjonalnie do Slacka, Messengera, Discorda lub WhatsAppa, ale w wersji ogólnej, możliwej do rozwinięcia w kilku kierunkach.

System ma umożliwiać:

- rozmowy 1:1,
- rozmowy grupowe,
- wysyłanie i odbieranie wiadomości w czasie rzeczywistym,
- przechowywanie historii wiadomości,
- status online/offline,
- potwierdzenia dostarczenia i odczytu,
- załączniki,
- powiadomienia push,
- wyszukiwanie wiadomości,
- moderację i blokowanie użytkowników,
- skalowanie do dużej liczby użytkowników.

---

## 2. Założenia produktowe

Zakładamy zakres MVP+.

### Funkcje podstawowe

1. Rejestracja i logowanie użytkownika.
2. Lista konwersacji użytkownika.
3. Czat 1:1.
4. Czat grupowy.
5. Wysyłanie tekstu.
6. Edycja i usuwanie własnych wiadomości.
7. Historia wiadomości z paginacją.
8. Status wiadomości:
   - `sent`,
   - `delivered`,
   - `read`.
9. Status obecności:
   - `online`,
   - `offline`,
   - `last seen`.
10. Powiadomienia push dla użytkowników offline.

### Funkcje rozszerzone

1. Załączniki: obrazy, pliki, audio.
2. Reakcje emoji.
3. Wyszukiwanie wiadomości.
4. Typing indicator.
5. Blokowanie użytkowników.
6. Role w grupach:
   - owner,
   - admin,
   - member.
7. Moderacja treści.
8. Rate limiting i ochrona przed spamem.
9. Szyfrowanie transportu i danych wrażliwych.
10. Event sourcing lub outbox pattern dla niezawodności.

---

## 3. Wymagania niefunkcjonalne

### Dostępność

System powinien być dostępny przez większość czasu.

Dla poważnego produktu warto celować w:

- 99.9% dla MVP,
- 99.95% lub więcej dla większej skali.

### Opóźnienia

Dla czatu czas rzeczywisty jest kluczowy.

Docelowe wartości:

- wysłanie wiadomości do serwera: poniżej 100 ms,
- dostarczenie do odbiorcy online: poniżej 300 ms,
- pobranie historii: poniżej 500 ms,
- propagacja typing indicator: poniżej 200 ms,
- aktualizacja presence: poniżej 1–3 sekund.

### Skalowalność

System powinien skalować się poziomo.

Najważniejsze komponenty do skalowania:

- WebSocket Gateway,
- Message Service,
- Conversation Service,
- Notification Service,
- bazy danych wiadomości,
- cache presence,
- kolejki zdarzeń.

### Spójność danych

Nie wszystko musi być silnie spójne.

Wymagana silna lub bliska silnej spójność:

- zapis wiadomości,
- członkostwo w konwersacji,
- uprawnienia do odczytu i wysyłania.

Wystarczy eventual consistency:

- unread count,
- presence,
- typing indicator,
- delivered/read receipts,
- powiadomienia push,
- indeks wyszukiwania.

### Bezpieczeństwo

System musi chronić:

- dane użytkowników,
- treść wiadomości,
- pliki,
- tokeny sesji,
- prywatność konwersacji.

---

## 4. Szacowanie skali

Załóżmy średnio-duży system.

### Przykładowe parametry

- 10 mln zarejestrowanych użytkowników,
- 1 mln DAU,
- 100 tys. użytkowników jednocześnie online,
- średnio 40 wiadomości dziennie na aktywnego użytkownika,
- 40 mln wiadomości dziennie,
- średni rozmiar wiadomości tekstowej: 1 KB po metadanych,
- załączniki przechowywane osobno w object storage.

### Ruch

```text
40 000 000 / 86 400 ≈ 463 wiadomości/s
```

W piku zakładamy 10x:

```text
~4 600 wiadomości/s
```

Dla systemu globalnego można projektować od razu na 10–50 tys. wiadomości/s.

### Storage wiadomości

Przy 40 mln wiadomości dziennie i około 1 KB na wiadomość:

```text
40 GB dziennie
~1.2 TB miesięcznie
~14.6 TB rocznie
```

Po indeksach, replikacji i metadanych realnie może to być 3–5x więcej.

---

## 5. Architektura wysokiego poziomu

```text
Client Apps
  ├── Web
  ├── iOS
  └── Android
       │
       ▼
API Gateway / Load Balancer
       │
       ├── Auth Service
       ├── User Service
       ├── Conversation Service
       ├── Message Service
       ├── WebSocket Gateway
       ├── Presence Service
       ├── Notification Service
       ├── Attachment Service
       ├── Search Service
       └── Moderation Service
              │
              ▼
      Event Bus / Message Queue
              │
              ▼
Datastores
  ├── PostgreSQL / MySQL: users, conversations, memberships
  ├── Cassandra / DynamoDB / ScyllaDB: messages
  ├── Redis: presence, sessions, ephemeral state
  ├── Kafka / Pulsar: event streaming
  ├── S3 / GCS / Azure Blob: attachments
  ├── Elasticsearch / OpenSearch: search
  └── Data Warehouse: analytics, abuse detection
```

### Architektura logiczna

```text
                         ┌──────────────────┐
                         │   Client Apps     │
                         └────────┬─────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │      API Gateway / LB      │
                    └───────┬───────────┬───────┘
                            │           │
                  HTTP APIs │           │ WebSocket
                            │           │
             ┌──────────────▼───┐   ┌───▼────────────────┐
             │ Application APIs  │   │ WebSocket Gateway  │
             └───────┬──────────┘   └───┬────────────────┘
                     │                  │
       ┌─────────────┼──────────────────┼──────────────┐
       │             │                  │              │
┌──────▼──────┐ ┌────▼────────┐ ┌───────▼──────┐ ┌─────▼──────┐
│ Auth Service│ │ Conv Service│ │ Msg Service  │ │Presence Svc│
└─────────────┘ └────┬────────┘ └───────┬──────┘ └─────┬──────┘
                     │                  │              │
              ┌──────▼──────┐    ┌──────▼──────┐ ┌─────▼─────┐
              │ SQL DB      │    │ Message DB  │ │ Redis     │
              └─────────────┘    └──────┬──────┘ └───────────┘
                                         │
                                  ┌──────▼──────┐
                                  │ Kafka/NATS  │
                                  └───┬────┬────┘
                                      │    │
                         ┌────────────▼┐ ┌─▼────────────┐
                         │Notification │ │Search Indexer│
                         │Service      │ │              │
                         └─────────────┘ └─────┬────────┘
                                               │
                                        ┌──────▼──────┐
                                        │OpenSearch   │
                                        └─────────────┘
```

---

## 6. Główne komponenty

### 6.1 Client

Klient może być:

- aplikacją webową,
- aplikacją mobilną,
- desktop appką.

Odpowiada za:

- utrzymanie WebSocket connection,
- retry wysyłki wiadomości,
- lokalny cache wiadomości,
- optimistic UI,
- obsługę offline mode,
- synchronizację po reconnect,
- upload plików przez pre-signed URL.

Klient nie powinien być źródłem prawdy dla autoryzacji. Wszystkie uprawnienia muszą być sprawdzane po stronie backendu.

### 6.2 API Gateway

Odpowiada za:

- routing requestów,
- TLS termination,
- rate limiting,
- podstawową walidację,
- autoryzację tokenu,
- request tracing,
- ochronę przed nadużyciami.

Przykładowe technologie:

- NGINX,
- Envoy,
- Kong,
- AWS API Gateway,
- Cloudflare.

### 6.3 Auth Service

Odpowiada za:

- logowanie,
- rejestrację,
- refresh tokeny,
- JWT lub opaque session tokens,
- obsługę urządzeń,
- unieważnianie sesji,
- MFA, jeżeli potrzebne.

Rekomendacja:

- krótkotrwały access token,
- dłużej żyjący refresh token,
- osobna tabela/device registry dla urządzeń mobilnych.

### 6.4 User Service

Przechowuje dane użytkowników:

- id,
- username,
- display name,
- avatar,
- phone/email,
- ustawienia prywatności,
- lista zablokowanych użytkowników.

Dane użytkownika powinny być oddzielone od danych czatu, ponieważ mają inny profil odczytu/zapisu.

### 6.5 Conversation Service

Zarządza konwersacjami.

Odpowiada za:

- tworzenie rozmów 1:1,
- tworzenie grup,
- dodawanie/usuwanie członków,
- role i uprawnienia,
- listę konwersacji użytkownika,
- ustawienia konwersacji,
- unread count,
- ostatnią wiadomość w konwersacji.

Dane konwersacji zwykle trzymamy w relacyjnej bazie, ponieważ relacje i uprawnienia są ważne.

### 6.6 Message Service

Najważniejszy komponent systemu.

Odpowiada za:

- walidację wiadomości,
- sprawdzenie członkostwa w konwersacji,
- nadanie globalnego lub lokalnego ID wiadomości,
- trwały zapis wiadomości,
- publikację eventu `MessageCreated`,
- obsługę edycji/usuwania,
- paginację historii.

Message Service nie powinien bezpośrednio wysyłać pushy ani aktualizować wyszukiwarki. Powinien zapisywać wiadomość i emitować event. Reszta systemu reaguje asynchronicznie.

### 6.7 WebSocket Gateway

Odpowiada za połączenia real-time.

Funkcje:

- utrzymywanie WebSocketów,
- mapowanie `user_id -> active connections`,
- odbieranie eventów od klientów,
- wysyłanie eventów do klientów,
- heartbeat/ping-pong,
- reconnect handling,
- autoryzacja połączenia.

WebSocket Gateway powinien być stateless na tyle, na ile to możliwe, ale w praktyce trzyma lokalnie aktywne połączenia.

Do współdzielenia informacji między instancjami można użyć:

- Redis Pub/Sub,
- Kafka,
- NATS,
- dedicated routing layer.

### 6.8 Presence Service

Odpowiada za:

- online/offline,
- last seen,
- aktywne urządzenia,
- typing indicator.

Presence powinien być traktowany jako dane nietrwałe lub półtrwałe.

Rekomendowany storage:

- Redis z TTL.

Przykład:

```text
presence:user:123 = online
TTL = 30s
```

Klient co kilka sekund wysyła heartbeat. Jeżeli heartbeat nie zostanie odnowiony, użytkownik jest uznawany za offline.

### 6.9 Notification Service

Odpowiada za powiadomienia:

- push mobile,
- web push,
- email fallback,
- desktop notification.

Źródłem danych powinny być eventy, np.:

```text
MessageCreated
MessageMentioned
GroupInviteCreated
```

Notification Service sprawdza:

- czy odbiorca jest offline,
- czy ma wyciszoną konwersację,
- czy nadawca nie jest zablokowany,
- czy wiadomość nie została cofnięta,
- czy użytkownik ma aktywny device token.

### 6.10 Attachment Service

Załączniki powinny być uploadowane poza Message Service.

Typowy flow:

1. Klient prosi backend o upload URL.
2. Backend generuje pre-signed URL do S3/GCS.
3. Klient uploaduje plik bezpośrednio do object storage.
4. Klient wysyła wiadomość z `attachment_id`.
5. Message Service zapisuje wiadomość z referencją do załącznika.
6. Attachment Service skanuje plik antywirusem i generuje miniatury.

Nie należy uploadować dużych plików przez główne API czatu, bo to niepotrzebnie obciąża backend.

### 6.11 Search Service

Wyszukiwanie wiadomości powinno działać asynchronicznie.

Flow:

1. Message Service zapisuje wiadomość.
2. Publikuje `MessageCreated`.
3. Search Indexer odbiera event.
4. Indeksuje wiadomość w Elasticsearch/OpenSearch.

Wyszukiwarka może być opóźniona o kilka sekund. To akceptowalne.

---

## 7. Data model

### 7.1 Users

Relacyjna baza, np. PostgreSQL.

```sql
users (
  id UUID PRIMARY KEY,
  username VARCHAR UNIQUE NOT NULL,
  display_name VARCHAR,
  email VARCHAR UNIQUE,
  phone_number VARCHAR UNIQUE,
  avatar_url TEXT,
  status VARCHAR,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
)
```

### 7.2 Devices

```sql
user_devices (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  device_type VARCHAR NOT NULL,
  push_token TEXT,
  last_seen_at TIMESTAMP,
  created_at TIMESTAMP,
  revoked_at TIMESTAMP
)
```

### 7.3 Conversations

```sql
conversations (
  id UUID PRIMARY KEY,
  type VARCHAR NOT NULL, -- direct, group
  title VARCHAR,
  avatar_url TEXT,
  created_by UUID,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  last_message_id UUID,
  last_message_at TIMESTAMP
)
```

### 7.4 Conversation Members

```sql
conversation_members (
  conversation_id UUID NOT NULL,
  user_id UUID NOT NULL,
  role VARCHAR NOT NULL, -- owner, admin, member
  joined_at TIMESTAMP,
  left_at TIMESTAMP,
  muted_until TIMESTAMP,
  last_read_message_id UUID,
  last_read_at TIMESTAMP,
  PRIMARY KEY (conversation_id, user_id)
)
```

Dodatkowy indeks:

```sql
CREATE INDEX idx_conversation_members_user
ON conversation_members(user_id, joined_at DESC);
```

To pozwala szybko pobrać konwersacje użytkownika.

### 7.5 Messages

Dla dużej skali lepsza będzie Cassandra, ScyllaDB, DynamoDB albo partycjonowana relacyjna baza.

Model pod główny access pattern:

> Pobierz wiadomości z danej konwersacji, posortowane malejąco po czasie.

Przykład dla Cassandry/ScyllaDB:

```sql
messages_by_conversation (
  conversation_id UUID,
  bucket_date DATE,
  message_id TIMEUUID,
  sender_id UUID,
  message_type TEXT,
  body TEXT,
  attachments LIST<TEXT>,
  reply_to_message_id UUID,
  created_at TIMESTAMP,
  edited_at TIMESTAMP,
  deleted_at TIMESTAMP,
  PRIMARY KEY ((conversation_id, bucket_date), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

`bucket_date` chroni przed zbyt dużymi partycjami dla bardzo aktywnych konwersacji.

### 7.6 Message Status

Dla wiadomości 1:1 można trzymać status bezpośrednio przy wiadomości. Dla grup trzeba uważać, bo status per użytkownik może eksplodować rozmiarem.

```sql
message_receipts (
  message_id UUID,
  user_id UUID,
  conversation_id UUID,
  delivered_at TIMESTAMP,
  read_at TIMESTAMP,
  PRIMARY KEY (message_id, user_id)
)
```

Dla dużych grup lepiej przechowywać `last_read_message_id` per użytkownik per konwersacja, zamiast receipt dla każdej wiadomości.

### 7.7 Attachments

```sql
attachments (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  storage_key TEXT NOT NULL,
  file_name TEXT,
  mime_type TEXT,
  size_bytes BIGINT,
  checksum TEXT,
  scan_status VARCHAR, -- pending, clean, infected
  thumbnail_url TEXT,
  created_at TIMESTAMP
)
```

### 7.8 Blocked Users

```sql
blocked_users (
  blocker_id UUID NOT NULL,
  blocked_id UUID NOT NULL,
  created_at TIMESTAMP,
  PRIMARY KEY (blocker_id, blocked_id)
)
```

---

## 8. API Design

### 8.1 Auth

```http
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
```

### 8.2 Users

```http
GET /users/me
PATCH /users/me
GET /users/{userId}
POST /users/{userId}/block
DELETE /users/{userId}/block
```

### 8.3 Conversations

```http
POST /conversations
GET /conversations
GET /conversations/{conversationId}
PATCH /conversations/{conversationId}
DELETE /conversations/{conversationId}
```

Tworzenie rozmowy 1:1:

```json
{
  "type": "direct",
  "participant_ids": ["user_2"]
}
```

Tworzenie grupy:

```json
{
  "type": "group",
  "title": "Project Alpha",
  "participant_ids": ["user_2", "user_3", "user_4"]
}
```

### 8.4 Members

```http
POST /conversations/{conversationId}/members
DELETE /conversations/{conversationId}/members/{userId}
PATCH /conversations/{conversationId}/members/{userId}
```

### 8.5 Messages

```http
POST /conversations/{conversationId}/messages
GET /conversations/{conversationId}/messages?before={cursor}&limit=50
PATCH /conversations/{conversationId}/messages/{messageId}
DELETE /conversations/{conversationId}/messages/{messageId}
```

Wysłanie wiadomości:

```json
{
  "client_message_id": "client-generated-uuid",
  "type": "text",
  "body": "Hello!",
  "attachments": [],
  "reply_to_message_id": null
}
```

Odpowiedź:

```json
{
  "message_id": "msg_123",
  "client_message_id": "client-generated-uuid",
  "conversation_id": "conv_123",
  "sender_id": "user_1",
  "type": "text",
  "body": "Hello!",
  "created_at": "2026-06-05T12:00:00Z",
  "status": "sent"
}
```

`client_message_id` jest ważny, bo pozwala obsłużyć retry bez duplikowania wiadomości.

### 8.6 Read Receipts

```http
POST /conversations/{conversationId}/read
```

Payload:

```json
{
  "last_read_message_id": "msg_123"
}
```

### 8.7 Attachments

```http
POST /attachments/upload-url
GET /attachments/{attachmentId}
```

Payload:

```json
{
  "file_name": "image.png",
  "mime_type": "image/png",
  "size_bytes": 123456
}
```

Odpowiedź:

```json
{
  "attachment_id": "att_123",
  "upload_url": "https://storage.example.com/pre-signed-url",
  "expires_at": "2026-06-05T12:10:00Z"
}
```

---

## 9. WebSocket Protocol

Połączenie:

```text
wss://chat.example.com/ws
Authorization: Bearer <token>
```

### Eventy od klienta do serwera

#### Send message

```json
{
  "type": "message.send",
  "payload": {
    "client_message_id": "uuid",
    "conversation_id": "conv_123",
    "message_type": "text",
    "body": "Hello"
  }
}
```

#### Typing started

```json
{
  "type": "typing.start",
  "payload": {
    "conversation_id": "conv_123"
  }
}
```

#### Typing stopped

```json
{
  "type": "typing.stop",
  "payload": {
    "conversation_id": "conv_123"
  }
}
```

#### Mark read

```json
{
  "type": "message.read",
  "payload": {
    "conversation_id": "conv_123",
    "last_read_message_id": "msg_123"
  }
}
```

### Eventy od serwera do klienta

#### New message

```json
{
  "type": "message.created",
  "payload": {
    "message_id": "msg_123",
    "conversation_id": "conv_123",
    "sender_id": "user_1",
    "body": "Hello",
    "created_at": "2026-06-05T12:00:00Z"
  }
}
```

#### Message delivered

```json
{
  "type": "message.delivered",
  "payload": {
    "message_id": "msg_123",
    "user_id": "user_2",
    "delivered_at": "2026-06-05T12:00:01Z"
  }
}
```

#### Message read

```json
{
  "type": "message.read",
  "payload": {
    "conversation_id": "conv_123",
    "user_id": "user_2",
    "last_read_message_id": "msg_123",
    "read_at": "2026-06-05T12:00:03Z"
  }
}
```

#### Presence update

```json
{
  "type": "presence.updated",
  "payload": {
    "user_id": "user_2",
    "status": "online",
    "last_seen_at": null
  }
}
```

---

## 10. Przepływ wysyłania wiadomości

### 10.1 Użytkownik online

```text
1. Client A wysyła message.send przez WebSocket.
2. WebSocket Gateway waliduje token.
3. Gateway przekazuje żądanie do Message Service.
4. Message Service:
   - sprawdza członkostwo w konwersacji,
   - sprawdza blokady,
   - waliduje payload,
   - zapisuje wiadomość w Message DB,
   - zapisuje event do outboxa lub publikuje do Kafka.
5. Message Service zwraca ACK do Client A.
6. Event MessageCreated trafia do Message Routera.
7. Router sprawdza aktywne połączenia odbiorców.
8. WebSocket Gateway wysyła message.created do Client B.
9. Client B odsyła delivered/read.
10. Receipts Service aktualizuje status.
```

### 10.2 Użytkownik offline

```text
1. Client A wysyła wiadomość.
2. Message Service zapisuje wiadomość.
3. Event MessageCreated trafia do Kafka.
4. Notification Service sprawdza, że User B jest offline.
5. Notification Service sprawdza ustawienia powiadomień.
6. Wysyła push przez APNs/FCM/Web Push.
7. User B otwiera aplikację.
8. Client B pobiera brakujące wiadomości przez REST lub sync API.
```

---

## 11. Idempotencja i retry

To krytyczne w czacie.

Klient może wysłać tę samą wiadomość kilka razy, np. przez słaby internet. Backend musi uniknąć duplikatów.

Rozwiązanie:

- klient generuje `client_message_id`,
- backend zapisuje mapowanie:

```sql
message_deduplication (
  sender_id UUID,
  client_message_id UUID,
  message_id UUID,
  created_at TIMESTAMP,
  PRIMARY KEY (sender_id, client_message_id)
)
```

Przy ponownej próbie backend zwraca istniejącą wiadomość.

---

## 12. Ordering wiadomości

W czacie kolejność jest trudniejsza, niż wygląda.

### Opcja A: timestamp serwera

Prosta, ale może powodować konflikty przy równoległych zapisach.

### Opcja B: sekwencja per konwersacja

Każda wiadomość dostaje `conversation_sequence_number`.

```text
conv_123:
  msg A -> seq 1
  msg B -> seq 2
  msg C -> seq 3
```

Zaleta:

- jednoznaczna kolejność.

Wada:

- wymaga mechanizmu generowania sekwencji,
- gorące konwersacje mogą tworzyć bottleneck.

### Opcja C: Snowflake ID / TimeUUID

Dobre praktyczne rozwiązanie.

Message ID zawiera czas i jest prawie monotoniczne.

Rekomendacja:

- dla MVP: server timestamp + UUID,
- dla większej skali: Snowflake ID lub TimeUUID,
- dla bardzo dużych grup: sequence per conversation z partycjonowaniem.

---

## 13. Gwarancje dostarczenia

Realistyczny system czatu zwykle oferuje:

- trwały zapis wiadomości,
- best-effort real-time delivery,
- synchronizację po reconnect,
- brak gwarancji exactly-once na poziomie sieci,
- idempotencję po stronie backendu i klienta.

Czyli praktycznie:

```text
At-least-once delivery + deduplication
```

Nie należy obiecywać czystego exactly-once, bo przy systemach rozproszonych jest to kosztowne i często złudne.

---

## 14. Synchronizacja po reconnect

Klient powinien przechowywać:

```text
last_seen_event_id
last_received_message_id per conversation
```

Po reconnect:

```http
GET /sync?since_event_id=evt_123
```

Odpowiedź:

```json
{
  "events": [
    {
      "event_id": "evt_124",
      "type": "message.created",
      "payload": {}
    },
    {
      "event_id": "evt_125",
      "type": "message.read",
      "payload": {}
    }
  ],
  "next_cursor": "evt_125"
}
```

Jeżeli klient był offline zbyt długo, backend może odpowiedzieć:

```json
{
  "sync_required": "full",
  "reason": "cursor_expired"
}
```

Wtedy klient pobiera listę konwersacji i najnowsze wiadomości ponownie.

---

## 15. Conversation list i unread count

### Conversation list

Lista konwersacji użytkownika powinna być szybka.

Widok:

```json
{
  "conversation_id": "conv_123",
  "title": "Anna",
  "avatar_url": "...",
  "last_message": {
    "body": "See you tomorrow",
    "created_at": "2026-06-05T12:00:00Z",
    "sender_id": "user_2"
  },
  "unread_count": 3,
  "muted": false
}
```

Można utrzymywać denormalizowany widok:

```sql
user_conversation_inbox (
  user_id UUID,
  conversation_id UUID,
  last_message_id UUID,
  last_message_preview TEXT,
  last_message_at TIMESTAMP,
  unread_count INT,
  pinned BOOLEAN,
  archived BOOLEAN,
  muted_until TIMESTAMP,
  PRIMARY KEY (user_id, conversation_id)
)
```

Aktualizowany asynchronicznie po `MessageCreated`.

Dla systemów o większej skali można trzymać inbox w Cassandra/DynamoDB z partition key `user_id`.

### Unread count

Unread count może być liczony na kilka sposobów.

#### Opcja A: dynamicznie

Liczysz wiadomości po `last_read_message_id`.

Plus:

- brak ryzyka niespójności.

Minus:

- kosztowne dla dużej liczby konwersacji.

#### Opcja B: denormalizowany licznik

Trzymasz `unread_count` w `user_conversation_inbox`.

Plus:

- bardzo szybki odczyt.

Minus:

- trzeba obsługiwać korekty i race conditions.

Rekomendacja:

- `last_read_message_id` jako źródło prawdy,
- `unread_count` jako denormalizowany cache.

---

## 16. Presence i typing indicator

Presence nie powinien być zapisywany przy każdym heartbeat do głównej bazy.

### Presence

Redis:

```text
SET presence:user_123 online EX 30
```

Heartbeat co 10 sekund.

Jeżeli TTL wygaśnie, użytkownik jest offline.

### Typing indicator

Typing indicator też powinien mieć TTL.

```text
SET typing:conv_123:user_123 true EX 5
```

Nie zapisujemy typing events do trwałej bazy.

---

## 17. Powiadomienia push

Notification Service powinien słuchać eventów.

```text
Kafka topic: message.created
```

Proces:

1. Pobierz odbiorców konwersacji.
2. Usuń nadawcę.
3. Sprawdź, kto jest online.
4. Sprawdź ustawienia mute.
5. Sprawdź blocklistę.
6. Wyślij push do offline devices.
7. Zapisz wynik próby wysłania.

Ważne: push notification nie jest gwarantowanym kanałem dostarczenia. Użytkownik i tak musi pobrać wiadomości z backendu po otwarciu aplikacji.

---

## 18. Załączniki

### Storage

Pliki powinny iść do object storage:

- AWS S3,
- Google Cloud Storage,
- Azure Blob Storage,
- MinIO dla self-hosted.

### Flow

```text
Client -> Backend: request upload URL
Backend -> Client: pre-signed URL
Client -> Object Storage: upload file
Client -> Backend: send message with attachment_id
Backend -> Message DB: save message
```

### Bezpieczeństwo plików

Należy dodać:

- limit rozmiaru pliku,
- MIME type validation,
- skan antywirusowy,
- generowanie miniaturek,
- blokowanie podejrzanych plików,
- krótkotrwałe signed download URLs,
- kontrolę dostępu przy pobieraniu.

---

## 19. Wyszukiwanie wiadomości

Nie należy szukać bezpośrednio w głównej bazie wiadomości.

Lepszy model:

```text
Message DB -> Event -> Search Indexer -> OpenSearch
```

Indeks:

```json
{
  "message_id": "msg_123",
  "conversation_id": "conv_123",
  "sender_id": "user_1",
  "body": "hello world",
  "created_at": "2026-06-05T12:00:00Z"
}
```

Przy wyszukiwaniu trzeba sprawdzić, czy użytkownik ma dostęp do danej konwersacji. Nie wolno ufać samemu indeksowi.

---

## 20. Bazy danych

### Dla MVP

Najprostszy rozsądny stack:

```text
PostgreSQL      — users, conversations, memberships, messages
Redis           — sessions, presence, typing
S3-compatible   — attachments
Kafka/RabbitMQ  — async events
OpenSearch      — search
```

Dla MVP wiadomości mogą być w PostgreSQL, o ile dobrze zaprojektujemy indeksy i partycjonowanie.

### Dla większej skali

```text
PostgreSQL      — users, memberships, permissions
ScyllaDB/Cassandra/DynamoDB — messages
Redis Cluster   — presence, ephemeral data
Kafka/Pulsar    — event streaming
S3/GCS          — attachments
OpenSearch      — search
ClickHouse/BigQuery — analytics
```

---

## 21. Partycjonowanie wiadomości

Główny problem: duże konwersacje.

Access pattern:

```text
get latest messages for conversation X
get messages before cursor Y
```

Dobra partycja:

```text
partition_key = conversation_id + time_bucket
sort_key = message_id/timeuuid
```

Przykład:

```text
messages_by_conversation:
  PK: conversation_id#2026-06
  SK: created_at#message_id
```

Dla bardzo aktywnych grup można dodać bucketing:

```text
PK: conversation_id#bucket_number
```

Ale to komplikuje odczyt. Nie należy robić tego przed realną potrzebą.

---

## 22. Cache

Redis może przechowywać:

- profile najczęściej używanych użytkowników,
- membership cache,
- presence,
- typing,
- rate limit counters,
- active WebSocket connection mapping,
- ostatnie wiadomości konwersacji,
- unread count cache.

Przykład membership cache:

```text
conversation_members:conv_123 = [user_1, user_2, user_3]
TTL = 5 min
```

Trzeba uważać przy zmianach członkostwa. Po dodaniu/usunięciu użytkownika cache musi zostać unieważniony.

---

## 23. Event-driven architecture

Najważniejsze eventy:

```text
MessageCreated
MessageEdited
MessageDeleted
MessageDelivered
MessageRead
ConversationCreated
ConversationMemberAdded
ConversationMemberRemoved
UserBlocked
AttachmentUploaded
AttachmentScanned
```

Eventy pozwalają oddzielić główną ścieżkę zapisu od zadań pobocznych.

### Przykład eventu

```json
{
  "event_id": "evt_123",
  "event_type": "MessageCreated",
  "occurred_at": "2026-06-05T12:00:00Z",
  "payload": {
    "message_id": "msg_123",
    "conversation_id": "conv_123",
    "sender_id": "user_1"
  }
}
```

---

## 24. Outbox pattern

To ważne, jeżeli chcemy uniknąć sytuacji:

> wiadomość zapisana w bazie, ale event nie został opublikowany.

Rozwiązanie:

W tej samej transakcji zapisujemy:

1. wiadomość,
2. rekord w tabeli `outbox_events`.

```sql
outbox_events (
  id UUID PRIMARY KEY,
  event_type VARCHAR NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMP,
  published_at TIMESTAMP
)
```

Osobny worker publikuje eventy do Kafka i oznacza je jako opublikowane.

To daje dużo większą niezawodność.

---

## 25. Rate limiting i anti-spam

Przykładowe limity:

- max 20 wiadomości / 10 sekund / użytkownik,
- max 5 nowych konwersacji / minutę,
- max 100 zaproszeń do grup / godzinę,
- max 50 MB uploadu / minutę,
- ostrzejsze limity dla nowych kont.

Redis:

```text
rate:user_123:send_message
```

Do tego można dodać:

- reputację konta,
- wykrywanie masowego spamu,
- blokowanie linków,
- CAPTCHA przy podejrzanym zachowaniu.

---

## 26. Moderacja

W zależności od produktu:

- automatyczne wykrywanie spamu,
- raportowanie wiadomości,
- blokowanie użytkowników,
- usuwanie treści przez moderatora,
- shadow ban,
- ograniczanie nowych kont.

Tabela raportów:

```sql
message_reports (
  id UUID PRIMARY KEY,
  message_id UUID,
  reporter_id UUID,
  reason VARCHAR,
  details TEXT,
  created_at TIMESTAMP,
  status VARCHAR
)
```

---

## 27. Bezpieczeństwo

### Transport

- TLS wszędzie.
- HSTS dla web.
- Secure cookies, jeżeli używamy cookies.
- Tokeny z krótkim TTL.

### Autoryzacja

Każdy request do wiadomości musi sprawdzać:

```text
Czy użytkownik jest członkiem tej konwersacji?
Czy użytkownik nie został usunięty?
Czy konwersacja nie jest zablokowana?
Czy nadawca nie jest zablokowany przez odbiorcę?
```

### Dane

- Hasła tylko jako hash, np. Argon2id lub bcrypt.
- Szyfrowanie danych w spoczynku.
- Szyfrowanie object storage.
- Signed URLs dla plików.
- Audit log dla operacji administracyjnych.

### End-to-end encryption

Jeżeli system ma być jak Signal/WhatsApp, trzeba osobno zaprojektować E2EE.

Wtedy backend:

- nie widzi treści wiadomości,
- nie może łatwo robić search,
- nie może robić moderacji treści,
- przechowuje ciphertext,
- musi zarządzać kluczami urządzeń.

Dla Slack-like systemu zwykle stosuje się encryption in transit + at rest, ale nie pełne E2EE.

---

## 28. Multi-device support

Użytkownik może być zalogowany na kilku urządzeniach.

Model:

```text
user_id -> device_id -> websocket_connection_id
```

Wiadomość do użytkownika online trzeba wysłać do wszystkich aktywnych urządzeń.

Read receipt powinien być per użytkownik, niekoniecznie per urządzenie, chyba że produkt wymaga dokładniejszej semantyki.

---

## 29. Obsługa offline

Klient powinien:

- lokalnie zapisywać pending messages,
- pokazywać stan `sending`,
- retry po reconnect,
- używać `client_message_id`,
- po reconnect robić sync,
- deduplikować wiadomości po `message_id` i `client_message_id`.

Statusy lokalne:

```text
local_pending
sending
sent
delivered
read
failed
```

---

## 30. Edycja i usuwanie wiadomości

### Edycja

Nie należy nadpisywać wiadomości bez śladu, jeżeli produkt wymaga audytu.

Można przechowywać:

```sql
message_edits (
  id UUID PRIMARY KEY,
  message_id UUID,
  old_body TEXT,
  new_body TEXT,
  edited_by UUID,
  edited_at TIMESTAMP
)
```

### Usuwanie

#### Soft delete

```text
deleted_at != null
body = null albo body ukryte
```

Zalecane.

#### Hard delete

Fizyczne usunięcie, potrzebne np. dla wymagań prywatności, ale bardziej skomplikowane przy replikach, backupach i search indexie.

---

## 31. Fan-out strategy

Po `MessageCreated` system powinien zaktualizować inbox dla każdego członka rozmowy.

Dla małych grup można zrobić fan-out on write:

```text
jedna wiadomość -> aktualizacja inboxu każdego członka
```

Dla dużych grup lepiej:

```text
fan-out on read
```

### Fan-out on write

Plusy:

- szybka lista konwersacji,
- proste unread count.

Minusy:

- kosztowne dla dużych grup.

### Fan-out on read

Plusy:

- tanie zapisy dla dużych grup.

Minusy:

- droższy odczyt,
- bardziej złożona logika.

Rekomendacja:

- rozmowy 1:1 i małe grupy: fan-out on write,
- bardzo duże kanały: fan-out on read lub hybryda.

---

## 32. WebSocket scaling

Problem: użytkownik A jest połączony z instancją WS-1, a użytkownik B z WS-7.

Potrzebujemy routingu eventów.

### Opcja A: Redis Pub/Sub

Dobre dla MVP.

```text
Message Service -> Redis Pub/Sub -> wszystkie WS gateways -> właściwy gateway wysyła do klienta
```

Minus: przy dużej skali broadcast do wielu gatewayów może być nieefektywny.

### Opcja B: Kafka/NATS per shard

Lepsze dla większej skali.

```text
user_id hash -> shard
gateway subskrybuje shard swoich użytkowników
```

### Opcja C: Connection Registry

Redis przechowuje:

```text
user_connection:user_123 -> gateway_7, connection_abc
```

Message Router wie, do którego gatewaya wysłać event.

Rekomendacja:

- MVP: Redis Pub/Sub,
- produkcja większej skali: NATS/Kafka + connection registry.

---

## 33. Consistency model

### Wiadomości

Wiadomość jest uznana za wysłaną dopiero po trwałym zapisie w bazie.

ACK do nadawcy powinien oznaczać:

```text
Serwer zapisał wiadomość.
```

Nie powinien oznaczać:

```text
Odbiorca ją przeczytał.
```

### Dostarczenie

`delivered` oznacza, że wiadomość dotarła na urządzenie odbiorcy lub została pobrana przez klienta.

### Odczyt

`read` oznacza, że klient odbiorcy zgłosił przeczytanie.

Nie należy traktować delivered/read jako krytycznie spójnych danych. Mogą przyjść z opóźnieniem.

---

## 34. Disaster recovery

Wymagane:

- backup relacyjnej bazy,
- replikacja Message DB,
- versioning lub lifecycle policy dla object storage,
- odtwarzalny Kafka topic albo outbox,
- snapshoty OpenSearch nie jako jedyne źródło prawdy,
- runbooki awaryjne.

RPO/RTO dla sensownego systemu:

```text
RPO: 5–15 minut
RTO: 30–60 minut
```

Dla krytycznego systemu można zejść niżej, ale rośnie koszt.

---

## 35. Observability

Należy mierzyć:

### Metryki biznesowe

- wiadomości/s,
- aktywni użytkownicy,
- liczba WebSocket connections,
- push delivery rate,
- liczba błędów wysyłki,
- liczba raportów nadużyć.

### Metryki techniczne

- latency send message,
- latency delivery,
- DB write latency,
- queue lag,
- WebSocket disconnect rate,
- Redis latency,
- Kafka consumer lag,
- error rate per endpoint,
- p95/p99 response time.

### Logi

Każdy request/event powinien mieć:

```text
request_id
user_id
conversation_id
message_id
trace_id
```

Nie należy logować pełnej treści wiadomości w logach aplikacyjnych, chyba że mamy bardzo wyraźną podstawę i politykę retencji.

### Alerty

Przykładowe alerty:

- wzrost p99 wysyłki wiadomości powyżej 1 sekundy,
- Kafka lag powyżej ustalonego limitu,
- spadek skuteczności pushy,
- Redis memory powyżej 80%,
- WebSocket disconnect spike,
- DB write errors,
- wzrost 5xx,
- brak publikacji eventów z outboxa,
- duża liczba failed attachment scans.

---

## 36. Deployment

### Propozycja infrastruktury

```text
Kubernetes
  ├── api-gateway
  ├── auth-service
  ├── user-service
  ├── conversation-service
  ├── message-service
  ├── websocket-gateway
  ├── presence-service
  ├── notification-service
  ├── attachment-service
  ├── search-indexer
  └── background-workers
```

### Skalowanie

Horizontal Pod Autoscaler na podstawie:

- CPU,
- memory,
- liczby WebSocket connections,
- Kafka lag,
- request rate.

WebSocket Gateway powinien mieć osobne reguły skalowania, bo jego głównym ograniczeniem nie zawsze jest CPU, ale liczba aktywnych połączeń.

---

## 37. CI/CD

Pipeline:

```text
1. lint
2. unit tests
3. integration tests
4. contract tests
5. security scan
6. build Docker image
7. push image
8. deploy to staging
9. smoke tests
10. canary deploy to production
11. full rollout
```

Dla Message Service i Conversation Service warto mieć testy kontraktowe, bo wiele komponentów zależy od eventów.

---

## 38. Testowanie

### Unit tests

- walidacja wiadomości,
- uprawnienia,
- blokady,
- deduplikacja,
- unread count.

### Integration tests

- wysłanie wiadomości end-to-end,
- reconnect i sync,
- upload załącznika,
- read receipts,
- push notification flow.

### Load tests

Scenariusze:

- 100k równoczesnych WebSocketów,
- 5k wiadomości/s,
- duże grupy,
- reconnect storm,
- masowe push notifications,
- wolny consumer Kafka.

### Chaos tests

- restart WebSocket Gateway,
- niedostępny Redis,
- opóźniona Kafka,
- padnięty Search Service,
- spowolniona Message DB.

---

## 39. Edge cases

### Duplikaty wiadomości

Rozwiązanie: `client_message_id`.

### Wiadomość wysłana do konwersacji po usunięciu użytkownika

Backend sprawdza membership w momencie zapisu.

### Użytkownik blokuje drugiego użytkownika

Nowe wiadomości powinny być odrzucane lub ukrywane zgodnie z polityką produktu.

### Odbiorca offline przez miesiąc

Cursor sync może wygasnąć. Klient robi full sync.

### Wiadomość z załącznikiem, który nie przeszedł skanu

Wiadomość może być ukryta, a załącznik oznaczony jako blocked.

### Duża grupa z 100k członków

Nie aktualizować synchronicznie inboxu każdego członka w request path. Użyć async fan-out albo fan-out on read.

### Reconnect storm po awarii

Potrzebne:

- exponential backoff po stronie klienta,
- rate limiting reconnectów,
- rozproszenie reconnect delay.

---

## 40. Rekomendowany MVP

Dla pierwszej wersji nie warto robić zbyt rozproszonej architektury. Lepiej zacząć prościej.

### MVP stack

```text
Backend: Node.js/NestJS, Go albo Java/Kotlin
DB: PostgreSQL
Cache: Redis
Queue: RabbitMQ albo Kafka
Realtime: WebSocket
Storage: S3
Search: OpenSearch opcjonalnie później
Deployment: Kubernetes albo prostszy container hosting
```

### MVP moduły

```text
Auth
Users
Conversations
Messages
WebSocket
Presence
Notifications
Attachments
```

### MVP baza

PostgreSQL wystarczy, jeżeli:

- wiadomości partycjonujemy po czasie,
- mamy dobre indeksy,
- nie próbujemy obsłużyć od razu setek milionów użytkowników,
- używamy Redis dla presence,
- załączniki trzymamy w S3.

---

## 41. Docelowa wersja skalowalna

Gdy PostgreSQL zacznie być ograniczeniem dla wiadomości:

```text
Messages -> ScyllaDB/Cassandra/DynamoDB
Events -> Kafka
Inbox -> DynamoDB/Cassandra albo Redis + persistent store
Search -> OpenSearch
Analytics -> ClickHouse/BigQuery
```

Relacyjna baza zostaje dla:

- users,
- conversations,
- memberships,
- billing, jeżeli istnieje,
- ustawień,
- permissions.

---

## 42. Kompromisy projektowe

### PostgreSQL vs Cassandra dla wiadomości

#### PostgreSQL

Dobry na start. Łatwiejszy, transakcyjny, szybki development.

Problem: przy ogromnej skali wiadomości trzeba mocno partycjonować i pilnować indeksów.

#### Cassandra/ScyllaDB

Lepsza do dużych wolumenów append-only.

Problem: trudniejszy model danych, słabsza elastyczność zapytań.

Rekomendacja:

- zacząć od PostgreSQL,
- zaprojektować interfejs `MessageRepository` tak, żeby późniejsza migracja była możliwa.

### REST vs WebSocket

REST do:

- historii,
- listy konwersacji,
- ustawień,
- uploadu,
- synchronizacji.

WebSocket do:

- nowych wiadomości,
- typing,
- presence,
- read receipts,
- real-time events.

### Exactly-once vs at-least-once

Exactly-once jest złudnie atrakcyjne, ale drogie.

Lepsze:

```text
At-least-once + idempotencja + deduplikacja
```

### Fan-out on write vs fan-out on read

Dla małych grup fan-out on write jest wygodniejszy.

Dla dużych kanałów fan-out on read jest tańszy.

Najlepszy jest model hybrydowy.

---

## 43. Bounded contexts

Jeżeli projekt jest robiony w DDD lub mikroserwisach:

```text
Identity Context
  - users
  - sessions
  - devices

Conversation Context
  - conversations
  - members
  - roles

Messaging Context
  - messages
  - receipts
  - reactions

Realtime Context
  - websocket connections
  - event routing

Presence Context
  - online/offline
  - typing

Notification Context
  - push
  - email
  - user notification preferences

Media Context
  - attachments
  - thumbnails
  - virus scanning

Search Context
  - indexing
  - querying
```

Na start można to wdrożyć jako modularny monolit. Mikroserwisy mają sens dopiero wtedy, gdy zespół i skala uzasadniają koszt operacyjny.

---

## 44. Kolejność implementacji

1. Auth + users.
2. Conversations 1:1.
3. Message send przez REST.
4. Message history.
5. WebSocket delivery.
6. Conversation list.
7. Redis presence.
8. Read receipts.
9. Push notifications.
10. Attachments.
11. Group chat.
12. Search.
13. Moderation.
14. Outbox pattern.
15. Skalowanie WebSocket Gateway.
16. Migracja wiadomości do storage zoptymalizowanego pod dużą skalę, jeśli będzie potrzeba.

---

## 45. Największe ryzyka techniczne

1. **Niedopracowana idempotencja** — duplikaty wiadomości będą bardzo widoczne dla użytkowników.
2. **Brak dobrego sync po reconnect** — użytkownicy będą gubić wiadomości.
3. **Źle zaprojektowane partycjonowanie wiadomości** — baza zacznie boleć przy wzroście.
4. **Presence trzymany w głównej bazie** — niepotrzebne obciążenie.
5. **Zbyt wczesne mikroserwisy** — duży koszt developmentu i DevOps.
6. **Brak outbox pattern** — niespójności między bazą a eventami.
7. **Brak rate limiting** — spam i abuse szybko zniszczą jakość systemu.
8. **Załączniki przez backend API** — łatwo przeciążyć aplikację.
9. **Brak observability** — problemy z real-time są trudne do debugowania.
10. **Zbyt optymistyczne exactly-once assumptions** — realny świat sieci tego nie lubi.

---

## 46. Podsumowanie

Najrozsądniejszy projekt:

```text
Clients
  -> API Gateway
  -> Modular Backend / Services
  -> PostgreSQL for users/conversations
  -> Message Store for messages
  -> Redis for presence/cache/rate limits
  -> Kafka/RabbitMQ for async events
  -> WebSocket Gateway for realtime
  -> S3/GCS for attachments
  -> OpenSearch for search
  -> Push providers for notifications
```

Najważniejsze decyzje:

- wiadomość uznajemy za wysłaną dopiero po trwałym zapisie,
- WebSocket służy do real-time, REST do historii i synchronizacji,
- idempotencja przez `client_message_id`,
- presence i typing tylko w Redis z TTL,
- załączniki przez pre-signed URL,
- event-driven flow dla powiadomień, search i inbox updates,
- outbox pattern dla niezawodności,
- hybrydowy fan-out dla małych i dużych konwersacji,
- modularny monolit na start, mikroserwisy dopiero po uzasadnieniu skalą.

To jest architektura, którą można bezpiecznie zacząć jako MVP, a potem rozwinąć do systemu obsługującego bardzo duży ruch.
