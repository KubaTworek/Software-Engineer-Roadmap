# Kafka i messaging

[Spis rozdziałów](README.md)

<!-- question-toc:start -->
## Spis pytań

- [MSG-01. Kafka czy RabbitMQ? Kolejka czy event stream?](#msg-01-kafka-czy-rabbitmq-kolejka-czy-event-stream)
- [MSG-02. Jak działa partycja Kafka i co określa klucz?](#msg-02-jak-działa-partycja-kafka-i-co-określa-klucz)
- [MSG-03. Jak działają consumer groups i przydzielanie partycji?](#msg-03-jak-działają-consumer-groups-i-przydzielanie-partycji)
- [MSG-04. Co robią Spring Kafka i `@KafkaListener`?](#msg-04-co-robią-spring-kafka-i-kafkalistener)
- [MSG-05. Co się stanie, gdy konsument padnie w połowie batcha?](#msg-05-co-się-stanie-gdy-konsument-padnie-w-połowie-batcha)
- [MSG-06. Jak zachować kolejność wiadomości dla klienta?](#msg-06-jak-zachować-kolejność-wiadomości-dla-klienta)
- [MSG-07. Jak zaprojektować idempotentnego konsumenta?](#msg-07-jak-zaprojektować-idempotentnego-konsumenta)
- [MSG-08. Kiedy retry, a kiedy DLQ i jak bezpiecznie zrobić replay?](#msg-08-kiedy-retry-a-kiedy-dlq-i-jak-bezpiecznie-zrobić-replay)
- [MSG-09. Jak badać rosnący lag lub konsumenta, który utknął?](#msg-09-jak-badać-rosnący-lag-lub-konsumenta-który-utknął)
- [MSG-10. Co zrobić z hot partition?](#msg-10-co-zrobić-z-hot-partition)
- [MSG-11. Jak utrzymać spójność DB i Kafka przy błędzie publikacji po zapisie?](#msg-11-jak-utrzymać-spójność-db-i-kafka-przy-błędzie-publikacji-po-zapisie)
- [MSG-12. Jak zbudować A → Kafka → B → REST C?](#msg-12-jak-zbudować-a--kafka--b--rest-c)
- [MSG-13. Gdzie kończą się gwarancje exactly-once Kafka?](#msg-13-gdzie-kończą-się-gwarancje-exactly-once-kafka)
- [MSG-14. Do czego służy Schema Registry i jak rozwijać kontrakty eventów?](#msg-14-do-czego-służy-schema-registry-i-jak-rozwijać-kontrakty-eventów)
- [MSG-15. Push czy pull w architekturze zdarzeniowej?](#msg-15-push-czy-pull-w-architekturze-zdarzeniowej)
<!-- question-toc:end -->

## MSG-01. Kafka czy RabbitMQ? Kolejka czy event stream?

Kafka przechowuje partycjonowany log z retencją i niezależnymi pozycjami konsumentów; dobrze pasuje do replay, wielu odbiorców i dużych strumieni. RabbitMQ daje bogate mechanizmy routingu i modeli kolejkowych. Szczegółowe możliwości zależą też od rodzaju kolejki/streamu i wersji.

Zdarzenie opisuje fakt, command prośbę o wykonanie. Nie wybieram brokera wyłącznie po throughput. Potrzebne są kryteria: kolejność, routing, opóźnienie, retencja, dostawa i koszty operacyjne. Kafka topic może rozdzielać pracę w grupie, ale nie usuwa danych po pojedynczym odbiorze.

Przykład work queue: każde zadanie ma wykonać jeden worker z grupy. Przykład event stream: fakt OrderPlaced niezależnie potrzebują billing, wysyłka i analityka, a nowa projekcja ma odtworzyć historię. Kafka ułatwia drugi model przez retencję i osobne offsety grup.

„Kafka nie jest kolejką” nie znaczy, że nie można nią rozdzielać pracy. Różni się sposób postępu, powtórzeń i usuwania danych. W RabbitMQ także istnieją różne typy kolejek i streams, więc porównuję konkretną konfigurację: routing, opóźnione próby, replay, rozmiar rekordów, operacje i wymagane gwarancje.

## MSG-02. Jak działa partycja Kafka i co określa klucz?

Partycja jest uporządkowanym logiem rekordów z offsetami, z liderem i replikami. Klucz typowo kieruje zdarzenia tego samego agregatu do jednej partycji, zapewniając lokalny porządek. Nie ma domyślnego globalnego porządku między partycjami.

Liczbę partycji dobieram do ruchu, storage i potrzebnej równoległości z zapasem operacyjnym; nie istnieje jedna poprawna liczba dla każdego topicu. Dodanie partycji może zmienić mapowanie klucza. Ważne są też rozmiar rekordów, batching i potwierdzenia producenta.

Klucz orderId utrzymuje zdarzenia zamówienia w jednej partycji przy stabilnym partycjonowaniu. Klucz status może skupić niemal cały ruch PENDING w jednym miejscu i ograniczyć throughput. Dobór klucza to decyzja o ownershipie kolejności i rozkładzie pracy.

Producent może batchować rekordy, kompresować i czekać na określone potwierdzenia. Awaria lidera wiąże się z replikacją i wyborem nowego, więc acks oraz minimalna liczba wymaganych replik mają znaczenie dla trwałości. Zwiększenie liczby partycji nie zwiększa bez końca przepustowości: rosną metadane, pliki, rebalance i koszt operacyjny.

## MSG-03. Jak działają consumer groups i przydzielanie partycji?

Grupa współdzieli przetwarzanie: w danym momencie partycja jest przypisana jednemu konsumentowi grupy. Strategia przypisania i protokół koordynują przydział oraz rebalance. Konsument może obsługiwać wiele partycji, a nadmiar konsumentów grupy pozostanie bez pracy.

Inna grupa może niezależnie czytać tę samą partycję i ma własne offsety. Dla pięciu usług potrzebujących wszystkich zdarzeń tworzę pięć logicznych subskrypcji/grup, nie pięciu członków jednej grupy. Zmiana przypisania wymaga bezpiecznego zakończenia pracy w toku.

Przy 12 partycjach i 3 konsumentach grupa może przydzielić każdemu kilka partycji; przy 20 konsumentach co najmniej część nie dostanie pracy. Pięć różnych usług czytających wszystkie fakty potrzebuje pięciu grup, choć każda grupa może mieć wiele replik.

Rebalance zmienia ownership, nie przenosi automatycznie poprawnie zakończonego stanu dowolnych tasków aplikacji. Trzeba uwzględnić wycofanie przypisania i bezpieczny commit. Równa liczba partycji nie oznacza równego obciążenia: jedna może zawierać znacznie droższe rekordy lub gorącego klienta. Dlatego obserwuję lag i czas per partycja.

## MSG-04. Co robią Spring Kafka i `@KafkaListener`?

Spring Kafka integruje producentów, listener containers, konwersję, obsługę błędów i transakcje z aplikacją Spring. KafkaListener deklaruje metodę obsługującą rekordy/batche, ale jej realne zachowanie zależy od konfiguracji container factory, group, ack mode i error handlera.

Konfiguruję identyfikatory, serializery, concurrency, bezpieczeństwo i limity przetwarzania; monitoruję lag i błędy. Nie zakładam, że samo dodanie adnotacji daje exactly-once w bazie. Na pytanie o doświadczenie podaję własny przepływ, awarię i sposób naprawy.

Szkic listenera Spring Kafka:

<!-- example:MSG-04-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@KafkaListener(topics = "orders", groupId = "billing")
public void onOrder(OrderPlaced event) {
    billing.handle(event);
}
~~~

Konfiguracja listener factory określa deserializację, ack, błędy i concurrency. Event oraz billing są domenowe. Sam powrót z metody nie jest uniwersalnym „commitem wszystkich zasobów”. Test integracyjny powinien zatrzymać proces w oknie między efektem a potwierdzeniem i potwierdzić bezpieczne redelivery. Walidację schematu rozdzielam od decyzji domenowej, żeby nie ponawiać w nieskończoność rekordu, który nigdy nie może przejść.

## MSG-05. Co się stanie, gdy konsument padnie w połowie batcha?

Konsument może wykonać efekt, a paść przed potwierdzeniem offsetu. Rekord wróci i handler musi bezpiecznie rozpoznać wcześniejsze przetworzenie. Commit przed efektem tworzy odwrotne ryzyko: restart ominie niewykonaną pracę. Offset commit wskazuje pozycję następnego rekordu.

**Błędny wariant przy równoległym batchu:** rekord 11 skończył, 10 nadal działa, więc commit(maxCompleted+1)=12 ominie 10 po crashu.

<!-- example:MSG-05-1 kind:java-members-negative -->
**Kontekst:** Java 21 — kompilowalny kontrprzykład błędnej logiki; skrypt nie uruchamia tego kodu jako poprawnego rozwiązania.

~~~java
static long unsafePosition(java.util.NavigableSet<Long> completed) {
    return completed.last() + 1;
}
~~~

**Poprawny rdzeń śledzenia ciągłego prefiksu:**

<!-- example:MSG-05-2 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static long commitPosition(long next, java.util.NavigableSet<Long> completed) {
    while (completed.remove(next)) next++;
    return next;
}
~~~

Dla next=10 i completed={11} drugi wariant zwróci 10. Gdy dojdzie 10, zwróci 12. Metody są modelem jednowątkowego trackera jednej partycji, bez pełnego consumer lifecycle i walidacji zakresów. Zapis efektu i markera deduplikacji musi być atomowy lokalnie; tracker nie chroni sam przed podwójnym skutkiem. Rebalance wymaga osobnej obsługi ownershipu oraz prac w toku.

<!-- follow-up:MSG-05 -->
**Sprawdź rozumienie:** Tracker przesuwa offset prawidłowo. Czy zdalna płatność jest już dokładnie raz?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Tracker zapobiega pominięciu niedokończonego prefiksu. Crash po efekcie przed commit offsetu nadal daje redelivery; zewnętrzny skutek potrzebuje idempotency key, statusu i rekoncyliacji.

</details>

## MSG-06. Jak zachować kolejność wiadomości dla klienta?

Używam stabilnego klucza klienta/agregatu i sekwencyjnego przetwarzania jego zdarzeń w partycji. Ordering logu nie gwarantuje kolejności zakończenia asynchronicznych wywołań. Retry w osobnym topicu może pozwolić kolejnym zdarzeniom wyprzedzić wcześniejsze.

Jeżeli kolejność jest krytyczna, blokuję odpowiednią sekwencję albo wykorzystuję wersję agregatu, wykrywanie luk i parking. Globalny porządek przez jedną partycję ogranicza skalowanie. Zmiany partycjonowania i replay wymagają świadomego planu.

Jeżeli wersja 8 zamówienia dotrze po 9 wskutek retry lub replay, consumer nie powinien ślepo nadpisać nowego stanu starym. Dla projekcji można warunkowo zastosować tylko nowszą wersję; dla komend wymagających wszystkich kroków należy wykryć lukę i ją rozstrzygnąć.

Klucz gwarantuje lokalny porządek zapisu do logu, ale wiele producentów i równoległe handlery mogą wymagać dodatkowej sekwencji domenowej. Retry topics poprawiają dostępność partycji kosztem kolejności. Przy operacji finansowej świadomie wybieram, czy blokada jednego agregatu ma zatrzymać tylko jego, czy całą partycję.

## MSG-07. Jak zaprojektować idempotentnego konsumenta?

Event ma stabilne ID. W jednej transakcji lokalnej zapisuję marker przetworzenia z unique constraintem i zmianę domenową; duplikat zwraca już wykonany wynik. Sam check w pamięci przed zapisem ma wyścig i nie przetrwa restartu.

Zdalny efekt wymaga własnego idempotency key/statusu. Określam retencję markerów w relacji do replay i danych archiwalnych. Idempotentny producer Kafka ogranicza pewne duplikaty publikacji, ale nie zapewnia sam idempotencji biznesowej konsumenta.

Trwała deduplikacja opiera się na constraint:

<!-- example:MSG-07-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
CREATE TABLE processed_event (
    consumer_name text NOT NULL,
    event_id text NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);
~~~

Wstawienie markera i efekt domenowy wykonuję w jednej transakcji. Konflikt klucza oznacza już obsłużony rekord według kontraktu. Consumer_name pozwala różnym projekcjom przetworzyć ten sam fakt niezależnie. Dla replay po roku retencja markerów musi nadal chronić nieodwracalne efekty. Projekcję można odbudować do nowej generacji, lecz nie oznacza to ponownego wysłania wszystkich przelewów.

## MSG-08. Kiedy retry, a kiedy DLQ i jak bezpiecznie zrobić replay?

Błąd przejściowy dostaje ograniczone retry z backoff/jitter. Niepoprawny schemat lub trwałe naruszenie kontraktu nie powinny zapętlać partycji bez końca. DLQ przechowuje oryginalną tożsamość, bezpieczne metadane błędu i kontekst do naprawy, z alertem i właścicielem.

Replay wykonuję po usunięciu przyczyny, zachowując ID, kontrolując tempo, kolejność i duplikaty. DLQ nie oznacza sukcesu biznesowego. Nie wrzucam tam sekretów ani pełnego payloadu bez kontroli dostępu i retencji.

DLQ powinno mieć procedurę wyjścia: kto analizuje błąd, jak poprawia dane/kod i jak sprawdza wynik replay. W przeciwnym razie przeniesienie rekordu tylko usuwa widoczny problem z głównej kolejki. Wiek najstarszego wpisu jest często ważniejszy niż sama liczba.

Przy rekordzie z błędnym schematem retry co sekundę nie pomoże; przy chwilowej niedostępności DB może pomóc. Zachowuję oryginalne ID i przyczynę, ale nie loguję całego wrażliwego payloadu bez potrzeby. Replay ma kontrolę tempa i obserwację efektów, żeby naprawa nie zamieniła się w nowy incydent.

## MSG-09. Jak badać rosnący lag lub konsumenta, który utknął?

Rozbijam lag na partycje i wiek najstarszej pracy. Sprawdzam tempo napływu i przetwarzania, poll, rebalance, max poll interval, błędy, GC, DB, zewnętrzne API i czas deserializacji. Lag równy zero nie dowodzi, że efekt biznesowy został wykonany poprawnie.

Zwiększam równoległość do granicy partycji i downstreamu, usuwam hotspot lub optymalizuję handler. Nieograniczony lag grozi przekroczeniem retencji i utratą możliwości odczytu. Mierzę czas nadrobienia po awarii, a nie tylko stan bieżący.

Jeżeli producent dostarcza 1000 rekordów/s, a grupa kończy 800/s, lag rośnie około 200/s. Aby nadrobić milion rekordów w godzinę, trzeba oprócz bieżącego ruchu uzyskać około 278 dodatkowych rekordów/s. To prosty sposób oceny, czy proponowane skalowanie ma szansę.

Offset lag nie zawsze opisuje wiek ani koszt: jeden ciężki rekord może trwać długo, a tysiące lekkich kończyć się szybko. Zbieram więc throughput, processing latency, oldest age, rebalance i błędy. Przed zwiększeniem concurrency sprawdzam, czy downstream ma wystarczający zapas.

## MSG-10. Co zrobić z hot partition?

Najpierw sprawdzam rozkład kluczy i koszt rekordów: jedna duża grupa może dominować ruch lub pojedynczy rekord blokować handler. Dodatkowy konsument nie podzieli automatycznie tej samej partycji w jednej grupie.

Zmiana klucza, subsharding lub wydzielenie gorącego agregatu pomagają tylko, jeśli można zachować wymagane reguły porządku. Salting bez planu rekonstrukcji kolejności psuje semantykę. Czasem właściwa jest kontrola ruchu jednego klienta, nie przebudowa wszystkich topiców.

Gorąca partycja może wynikać z jednego tenanta, złego klucza albo jednego bardzo drogiego typu rekordu. Dodanie partycji pomaga dopiero po kontrolowanej zmianie routingu kluczy i sprawdzeniu kolejności. Stare dane w już istniejącej partycji nie rozdzielą się automatycznie.

Jeżeli agregat wymaga ścisłej sekwencji, jego pojedynczy strumień ma naturalną granicę równoległości. Można wydzielić kosztowne niezależne obliczenia, ale końcowe zastosowanie zmian nadal musi respektować wersje. Quota jednego klienta, osobny topic lub shard redukują wpływ na pozostałych bez udawania, że każdą pracę można dowolnie podzielić.

## MSG-11. Jak utrzymać spójność DB i Kafka przy błędzie publikacji po zapisie?

Zwykły commit DB i późniejsze send to dwa niezależne efekty. Po awarii nie można po prostu wycofać już zatwierdzonej transakcji. Transactional outbox zapisuje zmianę domenową i zamiar publikacji w tej samej transakcji; relay/CDC publikuje później z retry.

Relay może wysłać duplikat, więc odbiorca musi być idempotentny. Dotyczy to także XML → transformacja → zapis → następny topic. Przy błędzie przed commitem rollback lokalny działa; po commicie potrzebne jest dokończenie lub jawna kompensacja, nie udawany rollback.

Minimalny outbox zapisuje identyfikator zdarzenia, agregat, wersję i payload:

<!-- example:MSG-11-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
BEGIN;
UPDATE orders SET status = 'CONFIRMED' WHERE id = :id;
INSERT INTO outbox(event_id, aggregate_id, event_type, payload)
VALUES (:event_id, :id, 'OrderConfirmed', :payload);
COMMIT;
~~~

To szkic; należy sprawdzić wynik UPDATE i constrainty. Relay może opublikować rekord, paść przed oznaczeniem go jako wysłany i wysłać ponownie. Outbox usuwa lukę „commit bez zamiaru publikacji”, nie gwarantuje pojedynczej dostawy. Monitoring obejmuje wiek zaległych zdarzeń, retencję oraz zdolność odtworzenia po awarii.

<!-- follow-up:MSG-11 -->
**Sprawdź rozumienie:** Relay wysłał outbox event i padł przed oznaczeniem go jako opublikowanego. Co po restarcie?

<details>
<summary>Kierunek odpowiedzi</summary>

Może wysłać go ponownie. Outbox chroni trwały zamiar, a konsument deduplikuje po stabilnym event ID. Oznaczenie przed wysłaniem zamieniłoby duplikat w ryzyko utraty.

</details>

## MSG-12. Jak zbudować A → Kafka → B → REST C?

A publikuje zdarzenie, B konsumuje przez listener, waliduje i przekształca dane, po czym wywołuje C klientem HTTP z deadline i idempotency key. Jeśli C padnie po wykonaniu skutku, odpowiedź może zginąć i B musi bezpiecznie ponowić lub sprawdzić status.

Nie potwierdzam offsetu przed zapewnieniem trwałej odpowiedzialności za dalszą pracę. Dla długich integracji B może zapisać trwały job/outbox i od razu zakończyć odbiór. Ustalam ordering i sposób pogodzenia statusu Kafka, DB oraz C.

B może synchronicznie czekać na C i dopiero potem zakończyć rekord, ale wtedy wolne C zużywa budżet poll i blokuje postęp. Alternatywnie B atomowo zapisuje trwałe zadanie integracyjne, potwierdza odpowiedzialność i osobny worker wywołuje C. Zmienia się wtedy moment zakończenia biznesowego procesu.

Jeśli C odpowie timeoutem po wykonaniu, worker sprawdza tę samą operację lub powtarza z tym samym idempotency key. Duplikat message nie powinien utworzyć nowego joba. Rozdzielam status „Kafka odebrana”, „job zapisany” i „C potwierdziło”, bo mieszanie ich daje fałszywy obraz sukcesu.

## MSG-13. Gdzie kończą się gwarancje exactly-once Kafka?

Transakcje Kafka mogą atomowo połączyć publikacje i offsety w obsługiwanym przepływie consume-transform-produce; odbiorcy muszą respektować odpowiednią izolację. Nie obejmuje to automatycznie dowolnego przelewu bankowego lub zapisu zewnętrznej bazy.

Rozróżniam dostarczenie, wykonanie handlera i pojedynczy efekt biznesowy. Dla granicy Kafka–DB używam lokalnej deduplikacji/outbox i rekoncyliacji. Każda obietnica exactly-once powinna wskazywać domenę atomowości i zachowanie po crashu. [Gwarancje dostawy Kafka](https://kafka.apache.org/40/design/design/).

Pojęcie exactly-once powinno wskazywać granicę: transakcja Kafka może atomowo opublikować wynik i przesunąć offset w Kafka. Konsument read_committed nie powinien zobaczyć niezatwierdzonego wyniku. Zewnętrzny email lub DB update nie staje się przez to częścią tej samej transakcji.

<!-- example:MSG-13-1 kind:properties-fragment -->
**Kontekst:** Fragment konfiguracji properties; wymaga zgodnej wersji biblioteki i otoczenia; bez testu uruchomienia.

~~~properties
isolation.level=read_committed
~~~

To pojedyncze ustawienie konsumenta, nie kompletna konfiguracja transakcyjnego przepływu. Po awarii efekt zewnętrzny nadal potrzebuje deduplikacji/statusu. W rozmowie rozróżniam liczbę wykonań handlera, liczbę widocznych rezultatów logu i pojedynczy skutek finansowy; te obietnice wymagają różnych dowodów.

## MSG-14. Do czego służy Schema Registry i jak rozwijać kontrakty eventów?

Registry przechowuje schematy i wersje oraz może kontrolować kompatybilność. Serializer zapisuje identyfikację schematu, a odbiorca odtwarza zgodną reprezentację. To dodatkowy komponent ekosystemu, nie automatyczna własność każdego brokera Kafka.

Wybieram Avro/Protobuf/JSON Schema i kierunek kompatybilności, testuję stare i nowe aplikacje. Dodanie pola wymaga prawidłowych defaults/semantyki, a usunięcie znaczenia może być breaking change mimo poprawnej walidacji schematu. [Dokumentacja Confluent Schema Registry](https://docs.confluent.io/platform/current/schema-registry/index.html).

Kompatybilność schematu nie chroni przed zmianą znaczenia, np. centy → euro w tym samym polu long. Nowe opcjonalne pole również może prowadzić do błędnej wartości domyślnej, jeśli stary rekord nie miał wymaganej informacji biznesowej.

Testuję starego producenta z nowym konsumentem i odwrotnie według planu rolloutu. Zmiana nazw, numerów pól Protobuf, enumów lub sposobu interpretacji null ma własne reguły. Registry jest zależnością z cache i kontrolą dostępu; jego chwilowa awaria może inaczej wpływać na klienta ze znanym schematem niż na publikację zupełnie nowej wersji.

## MSG-15. Push czy pull w architekturze zdarzeniowej?

W push źródło lub broker inicjuje dostarczenie, w pull odbiorca pobiera dane we własnym tempie. KafkaConsumer pobiera rekordy przez poll, ale wykonanie callbacka aplikacyjnego może wyglądać jak push. Interfejs biblioteki i protokół brokera to dwa poziomy.

Pull ułatwia kontrolę popytu, push wymaga zarządzania wolnym odbiorcą i buforami. Oba modele potrzebują potwierdzeń, retry i limitów. Sposób inicjowania transferu sam nie ustala trwałości ani gwarancji exactly-once.

Pull pozwala konsumentowi kontrolować pobieranie, ale nie zatrzymuje producenta zapisującego do logu. Gdy odbiorca zwalnia, dane nadal rosną w brokerze aż do ograniczeń retencji/storage. Backpressure aplikacyjny musi więc uwzględnić także tę granicę.

Push wymaga kontroli liczby niepotwierdzonych wiadomości i wolnego odbiorcy; inaczej broker lub klient gromadzi nieograniczony bufor. Publiczny callback listenera to interfejs biblioteki, nie dowód protokołu sieciowego push. Wybierając model, określam miejsce buforowania, limit, właściciela retry i zachowanie po utracie odbiorcy.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [kafka](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/kafka/README.md)
- [consumer](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/consumer/README.md)
- [cdc reconciliation](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_b/cdc_reconciliation/README.md)
<!-- labs:end -->
