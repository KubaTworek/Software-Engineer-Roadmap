# Region Control Plane

## Sens laboratorium

Control plane wskazuje właściciela zapisu dla miasta/bucketu i wydaje rosnący fencing token. Niezmiennik: **stary właściciel po wygaśnięciu lease nie może dalej wykonywać zaakceptowanych zapisów**.

Krytyczny przepływ: stabilny routing miasta → acquire/renew lease → `ownershipEpoch` → data plane dołącza epoch do zapisu → magazyn odrzuca niższy token. Client hint nie może przejąć własności zapisu.

Rekomendowany test: `RegionControlServiceTest`, w tym odrzucenie starego właściciela po failoverze.

## Granica produkcyjna

Rejestr lease działa in-memory i służy jako model. Produkcyjny control plane wymaga quorum store, obserwowalnego failoveru, fencing egzekwowanego przez docelowy magazyn oraz osobnej ścieżki dla odczytów i zapisów.
