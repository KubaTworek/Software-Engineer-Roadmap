# Replikacja PostgreSQL i retencja WAL

## Problem i eksperyment

Read replica może zwrócić stare dane, choć wszystkie requesty kończą się sukcesem.
Zatrzymany slot CDC może zatrzymywać WAL i zapełnić dysk primary.

Z `backend-engineering`, Docker Linux:

```powershell
pwsh -File labs/database-operations/replication-drill.ps1
```

Skrypt uruchamia prywatny, efemeryczny stack PostgreSQL 16 bez publikowania portów.
Tworzy fizyczną replikę przez pg_basebackup. Zatrzymuje replay, aktualizuje primary,
sprawdza starą wartość na replice, wznawia replay i czeka na nową wartość w skończonym
budżecie. Potem tworzy logiczny slot `test_decoding`, generuje WAL i sprawdza,
że `pg_wal_lsn_diff` wskazuje zatrzymane bajty. `finally` usuwa cały własny stack
z losowym project name. Lokalne hasło nie jest konfiguracją produkcyjną.

Read-your-writes kieruj do właściwego primary albo użyj jawnego mechanizmu
oczekiwania na LSN. Wiek ostatniego replay timestamp przy bezczynnym primary
nie dowodzi lag. Pusty pg_stat_replication może oznaczać brak repliki.

## Granice

Eksperyment nie zapełnia dysku i nie wykonuje failoveru. Retencja slotu, lag,
wolne miejsce i odpowiedzialność za restart connectora wymagają alertów oraz
runbooka. Nie usuwaj slotu bez planu snapshot/recovery: jego utrata może wymagać
ponownej inicjalizacji CDC. Hot standby może anulować zapytanie kolidujące
z replay; zwiększanie tolerancji ma koszt świeżości i utrzymania starych wersji.
