ALTER TABLE drivers ADD COLUMN IF NOT EXISTS active_ride_id UUID;
ALTER TABLE drivers ADD COLUMN IF NOT EXISTS offer_expires_at TIMESTAMP;

ALTER TABLE rides ADD COLUMN IF NOT EXISTS offered_at TIMESTAMP;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS offer_expires_at TIMESTAMP;

-- Ostatnią linią obrony jest baza: jedna aktywna relacja driver -> ride.
-- Indeks częściowy nie obejmuje historii zakończonych przejazdów.
CREATE UNIQUE INDEX IF NOT EXISTS uq_rides_active_driver
    ON rides(driver_id)
    WHERE driver_id IS NOT NULL
      AND status IN ('DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED', 'IN_PROGRESS');

CREATE INDEX IF NOT EXISTS idx_drivers_expired_offer
    ON drivers(offer_expires_at)
    WHERE availability_status = 'OFFERED_RIDE';

ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_outbox_claim
    ON outbox_events(status, locked_until, created_at);
