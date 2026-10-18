# News Feed — runbook

## Feed ma wysoki error rate lub p99

1. Potwierdź zakres: region, endpoint, status i moment początku. Nie restartuj wszystkich instancji przed zachowaniem śladów.
2. Sprawdź nasycenie puli HTTP, Hikari, Redis i wątków konsumentów. Latency bez błędów często oznacza kolejkę, nie „wolny kod”.
3. Porównaj cache hit ratio, opóźnienie Redis oraz czas hydracji PostgreSQL. Przy awarii rekomendacji oczekuj degradacji do kandydatów following/trending.
4. Jeśli rośnie consumer lag, ogranicz backfill, sprawdź poison record/DLT i zwiększ konsumentów tylko do liczby partycji.
5. Przy podejrzeniu wycieku prywatności wyłącz odpowiednią projekcję, lecz pozostaw `ContentAccessPolicy`; pobierz przykładowe `postId`, `viewerId` i decyzję bez logowania treści lub tokenu.

## Outbox nie opróżnia się

1. Sprawdź dostępność brokera i błędy producenta; rekord `PROCESSED` oznacza otrzymanie ack, nie obsługę przez konsumenta.
2. Porównaj wiek najstarszego `PENDING/FAILED/CLAIMED` z 30-sekundowym lease.
3. Nie zmieniaj rekordów na `PROCESSED` ręcznie. Po naprawie relay wznowi dostarczenie; duplikaty są oczekiwane.
4. Jeśli pojedynczy payload jest trwale błędny, zachowaj go do analizy, przenieś do procedury quarantine i dopiero odblokuj kolejkę.

## Bezpieczny rollback

- Kod aplikacji musi działać ze schematem expand przed wdrożeniem contract migration.
- Rollback aplikacji nie cofa automatycznie danych ani eventów.
- Po rollbacku sprawdź wersję producenta/konsumenta, lag i zgodność payloadu, następnie wykonaj rekoncyliację projekcji.
