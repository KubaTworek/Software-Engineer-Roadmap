# Plan wykonania zapytania jako źródło prawdy

# Wprowadzenie

Jednym z największych błędów podczas optymalizacji SQL jest podejmowanie decyzji wyłącznie na podstawie:
- intuicji,
- „dobrych praktyk”,
- samego istnienia indeksów,
- wyglądu query.

W praktyce jedynym wiarygodnym źródłem informacji o tym, co naprawdę robi baza danych, jest plan wykonania zapytania.

To właśnie plan pokazuje:
- jakie operacje zostały wykonane,
- ile rekordów przetworzono,
- czy użyto indeksu,
- gdzie powstaje koszt,
- które operacje dominują runtime,
- czy planner podjął dobrą decyzję.

Dlatego:

> EXPLAIN ANALYZE jest podstawowym narzędziem optymalizacji SQL.

---

# EXPLAIN vs EXPLAIN ANALYZE

## EXPLAIN

```sql
EXPLAIN
SELECT * FROM orders WHERE user_id = 123;
```

Pokazuje:
- przewidywany plan wykonania,
- estymacje optimizera,
- szacowany koszt.

Baza:
- nie wykonuje query,
- pokazuje jedynie przewidywania.

---

## EXPLAIN ANALYZE

```sql
EXPLAIN ANALYZE
SELECT * FROM orders WHERE user_id = 123;
```

W tym przypadku PostgreSQL:
- wykonuje query,
- mierzy rzeczywiste czasy,
- pokazuje realną liczbę rekordów,
- porównuje estymacje z rzeczywistością.

To kluczowe, ponieważ planner może się mylić.

Powody:
- nieaktualne statystyki,
- nierównomierny rozkład danych,
- dynamiczny workload,
- skewed data.

---

# Sequential Scan vs Index Scan

Jednym z pierwszych elementów planu, na które należy patrzeć, jest typ skanu tabeli.

---

# Sequential Scan (Seq Scan)

```text
Seq Scan on orders
```

Oznacza:
- pełny skan tabeli,
- odczyt wszystkich rekordów,
- sprawdzenie każdego wiersza.

Koszt rośnie liniowo wraz z rozmiarem tabeli.

---

# Przykład

```sql
SELECT *
FROM orders
WHERE user_id = 123;
```

bez indeksu bardzo często wykona:

```text
Seq Scan
```

Przy milionach rekordów oznacza to:
- ogromny koszt I/O,
- duży runtime,
- wysoki latency.

---

# Index Scan

Po dodaniu indeksu:

```sql
CREATE INDEX idx_orders_user_id
ON orders(user_id);
```

plan może zmienić się na:

```text
Index Scan using idx_orders_user_id
```

lub:

```text
Bitmap Index Scan
```

---

# Co to oznacza?

Baza:
1. traversuje strukturę B-Tree,
2. odnajduje pasujące rekordy,
3. odczytuje jedynie niewielki fragment tabeli.

Dzięki temu:
- liczba odczytanych stron spada,
- I/O maleje,
- query działa znacznie szybciej.

---

# Ważna uwaga

Seq Scan nie zawsze jest błędem.

Dla:
- małych tabel,
- bardzo niskiej selektywności,
- dużego procentu pasujących rekordów,

pełny skan może być tańszy niż index scan.

Dlatego:
> sam fakt występowania Seq Scan nie oznacza automatycznie problemu.

---

# Filter vs Index Cond

Jednym z najważniejszych elementów planu jest różnica pomiędzy:

- Filter,
- Index Cond.

---

# Index Cond

```text
Index Cond: (user_id = 123)
```

To bardzo dobra sytuacja.

Oznacza:
- warunek został użyty bezpośrednio podczas traversowania indeksu,
- baza od razu zawęża zakres skanowania,
- mniej rekordów jest odczytywanych.

---

# Filter

```text
Filter: (total_amount > 1000)
```

Oznacza:
1. rekord został pobrany,
2. dopiero później sprawdzono warunek.

To znaczy, że część pracy została wykonana niepotrzebnie.

Duża liczba Filter bardzo często oznacza:
- źle zaprojektowany indeks,
- brak composite index,
- złą kolejność kolumn.

---

# Sort jako kosztowna operacja

Jednym z najdroższych elementów planu może być:

```text
Sort
```

---

# Problem

Jeżeli query zawiera:

```sql
ORDER BY created_at DESC
```

a baza nie posiada odpowiedniego indeksu, PostgreSQL musi:
1. odczytać rekordy,
2. zbudować strukturę sortującą,
3. posortować dane,
4. czasem spillować dane na dysk.

---

# Dlaczego Sort jest kosztowny?

Sortowanie jest:
- CPU-intensive,
- memory-intensive,
- disk-intensive.

Przy dużych datasetach Sort może być jedną z najdroższych operacji.

---

# Rozwiązanie

Tworzenie indeksów wspierających jednocześnie:
- WHERE,
- ORDER BY.

Przykład:

```sql
CREATE INDEX idx_orders_user_created
ON orders(user_id, created_at DESC);
```

Teraz baza:
- filtruje po user_id,
- jednocześnie zwraca dane już posortowane.

W planie:
- znika osobna operacja Sort.

---

# Nested Loop vs Hash Join vs Merge Join

Podczas analizy JOIN-ów należy patrzeć na algorytm łączenia tabel.

---

# Nested Loop

Nested Loop działa dobrze dla:
- małych tabel,
- małych result setów,
- sytuacji z dobrym indeksem po stronie wewnętrznej.

Jednak:

```text
Nested Loop + Seq Scan
```

na dużych tabelach może być katastrofalne.

---

# Dlaczego?

Dla każdego rekordu:
- tabela B jest skanowana ponownie.

To może prowadzić do:
- ogromnego I/O,
- bardzo wysokiego latency,
- złożoności zbliżonej do O(n²).

---

# Hash Join

Hash Join zwykle działa dobrze dla:
- większych datasetów,
- equality joins.

PostgreSQL:
1. buduje hash table,
2. wykonuje lookup po hashach.

---

# Merge Join

Merge Join działa dobrze gdy:
- dane są posortowane,
- lub mogą zostać efektywnie posortowane.

---

# Nie istnieje najlepszy JOIN

Wybór zależy od:
- rozmiaru danych,
- selektywności,
- indeksów,
- memory settings,
- workloadu.

---

# Estimated Rows vs Actual Rows

Jednym z najważniejszych elementów planu są:

```text
rows=...
actual rows=...
```

---

# Problem błędnych estymacji

Przykład:

```text
rows=10
actual rows=100000
```

oznacza ogromny problem statystyk.

Planner:
- wybiera plan na podstawie przewidywań,
- nie zna realnych danych w czasie wykonania.

Jeżeli estymacje są błędne, optimizer może:
- wybrać zły join algorithm,
- nie użyć indeksu,
- źle oszacować koszt sortowania,
- wybrać fatalny access path.

---

# Przyczyny błędnych estymacji

- nieaktualne statystyki,
- skewed data,
- dynamiczne dane,
- nierównomierny rozkład wartości.

---

# Rozwiązania

## ANALYZE

```sql
ANALYZE orders;
```

## Większy statistics target

```sql
ALTER TABLE orders
ALTER COLUMN user_id
SET STATISTICS 1000;
```

---

# OFFSET Pagination jako anty-pattern

Bardzo częsty problem wydajnościowy API.

---

# OFFSET

```sql
SELECT *
FROM orders
ORDER BY id
OFFSET 50000
LIMIT 50;
```

Na pierwszy rzut oka wygląda niewinnie.

---

# Problem

PostgreSQL musi:
1. znaleźć rekordy,
2. posortować je,
3. przejść przez pierwsze 50000,
4. zignorować je,
5. zwrócić dopiero kolejne 50.

Koszt rośnie liniowo wraz z OFFSET.

---

# Duży OFFSET

```text
OFFSET 100000
OFFSET 1000000
```

może być bardzo kosztowny.

---

# Keyset Pagination

Znacznie lepsze rozwiązanie.

```sql
SELECT *
FROM orders
WHERE id > 50000
ORDER BY id
LIMIT 50;
```

---

# Dlaczego jest szybsze?

Baza:
- nie musi pomijać rekordów,
- może bezpośrednio przejść do odpowiedniego miejsca indeksu.

To:
- redukuje I/O,
- utrzymuje stały runtime,
- działa dobrze przy dużych datasetach.

---

# Gdzie używać keyset pagination?

- duże API,
- infinite scroll,
- systemy high throughput,
- bardzo duże tabele.

---

# Execution Time jako finalna metryka

Każda optymalizacja powinna kończyć się porównaniem:

- planu przed,
- planu po,
- Execution Time przed,
- Execution Time po.

---

# Nie optymalizuj „na oko”

Najlepszą praktyką jest dokumentowanie:
- rodzaju skanu,
- liczby rekordów,
- kosztu,
- czasu wykonania,
- liczby odczytanych bloków,
- użytego join algorithm.

Dopiero wtedy można realnie ocenić:
- czy optymalizacja działa,
- czy indeks przynosi korzyść,
- czy query rzeczywiście stało się szybsze.

---

# Najważniejsza zasada

> Nie zgaduj. Sprawdzaj plan wykonania.

To właśnie plan wykonania pokazuje:
- co naprawdę robi baza,
- dlaczego query jest wolne,
- gdzie powstaje koszt,
- czy indeks działa,
- które operacje dominują runtime.

Bez EXPLAIN ANALYZE optymalizacja SQL bardzo często zamienia się w zgadywanie zamiast inżynierii.