-- Historyczna migracja utworzyła konto demonstracyjne ze znanym hasłem.
-- Pozostawiamy rekord ze względu na klucze obce, ale domyślnie blokujemy login.
UPDATE users
SET status = 'DISABLED', updated_at = now()
WHERE id = '00000000-0000-0000-0000-000000000001';
