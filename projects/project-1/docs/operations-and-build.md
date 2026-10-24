# Booking — budżet zasobów, baza i build

## Problem i dowód

Rezerwacje mogą być poprawne transakcyjnie, a replika nadal może zostać zabita
przez kernel lub przestać przyjmować sockety. `BookingMemoryBudget` sprawdza,
czy Xmx i jawna rezerwa natywna mieszczą się w limicie. Test obejmuje także overflow;
nie traktuje tego rachunku jako pomiaru RSS. Przykładowo 480 MiB heap + 100 MiB
rezerwy nie mieści się w kontenerze 512 MiB, niezależnie od chwilowo wolnego heapu.

Z `project-1`:

`````powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=BookingMemoryBudgetTest" test
pwsh -File scripts/process-diagnostics.ps1 -Container NAZWA_REPLIKI_BOOKING
.\mvnw.cmd --batch-mode --no-transfer-progress dependency:tree
```

Podaj faktyczną nazwę kontenera Booking; nie uruchamiaj przykładowej nazwy dosłownie.
Skrypt odczytuje `/proc/1`, limity cgroup v2 i Docker State. Wymaga JVM jako PID 1
oraz Linux Engine. Zestaw z JFR/JMX i [laboratorium Linux](../../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_g/README.md).
Przed soak testem zanotuj Xmx, limit, native reserve, baseline liczby deskryptorów
i CPU quota. Po teście porównaj trendy, nie tylko p99 ostatniego requestu.

## Operacje na bazie

[scripts/database-observations.sql](../scripts/database-observations.sql) jest plikiem
odczytu dla psql: uruchom przez `psql -v ON_ERROR_STOP=1 -f scripts/database-observations.sql`
na bazie Booking. Connection string przekazuj poza historią poleceń zgodnie
z konfiguracją lokalną. Skrypt nie wykonuje terminate backend ani VACUUM FULL.

Przy rosnącym czasie rezerwacji: blokująca transakcja → lock wait → pula połączeń
→ timeouty klientów. Zbierz wiek transakcji, blockers, statystyki tabel i plan.
Autovacuum/n_dead_tup nie zastępują pomiaru bloat. DDL z krótkim lock_timeout
może się nie udać i wymaga ponownego zaplanowania, nie wyłączenia limitu w ciemno.

## Aktualizacja i powtarzalność

`dependency:tree` oraz `help:effective-pom` pokazują rzeczywiste wersje aplikacji.
Przed aktualizacją kontraktu HTTP lub Hibernate zachowaj baseline testów lifecycle,
transakcji i security; potem uruchom pełne verify i profil infrastrukturalny.
Ćwiczenie prawdziwej niezgodności binarnej jest w [bloku build](../../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_h/README.md).

Porównanie hash JAR wymaga tego samego JDK, wrappera, źródeł i outputTimestamp;
dwa udane buildy nie dowodzą identycznego artefaktu. Nie modyfikuj main flow
rezerwacji w celu odtworzenia awarii pamięci — służy do tego izolowany probe.
Z katalogu project-1 wykonaj

```powershell
pwsh -File scripts/check-reproducible-build.ps1
```

Skrypt porównuje dwa czyste buildy z ustalonym outputTimestamp. Pomija testy,
bo mierzy artefakt po osobnym verify. Wynik dotyczy tego samego lokalnego
JDK i repozytorium zależności, nie dowodzi zgodności dwóch różnych maszyn.

## Bezpieczeństwo rotacji i cache po COMMIT

AuthService.refresh pobiera refresh token przez PESSIMISTIC_WRITE. Odczyt
aktywności, revoke i zapis następcy mają jedną transakcję. Test na PostgreSQL
uruchamia dwa rzeczywiste wywołania proxy i wymaga jednego sukcesu, jednej odmowy
oraz jednego aktywnego następcy. Ten projekt nie implementuje rodzin/reuse policy
z laboratorium 1C; blokuje podwójne wydanie z tego samego tokenu. Clock steruje
czasem rotacji zamiast Instant.now rozproszonego po serwisie.

CacheInvalidation uruchamia każdą invalidację dopiero po COMMIT i izoluje jej
wyjątek, aby awaria Redis nie zamieniła zatwierdzonej rezerwacji w błąd odpowiedzi.
Niepowodzenie jest logowane, a stary odczyt ograniczony TTL. Nie jest to trwały
retry ani atomowe DB+Redis. Rezerwowanie miejsc zawsze sprawdza SQL, więc stary
cache nie sprzedaje dodatkowych miejsc. Przy wymaganiu natychmiastowej świeżości
potrzebny jest odczyt primary albo trwały mechanizm invalidacji.
