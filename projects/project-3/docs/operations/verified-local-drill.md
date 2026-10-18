# Zweryfikowany lokalny drill

Ten zapis odróżnia wykonywalny dowód od planowanego runbooka. Wyniki pochodzą z lokalnego
Docker Compose uruchomionego 14 września 2026 (Europe/Warsaw). Nie są SLO produkcyjnym
ani uniwersalnym benchmarkiem — służą jako powtarzalny punkt odniesienia dla laboratorium.

## Pełny przepływ biznesowy

Komenda: `./scripts/smoke-test.ps1`

Wynik: **PASS**. Test utworzył rezerwację i opłacone zamówienie, potwierdził rezerwację,
publisher confirm outboxa oraz inbox powiadomienia. Ponowienie komendy z tym samym
`Idempotency-Key` zwróciło to samo zamówienie. Po restarcie Payment Mock lookup nadal
zwracał trwały status `PAID`.

## Restore i awarie zależności

Komenda: `./scripts/local-restore-drill.ps1`

- snapshot Order DB odtworzono do izolowanej bazy;
- liczba rekordów źródła i kopii: 2;
- czas odtworzenia i walidacji: 11,14 s;
- baza tymczasowa została usunięta po teście.

Komenda: `./scripts/operational-drill.ps1`

| Zależność | Czas do odzyskania health checku |
| --- | ---: |
| Redis | 8,49 s |
| RabbitMQ | 19,82 s |
| PostgreSQL Order DB | 11,09 s |

## Krótki soak

Komenda:

```shell
docker compose --profile apps --profile load-tests run --rm \
  -e SOAK_DURATION=30s -e SOAK_RPS=5 k6 run load-tests/soak.js
```

- 150 iteracji w modelu otwartym;
- `http_req_failed`: 0%;
- checks: 100%;
- p95: 634,61 ms;
- p99: 815,94 ms;
- brak przekroczenia progów eksperymentu.

Pierwsze uruchomienie ujawniło odpowiedzi `429`, ponieważ wszystkie VU wyglądały jak
jeden anonimowy klient. Generator otrzymał techniczny API key z osobnym budżetem, dzięki
czemu test pojemności downstreamu nie miesza się z oddzielnym testem admission control.

## Open model kontra closed model

Komenda: `docker compose --profile apps --profile load-tests run --rm k6 run load-tests/open-vs-closed.js`

| Model | Żądania | p95 | p99 | Błędy |
| --- | ---: | ---: | ---: | ---: |
| closed, 10 VU / 30 s | 109 042 | 5,70 ms | 9,91 ms | 0% |
| open, 20 iteracji/s / 30 s | 601 | 2,61 ms | 3,02 ms | 0% |

Closed model wykonał tyle pracy, ile pozwalała aktualna latency, natomiast open model
utrzymywał zewnętrzne tempo napływu. W eksperymencie degradacyjnym różnica jest kluczowa:
closed model sam zwolni wraz z systemem i może ukryć coordinated omission.

## Wniosek

Drill potwierdza lokalne wiring i oczekiwane mechanizmy, nie deklaruje gotowości
produkcyjnej. Kolejne wykonania powinny zachować surowe raporty w `load-tests/reports/`
i porównywać trendy, zamiast traktować powyższe liczby jako stałe wymagania.
