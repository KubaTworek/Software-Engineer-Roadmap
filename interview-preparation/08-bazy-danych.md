# SQL i bazy danych

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#08-sql-i-bazy-danych)

<!-- question-toc:start -->
## Spis pytań

- [DB-01. Co oznacza ACID i czego nie gwarantuje samo rozpoczęcie transakcji?](#db-01-co-oznacza-acid-i-czego-nie-gwarantuje-samo-rozpoczęcie-transakcji)
- [DB-02. Jakie są poziomy izolacji i jak PostgreSQL wykorzystuje MVCC?](#db-02-jakie-są-poziomy-izolacji-i-jak-postgresql-wykorzystuje-mvcc)
- [DB-03. Czym MVCC PostgreSQL różni się od MySQL/InnoDB?](#db-03-czym-mvcc-postgresql-różni-się-od-mysqlinnodb)
- [DB-04. Jak działa indeks i dlaczego kolejność kolumn ma znaczenie?](#db-04-jak-działa-indeks-i-dlaczego-kolejność-kolumn-ma-znaczenie)
- [DB-05. Jak znaleźć przyczynę, gdy SQL zwalnia z 50 ms do 10 s lub działa szybko tylko lokalnie?](#db-05-jak-znaleźć-przyczynę-gdy-sql-zwalnia-z-50-ms-do-10-s-lub-działa-szybko-tylko-lokalnie)
- [DB-06. Co zrobić, gdy CPU bazy osiąga 100%, a tabela ma setki milionów wierszy?](#db-06-co-zrobić-gdy-cpu-bazy-osiąga-100-a-tabela-ma-setki-milionów-wierszy)
- [DB-07. Jak dobrać pulę połączeń i zbadać „pool exhausted” przy wykorzystaniu 34%?](#db-07-jak-dobrać-pulę-połączeń-i-zbadać-pool-exhausted-przy-wykorzystaniu-34)
- [DB-08. Jak wydajnie przetwarzać miliony rekordów bez OOM?](#db-08-jak-wydajnie-przetwarzać-miliony-rekordów-bez-oom)
- [DB-09. Co wpływa na JDBC/Hibernate batching i strategię ID?](#db-09-co-wpływa-na-jdbchibernate-batching-i-strategię-id)
- [DB-10. Kiedy bulk UPDATE, usuwanie porcjami i małe transakcje?](#db-10-kiedy-bulk-update-usuwanie-porcjami-i-małe-transakcje)
- [DB-11. Jak zaprojektować paginację milionów rekordów?](#db-11-jak-zaprojektować-paginację-milionów-rekordów)
- [DB-12. INNER, LEFT i RIGHT JOIN oraz działy bez pracowników — jak je rozumieć?](#db-12-inner-left-i-right-join-oraz-działy-bez-pracowników--jak-je-rozumieć)
- [DB-13. Czym różnią się `COUNT(*)`, `COUNT(1)` i `COUNT(kolumna)`?](#db-13-czym-różnią-się-count-count1-i-countkolumna)
- [DB-14. Jak znaleźć drugą/N-tą pensję i ranking pracowników według doświadczenia?](#db-14-jak-znaleźć-drugąn-tą-pensję-i-ranking-pracowników-według-doświadczenia)
- [DB-15. Jaka jest logiczna kolejność wykonywania klauzul SQL?](#db-15-jaka-jest-logiczna-kolejność-wykonywania-klauzul-sql)
- [DB-16. Po co normalizacja i kiedy SQL, NoSQL, Redis lub Elasticsearch?](#db-16-po-co-normalizacja-i-kiedy-sql-nosql-redis-lub-elasticsearch)
- [DB-17. Czym różnią się partycjonowanie, sharding i read replicas? Jak wpływają na JOIN?](#db-17-czym-różnią-się-partycjonowanie-sharding-i-read-replicas-jak-wpływają-na-join)
- [DB-18. Jak magazyn kolumnowy przyspiesza analitykę?](#db-18-jak-magazyn-kolumnowy-przyspiesza-analitykę)
<!-- question-toc:end -->

## DB-01. Co oznacza ACID i czego nie gwarantuje samo rozpoczęcie transakcji?

Atomicity oznacza całość albo brak lokalnej transakcji, consistency zachowanie zdefiniowanych ograniczeń, isolation reguły obserwowania współbieżnych operacji, a durability trwałość zatwierdzonego wyniku zgodnie z konfiguracją silnika. Poprawność reguł domeny zależy także od autora schematu i kodu.

Transakcja na domyślnej izolacji nie usuwa wszystkich anomalii. Nie gwarantuje też atomowości z HTTP ani zerowej utraty danych przy każdej konfiguracji replikacji. Najpierw definiuję niezmiennik, potem dobieram constraint, SQL i poziom izolacji.

Constraint UNIQUE chroni unikalność ID, CHECK może ograniczyć dopuszczalne wartości, a transakcja wiąże kilka zmian. Nie wszystkie reguły da się jednak wyrazić pojedynczym constraintem: np. ograniczenie sumy przydziałów wymaga odpowiedniego modelu lub synchronizacji operacji.

Durability zależy od tego, co potwierdzono: zapis do logu lokalnego, fsync czy wymagane repliki. Backup pomaga odzyskać dane po błędnym DELETE, natomiast replika może wiernie powielić ten błąd. Projekt ochrony danych musi więc łączyć gwarancje commitu, replikację, odtwarzanie i kontrolę niezmienników, zamiast traktować skrót ACID jako jedną przełączaną opcję.

## DB-02. Jakie są poziomy izolacji i jak PostgreSQL wykorzystuje MVCC?

MVCC pozwala odczytywać odpowiednie wersje wierszy, dzięki czemu zwykły odczyt często nie blokuje zapisu. W PostgreSQL Read Committed daje snapshot instrukcji, Repeatable Read transakcji, a Serializable wykrywa anomalie serializacji i może wymagać retry. Read Uncommitted zachowuje się jak Read Committed.

Nie oznacza to braku locków ani identycznej semantyki w każdym silniku. Długie transakcje utrzymują potrzebę starych wersji i utrudniają sprzątanie. Dla Serializable obsługuję błąd serializacji przez ponowienie całej bezpiecznej transakcji. [Izolacja PostgreSQL](https://www.postgresql.org/docs/current/transaction-iso.html).

W PostgreSQL Read Committed dwa SELECT w jednej transakcji mogą zobaczyć różne zatwierdzone wyniki. Repeatable Read utrzymuje snapshot, ale nie eliminuje każdego konfliktu biznesowego. Serializable może wykryć niebezpieczny wzorzec i przerwać jedną transakcję.

<!-- example:DB-02-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
BEGIN ISOLATION LEVEL SERIALIZABLE;
-- odczyt oraz decyzja zależna od wielu rekordów
COMMIT;
~~~

To szkic granicy; aplikacja musi obsłużyć serialization failure przez ponowienie całej operacji. Długie snapshoty utrudniają odzyskiwanie martwych wersji, dlatego eksport milionów rekordów i interaktywna transakcja mają różne wymagania. „Czytelnik nie blokuje writera” jest uproszczeniem, nie brakiem jakichkolwiek locków.

## DB-03. Czym MVCC PostgreSQL różni się od MySQL/InnoDB?

PostgreSQL przechowuje wersje wierszy w tabeli, a vacuum odzyskuje martwe wersje. InnoDB odtwarza starsze wersje przez undo log i purge. Domyślnie PostgreSQL używa Read Committed, a InnoDB Repeatable Read; różnią się także locking reads i mechanizmy blokad zakresów.

Nie przenoszę wprost wniosków o phantom reads, snapshotach czy deadlockach między silnikami. Testuję konkretne SQL na docelowej wersji. [MVCC InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-multi-versioning.html), [współbieżność PostgreSQL](https://www.postgresql.org/docs/current/mvcc.html).

Różnica fizycznego przechowywania starych wersji wpływa na vacuum/undo retention i zachowanie długich transakcji. Domyślny poziom izolacji również zmienia rezultat prostego eksperymentu: identyczny kod może inaczej obserwować zmiany na PostgreSQL i MySQL.

Test przenośności uruchamia dwa połączenia, bariery między SELECT i UPDATE oraz kontrolę wyniku. Sprawdzam zwykłe consistent reads i locking reads osobno, wraz z zakresem indeksu, bo InnoDB może używać next-key/gap locks. Nie wyciągam wniosków o blokadach z nazwy ORM: decydują konkretny SQL, plan i silnik.

## DB-04. Jak działa indeks i dlaczego kolejność kolumn ma znaczenie?

Indeks B-tree organizuje klucze w porządku umożliwiającym wyszukiwanie i zakresy bez skanowania całej tabeli. W indeksie złożonym kolejność determinuje, które warunki i sortowanie można efektywnie obsłużyć; liczą się równości, zakresy, selectivity oraz plan silnika.

Każdy dodatkowy indeks kosztuje miejsce, cache i utrzymanie zapisów. Nie indeksuję każdej kolumny WHERE osobno. Dobieram strukturę do rzeczywistego zapytania i danych, a skuteczność potwierdzam planem oraz pomiarem odczytów i zapisów.

Dla zapytania tenant + status + najnowsze zamówienia kandydatem jest:

<!-- example:DB-04-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
CREATE INDEX idx_orders_lookup
ON orders (tenant_id, status, created_at DESC, id DESC);
~~~

Indeks trzeba ocenić dla rzeczywistej selektywności i rozkładu danych. Jeżeli niemal wszystkie rekordy mają dany status, inny plan może być tańszy. Dodanie kolumn zwiększa rozmiar i koszt aktualizacji. Index-only scan zależy także od potrzebnych pól i warunków silnika, nie samego istnienia indeksu. Przy zmianie porównuję odczyt oraz transakcje zapisujące dane, szczególnie batch i maintenance. [Indeksy wielokolumnowe PostgreSQL](https://www.postgresql.org/docs/current/indexes-multicolumn.html).

<!-- follow-up:DB-04 -->
**Sprawdź rozumienie:** Indeks przyspieszył SELECT, ale batch zapisujący dane trwa dwa razy dłużej. Jak ocenić zmianę?

<details>
<summary>Kierunek odpowiedzi</summary>

Uwzględnij aktualizacje indeksu, miejsce, cache, locki i czas batcha obok odczytu. Optymalizacja musi spełniać budżety całego workloadu; samo szybsze jedno zapytanie nie wystarcza.

</details>

## DB-05. Jak znaleźć przyczynę, gdy SQL zwalnia z 50 ms do 10 s lub działa szybko tylko lokalnie?

Zbieram rzeczywisty SQL, parametry, plan i wait events. Porównuję estymowaną i faktyczną liczność, indeksy, statystyki, blokady, rozmiar danych, cache dyskowy, I/O oraz współbieżny workload. Lokalna tabela ze stu wierszami nie reprezentuje produkcji.

EXPLAIN ANALYZE wykonuje zapytanie, więc używam go świadomie, szczególnie przy modyfikacjach. Naprawą może być indeks, zmiana zapytania, aktualizacja statystyk lub ograniczenie pracy. CTE pomaga w czytelności, ale nie gwarantuje szybszego planu. [EXPLAIN PostgreSQL](https://www.postgresql.org/docs/current/using-explain.html).

Czytam plan od kosztownych węzłów, porównując estymację i actual rows oraz liczbę loops. Nested loop wykonany milion razy może dominować mimo taniego pojedynczego dostępu. Duża różnica estymacji sugeruje statystyki, korelację kolumn albo parametry, niekoniecznie brak indeksu.

<!-- example:DB-05-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM orders
WHERE tenant_id = 7 AND status = 'PENDING';
~~~

Przykład jest SELECT, ale nadal wykonuje pracę i może obciążyć produkcję. Parametry biorę z rzeczywistego wolnego requestu, bo plan dla małego tenanta może nie reprezentować największego. CTE nie gwarantuje materializacji ani przyspieszenia we wszystkich wersjach.

[Przeczytaj przykładowy materiał diagnostyczny](DIAGNOSTYKA.md#diag-01-plan-sql-przed-i-po-indeksie).

## DB-06. Co zrobić, gdy CPU bazy osiąga 100%, a tabela ma setki milionów wierszy?

Patrzę na zapytania konsumujące łącznie najwięcej czasu i CPU, ich częstotliwość, plany i liczbę przetwarzanych wierszy. Eliminuję N+1, pełne skany bez potrzeby, zbyt szerokie wyniki i kosztowne sortowanie. Sprawdzam też maintenance i background jobs.

Następnie rozważam właściwe indeksy, retencję/archiwizację, partycjonowanie i przeniesienie analityki. Więcej połączeń zwykle nie poprawia nasyconego CPU. Cache pomaga tylko zapytaniom z trafieniami; write-heavy workload pozostaje kosztem bazy.

CPU bazy może wynikać z wielu szybkich zapytań, a nie jednego wolnego. Zapytanie kosztujące 2 ms uruchamiane 50 tys. razy ma inny łączny wpływ niż jedno 2-sekundowe. Dlatego sortuję statystyki także po total time, wywołaniach i przetworzonych wierszach.

Dla ogromnej tabeli partycjonowanie pomoże, jeśli filtr umożliwia pruning lub upraszcza retencję, ale nie uratuje skanu wszystkich partycji. Read replica odciąży kwalifikujące się odczyty; nie przyspieszy automatycznie kosztownego UPDATE primary. Ograniczenie napływu w krytycznym momencie może przywrócić stabilność skuteczniej niż dodatkowe połączenia.

## DB-07. Jak dobrać pulę połączeń i zbadać „pool exhausted” przy wykorzystaniu 34%?

Zestawiam active, idle, pending, acquisition latency i czas transakcji z limitem sesji DB. Metryki oraz błąd muszą pochodzić z tej samej puli, instancji i okna czasu. Niskie active nie wyklucza problemu utworzenia nowych połączeń przez sieć, limit serwera lub błędną konfigurację.

847 sesji przy maksimum jednej puli 40 może oznaczać wiele podów, drugą pulę albo surowe JDBC bez close. Najpierw identyfikuję klientów/role i sumę pul wszystkich instancji, potem szukam wycieku. Rozmiar dobieram testem przepustowości DB; heurystyka „rdzenie × 2 + dyski” nie jest uniwersalną receptą, a podniesienie limitu może pogłębić nasycenie.

<!-- example:DB-07-1 kind:jdbc-sketch -->
**Kontekst:** Szkic JDBC — wymaga skonfigurowanego DataSource i bazy; poza automatycznym wykonaniem.

~~~java
try (var connection = dataSource.getConnection();
     var statement = connection.prepareStatement("select 1");
     var rows = statement.executeQuery()) {
    rows.next();
}
~~~

To fragment z dostarczonym DataSource. Close wrappera zwraca connection do puli, nie musi zamykać fizycznego socketu. Mierzę także czas przetrzymywania: długa transakcja i wyciek mogą powodować podobny objaw, ale wymagają innej naprawy.

## DB-08. Jak wydajnie przetwarzać miliony rekordów bez OOM?

Czytam projekcje/DTO porcjami, keyset pagination lub kontrolowanym streamingiem, z zamykaniem kursora i limitem pracy. Dla zapisu stosuję zmierzony batching i checkpointy. W Hibernate okresowo flush/clear ogranicza rozrost persistence context.

Rozmiar batcha dobieram testem, nie stałą z internetu. Długotrwały cursor może trzymać connection i snapshot. Po restarcie proces wznawia się od trwałego checkpointu, a idempotencja zapobiega podwójnemu efektowi ostatniej niepewnej porcji.

Proces eksportu powinien mieć stabilny porządek, checkpoint i ograniczoną porcję. Po każdej udanej porcji zapisuje ostatni przetworzony klucz. Jeśli między skutkiem a checkpointem nastąpi crash, powtórzenie ostatniej porcji musi być bezpieczne.

Hibernate session trzyma referencje do managed encji, więc samo iterowanie nie musi ograniczać heapu. Flush/clear pomaga przy zapisie, ale po clear encje są detached i późniejsze modyfikacje nie będą śledzone. Dla odczytu często prostsza jest projekcja lub JDBC. Mierzę pamięć oraz czas trzymania connection i snapshotu, nie tylko rekordy/s.

## DB-09. Co wpływa na JDBC/Hibernate batching i strategię ID?

JDBC batching grupuje zgodne instrukcje i zmniejsza round trips, jeśli współpracują ustawienia ORM, kształt SQL, sterownik oraz strategia ID. saveAll samo w sobie nie dowodzi batching. Identity często wymaga otrzymania ID po każdym insercie, natomiast sekwencja z alokacją zakresu może pozwolić przygotować więcej zapisów wcześniej.

Batch size dobieram na reprezentatywnym pomiarze: zbyt mały mnoży komunikację, zbyt duży zwiększa bufory, lock duration i koszt ponowienia. Kontroluję limity parametrów/pakietów, pamięć persistence context oraz atomowość porcji. Potwierdzenie obejmuje rzeczywiste instrukcje i transfer, nie tylko liczbę wywołań repozytorium.

Allocation size sekwencji może pozostawić luki po restarcie; to nie dowód utraty transakcji. Numeracja dokumentów wymagająca osobnych gwarancji jest kontraktem biznesowym, a nie skutkiem użycia technicznego PK. Wspierane optymalizacje różnią się między silnikami, więc wynik przenoszę wraz z warunkami testu.

## DB-10. Kiedy bulk UPDATE, usuwanie porcjami i małe transakcje?

Bulk UPDATE unika ładowania każdej encji i dirty checking, lecz omija część lifecycle oraz może rozminąć się z L1/versioningiem. Duże DELETE dzielę według stabilnego klucza i limitu, żeby ograniczyć locki, log transakcyjny i wpływ na replikację.

Commit porcji umożliwia wznowienie, ale rezygnuje z atomowości całego zbioru. Gdy domena wymaga „wszystko albo nic”, potrzebny jest inny model, np. staging i atomowa aktywacja wyniku. Po bulk operacjach odświeżam lub czyszczę kontekst i cache.

Aktualizacja zbiorcza działa w DB bez ładowania każdego obiektu:

<!-- example:DB-10-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
UPDATE reservation
SET status = 'EXPIRED'
WHERE status = 'HELD' AND expires_at < :now;
~~~

To przykład; przy wielkim zbiorze potrzebne mogą być porcje według klucza i limit czasu. Jeśli aplikacja ma już te encje w L1, widzi stary stan, dopóki go nie odświeży. Bulk update powinien uwzględnić wersjonowanie i zdarzenia: pominięcie callbacków może oznaczać brak wymaganych faktów w outbox. Wybór szybszego SQL nie może cicho zmienić kontraktu.

## DB-11. Jak zaprojektować paginację milionów rekordów?

Offset jest prosty, ale duży offset wymaga pominięcia wielu wierszy i zmiany danych mogą przesuwać strony. Keyset używa ostatniego klucza porządku, np. `(created_at, id)`, i dopasowanego indeksu. Cursor musi obejmować sortowanie i filtry.

Keyset nie daje taniego skoku do dowolnej strony. Snapshot lub granica czasu pomaga przy eksporcie spójnego zbioru; samo uporządkowanie po nieunikalnej dacie nie wystarczy. Ograniczam page size i świadomie rezygnuję z drogiego dokładnego count, jeśli nie jest potrzebny.

Warunek keyset dla malejącego porządku może wyglądać tak:

<!-- example:DB-11-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
SELECT id, created_at
FROM orders
WHERE tenant_id = :tenant
  AND (created_at, id) < (:last_created_at, :last_id)
ORDER BY created_at DESC, id DESC
LIMIT :page_size;
~~~

To wariant PostgreSQL zakładający niepuste klucze sortowania i ustalony filtr. Cursor powinien przenosić również znaczenie zapytania, aby nie użyć go przypadkiem z innym zestawem filtrów. Przy mutowalnym kluczu sortowania rekord może zmienić pozycję. Jeśli potrzebny jest spójny eksport, wyznaczam granicę snapshotu lub odrębny protokół.

<!-- follow-up:DB-11 -->
**Sprawdź rozumienie:** Cursor powstał dla status=PENDING. Czy można użyć go dla status=CONFIRMED i innego sortowania?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie bez zdefiniowanego kontraktu. Cursor jest związany z filtrami, porządkiem i granicą odczytu. Zmiana warunków może zgubić lub powtórzyć rekordy; waliduj parametry albo wymagaj nowego cursora.

</details>

## DB-12. INNER, LEFT i RIGHT JOIN oraz działy bez pracowników — jak je rozumieć?

INNER zachowuje dopasowania, LEFT także niedopasowane wiersze lewej tabeli, a RIGHT analogicznie prawej. Warunek prawej tabeli w WHERE po LEFT JOIN może odrzucić NULL i zmienić efekt na odpowiadający INNER.

Działy bez pracowników znajdę przez `NOT EXISTS` skorelowane po department_id albo LEFT JOIN z testem braku niepustego klucza pracownika. Nie testuję kolumny, która legalnie może być NULL. Dobór indeksu na kluczu relacji wpływa na plan.

Zapytanie o działy bez pracowników można zapisać bez niejasności NULL:

<!-- example:DB-12-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
SELECT d.id, d.name
FROM department d
WHERE NOT EXISTS (
    SELECT 1 FROM employee e WHERE e.department_id = d.id
);
~~~

Warunek bazuje na relacji, a nie np. nullable email pracownika. Przy LEFT JOIN filtr statusu pracownika w ON oznacza coś innego niż w WHERE: pierwszy zachowuje pusty dział dla wybranego statusu, drugi może go usunąć. Testuję dział pusty, kilka osób i rekordy z null w polach opcjonalnych, a plan sprawdzam na indeksie klucza obcego.

## DB-13. Czym różnią się `COUNT(*)`, `COUNT(1)` i `COUNT(kolumna)`?

COUNT(*) liczy wiersze, COUNT(1) wiersze z niepustym stałym wyrażeniem, więc daje ten sam wynik. COUNT(kolumna) pomija NULL. COUNT DISTINCT liczy unikalne niepuste wartości. Nie zakładam uniwersalnej przewagi wydajności COUNT(1) nad COUNT(*).

Po LEFT JOIN COUNT(*) może policzyć również wiersz reprezentujący brak dopasowania; do liczby pracowników używam niepustego klucza z prawej tabeli. Dokładny count dużego zbioru może być kosztowny mimo krótkiego SQL.

Dla pustego działu LEFT JOIN nadal tworzy wiersz wyniku z null po stronie pracownika. COUNT(*) wynosi wtedy 1, a COUNT(e.id) 0, jeśli id nie jest nullable:

<!-- example:DB-13-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
SELECT d.id, COUNT(e.id) AS employee_count
FROM department d
LEFT JOIN employee e ON e.department_id = d.id
GROUP BY d.id;
~~~

GROUP BY może wymagać dodatkowych pól zależnie od SELECT i silnika. W agregacjach rozróżniam również brak grupy od istniejącej grupy z zerową liczbą dopasowań. Dla dashboardu można użyć projekcji przybliżonej, ale billing potrzebuje zdefiniowanej dokładności i granicy czasu.

## DB-14. Jak znaleźć drugą/N-tą pensję i ranking pracowników według doświadczenia?

Najpierw określam remisy. DENSE_RANK nadaje równe miejsca bez luk, RANK zostawia luki, ROW_NUMBER nadaje unikalne numery według porządku, wymagając tie-breakera dla determinizmu. Dla N-tej różnej pensji filtruję DENSE_RANK liczony malejąco po pensji.

Ranking doświadczenia liczę analogicznie, ewentualnie z PARTITION BY dział. Gdy pytanie dotyczy N-tego pracownika, a nie N-tej wartości, potrzebny jest inny ranking. Brak N wartości powinien dać pusty wynik lub jawny kontrakt, nie zmyśloną pensję.

N-ta różna pensja może zwrócić kilku pracowników:

<!-- example:DB-14-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
SELECT id, salary
FROM (
    SELECT id, salary,
           DENSE_RANK() OVER (ORDER BY salary DESC) AS position
    FROM employee
    WHERE salary IS NOT NULL
) ranked
WHERE position = :n;
~~~

Dla rankingu w dziale dodaję PARTITION BY department_id. Przy ROW_NUMBER definiuję tie-breaker ID, a przy RANK świadomie akceptuję luki po remisach. Test [100,100,90] daje drugą różną pensję 90, ale drugiego pracownika z pensją 100 — stąd potrzeba uzgodnienia znaczenia pytania przed napisaniem SQL.

## DB-15. Jaka jest logiczna kolejność wykonywania klauzul SQL?

Pomocny model to FROM/JOIN → WHERE → GROUP BY → HAVING → obliczenie wyników/window zgodnie z regułami dialektu → SELECT → DISTINCT → ORDER BY → LIMIT/OFFSET. Wyjaśnia to np. dlaczego alias SELECT zwykle nie jest dostępny w WHERE.

To kolejność logiczna, nie fizyczny plan: optymalizator może przestawiać operacje przy zachowaniu semantyki. Przy funkcjach okienkowych i filtrach zależnych od aliasów często potrzebna jest podkwerenda; szczegóły sprawdzam w używanym dialekcie.

WHERE filtruje wiersze przed grupowaniem, a HAVING grupy po agregacji. Dlatego warunek COUNT(*) > 1 należy do HAVING lub zewnętrznego zapytania, a nie zwykłego WHERE:

<!-- example:DB-15-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
SELECT department_id, COUNT(*) AS size
FROM employee
WHERE active = true
GROUP BY department_id
HAVING COUNT(*) > 1
ORDER BY size DESC;
~~~

Logiczny model wyjaśnia wynik, lecz fizyczny silnik może przepchnąć bezpieczny filtr wcześniej lub wybrać indeks zamiast sortowania. Przy optymalizacji nie próbuję „wymuszać kolejności myślenia” przez sam zapis CTE; patrzę na plan wykonania.

## DB-16. Po co normalizacja i kiedy SQL, NoSQL, Redis lub Elasticsearch?

Normalizacja ogranicza redundancję i anomalie aktualizacji. SQL dobrze pasuje do relacji, constraints i transakcji; NoSQL obejmuje różne modele dobierane do access patterns, a nie jeden zestaw gwarancji. Redis służy m.in. do szybkiego stanu/cache, Elasticsearch do wyszukiwania i analityki nad indeksem.

Dla księgowania wybieram trwałe źródło prawdy z odpowiednimi gwarancjami; search i cache są zwykle projekcjami odbudowywalnymi. Denormalizacja przyspiesza odczyty kosztem synchronizacji kopii. Nie wyprowadzam decyzji wyłącznie z liczby użytkowników.

W e-commerce DB transakcyjna może przechowywać zamówienia i stock, Redis popularne opisy, a Elasticsearch indeks wyszukiwania. Aktualizacja ceny w search nie powinna sama uprawniać do pobrania wskazanej kwoty: checkout weryfikuje autorytatywną wersję/ofertę.

Denormalizacja kopiuje dane celowo, np. nazwę produktu do historycznej pozycji zamówienia. Taki snapshot nie zawsze ma być synchronizowany z dzisiejszą nazwą. W projekcie rozróżniam kopię historycznego faktu od odtwarzalnej projekcji aktualnego stanu. „SQL vs NoSQL” bez listy zapytań, constraints i modelu awarii nie daje rzetelnego wyboru.

## DB-17. Czym różnią się partycjonowanie, sharding i read replicas? Jak wpływają na JOIN?

Partycjonowanie dzieli dane logicznie/fizycznie w ramach rozwiązania bazy; sharding rozkłada podzbiory między niezależne węzły. Repliki powielają dane i skalują odczyty, ale nie usuwają bottlenecku zapisów primary. Lag wpływa na read-your-writes.

JOIN między shardami wymaga koordynacji, transferu lub denormalizacji, dlatego współlokuję często łączone dane. Najpierw poprawiam zapytania, indeksy i retencję. Sharding wprowadza resharding, cross-shard transakcje i trudniejsze operacje — nie jest darmowym kolejnym krokiem.

Współlokacja danych po tenant_id ułatwia lokalne JOIN i transakcje wewnątrz tenanta, ale bardzo duży tenant może przeciążyć jeden shard. Podział po innym kluczu wyrówna ruch, lecz komplikuje zestawienia i granice spójności.

Read replica opóźniona o 2 s może po udanym zapisie zwrócić stary status. Można odczytać z primary, czekać na znaną pozycję replikacji lub zaakceptować oznaczoną nieświeżość. Resharding wymaga przeniesienia danych i zmian routingowych z kontrolą dwóch writerów. Samo dodanie instancji DB nie rozdziela automatycznie istniejących zapisów.

## DB-18. Jak magazyn kolumnowy przyspiesza analitykę?

Przechowuje wartości kolumn razem, co umożliwia czytanie tylko potrzebnych pól, lepszą kompresję, wektoryzację i pomijanie bloków według metadanych. Zapytanie sumujące trzy kolumny z miliarda wierszy może ograniczyć koszt transferu względem pełnych rekordów.

To inne potrzeby niż pojedyncze aktualizacje OLTP. Rozdzielam operacyjne transakcje od hurtowni i kontroluję opóźnienie zasilania. Wide-column store, np. model oparty na partycjach i clustering keys, nie jest automatycznie tym samym co analityczny magazyn kolumnowy.

W zapytaniu „suma sprzedaży według regionu za rok” magazyn kolumnowy czyta kilka kolumn i może pominąć całe bloki dzięki zakresom danych. Magazyn wierszowy jest wygodniejszy dla pojedynczego zamówienia i częstych zmian wielu jego pól. To różnica wzorca dostępu, nie ranking technologii.

Pipeline OLTP → analityka ma opóźnienie, wersje i ponowienia. Dashboard może tolerować minuty świeżości, ale kontrola dostępnych środków nie powinna opierać się na takiej projekcji. Mierzę koszt skanowanych bajtów, kompresję i czas zasilania, a nie jedynie liczbę rekordów.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [execution plan](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/execution_plan/README.md)
- [index](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/index/README.md)
- [pagination](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/sql/pagination/README.md)
<!-- labs:end -->
