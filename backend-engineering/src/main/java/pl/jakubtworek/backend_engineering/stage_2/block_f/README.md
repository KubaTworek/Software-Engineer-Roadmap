# Modernizacja systemu — ownership i bezpieczny cutover

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** Modernizacja systemu — ownership i bezpieczny cutover.
> - **Typowy błąd:** Promocja starej kopii albo cofnięcie routingu po nowych zapisach bez reverse sync.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CutoverSimulationTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** `CutoverSimulation` = `simulation`.
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Problem i niezmiennik

Skopiowanie rekordów nie wystarcza, gdy w czasie backfillu źródło nadal się zmienia.
W każdej chwili istnieje jeden właściciel zapisu; przełączenie wymaga dowodu,
że docelowe dane odpowiadają spójnemu watermarkowi źródła.

## Kontrprzykład i rozwiązanie

`CutoverSimulation` modeluje zapis, zatrzymanie zapisów, watermark, weryfikację
i promocję. Weryfikacja starej kopii jest odrzucana. Po nowym zapisie nie można
cofnąć samego routingu: potrzebna jest synchronizacja zwrotna i nowa weryfikacja.
To świadomie model w jednej JVM, bez obietnicy rozproszonego fencing protokołu.

## Uruchomienie

Z `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CutoverSimulationTest" test
```

Test wymusza lukę watermarku, blokadę zapisów podczas cutoveru, odrzucenie starego
właściciela i niebezpiecznego rollbacku. Naiwne `owner=NEW` bez sprawdzeń łamie
każdą z tych granic.

## Przeniesienie do systemu

Najpierw characterization tests oraz kontrakt starego API. Anti-corruption layer
mapuje obcy język domeny na lokalny, nie przecieka jego encjami do nowej domeny.
Rozbij zmianę przez Strangler Fig lub branch by abstraction: shadow read bez
skutków ubocznych → pomiar różnic → cutover → obserwacja → wycofanie starej ścieżki.
W krótkim pause można porównać watermark i zawartość; online migration wymaga
snapshot + CDC, retencji logu i końcowego dogonienia zmian.

W projekcie 2 `ShadowCatalogReader` chroni odpowiedź starego katalogu podczas
niedostępności nowego odczytu. Próbka i różnice są liczone w adapterze; pomiar lag pochodzi od operatora;
nie są kryptograficznym dowodem ani rozproszoną blokadą. Routing zapisów pozostaje
osobną decyzją. Rollback read modelu jest tańszy od rollbacku ownershipu zapisu.

Przed usunięciem starego kodu sprawdź klientów, kontrakty eventów, retention,
monitoring i okres obserwacji. ADR zapisuje także opcję pozostawienia monolitu.
