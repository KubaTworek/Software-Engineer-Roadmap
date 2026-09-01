# migration

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** migration.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „migration” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Migracje bez przestoju — expand, migrate, contract



Zmiana schematu jest wdrożeniem rozproszonym w czasie: przez pewien okres stary kod, nowy kod i dane pośrednie mogą istnieć równocześnie. Bezpieczna migracja nie zakłada atomowego przełączenia wszystkich instancji aplikacji.

## Przykładowy scenariusz

Chcemy zastąpić `first_name` i `last_name` polem `display_name`.

1. **Expand** — `V2__expand_display_name.sql` dodaje nullable kolumnę. Nowa wersja aplikacji może wykonywać dual-write, ale nadal potrafi czytać stary format.
2. **Migrate** — [`../backfill/backfill_display_name.sql`](../backfill/backfill_display_name.sql)
   uzupełnia dane małymi, wznawialnymi partiami poza historią Flyway. Po każdej
   partii monitorujemy locki, replication lag i czas zapytań.
3. **Contract** — `V3__contract_display_name.sql` waliduje dane i dopiero potem ustanawia `NOT NULL`. Usunięcie starych kolumn powinno nastąpić w późniejszym wdrożeniu, gdy żaden rollback nie potrzebuje starego modelu.

`V1__create_customer_profile.sql` pokazuje stan początkowy. Migracje V1, V2 i V3
są wykonywane przez Flyway w `PostgreSqlExecutableLabTest`, ale nie są podłączone
do startu aplikacji. Test zatrzymuje Flyway po każdej istotnej wersji, sprawdza
stan pośredni, uruchamia osobny backfill i dopiero potem wykonuje contract.

## Dlaczego backfill jest osobnym krokiem?

Jedna aktualizacja milionów rekordów może wygenerować duży WAL, długo utrzymywać blokady, zwiększyć replication lag i utrudnić vacuum. Backfill używa `FOR UPDATE SKIP LOCKED`, dzięki czemu kilka workerów może bezpiecznie pobierać różne partie. Warunek `display_name IS NULL` czyni operację idempotentną i wznawialną.

## Operacje wymagające szczególnej ostrożności

- dodanie kolumny z kosztownym defaultem do dużej tabeli;
- budowa indeksu bez `CONCURRENTLY`;
- zmiana typu kolumny wymagająca przepisania tabeli;
- ustawienie `NOT NULL` bez wcześniejszej walidacji;
- usunięcie lub zmiana nazwy używana jeszcze przez starą wersję aplikacji.

`CREATE INDEX CONCURRENTLY` w PostgreSQL nie może działać wewnątrz zwykłej transakcji. Narzędzie migracyjne musi uruchomić taki krok poza transakcją albo w oddzielnej migracji z odpowiednią konfiguracją.

## Kryteria gotowości przed contract

- backfill nie pozostawia rekordów w starym formacie;
- wszystkie działające wersje aplikacji czytają nowe pole;
- dual-write i błędy migracji są obserwowalne;
- rollback nowej wersji nie wymaga usuniętej kolumny;
- czas blokad i wpływ na repliki zostały sprawdzone na danych zbliżonych do produkcyjnych.
