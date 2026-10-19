# wide column

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** wide column.
> - **Typowy błąd:** Klucz partycji skupiający cały gorący ruch na jednej partycji.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=PartitioningTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Model i H2 sprawdzają logikę; blokady, plan zapytania i zachowanie silnika potwierdza dopiero odpowiedni profil infrastrukturalny.
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
