# OFFSET vs Keyset Pagination — szczegółowe podsumowanie

# Wprowadzenie

Paginacja jest jednym z podstawowych mechanizmów praktycznie każdej aplikacji backendowej. Występuje w:
- API,
- dashboardach,
- tabelach administracyjnych,
- infinite scroll,
- feedach,
- listach zamówień,
- komunikatorach,
- logach,
- systemach analitycznych.

Na pierwszy rzut oka problem wydaje się prosty:
- pobrać kolejną „stronę” rekordów.

Jednak sposób implementacji paginacji ma ogromny wpływ na:
- wydajność,
- zużycie pamięci,
- ilość I/O,
- skalowalność systemu,
- stabilność latency.

Dwa najpopularniejsze podejścia to:
- OFFSET pagination,
- keyset pagination (seek pagination / cursor pagination).

Choć logicznie rozwiązują ten sam problem, działają zupełnie inaczej na poziomie silnika bazy danych.

---

# OFFSET Pagination

Najbardziej klasyczna forma paginacji wygląda tak:

```sql
SELECT *
FROM orders
ORDER BY id
LIMIT 10 OFFSET 1000;
```

Znaczenie:
- pomiń pierwsze 1000 rekordów,
- zwróć kolejne 10.

To bardzo intuicyjny model:
- łatwo implementować numerowane strony,
- łatwo przechodzić do page=5,
- łatwo liczyć page count.

Dlatego OFFSET jest bardzo popularny.

---

# Jak działa OFFSET wewnętrznie

Problem polega na tym, że PostgreSQL nie „teleportuje się” do offsetu.

Baza musi:
1. znaleźć rekordy,
2. uporządkować je,
3. przejść przez OFFSET rekordów,
4. odrzucić je,
5. zwrócić dopiero LIMIT rekordów.

To kluczowy problem.

---

# Dlaczego OFFSET jest kosztowny

Przykład:

```sql
LIMIT 10 OFFSET 100000
```

zwraca:
```text
10 rekordów
```

ale PostgreSQL musi:
- przejść przez około 100000 rekordów,
- odczytać je,
- pominąć,
- dopiero potem zwrócić wynik.

Koszt:
- rośnie liniowo wraz z OFFSET,
- mimo że wynik końcowy jest bardzo mały.

---

# OFFSET i indeksy

Bardzo częste nieporozumienie:

> „mam indeks, więc OFFSET będzie szybki”.

Nie do końca.

Nawet mając indeks:
```sql
ORDER BY id
```

PostgreSQL nadal:
- traversuje indeks od początku,
- przechodzi przez kolejne wpisy,
- ignoruje wcześniejsze rekordy.

Indeks pomaga:
- utrzymać kolejność,
- uniknąć pełnego sortowania,

ale:
- nie eliminuje kosztu skipowania rekordów.

---

# Wzrost kosztu OFFSET

To fundamentalna cecha OFFSET pagination.

Przykładowo:

| OFFSET | Koszt |
|---|---|
| 0 | bardzo niski |
| 1000 | niski |
| 10000 | średni |
| 100000 | wysoki |
| 1000000 | bardzo wysoki |

Runtime rośnie mniej więcej liniowo z OFFSET.

To oznacza:
- im „dalsza” strona,
- tym większy koszt query.

---

# Dlaczego to jest problem produkcyjny

W małych tabelach problem często nie istnieje.

Jednak dla:
- milionów rekordów,
- dużych feedów,
- activity logów,
- event streamów,
- systemów OLTP,

duże OFFSET-y zaczynają:
- generować ogromne I/O,
- obciążać CPU,
- zwiększać latency,
- destabilizować performance.

---

# Keyset Pagination

Alternatywą jest:
```text
keyset pagination
```

nazywana również:
- seek pagination,
- cursor pagination.

Idea jest zupełnie inna.

Zamiast:
```text
„pomijaj N rekordów”
```

mówimy:
```text
„zacznij po konkretnym kluczu”
```

---

# Podstawowy przykład

Zamiast:

```sql
LIMIT 10 OFFSET 100000
```

używamy:

```sql
SELECT *
FROM orders
WHERE id > 100000
ORDER BY id
LIMIT 10;
```

To fundamentalna różnica.

---

# Jak działa keyset pagination

PostgreSQL:
- wykorzystuje indeks,
- odnajduje miejsce startowe,
- zaczyna czytać dopiero od wskazanego klucza.

Nie musi:
- przechodzić przez wcześniejsze rekordy,
- odrzucać danych,
- wykonywać kosztownego skipowania.

---

# Dlaczego keyset jest szybki

Najważniejsza cecha:

> koszt zależy głównie od LIMIT,
> a nie od numeru strony.

Przykład:

```sql
WHERE id > 500000
LIMIT 10
```

jest zwykle bardzo szybkie, ponieważ:
- baza wchodzi bezpośrednio w odpowiednie miejsce indeksu,
- czyta tylko niewielki fragment danych.

---

# OFFSET vs Keyset — fundamentalna różnica

OFFSET:
```text
przejdź przez wcześniejsze rekordy
i je odrzuć
```

Keyset:
```text
przejdź bezpośrednio
do odpowiedniego miejsca indeksu
```

To właśnie dlatego keyset skaluje się znacznie lepiej.

---

# Złożoność wydajnościowa

W uproszczeniu:

## OFFSET
Koszt:
```text
O(offset + limit)
```

## Keyset
Koszt:
```text
O(limit)
```

To ogromna różnica dla dużych datasetów.

---

# Stable Ordering

Keyset pagination wymaga:
```text
stabilnego ORDER BY
```

To bardzo ważne.

Przykład:

```sql
ORDER BY created_at DESC
```

może być problematyczny, ponieważ:
- wiele rekordów może mieć ten sam timestamp.

---

# Tie-breaker

Dlatego zwykle dodaje się:
```sql
ORDER BY created_at DESC, id DESC
```

`id` pełni rolę:
```text
tie-breakera
```

Dzięki temu:
- kolejność jest deterministyczna,
- rekordy nie są pomijane,
- rekordy nie pojawiają się podwójnie.

---

# Cursor Pagination

Keyset pagination bardzo często używa:
```text
cursorów
```

API zwraca:
- dane,
- oraz cursor następnej strony.

Przykład:
```json
{
  "items": [...],
  "next_cursor": "500000"
}
```

Kolejne zapytanie:

```sql
WHERE id > 500000
```

---

# Infinite Scroll

Dlatego keyset pagination idealnie nadaje się do:
- infinite scroll,
- feedów społecznościowych,
- timeline,
- chatów,
- event streamów,
- API typu next/prev.

To obecnie standard dużych systemów.

---

# Problem random access

Największą wadą keyset pagination jest brak wygodnego:
```text
„skocz do strony 57”
```

OFFSET bardzo dobrze obsługuje:
- page numbers,
- random access pages.

Keyset:
- działa głównie dla next/previous,
- nie nadaje się do łatwego przeskakiwania.

---

# Czy to realny problem?

W praktyce:
- większość nowoczesnych UI,
- i tak używa infinite scroll,
- albo next/prev.

Użytkownicy rzadko:
- przechodzą bezpośrednio do strony 731.

Dlatego:
- ograniczenie keyset często nie ma znaczenia UXowego.

---

# Keyset a consistency

Keyset pagination ma również przewagę consistency.

Przy OFFSET:
- nowe rekordy mogą zmieniać numerację stron,
- rekordy mogą „przeskakiwać” pomiędzy stronami.

Przykład:
1. pobieramy page 1,
2. ktoś dodaje nowe rekordy,
3. page 2 może zawierać:
    - duplikaty,
    - pominięte rekordy.

---

# Keyset jest bardziej stabilny

Ponieważ:
- paginacja opiera się na konkretnym kluczu,
- a nie na pozycji rekordów.

To szczególnie ważne dla:
- dynamicznych feedów,
- systemów realtime,
- event streams.

---

# Composite Keyset Pagination

Często keyset używa wielu kolumn.

Przykład:

```sql
WHERE (created_at, id)
    < ('2025-01-01', 500000)
ORDER BY created_at DESC, id DESC
LIMIT 10;
```

To bardzo ważny wzorzec produkcyjny.

---

# Indeksy dla keyset pagination

Keyset wymaga odpowiednich indeksów.

Przykład:

```sql
CREATE INDEX idx_orders_user_id
ON orders(user_id, id);
```

lub:

```sql
CREATE INDEX idx_orders_feed
ON orders(user_id, created_at DESC, id DESC);
```

Bez indeksu:
- keyset traci większość zalet.

---

# OFFSET nadal ma zastosowania

OFFSET nie jest „zły”.

Jest bardzo dobry dla:
- małych tabel,
- małych OFFSET,
- admin paneli,
- raportów,
- systemów wymagających page numbers,
- prostych CRUD UI.

Problem zaczyna się przy:
- bardzo dużych tabelach,
- głębokich stronach,
- wysokim concurrency.

---

# Kiedy wybierać OFFSET

OFFSET sprawdza się gdy:
- dataset jest mały,
- query są rzadkie,
- potrzebne są numerowane strony,
- UX wymaga random access.

---

# Kiedy wybierać keyset

Keyset jest lepszy gdy:
- tabela jest bardzo duża,
- API działa pod dużym loadem,
- używany jest infinite scroll,
- ważny jest stabilny latency,
- potrzebna jest wysoka skalowalność.

---

# Największy błąd projektowy

Najczęstszym błędem jest:
- używanie OFFSET dla ogromnych tabel,
- bez świadomości liniowego kosztu skipowania rekordów.

Lokalnie query może działać szybko.

Produkcyjnie:
- OFFSET 500000,
- OFFSET 5000000

potrafi dramatycznie obciążyć bazę.

---

# Najważniejsza praktyczna zasada

OFFSET:
```text
skanuje i odrzuca rekordy
```

Keyset:
```text
wyszukuje miejsce startowe
w indeksie
```

To fundamentalna różnica architektoniczna.

Dlatego:
- OFFSET jest prostszy,
- ale gorzej się skaluje.

Keyset:
- wymaga bardziej świadomego projektu,
- ale daje znacznie lepszą wydajność dla dużych systemów.