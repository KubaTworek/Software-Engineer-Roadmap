# Replikacja, stale reads i sharding — szczegółowe podsumowanie

# Wprowadzenie

W nowoczesnych systemach backendowych pojedyncza baza danych bardzo szybko staje się ograniczeniem skalowalności. Gdy rośnie:
- liczba użytkowników,
- liczba zapisów,
- liczba odczytów,
- throughput,
- wymagania dostępności,

pojawia się potrzeba:
- replikacji,
- rozdzielania ruchu,
- shardingu,
- dystrybucji danych pomiędzy wiele instancji.

Te mechanizmy pozwalają budować systemy:
- bardziej skalowalne,
- bardziej odporne,
- obsługujące większy ruch.

Jednocześnie wprowadzają ogromną dodatkową złożoność:
- consistency,
- stale reads,
- routing zapytań,
- hotspoty,
- distributed systems trade-offs.

To bardzo ważne, ponieważ wiele problemów pojawia się dopiero produkcyjnie, przy dużym ruchu i wysokiej współbieżności.

---

# Replikacja — podstawowy model

Najczęściej spotykany model produkcyjny wygląda następująco:

```text
Leader / Primary
↓
Replica / Read Replica
```

Leader:
- przyjmuje zapisy,
- wykonuje UPDATE/INSERT/DELETE,
- jest źródłem prawdy.

Replica:
- synchronizuje dane z leadera,
- zwykle obsługuje odczyty,
- odciąża primary.

To pozwala:
- rozłożyć load,
- skalować read throughput,
- zwiększać dostępność.

---

# Replikacja asynchroniczna

Najczęściej używana jest:
```text
asynchronous replication
```

Oznacza to, że:
- leader najpierw zatwierdza zapis,
- dopiero później replika synchronizuje zmiany.

To bardzo ważne.

---

# Problem stale reads

Jeżeli aplikacja:
1. wykonuje INSERT na leaderze,
2. a chwilę później robi SELECT na replice,

może otrzymać:
```text
stare dane
```

ponieważ replika:
- jeszcze nie zsynchronizowała zmian.

To właśnie:
```text
stale read
```

---

# Read-after-write consistency

W systemach rozproszonych bardzo ważnym konceptem jest:

```text
read-after-write consistency
```

Oznacza ona:

> po wykonaniu zapisu użytkownik natychmiast widzi własne zmiany.

Przy asynchronicznej replikacji:
- nie ma gwarancji read-after-write consistency.

---

# Typowy problem produkcyjny

Przykład:
1. użytkownik zmienia profil,
2. backend zapisuje dane na leaderze,
3. frontend od razu pobiera profil,
4. request trafia na replikę,
5. użytkownik widzi stare dane.

To bardzo częsty problem:
- social media,
- ecommerce,
- profile użytkowników,
- dashboardy,
- messaging systems.

---

# Dlaczego repliki istnieją mimo tego problemu

Ponieważ:
- większość ruchu to odczyty,
- read scaling jest krytyczny,
- pełna synchroniczność byłaby bardzo kosztowna.

Replica pozwala:
- obsłużyć ogromny read throughput,
- zmniejszyć load leadera,
- poprawić skalowalność.

Trade-off:
```text
większa skalowalność
vs
słabsza consistency
```

---

# Sticky Sessions

Jednym z najczęstszych rozwiązań jest:

```text
sticky session
```

Po zapisie:
- użytkownik przez pewien czas czyta z leadera,
- dopiero później wraca na repliki.

To pozwala:
- utrzymać read-after-write consistency,
- bez pełnej synchroniczności całego systemu.

---

# Read Routing

W praktyce aplikacje bardzo często:
- świadomie wybierają źródło odczytu.

Przykładowo:
- krytyczne odczyty → leader,
- zwykłe odczyty → repliki.

To ważny aspekt architektury backendowej.

---

# Synchronous Replication

Alternatywą jest:
```text
synchronous replication
```

W PostgreSQL można używać np.:
```text
synchronous_commit
```

Wtedy:
- leader nie potwierdzi COMMIT,
- dopóki replika nie zapisze danych.

---

# Zalety synchronous replication

Zapewnia:
- silniejszą consistency,
- read-after-write guarantees,
- mniejsze ryzyko stale reads.

---

# Koszt synchronous replication

Cena jest jednak bardzo wysoka:
- większy latency,
- wolniejsze zapisy,
- większa zależność od sieci,
- mniejszy throughput.

Każdy COMMIT:
- musi czekać na replikę.

Dlatego synchronous replication:
- zwiększa consistency,
- ale zmniejsza wydajność.

---

# Version-based consistency

Innym podejściem jest:
```text
version tracking
```

Każdy rekord posiada:
- version,
- timestamp,
- sequence number.

Po zapisie aplikacja:
- zna oczekiwaną wersję danych.

Jeżeli replika zwróci:
```text
version < expected_version
```

to:
- wiadomo że odczyt jest stale,
- można:
    - retry,
    - poczekać,
    - przełączyć się na leader.

---

# Consistency jako kontrakt

To bardzo ważna zasada systemów rozproszonych:

> consistency nie jest magiczną właściwością systemu,
> tylko świadomie zaprojektowanym kontraktem.

Trzeba jasno określić:
- czy stale reads są akceptowalne,
- które operacje wymagają silnej spójności,
- gdzie można zaakceptować eventual consistency.

---

# Eventual Consistency

Wiele systemów działa w modelu:
```text
eventual consistency
```

Oznacza to:
- repliki mogą chwilowo być niespójne,
- ale „ostatecznie” dogonią leadera.

To bardzo popularny model:
- social feeds,
- analytics,
- recommendation systems,
- log systems.

---

# Sharding — skalowanie przez podział danych

Replikacja pomaga głównie skalować:
```text
read throughput
```

Jednak gdy:
- zapisów jest bardzo dużo,
- pojedynczy leader nie wystarcza,

pojawia się potrzeba:
```text
shardingu
```

---

# Czym jest sharding

Sharding oznacza:
- podział danych pomiędzy wiele niezależnych instancji,
- każda instancja przechowuje tylko fragment danych.

To pozwala:
- skalować write throughput,
- rozłożyć storage,
- zwiększyć capacity systemu.

---

# Najważniejszy problem shardingu

Kluczowym problemem jest:
```text
shard key
```

Czyli:
- sposób przypisywania danych do shardów.

To jedna z najważniejszych decyzji architektonicznych całego systemu.

---

# Zły shard key = hotspot

Najgorszy możliwy scenariusz:
```text
wszystkie nowe zapisy trafiają do jednego shardu
```

To tworzy:
```text
hotspot
```

czyli shard:
- przeciążony,
- receiving most writes,
- będący bottleneckiem systemu.

---

# Antywzorzec — timestamp jako shard key

Bardzo częsty błąd:

```text
shard_key = created_at
```

lub:
```text
auto_increment ID
```

---

# Dlaczego to jest złe

Nowe rekordy:
- zawsze trafiają do „ostatniego” shardu.

Przykład:
- shard_2025_11,
- shard_2025_12,
- shard_2026_01.

Wszystkie nowe inserty trafiają do:
```text
shard_2026_01
```

Starsze shardy:
- praktycznie nie dostają ruchu.

---

# Skutek hotspotu

Jeden shard:
- ma 90–100% write traffic,
- przeciąża CPU,
- przeciąża dyski,
- staje się bottleneckiem.

Pozostałe shardy:
- są praktycznie bezczynne.

To całkowicie niszczy sens shardingu.

---

# Hash-based sharding

Najczęstsze rozwiązanie:
```text
hash(user_id)
```

Przykład:

```text
shard = hash(user_id) % N
```

Dzięki temu:
- zapisy rozkładają się równomiernie,
- ruch jest lepiej dystrybuowany,
- unika się hotspotów.

---

# Dlaczego hash działa lepiej

Hash:
- losowo rozprowadza rekordy,
- niezależnie od czasu,
- niezależnie od kolejności insertów.

To bardzo ważne dla:
- wysokiego throughputu,
- równomiernego loadu,
- skalowania write-heavy systemów.

---

# Compound Shard Keys

Często stosuje się:
```text
compound keys
```

Przykład:

```text
(user_id % N, created_at)
```

To pozwala:
- równomiernie rozkładać writes,
- zachować lokalne sortowanie po czasie,
- wspierać range scans wewnątrz shardu.

---

# Hot Tenant Problem

Nawet hash sharding nie rozwiązuje wszystkiego.

Przykład:
- jeden tenant generuje 50% ruchu.

Wtedy:
- jego hash bucket nadal staje się hotspotem.

To:
```text
hot tenant problem
```

---

# Dedicated Shards

Często rozwiązaniem jest:
```text
dedicated shard
```

czyli:
- największy tenant dostaje własny shard.

To bardzo częsty wzorzec:
- SaaS systems,
- multitenancy,
- enterprise platforms.

---

# Monitoring shardów

Sharding wymaga bardzo dobrego monitoringu.

Trzeba obserwować:
- load per shard,
- write throughput,
- hot keys,
- hot tenants,
- uneven distribution,
- storage growth.

Bez tego hotspoty często są niewidoczne aż do momentu awarii.

---

# Sharding nie jest darmowy

Bardzo ważna zasada:

> sharding rozwiązuje problem skalowania,
> ale dramatycznie zwiększa złożoność systemu.

---

# Problemy shardingu

Sharding utrudnia:
- JOIN-y,
- transakcje,
- globalne indeksy,
- globalne ORDER BY,
- analytics,
- agregacje,
- uniqueness constraints.

Pojawia się:
- routing query,
- distributed transactions,
- resharding,
- balancing,
- consistency management.

---

# Distributed Transactions

Jednym z najtrudniejszych problemów są:
```text
distributed transactions
```

Jeżeli:
- jedna operacja dotyka wielu shardów,
- consistency staje się bardzo trudna.

To dlatego wiele systemów:
- unika cross-shard transactions,
- projektuje bounded contexts,
- ogranicza global consistency.

---

# Re-sharding

Kolejny bardzo trudny problem:
```text
re-sharding
```

Czyli:
- zmiana liczby shardów,
- przenoszenie danych,
- redistribucja ruchu.

To jedna z najtrudniejszych operacji operacyjnych w dużych systemach.

---

# Najważniejsza praktyczna zasada

Replikacja i sharding to nie tylko:
```text
skalowanie
```

To przede wszystkim:
- świadome zarządzanie consistency,
- trade-off pomiędzy latency i correctness,
- routing danych,
- kontrola hotspotów,
- zarządzanie distributed systems complexity.

---

# Finalna zasada

Najważniejszy wniosek brzmi:

> skalowanie danych nie jest wyłącznie problemem technicznym,
> ale problemem projektowym i produktowym.

Wybór:
- consistency model,
- shard key,
- replication strategy,
- routing policy

wpływa bezpośrednio na:
- wydajność,
- poprawność,
- skalowalność,
- koszt operacyjny całego systemu.
