# File Storage System Design

Kompleksowy projekt systemu typu **Dropbox / Google Drive / OneDrive** z obsługą uploadu, downloadu, folderów, udostępniania, wersjonowania, synchronizacji, bezpieczeństwa i skalowalnego przechowywania plików.

---

## Spis treści

1. [Cel systemu](#1-cel-systemu)
2. [Wymagania funkcjonalne](#2-wymagania-funkcjonalne)
3. [Wymagania niefunkcjonalne](#3-wymagania-niefunkcjonalne)
4. [High-level architecture](#4-high-level-architecture)
5. [Główne komponenty](#5-główne-komponenty)
6. [Storage design](#6-storage-design)
7. [Database design](#7-database-design)
8. [API design](#8-api-design)
9. [Upload flow](#9-upload-flow)
10. [Download flow](#10-download-flow)
11. [Deduplikacja](#11-deduplikacja)
12. [Spójność i transakcje](#12-spójność-i-transakcje)
13. [Transactional outbox](#13-transactional-outbox)
14. [Event-driven architecture](#14-event-driven-architecture)
15. [Cache design](#15-cache-design)
16. [Permission model](#16-permission-model)
17. [Quota management](#17-quota-management)
18. [Skanowanie antywirusowe](#18-skanowanie-antywirusowe)
19. [Search indexing](#19-search-indexing)
20. [Sync design](#20-sync-design)
21. [Konflikty edycji](#21-konflikty-edycji)
22. [CDN](#22-cdn)
23. [Observability](#23-observability)
24. [Reliability](#24-reliability)
25. [Garbage collection](#25-garbage-collection)
26. [Security design](#26-security-design)
27. [Rate limiting](#27-rate-limiting)
28. [Skalowanie](#28-skalowanie)
29. [Indeksy w bazie](#29-indeksy-w-bazie)
30. [Najważniejsze decyzje architektoniczne](#30-najważniejsze-decyzje-architektoniczne)
31. [MVP vs produkcja](#31-mvp-vs-produkcja)
32. [Potencjalne problemy i rozwiązania](#32-potencjalne-problemy-i-rozwiązania)
33. [Proponowany stack technologiczny](#33-proponowany-stack-technologiczny)
34. [Minimalny model domenowy](#34-minimalny-model-domenowy)
35. [Najważniejsze endpointy](#35-najważniejsze-endpointy)
36. [Rekomendowana architektura docelowa](#36-rekomendowana-architektura-docelowa)
37. [Rekomendowana wersja implementacyjna](#37-rekomendowana-wersja-implementacyjna)

---

## 1. Cel systemu

System ma umożliwiać użytkownikom:

- przesyłanie plików,
- pobieranie plików,
- organizowanie plików w folderach,
- udostępnianie plików innym użytkownikom,
- przechowywanie wielu wersji pliku,
- synchronizację między urządzeniami,
- odzyskiwanie usuniętych plików,
- wyszukiwanie po nazwie i metadanych,
- bezpieczny dostęp z kontrolą uprawnień.

Zakładamy, że pliki mogą mieć od kilku KB do wielu GB, więc system musi wspierać:

- chunked upload,
- resumable upload,
- przechowywanie zawartości plików poza główną bazą danych,
- oddzielenie metadanych od binary content.

---

## 2. Wymagania funkcjonalne

### Upload pliku

Użytkownik może przesłać plik do swojego katalogu. Upload powinien obsługiwać:

- małe pliki w jednym żądaniu,
- duże pliki dzielone na części,
- wznowienie uploadu po zerwaniu połączenia,
- deduplikację na poziomie hashy,
- walidację limitów przestrzeni.

### Download pliku

Użytkownik może pobrać plik, jeżeli ma do niego uprawnienia. System powinien obsługiwać:

- generowanie krótkotrwałych linków do pobrania,
- streamowanie dużych plików,
- pobieranie konkretnych wersji pliku,
- kontrolę dostępu przed wydaniem linku.

### Foldery i struktura katalogów

System powinien umożliwiać:

- tworzenie folderów,
- przenoszenie plików,
- zmianę nazwy pliku/folderu,
- usuwanie,
- listowanie zawartości katalogu,
- obsługę ścieżek typu `/Documents/Work/file.pdf`.

### Udostępnianie

Plik lub folder można udostępnić:

- konkretnemu użytkownikowi,
- grupie użytkowników,
- przez publiczny link,
- z poziomem uprawnień: `read`, `write`, `owner`.

### Wersjonowanie

Każda modyfikacja pliku tworzy nową wersję. System powinien pozwalać:

- pobrać starszą wersję,
- przywrócić starszą wersję,
- ograniczać liczbę przechowywanych wersji,
- usuwać stare wersje zgodnie z polityką retencji.

### Soft delete

Usunięte pliki trafiają do kosza i mogą zostać przywrócone przez określony czas, np. 30 dni.

---

## 3. Wymagania niefunkcjonalne

### Skalowalność

System powinien obsługiwać wzrost liczby użytkowników, plików i operacji bez przebudowy architektury.

Najważniejsze założenie: **pliki nie są przechowywane w relacyjnej bazie danych**. Baza przechowuje metadane, a rzeczywista zawartość plików trafia do object storage.

### Dostępność

Docelowo system powinien mieć wysoką dostępność, np.:

- API: 99.9% lub więcej,
- storage: zależnie od klasy object storage,
- metadane replikowane między instancjami bazy.

### Spójność

Silna spójność jest potrzebna dla:

- uprawnień,
- limitów quota,
- informacji o właścicielu,
- aktualnej wersji pliku.

Eventual consistency wystarczy dla:

- indeksowania wyszukiwarki,
- przeliczania statystyk,
- generowania miniaturek,
- skanowania antywirusowego,
- synchronizacji między urządzeniami.

### Bezpieczeństwo

System musi zapewniać:

- szyfrowanie danych w tranzycie,
- szyfrowanie danych w spoczynku,
- autoryzację każdej operacji,
- izolację danych między użytkownikami,
- podpisywane linki do pobierania/uploadu,
- audyt dostępu do plików.

---

## 4. High-level architecture

```text
Client
  |
  | HTTPS
  v
API Gateway
  |
  +--> Auth Service
  |
  +--> File Metadata Service
  |
  +--> Upload Service
  |
  +--> Download Service
  |
  +--> Sharing Service
  |
  +--> Search Service
  |
  +--> Sync Service
  |
  +--> Notification Service
  |
  v
Message Queue / Event Bus
  |
  +--> Thumbnail Worker
  +--> Antivirus Worker
  +--> Indexing Worker
  +--> Cleanup Worker
  +--> Audit Worker

Data Layer:
  - Metadata DB
  - Object Storage
  - Cache
  - Search Index
  - Audit Log Storage
```

---

## 5. Główne komponenty

### 5.1 API Gateway

Odpowiada za:

- terminację TLS,
- rate limiting,
- routing do serwisów,
- walidację tokenów,
- podstawowy request logging,
- ochronę przed nadużyciami.

Możliwe technologie:

- NGINX,
- Envoy,
- Kong,
- AWS API Gateway,
- GCP API Gateway.

### 5.2 Auth Service

Odpowiada za:

- logowanie,
- rejestrację,
- JWT/session tokens,
- refresh tokens,
- integrację OAuth,
- role użytkowników,
- MFA, jeśli wymagane.

Przykładowe role:

```text
OWNER
EDITOR
VIEWER
```

### 5.3 File Metadata Service

Najważniejszy serwis domenowy.

Przechowuje informacje o:

- plikach,
- folderach,
- wersjach,
- właścicielach,
- ścieżkach,
- rozmiarach,
- statusach uploadu,
- hashach,
- lokalizacji danych w object storage.

Nie przechowuje binarnej zawartości plików.

### 5.4 Upload Service

Odpowiada za upload małych i dużych plików.

Dla dużych plików powinien wspierać:

- inicjalizację uploadu,
- podział pliku na chunki,
- upload chunków bezpośrednio do object storage,
- zatwierdzenie uploadu,
- składanie pliku lub zapis manifestu chunków,
- retry,
- resumable upload.

Najlepszy wzorzec: **client uploads directly to object storage using pre-signed URLs**.

Dzięki temu API nie jest przeciążane dużymi plikami.

### 5.5 Download Service

Odpowiada za:

- sprawdzenie uprawnień,
- wybór właściwej wersji pliku,
- wygenerowanie signed URL,
- ewentualnie streamowanie przez backend dla bardzo wrażliwych danych.

Preferowany wariant:

```text
Client -> API -> authorization check -> signed URL -> Object Storage/CDN
```

### 5.6 Sharing Service

Odpowiada za:

- udostępnianie plików użytkownikom,
- publiczne linki,
- wygasające linki,
- hasła do linków,
- odwoływanie dostępu,
- dziedziczenie uprawnień z folderów.

Ważna decyzja projektowa: uprawnienia folderów mogą być kosztowne, jeżeli system musi szybko ustalać dostęp do milionów plików w głębokim drzewie katalogów.

Dla dużej skali lepiej stosować:

- ACL na zasobach,
- dziedziczenie uprawnień liczone asynchronicznie,
- materializowaną tabelę access control,
- cache uprawnień.

### 5.7 Sync Service

Synchronizacja pozwala klientom wykrywać zmiany.

Zamiast porównywać całe drzewo katalogów, system powinien utrzymywać **change log**.

Przykład:

```text
change_id | user_id | resource_id | operation | timestamp
```

Klient pyta:

```http
GET /sync/changes?cursor=123456
```

System zwraca zmiany od danego kursora. To jest znacznie wydajniejsze niż pełne listowanie plików.

### 5.8 Search Service

Search Service indeksuje:

- nazwy plików,
- nazwy folderów,
- typy MIME,
- właścicieli,
- tagi,
- ewentualnie treść dokumentów, jeśli system to obsługuje.

Dane do indeksu trafiają asynchronicznie przez event bus.

Możliwe technologie:

- Elasticsearch,
- OpenSearch,
- Meilisearch,
- PostgreSQL full-text search dla prostszej wersji.

### 5.9 Background Workers

Workerzy wykonują zadania poza główną ścieżką request-response:

- skanowanie antywirusowe,
- generowanie miniaturek,
- ekstrakcja metadanych,
- indeksowanie wyszukiwarki,
- czyszczenie starych wersji,
- usuwanie plików po retencji,
- naliczanie quota,
- audyt.

---

## 6. Storage design

### 6.1 Object Storage

Zawartość plików powinna być przechowywana w systemie typu:

- Amazon S3,
- Google Cloud Storage,
- Azure Blob Storage,
- MinIO,
- Ceph Object Gateway.

Przykładowy object key:

```text
tenant_id/user_id/file_id/version_id/blob_id
```

Lepiej nie używać oryginalnych nazw plików w object key, ponieważ:

- nazwy mogą zawierać problematyczne znaki,
- nazwy mogą się zmieniać,
- nazwy mogą ujawniać prywatne informacje,
- łatwiej uniknąć kolizji.

Dobry wzorzec:

```text
objects/{hash_prefix}/{sha256}
```

albo:

```text
objects/{tenant_id}/{file_id}/{version_id}
```

### 6.2 Chunk storage

Dla dużych plików:

```text
uploads/{upload_id}/chunks/{chunk_number}
```

Po zakończeniu uploadu system może:

1. złożyć chunki w jeden obiekt,
2. albo przechowywać manifest chunków.

Manifest:

```json
{
  "file_id": "file_123",
  "version_id": "ver_456",
  "chunks": [
    {
      "index": 0,
      "object_key": "chunks/abc/0",
      "size": 8388608,
      "sha256": "..."
    },
    {
      "index": 1,
      "object_key": "chunks/abc/1",
      "size": 8388608,
      "sha256": "..."
    }
  ]
}
```

Dla bardzo dużej skali manifest chunków daje większą elastyczność, ale komplikuje download. Dla prostszego systemu lepiej po uploadzie złożyć jeden finalny obiekt.

---

## 7. Database design

Najlepszy wybór na start: **PostgreSQL** dla metadanych.

Dla bardzo dużej skali można przejść w stronę:

- PostgreSQL z partycjonowaniem,
- CockroachDB,
- Spanner,
- DynamoDB/Cassandra dla części metadanych,
- oddzielnego systemu do changelogów.

### 7.1 Tabela `users`

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    display_name TEXT,
    password_hash TEXT,
    storage_quota_bytes BIGINT NOT NULL,
    storage_used_bytes BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### 7.2 Tabela `folders`

```sql
CREATE TABLE folders (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    parent_folder_id UUID REFERENCES folders(id),
    name TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,

    UNIQUE(owner_id, parent_folder_id, name)
);
```

Uwaga: `UNIQUE(owner_id, parent_folder_id, name)` może wymagać dodatkowej obsługi dla soft delete. W praktyce często stosuje się częściowy indeks:

```sql
CREATE UNIQUE INDEX unique_active_folder_name
ON folders(owner_id, parent_folder_id, name)
WHERE is_deleted = FALSE;
```

### 7.3 Tabela `files`

```sql
CREATE TABLE files (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id),
    parent_folder_id UUID REFERENCES folders(id),
    name TEXT NOT NULL,
    mime_type TEXT,
    current_version_id UUID,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,

    UNIQUE(owner_id, parent_folder_id, name)
);
```

Statusy:

```text
UPLOADING
ACTIVE
QUARANTINED
DELETED
FAILED
```

### 7.4 Tabela `file_versions`

```sql
CREATE TABLE file_versions (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL REFERENCES files(id),
    version_number INT NOT NULL,
    object_key TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,

    UNIQUE(file_id, version_number)
);
```

### 7.5 Tabela `upload_sessions`

```sql
CREATE TABLE upload_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    file_id UUID REFERENCES files(id),
    target_folder_id UUID REFERENCES folders(id),
    filename TEXT NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    chunk_size_bytes BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    uploaded_chunks INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);
```

Statusy:

```text
INITIATED
IN_PROGRESS
COMPLETED
ABORTED
EXPIRED
FAILED
```

### 7.6 Tabela `upload_chunks`

```sql
CREATE TABLE upload_chunks (
    upload_session_id UUID NOT NULL REFERENCES upload_sessions(id),
    chunk_index INT NOT NULL,
    object_key TEXT NOT NULL,
    sha256 TEXT,
    size_bytes BIGINT,
    uploaded_at TIMESTAMP NOT NULL,

    PRIMARY KEY(upload_session_id, chunk_index)
);
```

### 7.7 Tabela `permissions`

```sql
CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    resource_type TEXT NOT NULL,
    resource_id UUID NOT NULL,
    grantee_type TEXT NOT NULL,
    grantee_id UUID,
    permission_level TEXT NOT NULL,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP
);
```

`resource_type`:

```text
FILE
FOLDER
```

`grantee_type`:

```text
USER
GROUP
PUBLIC_LINK
```

`permission_level`:

```text
READ
WRITE
OWNER
```

### 7.8 Tabela `share_links`

```sql
CREATE TABLE share_links (
    id UUID PRIMARY KEY,
    resource_type TEXT NOT NULL,
    resource_id UUID NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    permission_level TEXT NOT NULL,
    password_hash TEXT,
    expires_at TIMESTAMP,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP
);
```

W bazie należy trzymać **hash tokenu**, nie token wprost.

### 7.9 Tabela `change_log`

```sql
CREATE TABLE change_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id UUID NOT NULL,
    operation TEXT NOT NULL,
    payload JSONB,
    created_at TIMESTAMP NOT NULL
);
```

Operacje:

```text
FILE_CREATED
FILE_UPDATED
FILE_DELETED
FILE_RESTORED
FILE_MOVED
FOLDER_CREATED
FOLDER_DELETED
PERMISSION_CHANGED
```

---

## 8. API design

### 8.1 Upload małego pliku

```http
POST /v1/files
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

Request:

```text
folder_id
file
```

Response:

```json
{
  "file_id": "file_123",
  "version_id": "ver_001",
  "name": "report.pdf",
  "size_bytes": 1234567,
  "status": "ACTIVE"
}
```

Ten wariant jest prosty, ale nie najlepszy dla dużych plików.

### 8.2 Inicjalizacja uploadu dużego pliku

```http
POST /v1/uploads
Authorization: Bearer <token>
Content-Type: application/json
```

Request:

```json
{
  "folder_id": "folder_123",
  "filename": "video.mp4",
  "total_size_bytes": 5368709120,
  "mime_type": "video/mp4",
  "chunk_size_bytes": 8388608
}
```

Response:

```json
{
  "upload_id": "upload_123",
  "chunk_size_bytes": 8388608,
  "total_chunks": 640,
  "expires_at": "2026-06-05T12:00:00Z"
}
```

### 8.3 Pobranie signed URL dla chunka

```http
POST /v1/uploads/{upload_id}/chunks/{chunk_index}/signed-url
Authorization: Bearer <token>
```

Response:

```json
{
  "upload_url": "https://object-storage.example.com/...",
  "method": "PUT",
  "expires_in_seconds": 900
}
```

### 8.4 Potwierdzenie uploadu chunka

```http
POST /v1/uploads/{upload_id}/chunks/{chunk_index}/complete
Authorization: Bearer <token>
```

Request:

```json
{
  "sha256": "abc123...",
  "size_bytes": 8388608
}
```

Response:

```json
{
  "upload_id": "upload_123",
  "chunk_index": 42,
  "status": "UPLOADED"
}
```

### 8.5 Finalizacja uploadu

```http
POST /v1/uploads/{upload_id}/complete
Authorization: Bearer <token>
```

Response:

```json
{
  "file_id": "file_123",
  "version_id": "ver_001",
  "status": "PROCESSING"
}
```

Po finalizacji system może wykonać asynchronicznie:

- składanie chunków,
- skan antywirusowy,
- generowanie miniaturek,
- indeksowanie,
- aktualizację quota.

### 8.6 Download pliku

```http
GET /v1/files/{file_id}/download
Authorization: Bearer <token>
```

Response:

```json
{
  "download_url": "https://object-storage.example.com/...",
  "expires_in_seconds": 300
}
```

### 8.7 Pobranie metadanych pliku

```http
GET /v1/files/{file_id}
Authorization: Bearer <token>
```

Response:

```json
{
  "id": "file_123",
  "name": "report.pdf",
  "mime_type": "application/pdf",
  "size_bytes": 1234567,
  "owner_id": "user_123",
  "current_version_id": "ver_003",
  "created_at": "2026-06-01T10:00:00Z",
  "updated_at": "2026-06-05T10:00:00Z"
}
```

### 8.8 Listowanie folderu

```http
GET /v1/folders/{folder_id}/children?limit=100&cursor=abc
Authorization: Bearer <token>
```

Response:

```json
{
  "items": [
    {
      "type": "FILE",
      "id": "file_123",
      "name": "report.pdf",
      "size_bytes": 1234567
    },
    {
      "type": "FOLDER",
      "id": "folder_456",
      "name": "Invoices"
    }
  ],
  "next_cursor": "def"
}
```

### 8.9 Udostępnianie pliku

```http
POST /v1/files/{file_id}/permissions
Authorization: Bearer <token>
```

Request:

```json
{
  "grantee_type": "USER",
  "grantee_id": "user_456",
  "permission_level": "READ"
}
```

Response:

```json
{
  "permission_id": "perm_123",
  "resource_type": "FILE",
  "resource_id": "file_123",
  "permission_level": "READ"
}
```

### 8.10 Publiczny link

```http
POST /v1/files/{file_id}/share-link
Authorization: Bearer <token>
```

Request:

```json
{
  "permission_level": "READ",
  "expires_at": "2026-07-01T00:00:00Z",
  "password": "optional-password"
}
```

Response:

```json
{
  "url": "https://app.example.com/s/eyJhbGciOi..."
}
```

### 8.11 Sync changes

```http
GET /v1/sync/changes?cursor=123456&limit=500
Authorization: Bearer <token>
```

Response:

```json
{
  "changes": [
    {
      "change_id": 123457,
      "operation": "FILE_UPDATED",
      "resource_type": "FILE",
      "resource_id": "file_123",
      "timestamp": "2026-06-05T11:00:00Z"
    }
  ],
  "next_cursor": 123457
}
```

---

## 9. Upload flow

### 9.1 Flow dla dużego pliku

```text
1. Client -> API: init upload
2. API -> Metadata DB: create upload_session
3. API -> Client: upload_id

4. Client -> API: request signed URL for chunk 0
5. API -> Object Storage: generate pre-signed PUT URL
6. API -> Client: signed URL

7. Client -> Object Storage: PUT chunk
8. Client -> API: mark chunk completed

9. Repeat for all chunks

10. Client -> API: complete upload
11. API -> Metadata DB: validate chunks
12. API -> Queue: FileUploaded event
13. Worker -> Object Storage: compose chunks
14. Worker -> Metadata DB: create file_version, mark ACTIVE
15. Worker -> Queue: FileVersionCreated event
```

---

## 10. Download flow

```text
1. Client -> API: request download
2. API -> Auth Service: validate user
3. API -> Metadata DB: get file metadata
4. API -> Permission Service: check access
5. API -> Object Storage: generate signed URL
6. API -> Client: signed download URL
7. Client -> Object Storage/CDN: download file
```

Nie należy bez potrzeby przepuszczać całego pliku przez backend. Backend powinien autoryzować i wydawać krótkotrwały signed URL.

---

## 11. Deduplikacja

Możliwe poziomy deduplikacji:

### Poziom 1: deduplikacja całych plików

Jeżeli dwa pliki mają ten sam SHA-256, można przechowywać tylko jedną fizyczną kopię.

Tabela:

```sql
CREATE TABLE blobs (
    id UUID PRIMARY KEY,
    sha256 TEXT UNIQUE NOT NULL,
    object_key TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    ref_count BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL
);
```

`file_versions` może wtedy wskazywać na `blob_id`.

```sql
ALTER TABLE file_versions
ADD COLUMN blob_id UUID REFERENCES blobs(id);
```

### Poziom 2: deduplikacja chunków

Bardziej zaawansowane rozwiązanie. Przydatne, jeśli użytkownicy często przechowują podobne duże pliki.

Wada: większa złożoność manifestów, garbage collection i bezpieczeństwa.

Rekomendacja: zacząć od deduplikacji całych plików, a deduplikację chunków wprowadzić dopiero, gdy realnie uzasadniają to koszty storage.

---

## 12. Spójność i transakcje

Najważniejsze operacje powinny być transakcyjne po stronie metadanych.

Przykład finalizacji uploadu:

```text
BEGIN;

1. Pobierz upload_session FOR UPDATE.
2. Sprawdź, czy wszystkie chunki są przesłane.
3. Sprawdź quota użytkownika.
4. Utwórz file albo nową file_version.
5. Zaktualizuj current_version_id.
6. Zaktualizuj storage_used_bytes.
7. Dodaj wpis do change_log.
8. Zmień status upload_session na COMPLETED.

COMMIT;
```

Dopiero po commicie emitujemy event lub używamy wzorca **transactional outbox**.

---

## 13. Transactional outbox

Żeby uniknąć sytuacji, w której baza została zaktualizowana, ale event nie został wysłany, warto użyć outbox pattern.

Tabela:

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type TEXT NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
```

W tej samej transakcji zapisujemy:

- zmianę metadanych,
- wpis w `change_log`,
- event w `outbox_events`.

Osobny proces publikuje eventy do kolejki.

---

## 14. Event-driven architecture

Przykładowe eventy:

```json
{
  "event_type": "FileVersionCreated",
  "file_id": "file_123",
  "version_id": "ver_456",
  "owner_id": "user_123",
  "object_key": "objects/abc",
  "mime_type": "application/pdf",
  "size_bytes": 1234567
}
```

Konsumenci:

- Search Indexer,
- Antivirus Scanner,
- Thumbnail Generator,
- Audit Logger,
- Quota Aggregator,
- Notification Service.

Broker:

- Kafka,
- RabbitMQ,
- AWS SQS/SNS,
- Google Pub/Sub,
- NATS.

Dla większej skali i wielu konsumentów najlepszy jest Kafka albo Pub/Sub. Dla prostszego systemu wystarczy SQS/RabbitMQ.

---

## 15. Cache design

Cache może przyspieszać:

- metadane często pobieranych plików,
- listowanie folderów,
- uprawnienia,
- profile użytkowników,
- signed URL metadata.

Możliwe technologie:

- Redis,
- Memcached.

Przykładowe klucze:

```text
file:{file_id}:metadata
folder:{folder_id}:children:{cursor}
permissions:{user_id}:{resource_id}
quota:{user_id}
```

Trzeba uważać z cache uprawnień. Po odebraniu dostępu cache musi być szybko unieważniony.

Rekomendacja:

- krótki TTL, np. 30-120 sekund,
- explicit invalidation po zmianie ACL,
- brak cache dla bardzo wrażliwych operacji lub ponowna walidacja przed downloadem.

---

## 16. Permission model

### Minimalny model

```text
owner_id na pliku/folderze
+
permissions table
```

Każde żądanie sprawdza:

```text
Czy user jest ownerem?
Czy user ma bezpośredni permission?
Czy user ma permission przez folder nadrzędny?
Czy dostęp pochodzi z publicznego linku?
```

### Problem z dziedziczeniem

Jeżeli plik jest w folderze udostępnionym, trzeba szybko wiedzieć, czy użytkownik ma dostęp.

Opcje:

#### Opcja A: runtime traversal

Przy każdym checku idziemy po rodzicach folderów aż do roota.

Plusy:

- proste,
- zawsze aktualne.

Minusy:

- wolne dla głębokich struktur,
- kosztowne przy dużej skali.

#### Opcja B: materialized permissions

Po udostępnieniu folderu asynchronicznie propagujemy uprawnienia na potomków.

Plusy:

- szybki odczyt.

Minusy:

- trudniejsze odwoływanie dostępu,
- większa złożoność,
- ryzyko opóźnień.

### Rekomendacja

Na start: runtime traversal + cache.

Dla dużej skali: materialized access table.

Przykład:

```sql
CREATE TABLE effective_permissions (
    user_id UUID NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id UUID NOT NULL,
    permission_level TEXT NOT NULL,
    source_permission_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,

    PRIMARY KEY(user_id, resource_type, resource_id)
);
```

---

## 17. Quota management

Każdy użytkownik ma limit przestrzeni:

```text
storage_quota_bytes
storage_used_bytes
```

Trudność: co liczyć do quota?

Możliwe podejścia:

1. Liczyć każdy plik logiczny użytkownika.
2. Liczyć tylko unikalne bloby.
3. Liczyć wszystkie wersje.
4. Liczyć tylko aktualne wersje.

Najbardziej przewidywalne dla użytkownika:

```text
quota = suma rozmiarów aktualnych plików + wersje przechowywane w historii + pliki w koszu
```

Deduplikacja może obniżać koszt infrastruktury, ale niekoniecznie powinna zmieniać quota użytkownika. Inaczej użytkownik może widzieć niezrozumiałe wyniki.

---

## 18. Skanowanie antywirusowe

Po uploadzie plik nie powinien od razu być dostępny publicznie, jeśli wymagamy security-first.

Statusy:

```text
UPLOADED
SCANNING
ACTIVE
QUARANTINED
FAILED
```

Flow:

```text
1. File uploaded
2. Event FileUploaded
3. Antivirus Worker scans object
4. If clean -> ACTIVE
5. If suspicious -> QUARANTINED
```

Dla UX można dopuścić plik właścicielowi od razu, ale nie pozwalać na udostępnianie do zakończenia skanu. Bezpieczniejszy wariant: zablokować download do końca skanowania.

---

## 19. Search indexing

Po zmianie pliku emitowany jest event:

```text
FileCreated
FileRenamed
FileDeleted
PermissionChanged
```

Indexer aktualizuje indeks.

W indeksie nie powinno się trzymać danych, których użytkownik nie może zobaczyć. Najprostsze podejście:

- indeksować dokument,
- przy wyszukiwaniu filtrować wyniki po `owner_id` i dostępach.

Dla public/shared files trzeba uwzględnić ACL.

Przykładowy dokument w indeksie:

```json
{
  "resource_id": "file_123",
  "resource_type": "FILE",
  "name": "invoice_may.pdf",
  "mime_type": "application/pdf",
  "owner_id": "user_123",
  "shared_with_user_ids": ["user_456"],
  "created_at": "2026-06-01T10:00:00Z",
  "updated_at": "2026-06-05T10:00:00Z"
}
```

---

## 20. Sync design

Klient nie powinien pytać: „daj mi wszystkie pliki” przy każdym starcie.

Powinien trzymać lokalny cursor:

```text
last_seen_change_id
```

I pytać:

```http
GET /v1/sync/changes?cursor=last_seen_change_id
```

Ważne przypadki:

- klient był offline przez długi czas,
- changelog został przycięty,
- użytkownik ma bardzo dużo zmian.

Rozwiązanie:

```json
{
  "requires_full_resync": true
}
```

Jeśli cursor jest zbyt stary, klient wykonuje pełną synchronizację.

---

## 21. Konflikty edycji

Typowy przypadek:

```text
Laptop A edytuje file.docx offline.
Laptop B edytuje file.docx offline.
Oba wracają online.
```

System powinien wykryć konflikt przez `base_version_id`.

Upload nowej wersji powinien zawierać:

```json
{
  "base_version_id": "ver_123"
}
```

Jeśli aktualna wersja pliku jest inna niż `base_version_id`, system może:

1. odrzucić upload jako conflict,
2. zapisać jako „conflicted copy”,
3. automatycznie merge'ować dla wybranych typów plików.

Najbezpieczniejszy wariant:

```text
filename (conflicted copy from Jakub's MacBook).docx
```

---

## 22. CDN

Dla publicznych lub często pobieranych plików można użyć CDN.

Flow:

```text
Client -> CDN -> Object Storage
```

Ale dla prywatnych plików trzeba uważać:

- signed URLs,
- signed cookies,
- krótki TTL,
- cache invalidation po odebraniu dostępu,
- brak cache dla bardzo wrażliwych plików.

---

## 23. Observability

System powinien mierzyć:

### API metrics

- request count,
- latency p50/p95/p99,
- error rate,
- rate-limit hits,
- auth failures.

### Upload metrics

- upload success rate,
- average upload duration,
- failed chunk count,
- retry count,
- incomplete upload sessions.

### Storage metrics

- total stored bytes,
- object count,
- orphaned objects,
- storage cost,
- replication lag.

### Queue metrics

- queue depth,
- consumer lag,
- failed events,
- dead-letter queue size.

### Security metrics

- failed access checks,
- suspicious downloads,
- public link accesses,
- malware detections.

---

## 24. Reliability

### Retry strategy

Operacje idempotentne powinny mieć retry.

Przykłady:

- potwierdzenie chunka,
- publikacja eventu,
- indeksowanie,
- generowanie miniaturek.

### Idempotency keys

Dla uploadu i tworzenia plików warto wspierać:

```http
Idempotency-Key: 7fd6d9b2-...
```

Tabela:

```sql
CREATE TABLE idempotency_keys (
    key TEXT PRIMARY KEY,
    user_id UUID NOT NULL,
    request_hash TEXT NOT NULL,
    response_body JSONB,
    status_code INT,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);
```

### Dead Letter Queue

Eventy, których nie udało się przetworzyć po kilku próbach, trafiają do DLQ.

---

## 25. Garbage collection

System musi usuwać:

- nieukończone upload sessions,
- osierocone chunki,
- stare wersje plików,
- pliki po retencji kosza,
- obiekty bez referencji.

Przykładowe zadania:

```text
CleanupExpiredUploadSessions
CleanupOrphanedChunks
CleanupDeletedFilesAfterRetention
CleanupUnreferencedBlobs
```

Przy deduplikacji trzeba uważać na `ref_count`. Fizyczny blob można usunąć dopiero wtedy, gdy nie wskazuje na niego żadna wersja pliku.

---

## 26. Security design

### Szyfrowanie

Minimum:

- TLS dla całej komunikacji,
- encryption at rest w object storage,
- encryption at rest w bazie,
- rotacja kluczy.

Lepszy wariant:

- envelope encryption,
- osobny data encryption key per tenant albo per user,
- KMS do zarządzania kluczami.

### Signed URLs

Signed URL powinien mieć:

- krótki czas ważności,
- ograniczenie metody HTTP,
- opcjonalnie ograniczenie content-type,
- opcjonalnie ograniczenie content-length,
- niemożliwość eskalacji dostępu.

Przykład:

```text
PUT URL ważny 15 minut tylko dla konkretnego object_key
GET URL ważny 5 minut tylko dla konkretnej wersji pliku
```

### Public links

Dla publicznych linków:

- token musi być losowy i długi,
- w bazie przechowywać hash tokenu,
- możliwość ustawienia daty wygaśnięcia,
- możliwość hasła,
- możliwość odwołania,
- rate limiting,
- audit log.

### Audit log

Warto logować:

```text
FILE_VIEWED
FILE_DOWNLOADED
FILE_SHARED
FILE_PERMISSION_CHANGED
FILE_DELETED
PUBLIC_LINK_CREATED
PUBLIC_LINK_USED
```

---

## 27. Rate limiting

Przykładowe limity:

```text
Upload init: 60/min/user
Signed URL generation: 300/min/user
Download request: 600/min/user
Public link access: IP-based limit
Failed auth: aggressive throttling
```

Rate limiting można robić w API Gateway i Redisie.

---

## 28. Skalowanie

### API layer

Stateless, skalowanie poziome:

```text
API instances behind load balancer
```

### Metadata DB

Etapy skalowania:

1. pojedynczy PostgreSQL z repliką read-only,
2. connection pooling,
3. indeksy i partycjonowanie,
4. read replicas,
5. sharding po `owner_id` albo `tenant_id`,
6. ewentualnie distributed SQL.

### Object Storage

Skaluje się niezależnie od aplikacji.

### Queue

Partycjonowanie eventów np. po:

```text
user_id
file_id
tenant_id
```

### Search

Indeks partycjonowany po tenantach lub hashach ownerów.

---

## 29. Indeksy w bazie

Przykładowe ważne indeksy:

```sql
CREATE INDEX idx_files_owner_parent
ON files(owner_id, parent_folder_id)
WHERE is_deleted = FALSE;

CREATE INDEX idx_folders_owner_parent
ON folders(owner_id, parent_folder_id)
WHERE is_deleted = FALSE;

CREATE INDEX idx_file_versions_file_id
ON file_versions(file_id, version_number DESC);

CREATE INDEX idx_permissions_resource
ON permissions(resource_type, resource_id);

CREATE INDEX idx_permissions_grantee
ON permissions(grantee_type, grantee_id);

CREATE INDEX idx_change_log_user_id_id
ON change_log(user_id, id);

CREATE INDEX idx_upload_sessions_user_status
ON upload_sessions(user_id, status);
```

---

## 30. Najważniejsze decyzje architektoniczne

### Decyzja 1: pliki poza bazą

Pliki trzymamy w object storage, nie w PostgreSQL.

Powód:

- niższy koszt,
- lepsza skalowalność,
- łatwiejszy download/upload,
- natywne wsparcie dla signed URLs,
- replikacja i trwałość po stronie storage.

### Decyzja 2: upload bezpośrednio do object storage

Backend nie powinien być proxy dla dużych plików.

Powód:

- mniejsze obciążenie API,
- lepsza wydajność,
- prostsze skalowanie,
- niższe koszty transferu przez backend.

### Decyzja 3: metadane jako źródło prawdy

Object storage przechowuje dane, ale prawdą domenową jest Metadata DB.

Jeżeli obiekt istnieje w storage, ale nie ma go w DB, jest osierocony.

Jeżeli DB wskazuje na object key, którego nie ma w storage, mamy błąd integralności, który musi zostać wykryty przez health/consistency checker.

### Decyzja 4: asynchroniczne przetwarzanie

Skanowanie, thumbnailing i indeksowanie nie powinny blokować uploadu, chyba że wymogi bezpieczeństwa mówią inaczej.

---

## 31. MVP vs produkcja

### MVP

W MVP wystarczy:

- PostgreSQL,
- S3/MinIO,
- Redis,
- proste JWT auth,
- upload przez signed URLs,
- podstawowe foldery,
- podstawowy sharing,
- soft delete,
- limit quota,
- podstawowy changelog.

Nie trzeba od razu budować:

- deduplikacji chunków,
- zaawansowanego ACL,
- globalnego search,
- pełnej synchronizacji offline,
- distributed SQL,
- multi-region active-active.

### Produkcja

W produkcji dodałbym:

- transactional outbox,
- kolejkę eventów,
- workerów,
- antywirus,
- monitoring p95/p99,
- DLQ,
- retencję wersji,
- dokładny audit log,
- CDN,
- KMS,
- backup i disaster recovery,
- testy integralności object storage vs Metadata DB.

---

## 32. Potencjalne problemy i rozwiązania

### Problem: użytkownik anuluje upload

Rozwiązanie:

- upload session wygasa,
- cleanup worker usuwa chunki,
- quota nie jest naliczana przed finalizacją.

### Problem: chunk przesłany dwa razy

Rozwiązanie:

- `PRIMARY KEY(upload_session_id, chunk_index)`,
- operacja idempotentna,
- drugi request zwraca ten sam status.

### Problem: upload zakończony, ale worker padł

Rozwiązanie:

- status `PROCESSING`,
- event w outboxie,
- worker może wznowić przetwarzanie,
- retry i DLQ.

### Problem: uprawnienie cofnięte, ale signed URL nadal działa

Rozwiązanie:

- krótkie TTL signed URL,
- dla bardzo wrażliwych danych proxy download przez backend,
- ewentualnie signed cookies z szybką invalidacją,
- brak długich publicznych URL-i do prywatnych danych.

### Problem: rename folderu z milionem plików

Rozwiązanie:

- nie przechowywać pełnej ścieżki jako źródła prawdy,
- trzymać relację parent-child,
- ewentualną pełną ścieżkę materializować asynchronicznie.

### Problem: listowanie folderu z milionem plików

Rozwiązanie:

- paginacja cursor-based,
- indeks po `parent_folder_id`,
- sortowanie po stabilnym polu, np. `(name, id)` albo `(created_at, id)`.

---

## 33. Proponowany stack technologiczny

### Backend

- TypeScript + Node.js / NestJS,
- Java + Spring Boot,
- Go dla wysokiej wydajności.

Dla tego typu systemu Go lub Java będą bardzo mocne, ale TypeScript/NestJS może być szybszy rozwojowo.

### Database

- PostgreSQL jako główna baza metadanych.
- Redis jako cache i rate limiter.
- OpenSearch/Elasticsearch jako search.
- S3-compatible object storage.

### Queue

- SQS/SNS dla AWS,
- Pub/Sub dla GCP,
- Kafka dla większej skali eventów,
- RabbitMQ dla prostszej infrastruktury.

### Infra

- Kubernetes albo managed containers,
- Terraform,
- Prometheus + Grafana,
- OpenTelemetry,
- Sentry,
- centralized logging.

---

## 34. Minimalny model domenowy

```text
User
 └── Folder
      ├── Folder
      └── File
            └── FileVersion
                  └── Blob/Object
```

Do tego:

```text
UploadSession
UploadChunk
Permission
ShareLink
ChangeLog
AuditLog
OutboxEvent
```

---

## 35. Najważniejsze endpointy

```text
POST   /v1/uploads
POST   /v1/uploads/{id}/chunks/{index}/signed-url
POST   /v1/uploads/{id}/chunks/{index}/complete
POST   /v1/uploads/{id}/complete

GET    /v1/files/{id}
GET    /v1/files/{id}/download
PATCH  /v1/files/{id}
DELETE /v1/files/{id}
POST   /v1/files/{id}/restore

GET    /v1/files/{id}/versions
GET    /v1/files/{id}/versions/{version_id}/download
POST   /v1/files/{id}/versions/{version_id}/restore

POST   /v1/folders
GET    /v1/folders/{id}/children
PATCH  /v1/folders/{id}
DELETE /v1/folders/{id}

POST   /v1/files/{id}/permissions
DELETE /v1/permissions/{id}

POST   /v1/files/{id}/share-link
DELETE /v1/share-links/{id}

GET    /v1/sync/changes
GET    /v1/search?q=...
```

---

## 36. Rekomendowana architektura docelowa

```text
                         +----------------+
                         |     Client     |
                         +-------+--------+
                                 |
                              HTTPS
                                 |
                         +-------v--------+
                         |  API Gateway   |
                         +-------+--------+
                                 |
        +------------------------+-------------------------+
        |                        |                         |
+-------v--------+      +--------v---------+      +--------v--------+
| Auth Service   |      | Metadata Service |      | Sharing Service |
+----------------+      +--------+---------+      +-----------------+
                                 |
                         +-------v--------+
                         |  PostgreSQL    |
                         +-------+--------+
                                 |
                         +-------v--------+
                         | Transactional  |
                         |    Outbox      |
                         +-------+--------+
                                 |
                         +-------v--------+
                         |  Event Bus     |
                         +-------+--------+
                                 |
      +--------------------------+--------------------------+
      |                          |                          |
+-----v------+          +--------v--------+         +-------v------+
| AV Worker  |          | Search Indexer  |         | Thumbnailer |
+------------+          +-----------------+         +--------------+

Upload/Download path:

Client <----signed URLs----> Object Storage <----optional----> CDN
```

---

## 37. Rekomendowana wersja implementacyjna

Gdybym miał to budować praktycznie, zacząłbym tak:

1. **PostgreSQL** dla users, files, folders, versions, permissions.
2. **S3/MinIO** dla zawartości plików.
3. **Upload przez signed URLs**.
4. **Chunked upload** dla plików powyżej np. 50 MB.
5. **Redis** dla cache, rate limitingu i krótkich locków.
6. **Queue + workers** dla skanowania, miniaturek i indeksowania.
7. **Soft delete + wersjonowanie** od początku.
8. **Change log** od początku, nawet jeśli klient sync pojawi się później.
9. **Audit log** przynajmniej dla sharingu i publicznych linków.
10. **Transactional outbox**, jeśli system ma być produkcyjny, a nie tylko demo.

Największy błąd, którego należy unikać: przepuszczanie dużych plików przez backend aplikacyjny i przechowywanie zawartości plików w bazie danych. To szybko zabije skalowalność i koszty.
