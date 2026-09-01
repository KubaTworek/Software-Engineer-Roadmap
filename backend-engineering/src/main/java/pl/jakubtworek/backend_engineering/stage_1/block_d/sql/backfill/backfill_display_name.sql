-- Uruchamiaj wielokrotnie aż UPDATE zwróci 0 zmienionych rekordów.
-- SKIP LOCKED pozwala kilku workerom pobierać rozłączne partie.
WITH batch AS (
    SELECT id
    FROM customer_profile
    WHERE display_name IS NULL
    ORDER BY id
    LIMIT 1000
    FOR UPDATE SKIP LOCKED
)
UPDATE customer_profile AS profile
SET display_name = concat_ws(' ', profile.first_name, profile.last_name)
FROM batch
WHERE profile.id = batch.id;
