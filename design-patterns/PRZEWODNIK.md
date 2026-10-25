# Jak wybierać i łączyć wzorce

[README](README.md) · [Indeks](INDEKS.md) · [Materiały](MATERIALY.md)

## Zacznij od zmiany albo awarii

| Obserwowany problem | Kierunek porównania | Najpierw sprawdź |
| --- | --- | --- |
| Wiele wariantów jednej reguły ceny | Strategy, Specification | Czy wystarczy funkcja i jawny kontrakt wyniku? |
| Złożone tworzenie i niepoprawne stany pośrednie | Builder, Domain Factory, Type-state | Czy prosty walidujący konstruktor rozwiązuje problem? |
| Stare API nie pasuje do portu | Adapter, ACL | Czy różnią się tylko typy, czy również znaczenie danych? |
| Dwie niezależne osie podklas | Bridge | Czy obie osie rzeczywiście mają więcej niż jeden wariant? |
| Dodatkowe zachowanie wokół wywołania | Decorator, Proxy, Interceptor | Czy kolejność auth, cache, retry i metryk jest poprawna? |
| Zmiana zachowania wraz z cyklem życia | State, tabela przejść | Kto kontroluje przejście i jego atomowość? |
| Wiele reakcji na ten sam fakt | Observer, Pub/Sub, Domain Event | Czy reakcje muszą przeżyć restart i działać asynchronicznie? |
| Kod domenowy zależy od HTTP i SQL | Ports and Adapters, DI | Jaki konkretny port wynika z potrzeby use case? |
| Dwa zapisy nadpisują się nawzajem | Optimistic Lock, Pessimistic Lock | Jaka jest granica transakcji i jak klient obsłuży konflikt? |
| Commit bazy i publikacja eventu mogą się rozdzielić | Outbox, CDC | Czy publikujesz fakt biznesowy, czy techniczną zmianę wiersza? |
| Wiadomość lub request może przyjść drugi raz | Inbox, Idempotency Key | Czy marker i lokalny skutek są atomowe? |
| Proces obejmuje kilka niezależnych właścicieli danych | Saga, TCC, 2PC | Co jest odwracalne, a jaki wynik może pozostać nieznany? |
| Odczyt i zapis mają inne potrzeby | CQRS, Materialized View | Czy osobne modele wystarczą bez osobnych usług i baz? |
| Potrzebna jest rekonstrukcja pełnego stanu z faktów | Event Sourcing | Czy jesteś gotowy utrzymywać historyczne schematy i replay? |
| Awaria zależności blokuje całą aplikację | Deadline, Bulkhead, Circuit Breaker | Jaki zasób się wyczerpuje: czas, wątki, połączenia czy pamięć? |
| Przeciążenie rośnie po dołożeniu retry | Retry Budget, Backpressure, Load Shedding | Ile łącznych prób powstaje dla jednego żądania? |
| Jeden klucz dominuje ruch | Hot-key mitigation, Single Flight, caching | Czy można rozbić zapis bez naruszenia ścisłego limitu? |
| System ma rosnąć bez wspólnego wdrożenia | Modular Monolith, Microservices | Czy niezależność uzasadnia koszt operacyjny i spójności? |
| Nowe i stare wersje muszą współistnieć | Expand–Contract, Canary, Branch by Abstraction | Czy rollback kodu nadal odczyta dane nowej wersji? |

Nazwy znajdziesz przez [indeks alfabetyczny](INDEKS.md). Każda karta podaje konkretny przykład i pytanie,
które rozstrzyga, czy mechanizm pasuje do sytuacji.

## Pary, które łatwo pomylić

| Para | Istotne rozróżnienie |
| --- | --- |
| Factory Method / Simple Factory | GoF deleguje tworzenie do nadpisywanej metody kreatora; nazwana funkcja tworząca nie potrzebuje tej hierarchii. |
| Abstract Factory / Builder | Fabryka wybiera spójną rodzinę, builder organizuje etapy tworzenia złożonego produktu. |
| Adapter / Bridge / Decorator / Proxy / Facade | Dopasowanie kontraktu / niezależne osie / dołożenie zachowania / kontrola dostępu / uproszczenie podsystemu. Podobna delegacja ma odmienne cele. |
| Strategy / State | Strategię wybiera klient jako politykę; State opisuje zachowanie oraz przejścia wynikające z fazy życia. |
| Strategy / Template Method | Kompozycja wymiennej funkcji versus dziedziczenie szkieletu algorytmu. |
| Command / Event | Polecenie wyraża intencję i może zostać odrzucone; event opisuje fakt, który już zaszedł. |
| Observer / trwały Pub/Sub | Lokalny callback nie zapewnia retencji, replay, offsetów ani potwierdzeń transportu. |
| Repository / DAO / Data Mapper | Kolekcja agregatów / techniczne operacje danych / tłumaczenie stanu obiekt–baza. |
| Value Object / DTO | Równość i niezmienniki domenowe versus kontrakt przenoszenia danych; rekord może pełnić jedną lub obie role zależnie od granicy. |
| CQRS / Event Sourcing | Oddzielne modele zapisu i odczytu versus historia zdarzeń jako źródło prawdy. Można je stosować niezależnie. |
| Outbox / Inbox / Idempotency Key | Atomowy zamiar wysyłki / atomowe przyjęcie i lokalny skutek / deduplikacja żądań klienta wraz z wynikiem. |
| Saga / rollback ACID | Semantyczne, czasem zawodne kompensacje versus wycofanie jednej lokalnej transakcji. |
| Idempotencja / exactly-once | Własność powtórzonego efektu versus gwarancja określonego zakresu protokołu; nazwa brokera nie obejmuje dowolnego HTTP. |
| Timeout / anulowanie | Koniec oczekiwania klienta versus próba zakończenia pracy. Żadne samo w sobie nie cofa wcześniejszego commitu. |
| Rate Limiter / Bulkhead / Circuit Breaker | Tempo przyjęć / liczba równoległych prac / dopuszczanie wywołań według historii awarii. |
| Cache / Identity Map | Kopia służąca przyspieszeniu odczytu versus jedna reprezentacja ID w zakresie pracy. |
| Leader–Followers / Leader Election | Wzorzec organizacji wątków obsługi zdarzeń versus protokół własności roli w systemie rozproszonym. |
| Partitions / Shards / Replicas | Podział uporządkowanego strumienia / podział danych między właścicieli / kopie danych; mogą współistnieć. |

## Przykład złożenia: przyjęcie zamówienia

Przyjmij wymagania: jedno zamówienie może mieć wiele prób HTTP, zapas jest w innym kontekście, płatność może
zakończyć się przed timeoutem klienta, a wyszukiwarka może być opóźniona. Nie wynika z tego obowiązek
mikroserwisów: te same granice można najpierw zachować w modularnym monolicie.

1. **Granica API:** walidacja, identyfikacja tenanta i uprawnienia do zasobu. Idempotency key ma zakres
   `(tenant, operacja, key)` i odcisk istotnego payloadu.
2. **Use case:** application service wywołuje model Order przez porty. Reguła rabatu może być Strategy, a
   kwalifikacja klienta Specification.
3. **Commit:** Order i outbox trafiają do jednej transakcji. Konflikt wersji powoduje ponowny odczyt i
   decyzję, a nie ślepe zapisanie starego obiektu.
4. **Dostawa faktu:** relay może wysłać OrderPlaced dwa razy. Konsument zapisuje inbox i własny lokalny skutek
   w tej samej transakcji.
5. **Proces:** orkiestrator sagi pilnuje rezerwacji, płatności i wysyłki. Każdy krok ma ID i trwały postęp;
   timeout płatności oznacza sprawdzenie jej statusu albo ponowienie z tym samym kluczem, a nie automatyczny
   refund.
6. **Odczyt:** projekcja CQRS pokazuje status z określonym lagiem. Nie potrzeba Event Sourcing, jeśli źródłem
   prawdy pozostaje aktualny stan i outbox.
7. **Odporność:** wspólny deadline ogranicza czas, bulkhead zasoby, retry budget dodatkowe próby, breaker
   odcina zależność po uzasadnionej serii awarii.
8. **Naprawa:** reconciliation znajduje PAID bez zlecenia wysyłki; naprawa jest idempotentna i respektuje
   wersję, aby nie nadpisać nowszej decyzji.

[Modele i scenariusze poprawności](LABORATORIA.md) opisują wybrane fragmenty tej historii. Cały opis powyżej
jest scenariuszem architektonicznym, nie dodatkową gotową aplikacją.

## Antywzorce i sygnały ostrzegawcze

| Sygnał | Dlaczego szkodzi | Kierunek naprawy |
| --- | --- | --- |
| Pattern Mania / Speculative Generality | Interfejs i pięć klas dla stałej reguły zwiększają koszt zmian bez obsługi realnej zmienności. | Zacznij od prostej funkcji; wydziel politykę przy rzeczywistym wariancie. |
| God Object / God Service | Jeden obiekt zna wszystkie dane, integracje i reguły. | Wyznacz use case, niezmienniki i właścicieli danych. |
| Distributed Monolith | Wiele wdrożeń wymaga wspólnego release, schematu i długich łańcuchów synchronicznych. | Ogranicz kontrakty, usuń boczny dostęp do danych albo skonsoliduj moduły. |
| Anemic Domain Model | Złożone reguły są rozproszone po kontrolerach i serwisach, a obiekty służą tylko jako rekordy. | Przenieś niezmienniki do modelu; dla prostego CRUD świadomie wybierz Transaction Script. |
| Dual Write | Commit bazy i publish lub dwa niezależne zapisy mogą zakończyć się połową operacji. | Lokalna transakcja z Outbox i idempotentny odbiorca; określ źródło prawdy. |
| Retry Storm / Unbounded Queue | Awaria generuje coraz więcej pracy i pamięci zamiast odzyskania. | Jeden budżet prób, backoff z jitter, ograniczenia zasobów i admission control. |
| Shared Mutable Singleton | Globalny obiekt ukrywa zależności, wyścigi i sprzężenie testów. | Jawne zależności, niezmienne wartości i określony właściciel stanu. |
| Leaky Abstraction | Lokalnie wyglądający getter wykonuje HTTP albo lazy SQL bez widocznego kosztu i timeoutu. | Ujawnij granicę zdalną i kontrakt awarii w API właściwej warstwy. |
| Happy-path-only Tests | Test nie obejmuje duplikatu, luki, konfliktu, crash po efekcie ani awarii kompensacji. | Nazwij niezmiennik i odtwórz najkrótszą historię, która go łamie. |

## Sześć pytań przed zastosowaniem wzorca

1. Jaki konkretny problem, zmienność albo awarię rozwiązuje?
2. Co pozostaje niezmiennikiem i kto go egzekwuje?
3. Jaki jest koszt pamięci, czasu, dodatkowych zapisów i operacji?
4. Co stanie się po duplikacie, timeout, zmianie kolejności i restarcie?
5. Jaki prostszy wariant porównano i kiedy byłby wystarczający?
6. Jaki test albo eksperyment może obalić założenie o poprawności?
