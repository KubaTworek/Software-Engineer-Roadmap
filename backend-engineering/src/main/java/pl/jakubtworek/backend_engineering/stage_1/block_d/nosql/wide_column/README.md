# wide column

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** wide column.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „wide column” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=PartitioningTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Wide-column: partycja jest jednostką skali



W Cassandrze lub ScyllaDB tabela jest projektowana dla konkretnego zapytania.
Partition key wskazuje nody i lokalizuje dane, a clustering columns porządkują
wiersze wewnątrz partycji. Zapytanie niezgodne z tym kształtem zwykle wymaga
innej tabeli, a nie dowolnego skanu.

`DeviceMetricRow` pokazuje metryki urządzenia uporządkowane w czasie.
`BucketedPartitionKey` buduje klucz z:

- właściciela danych, np. `deviceId`,
- początku bucketu czasowego,
- deterministycznego numeru sharda.

Bucket ogranicza wzrost partycji. Shard rozprasza zapis popularnego klucza, ale
powoduje read fan-out: aby pobrać cały bucket, trzeba odczytać wszystkie shardy i
scalić wyniki. Zbyt wiele shardów zwiększa liczbę round-tripów, a zbyt mało nie
usuwa hot partition.

`PartitionLoadAnalyzer` grupuje próbkę kluczy i oblicza udział najbardziej
obciążonej partycji. Próg nie jest uniwersalny — należy go zestawić z limitem
throughputu pojedynczej partycji, rozmiarem rekordów, compaction, retencją i
rozkładem ruchu w percentylach, nie tylko ze średnią.

`UserEventRow` reprezentuje query „ostatnie zdarzenia użytkownika”. Dla bardzo
aktywnych użytkowników sam `userId` nie jest bezpiecznym kluczem na zawsze.
Dodanie bucketu dnia lub godziny ogranicza partycję, ale wymaga od klienta
przejścia przez kilka bucketów podczas odczytu długiego zakresu.
