# Marketplace — Strangler odczytu katalogu

## Problem i granica

Nowy read model może być spóźniony albo mieć inną semantykę ceny. Zmiana routingu
przed porównaniem wyników daje poprawne HTTP z błędną odpowiedzią biznesową.
`ShadowCatalogReader` jest małym adapterem granicy: przyjmuje dwie funkcje odczytu
tego samego DTO. W shadow zwraca starą odpowiedź, mierzy zgodność i toleruje
awarię kandydata. Nie wykonuje drugiej komendy zamówienia ani płatności.

Promocja wymaga własnej zebranej próbki bez różnic oraz potwierdzonego lag=0;
nie przyjmuje wymyślonych liczników próbki od wywołującego. Synchronizacja chroni
przejście w pojedynczej instancji. To celowo nie jest globalny release coordinator.
`rollbackReads` cofa wyłącznie routing odczytu, bez obietnicy cofnięcia danych.

## Uruchomienie

Z `project-2`:

```powershell
.\gradlew.bat test --tests '*ShadowCatalogReaderTest' --no-daemon
```

Testy dowodzą zachowania starej odpowiedzi podczas awarii kandydata, odrzucenia
promocji bez próbki/zgodności i rollbacku odczytu. Adapter jest wykonywalnym
przykładem na granicy katalogu, nie automatycznie podłączonym nowym endpointem.
Istniejące REST/GraphQL/gRPC nadal używają obecnych use case'ów.

## Plan rzeczywistego cutoveru

1. Ustal DTO, jednostkę ceny, zaokrąglenia, nullability i ownership.
2. Zbuduj spójny snapshot, wznów CDC i dogoń watermark każdej partycji.
3. Przetestuj tombstone, opóźniony event i zmianę w czasie backfillu.
4. Włącz shadow tylko dla odczytów. Zbierz reprezentatywną próbkę oraz różnice
   bez logowania danych osobowych; zakres próbki jest częścią decyzji.
5. Promuj routing przez istniejący mechanizm delivery; obserwuj p99, błędy i drift.
6. W razie błędu wróć do odczytu źródła. Przeniesienie ownershipu zapisu to osobny
   cutover z fencingiem, pause/watermark i możliwością synchronizacji zwrotnej.
7. Usuń stary adapter dopiero po sprawdzeniu klientów i okresu obserwacji.

Rozproszony rollout musi utrwalać etap i dowody we wspólnym storage.
Model [cutoveru](../../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_f/README.md)
pokazuje niezmiennik; nie zastępuje wdrożenia tego protokołu między replikami.
## Izolacja i ważność dowodu

Odczyt kandydata ma osobną pulę (domyślnie 2 sloty), brak kolejki i deadline
25 ms. Żaden monitor nie obejmuje I/O. Timeout lub nasycenie zachowuje starą
odpowiedź; także po promocji awaria powoduje fallback i reset dowodu. Telemetria
jest asynchroniczna, best effort, w tym samym ograniczonym bulkheadzie.
Anulowanie Future nie zatrzyma adaptera ignorującego interrupt: może zająć slot,
ale nie powiększy kolejki ani zablokować monitora. Zamknij reader przez close().

Próbka ma pięciominutowe okno. Rollback, upływ okna i zmiana wersji kandydata
(wywołanie rollbackReads przez wdrażającego) usuwają stary dowód. Wynik rozpoczęty
w poprzedniej epoce nie zasili nowej próbki. Lag ujemny/nieznany blokuje promocję.
Test z zawieszonym adapterem dowodzi ograniczenia czasu i saturacji; próbka
nadal musi być reprezentatywna biznesowo i zebrana na wszystkich replikach.
