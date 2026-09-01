# connection pool

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** connection pool.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „connection pool” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ConnectionPoolBudgetTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Pula połączeń — budżet, kolejka i backpressure



Pula połączeń nie jest sposobem na „dodanie mocy” bazie. Ograniczona pula kontroluje, ile zapytań aplikacja może wykonywać równocześnie, a oczekujące żądania tworzą kolejkę przed bazą. Zbyt mała pula może nie wykorzystywać dostępnej przepustowości. Zbyt duża przenosi kolejkę do bazy, zwiększa przełączanie kontekstu i może pogorszyć latency całego systemu.

## Najpierw budżet globalny

Jeżeli PostgreSQL dopuszcza 200 połączeń, 20 rezerwujemy dla administracji i innych klientów, a aplikacja ma wykorzystywać najwyżej 80% pozostałego limitu, jej globalny budżet wynosi:

```text
(200 - 20) * 80% = 144 połączenia
```

Przy sześciu instancjach daje to najwyżej 24 połączenia na instancję. Autoscaling do dwunastu instancji bez zmiany konfiguracji puli próbowałby otworzyć 288 połączeń. Dlatego liczymy `maksymalna liczba instancji * maksymalna pula`, nie tylko stan obecny.

Klasa `ConnectionPoolBudget` zapisuje ten rachunek jawnie. Wynik jest górnym limitem bezpieczeństwa, a nie rekomendacją, żeby zawsze otwierać tyle połączeń.

## Ile połączeń rzeczywiście potrzeba?

Punkt startowy daje prawo Little'a:

```text
równoległa praca z DB ≈ requesty/s * średni czas pracy z DB w sekundach
```

Dla 400 requestów/s oraz średnio 25 ms spędzanych w bazie jest to około 10 równoległych operacji. Trzeba jednak zmierzyć także percentyle, transakcje wykonujące kilka zapytań i ruch w tle. Obliczenie nie uwzględnia locków, wolnych zapytań ani przeciążonej bazy.

## Metryki, które trzeba czytać razem

- `active`, `idle`, `max` — wykorzystanie puli;
- `pending` i czas pozyskania połączenia — czy przed pulą rośnie kolejka;
- czas zapytań i transakcji — jak długo połączenie pozostaje zajęte;
- CPU, I/O, lock waits oraz liczba sesji po stronie bazy — czy to baza jest granicą;
- timeouty i procent błędów — czy przeciążenie jest szybko odcinane.

Stałe `active == max` nie dowodzi jeszcze, że pula jest za mała. Jeżeli jednocześnie rośnie czas zapytań, CPU bazy albo lock wait, dołożenie połączeń zwykle pogorszy sytuację. Najpierw usuwa się długie transakcje, N+1 i kosztowne zapytania, a rozmiar puli potwierdza testem obciążeniowym.

## Bezpieczna konfiguracja

Ustaw skończony connection timeout, krótszy od budżetu czasu requestu. Dzięki temu aplikacja odrzuci nadmiar pracy, zamiast bez końca budować kolejkę. Rezerwę pozostaw dla migracji, zadań administracyjnych i innych usług. Osobne workloady, na przykład API i długie joby raportowe, mogą potrzebować osobnych pul lub limitów, żeby jeden z nich nie zagłodził drugiego.

## Eksperyment

1. Policz budżet dla minimalnej i maksymalnej liczby instancji.
2. Oszacuj potrzebną współbieżność na podstawie throughputu i czasu w DB.
3. Uruchom reprezentatywny test obciążeniowy z małą pulą.
4. Zwiększaj ją stopniowo i obserwuj throughput, p95/p99, `pending` oraz bazę.
5. Zatrzymaj się, gdy throughput przestaje rosnąć albo pogarszają się opóźnienia.
