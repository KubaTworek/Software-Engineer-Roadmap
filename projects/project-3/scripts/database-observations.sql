-- Ticketing: rozpoznaj lag/retencję przed restartem zależności.
SELECT application_name, state, sync_state,
       pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS replay_lag_bytes
FROM pg_stat_replication;
SELECT slot_name, active, wal_status,
       pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS retained_wal_bytes
FROM pg_replication_slots;
SELECT pg_is_in_recovery() AS is_replica,
       CASE WHEN pg_is_in_recovery() THEN pg_last_wal_receive_lsn() = pg_last_wal_replay_lsn() END AS received_equals_replayed;
-- Pusty wynik nie dowodzi zdrowej repliki: może oznaczać brak replikacji.
-- Wiek ostatniego replay timestamp przy nieaktywnym primary nie jest sam w sobie lagiem.
