# Location Service

## Sens laboratorium

Usługa obsługuje strumień szybko zmieniających się pozycji kierowców oraz zapytanie „kto jest w pobliżu?”. Najważniejszym niezmiennikiem jest to, że **starszy lub ponowiony pomiar nie może nadpisać nowszej pozycji**.

Krytyczny przepływ: aplikacja kierowcy wysyła `cityId`, współrzędne, dokładność, monotoniczny `sequence` i czas urządzenia → skrypt Lua porównuje sekwencję → atomowo przenosi kierowcę między komórkami H3/GEO → zapisuje snapshot z TTL → publikuje wersjonowane zdarzenie z kluczem `driverId`.

## Najważniejsze decyzje

- Redis przechowuje stan ulotny; PostgreSQL nie jest właściwym magazynem dla każdego punktu GPS.
- `recordedAt` opisuje czas urządzenia, a `receivedAt` służy do oceny świeżości po stronie serwera.
- TTL usuwa martwe snapshoty, ale sam wpis GEO wymaga jawnego cleanupu.
- `sequence` chroni przed zmianą kolejności pakietów lepiej niż zegar telefonu.
- H3 ogranicza przestrzeń kandydatów; dokładny routing i ETA są osobnym etapem rankingu.

## Test i uruchomienie

```powershell
..\..\mvnw.cmd -pl services/location-service -Pinfrastructure-tests verify
```

`RedisLocationInfrastructureTest` uruchamia prawdziwego Redisa i dowodzi, że spóźniona aktualizacja nie cofa pozycji.

## Granica produkcyjna

Skrypt dotyka kilku kluczy i jest poprawnym laboratorium pojedynczego Redisa, ale nie gwarantuje atomowości między różnymi slotami Redis Cluster. W systemie shardowanym trzeba kolokować klucze, kierować zapis do właściciela partycji albo aktualizować indeks przez uporządkowany strumień. Historia tras, antyfraud GPS i privacy retention są poza tym hot-path.
