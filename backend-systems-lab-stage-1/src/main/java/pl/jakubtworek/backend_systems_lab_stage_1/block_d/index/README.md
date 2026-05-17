# Indeksy SQL — Szczegółowe Podsumowanie

# Wprowadzenie

Indeksy w relacyjnych bazach danych są jednym z najważniejszych mechanizmów optymalizacji wydajności, ale jednocześnie należą do najczęściej błędnie rozumianych elementów projektowania systemów bazodanowych.

Bardzo wiele osób traktuje indeks jako uniwersalne rozwiązanie problemów wydajnościowych, podczas gdy w praktyce indeks jest jedynie wyspecjalizowaną strukturą wyszukiwania zoptymalizowaną pod konkretne wzorce dostępu do danych.

Oznacza to, że indeks pomaga tylko wtedy, gdy:
- sposób wykonywania zapytania odpowiada strukturze indeksu,
- workload rzeczywiście korzysta z danego access pattern,
- selektywność danych jest odpowiednia.

---

# Indeksy B-Tree

Najczęściej używanym typem indeksu w systemach OLTP jest indeks B-Tree.

Działa on podobnie do:
- książki telefonicznej,
- katalogu bibliotecznego,
- słownika posortowanego alfabetycznie.

Dane są uporządkowane według klucza indeksu, dzięki czemu baza może bardzo szybko:
- odnajdywać rekordy,
- wykonywać range scans,
- sortować,
- grupować,
- wyszukiwać prefiksy.

---

# Zapytania dobrze współpracujące z B-Tree

## Equality lookup

```sql
WHERE user_id = ?
```

## Range scan

```sql
WHERE created_at BETWEEN ? AND ?
```

## ORDER BY

```sql
ORDER BY created_at DESC
```

## GROUP BY

```sql
GROUP BY user_id
```

---

# Sequential Scan vs Index Scan

Bez indeksu baza wykonuje:

```text
Seq Scan
```

czyli:
- odczytuje całą tabelę,
- sprawdza każdy rekord,
- wykonuje pełny skan danych.

Koszt rośnie liniowo wraz z liczbą rekordów.

Przy małych tabelach Sequential Scan może być:
- całkowicie akceptowalny,
- a czasem nawet szybszy niż użycie indeksu.

Dlaczego?

Ponieważ odczyt sekwencyjny:
- bardzo dobrze współpracuje z cache,
- minimalizuje random I/O,
- jest wydajny dla dysku.

Problem pojawia się przy:
- milionach rekordów,
- dużych tabelach,
- cold cache,
- wysokim concurrency.

---

# Jak działa Index Scan

Po dodaniu indeksu baza:
1. traversuje strukturę B-Tree,
2. odnajduje właściwy zakres,
3. przechodzi bezpośrednio do interesujących rekordów.

Zamiast:
- czytać całą tabelę,
- odczytuje niewielki fragment danych.

Dobrze dobrany indeks może skrócić czas wykonania:
- z sekund,
- do pojedynczych milisekund.

---

# Selektywność danych

Jednym z najważniejszych aspektów indeksów jest selektywność.

Indeks działa najlepiej wtedy, gdy warunek:
- zwraca małą część tabeli,
- znacząco zawęża wynik.

---

# Wysoka selektywność

Bardzo dobre kandydaty do indeksowania:

- user_id,
- email,
- order_id,
- invoice_number,
- transaction_id.

Dlaczego?

Ponieważ:
- posiadają dużo unikalnych wartości,
- filtrują niewielką liczbę rekordów.

---

# Niska selektywność

Problematyczne są kolumny posiadające mało możliwych wartości.

Przykład:

```text
status:
- NEW
- PAID
- CANCELLED
- SHIPPED
- REFUNDED
```

Jeżeli:

```sql
WHERE status = 'PAID'
```

zwraca:
- 20%,
- 30%,
- albo więcej tabeli,

optimizer może całkowicie zignorować indeks.

---

# Dlaczego indeks jest ignorowany

Przy dużej liczbie wyników index scan generuje:
- dużo random I/O,
- wiele heap fetches,
- kosztowny odczyt rozproszonych stron.

W praktyce pełny skan tabeli może być tańszy niż:
1. traversowanie indeksu,
2. pobieranie wskaźników,
3. odczytywanie rekordów z heap storage.

To bardzo częsta sytuacja:
- indeks istnieje,
- ale planner świadomie go nie używa.

---

# Funkcje w WHERE niszczą indeksy

Bardzo ważna zasada:

> klasyczny indeks działa wyłącznie na przechowywanej wartości.

Przykład:

```sql
SELECT *
FROM users
WHERE LOWER(email) = 'john@example.com';
```

Nawet jeśli istnieje:

```sql
CREATE INDEX idx_users_email
ON users(email);
```

baza może nie użyć indeksu.

---

# Dlaczego?

Indeks przechowuje:

```text
John@example.com
```

a nie:

```text
lower(John@example.com)
```

Dla optimizera to zupełnie inne wyrażenie.

---

# Najczęstsze funkcje psujące indeksy

- LOWER()
- UPPER()
- CAST()
- DATE()
- SUBSTRING()
- COALESCE()

---

# Rozwiązania

## 1. Normalizacja danych

Przechowywanie:

```sql
email_lowercase
```

i wykonywanie:

```sql
WHERE email_lowercase = ?
```

---

## 2. Functional Index

```sql
CREATE INDEX idx_users_email_lower
ON users(LOWER(email));
```

---

# Covering Index / Index-Only Scan

Jedna z najpotężniejszych optymalizacji.

Covering index zawiera:
- kolumny filtrowane,
- kolumny sortujące,
- kolumny SELECT,
- kolumny agregowane.

Dzięki temu baza może wykonać:

```text
Index-Only Scan
```

bez dostępu do tabeli.

---

# Przykład

```sql
CREATE INDEX idx_sales_sub_eur
ON sales(subsidiary_id, eur_value);

SELECT SUM(eur_value)
FROM sales
WHERE subsidiary_id = 42;
```

Indeks zawiera:
- subsidiary_id,
- eur_value.

Baza może więc:
- odczytać wyłącznie indeks,
- wykonać agregację bez heap access.

---

# Zalety Index-Only Scan

- minimalizacja I/O,
- mniej heap reads,
- mniej cache misses,
- niższy latency,
- lepszy throughput.

Szczególnie ważne dla:
- analytics,
- dashboardów,
- API,
- agregacji.

---

# Koszty Covering Index

Każdy dodatkowy indeks:
- zwiększa rozmiar danych,
- zwiększa koszt INSERT,
- zwiększa koszt UPDATE,
- zwiększa write amplification.

Indeksy nie są darmowe.

---

# Composite Indexes

Indeksy wielokolumnowe są jednym z najważniejszych mechanizmów optymalizacji.

Jednocześnie są jednym z najczęstszych źródeł błędów projektowych.

---

# Leftmost Prefix Rule

Indeks wielokolumnowy działa:
- od lewej do prawej.

Pierwsza kolumna ma kluczowe znaczenie.

Kolejne działają tylko w kontekście poprzednich.

---

# Zły indeks

```sql
CREATE INDEX idx_bad
ON orders(created_at, user_id);
```

Zapytanie:

```sql
SELECT *
FROM orders
WHERE user_id = 1;
```

nie będzie efektywne.

---

# Dlaczego?

Indeks jest posortowany przede wszystkim po:

```text
created_at
```

a nie po:

```text
user_id
```

Baza nie potrafi efektywnie:
- odnaleźć wszystkich user_id = 1,
- bez znajomości pierwszej kolumny.

---

# Dobry indeks

```sql
CREATE INDEX idx_good
ON orders(user_id, created_at);
```

Teraz baza może:
1. znaleźć user_id,
2. skanować created_at,
3. jednocześnie obsłużyć ORDER BY.

---

# Kolejność kolumn w indeksie

Ogólna zasada:

## Po lewej stronie:
- equality filters,
- najbardziej selektywne kolumny,
- najczęściej używane WHERE.

## Następnie:
- range scans,
- ORDER BY,
- GROUP BY.

## Na końcu:
- dodatkowe kolumny do pokrycia SELECT.

---

# JOIN i indeksy

Brak indeksu na foreign key bardzo często prowadzi do:
- Nested Loop,
- Seq Scan,
- ogromnego kosztu I/O,
- złożoności O(n²).

---

# Zasada

Pola używane w:
- JOIN,
- foreign keys,
- relationship columns

praktycznie zawsze powinny posiadać indeks.

---

# ORDER BY bez indeksu

Jeżeli baza nie posiada odpowiedniego indeksu:
- musi wykonać sortowanie,
- używa pamięci,
- może spillować dane na dysk.

Przykład:

```sql
SELECT *
FROM orders
WHERE user_id = 1
ORDER BY created_at DESC;
```

---

# Optymalny indeks

```sql
CREATE INDEX idx_orders_user_created
ON orders(user_id, created_at DESC);
```

Dzięki temu:
- dane przychodzą już posortowane,
- nie potrzeba dodatkowego Sort operation.

---

# Over-Indexing

Zbyt duża liczba indeksów jest ogromnym problemem produkcyjnym.

Każdy indeks:
- zajmuje miejsce,
- spowalnia INSERT,
- spowalnia UPDATE,
- spowalnia DELETE,
- zwiększa VACUUM cost,
- zwiększa write amplification.

Wiele systemów cierpi bardziej z powodu:
- nadmiaru indeksów,
- niż ich braku.

---

# Najważniejsza zasada

Indeksy optymalizują:
- konkretne zapytania,
- konkretne workloady,
- konkretne access patterns.

Nie istnieje:
- uniwersalny dobry indeks,
- „indeks na wszelki wypadek”.

---

# Dobrze zaprojektowane indeksy

Pozwalają:
- eliminować pełne skany,
- redukować I/O,
- przyspieszać JOIN-y,
- eliminować sortowanie,
- wykonywać Index-Only Scan,
- obniżać latency.

---

# Źle zaprojektowane indeksy

Powodują:
- większy koszt write path,
- większe zużycie storage,
- mylenie optimizera,
- większe koszty utrzymania,
- brak realnego przyspieszenia.

---

# Finalna zasada

> Indeks projektuje się pod realne zapytania i realny workload, a nie „na wszelki wypadek”.