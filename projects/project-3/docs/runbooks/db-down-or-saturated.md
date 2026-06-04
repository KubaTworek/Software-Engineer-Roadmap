# Runbook — DB down albo DB saturation

## Objaw

Wzrost latency, 5xx, timeouty JPA/Hikari albo brak połączeń z PostgreSQL.

## Metryki

```promql
hikaricp_connections_active
hikaricp_connections_max
hikaricp_connections_timeout_total
```

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))
```

## Logi

```logql
{level="WARN"} |= "request_failed"
```

Szukaj błędów `connection`, `timeout`, `SQL`, `Hikari`.

## Trace

Sprawdź, czy najdłuższe spany dotyczą operacji DB.

## Reakcja

- Jeśli DB jest niedostępna: przełącz funkcje zależne od zapisu w tryb degradacji, jeśli to bezpieczne.
- Jeśli pula połączeń jest pełna: znajdź endpoint generujący najwięcej żądań i sprawdź wolne transakcje.
- Jeśli read-heavy endpoint obciąża DB: sprawdź cache hit ratio.
- Nie zwiększaj bezrefleksyjnie puli połączeń — możesz tylko przenieść problem na DB.
