# URL Shortener — runbook

## Redirect error-budget burn

1. Rozdziel odpowiedzi origin `5xx` od oczekiwanych `404/410`.
2. Sprawdź cache hit ratio, latency Redisa i liczbę fallbacków do PostgreSQL.
3. Jeśli Redis jest niedostępny, ogranicz ruch create/dashboard i zachowaj capacity primary dla redirectów.
4. Nie wydłużaj edge TTL bez oceny revocation SLO.

## Projection backlog lub dead letter

1. Sprawdź dostępność Redisa i adaptera globalnego storage.
2. Porównaj `attempts`, `last_error`, `locked_until` i wiek najstarszego rekordu.
3. Dla poison record popraw serializer lub payload, następnie jawnie odtwórz projekcję ze źródła prawdy.
4. Nigdy nie oznaczaj rekordu jako dostarczony wyłącznie po to, żeby zgasić alert.

## Analytics publish failures albo DLQ

1. Potwierdź dostępność exchange, routing key i kolejki.
2. Oceń utratę best-effort przez porównanie logów edge z liczbą eventów SQL.
3. Poison record odizoluj w DLQ; nie wykonuj bezwarunkowego replayu efektów biznesowych.
4. Jeśli statystyki są rozliczeniowe, przełącz projekt na durable publishing i zaplanuj publisher confirms.
