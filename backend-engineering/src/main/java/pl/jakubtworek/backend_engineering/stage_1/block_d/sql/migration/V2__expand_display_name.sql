-- Expand: kompatybilna wstecz zmiana schematu. Na tym etapie aplikacja może
-- zapisywać oba formaty, ale nie może jeszcze zakładać, że display_name istnieje.
ALTER TABLE customer_profile
    ADD COLUMN display_name VARCHAR(201);
