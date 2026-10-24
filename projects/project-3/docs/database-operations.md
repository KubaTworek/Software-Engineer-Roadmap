# Ticketing — świeżość danych, lag i zatrzymany slot

## Problem i decyzja

Lista wydarzeń może tolerować opóźnienie, potwierdzenie rezerwacji musi odczytać
stan spełniający read-your-writes. `ReplicaReadPolicy` w common zwraca PRIMARY,
REPLICA lub REJECT na podstawie jawnej dostępności, pomiaru lag i wymagań klienta.
Brak pomiaru nie jest zerem. `ReplicaReadPolicyTest` sprawdza granicę budżetu,
UNKNOWN, utratę primary i silniejszy kontrakt odczytu po zapisie.

To polityka decyzji z testem, nie wdrożony router połączeń: obecny Compose ma
primary per usługa i nie ma automatycznej konfiguracji read replicas.
Nie deklarujemy zmierzonego RPO ani failoveru na podstawie testu modelu.

Z `project-3`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -pl common -am verify
pwsh -File scripts/database-observations.ps1 -Service order-db
```

Skrypt czyta statystyki lokalnego primary usługi. Rola domyślna to app zgodnie
z lokalnym Compose. Plik SQL można uruchomić osobno przez psql z rolą mającą
odpowiednie uprawnienia obserwacyjne. Nie loguje pełnej treści zapytań klientów.

## Runbook

1. Ustal wpływ: stare widoki katalogu, timeout rezerwacji czy zajętość dysku.
2. Sprawdź rolę węzła i pg_stat_replication. Brak wierszy nie dowodzi zdrowej repliki.
3. Porównaj LSN nadany/odebrany/odtworzony i świeżość krytycznej ścieżki.
   Wiek ostatniego timestampu przy braku zapisów nie jest dowodem lag.
4. Przy retencji WAL sprawdź slot, restart_lsn, aktywność connectora i wolne miejsce.
   Wyznacz czas do wyczerpania na podstawie tempa przyrostu, nie samego progu bajtów.
5. Zatrzymaj niekrytyczne obciążenie i napraw konsumenta. Usunięcie slotu wymaga
   planu snapshot/replay i sprawdzenia dostępności historii.
6. Potwierdź dogonienie zmian oraz journey rezerwacja → płatność. Failover wymaga
   fencing starego primary i osobnego pomiaru RPO/RTO.

Prawdziwy eksperyment dwóch PostgreSQL, pause/resume replay i slotu WAL jest w
[laboratorium replikacji](../../../backend-engineering/labs/database-operations/README.md).
Używa osobnego efemerycznego stacka, więc nie zatrzymuje bazy platformy.

## Sygnały i granice

Do istniejących dashboardów dodaj pomiar lag, retained WAL, wolnego dysku,
czasu najstarszej transakcji i blokad z eksportera bazy wybranego środowiska.
Skrypt jest ręcznym snapshotem, nie nowym eksporterem Prometheus.
Alert połącz z SLO świeżości oraz runbookiem; nie zgaduj uniwersalnych progów.
