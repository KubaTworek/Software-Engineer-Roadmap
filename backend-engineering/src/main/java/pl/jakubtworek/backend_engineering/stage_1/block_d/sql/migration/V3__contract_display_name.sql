-- Walidacja constraintu oddzielona od jego utworzenia ogranicza czas trzymania
-- silnej blokady. Stare kolumny usuwamy dopiero w późniejszej migracji.
ALTER TABLE customer_profile
    ADD CONSTRAINT customer_profile_display_name_present
        CHECK (display_name IS NOT NULL AND btrim(display_name) <> '') NOT VALID;

ALTER TABLE customer_profile
    VALIDATE CONSTRAINT customer_profile_display_name_present;

ALTER TABLE customer_profile
    ALTER COLUMN display_name SET NOT NULL;
