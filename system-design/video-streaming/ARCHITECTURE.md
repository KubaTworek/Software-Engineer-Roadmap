# Architecture Notes

## MVP boundary

Ten projekt jest modularnym monolitem Spring Boot. To świadoma decyzja: na Fazę 1 ważniejsze są poprawne granice modułów i działający pipeline wideo niż przedwczesne rozbijanie systemu na mikroserwisy.

## Modules

- `auth` — użytkownicy, JWT, role.
- `catalog` — metadane i statusy wideo.
- `upload` — signed URL, upload completion.
- `storage` — adapter MinIO/S3.
- `transcoding` — asynchroniczny worker FFmpeg -> HLS.
- `playback` — kontrola gotowości i wydawanie manifestu HLS.
- `watch` — zapis pozycji oglądania.
- `admin` — prosty panel administracyjny.
- `config` — konfiguracja bezpieczeństwa i properties.

## Production split later

Docelowo można rozdzielić:

- Auth Service
- Catalog Service
- Upload Service
- Transcoding Service
- Playback Service
- Watch History Service
- Analytics Ingestion Service
