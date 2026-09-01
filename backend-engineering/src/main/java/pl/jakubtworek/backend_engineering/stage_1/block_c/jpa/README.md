# jpa

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** jpa.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „jpa” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=JpaQueryBehaviorTest" test`
> - **Role klas:** `UserController` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## JPA i Hibernate jako świadoma warstwa dostępu do danych



## Cel laboratorium

JPA mapuje model obiektowy na relacyjny, ale nie usuwa kosztu SQL, sieci ani ograniczeń bazy. Kod powinien być oceniany jednocześnie na trzech poziomach:

1. invariant domenowy,
2. zachowanie persistence contextu,
3. wygenerowany SQL i jego plan wykonania.

Laboratorium używa osobnych tabel `jpa_users` i `jpa_orders`. Nie współdzieli ich z encjami innych ćwiczeń tylko dlatego, że klasy mają podobne nazwy. Własność schematu i znaczenie danych muszą być jawne.

## Mapa kodu

| Element | Zagadnienie |
|---|---|
| `User` | agregat, walidacja, relacja lazy i dwustronne utrzymanie powiązania |
| `Order` | encja zależna i jawne `ManyToOne(fetch = LAZY)` |
| `UserRepository.findAllWithOrders` | usunięcie N+1 przez fetch join dla małego, niepaginowanego zbioru |
| `UserRepository.findNextSlice` | keyset pagination po stabilnej parze `(lastName, id)` |
| `UserService` | granica transakcji i mapowanie encji na DTO wewnątrz persistence contextu |
| `GET /users/cursor` | kontrakt kolejnej strony z dwuczęściowym cursorem |
| `UserJdbcRepository` | jawny SQL jako alternatywa dla zapytania, do którego ORM nie pasuje |

## Persistence context

W ramach jednej sesji Hibernate utrzymuje identity map: jeden rekord odpowiada jednej zarządzanej instancji encji. Zmiana tej instancji jest wykrywana przez dirty checking i wysyłana podczas flush. `save()` nie jest więc potrzebne po każdej zmianie zarządzanej encji.

Flush synchronizuje SQL z bazą, lecz nie jest commitem. Constraint, konflikt wersji albo błąd commitu nadal może wycofać transakcję. `readOnly = true` jest wskazówką optymalizacyjną, a nie uniwersalnym zabezpieczeniem przed zapisem.

Encja po wyjściu z transakcji staje się detached. Zwracanie encji bezpośrednio z kontrolera utrudnia kontrolę lazy loadingu, kontraktu API i liczby zapytań. Serwis zwraca DTO zawierające dokładnie dane potrzebne danemu use case'owi.

## N+1 nie jest problemem adnotacji

N+1 powstaje, gdy najpierw pobieramy N encji, a później uruchamiamy osobne zapytanie przy dostępie do relacji każdej z nich. Problemem jest liczba round-tripów i niejawny koszt, a nie samo `LAZY`.

Warianty rozwiązania:

| Technika | Dobra dla | Ryzyko |
|---|---|---|
| fetch join | jeden mały graf bez paginacji kolekcji | mnożenie wierszy i duży koszt hydracji |
| `@EntityGraph` | różne plany pobierania dla prostych zapytań | nadal może tworzyć duży join |
| batch fetching | wiele relacji ładowanych porcjami | kilka zapytań i potrzeba strojenia batch size |
| projekcja DTO | endpoint o znanym kształcie odczytu | osobny model odczytowy i brak pełnej encji |
| dwa zapytania: IDs + relacje | paginacja rodziców z kolekcją | większa złożoność repozytorium |

Nie paginuj kolekcji przez jeden `join fetch`. `LIMIT` działa na fizycznych wierszach wyniku SQL, nie na logicznych encjach nadrzędnych. Najpierw wybierz stronę identyfikatorów rodziców, a następnie pobierz relacje dla tego ograniczonego zbioru.

Test N+1 powinien mierzyć liczbę przygotowanych statementów po wyczyszczeniu persistence contextu. Sam poprawny wynik funkcjonalny nie wykrywa regresji zapytań.

## Offset i keyset pagination

`Pageable` zwykle generuje `OFFSET`. Jest wygodne dla płytkich, numerowanych stron, ale koszt głębokiej strony rośnie wraz z liczbą pomijanych rekordów. `Page` uruchamia też zapytanie `COUNT`, które dla złożonego filtra może być droższe niż pobranie danych.

Keyset pagination nie pyta „pomiń N”, lecz „pobierz rekordy po ostatnim widzianym kluczu”. Laboratorium sortuje po `(lastName ASC, id ASC)` i używa tego samego układu w kursorze oraz indeksie. `id` jest tie-breakerem, dzięki któremu osoby o tym samym nazwisku nie znikają między stronami.

Warunek następnej strony:

```sql
WHERE last_name > :last_name
   OR (last_name = :last_name AND id > :id)
ORDER BY last_name, id
LIMIT :page_size_plus_one;
```

Kursor powinien być nieprzezroczysty dla klienta, wersjonowany i podpisany, jeśli zawiera dane, których klient nie może zmieniać. `Slice` pozwala ustalić `hasNext` bez pełnego `COUNT`.

Keyset zapewnia stabilne przechodzenie względem klucza, ale nie tworzy snapshotu całego przeglądania. Aktualizacja kolumn sortowania pomiędzy requestami może przesunąć rekord. Jeśli wymagany jest historyczny snapshot, trzeba dodać granicę czasu, wersję zbioru albo wykonać eksport poza zwykłym API stronicowanym.

## Constrainty są częścią modelu

Walidacja w Javie daje szybki, czytelny błąd, ale może zostać ominięta przez inny proces, skrypt lub wyścig requestów. Baza powinna chronić reguły, które potrafi wyrazić:

- `NOT NULL` dla danych wymaganych,
- `UNIQUE` dla kluczy naturalnych i idempotency keys,
- `FOREIGN KEY` dla relacji będących w tej samej granicy własności,
- `CHECK` dla prostych zakresów i zamkniętych zbiorów statusów,
- właściwy typ i precyzję dla pieniędzy oraz czasu.

Sprawdzenie „czy istnieje?” w aplikacji nie zastępuje `UNIQUE`, bo dwa requesty mogą przejść check równocześnie. Kod powinien przechwycić naruszenie constraintu i przetłumaczyć je na błąd domenowy lub konflikt API.

## Indeksy wynikają z zapytań

Indeks jest kopią wybranych danych utrzymywaną przy każdym zapisie. Projektuj go od konkretnego filtra, joinu i sortowania. Dla keysetu `(last_name, id)` potrzebny jest indeks w tej samej kolejności. Indeks na samym `last_name` nie obsługuje całego porządku równie dobrze.

Przy planie wykonania sprawdzaj:

- `actual rows` względem estymacji,
- liczbę pętli operatora,
- `rows removed by filter`,
- `Buffers` i odczyty z dysku,
- sortowanie oraz jego pamięć/dysk,
- algorytm joinu,
- czas planowania i wykonania.

`Seq Scan` na małej tabeli lub zapytaniu zwracającym większość danych może być najlepszym planem. Celem nie jest wymuszenie indeksu, lecz minimalny koszt dla realnego workloadu.

## Batch processing

`saveAll` nie gwarantuje jednego batcha SQL. Wpływają na to strategia generowania ID, ustawienia `hibernate.jdbc.batch_size`, kolejność statementów i moment flush. Przy dużym imporcie przetwarzaj porcjami oraz okresowo wykonuj `flush()` i `clear()`, aby persistence context nie zatrzymał wszystkich encji w pamięci.

Batch ma ograniczony rozmiar, timeout i sposób wznowienia. Jedna transakcja na milion rekordów długo trzyma zasoby, powiększa rollback i utrudnia odzyskanie po błędzie. Wiele małych transakcji wymaga natomiast idempotentnego checkpointu.

## Connection pool

Pool nie zwiększa pojemności bazy. Zbyt duży pool mnoży konkurujące zapytania, pamięć bazy i context switching. Budżet połączeń musi uwzględniać wszystkie instancje aplikacji, migracje, zadania administracyjne i rezerwę operacyjną.

Rozmiar poola dobieraj na podstawie limitu bazy oraz pomiarów czasu oczekiwania na połączenie, czasu zapytania i liczby aktywnych połączeń. Długi czas oczekiwania nie zawsze oznacza „dodaj połączeń” — może wskazywać wolne SQL, lock contention lub zbyt długą transakcję.

Praktyczny kalkulator i ograniczenia modelu znajdują się w `stage_1/block_d/sql/connection_pool`.

## Migracje bez przestoju

Dla wdrożeń, w których stara i nowa wersja aplikacji działają równocześnie, używaj sekwencji expand–migrate–contract:

1. **expand** — dodaj kompatybilną strukturę, np. nullable column lub nową tabelę,
2. **migrate** — wdroż dual read/write i wykonaj backfill małymi porcjami,
3. **contract** — dopiero po wyłączeniu starego kodu dodaj `NOT NULL`, usuń starą kolumnę lub indeks.

`ALTER TABLE` może blokować ruch albo przepisać tabelę. Przed migracją sprawdź zachowanie konkretnej wersji PostgreSQL, ustaw `lock_timeout`, oszacuj czas i przygotuj sposób przerwania. Rollback aplikacji po migracji destrukcyjnej może być niemożliwy, dlatego kompatybilność wstecz jest częścią strategii wdrożenia.

Przykładowe kroki znajdują się w `stage_1/block_d/sql/migration`.

## Granice laboratorium

- H2 w testach potwierdza mapowanie i zachowanie JPA, lecz nie plan PostgreSQL.
- Pliki SQL dla `EXPLAIN (ANALYZE, BUFFERS)` należy uruchamiać na PostgreSQL z reprezentatywną ilością danych.
- Liczby z lokalnego benchmarku nie są SLO produkcyjnym.
- Model NoSQL zaczyna się od access patternu; nie jest automatycznym lekarstwem na wolny SQL.

## Pytania kontrolne

1. Jaki invariant chroni Java, a jaki constraint bazy?
2. Ile statementów wykonuje endpoint dla 1, 20 i 100 rekordów?
3. Czy indeks odpowiada filtrowaniu i kolejności sortowania?
4. Czy paginacja wymaga numeru strony, czy wystarczy next/previous?
5. Czy migracja działa przy równoległej starej i nowej wersji aplikacji?
6. Czy pool respektuje globalny limit połączeń po autoskalowaniu?
7. Czy dodatkowy magazyn danych ma jawnego właściciela i sposób odbudowy?
