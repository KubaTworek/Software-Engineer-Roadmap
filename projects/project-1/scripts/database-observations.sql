-- Odczyt diagnostyczny Booking: nie logujemy treści requestów ani pełnych query z PII.
SELECT pid, state, now()-xact_start AS transaction_age, wait_event_type, wait_event,
       pg_blocking_pids(pid) AS blockers
FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid();
SELECT relname, n_live_tup, n_dead_tup, last_autovacuum, last_autoanalyze
FROM pg_stat_user_tables WHERE relname IN ('events','reservations','capacity_pools');
-- n_dead_tup jest estymacją, nie dokładną liczbą martwych rekordów ani bajtów bloat.
