# Rate Limiter — runbook operacyjny

## Cel

Odróżnić prawidłowe odrzucanie nadmiarowego ruchu od awarii data plane. Sam
wzrost odpowiedzi `429` nie jest incydentem, jeśli chroniony downstream pozostaje
stabilny, a ruch pochodzi od niewielkiej grupy klientów.

## Sygnały

1. Sprawdź p99 `rate_limiter_redis_latency_seconds` i błędy Redisa.
2. Sprawdź `rate_limiter_redis_circuit_open` oraz tempo fallbacków według
   strategii.
3. Porównaj allowed/denied z RPS, error rate i saturation chronionego systemu.
4. Porównaj `rate_limiter_config_version` między instancjami.
5. Ustal, czy problem dotyczy jednej reguły, regionu czy całego data plane.

Nie używaj user ID, API key ani tenant ID jako tagów Prometheusa. Do analizy
pojedynczego klienta użyj ograniczonych czasowo logów/audytu zgodnie z polityką
PII.

## Decyzja

| Obserwacja | Interpretacja | Pierwsza reakcja |
| --- | --- | --- |
| denied rośnie, Redis zdrowy, downstream stabilny | limiter działa | identyfikuj źródło; nie podnoś limitu automatycznie |
| p99 Redis rośnie przed fallbackiem | store przeciążony | zatrzymaj retry, sprawdź hot keys i połączenia |
| circuit open + fail-open | ochrona osłabiona | ogranicz ruch na edge/gateway |
| circuit open + fail-closed | legalny ruch blokowany | przywróć store lub kontrolowanie przełącz strategię |
| config version różni się między instancjami | control-plane lag | wstrzymaj rollout reguły, przywróć zgodny snapshot |
| restart Redisa powoduje burst | utrata stanu | ogranicz ruch upstream, oceń persistence/restore |

## Bezpieczna zmiana reguły

1. Pobierz bieżącą wersję z `GET /admin/rules`.
2. Zweryfikuj `cost <= capacity`, scope i failure strategy.
3. Zastosuj zmianę z `If-Match` oraz identyfikatorem operatora.
4. Obserwuj denial rate, fallback, Redis p99 i downstream saturation.
5. Jeśli pogarsza SLO, opublikuj poprzednią regułę jako nową wersję; nie edytuj
   historii audytu.

## Po incydencie

Zapisz timeline, affected rules/regions, wybraną semantykę fail-open/closed,
oszacowany overshoot albo false rejection oraz brakujące testy. Jeśli zdarzenie
dotyczy usage, wykonaj reconciliation z autorytatywnym źródłem biznesowym —
licznik Redis i best-effort Kafka nie są ledgerem.
