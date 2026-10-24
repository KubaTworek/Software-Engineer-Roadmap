# Marketplace — staging dostawcy i Temporal

## Import katalogu: problem → transakcja → dowód

`SupplierFeedFile` tworzy prywatną kopię niezmiennego pliku UTF-8, liczy SHA-256
tej kopii i czyta format `sku,amount` bez nagłówka, quoting i pól wieloliniowych.
Bufor porcji ma najwyżej 1000 rekordów, a bufor linii 512 znaków. Zbyt długa
lub błędna linia trafia do kwarantanny jako kod `INVALID_ROW`, bez surowych danych.
Dostawca musi opublikować źródło atomowym rename lub jako immutable object.
SHA-256 identyfikuje treść; nie uwierzytelnia dostawcy.

`SupplierCatalogImport` blokuje run przez FOR UPDATE. Efekt porcji, kwarantanna
i checkpoint mają jeden COMMIT. Retry sprawdza również treść już zapisanych
rekordów. Luka, zmieniony fingerprint i częściowe nakładanie są odrzucane.
Konflikt SKU wycofuje całą porcję; nie jest automatycznie ignorowany.

Lifecycle: RUNNING → COMPLETE / FAILED / CANCELLED. FAILED można wznowić
z tym samym fingerprintem przez `resume`; CANCELLED pozostaje terminalny.
EOF i zgodność liczby wierszy z accepted + rejected są warunkiem COMPLETE.
Kwarantanna domyślnie blokuje publikację; `allowRejected=true` jest świadomą
polityką operatora. COMPLETE atomowo przełącza `supplier_active_feed`.
`publishedPrice` widzi wyłącznie cały opublikowany snapshot, nigdy pół importu.
Retry COMPLETE weryfikuje dane bez ponownego publikowania.

To feed jednego dostawcy, nie modyfikacja agregatów Product używanych przez
zamówienia. Mapowanie SKU → Product, waluty i wersji dostawcy wymaga osobnego
kontraktu. Równoległe publikacje różnych run trzeba serializować po stronie
operatora; UUID i hash nie ustalają, który feed biznesowo jest nowszy.

Eksport używa kursora JDBC z fetchSize=500 w transakcji i zapisuje do Writer.
Przy błędzie odbiorcy odrzuć częściowy plik; publikuj eksport przez atomic rename.
Test PostgreSQL sprawdza rollback porcji, nową instancję, retry, kwarantannę,
wznowienie FAILED, EOF, terminalne COMPLETE, atomową publikację i zawartość eksportu.
Ten test nie symuluje SIGKILL. Oddzielny test Spring Batch uruchamia dwie JVM
z JobRepository na dysku i weryfikuje wznowienie po FAILED.
To autorski adapter JDBC osadzony w domenie. Prawdziwy Spring Batch jest w
[oddzielnym laboratorium](../../../backend-engineering/labs/durable-processes/README.md),
aby nie mieszać Spring Batch 5 ze Spring Boot 4 projektu.

```powershell
.\gradlew.bat integrationTest --tests '*SupplierCatalogImportIntegrationTest' --no-daemon
```

## Temporal: rekoncyliacja niepewnej płatności

`PaymentReconciliationWorkflowImpl` odpytuje `PaymentLookupActivity`, maksymalnie
12 razy, z trwałą minutową przerwą. Nie wysyła kolejnego reserve. Adapter używa
`ReconcileUnknownPaymentUseCase` przez proxy Springa: odczyt providera i lokalna
zmiana stanu nadal zachowują istniejącą granicę transakcji i eventów.
UNKNOWN po limicie daje MANUAL_REVIEW, nigdy fikcyjne REJECTED.

```powershell
.\gradlew.bat test --tests '*PaymentReconciliationWorkflowTest' --no-daemon
```

Test server Temporal przesuwa czas; test nie uruchamia prawdziwego operatora.
Zwykły start aplikacji nie wymaga Temporal. Aby uruchomić worker lokalnie:

1. Uruchom lokalny Temporal CLI: `temporal server start-dev --db-filename temporal-local.db`.
   Plik bazy trzymaj poza repozytorium lub w ignorowanym katalogu lokalnym.
2. Uruchom marketplace z dotychczasowymi profilami postgres/kafka oraz
   `--marketplace.temporal.enabled=true --marketplace.temporal.target=127.0.0.1:7233`.
3. Dla istniejącego zamówienia z UNKNOWN użyj CLI:
   `temporal workflow start --task-queue marketplace-payment-reconciliation --type PaymentReconciliationWorkflow --workflow-id reconcile-ORDER_UUID --input '"ORDER_UUID"' --input '"CORRELATION_UUID"'`.
   Zastąp UUID rzeczywistymi identyfikatorami; stabilny workflow-id identyfikuje proces.
4. Obserwuj historię, zatrzymaj worker i uruchom ponownie. Jeśli lokalny fake
   providera nie obsługuje lookup, oczekiwany jest MANUAL_REVIEW.

Konfiguracja jest lokalna bez TLS. Wdrożenie wymaga namespace, auth/TLS,
monitoringu, zgodności historii i bezpiecznej retencji payloadów. Saga zamówienia
pozostaje w dotychczasowym process managerze; workflow wykonuje tylko lookup.
Nie należy równolegle uruchamiać wielu workflow-id dla tej samej rekoncyliacji.
Samo retry SDK nie daje exactly-once płatności. Pełny przykład Signal, kompensacji,
wersjonowania i replay jest w laboratorium powyżej.
