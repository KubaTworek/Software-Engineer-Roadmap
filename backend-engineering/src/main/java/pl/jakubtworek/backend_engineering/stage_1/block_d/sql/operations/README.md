# Operacje PostgreSQL — długie transakcje i DDL

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Operacje PostgreSQL — długie transakcje i DDL.
> - **Typowy błąd:** Uruchomienie DDL bez limitu lock wait przy długiej transakcji czytającej.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests "-Dtest=PostgreSqlOperationsTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Model i H2 sprawdzają logikę; blokady, plan zapytania i zachowanie silnika potwierdza dopiero odpowiedni profil infrastrukturalny.
<!-- material-card:end -->

## Problem i niezmiennik

Stały ruch nie oznacza stałego stanu bazy. Długi snapshot utrzymuje stare wersje
wierszy, a transakcja czytająca tabelę może blokować DDL. Czas oczekiwania operacji
administracyjnej musi być ograniczony; restart aplikacji nie zastępuje diagnozy.

## Uruchomienie

Z `backend-engineering`, Docker Engine wymagany:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests "-Dtest=PostgreSqlOperationsTest" test
```

`PostgreSqlOperationsTest` używa dwóch rzeczywistych połączeń PostgreSQL 16.
Pierwszy test utrzymuje transakcję odczytu, wykazuje SQLSTATE 55P03 po
`lock_timeout`, a po rollbacku pozwala wykonać DDL. Drugi ustala snapshot
REPEATABLE READ, zmienia dane i uruchamia VACUUM: stary snapshot nadal musi
widzieć poprzednią wartość. H2 nie jest substytutem tych testów.

## Diagnostyka

`pg_stat_activity`, `pg_blocking_pids`, `pg_stat_user_tables` i plany EXPLAIN
rozdzielają lock wait, stare snapshoty i błędne estymacje. `n_dead_tup` to
estymacja; nie jest dokładnym pomiarem bloat. `ANALYZE` odświeża statystyki,
`VACUUM` odzyskuje przestrzeń do ponownego użycia i chroni przed wraparound,
nie musi zmniejszyć pliku. Nie stosuj `VACUUM FULL` odruchowo: wymaga silnej
blokady i dodatkowego miejsca. DDL dobieraj do wersji bazy, transakcji i limitów czasu.

Test ręcznie wywołuje VACUUM; nie dowodzi uruchomienia autovacuum w określonym
czasie. Dla niego zbierz konfigurację progów, logi i trendy martwych krotek.
Zobacz też [replikację i retencję WAL](../../../../../../../../../../labs/database-operations/README.md).
