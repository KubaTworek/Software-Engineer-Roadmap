# consistency

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** consistency.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „consistency” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ConditionalDocumentStoreTest,QuorumConfigurationTest,ReplicatedValueStoreTest" test`
> - **Role klas:** `QuorumConfiguration` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Spójność w bazach NoSQL



Spójność jest właściwością konkretnej operacji i ścieżki odczytu, a nie prostą
etykietą całej bazy. Źródło prawdy może wymagać silnej spójności, podczas gdy
cache, wyszukiwarka lub projekcja CQRS świadomie dopuszcza stare dane.

## Conditional write

`ConditionalDocumentStore` przechowuje `VersionedValue`. Dwóch writerów może
odczytać tę samą wersję, ale `replaceIfVersion` zastosuje tylko jedną zmianę.
Przegrany otrzymuje aktualny snapshot i podejmuje jawną decyzję o retry lub
konflikcie. Warunek i zapis muszą być atomowe po stronie bazy; wcześniejszy
odczyt w aplikacji nie wystarcza.

## Quorum

`QuorumConfiguration` pokazuje relacje `R + W > N` oraz `2W > N`. Konfiguracja
`N=3, R=2, W=2` tworzy przecięcia i toleruje awarię jednej repliki. Konfiguracja
`N=3, R=1, W=1` jest bardziej dostępna, ale odczyt może ominąć udany zapis, a
dwa zapisy mogą zakończyć się na rozłącznych replikach.

To warunek konieczny dla niektórych gwarancji, nie dowód linearizability.
Timestampy klientów, konflikty równoczesnych wersji, sloppy quorum i read repair
mogą zmienić obserwowane zachowanie.

## Replication lag i gwarancje sesyjne

`ReplicatedValueStore` ma leadera, replikę oraz kolejkę zmian. `readReplica()`
celowo może zwrócić stary stan. `write()` zwraca `ConsistencyToken`, a
`readYourWrites(token)` nie pozwala zejść poniżej zapisanej przez klienta wersji:
czyta z leadera, dopóki replika nie dogoni tokenu.

Read-your-writes nie jest tym samym co strong consistency dla wszystkich
klientów. Inne ważne gwarancje sesyjne to monotonic reads, monotonic writes oraz
writes-follow-reads.

## Pytania projektowe

- Czy stary odczyt może spowodować stratę pieniędzy lub naruszenie uprawnień?
- Czy konflikt można bezpiecznie ponowić?
- Czy projekcja ma wersję i sposób odbudowy?
- Jak klient rozpoznaje, że czyta stan starszy od własnego zapisu?
- Co system robi podczas partition: odmawia operacji czy akceptuje rozbieżność?
