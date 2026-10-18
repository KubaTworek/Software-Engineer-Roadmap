alter table outbox_events add column claimed_at timestamp(6) with time zone;

create index idx_outbox_recovery on outbox_events(status, claimed_at);
