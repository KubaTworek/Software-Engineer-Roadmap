# Wykonywalne laboratorium PostgreSQL

Skrypty w tym katalogu pozostają materiałem do ręcznej analizy, ale ich
najważniejsze gwarancje sprawdza również `PostgreSqlExecutableLabTest` na
prawdziwym PostgreSQL 16. Suite używa JDBC, Flyway i Testcontainers. Nie korzysta
z H2, ponieważ plany wykonania, MVCC, poziomy izolacji i kody błędów constraintów
są cechami konkretnego silnika.

## Mapa eksperymentów

| Eksperyment | Co jest wykonywane | Co test rzeczywiście dowodzi |
|---|---|---|
| expand–migrate–contract | Flyway V1 → V2, osobny backfill, Flyway V3 | kolejne wersje są wdrażalne, dane zostają uzupełnione przed `NOT NULL`, stare kolumny pozostają na czas rollbacku |
| indeks | `EXPLAIN (ANALYZE, BUFFERS)` dla selektywnego access patternu | PostgreSQL wybiera właściwy indeks i raportuje rzeczywistą pracę oraz bufory |
| paginacja | głęboki `OFFSET` i keyset na tym samym zbiorze | offset musi odwiedzić pomijane rekordy, keyset zaczyna od kursora |
| izolacja | dwa połączenia JDBC i commit między odczytami | `READ COMMITTED` odświeża snapshot per statement, `REPEATABLE READ` zachowuje snapshot transakcji |
| optimistic locking | dwa odczyty tej samej wersji, dwa warunkowe `UPDATE` | `UPDATE ... WHERE version = ?` pozwala wygrać jednemu writerowi, a staremu zwraca zero zmienionych wierszy |
| invariants | zapisy omijające kod aplikacji | `CHECK`, `UNIQUE` i `FOREIGN KEY` odrzucają niepoprawny stan właściwymi SQLSTATE |

## Dlaczego nie porównujemy czasu zapytań

Test automatyczny nie powinien wymagać, aby keyset był zawsze określoną liczbę
milisekund szybszy. Wynik zależy od sprzętu, obciążenia, cache'a, konfiguracji
PostgreSQL i stanu statystyk. Stabilniejszą obserwacją jest ilość wykonanej pracy:

- typ skanu i nazwa użytego indeksu,
- `Actual Rows` na węzłach planu,
- liczba wierszy odrzuconych przez filtr,
- bufory trafione, odczytane i zapisane,
- różnica między estymacją `rows` a rzeczywistym `actual rows`.

W eksperymencie z paginacją głęboki `OFFSET 40000` musi przejść co najmniej przez
40 020 pozycji, aby zwrócić 20 rekordów. Keyset z warunkiem po indeksowanym `id`
odwiedza najwyżej rozmiar strony. Nie zapisujemy czasu uzyskanego na jednym
uruchomieniu jako uniwersalnego progu wydajności.

## Granica migracji Flyway

Flyway wykonuje wersjonowane migracje:

1. `V1__create_customer_profile.sql`,
2. `V2__expand_display_name.sql`,
3. `V3__contract_display_name.sql`.

[`backfill/backfill_display_name.sql`](backfill/backfill_display_name.sql) pozostaje
osobnym, wznawialnym procesem operacyjnym i nie jest migracją wersjonowaną Flyway.
Test uruchamia go jawnie pomiędzy V2 i V3. W produkcji pojedyncza migracja Flyway
nie powinna utrzymywać transakcji przez wielogodzinny backfill dużej tabeli.
Contract można wdrożyć dopiero po metryce potwierdzającej brak rekordów
`display_name IS NULL` i po wycofaniu starej wersji aplikacji.

## Interpretacja optimistic lockingu

Zero zmienionych rekordów nie jest technicznym sukcesem. Oznacza konflikt wersji.
Aplikacja powinna wtedy:

1. wycofać lokalną operację,
2. odczytać najnowszy stan,
3. zdecydować, czy operację można bezpiecznie powtórzyć,
4. albo zwrócić użytkownikowi jawny konflikt.

Automatyczny retry ma sens tylko wtedy, gdy logika biznesowa może zostać ponownie
przeliczona na aktualnym stanie. Nie wolno bezmyślnie nadpisywać wersji zwycięzcy.

## Uruchomienie

Wymagany jest działający Docker. Z katalogu `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests '-Dtest=PostgreSqlExecutableLabTest' test
```

Test używa obrazu `postgres:16-alpine` i tagu `infrastructure`. Zwykłe `verify`
go nie wybiera, natomiast profil `infrastructure-tests` uruchamia go i kończy
się błędem bez działającego Dockera. Dzięki temu wynik profilu jest dowodem
wykonania eksperymentów PostgreSQL, a nie jedynie ich kompilacji.
