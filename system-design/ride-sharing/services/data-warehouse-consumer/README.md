# Data Warehouse Consumer

## Sens laboratorium

Consumer buduje analityczną kopię zdarzeń bez wpływania na transakcję ride. Niezmiennik: **ponowne dostarczenie tego samego `eventId` nie może utworzyć drugiego faktu**.

Krytyczny przepływ: Kafka envelope → walidacja metadanych → ekstrakcja wymiarów → `INSERT ... ON CONFLICT(event_id) DO NOTHING` → commit offsetu po zapisie. Raw payload pozostaje dostępny do ponownej interpretacji.

Rekomendowany test: `WarehouseConsumerTest`.

## Granica produkcyjna

Laboratorium nie rozwiązuje ewolucji schematu warehouse, late-arriving dimensions, GDPR erasure ani pełnego replayu. W produkcji potrzebne są DLQ, checkpoint backfillu, partycjonowanie tabeli i mierzenie laga od `occurredAt`, a nie wyłącznie czasu konsumpcji.
