# Canary, rollback i obsługa incydentu

## Warunek promocji

`CanaryDecisionPolicyTest` pokazuje bramkę na error rate i p99. Promocja wymaga
minimalnej próbki oraz braku regresji względem baseline. `INCONCLUSIVE` zatrzymuje
rollout. Kill switch ma wyłączać nową ścieżkę bez redeployu, lecz jego wartość,
właściciel i termin usunięcia muszą być audytowalne.

## Runbook scenariuszowy

1. Zamroź rollout i zapisz czas początku incydentu.
2. Porównaj canary z baseline: ruch, error rate, p99, saturation i consumer lag.
3. Wyłącz flagę albo wykonaj rollback obrazu. Nie cofaj migracji expand/contract,
   dopóki stara i nowa wersja aplikacji mogą działać równocześnie.
4. Jeżeli przyczyną jest poison record, odizoluj go w DLQ; nie kasuj offsetu.
5. Zweryfikuj recovery syntetycznym zamówieniem oraz spadkiem lag, nie samym HTTP 200.
6. Utwórz timeline oparty na zdarzeniach i metrykach, bez szukania winnego.

Game day powinien wstrzyknąć: timeout payment, niedostępność Kafki, crash po publish
przed `markPublished`, wolnego klienta WebSocket i błędną migrację kompatybilności.
