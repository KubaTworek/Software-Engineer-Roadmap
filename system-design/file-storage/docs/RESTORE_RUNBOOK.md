# Restore drill — File Storage

## Cel i warunek zaliczenia

Drill dowodzi, że wskazany punkt odtworzenia zawiera spójne metadane i bloby. Zaliczenie wymaga zmierzonego RPO/RTO, poprawnych migracji Flyway, zerowej liczby brakujących obiektów w próbce krytycznej oraz ręcznej weryfikacji pobrania kilku plików.

## Procedura

1. Zapisz identyfikator snapshotu bazy, wersję Flyway, snapshot bucketa i czas graniczny.
2. Utwórz izolowaną bazę i bucket. Nigdy nie wykonuj drillu na zasobach produkcyjnych.
3. Odtwórz PostgreSQL z `pg_restore`; nie kieruj jeszcze ruchu aplikacyjnego.
4. Odtwórz wersje obiektów odpowiadające temu samemu punktowi czasowemu.
5. Uruchom aplikację z wyłączonymi workerami publikującymi i sprawdź migracje.
6. Uruchom rekoncyliację referencji DB → storage, następnie inventory storage → DB z okresem karencji.
7. Pobierz próbkę: mały plik, multipart, współdzielony blob, starą wersję i plik po restore z kosza. Porównaj SHA-256.
8. Włącz workerów bez skutków zewnętrznych i sprawdź backlog outboxa/DLQ.
9. Zapisz rzeczywiste RPO/RTO, findingi, decyzję go/no-go i kroki naprawcze.

## Granice

Lokalny `BackupService` zapisuje wyłącznie manifest edukacyjny. Produkcyjny backup wykonują narzędzia PostgreSQL i dostawcy object storage; ich konfiguracja, szyfrowanie, retencja i uprawnienia muszą być testowane niezależnie.
