# Notification System — System Design

## 1. Cel systemu

System Notification ma umożliwiać niezawodne wysyłanie powiadomień do użytkowników przez wiele kanałów, np.:

- email,
- SMS,
- push mobile/web,
- in-app notifications,
- ewentualnie Slack/WhatsApp/webhook w przyszłości.

System powinien obsługiwać zarówno powiadomienia **transakcyjne**, jak i **marketingowe / produktowe**, przy czym te pierwsze mają wyższy priorytet.

Przykłady:

- reset hasła,
- potwierdzenie płatności,
- alert bezpieczeństwa,
- przypomnienie o wydarzeniu,
- powiadomienie o nowej wiadomości,
- newsletter,
- kampania promocyjna,
- alert systemowy.

---

## 2. Wymagania funkcjonalne

### 2.1. Tworzenie powiadomień

System powinien pozwalać innym serwisom na tworzenie zleceń powiadomień przez API lub eventy.

Przykład:

```json
{
  "user_id": "user_123",
  "type": "PASSWORD_RESET",
  "channels": ["email"],
  "priority": "high",
  "template_id": "password_reset_v2",
  "payload": {
    "reset_link": "https://example.com/reset/abc"
  }
}
```

Źródłami powiadomień mogą być:

- User Service,
- Payment Service,
- Order Service,
- Messaging Service,
- Marketing Platform,
- Admin Panel,
- Scheduler.

---

### 2.2. Obsługa wielu kanałów

System powinien wspierać różne kanały dostarczenia:

| Kanał | Przykład providera |
|---|---|
| Email | SendGrid, SES, Mailgun |
| SMS | Twilio, Vonage |
| Push | Firebase Cloud Messaging, APNs |
| In-app | własna baza + WebSocket/SSE |
| Webhook | HTTP callback do zewnętrznego systemu |

Każdy kanał powinien mieć osobny adapter, żeby nie wiązać logiki biznesowej z konkretnym dostawcą.

---

### 2.3. Szablony wiadomości

System powinien obsługiwać szablony wiadomości.

Przykład szablonu email:

```html
Cześć {{first_name}},

Kliknij tutaj, aby zresetować hasło:
{{reset_link}}
```

Szablony powinny wspierać:

- wersjonowanie,
- różne języki,
- różne kanały,
- podgląd przed wysyłką,
- walidację wymaganych zmiennych,
- fallback językowy, np. `pl-PL -> pl -> en`.

Przykładowa struktura:

```text
template_id: password_reset
version: 3
locale: pl-PL
channel: email
subject: Reset hasła
body: ...
```

---

### 2.4. Preferencje użytkownika

Użytkownik powinien móc zdecydować, jakie typy powiadomień chce otrzymywać.

| Typ powiadomienia | Email | SMS | Push | In-app |
|---|---:|---:|---:|---:|
| Security alerts | tak | tak | tak | tak |
| Marketing | nie | nie | tak | tak |
| Payment updates | tak | tak | nie | tak |
| Weekly summary | tak | nie | nie | tak |

Ważne: nie wszystkie powiadomienia powinny dać się wyłączyć. Przykładowo reset hasła, alert bezpieczeństwa albo informacja prawna mogą być obowiązkowe.

---

### 2.5. Priorytety

System powinien wspierać priorytety:

| Priorytet | Przykład | SLA |
|---|---|---|
| Critical | OTP, reset hasła, alert bezpieczeństwa | sekundy |
| High | płatność, zamówienie | < 1 min |
| Normal | komentarz, wiadomość | kilka minut |
| Low | newsletter, digest | best effort |

Priorytet wpływa na:

- kolejkę,
- liczbę retry,
- timeout,
- provider fallback,
- rate limit,
- monitoring.

---

### 2.6. Harmonogramowanie

System powinien wspierać:

- wysyłkę natychmiastową,
- wysyłkę o konkretnej godzinie,
- cykliczne kampanie,
- digesty dzienne/tygodniowe,
- powiadomienia z opóźnieniem.

Przykład:

```json
{
  "send_at": "2026-06-05T18:00:00Z"
}
```

---

### 2.7. Deduplicacja

System powinien zapobiegać przypadkowemu wysłaniu tego samego powiadomienia wiele razy.

Mechanizmy:

- `idempotency_key`,
- hash z `user_id + notification_type + entity_id`,
- deduplication window, np. 5 minut,
- unikalny constraint w bazie.

Przykład:

```text
idempotency_key = payment_failed:user_123:invoice_456
```

---

### 2.8. Statusy powiadomień

Każde powiadomienie powinno mieć cykl życia:

```text
CREATED
VALIDATED
QUEUED
PROCESSING
SENT
DELIVERED
FAILED
RETRYING
CANCELLED
EXPIRED
```

W praktyce trzeba rozróżnić:

- `SENT` — provider przyjął wiadomość,
- `DELIVERED` — wiadomość faktycznie dotarła,
- `OPENED` — użytkownik otworzył email/push,
- `CLICKED` — użytkownik kliknął link,
- `BOUNCED` — email wrócił,
- `UNSUBSCRIBED` — użytkownik się wypisał.

---

## 3. Wymagania niefunkcjonalne

### 3.1. Skalowalność

System powinien obsługiwać zarówno mały ruch, jak i duże kampanie.

Przykładowe założenia:

```text
10 mln użytkowników
1 mln aktywnych dziennie
średnio 5 powiadomień / użytkownika / dzień
peak: 50 000 notification requests / minutę
```

Nie projektowałbym tego jako jednego synchronicznego serwisu, bo wysyłka przez zewnętrznych providerów jest wolna, zawodna i ma limity.

---

### 3.2. Dostępność

Cel:

```text
API availability: 99.9%+
Delivery pipeline availability: 99.5%+
```

System powinien przyjmować zlecenia nawet wtedy, gdy provider email/SMS ma awarię. Wtedy powiadomienia trafiają do kolejki retry albo są kierowane do alternatywnego providera.

---

### 3.3. Niezawodność

Ważniejsze jest, żeby powiadomienie nie zginęło, niż żeby zawsze zostało wysłane natychmiast.

Mechanizmy:

- durable queue,
- retry z backoffem,
- dead-letter queue,
- idempotency,
- audyt statusów,
- outbox pattern,
- provider fallback.

---

### 3.4. Latencja

Dla powiadomień krytycznych:

```text
p95 enqueue latency < 100 ms
p95 processing start < 1 s
p95 provider accepted < 5 s
```

Dla newsletterów czy kampanii marketingowych latencja może być znacznie większa.

---

### 3.5. Spójność

System może być **eventually consistent**.

Nie ma potrzeby, żeby status `DELIVERED` był natychmiast znany każdemu serwisowi. Wystarczy, że:

- zlecenie zostało trwale zapisane,
- system podejmie próbę wysłania,
- status zostanie później zaktualizowany.

---

## 4. High-Level Architecture

```text
                        ┌────────────────────┐
                        │  Client Services   │
                        │ User/Order/Payment │
                        └─────────┬──────────┘
                                  │
                                  ▼
                        ┌────────────────────┐
                        │ Notification API   │
                        └─────────┬──────────┘
                                  │
                                  ▼
                        ┌────────────────────┐
                        │ Notification DB    │
                        └─────────┬──────────┘
                                  │
                                  ▼
                        ┌────────────────────┐
                        │ Message Broker     │
                        │ Kafka/SQS/RabbitMQ │
                        └──────┬──────┬──────┘
                               │      │
              ┌────────────────┘      └────────────────┐
              ▼                                        ▼
     ┌──────────────────┐                    ┌──────────────────┐
     │ Email Worker     │                    │ SMS Worker       │
     └────────┬─────────┘                    └────────┬─────────┘
              │                                       │
              ▼                                       ▼
     ┌──────────────────┐                    ┌──────────────────┐
     │ Email Provider   │                    │ SMS Provider     │
     └──────────────────┘                    └──────────────────┘

              ┌──────────────────┐
              │ Push Worker      │
              └────────┬─────────┘
                       ▼
              ┌──────────────────┐
              │ FCM / APNs       │
              └──────────────────┘

              ┌──────────────────┐
              │ In-App Service   │
              └──────────────────┘
```

---

## 5. Główne komponenty

### 5.1. Notification API

Odpowiada za przyjmowanie zleceń.

Zadania:

- autoryzacja serwisów,
- walidacja requestu,
- sprawdzenie idempotency key,
- zapis notification requestu,
- publikacja eventu do brokera,
- zwrócenie `notification_id`.

API nie powinno synchronicznie wysyłać wiadomości do providera.

Przykładowa odpowiedź:

```json
{
  "notification_id": "notif_789",
  "status": "QUEUED"
}
```

---

### 5.2. Notification Orchestrator

To centralny komponent decydujący, co właściwie należy wysłać.

Zadania:

- pobranie preferencji użytkownika,
- wybór kanałów,
- sprawdzenie reguł biznesowych,
- dobranie template’u,
- wykonanie personalizacji,
- rozbicie jednego requestu na wiele channel-specific jobs.

Przykład:

```text
Notification Request:
  user_id = 123
  type = PAYMENT_FAILED
  channels = auto

Orchestrator:
  sprawdza preferencje
  email = allowed
  sms = allowed for high priority
  push = disabled

Tworzy:
  email_job
  sms_job
```

---

### 5.3. Template Service

Odpowiada za szablony.

Funkcje:

- CRUD szablonów,
- wersjonowanie,
- rendering,
- walidacja zmiennych,
- fallback językowy,
- obsługa A/B testów,
- preview.

Szablony powinny być cache’owane, bo będą często używane.

Rekomendacja:

- baza: PostgreSQL,
- cache: Redis,
- rendering: Handlebars, Mustache, Liquid albo własny ograniczony silnik.

Nie pozwalałbym na wykonywanie dowolnego kodu w szablonach. To ryzyko bezpieczeństwa.

---

### 5.4. Preference Service

Przechowuje ustawienia użytkownika.

Przykładowy model:

```json
{
  "user_id": "user_123",
  "preferences": {
    "marketing": {
      "email": false,
      "sms": false,
      "push": true
    },
    "security": {
      "email": true,
      "sms": true,
      "push": true,
      "mandatory": true
    }
  }
}
```

Powinien obsługiwać:

- global opt-out,
- opt-out per kanał,
- opt-out per kategoria,
- wymogi prawne,
- unsubscribe links,
- double opt-in dla marketingu,
- historia zmian preferencji.

---

### 5.5. Queue / Message Broker

Broker jest krytyczny.

Możliwe opcje:

| Technologia | Dobre zastosowanie |
|---|---|
| Kafka | duża skala, event streaming, audyt |
| SQS | prostota, managed queue, AWS |
| RabbitMQ | routing, mniejsze/średnie systemy |
| Google Pub/Sub | GCP |
| Azure Service Bus | Azure |

Dla dużego systemu wybrałbym:

```text
Kafka albo SQS + DLQ
```

Przykładowe topiki/kolejki:

```text
notification.created
notification.email.high
notification.email.normal
notification.sms.high
notification.push.normal
notification.retry
notification.dlq
notification.status.updated
```

Oddzielne kolejki per kanał i priorytet ułatwiają skalowanie.

---

### 5.6. Workers

Workers wykonują właściwą wysyłkę.

Każdy kanał ma osobny worker:

- Email Worker,
- SMS Worker,
- Push Worker,
- In-App Worker,
- Webhook Worker.

Zadania workera:

1. pobiera job z kolejki,
2. sprawdza status i idempotency,
3. renderuje wiadomość albo pobiera wyrenderowaną,
4. wywołuje provider adapter,
5. zapisuje wynik,
6. w razie błędu planuje retry,
7. po permanentnym błędzie wysyła do DLQ.

---

### 5.7. Provider Adapter Layer

Nie należy wołać SendGrid, Twilio czy FCM bezpośrednio z logiki biznesowej.

Lepszy wzorzec:

```text
EmailWorker -> EmailProviderInterface -> SendGridAdapter
                                      -> SESAdapter
                                      -> MailgunAdapter
```

Interfejs:

```typescript
interface EmailProvider {
  sendEmail(message: EmailMessage): Promise<ProviderResponse>;
}
```

Dzięki temu można:

- zmienić providera,
- zrobić fallback,
- testować lokalnie,
- mierzyć jakość providerów,
- ograniczyć vendor lock-in.

---

### 5.8. Delivery Status Webhook Handler

Providerzy często wysyłają webhooks o statusie dostarczenia.

Przykład:

- email delivered,
- email bounced,
- SMS delivered,
- push token invalid,
- user unsubscribed,
- spam complaint.

Webhook Handler powinien:

- zweryfikować podpis providera,
- zmapować status providera na wewnętrzny status,
- zaktualizować bazę,
- opublikować event `notification.status.updated`.

---

## 6. Proponowany przepływ danych

### 6.1. Wysyłka natychmiastowa

```text
1. Payment Service wykrywa failed payment.
2. Payment Service wywołuje Notification API.
3. API waliduje request.
4. API zapisuje NotificationRequest w DB.
5. API publikuje notification.created.
6. Orchestrator pobiera event.
7. Orchestrator sprawdza preferencje i szablon.
8. Orchestrator tworzy email_job oraz push_job.
9. Jobs trafiają do kolejek kanałowych.
10. Email Worker wysyła email.
11. Push Worker wysyła push.
12. Statusy są zapisywane w DB.
13. Provider webhook aktualizuje status DELIVERED/BOUNCED.
```

---

### 6.2. Wysyłka zaplanowana

```text
1. Service tworzy notification z send_at.
2. Notification API zapisuje request jako SCHEDULED.
3. Scheduler cyklicznie szuka rekordów gotowych do wysyłki.
4. Scheduler publikuje notification.created.
5. Dalej przepływ jest taki sam jak dla wysyłki natychmiastowej.
```

Dla dużej skali scheduler nie powinien robić prostego `SELECT * WHERE send_at <= now()`, bo to może zabić bazę. Lepiej użyć indeksów, shardingu czasowego albo opóźnionych kolejek, jeśli technologia to wspiera.

---

### 6.3. In-app notification

In-app notification różni się od email/SMS, bo musi być przechowywane i widoczne w aplikacji.

Przepływ:

```text
1. InAppWorker zapisuje notification do tabeli user_notifications.
2. Jeśli użytkownik jest online, wysyła event przez WebSocket/SSE.
3. Frontend pokazuje badge/licznik.
4. Użytkownik może oznaczyć jako przeczytane.
```

---

## 7. API Design

### 7.1. Create Notification

```http
POST /v1/notifications
```

Request:

```json
{
  "user_id": "user_123",
  "type": "PAYMENT_FAILED",
  "priority": "high",
  "channels": ["email", "push"],
  "template_id": "payment_failed_v1",
  "locale": "pl-PL",
  "payload": {
    "amount": "99.00 PLN",
    "invoice_url": "https://example.com/invoices/456"
  },
  "idempotency_key": "payment_failed:user_123:invoice_456"
}
```

Response:

```json
{
  "notification_id": "notif_789",
  "status": "QUEUED"
}
```

---

### 7.2. Get Notification Status

```http
GET /v1/notifications/{notification_id}
```

Response:

```json
{
  "notification_id": "notif_789",
  "status": "SENT",
  "channels": [
    {
      "channel": "email",
      "status": "DELIVERED",
      "provider": "sendgrid",
      "sent_at": "2026-06-05T12:00:00Z",
      "delivered_at": "2026-06-05T12:00:03Z"
    },
    {
      "channel": "push",
      "status": "FAILED",
      "error_code": "INVALID_TOKEN"
    }
  ]
}
```

---

### 7.3. Cancel Scheduled Notification

```http
DELETE /v1/notifications/{notification_id}
```

Dozwolone tylko dla statusów:

```text
SCHEDULED
QUEUED
```

Nie powinno się anulować wiadomości, która już została wysłana do providera.

---

### 7.4. User Preferences

```http
GET /v1/users/{user_id}/notification-preferences
PUT /v1/users/{user_id}/notification-preferences
```

---

### 7.5. In-app Notifications

```http
GET /v1/users/{user_id}/notifications
PATCH /v1/users/{user_id}/notifications/{id}/read
PATCH /v1/users/{user_id}/notifications/read-all
```

---

## 8. Model danych

### 8.1. `notification_requests`

```sql
CREATE TABLE notification_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(100) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    template_id VARCHAR(100),
    locale VARCHAR(20),
    payload JSONB NOT NULL,
    idempotency_key VARCHAR(255),
    scheduled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    UNIQUE(idempotency_key)
);
```

Indeksy:

```sql
CREATE INDEX idx_notification_requests_user_id 
ON notification_requests(user_id);

CREATE INDEX idx_notification_requests_status_scheduled 
ON notification_requests(status, scheduled_at);

CREATE INDEX idx_notification_requests_created_at 
ON notification_requests(created_at);
```

---

### 8.2. `notification_jobs`

```sql
CREATE TABLE notification_jobs (
    id UUID PRIMARY KEY,
    notification_request_id UUID NOT NULL,
    user_id UUID NOT NULL,
    channel VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider VARCHAR(50),
    provider_message_id VARCHAR(255),
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

Indeksy:

```sql
CREATE INDEX idx_notification_jobs_status_retry 
ON notification_jobs(status, next_retry_at);

CREATE INDEX idx_notification_jobs_request_id 
ON notification_jobs(notification_request_id);

CREATE INDEX idx_notification_jobs_provider_message_id 
ON notification_jobs(provider_message_id);
```

---

### 8.3. `notification_templates`

```sql
CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    template_key VARCHAR(100) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    locale VARCHAR(20) NOT NULL,
    version INT NOT NULL,
    subject TEXT,
    body TEXT NOT NULL,
    required_variables JSONB,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    UNIQUE(template_key, channel, locale, version)
);
```

---

### 8.4. `notification_preferences`

```sql
CREATE TABLE notification_preferences (
    user_id UUID NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    PRIMARY KEY(user_id, notification_type, channel)
);
```

---

### 8.5. `in_app_notifications`

```sql
CREATE TABLE in_app_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    notification_request_id UUID,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    action_url TEXT,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL
);
```

Indeksy:

```sql
CREATE INDEX idx_in_app_user_created 
ON in_app_notifications(user_id, created_at DESC);

CREATE INDEX idx_in_app_user_unread 
ON in_app_notifications(user_id, read_at)
WHERE read_at IS NULL;
```

---

### 8.6. `notification_events`

Tabela audytowa.

```sql
CREATE TABLE notification_events (
    id UUID PRIMARY KEY,
    notification_job_id UUID,
    notification_request_id UUID,
    event_type VARCHAR(100) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL
);
```

Przykładowe eventy:

```text
REQUEST_CREATED
JOB_CREATED
JOB_SENT
JOB_DELIVERED
JOB_FAILED
JOB_RETRIED
JOB_EXPIRED
PROVIDER_WEBHOOK_RECEIVED
```

---

## 9. Kolejki i topiki

Proponowany podział:

```text
notification.created

notification.email.critical
notification.email.high
notification.email.normal
notification.email.low

notification.sms.critical
notification.sms.high
notification.sms.normal

notification.push.critical
notification.push.high
notification.push.normal
notification.push.low

notification.inapp.normal

notification.retry
notification.dlq
notification.status.updated
```

Dlaczego nie jedna kolejka?

Bo różne kanały mają różne:

- limity,
- czasy odpowiedzi,
- koszty,
- strategie retry,
- priorytety,
- providerów.

SMS jest droższy niż email, a push ma inne błędy niż email. Wrzucenie wszystkiego do jednej kolejki utrudnia kontrolę.

---

## 10. Retry strategy

Retry trzeba projektować ostrożnie, bo można przypadkowo zasypać providera albo użytkownika.

### 10.1. Błędy tymczasowe

Przykłady:

- timeout,
- 429 rate limited,
- 500 provider error,
- network error.

Strategia:

```text
retry with exponential backoff + jitter
```

Przykład:

```text
1. retry: po 30 sekundach
2. retry: po 2 minutach
3. retry: po 10 minutach
4. retry: po 30 minutach
5. retry: po 2 godzinach
```

---

### 10.2. Błędy permanentne

Przykłady:

- invalid email,
- invalid phone number,
- push token expired,
- user unsubscribed,
- hard bounce.

Nie robimy retry. Aktualizujemy status i ewentualnie profil użytkownika.

---

### 10.3. Dead-letter queue

Do DLQ trafiają wiadomości, których nie udało się przetworzyć po maksymalnej liczbie prób.

DLQ powinno mieć:

- dashboard,
- alerty,
- możliwość replay,
- możliwość ręcznego oznaczenia jako ignored,
- zapis błędu.

---

## 11. Rate limiting i throttling

System musi uwzględniać limity providerów.

Przykład:

```text
SendGrid: X wiadomości / sekundę
Twilio: Y SMS / sekundę
FCM: Z requestów / sekundę
```

Potrzebne mechanizmy:

- global rate limit per provider,
- rate limit per tenant,
- rate limit per user,
- rate limit per notification type,
- throttling kampanii marketingowych,
- oddzielna przepustowość dla powiadomień krytycznych.

Przykład polityki:

```text
critical/security: omija limity marketingowe
marketing: max 1 email / user / dzień
sms: max 3 SMS / user / dzień
push: max 10 push / user / dzień
```

---

## 12. Idempotency

Każdy endpoint tworzący powiadomienie powinien wspierać `idempotency_key`.

Scenariusz:

```text
Payment Service wysyła request.
Notification API zapisuje notification.
Payment Service nie dostaje odpowiedzi przez timeout.
Payment Service ponawia request.
Bez idempotency użytkownik dostaje dwa emaile.
```

Rozwiązanie:

```text
UNIQUE(idempotency_key)
```

Jeżeli request z takim kluczem już istnieje, API zwraca istniejące `notification_id`.

---

## 13. Outbox Pattern

Jeżeli Notification API zapisuje request do bazy i publikuje event do brokera, może wystąpić problem:

```text
DB write success
Broker publish failed
```

Wtedy request istnieje, ale nikt go nie przetworzy.

Lepszy wzorzec:

```text
1. W jednej transakcji zapisujemy notification_request i outbox_event.
2. Osobny Outbox Publisher czyta outbox_events.
3. Publikuje event do brokera.
4. Oznacza outbox_event jako published.
```

Tabela:

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP NULL
);
```

To jest ważny element. Bez niego system może wyglądać poprawnie, ale gubić powiadomienia w rzadkich edge case’ach.

---

## 14. Provider fallback

Dla krytycznych powiadomień warto mieć fallback.

Przykład email:

```text
Primary: SendGrid
Fallback: Amazon SES
```

Przykład SMS:

```text
Primary: Twilio
Fallback: Vonage
```

Strategia:

```text
1. Spróbuj primary provider.
2. Jeśli timeout/5xx/429 utrzymuje się, przełącz na fallback.
3. Nie przełączaj przy błędach permanentnych, np. invalid phone.
```

Trzeba uważać, żeby fallback nie wysłał duplikatu, jeśli primary faktycznie wysłał wiadomość, ale odpowiedź zaginęła. Dlatego provider-level idempotency, jeśli dostępne, jest bardzo przydatne.

---

## 15. Observability

### 15.1. Metryki

Najważniejsze metryki:

```text
notifications_created_total
notifications_sent_total
notifications_failed_total
notifications_delivered_total
notification_processing_latency
notification_queue_lag
provider_latency
provider_error_rate
retry_count
dlq_size
bounce_rate
unsubscribe_rate
sms_cost_total
push_invalid_token_rate
```

---

### 15.2. Logi

Każdy request powinien mieć:

```text
correlation_id
notification_id
job_id
user_id
channel
provider
status
error_code
```

Nie logowałbym pełnej treści wiadomości, szczególnie jeśli może zawierać dane osobowe, linki resetu hasła albo kody OTP.

---

### 15.3. Tracing

Distributed tracing:

```text
Payment Service
  -> Notification API
    -> Outbox Publisher
      -> Kafka
        -> Email Worker
          -> Provider
```

Trace ID powinien przechodzić przez cały pipeline.

---

### 15.4. Alerty

Przykładowe alerty:

```text
DLQ size > threshold
provider error rate > 5% przez 5 minut
critical notification p95 latency > 10s
queue lag rośnie przez 10 minut
bounce rate spike
webhook signature validation failures
```

---

## 16. Bezpieczeństwo

### 16.1. Autoryzacja

Notification API nie powinno być publiczne dla każdego klienta.

Opcje:

- service-to-service auth,
- mTLS,
- OAuth client credentials,
- signed requests,
- API keys dla zewnętrznych tenantów.

---

### 16.2. Ochrona danych

Powiadomienia mogą zawierać dane wrażliwe.

Zasady:

- szyfrowanie danych w spoczynku,
- TLS w komunikacji,
- nie logować payloadów z PII,
- maskowanie emaili i telefonów w logach,
- krótki TTL dla OTP/reset tokenów,
- ograniczony dostęp do tabel audytowych.

---

### 16.3. Template injection

Jeśli użytkownik albo admin może edytować template, trzeba pilnować:

- braku wykonywania kodu,
- escapingu HTML,
- walidacji zmiennych,
- ograniczenia helperów,
- osobnej roli do publikowania template’ów.

---

### 16.4. Unsubscribe i compliance

Dla marketingu:

- link unsubscribe,
- suppression list,
- historia zgód,
- double opt-in tam, gdzie wymagane,
- brak wysyłki marketingowej bez zgody.

Dla powiadomień transakcyjnych unsubscribe nie zawsze ma zastosowanie.

---

## 17. Multi-tenancy

Jeżeli system obsługuje wiele organizacji/tenantów, prawie wszystkie tabele powinny mieć `tenant_id`.

Przykład:

```sql
tenant_id UUID NOT NULL
```

Wtedy rate limits, providery, template’y i preferencje mogą być tenant-specific.

Trzeba pilnować izolacji:

- indeksy po `tenant_id`,
- autoryzacja per tenant,
- osobne limity,
- osobne klucze providerów,
- opcjonalnie osobne kolejki dla dużych tenantów.

---

## 18. Sharding i skalowanie bazy

Przy dużej skali tabele `notification_jobs` i `notification_events` będą rosły bardzo szybko.

### 18.1. Partycjonowanie po czasie

```text
notification_events_2026_06
notification_events_2026_07
...
```

Dobre dla audytu i archiwizacji.

### 18.2. Partycjonowanie po user_id

Dobre dla zapytań użytkownika, np. in-app notifications.

### 18.3. TTL / archiwizacja

Przykład:

```text
notification_jobs: trzymamy 90 dni
notification_events: trzymamy 180 dni
in_app_notifications: trzymamy 1 rok albo według ustawień
```

Starsze dane można przenieść do object storage, np. S3.

---

## 19. Cache

Cache przyda się dla:

- template’ów,
- preferencji użytkownika,
- konfiguracji providerów,
- rate limitów,
- suppression list.

Redis:

```text
template:{template_key}:{channel}:{locale}:{version}
preferences:{user_id}
rate_limit:{provider}:{window}
dedupe:{idempotency_key}
```

Trzeba uważać, żeby cache preferencji nie spowodował wysłania wiadomości po unsubscribe. Dla operacji opt-out warto natychmiast invalidować cache.

---

## 20. Handling user identity and contact data

Notification System nie musi być właścicielem danych użytkownika.

Możliwe podejścia:

### Opcja A — system pobiera dane z User Service

```text
Notification Worker -> User Service -> email/phone/push tokens
```

Plusy:

- jedno źródło prawdy.

Minusy:

- zależność runtime,
- większa latencja,
- User Service może stać się bottleneckiem.

### Opcja B — lokalna kopia contact points

Notification System przechowuje:

```text
user_id
email
phone_number
push_tokens
locale
timezone
```

Aktualizowana eventami z User Service.

Plusy:

- szybciej,
- odporniej.

Minusy:

- eventual consistency,
- trzeba dbać o synchronizację.

Dla dużego systemu wybrałbym opcję B z eventami:

```text
user.email.updated
user.phone.updated
user.push_token.added
user.push_token.removed
```

---

## 21. Push notifications

Push wymaga dodatkowej logiki.

Tabela tokenów:

```sql
CREATE TABLE push_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    token TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,

    UNIQUE(token)
);
```

Jeżeli FCM/APNs zwróci invalid token, trzeba go oznaczyć jako nieaktywny.

```text
INVALID_TOKEN -> deactivate push token
```

Nie należy robić retry na niepoprawny token.

---

## 22. Email-specific concerns

Email wymaga obsługi:

- bounce,
- spam complaint,
- unsubscribe,
- open tracking,
- click tracking,
- SPF/DKIM/DMARC,
- suppression list,
- reputacji domeny.

System powinien mieć osobne domeny/nadawców dla różnych typów emaili:

```text
security.example.com
billing.example.com
marketing.example.com
```

Mieszanie krytycznych maili z marketingiem może zaszkodzić deliverability.

---

## 23. SMS-specific concerns

SMS jest drogi, więc potrzebne są limity.

Dodatkowo:

- walidacja numeru telefonu,
- format E.164,
- country-specific rules,
- limity per kraj,
- fallback kanału, np. jeśli SMS za drogi, użyj push dla non-critical,
- ochrona przed spamem OTP.

Przykład E.164:

```text
+48123123123
```

---

## 24. In-app notification design

### 24.1. REST API

```http
GET /v1/in-app-notifications?limit=20&cursor=abc
```

Response:

```json
{
  "items": [
    {
      "id": "inapp_123",
      "title": "Nowa wiadomość",
      "body": "Masz nową wiadomość od Anny",
      "action_url": "/messages/456",
      "read_at": null,
      "created_at": "2026-06-05T10:00:00Z"
    }
  ],
  "next_cursor": "def"
}
```

### 24.2. Real-time

Opcje:

- WebSocket,
- Server-Sent Events,
- polling.

Dla większości produktów wystarczy:

```text
WebSocket dla aktywnych użytkowników
REST API jako source of truth
```

WebSocket nie powinien być jedynym źródłem, bo użytkownik może być offline.

---

## 25. Exactly-once vs at-least-once

W praktyce system powinien zakładać model:

```text
at-least-once processing
```

Czyli worker może dostać tę samą wiadomość więcej niż raz.

Dlatego potrzebujemy:

- idempotency,
- deduplikacji,
- statusów jobów,
- provider message id,
- blokady przetwarzania.

Nie obiecywałbym exactly-once end-to-end, bo zewnętrzni providerzy i sieć tego nie gwarantują.

---

## 26. Concurrency control

Worker może równolegle przetwarzać ten sam job, jeśli retry albo redelivery z kolejki zadziałają w złym momencie.

Rozwiązania:

- optimistic locking,
- status transition z warunkiem,
- distributed lock,
- `SELECT FOR UPDATE SKIP LOCKED`,
- idempotent provider calls.

Przykład:

```sql
UPDATE notification_jobs
SET status = 'PROCESSING'
WHERE id = :job_id
  AND status IN ('QUEUED', 'RETRYING');
```

Jeśli affected rows = 0, worker nie powinien przetwarzać joba.

---

## 27. Status transition model

Dobrze zdefiniować dozwolone przejścia.

```text
CREATED -> VALIDATED
VALIDATED -> QUEUED
QUEUED -> PROCESSING
PROCESSING -> SENT
PROCESSING -> FAILED
FAILED -> RETRYING
RETRYING -> PROCESSING
FAILED -> DLQ
SENT -> DELIVERED
SENT -> BOUNCED
SENT -> OPENED
SENT -> CLICKED
```

Niektóre statusy są końcowe:

```text
DELIVERED
BOUNCED
CANCELLED
EXPIRED
DLQ
```

---

## 28. Failure scenarios

### 28.1. Provider niedostępny

```text
Worker dostaje timeout.
Job przechodzi do RETRYING.
Po kilku błędach high-priority job przechodzi na fallback provider.
```

### 28.2. Broker niedostępny

Notification API nie powinno publikować bezpośrednio do brokera bez outboxa.

```text
Request zapisany w DB + outbox.
Outbox Publisher opublikuje później.
```

### 28.3. DB chwilowo niedostępna

API powinno zwrócić błąd, bo bez trwałego zapisu nie można obiecać wysyłki.

Nie należy przyjmować powiadomienia tylko do pamięci.

### 28.4. Webhook od providera przychodzi przed aktualizacją joba

Może się zdarzyć.

Rozwiązanie:

- szukamy po `provider_message_id`,
- jeśli nie ma, zapisujemy webhook jako pending event,
- później reconciler dopasowuje.

### 28.5. Użytkownik wypisał się po zakolejkowaniu wiadomości

Dla marketingu worker powinien sprawdzić preferencje tuż przed wysłaniem albo mieć krótki TTL cache. Dla krytycznych powiadomień obowiązują inne reguły.

---

## 29. Reconciliation jobs

Warto mieć okresowe joby naprawcze:

```text
find stuck PROCESSING jobs older than 15 min
find SENT jobs without provider callback after 24h
replay failed outbox events
sync provider suppression lists
clean invalid push tokens
archive old notification events
```

Bez takich jobów system będzie z czasem gromadził „zombie statusy”.

---

## 30. Cost control

Koszty będą szczególnie ważne dla SMS i email marketingu.

Mechanizmy:

- budżet per tenant,
- budżet per kanał,
- rate limit SMS,
- limity kampanii,
- wybór tańszego kanału dla low-priority,
- agregowanie powiadomień w digest,
- deduplikacja podobnych alertów.

Przykład:

```text
Jeśli użytkownik dostaje 20 komentarzy w 5 minut,
nie wysyłaj 20 emaili.
Wyślij jeden digest: "Masz 20 nowych komentarzy".
```

---

## 31. Aggregation / Digest

Dla powiadomień niskiego priorytetu warto wspierać agregację.

Przykład:

```text
Zamiast:
- Anna skomentowała
- Piotr skomentował
- Maria skomentowała

Wyślij:
- Masz 3 nowe komentarze
```

Można to zrobić przez:

- agregację per user + notification type + time window,
- np. 5 minut dla komentarzy,
- 24h dla daily digest.

Tabela:

```sql
CREATE TABLE notification_digest_buffer (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    digest_key VARCHAR(255) NOT NULL,
    items JSONB NOT NULL,
    flush_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL
);
```

---

## 32. Template versioning

Nie należy nadpisywać aktywnego szablonu bez wersjonowania.

Problem:

```text
Notification utworzony o 10:00 używa template v1.
Admin edytuje template o 10:01.
Worker wysyła o 10:05.
Który template powinien zostać użyty?
```

Najbezpieczniej:

- notification request zapisuje `template_version`,
- worker renderuje konkretną wersję,
- stare wersje są immutable.

---

## 33. Localization and timezone

System powinien znać:

- `locale`,
- `timezone`,
- format daty,
- format waluty,
- fallback językowy.

Przykład:

```text
user.locale = pl-PL
user.timezone = Europe/Warsaw
```

Dla kampanii:

```text
send at 9:00 local user time
```

To oznacza, że scheduler musi umieć rozbić kampanię na wiele stref czasowych.

---

## 34. Suggested technology stack

Jedna możliwa konfiguracja:

```text
API: Java/Kotlin Spring Boot, Go albo Node.js
DB: PostgreSQL
Queue: Kafka albo AWS SQS
Cache: Redis
Workers: Go/Java/Kotlin
Search/analytics: ClickHouse albo BigQuery
Object storage: S3
Monitoring: Prometheus + Grafana
Tracing: OpenTelemetry
Logs: Loki/ELK
Email: SES + SendGrid fallback
SMS: Twilio + Vonage fallback
Push: FCM/APNs
```

Dla prostszego MVP:

```text
API: Node.js/NestJS lub Spring Boot
DB: PostgreSQL
Queue: SQS/RabbitMQ
Cache: Redis
Email: SendGrid/SES
SMS: Twilio
Push: FCM
```

---

## 35. MVP scope

Nie budowałbym wszystkiego od razu. Sensowny MVP:

### MVP

- Notification API,
- Email channel,
- Push channel,
- proste template’y,
- PostgreSQL,
- kolejka,
- retry,
- DLQ,
- idempotency,
- podstawowe preferencje,
- statusy,
- dashboard techniczny.

### Później

- SMS,
- provider fallback,
- A/B testing,
- digest,
- kampanie marketingowe,
- zaawansowany scheduler,
- multi-tenancy,
- analytics,
- cost control,
- suppression lists,
- in-app real-time.

---

## 36. Trade-offy

### 36.1. Synchroniczna wysyłka vs asynchroniczna

**Synchroniczna** jest prostsza, ale zawodna i wolna.

**Asynchroniczna** jest bardziej złożona, ale lepiej skaluje się i izoluje system od awarii providerów.

Rekomendacja: **asynchroniczna**.

---

### 36.2. Jeden worker vs osobne workery per kanał

Jeden worker jest prostszy, ale trudniejszy do skalowania i debugowania.

Osobne workery dają lepszą kontrolę.

Rekomendacja: **osobne workery per kanał**.

---

### 36.3. Jedna kolejka vs wiele kolejek

Jedna kolejka jest prostsza.

Wiele kolejek pozwala odseparować priorytety i kanały.

Rekomendacja: **wiele kolejek dla większego systemu**, jedna kolejka tylko dla MVP.

---

### 36.4. Pobieranie danych użytkownika live vs lokalna kopia

Live lookup jest spójniejszy, ale wolniejszy i mniej odporny.

Lokalna kopia jest szybsza, ale eventual consistent.

Rekomendacja: dla większej skali **lokalna kopia contact points aktualizowana eventami**.

---

## 37. Najważniejsze ryzyka

Największe ryzyka w takim systemie:

1. **Duplikaty powiadomień**  
   Rozwiązanie: idempotency, dedupe, status locking.

2. **Zgubione powiadomienia**  
   Rozwiązanie: durable queue, outbox pattern, retry.

3. **Provider outage**  
   Rozwiązanie: fallback, retry, circuit breaker.

4. **Wysyłka mimo opt-out**  
   Rozwiązanie: preference check blisko momentu wysyłki, invalidacja cache.

5. **Koszty SMS**  
   Rozwiązanie: rate limits, budżety, fallback do push/email.

6. **Kampanie blokujące powiadomienia krytyczne**  
   Rozwiązanie: osobne kolejki i priorytety.

7. **Niewłaściwe logowanie PII**  
   Rozwiązanie: maskowanie, ograniczenie logów, szyfrowanie.

---

## 38. Proponowany docelowy design

Najrozsądniejsza architektura produkcyjna:

```text
Notification API
  -> PostgreSQL
  -> Outbox Events
  -> Outbox Publisher
  -> Kafka/SQS

Kafka/SQS
  -> Notification Orchestrator
  -> Channel Queues

Channel Queues
  -> Email Worker
  -> SMS Worker
  -> Push Worker
  -> In-App Worker

Workers
  -> Provider Adapter
  -> External Provider

Provider Webhooks
  -> Webhook Handler
  -> Status Update Events
  -> Notification DB

Monitoring
  -> Metrics, Logs, Tracing, Alerts
```

---

## 39. Minimalny diagram logiczny

```text
┌──────────────────────┐
│ Source Services      │
└──────────┬───────────┘
           │ POST /notifications
           ▼
┌──────────────────────┐
│ Notification API     │
│ - auth               │
│ - validation         │
│ - idempotency        │
└──────────┬───────────┘
           │ transaction
           ▼
┌──────────────────────┐
│ PostgreSQL           │
│ notification_request │
│ outbox_events        │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Outbox Publisher     │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Message Broker       │
└─────┬────────┬────────┘
      │        │
      ▼        ▼
┌─────────┐ ┌─────────┐
│ Email   │ │ Push    │
│ Worker  │ │ Worker  │
└────┬────┘ └────┬────┘
     │           │
     ▼           ▼
┌─────────┐ ┌─────────┐
│ SES /   │ │ FCM /   │
│ SendGrid│ │ APNs    │
└─────────┘ └─────────┘
```

---

## 40. Podsumowanie decyzji architektonicznych

| Obszar | Decyzja |
|---|---|
| Processing | asynchroniczny |
| Queue | Kafka/SQS/RabbitMQ |
| DB | PostgreSQL |
| Cache | Redis |
| Reliability | outbox + retry + DLQ |
| Delivery semantics | at-least-once |
| Duplicate prevention | idempotency key + dedupe |
| Scaling | osobne workery per kanał i priorytet |
| Provider integration | adapter layer |
| Status tracking | osobna tabela jobs + events |
| Compliance | preferences + unsubscribe + suppression |
| Observability | metrics, logs, tracing, alerts |

Najważniejsza uwaga: **to nie powinien być prosty wrapper na SendGrid/Twilio**. Dobry Notification System to pipeline z kolejkami, idempotencją, preferencjami, retry, audytem i kontrolą kosztów. Bez tych elementów system może działać w demo, ale będzie sprawiał problemy produkcyjnie.
