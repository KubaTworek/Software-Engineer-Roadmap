# File Storage System Design

<!-- reading-guide:start -->
## Ścieżki czytania

| Cel czytania | Punkt wejścia |
| --- | --- |
| Teoria i model problemu | [3A. Model pojęciowy i granice systemu](#3a-model-pojęciowy-i-granice-systemu) |
| Implementacja i jej dowody | [38. Mapa teorii na kod](#38-mapa-teorii-na-kod) |
| Uruchomienie i eksperymenty | [41. Uruchamianie i strategia testów](#41-uruchamianie-i-strategia-testów) |
| Ograniczenia i scenariusze awarii | [50. Granice implementacji](#50-granice-implementacji) |

Wspólne mechanizmy i ich testy znajdziesz w [indeksie pojęć backend-engineering](../../backend-engineering/README.md#globalny-indeks-pojęć). Tutaj śledź ich konsekwencje dla opisywanego systemu.
Diagramy docelowe i warianty rozwoju czytaj razem z mapą implementacji; sam opis architektury nie oznacza, że wszystkie jej komponenty są uruchamiane w laboratorium.
<!-- reading-guide:end -->

## Zacznij tutaj

MinIO w laboratorium jest budowany z [oficjalnego commitu 0d7408fc9969](https://github.com/minio/minio/tree/0d7408fc9969caf07de6a8c3a84f9fbb10a6739e)
przez [docker/minio.Dockerfile](docker/minio.Dockerfile), ponieważ poprzedni obraz
rejestru stał się niedostępny. Testcontainers (jeśli dany test używa MinIO) i Compose
korzystają z lokalnego przepisu budowania; nie jest potrzebne logowanie do Quay.
Pierwszy build wymaga dostępu do GitHub, rejestru obrazów bazowych i modułów Go,
trwa dłużej oraz potrzebuje więcej pamięci niż samo pobranie obrazu. Warstwy
kompilacji są cache'owane przez Docker. Dla Compose użyj `docker compose build minio`
przed uruchomieniem usługi.
To przypięta wersja edukacyjna, nie rekomendacja bezpieczeństwa dla produkcji;
licencja AGPL jest dołączana do obrazu w `/licenses/minio/LICENSE`.

| Pytanie | Odpowiedź |
| --- | --- |
| Najważniejszy niezmiennik | Metadane opublikowanego pliku wskazują istniejący, zweryfikowany blob; treść w kwarantannie lub odrzucona nie może otrzymać URL-a pobrania. |
| Krytyczny przepływ | Rezerwacja quota → upload chunków → wznawialne złożenie → atomowy zapis metadanych/outboxa → skan AV → publikacja → post-processing. |
| Rekomendowany test | `mvnw.cmd test`; pełna saga i kontrakty PostgreSQL, MinIO oraz Kafka: `mvnw.cmd -Pinfrastructure-tests verify`. |
| Świadome ograniczenia | Laboratorium ma prawdziwe adaptery PostgreSQL/MinIO/Kafka, ale zewnętrzny KMS/HSM, silnik AV, wieloregionowa replikacja i automatyczne przełączenie ruchu pozostają granicami produkcyjnymi. |

Kompleksowy projekt systemu typu **Dropbox / Google Drive / OneDrive** z obsługą uploadu, downloadu, folderów, udostępniania, wersjonowania, synchronizacji, bezpieczeństwa i skalowalnego przechowywania plików.

---

## Spis treści

1. [Cel systemu](#1-cel-systemu)
2. [Wymagania funkcjonalne](#2-wymagania-funkcjonalne)
3. [Wymagania niefunkcjonalne](#3-wymagania-niefunkcjonalne)
   - [Model pojęciowy i granice systemu](#3a-model-pojęciowy-i-granice-systemu)
   - [Spójność, trwałość i dostępność](#3b-spójność-trwałość-i-dostępność)
   - [Control plane i data plane](#3c-control-plane-i-data-plane)
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
38. [Mapa teorii na kod](#38-mapa-teorii-na-kod)
39. [Capacity estimation](#39-capacity-estimation)
40. [Gwarancje i okna awarii](#40-gwarancje-i-okna-awarii)
41. [Uruchamianie i strategia testów](#41-uruchamianie-i-strategia-testów)
42. [Backup, restore i rekoncyliacja](#42-backup-restore-i-rekoncyliacja)
43. [HTTP, Range, cache i kontrakt API](#43-http-range-cache-i-kontrakt-api)
44. [Kafka, outbox i inbox](#44-kafka-outbox-i-inbox)
45. [Szyfrowanie kopertowe i rotacja kluczy](#45-szyfrowanie-kopertowe-i-rotacja-kluczy)
46. [Rekoncyliacja i bezpieczna naprawa danych](#46-rekoncyliacja-i-bezpieczna-naprawa-danych)
47. [Multi-region, HA i disaster recovery](#47-multi-region-ha-i-disaster-recovery)
48. [Metodyka testów wydajnościowych](#48-metodyka-testów-wydajnościowych)
49. [Macierz testów i wstrzykiwanie awarii](#49-macierz-testów-i-wstrzykiwanie-awarii)
50. [Granice implementacji](#50-granice-implementacji)

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

## 3A. Model pojęciowy i granice systemu

Najważniejszym krokiem w projektowaniu file storage jest rozdzielenie **logicznego pliku** od **fizycznej zawartości**. W prostym CRUD-zie oba pojęcia często są utożsamiane, ale przy wersjonowaniu, deduplikacji i współdzieleniu prowadzi to do błędów.

```text
File                  nazwa, owner, folder, ACL, status, aktualna wersja
  └── FileVersion     niezmienny snapshot zawartości i metadanych technicznych
        └── Blob      fizyczne bajty; może być współdzielony przez wiele wersji

UploadSession         tymczasowy proces dostarczenia bajtów
  └── UploadChunk     część procesu; nie jest jeszcze wersją ani plikiem
```

Z tego modelu wynikają istotne własności:

- rename zmienia `File`, a nie klucz fizycznego obiektu;
- nowa wersja nie nadpisuje starego bloba;
- skasowanie logicznego pliku nie musi od razu kasować bajtów;
- dwa pliki mogą wskazywać ten sam blob, jeżeli polityka deduplikacji na to pozwala;
- nieukończony chunk nie daje użytkownikowi pliku do pobrania;
- hash identyfikuje zawartość, ale nie zastępuje identyfikatora zasobu i kontroli ACL.

### Namespace i content store

System składa się z dwóch różnych problemów:

1. **Namespace** — drzewo folderów, nazwy, owner, sharing, wersje i soft delete. To domena transakcyjna przechowywana w Metadata DB.
2. **Content store** — niezmienne strumienie bajtów adresowane przez techniczny klucz. To domena object storage.

Namespace wymaga zapytań, constraintów i silnej spójności. Content store wymaga wysokiej trwałości, przepustowości i taniego skalowania. Próba obsłużenia obu potrzeb jednym magazynem zwykle pogarsza oba obszary.

### Tożsamość, hash i checksum

Te pojęcia pełnią różne role:

| Pojęcie | Znaczenie | Czego nie gwarantuje |
| --- | --- | --- |
| `file_id` | tożsamość logicznego zasobu | identyczności bajtów |
| `version_id` | tożsamość niezmiennego snapshotu | unikalnego fizycznego bloba |
| `object_key` | adres w konkretnym storage | integralności i własności domenowej |
| `SHA-256` | integralność/content identity | uprawnienia użytkownika |
| `ETag` storage | znacznik wersji HTTP/storage | zawsze SHA-256; przy multipart może mieć inną semantykę |

Hash klienta jest wskazówką, nie dowodem. Serwer powinien zweryfikować zawartość po złożeniu pliku, zanim wykorzysta hash do deduplikacji. W systemach wielotenantowych odpowiedź „ten hash już istnieje” może być kanałem bocznym ujawniającym obecność cudzego pliku.

## 3B. Spójność, trwałość i dostępność

Te trzy właściwości nie są synonimami:

- **spójność** mówi, jaki stan zobaczy kolejny odczyt;
- **trwałość** mówi, czy zaakceptowane dane przetrwają awarię;
- **dostępność** mówi, czy system odpowie mimo awarii zależności.

Przykładowo obiekt może być trwale zapisany w storage, ale jeszcze niewidoczny w namespace, ponieważ transakcja metadanych nie została zatwierdzona. Może też istnieć rekord DB wskazujący brakujący obiekt — to naruszenie integralności, którego nie naprawi samo retry HTTP.

### Macierz wymaganej spójności

| Operacja | Oczekiwana semantyka | Powód |
| --- | --- | --- |
| consume quota | serializacja dla użytkownika/tenanta | dwa uploady nie mogą razem przekroczyć limitu |
| zmiana ACL | read-after-write | po odebraniu dostępu kolejne wydanie URL-a musi zostać zablokowane |
| wybór aktualnej wersji | optimistic locking lub blokada | ochrona przed lost update |
| indeks wyszukiwania | eventual consistency | indeks jest projekcją możliwą do odbudowy |
| miniaturka | eventual consistency | nie jest źródłem prawdy |
| changelog klienta | monotoniczny cursor i kompletność audience | klient nie może trwale pominąć zmiany |
| outbox delivery | at-least-once | DB i broker nie współdzielą transakcji |

### Read-after-write

Po odpowiedzi `upload completed` użytkownik oczekuje co najmniej jednego z dwóch jawnych kontraktów:

- plik jest natychmiast pobieralny;
- plik istnieje, ale ma stan `QUARANTINED/PROCESSING`, a pobieranie stanie się możliwe później.

Laboratorium wybiera drugi wariant. Dzięki temu sukces finalizacji oznacza trwały zapis, ale nie omija bramki bezpieczeństwa. UI powinien pokazywać stan przetwarzania zamiast interpretować brak downloadu jako zaginięcie danych.

### Durability nie oznacza backupu

Replikacja object storage chroni głównie przed awarią nośnika lub węzła. Nie chroni automatycznie przed:

- błędem aplikacji kasującym poprawne obiekty;
- kompromitacją konta administracyjnego;
- uszkodzeniem metadanych i propagacją błędu do replik;
- błędną migracją;
- ransomware lub skasowaniem całego bucketa.

Dlatego potrzebne są wersjonowanie/immutability, niezależny backup, retencja oraz regularny restore drill. Deklarowane przez dostawcę „jedenaście dziewiątek durability” nie jest RPO ani dowodem odtwarzalności całego produktu.

### CAP w praktyce tego systemu

CAP dotyczy zachowania podczas partycji sieciowej. Dla metadanych i ACL bezpieczniej zwykle odrzucić zapis lub wydanie nowego URL-a niż zaakceptować dwa sprzeczne stany. Dla listowania, miniaturek i wyszukiwania można tolerować dane nieco starsze. Nie ma jednej etykiety „system jest CP/AP” dla wszystkich przepływów — decyzję podejmuje się per operacja i niezmiennik.

## 3C. Control plane i data plane

W skalowalnym file storage duże bajty nie powinny przechodzić tą samą ścieżką co decyzje domenowe.

```text
Control plane:
client → API → auth/ACL/quota/upload session → Metadata DB

Data plane:
client ↔ presigned URL ↔ object storage/CDN
```

Control plane odpowiada za małe, wrażliwe operacje: autoryzację, rezerwację quota, wydanie ograniczonego URL-a i zmianę stanu sesji. Data plane przenosi duże strumienie i skaluje się niezależnie.

Rozdzielenie daje:

- mniejsze zużycie socketów, pamięci i pasma instancji API;
- niezależne skalowanie transferu;
- możliwość wznowienia uploadu bez utrzymywania requestu backendowego;
- wykorzystanie CDN i natywnych mechanizmów multipart.

Ma też koszt: backend nie widzi automatycznie, czy klient rzeczywiście wysłał poprawne bajty. Potrzebne są callback/potwierdzenie chunka, `HEAD/stat`, checksum i finalizacja sterowana przez Metadata DB.

### Application chunks a natywny multipart upload

Istnieją dwa podobne, ale różne modele:

| Model | Zaleta | Koszt |
| --- | --- | --- |
| osobne obiekty `chunks/{index}` | łatwy do zrozumienia i przenośny między dostawcami | własny compose, cleanup i więcej obiektów tymczasowych |
| natywny multipart S3 | storage zarządza częściami i atomowym complete | zależność od semantyki API dostawcy, `uploadId` i minimalnych rozmiarów części |

Rozmiar chunka jest kompromisem. Zbyt mały zwiększa liczbę requestów, rekordów i kosztów. Zbyt duży podnosi koszt retry i wymagania pamięciowe klienta. Należy uwzględnić limity liczby części dostawcy, maksymalny rozmiar pliku i jakość sieci użytkownika.

### Download, Range i cache

Duże pliki powinny wspierać `Range`, aby klient mógł wznowić transfer i pobrać tylko fragment, np. przy streamingu wideo. Warstwa CDN powinna rozumieć:

- czy cache key obejmuje właściwą wersję/immutable object key;
- czy odpowiedzi prywatne nie trafiają do współdzielonego cache;
- jak długo działa URL po odebraniu ACL;
- czy `ETag` służy do walidacji cache, a nie jest błędnie traktowany jako uniwersalny SHA-256;
- jak obsłużyć `If-None-Match`, `206 Partial Content` i wiele zakresów.

Najprostsza bezpieczna strategia to niezmienny klucz wersji oraz krótko ważny podpisany URL. Purge CDN staje się wtedy potrzebny głównie przy incidentach i usuwaniu regulacyjnym, a nie po każdym rename.

### Partycjonowanie metadanych i hot users

Naturalnym kluczem shardingu jest zwykle `tenant_id` lub `owner_id`, ponieważ większość operacji dotyczy przestrzeni jednego właściciela. Zapewnia to locality dla quota i listowania folderów, ale tworzy ryzyko hot shardu dla bardzo dużego tenanta.

Alternatywy:

- hash `owner_id` — równy rozkład, słabsza kontrola geograficzna;
- `tenant_id + bucket` — rozbija dużego tenanta, komplikuje transakcje quota;
- region przypisany do tenanta — prostsza rezydencja danych, trudniejsza migracja;
- oddzielny append-only changelog — odciąża główne tabele od intensywnego sync.

Folder z milionami dzieci jest osobnym hotspotem nawet przy dobrym shardingu. Wymaga keyset pagination, stabilnego sortowania i indeksu zawierającego `parent_folder_id` oraz klucz cursora. `OFFSET` staje się coraz droższy i jest niestabilny przy równoległych zmianach.

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
GET /api/v1/sync/changes?cursor=123456&limit=500
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
GET /api/v1/sync/changes?cursor=last_seen_change_id
```

Ważne przypadki:

- klient był offline przez długi czas,
- changelog został przycięty,
- użytkownik ma bardzo dużo zmian.

Wykonywalna implementacja zapisuje osobną `change_log_audience`. Zmiana jest
widoczna właścicielowi oraz użytkownikom mającym w chwili zapisu bezpośrednie
lub odziedziczone uprawnienie. Przy cofnięciu dostępu wpis trafia do audience
jeszcze przed revocation, aby klient współdzielony mógł usunąć lokalną kopię.
Publiczny link nie jest klientem synchronizacji i nie otrzymuje changeloga.

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
- rezerwacja quota jest zwalniana; dzięki rezerwacji nie można przekroczyć limitu wieloma równoległymi sesjami.

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
POST   /api/v1/uploads
POST   /api/v1/uploads/{id}/chunks/{index}/signed-url
POST   /api/v1/uploads/{id}/chunks/{index}/complete
POST   /api/v1/uploads/{id}/complete

GET    /api/v1/files/{id}
GET    /api/v1/files/{id}/download
PATCH  /api/v1/files/{id}
DELETE /api/v1/files/{id}
POST   /api/v1/files/{id}/restore

GET    /api/v1/files/{id}/versions
GET    /api/v1/files/{id}/versions/{version_id}/download
POST   /api/v1/files/{id}/versions/{version_id}/restore

POST   /api/v1/folders
GET    /api/v1/folders/{id}/children
PATCH  /api/v1/folders/{id}
DELETE /api/v1/folders/{id}

POST   /api/v1/files/{id}/permissions
DELETE /api/v1/permissions/{id}

POST   /api/v1/files/{id}/share-link
DELETE /api/v1/share-links/{id}

GET    /api/v1/sync/changes
GET    /api/v1/search?q=...
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

---

## 38. Mapa teorii na kod

Ten projekt jest wykonywalnym laboratorium, a nie deklaracją pełnej gotowości produkcyjnej. Poniższa tabela oddziela gwarancje naprawdę egzekwowane przez kod od granic, które w realnym wdrożeniu zapewnia infrastruktura.

| Problem | Mechanizm w laboratorium | Najważniejsza gwarancja | Granica produkcyjna |
| --- | --- | --- | --- |
| duży upload | `UploadSession`, chunki i `UploadFinalizationCoordinator` | unikalny indeks chunka, deterministyczny klucz finalny, finalizacja wznawialna | natywny multipart upload dostawcy i lifecycle rules |
| quota | `storage_reserved_bytes` i blokada użytkownika | suma użycia i rezerwacji nie przekracza quota | quota organizacji, billing i limity storage po stronie dostawcy |
| publikacja | `QUARANTINED → ACTIVE/REJECTED` | żaden endpoint nie pobiera treści przed czystym skanem | skalowalny, fail-closed AV i izolowana strefa kwarantanny |
| processing | lease, `SKIP LOCKED`, retry z opóźnieniem | jeden claim na próbę; porzucone lease może przejąć inny worker | kolejka/broker, autoscaling i kontrola poison records |
| zdarzenia | transactional outbox, adapter Kafka i inbox `processed_events` | zmiana domenowa i event powstają atomowo; redelivery nie powtarza efektu konsumenta | monitoring partycji, retry topic/DLQ, schema registry i polityka replayu |
| deduplikacja | hash lock, `storage_blobs`, `ref_count` | współdzielony blob znika dopiero po ostatniej referencji | polityka tenantów, szyfrowanie i potwierdzenie opłacalności |
| download | ACL + status + `ETag`/`If-None-Match`, `Range` i HMAC/presigned URL | niezmieniony zasób zwraca 304, a poprawny pojedynczy zakres dokładnie 206 | wielozakresowe odpowiedzi, walidacja podpisu na edge i szybka revocation |
| szyfrowanie | losowy DEK, AES-GCM, opakowany DEK i wersja KEK | rotacja KEK nie wymaga ponownego szyfrowania wielkiego bloba | zewnętrzny KMS/HSM, IAM, audyt unwrap i bezpieczne cache kluczy |
| integralność | dwukierunkowy `StorageReconciliationService` | brakujący i osierocony obiekt tworzy finding; skan nigdy sam nie kasuje danych | stronicowany inventory, okres karencji i zatwierdzany runbook naprawczy |

Celowy przykład lokalny to `LoggingDomainEventPublisher`: pokazuje granicę portu publikacji, ale nie udaje trwałego brokera. Podobnie `NOOP` antivirus jest wygodny tylko w profilu developerskim; profil `production` odmawia startu z takim ustawieniem i domyślnymi sekretami.

## 39. Capacity estimation

Projektowanie zaczyna się od rzędu wielkości, nie od wyboru produktu. Przykładowy model wejściowy:

- `DAU` — liczba aktywnych użytkowników dziennie;
- `U` — średnia liczba uploadów na użytkownika dziennie;
- `S` — średni rozmiar pliku;
- `D` — średnia liczba pobrań jednego pliku;
- `R` — retencja w dniach;
- `V` — średnia liczba zachowanych wersji;
- `P` — udział ruchu przypadający na godzinę szczytu.

Z tego wynikają przybliżenia:

```text
upload_bytes/day    = DAU × U × S
stored_bytes        = upload_bytes/day × R × V × (1 - dedupe_ratio)
download_bytes/day  = upload_bytes/day × D
peak_upload_RPS     = DAU × U × P / 3600
peak_download_RPS   = DAU × U × D × P / 3600
metadata_rows/year  = DAU × U × 365 × V
```

Przykład ćwiczeniowy: `1 000 000 DAU`, `0,2 uploadu/dzień`, średnio `8 MiB`, trzy pobrania i `20%` ruchu w godzinie szczytu daje około `1,53 TiB` nowych danych dziennie, `11 uploadów/s` oraz `33 downloady/s` w szczycie. Transfer i storage dominują nad samą liczbą requestów. Wyniku nie należy traktować jako prognozy — trzeba wykonać analizę wrażliwości dla p50/p95 rozmiaru, retencji, wersji, cache hit ratio i ruchu między regionami.

Capacity plan powinien osobno uwzględnić:

- tymczasowe chunki oraz ich maksymalny czas życia;
- narzut wersjonowania i miniaturek;
- replikację oraz backup;
- egress do użytkownika i między regionami;
- liczbę połączeń PostgreSQL, a nie tylko CPU aplikacji;
- przepustowość AV i workerów, aby kolejka kwarantanny nie rosła bez ograniczeń.

## 40. Gwarancje i okna awarii

PostgreSQL i object storage nie tworzą jednej transakcji ACID. Implementacja stosuje sagę:

1. krótka transakcja blokuje sesję i przełącza ją w `ASSEMBLING`;
2. poza transakcją powstaje finalny obiekt o deterministycznym kluczu;
3. aplikacja weryfikuje SHA-256;
4. druga transakcja tworzy metadane w kwarantannie, wersję, outbox i job AV oraz konsumuje rezerwację quota;
5. dopiero po commicie usuwane są chunki.

Jeżeli proces padnie po kroku 2, ponowienie używa tego samego klucza. Jeżeli cleanup po kroku 4 zawiedzie, pozostają bezpieczne do usunięcia chunki tymczasowe. Callback rollbacku ogranicza orphan blobs w klasycznym uploadzie, ale nagłe zatrzymanie procesu nadal wymaga rekoncyliacji.

Outbox i workerzy zapewniają **at-least-once**, nie exactly-once. Claim z `FOR UPDATE SKIP LOCKED` ogranicza równoległe wykonanie, lecz awaria po efekcie zewnętrznym i przed oznaczeniem sukcesu powoduje redelivery. Dlatego adapter brokera i konsumenci muszą używać `eventId` jako klucza idempotencji.

Kolejność processingu nie bazuje na przypadkowym FIFO. Najpierw powstaje wyłącznie job AV. Dopiero czysty wynik publikuje plik i tworzy thumbnail/dedupe jobs dla konkretnej wersji. Stary job nie może zmienić nowej wersji, ponieważ każdy job przechowuje `version_id`.

## 41. Uruchamianie i strategia testów

Wymagane są Java 21, Docker oraz Docker Compose. Lokalna infrastruktura:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Szybki build nie uruchamia kontenerów:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Kontrakty infrastrukturalne są jawnie wydzielone:

```powershell
.\mvnw.cmd -Pinfrastructure-tests verify
```

| Test | Czego dowodzi |
| --- | --- |
| `AppUserQuotaTest` | rezerwacje blokują oversubscription przed finalizacją |
| `UploadSessionTest` | sesja nie kończy się bez wszystkich unikalnych chunków |
| `FilePublicationStateTest` | kwarantanna i odrzucenie blokują download |
| `ProcessingJobStateTest` | retry ma backoff i terminalny limit prób |
| `OutboxEventStateTest` | redelivery nie zawyża licznika prób i wraca do stanu oczekującego |
| `StorageBlobRefCountTest` | refcount współdzielonego bloba nie może spaść poniżej zera |
| `CdnServiceTest` | CDN URL zawiera kryptograficzny podpis ścieżki i expiry |
| `PostgreSqlUploadSchemaInfrastructureTest` | prawdziwy PostgreSQL egzekwuje unikalność chunka i migracje |
| `MinioStorageInfrastructureTest` | prawdziwe MinIO zachowuje kolejność compose, rozmiar i lifecycle obiektu |
| `UploadFinalizationFailureTest` | retry po awarii między zapisem storage i commitem używa deterministycznego klucza |
| `FileDownloadHttpSemanticsTest` | `Range`, `Content-Range`, `ETag` i 304 zachowują semantykę HTTP |
| `EnvelopeEncryptionServiceTest` | zmiana KEK przeopakowuje DEK, ale nie zmienia ciphertextu pliku |
| `KafkaOutboxInfrastructureTest` | prawdziwy broker zachowuje klucz agregatu i nagłówek `eventId` |
| `UploadSagaInfrastructureTest` | cała saga, AV gate, outbox, ACL sync, inbox, OpenAPI i rekoncyliacja działają na PostgreSQL/MinIO |

Test jednostkowy nie zastępuje kontraktu SDK/storage, a Testcontainers nie zastępuje testu obciążeniowego ani game day. Każdy test ma dowodzić nazwanej gwarancji, nie tylko zwiększać coverage.

## 42. Backup, restore i rekoncyliacja

Manifest tworzony przez `BackupService` **nie jest backupem**. Pełny punkt odtworzenia musi łączyć:

- spójny `pg_dump` metadanych wraz z wersją migracji Flyway;
- wersjonowany snapshot/replication object storage;
- zaszyfrowaną kopię konfiguracji i referencji do kluczy KMS;
- manifest zawierający timestamp, checksumy, zakres danych i identyfikator snapshotu;
- regularny restore drill do izolowanego środowiska.

Kolejność restore: odtwórz bazę, uruchom migracje tylko do zapisanej wersji, odtwórz obiekty, uruchom rekoncyliację, a dopiero po zerowej liczbie krytycznych findingów dopuść ruch. `StorageReconciliationService` nie usuwa automatycznie orphanów: świeży obiekt może znajdować się pomiędzy zapisem storage i commitem DB. Fizyczne usunięcie wymaga okresu karencji, ponownego skanu i audytowalnej decyzji.

RPO określa dopuszczalną utratę zmian, RTO — dopuszczalny czas niedostępności. Obie wartości trzeba zmierzyć w restore drill; sam fakt, że backup job ma status `COMPLETED`, nie dowodzi możliwości odtworzenia.

Skrypt `scripts/restore-drill.ps1` odtwarza przekazany `pg_dump` do nowej bazy z
bezpiecznikiem nazwy `_restore_drill`, kopiuje snapshot obiektów do izolowanego
MinIO i wykonuje podstawowe kontrole liczby rekordów oraz inventory. Nie kieruje
ruchu na odtworzone środowisko i nie kasuje istniejących danych. Po nim nadal
trzeba uruchomić rekoncyliację oraz testy biznesowe:

```powershell
.\scripts\restore-drill.ps1 -DatabaseDump .\backup.dump -ObjectSnapshot .\object-snapshot
```

## 43. HTTP, Range, cache i kontrakt API

Download dużego pliku nie jest zwykłym zwróceniem `byte[]`. Klient mobilny,
odtwarzacz i przeglądarka muszą móc wznowić przerwany transfer. Dla nagłówka
`Range: bytes=START-END` serwer zwraca `206 Partial Content`, `Content-Range`,
`Accept-Ranges: bytes` oraz długość wybranego fragmentu. Zakres spoza obiektu
kończy się `416` wraz z `Content-Range: bytes */TOTAL`. Implementacja celowo
obsługuje jeden zakres; multipart/byteranges jest osobną komplikacją i zwykle
powinien zostać delegowany do object storage lub CDN.

`ETag` opisuje konkretną reprezentację, a nie zawsze biznesową wersję pliku.
W tym laboratorium jest oparty na SHA-256 treści. `If-None-Match` umożliwia
warunkowy GET i odpowiedź `304 Not Modified` bez body. Nie należy utożsamiać
go z `If-Match`, który chroni komendę modyfikującą przed lost update. ETag S3
nie jest uniwersalnym MD5 — przy multipart uploadie i szyfrowaniu jego znaczenie
zależy od dostawcy.

Cache jest bezpieczny, gdy klucz zawiera niezmienną wersję/klucz obiektu.
Metadane i ACL mają krótsze TTL niż immutable content. Cofnięcie uprawnienia nie
unieważnia już pobranego pliku ani podpisanego URL-a, dlatego dane wrażliwe
wymagają krótkiego TTL, ewentualnie autoryzacji na edge i rotacji klucza obiektu.

Springdoc publikuje wykonywalny kontrakt pod `/api-docs` oraz UI pod
`/swagger-ui.html`. Błędy mają jednolity format `ProblemDetail` zgodny z RFC 9457:
status, tytuł, szczegół oraz URI instancji. Test infrastrukturalny sprawdza
obecność krytycznych operacji upload/download i bearer auth, dzięki czemu ich
przypadkowe usunięcie staje się wykrywalną zmianą kontraktu. W produkcji warto
porównywać wygenerowaną specyfikację z wersjonowanym baseline i klasyfikować
breaking changes przed wdrożeniem.

## 44. Kafka, outbox i inbox

Transakcja PostgreSQL nie może atomowo commitować danych i publikacji do Kafki.
Outbox zapisuje zmianę domenową oraz rekord zdarzenia w jednej transakcji. Osobny
relay claimuje rekordy przez lease/`SKIP LOCKED`, publikuje je i dopiero po ACK
oznacza jako wysłane. Awaria po ACK, ale przed aktualizacją outboxa, publikuje
zdarzenie ponownie. To poprawne **at-least-once**, a nie exactly-once.

Adapter Kafka używa stabilnego klucza `aggregateType:aggregateId`. Kafka zachowuje
kolejność tylko wewnątrz partycji, więc zdarzenia jednego agregatu muszą mieć ten
sam klucz. `acks=all` oraz idempotent producer ograniczają utratę i duplikaty po
stronie producenta, ale nie eliminują redelivery całego przepływu. `eventId` jest
przenoszony w nagłówku i pozostaje tożsamością logicznego zdarzenia podczas retry.

Konsument realizuje inbox pattern: unikalny marker `processed_events` oraz efekt
na projekcji muszą zostać zapisane w **tej samej lokalnej transakcji**. Offset
można commitować dopiero po jej sukcesie. Jeżeli proces padnie wcześniej, broker
dostarczy rekord ponownie, a constraint markera zamieni duplikat w no-op. Samo
sprawdzenie `exists` bez constraintu jest podatne na wyścig; demonstracja ma jeden
handler, a produkcyjny wariant powinien obsłużyć konflikt unikalności.

Retry topic powinien zachować `eventId`, pierwotny topic/partition/offset, typ
błędu i numer próby. Błąd przejściowy dostaje backoff z jitterem, błąd trwały
trafia do DLQ. DLQ nie jest archiwum do ignorowania: wymaga alarmu, diagnozy,
narzędzia replay oraz ochrony przed ponownym wykonaniem efektu biznesowego.

## 45. Szyfrowanie kopertowe i rotacja kluczy

TLS chroni dane w tranzycie, a server-side encryption dostawcy chroni nośniki.
Nie rozwiązuje to każdego modelu zagrożeń: administrator storage lub błędne IAM
mogą nadal uzyskać dostęp. Envelope encryption rozdziela role:

1. losowy **DEK** (data encryption key) szyfruje treść przez AES-256-GCM;
2. **KEK** (key encryption key) z KMS/HSM opakowuje mały DEK;
3. przy blobie zapisuje się ciphertext, IV/tag, encrypted DEK, `keyId` i wersję;
4. plaintext DEK żyje krótko w pamięci i nigdy nie trafia do logów ani bazy.

AES-GCM zapewnia poufność i integralność, ale ponowne użycie IV z tym samym
kluczem jest katastrofalne. Laboratorium generuje losowy 96-bitowy IV. W realnym
systemie trzeba ustalić format ciphertextu, AAD wiążące blob z tenantem/wersją,
limit wielkości segmentu oraz zachowanie dla streamingu; wielki plik zwykle jest
szyfrowany segmentami, aby zachować Range read.

Rotacja KEK odszyfrowuje i ponownie opakowuje tylko DEK. Nie kopiuje terabajtów
bloba, więc może być wykonana online. Kompromitacja DEK wymaga ponownego
szyfrowania danych. Usunięcie KEK może realizować crypto-shredding, ale dopiero
po uwzględnieniu backupów, retencji prawnej i replik. Produkcyjny KMS wymaga
minimalnego IAM, audytu każdego unwrap, limitów, cache z krótkim TTL i procedury
na niedostępność KMS.

Deduplikacja po plaintext hash ujawnia równość treści i kłóci się z losowym
szyfrowaniem. Bezpieczna polityka zwykle ogranicza deduplikację do tenanta albo
z niej rezygnuje dla danych wrażliwych. Convergent encryption umożliwia globalną
deduplikację, lecz otwiera ataki potwierdzające posiadanie znanego pliku.

## 46. Rekoncyliacja i bezpieczna naprawa danych

Rekoncyliacja musi działać w obu kierunkach:

- **DB → storage** wykrywa metadane wskazujące brakujący blob;
- **storage → DB** wykrywa obiekt bez referencji wersji, chunka lub wyniku joba.

Drugi przypadek nie oznacza od razu odpadu. Obiekt mógł powstać po pierwszym
kroku sagi, gdy commit jeszcze trwa, albo inventory może być opóźnione. Dlatego
skan stosuje grace period, zapisuje trwały finding i nigdy nie usuwa automatycznie.
Kasowanie wymaga ponownego skanu, potwierdzenia braku aktywnej sesji/lease,
retencji, audytu operatora i najlepiej kwarantanny przed fizycznym delete.

Skan produkcyjny musi być stronicowany, wznawialny i ograniczony przepustowością.
Dla miliardów obiektów korzysta się z inventory dostawcy zamiast listowania online.
Checkpoint określa zakres skanu, a metryki rozdzielają nowe findings, naprawione,
zaakceptowane wyjątki i wiek najstarszego problemu. Naprawa referencji jest inną
operacją niż naprawa danych: brakujący blob można odtworzyć z repliki/backupu,
ale nie wolno tworzyć pustego obiektu tylko po to, aby uciszyć kontrolę.

## 47. Multi-region, HA i disaster recovery

High availability obsługuje awarię komponentu w bieżącym regionie; disaster
recovery odtwarza usługę po utracie całej domeny awarii lub danych. Replika DB,
replikacja obiektów i backup mają inne cele. Replikacja szybko kopiuje również
przypadkowe usunięcie, a backup daje historyczny punkt odtworzenia.

Najprostszy model ma jeden region zapisu i region pasywny. Metadane mają jednego
lidera, object storage replikuje wersje asynchronicznie, a failover jest dozwolony
dopiero po ocenie replication lag. RPO wynika z ostatniego wspólnego punktu DB i
storage, nie z każdej metryki osobno. RTO obejmuje wykrycie, decyzję, przełączenie
DNS/routingu, rozgrzanie zależności, rekoncyliację i walidację biznesową.

Active-active utrudnia rename, unikalność nazw, quota i wersjonowanie. Opcje to
home region per tenant, globalnie konsystentna baza albo jawne reguły konfliktu.
Nie wystarczy last-write-wins oparte na zegarze ściennym: clock skew może utracić
poprawną zmianę. Identyfikatory muszą być globalnie unikalne, a change log mieć
porządek wystarczający dla konkretnego użytkownika/namespace, niekoniecznie dla
całego globu.

Restore drill jest testem, nie dokumentem. Powinien cyklicznie odtworzyć izolowane
środowisko, zmierzyć RPO/RTO, zweryfikować checksumy i krytyczne przepływy oraz
pozostawić raport. Failover bez failbacku i rotacji sekretów pozostawia operację
niedokończoną.

## 48. Metodyka testów wydajnościowych

Przed pomiarem trzeba zapisać workload: rozkład wielkości plików, read/write
ratio, odsetek Range reads, cache hit ratio, liczbę tenantów i hot keys. Średnia
ukrywa ogon; decyzje opiera się na p50/p95/p99, error rate, throughput oraz
saturation CPU, puli połączeń, socketów, DB, storage i kolejek.

- **load** potwierdza SLO przy oczekiwanym ruchu;
- **stress** znajduje punkt nasycenia i charakter degradacji;
- **spike** sprawdza nagły skok, kolejki i autoscaling;
- **soak** wykrywa wycieki, fragmentację i rosnący backlog.

Model zamknięty wysyła kolejne żądanie dopiero po odpowiedzi i przy spowolnieniu
sam redukuje obciążenie. To coordinated omission. Skrypt `load-tests/file-storage.js`
używa otwartego `arrival-rate`, taguje metadata read i Range download oraz ma
progi p95/p99 i error rate z abort. Wynik jest ważny tylko razem z konfiguracją
środowiska oraz dashboardem zależności; test z laptopa nie wyznacza capacity
produkcyjnego regionu.

## 49. Macierz testów i wstrzykiwanie awarii

| Warstwa | Narzędzie | Dowodzona właściwość | Czego nie dowodzi |
| --- | --- | --- | --- |
| model domenowy | szybki test jednostkowy | przejścia stanów i niezmienniki | zachowanie SQL/storage |
| adapter | Testcontainers | constrainty, locki, Range i kontrakt SDK | zachowanie klastra pod awarią |
| przekrój | PostgreSQL + MinIO | saga od quota do publikacji | skalowanie i region failover |
| broker | prawdziwa Kafka | klucz partycji, nagłówki i redelivery boundary | pełna operacja klastra |
| fault injection | kontrolowany wyjątek/restart | idempotencja konkretnego okna awarii | wszystkie przeploty |
| load/stress/soak | k6 + metryki | SLO i punkt nasycenia | poprawność biznesowa sama w sobie |
| restore drill | izolowany restore | osiągalne RPO/RTO i kompletność kopii | automatyczny failover produkcji |

Najważniejsze okna awarii to: po zapisie bloba przed commitem DB, po commicie
przed cleanupem chunków, po efekcie workera przed ACK, po publikacji brokera przed
oznaczeniem outboxa oraz po efekcie konsumenta przed commitem offsetu. Test nie
powinien tylko oczekiwać sukcesu requestu; po retry sprawdza niezmiennik: jeden
plik logiczny, poprawne quota/refcount, brak publikacji przed AV i najwyżej jeden
efekt konsumenta.

## 50. Granice implementacji

Laboratorium implementuje: sesje/chunki i wznawialną sagę, quota reservation,
kwarantannę AV, wersje, ACL i sync po udostępnieniu, outbox z lease, adapter Kafka,
inbox, deduplikację/refcount, Range/ETag, OpenAPI/Problem Details, envelope
encryption, dwukierunkową rekoncyliację, metryki, restore drill i profile testów.

Celowo uproszczone są: jednowęzłowe MinIO/PostgreSQL/Kafka w testach, NOOP AV w
development, lokalne wyprowadzenie KEK, jeden zakres HTTP, ograniczony inventory,
brak schema registry oraz ręczny start restore drill. Niezaimplementowane granice
produkcyjne to natywny multipart upload z bezpośrednim ruchem klient–storage,
rzeczywisty KMS/HSM i AV, edge authorization, multi-region replication/failover,
automatyczna naprawa zatwierdzona polityką oraz pełny pipeline observability.

To rozróżnienie jest częścią projektu systemu: diagram docelowy pokazuje, gdzie
powinna znaleźć się odpowiedzialność, a kod dowodzi najważniejszych gwarancji
bez udawania, że lokalny proces jest globalnym systemem storage.
