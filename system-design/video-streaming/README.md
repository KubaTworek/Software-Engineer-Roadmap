# Video Streaming Platform — System Design

Kompleksowy projekt architektury systemu do streamingu wideo typu **VOD + opcjonalnie live streaming**. Dokument zakłada platformę podobną do Netflix/YouTube pod względem głównych komponentów technicznych: upload, transkodowanie, packaging, CDN, playback, autoryzacja, wyszukiwanie, rekomendacje i analityka jakości odtwarzania.

---

## 1. Cel systemu

System ma umożliwiać:

1. Upload materiałów wideo.
2. Transkodowanie do wielu jakości i formatów.
3. Przechowywanie plików wideo i metadanych.
4. Streaming VOD oraz opcjonalnie live.
5. Adaptacyjne odtwarzanie w zależności od jakości sieci.
6. Autoryzację dostępu do treści.
7. Rekomendacje, wyszukiwanie i historię oglądania.
8. Skalowanie do dużej liczby użytkowników.
9. Monitoring jakości streamingu i błędów.

---

## 2. Założenia produktowe

### Typy treści

System obsługuje:

- VOD, czyli filmy, seriale, kursy, nagrania.
- Live streaming, na przykład wydarzenia sportowe, webinary, koncerty.
- Krótkie klipy, opcjonalnie podobne do Shorts/Reels.
- Treści darmowe, płatne i premium.

### Klienci

Aplikacje klienckie:

- Web.
- iOS.
- Android.
- Smart TV.
- Ewentualnie aplikacje embedded, np. dekodery TV.

### Format odtwarzania

Rekomendowane formaty:

- **HLS** jako podstawowy format, ponieważ ma bardzo szerokie wsparcie.
- **DASH** opcjonalnie, szczególnie dla Android TV, web i bardziej zaawansowanych integracji.
- **CMAF** jako wspólny format segmentów, żeby ograniczyć duplikację danych między HLS i DASH.

---

## 3. Wymagania funkcjonalne

### Dla użytkownika końcowego

Użytkownik może:

- Rejestrować się i logować.
- Przeglądać katalog treści.
- Wyszukiwać filmy.
- Oglądać film w różnych jakościach.
- Kontynuować oglądanie od ostatniej pozycji.
- Dodawać treści do listy.
- Oceniać lub lajkować treści.
- Pobierać wideo offline, opcjonalnie.
- Oglądać live streamy, opcjonalnie.
- Korzystać z napisów i wielu ścieżek audio.

### Dla twórcy/admina

Admin lub twórca może:

- Uploadować wideo.
- Uzupełniać metadane.
- Dodawać miniatury.
- Dodawać napisy.
- Publikować lub wycofywać treści.
- Ustawiać prawa dostępu.
- Analizować statystyki oglądalności.

---

## 4. Wymagania niefunkcjonalne

### Skalowalność

System powinien obsługiwać:

- Dużą liczbę użytkowników równoczesnych.
- Duże pliki wideo.
- Duży ruch odczytowy.
- Nierównomierne piki ruchu, np. premiera nowego odcinka.

### Dostępność

Proponowane cele:

- API: 99.9% lub więcej.
- Playback: 99.99% dla statycznych segmentów przez CDN.
- Transkodowanie może działać asynchronicznie i mieć niższy priorytet niż playback.

### Opóźnienia

Dla VOD:

- Start playbacku: najlepiej poniżej 2 sekund.
- Buforowanie: minimalne.
- Segmenty dostarczane z CDN.

Dla live:

- Standard live latency: 10–30 sekund.
- Low latency live: 2–5 sekund, jeśli wymagane.
- Ultra low latency poniżej 1 sekundy jest osobnym, trudniejszym problemem i wymaga WebRTC albo specjalistycznej infrastruktury.

### Bezpieczeństwo

System powinien obsługiwać:

- Autoryzację dostępu do treści.
- Podpisywane URL-e.
- Tokeny playbacku.
- DRM dla treści premium.
- Ochronę przed hotlinkingiem.
- Rate limiting.
- Audyt operacji administracyjnych.

---

## 5. Szacowanie skali

Przykładowe założenia:

- 10 mln zarejestrowanych użytkowników.
- 1 mln DAU.
- 100 tys. równoczesnych użytkowników w godzinach szczytu.
- Średnia sesja: 30 minut.
- Średni bitrate: 3 Mbps.
- 50 tys. godzin materiału VOD.
- 10 tys. nowych uploadów dziennie.

### Ruch sieciowy

Dla 100 tys. równoczesnych użytkowników przy 3 Mbps:

```text
100 000 × 3 Mbps = 300 000 Mbps = 300 Gbps
```

To ruch, którego **nie powinien obsługiwać backend aplikacyjny**. Musi iść przez CDN.

### Storage

Dla 50 tys. godzin materiału, po transkodowaniu do wielu jakości:

Załóżmy średnio 3 GB na godzinę dla wszystkich wariantów jakości po kompresji.

```text
50 000 h × 3 GB = 150 TB
```

Z replikacją, wersjami, miniaturami, napisami i plikami źródłowymi realnie może być:

```text
300–500 TB+
```

---

## 6. Architektura wysokiego poziomu

```text
                  ┌──────────────────────┐
                  │      Client Apps      │
                  │ Web / Mobile / TV     │
                  └──────────┬───────────┘
                             │
                             ▼
                    ┌────────────────┐
                    │      CDN       │
                    │ Video Segments │
                    └───────┬────────┘
                            │
                            ▼
                    ┌────────────────┐
                    │ Object Storage │
                    │ S3/GCS/Blob    │
                    └────────────────┘


Client ───────► API Gateway ───────► Auth Service
                     │              ► Catalog Service
                     │              ► Playback Service
                     │              ► User Service
                     │              ► Watch History Service
                     │              ► Search Service
                     │              ► Recommendation Service
                     │
                     ▼
              Event Streaming
              Kafka/PubSub/Kinesis
                     │
                     ▼
              Analytics / ML / Monitoring


Uploader/Admin ─► Upload Service ─► Raw Video Storage
                                      │
                                      ▼
                              Transcoding Pipeline
                                      │
                                      ▼
                              Packaged HLS/DASH
                                      │
                                      ▼
                              Origin Storage + CDN
```

---

## 7. Główne komponenty systemu

### 7.1 API Gateway

Odpowiada za:

- Routing requestów.
- TLS termination.
- Rate limiting.
- Walidację tokenów.
- WAF.
- Logowanie requestów.
- Throttling per użytkownik/IP.

Gateway nie powinien obsługiwać plików wideo. Playback segmentów powinien iść przez CDN.

### 7.2 Auth Service

Odpowiada za:

- Logowanie.
- Rejestrację.
- Tokeny JWT lub opaque tokens.
- Refresh tokeny.
- Integrację z OAuth.
- Role: user, creator, admin.
- Subskrypcje i uprawnienia.

Dla playbacku Auth Service nie powinien być pytany przy każdym segmencie wideo. Lepszy model:

1. Klient prosi Playback Service o manifest.
2. Playback Service sprawdza uprawnienia.
3. System generuje krótkotrwały signed URL albo signed cookie.
4. Segmenty idą bezpośrednio przez CDN.

### 7.3 Catalog Service

Przechowuje i udostępnia metadane treści:

- Tytuł.
- Opis.
- Kategoria.
- Gatunki.
- Język.
- Kraj.
- Czas trwania.
- Obsada.
- Status publikacji.
- Rating wiekowy.
- Miniatury.
- Dostępne jakości.
- Dostępne napisy i ścieżki audio.

Ten serwis jest intensywnie czytany, więc powinien mieć cache.

### 7.4 Upload Service

Odpowiada za upload plików źródłowych.

Ważne: duże pliki nie powinny przechodzić przez backend aplikacyjny.

Lepszy flow:

```text
Client/Admin ─► Upload Service ─► Signed upload URL
Client/Admin ─► Object Storage
Object Storage ─► Event ─► Transcoding Pipeline
```

Upload Service generuje pre-signed URL do object storage. Klient wysyła plik bezpośrednio do storage.

Dla dużych plików trzeba obsłużyć:

- Multipart upload.
- Resume upload.
- Walidację checksum.
- Limit rozmiaru.
- Skanowanie malware.
- Weryfikację formatu.

### 7.5 Transcoding Service

To jeden z najważniejszych komponentów.

Po uploadzie oryginalny plik trafia do kolejki, a worker transkoduje go do wielu jakości.

Przykładowe profile:

| Jakość | Rozdzielczość | Bitrate |
|---|---:|---:|
| 240p | 426×240 | 300–500 Kbps |
| 360p | 640×360 | 700–1000 Kbps |
| 480p | 854×480 | 1–1.5 Mbps |
| 720p | 1280×720 | 2.5–4 Mbps |
| 1080p | 1920×1080 | 5–8 Mbps |
| 4K | 3840×2160 | 15–25 Mbps |

Pipeline:

```text
Raw Video
   │
   ▼
Validation
   │
   ▼
Transcoding
   │
   ├── 240p
   ├── 360p
   ├── 480p
   ├── 720p
   ├── 1080p
   └── 4K
   │
   ▼
Packaging HLS/DASH
   │
   ▼
Generate Manifest
   │
   ▼
Store Segments
   │
   ▼
Update Catalog
```

Technologie:

- FFmpeg dla MVP.
- AWS MediaConvert / GCP Transcoder / Azure Media Services dla managed approach.
- Kubernetes jobs dla własnej infrastruktury.
- GPU acceleration opcjonalnie, ale często CPU jest tańszy i prostszy dla typowego VOD.

### 7.6 Packaging Service

Transkodowane wideo trzeba pociąć na segmenty i wygenerować manifest.

Dla HLS:

```text
master.m3u8
720p/playlist.m3u8
720p/segment_0001.ts lub .m4s
720p/segment_0002.ts lub .m4s
...
1080p/playlist.m3u8
1080p/segment_0001.ts lub .m4s
...
```

Dla DASH:

```text
manifest.mpd
video_720p/init.mp4
video_720p/segment_0001.m4s
...
```

Rekomendacja:

- Używać segmentów 2–6 sekund.
- Dla VOD zwykle 4–6 sekund.
- Dla low-latency live krótsze segmenty lub chunked CMAF.

### 7.7 Playback Service

Playback Service nie streamuje samego wideo. On wydaje klientowi informacje potrzebne do odtwarzania.

Przykładowy flow:

```text
Client ─► Playback Service: /playback/videos/{videoId}
Playback Service:
  - sprawdza auth
  - sprawdza subskrypcję/licencję
  - sprawdza region
  - generuje signed manifest URL
  - zwraca URL do HLS/DASH
Client ─► CDN: manifest.m3u8
Client ─► CDN: segmenty wideo
```

Odpowiedź Playback Service:

```json
{
  "videoId": "vid_123",
  "playbackUrl": "https://cdn.example.com/videos/vid_123/master.m3u8?token=...",
  "drm": {
    "enabled": true,
    "licenseUrl": "https://api.example.com/drm/license"
  },
  "subtitles": [
    {
      "language": "pl",
      "url": "https://cdn.example.com/videos/vid_123/subtitles/pl.vtt"
    }
  ],
  "expiresAt": "2026-06-05T12:30:00Z"
}
```

---

## 8. Adaptacyjny streaming

System powinien używać Adaptive Bitrate Streaming.

Zasada:

- Klient pobiera manifest.
- Manifest zawiera dostępne jakości.
- Player sam wybiera jakość na podstawie:
  - przepustowości sieci,
  - wielkości bufora,
  - CPU urządzenia,
  - rozdzielczości ekranu.

Przykładowy manifest master HLS:

```text
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
360p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
720p/playlist.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
1080p/playlist.m3u8
```

To kluczowe, bo backend nie powinien ręcznie decydować o jakości każdego segmentu.

---

## 9. CDN i origin

### CDN

CDN jest krytyczny, bo większość ruchu to statyczne segmenty wideo.

CDN odpowiada za:

- Cache segmentów blisko użytkownika.
- Redukcję obciążenia origin storage.
- Lepszą latencję.
- Ochronę przed skokami ruchu.
- Signed URLs/signed cookies.
- Geo-blocking.

### Origin

Origin to object storage, np.:

- Amazon S3.
- Google Cloud Storage.
- Azure Blob Storage.
- MinIO w rozwiązaniu self-hosted.

Struktura storage:

```text
/videos/{videoId}/raw/source.mp4
/videos/{videoId}/hls/master.m3u8
/videos/{videoId}/hls/360p/playlist.m3u8
/videos/{videoId}/hls/360p/segment_0001.m4s
/videos/{videoId}/hls/720p/playlist.m3u8
/videos/{videoId}/dash/manifest.mpd
/videos/{videoId}/subtitles/pl.vtt
/videos/{videoId}/thumbnails/thumb_001.jpg
```

---

## 10. Bazy danych

### 10.1 Relacyjna baza danych

Dobra dla danych spójnych i transakcyjnych.

Przykład: PostgreSQL.

Przechowuje:

- Użytkowników.
- Subskrypcje.
- Uprawnienia.
- Metadane filmów.
- Status uploadu.
- Status transkodowania.
- Relacje serial/sezon/odcinek.
- Dane billingowe, jeżeli są po stronie systemu.

### 10.2 NoSQL

Dobra dla dużych wolumenów eventów użytkownika.

Przykład: DynamoDB, Cassandra, Bigtable.

Przechowuje:

- Watch history.
- Pozycję odtwarzania.
- Like/dislike.
- Ostatnio oglądane.
- Eventy interakcji.

### 10.3 Search index

Przykład: Elasticsearch/OpenSearch/Meilisearch.

Przechowuje indeks wyszukiwania:

- Tytuły.
- Opisy.
- Tagi.
- Kategorie.
- Osoby.
- Pełnotekstowe wyszukiwanie.

### 10.4 Cache

Przykład: Redis/Memcached.

Cache dla:

- Sesji.
- Tokenów playbacku.
- Metadanych katalogu.
- Popularnych list.
- Feature flags.
- Rate limitingu.

### 10.5 Data warehouse

Przykład: BigQuery, Snowflake, Redshift.

Do:

- Analityki.
- Raportowania.
- ML.
- Rekomendacji.
- Analizy QoE.

---

## 11. Model danych

### Users

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT,
  status TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### Videos

```sql
CREATE TABLE videos (
  id UUID PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT,
  duration_seconds INT,
  status TEXT NOT NULL,
  visibility TEXT NOT NULL,
  owner_id UUID,
  age_rating TEXT,
  created_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP
);
```

### Video Assets

```sql
CREATE TABLE video_assets (
  id UUID PRIMARY KEY,
  video_id UUID NOT NULL REFERENCES videos(id),
  asset_type TEXT NOT NULL,
  codec TEXT,
  container TEXT,
  resolution TEXT,
  bitrate INT,
  storage_url TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
```

### Transcoding Jobs

```sql
CREATE TABLE transcoding_jobs (
  id UUID PRIMARY KEY,
  video_id UUID NOT NULL REFERENCES videos(id),
  status TEXT NOT NULL,
  error_message TEXT,
  attempts INT DEFAULT 0,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### Watch Progress

Dla wysokiej skali lepiej NoSQL.

Klucz:

```text
PK: userId
SK: videoId
```

Wartość:

```json
{
  "userId": "user_123",
  "videoId": "vid_456",
  "positionSeconds": 1842,
  "durationSeconds": 3600,
  "updatedAt": "2026-06-05T12:00:00Z"
}
```

---

## 12. API

### Auth

```http
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/logout
```

### Catalog

```http
GET /videos
GET /videos/{videoId}
GET /series/{seriesId}
GET /categories/{categoryId}/videos
```

### Search

```http
GET /search?q=matrix
```

### Upload

```http
POST /uploads
```

Request:

```json
{
  "filename": "movie.mp4",
  "contentType": "video/mp4",
  "sizeBytes": 5320000000
}
```

Response:

```json
{
  "uploadId": "upl_123",
  "uploadUrl": "https://storage.example.com/signed-upload-url",
  "expiresAt": "2026-06-05T12:30:00Z"
}
```

### Complete Upload

```http
POST /uploads/{uploadId}/complete
```

### Playback

```http
GET /playback/videos/{videoId}
```

Response:

```json
{
  "playbackUrl": "https://cdn.example.com/videos/vid_123/hls/master.m3u8?sig=...",
  "format": "HLS",
  "expiresAt": "2026-06-05T12:30:00Z"
}
```

### Watch Progress

```http
PUT /users/me/watch-progress/{videoId}
GET /users/me/watch-progress/{videoId}
```

Request:

```json
{
  "positionSeconds": 1234
}
```

---

## 13. Upload i transkodowanie — szczegółowy flow

```text
1. Admin tworzy obiekt video w Catalog Service.
2. Upload Service generuje signed upload URL.
3. Klient uploaduje raw video bezpośrednio do object storage.
4. Object storage emituje event: RawVideoUploaded.
5. Event trafia do kolejki.
6. Transcoding Orchestrator tworzy job.
7. Workery pobierają raw video.
8. Workery generują kilka jakości.
9. Packaging Service tworzy HLS/DASH.
10. Segmenty trafiają do origin storage.
11. Miniatury i napisy są generowane lub walidowane.
12. Catalog Service aktualizuje status video na READY.
13. CDN może zostać wstępnie rozgrzany dla popularnej treści.
```

Statusy filmu:

```text
DRAFT
UPLOADING
UPLOADED
PROCESSING
READY
PUBLISHED
FAILED
ARCHIVED
```

---

## 14. Odtwarzanie — szczegółowy flow

```text
1. User otwiera stronę filmu.
2. Client pobiera metadane z Catalog Service.
3. User klika Play.
4. Client woła Playback Service.
5. Playback Service sprawdza:
   - czy user jest zalogowany,
   - czy ma subskrypcję,
   - czy treść jest dostępna w regionie,
   - czy treść jest opublikowana.
6. Playback Service zwraca signed manifest URL.
7. Client pobiera manifest z CDN.
8. Player pobiera segmenty z CDN.
9. Client cyklicznie wysyła watch progress.
10. Client wysyła eventy QoE: startup time, buffering, bitrate changes, errors.
```

---

## 15. Live streaming

Live streaming wymaga osobnego pipeline’u.

### Flow live

```text
Broadcaster
   │ RTMP / SRT / WebRTC
   ▼
Ingest Service
   │
   ▼
Live Transcoder
   │
   ▼
Packager HLS/DASH
   │
   ▼
Origin
   │
   ▼
CDN
   │
   ▼
Viewers
```

### Ingest

Obsługiwane protokoły:

- RTMP — prosty i popularny.
- SRT — lepszy dla niestabilnych sieci.
- WebRTC — dla niskiej latencji.
- WHIP/WHEP — nowocześniejszy kierunek dla WebRTC ingest/playback.

### Live manifest

Dla live manifest jest aktualizowany na bieżąco i zawiera tylko ostatnie segmenty.

Dla DVR można przechowywać dłuższe okno, np.:

```text
DVR window: 2h
Segment length: 2s
Liczba segmentów w oknie: 3600
```

### Live to VOD

Po zakończeniu live streamu system może:

1. Zamknąć manifest.
2. Przepakować treść jako VOD.
3. Wygenerować miniatury.
4. Opublikować nagranie jako normalny film.

---

## 16. DRM i ochrona treści

Dla treści premium należy użyć DRM.

Typowe DRM-y:

- Widevine — Chrome, Android.
- FairPlay — Safari, iOS, Apple TV.
- PlayReady — Edge, Windows, Xbox, niektóre Smart TV.

Flow:

```text
Client ─► Playback Service: request playback
Playback Service ─► sprawdza dostęp
Client ─► CDN: pobiera zaszyfrowany manifest/segmenty
Client ─► DRM License Server: request license
DRM License Server ─► sprawdza token
DRM License Server ─► zwraca licencję
Client ─► odtwarza content
```

Dla mniej wrażliwych treści można użyć:

- Signed URLs.
- Signed cookies.
- Tokenów krótkoterminowych.
- Geo-blockingu.
- Referer/origin protection.

Samo signed URL nie jest pełnym DRM. Chroni głównie przed prostym hotlinkingiem, nie przed przechwyceniem treści po stronie klienta.

---

## 17. Rekomendacje

### MVP

Na start wystarczy:

- Popularne treści.
- Ostatnio oglądane.
- Podobne po kategorii/tagach.
- Ręcznie kuratorowane listy.
- Trending w ostatnich 24h/7d.

### Wersja zaawansowana

Dane wejściowe:

- Watch events.
- Completion rate.
- Likes/dislikes.
- Search queries.
- Click-through rate.
- Dwell time.
- Kategorie.
- Embeddingi treści.

Architektura:

```text
Events ─► Kafka ─► Data Lake/Warehouse
                    │
                    ▼
              Feature Store
                    │
                    ▼
              ML Training
                    │
                    ▼
              Recommendation Service
```

Podejścia:

- Collaborative filtering.
- Content-based recommendations.
- Embeddingi użytkowników i treści.
- Ranking model.
- A/B testing.

---

## 18. Wyszukiwanie

Search Service powinien korzystać z indeksu wyszukiwarki.

Indeksowany dokument:

```json
{
  "videoId": "vid_123",
  "title": "Example Movie",
  "description": "Opis filmu",
  "tags": ["action", "sci-fi"],
  "actors": ["Actor A", "Actor B"],
  "language": "pl",
  "publishedAt": "2026-06-05T10:00:00Z",
  "popularityScore": 87.4
}
```

Ranking może uwzględniać:

- Trafność tekstową.
- Popularność.
- Świeżość.
- Język użytkownika.
- Historię oglądania.
- Dostępność regionalną.
- Subskrypcję użytkownika.

---

## 19. Eventy i kolejki

System powinien być event-driven tam, gdzie operacje są asynchroniczne.

Przykładowe eventy:

```text
VideoUploaded
TranscodingStarted
TranscodingCompleted
TranscodingFailed
VideoPublished
PlaybackStarted
PlaybackPaused
PlaybackCompleted
PlaybackError
UserSubscribed
SubscriptionCancelled
```

Kolejka/event streaming:

- Kafka.
- Google Pub/Sub.
- AWS SNS/SQS.
- AWS Kinesis.
- RabbitMQ dla prostszego MVP.

Kafka jest dobra dla dużego wolumenu eventów analitycznych. SQS/PubSub są prostsze dla jobów.

---

## 20. Obserwowalność

### Metryki systemowe

- Request latency.
- Error rate.
- CPU/memory.
- Queue lag.
- Transcoding duration.
- CDN cache hit ratio.
- Origin egress.
- Storage usage.

### Metryki video QoE

Sama dostępność API nie mówi, czy użytkownik realnie może oglądać wideo.

Monitorować:

- Time to first frame.
- Startup delay.
- Rebuffering ratio.
- Liczba buffering events.
- Average bitrate.
- Bitrate switches.
- Playback failures.
- CDN errors.
- DRM license errors.
- Manifest load errors.
- Segment download latency.

### Logi

- Structured logs.
- Correlation ID.
- User ID, o ile zgodne z prywatnością.
- Video ID.
- Session ID.
- Device type.
- Region.

### Tracing

Tracing dla:

- Playback request.
- Auth check.
- Entitlement check.
- Signed URL generation.
- DRM license request.

Segmenty z CDN zwykle monitoruje się przez CDN logs, nie przez backend tracing.

---

## 21. Skalowanie

### Najważniejsza zasada

Backend nie powinien przesyłać segmentów wideo.

Segmenty powinny iść:

```text
Client ─► CDN ─► Object Storage
```

A nie:

```text
Client ─► Backend ─► Storage
```

### Skalowanie odczytu

- CDN dla segmentów.
- Cache dla metadanych.
- Read replicas dla PostgreSQL.
- Denormalizacja danych katalogu.
- Cache popularnych list.

### Skalowanie zapisu

- Kolejki dla upload/transcoding.
- Batch writes dla eventów.
- NoSQL dla watch progress.
- Idempotentne API.

### Skalowanie transkodowania

- Workery autoskalowane według długości kolejki.
- Priorytety jobów:
  - Premium/live najwyżej.
  - Popularni twórcy wyżej.
  - Reprocessing archiwalnych treści niżej.
- Retry z backoffem.
- Dead-letter queue.

---

## 22. Cache strategy

### CDN cache

Cache’owane:

- Segmenty wideo.
- Manifesty VOD.
- Miniatury.
- Napisy.
- Publiczne assety.

Niebezpieczne do cache’owania bez ostrożności:

- Manifesty z tokenem użytkownika.
- Treści regionalnie ograniczone.
- Treści premium.

Lepszy model:

- Segmenty mają stabilne URL-e.
- Dostęp kontrolowany signed cookies albo CDN token auth.
- Manifest może mieć krótki TTL.

### Application cache

Redis:

- Video metadata.
- Entitlements.
- Session/token validation.
- Popularne listy.
- Rate limiting.

---

## 23. Spójność danych

System nie musi być wszędzie silnie spójny.

### Silna spójność potrzebna dla:

- Płatności.
- Subskrypcji.
- Uprawnień dostępu.
- Statusu publikacji.
- Operacji administracyjnych.

### Eventual consistency wystarczy dla:

- Liczników wyświetleń.
- Rekomendacji.
- Trending.
- Search index.
- Watch analytics.
- CDN invalidation.

Przykład: po publikacji filmu może minąć kilka sekund, zanim pojawi się w search index. To akceptowalne.

---

## 24. Bezpieczeństwo

### API

- TLS wszędzie.
- OAuth2/OIDC.
- JWT z krótkim TTL.
- Refresh token rotation.
- Rate limiting.
- WAF.
- Bot protection.
- Audyt admin actions.

### Upload

- Pre-signed URL z krótkim TTL.
- Limit rozmiaru.
- Multipart upload.
- Checksum.
- Malware scan.
- Walidacja MIME type.
- Oddzielny bucket dla raw uploadów.
- Brak publicznego dostępu do raw plików.

### Playback

- Signed URLs/cookies.
- Token bound to user/session/device.
- Krótki TTL.
- Geo-blocking.
- DRM dla premium.
- Watermarking forensic dla bardzo wartościowych treści.

### Dane użytkownika

- Minimalizacja danych.
- Szyfrowanie danych w spoczynku.
- Szyfrowanie sekretów w KMS.
- Retencja logów.
- RODO/GDPR: eksport i usuwanie danych użytkownika.

---

## 25. Obsługa błędów

### Upload failed

Możliwe przyczyny:

- Zerwane połączenie.
- Zły format pliku.
- Przekroczony limit.
- Checksum mismatch.

Rozwiązanie:

- Multipart upload.
- Resume.
- Jasne statusy uploadu.
- Retry tylko dla bezpiecznych operacji.

### Transcoding failed

Możliwe przyczyny:

- Uszkodzony plik.
- Nieobsługiwany kodek.
- Brak zasobów.
- Timeout.

Rozwiązanie:

- Retry z limitem.
- Dead-letter queue.
- Przechowywanie error details.
- Możliwość ręcznego restartu joba.

### CDN miss spike

Możliwe przyczyny:

- Premiera popularnej treści.
- Invalidation zbyt szeroka.
- Cache TTL za krótki.

Rozwiązanie:

- Origin shielding.
- Pre-warming CDN.
- Długie TTL dla immutable segmentów.
- Segment URL z content hash.

### Playback errors

Rozwiązanie:

- Fallback CDN.
- Alternatywny manifest.
- Retry segmentów.
- QoE monitoring.
- Alerty po error rate.

---

## 26. Multi-region design

Dla globalnej platformy:

```text
Users ─► Nearest CDN Edge
             │
             ▼
        Regional Origin Shield
             │
             ▼
       Object Storage Replication
```

### API

Możliwe strategie:

1. Active-passive — prostsze, jeden region główny, drugi jako disaster recovery.
2. Active-active — trudniejsze, ale lepsze dla globalnej skali.

### Dane

- Segmenty wideo można replikować między regionami.
- Metadane katalogu można cache’ować regionalnie.
- Dane użytkownika wymagają większej ostrożności.
- Płatności i subskrypcje powinny mieć jasno wyznaczony source of truth.

---

## 27. Disaster recovery

Plan:

- Backup PostgreSQL.
- Cross-region replication object storage.
- Infrastructure as Code.
- Regularne testy restore.
- Dead-letter queues dla eventów.
- Reprocessing pipeline dla transkodowania.
- CDN fallback origin.

Cele:

```text
RPO: 5–15 minut dla danych transakcyjnych
RTO: 30–60 minut dla krytycznych usług
```

Dla małego MVP te wartości mogą być luźniejsze.

---

## 28. Deployment

### Konteneryzacja

Serwisy:

- Docker.
- Kubernetes.
- Helm/Kustomize.
- Autoscaling.

### CI/CD

Pipeline:

```text
Commit
  │
  ▼
Tests
  │
  ▼
Build Docker Image
  │
  ▼
Security Scan
  │
  ▼
Deploy to Staging
  │
  ▼
Integration Tests
  │
  ▼
Canary Deploy
  │
  ▼
Production
```

### Strategie wdrożeń

- Blue/green.
- Canary.
- Feature flags.
- Rollback automatyczny po wzroście error rate.

---

## 29. Proponowany podział na mikroserwisy

Na start nie należy przesadzać z liczbą mikroserwisów. Można zacząć od modularnego monolitu plus osobne workery.

### MVP

```text
API Backend
Transcoding Workers
Object Storage
PostgreSQL
Redis
CDN
Search Engine
```

### Wersja skalowalna

```text
Auth Service
User Service
Catalog Service
Upload Service
Transcoding Service
Playback Service
Search Service
Recommendation Service
Watch History Service
Analytics Ingestion Service
Notification Service
Admin Service
DRM/License Service
```

Rekomendacja: **nie zaczynać od pełnej mikroserwisowej architektury**, jeśli zespół jest mały. Największym problemem technicznym nie będzie REST API, tylko poprawny pipeline wideo, CDN, transkodowanie i obserwowalność playbacku.

---

## 30. MVP architecture

Dla pierwszej wersji:

```text
Frontend / Mobile
      │
      ▼
API Backend
      │
      ├── PostgreSQL
      ├── Redis
      ├── Object Storage
      ├── Queue
      ├── Transcoding Workers
      └── Search Engine
              │
              ▼
             CDN
```

MVP powinien mieć:

- Upload przez signed URL.
- Transkodowanie przez FFmpeg worker.
- HLS output.
- Object storage.
- CDN.
- Podstawowy katalog.
- Podstawowy playback.
- Watch progress.
- Proste wyszukiwanie.
- Admin panel.
- Monitoring podstawowy.

Nie robiłbym od razu:

- Zaawansowanych rekomendacji ML.
- Multi-region active-active.
- Własnego DRM, jeśli można użyć gotowego.
- Ultra low latency live.
- Pełnej mikroserwisowej fragmentacji.

---

## 31. Najważniejsze decyzje architektoniczne

### 1. HLS jako główny format

HLS daje największą kompatybilność. DASH można dodać później.

### 2. Segmenty przez CDN

Backend nie może być w ścieżce przesyłania wideo.

### 3. Upload bezpośrednio do object storage

Backend wydaje signed URL, ale nie przyjmuje wielogigabajtowych plików.

### 4. Transkodowanie asynchroniczne

Upload nie powinien czekać synchronicznie na transkodowanie.

### 5. Event-driven pipeline

Upload, transkodowanie, publikacja, analityka i rekomendacje powinny być oparte o eventy.

### 6. Watch progress w NoSQL lub Redis + trwały flush

Relacyjna baza może nie wytrzymać bardzo częstych zapisów pozycji odtwarzania przy dużej skali.

### 7. DRM tylko tam, gdzie naprawdę potrzebne

DRM komplikuje system. Dla treści premium jest potrzebne, dla zwykłych materiałów signed URLs mogą wystarczyć.

---

## 32. Przykładowy diagram komponentów

```text
                                  ┌─────────────────────┐
                                  │      Admin Panel     │
                                  └──────────┬──────────┘
                                             │
                                             ▼
┌─────────────┐       ┌──────────────────────────────┐
│   Clients   │──────►│          API Gateway          │
└──────┬──────┘       └──────────────┬───────────────┘
       │                             │
       │                             ├── Auth Service
       │                             ├── Catalog Service
       │                             ├── Search Service
       │                             ├── Playback Service
       │                             ├── User Service
       │                             └── Watch History Service
       │
       │
       ▼
┌─────────────┐
│     CDN     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Origin    │
│   Storage   │
└─────────────┘


Upload Flow:

Admin/Creator
     │
     ▼
Upload Service ───► Signed URL ───► Object Storage Raw Bucket
                                           │
                                           ▼
                                     Event Queue
                                           │
                                           ▼
                                  Transcoding Workers
                                           │
                                           ▼
                                  Packaged Video Bucket
                                           │
                                           ▼
                                          CDN
```

---

## 33. Potencjalny stack technologiczny

### Backend

- Java/Kotlin + Spring Boot.
- Go.
- Node.js/NestJS.
- Python/FastAPI dla części ML/analityki.

### Storage

- PostgreSQL.
- Redis.
- S3/GCS/Azure Blob.
- Elasticsearch/OpenSearch.
- BigQuery/Snowflake/Redshift.

### Video

- FFmpeg.
- Bento4/Shaka Packager.
- AWS MediaConvert lub GCP Transcoder.
- HLS/DASH.
- CMAF.
- DRM provider, np. Widevine/FairPlay/PlayReady przez gotowego vendora.

### Infra

- Kubernetes.
- Terraform.
- Prometheus.
- Grafana.
- OpenTelemetry.
- Kafka/PubSub/SQS.

---

## 34. Ryzyka techniczne

Największe ryzyka:

1. Koszt egressu z CDN i storage.
2. Błędy transkodowania dla nietypowych plików.
3. Zbyt wolny start playbacku.
4. Niska jakość streamingu na słabszych sieciach.
5. Niepoprawna autoryzacja dostępu do premium contentu.
6. Problemy z DRM na różnych urządzeniach.
7. Fragmentacja wsparcia Smart TV.
8. Zbyt agresywne rozdrobnienie mikroserwisów.
9. Brak QoE monitoringu.
10. Zbyt częste zapisy watch progress do relacyjnej bazy.

---

## 35. Pytania, które warto doprecyzować przed implementacją

Najważniejsze:

1. Czy system ma obsługiwać tylko VOD, czy też live?
2. Czy treści są premium i wymagają DRM?
3. Jaka jest przewidywana skala: użytkownicy, godziny wideo, concurrency?
4. Czy potrzebny jest upload przez użytkowników, czy tylko przez adminów?
5. Czy są wymagania regionalne/licencyjne?
6. Czy potrzebny jest offline playback?
7. Jakie platformy klienckie są priorytetem?
8. Czy system ma być globalny od początku?
9. Czy rekomendacje mają być ML-based czy regułowe?
10. Czy jest preferowany cloud provider?

---

## 36. Rekomendowana ścieżka wdrożenia

### Faza 1 — MVP VOD

- Auth.
- Upload przez signed URL.
- Transkodowanie do HLS.
- CDN.
- Catalog.
- Playback.
- Watch progress.
- Admin panel.
- Podstawowe logi i metryki.

### Faza 2 — Skalowanie

- Kolejki i autoskalowanie workerów.
- Search engine.
- Redis cache.
- QoE analytics.
- Lepsze retry i DLQ.
- CDN tuning.
- Pre-warming popularnych treści.

### Faza 3 — Premium

- Subskrypcje.
- Entitlements.
- DRM.
- Geo-blocking.
- Signed cookies.
- Audyt adminów.

### Faza 4 — Personalizacja

- Rekomendacje.
- Trending.
- Ranking.
- A/B testing.
- Data warehouse.
- Feature store.

### Faza 5 — Live

- Live ingest.
- Live transcoding.
- Live HLS/DASH.
- DVR.
- Live-to-VOD.
- Low-latency mode, jeśli potrzebny.

---

## 37. Najkrótsza wersja architektury

System składa się z warstwy API dla autoryzacji, katalogu, uploadu i playbacku, ale właściwe pliki wideo są zawsze dostarczane przez CDN. Upload działa przez signed URL bezpośrednio do object storage. Po uploadzie event uruchamia asynchroniczny pipeline transkodowania, który generuje profile jakości, segmenty HLS/DASH i manifesty.

Playback Service sprawdza uprawnienia użytkownika i wydaje signed URL albo signed cookie do CDN. Metadane trzymamy w PostgreSQL, historię oglądania i eventy w NoSQL/event streamingu, wyszukiwanie w OpenSearch, a analitykę i rekomendacje budujemy na eventach z playera.

Najważniejsze są: CDN, transkodowanie, adaptive bitrate, obserwowalność QoE i poprawna kontrola dostępu.

---

## 38. Kluczowa uwaga końcowa

Największy błąd przy takim projekcie to budowanie „ładnego backendu” i ignorowanie faktu, że prawdziwy ciężar systemu leży w:

- CDN,
- pipeline wideo,
- kosztach transferu,
- kompatybilności playerów,
- jakości odtwarzania,
- bezpieczeństwie dostępu do treści.

Backend powinien przede wszystkim koordynować procesy i autoryzować dostęp. Nie powinien fizycznie przesyłać segmentów wideo do użytkownika końcowego.
