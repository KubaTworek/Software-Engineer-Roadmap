# Problem N+1 w ORM — szczegółowe podsumowanie

# Wprowadzenie

Problem N+1 jest jednym z najczęstszych i najbardziej kosztownych problemów wydajnościowych występujących w ORM-ach. Dotyczy praktycznie wszystkich popularnych frameworków:
- Hibernate,
- JPA,
- Entity Framework,
- Django ORM,
- SQLAlchemy,
- ActiveRecord.

Bardzo często aplikacja działa poprawnie funkcjonalnie, ale pod większym obciążeniem nagle okazuje się, że baza danych wykonuje:
- setki,
- tysiące,
- albo dziesiątki tysięcy zapytań,

mimo że logicznie problem wydaje się prosty.

Najczęściej przyczyną jest właśnie:
```text
N+1 query problem
```

---

# Na czym polega problem N+1

Problem pojawia się wtedy, gdy:
1. ORM pobiera listę encji,
2. a następnie dla każdej encji osobno ładuje zależności.

Powstaje wtedy:
- 1 zapytanie bazowe,
- plus N dodatkowych zapytań.

Stąd nazwa:
```text
N + 1
```

---

# Typowy przykład

Przykład w Hibernate/JPA:

```java
List<Order> orders =
    session.createQuery("FROM Order").list();

for (Order o : orders) {
    System.out.println(o.getItems().size());
}
```

Logicznie wygląda to niewinnie.

Programista:
- pobiera zamówienia,
- następnie odczytuje pozycje każdego zamówienia.

Jednak ORM działa inaczej niż intuicyjnie się wydaje.

---

# Co naprawdę wykonuje baza

Najpierw ORM wykonuje:

```sql
SELECT * FROM orders;
```

To jest:
```text
1 query
```

Następnie dla każdego zamówienia:

```sql
SELECT *
FROM order_items
WHERE order_id = ?;
```

Jeżeli:
- mamy 100 zamówień,

to ORM wykona:
- 1 query bazowe,
- 100 dodatkowych query.

Łącznie:
```text
101 zapytań
```

---

# Dlaczego to jest problem

Sam pojedynczy SELECT zwykle jest szybki.

Problemem jest:
- ogromna liczba roundtripów,
- koszt sieci,
- parsowanie SQL,
- planowanie query,
- connection overhead,
- context switching.

Przy dużej liczbie rekordów koszt:
- komunikacji,
- a nie samego SQL,

staje się dominujący.

---

# N+1 jako problem skalowania

Problem N+1 często:
- nie jest widoczny lokalnie,
- działa dobrze na małych datasetach,
- pojawia się dopiero produkcyjnie.

Przykładowo:
- lokalnie mamy 5 rekordów,
- produkcyjnie 5000.

Nagle:
- API zaczyna wykonywać tysiące query,
- latency gwałtownie rośnie,
- baza dostaje ogromny load.

To bardzo typowy problem aplikacji ORM-owych.

---

# Lazy Loading jako główna przyczyna

Najczęściej źródłem N+1 jest:
```text
lazy loading
```

ORM:
- nie ładuje relacji od razu,
- pobiera je dopiero przy pierwszym użyciu.

To wygodne:
- zmniejsza początkowy koszt query,
- pozwala ładować dane „na żądanie”.

Jednak w pętli prowadzi do katastrofy wydajnościowej.

---

# Jak rozpoznać N+1

Najczęstsze symptomy:
- bardzo dużo podobnych SELECT-ów,
- powtarzające się query z różnym ID,
- ogromna liczba roundtripów,
- wysoki latency mimo małych query,
- logi ORM pełne identycznych zapytań.

Przykład:

```sql
SELECT * FROM order_items WHERE order_id = 1;
SELECT * FROM order_items WHERE order_id = 2;
SELECT * FROM order_items WHERE order_id = 3;
SELECT * FROM order_items WHERE order_id = 4;
```

To klasyczny sygnał N+1.

---

# Fetch Join jako podstawowe rozwiązanie

Najpopularniejszym rozwiązaniem jest:
```text
JOIN FETCH
```

Przykład:

```java
SELECT o
FROM Order o
JOIN FETCH o.items
```

ORM wykonuje wtedy:
- jedno większe zapytanie,
- zamiast wielu małych.

---

# Co generuje baza

SQL wygląda mniej więcej tak:

```sql
SELECT o.*, i.*
FROM orders o
JOIN order_items i
    ON i.order_id = o.id;
```

Dzięki temu:
- wszystkie dane są pobrane jednorazowo,
- eliminujemy dodatkowe query,
- znika problem N+1.

---

# Dlaczego JOIN FETCH jest szybszy

Największa oszczędność wynika z:
- redukcji roundtripów,
- mniejszej liczby query,
- mniejszego kosztu planowania,
- mniejszego connection overhead.

Jedno większe query bardzo często jest znacznie szybsze niż:
- setki małych query.

---

# Problem JOIN FETCH — eksplozja danych

JOIN FETCH nie jest jednak darmowy.

JOIN powoduje:
- powielanie danych encji nadrzędnej.

Przykład:
- jedno zamówienie,
- 10 pozycji.

W wyniku SQL:
- zamówienie pojawi się 10 razy.

---

# Row Multiplication

To bardzo ważny problem.

Logicznie:
```text
1 order
```

Fizycznie po JOIN:
```text
10 rows
```

Przy dużych relacjach może dojść do:
- ogromnego wzrostu result setu,
- dużego transferu danych,
- wysokiego zużycia pamięci,
- kosztownej deserializacji.

---

# ORM Hydration Cost

ORM musi później:
- deduplikować encje,
- budować graph obiektów,
- scalać rekordy,
- utrzymywać identity map.

To również kosztuje:
- CPU,
- pamięć,
- garbage collection.

---

# JOIN FETCH a paginacja

Jednym z największych problemów JOIN FETCH jest:
```text
pagination
```

Przykład:

```sql
SELECT o.*, i.*
FROM orders o
JOIN order_items i
    ON i.order_id = o.id
LIMIT 20;
```

Problem:
- LIMIT działa na fizycznych wierszach,
- a nie na logicznych encjach.

---

# Co to oznacza

LIMIT 20 nie oznacza:
```text
20 orders
```

tylko:
```text
20 joined rows
```

Jeżeli:
- jedno zamówienie ma 10 pozycji,

to:
- w wyniku może znaleźć się tylko kilka zamówień.

To bardzo częsty problem ORM-ów.

---

# Lepsza strategia paginacji

Najczęściej stosowane rozwiązanie:
1. pobranie ID parent entities,
2. osobne dociągnięcie relacji.

Przykład:
- najpierw pobieramy 20 order IDs,
- następnie:
```sql
WHERE order_id IN (...)
```

To:
- ogranicza eksplozję danych,
- poprawia paginację,
- zmniejsza memory usage.

---

# Batch Fetching

Kolejną strategią jest:
```text
batch loading
```

Zamiast:
- 1 query per entity,

ORM grupuje ID:

```sql
SELECT *
FROM order_items
WHERE order_id IN (...);
```

Przykładowo:
- po 10,
- po 50,
- po 100 rekordów.

---

# Zalety batch loading

To kompromis pomiędzy:
- JOIN FETCH,
- a klasycznym N+1.

Zalety:
- mniej query,
- brak gigantycznych JOIN-ów,
- lepsza paginacja,
- mniejsza eksplozja danych.

Dlatego:
- batch fetching często jest bardziej skalowalny,
- niż agresywne JOIN FETCH.

---

# EntityGraph

W JPA/Hibernate istnieje również:
```text
EntityGraph
```

Pozwala:
- deklaratywnie określać relacje do pobrania,
- bez ręcznego JOIN FETCH.

To wygodniejsze dla:
- dynamicznych scenariuszy,
- różnych widoków danych.

---

# N+1 nie zawsze jest problemem

Bardzo ważna zasada:

> nie każde N+1 trzeba optymalizować.

Jeżeli:
- N jest małe,
- query wykonywane rzadko,
- dane są cache’owane,
- relacje są niewielkie,

JOIN FETCH może:
- zwiększyć memory usage,
- zwiększyć transfer danych,
- pogorszyć paginację,
- zwiększyć koszt hydracji ORM.

---

# Trade-off

W praktyce zawsze istnieje trade-off:

## N+1:
- więcej małych query,
- więcej roundtripów,
- mniejsze result sety.

## JOIN FETCH:
- mniej query,
- większy result set,
- większy memory footprint.

Nie istnieje uniwersalnie najlepsze rozwiązanie.

---

# Znaczenie indeksów

Relacje używane przez ORM powinny być indeksowane.

Przykładowo:

```sql
CREATE INDEX idx_order_items_order_id
ON order_items(order_id);
```

Bez tego:
- każde dociągnięcie relacji,
- może wykonywać pełny skan tabeli.

To potrafi dramatycznie pogorszyć problem N+1.

---

# Jak diagnozować problem

Najlepsze narzędzia:
- logowanie SQL,
- Hibernate statistics,
- query profiler,
- APM,
- EXPLAIN ANALYZE.

Najważniejsze jest mierzenie:
- liczby query,
- execution time,
- liczby rekordów,
- transferu danych,
- memory usage.

---

# Największy błąd

Największym błędem jest:
- optymalizowanie „na ślepo”,
- zakładanie że JOIN FETCH zawsze jest lepszy.

W praktyce:
- czasem lepiej mieć kilka małych query,
- niż jeden gigantyczny JOIN.

---

# Najważniejsza praktyczna zasada

Problem N+1 nie polega wyłącznie na:
```text
liczbie query
```

Najważniejsze są:
- roundtripy,
- transfer danych,
- memory footprint,
- hydration cost,
- workload,
- cardinality relacji.

Dlatego:
- zawsze należy mierzyć realny workload,
- analizować execution plans,
- sprawdzać memory usage,
- dobierać strategię do konkretnego access pattern.