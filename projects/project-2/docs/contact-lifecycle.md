# Kontakt Notification: source → projection/cache → event → restore

## Problem i własność

Kontakt jest własnością Notification i ma osobne self-service API. Email i nazwa
nie trafiają do Ordering ani do zdarzenia usunięcia. Zamówienia zachowują UUID
klienta i dane transakcji zgodnie z własnym kontraktem retencji. To usunięcie
kontaktu, a nie deklaracja usunięcia wszystkich danych osoby w całej organizacji.
Referencje UUID i rejestr suppression nadal wymagają kontroli dostępu i retencji.

PUT/GET/DELETE `/api/contacts/{customer}` działają w profilu postgres.
W secure polityka OrderOwnership wymaga uwierzytelnionego właściciela lub admina.
DELETE wymaga `Deletion-Request-Id`; ponowienie dla tego samego klienta zwraca
ten sam identyfikator operacji. Brak profilu secure oznacza lokalny otwarty lab.

## Mechanizm i okna awarii

V8 tworzy source, projekcję, registry oraz osobny outbox bez PII. Advisory lock
po customer serializuje zapis kontaktu, usunięcie i późny replay. Erase zapisuje
tombstone, usuwa source/projection i dodaje intencję zdarzenia w jednym COMMIT.
Tombstone blokuje późniejsze odtworzenie kontaktu przez PUT/import/replay.

Cache to ograniczone LRU 1024 wpisy, bez JDBC pod monitorem. Każdy odczyt
sprawdza tombstone przed cache, więc opóźniona invalidacja nie udostępni kontaktu
po zakończonym erase. Żądanie już trwające przy erase może kończyć wcześniejszy
odczyt; nie obiecujemy fizycznego wymazania wszystkich referencji z heapu.

ErasureKafkaWorker jest opcjonalny: `marketplace.privacy.worker-enabled=true`.
Domyślny topic: marketplace.privacy-events.v1. Worker publikuje stały eventId i
subjectReference; acks poprzedza published_at. Awaria po ack przed COMMIT oznacza
duplikat. Odbiorca musi stosować inbox po eventId i tombstone przeciw starym
zdarzeniom. Nie dodajemy fikcyjnego wspólnego transakcyjnego rollbacku Kafka+SQL.
Limit transakcji relaya to 100 rekordów i ograniczony czas brokera.

## Restore starszego backupu

Samo usunięcie source jest niewystarczające: stary dump zawiera dawny email.
ContactLifecycle.exportRegistry eksportuje aktualny registry, a seal zapisuje
watermark, liczbę wpisów i SHA-256 kanonicznej treści. Body i zaufany manifest
muszą być przechowywane poza odtwarzanym backupem, z niezależną kontrolą dostępu.
Hash wykrywa niespójność z manifestem; nie uwierzytelnia podmienionych obu plików.

Maintenance `suppressAfterRestore` działa przed otwarciem ruchu i uruchomieniem
workerów. Wymaga zgodnego manifestu, odtwarza tombstone, usuwa stary source oraz
projection, czyści cache i kolejkuje ponowne zdarzenie z tym samym eventId.
Nie usuwamy registry samym TTL: najpierw trzeba wykazać wygaśnięcie wszystkich
backupów i źródeł replay. Baza i Kafka mają niezależne polityki retencji.

## Narzędzie maintenance

Task uruchamia osobny proces Java, bez serwera i bez workerów. Przygotuj istniejące
katalogi docelowe. Body i manifest zapisują się przez atomic rename; błąd między
dwoma rename pozostawia niezgodne pliki i blokuje suppression. Po eksporcie
przenieś manifest do niezależnie chronionego storage. Podane pliki są jawnie nadpisywane.

```powershell
$env:CONTACT_DB_URL='jdbc:postgresql://localhost:5432/marketplace'
$env:CONTACT_DB_USER='operator'
# CONTACT_DB_PASSWORD dostarcz lokalnie z bezpiecznego źródła.
.\gradlew.bat contactMaintenance -Poperation=export -PregistryFile=target/registry.json -PmanifestFile=target/registry-manifest.json
# Następnie wskaż CONTACT_DB_URL odizolowanej odtworzonej bazy, pozostawiając ruch zamknięty.
$env:CONTACT_RESTORE_MAINTENANCE='true'
.\gradlew.bat contactMaintenance -Poperation=suppress -PregistryFile=target/registry.json -PmanifestFile=target/registry-manifest.json
```

Flaga maintenance jest zobowiązaniem operatora; nie zatrzymuje innych procesów.
Zatrzymaj ingress/workery i sprawdź świeżość zaufanego manifestu przed operacją.
Exporter nie wykonuje automatycznej kopii offsite i nie zastępuje polityki backupu.

## Dowód

```powershell
.\gradlew.bat integrationTest --tests '*ContactErasureRestoreIntegrationTest'
```

Test wypełnia cache, robi pg_dump, usuwa kontakt, wymusza błąd brokera, publikuje
i odczytuje rzeczywiste zdarzenie Kafka bez emaila/nazwy, eksportuje registry
po erase poza DB, przywraca starszy dump do osobnej bazy PostgreSQL i stosuje
suppression. Sprawdza też odmowę starego manifestu, późnego PUT i retry maintenance.
Nie uruchamia powiadomień ani aplikacji na niesanitaryzowanej bazie restore.
To lokalny drill, nie dowód offsite backupu, utraty regionu ani zgodności prawnej.
