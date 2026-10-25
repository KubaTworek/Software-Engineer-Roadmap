# Skalowanie, replikacja i przetwarzanie danych

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#scaling)

Wzorce topologii oraz powiązane algorytmy. **17 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [Rendezvous Hashing i zmiana topologii](../../labs/06-distributed-data.md).
Studium w Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Skalowanie rozdziela ruch, dane i obliczenia, ale każdy z tych podziałów ma
inny kontrakt. Replika jest kopią, shard właścicielem podzbioru danych, a partycja logu zakresem porządku.
Zanim wybierzesz mechanizm, nazwij wąskie gardło i wymagany niezmiennik. Więcej węzłów może poprawić
przepustowość, lecz zwiększa koszt koordynacji, migracji oraz obserwowania nieaktualnych kopii.

## Spis kart

- [Load Balancing / Stateless Service](#load-balancing--stateless-service)
- [Sharding / Horizontal Partitioning](#sharding--horizontal-partitioning)
- [Consistent Hashing / Rendezvous Hashing](#consistent-hashing--rendezvous-hashing)
- [Read Replica / Leader–Follower Replication](#read-replica--leaderfollower-replication)
- [Multi-leader / Leaderless Replication](#multi-leader--leaderless-replication)
- [Partitioned Log / Partition per Key](#partitioned-log--partition-per-key)
- [Fan-out on Write / Fan-out on Read / Hybrid](#fan-out-on-write--fan-out-on-read--hybrid)
- [MapReduce / Scatter–Reduce](#mapreduce--scatterreduce)
- [Batch / Stream Processing / Lambda / Kappa](#batch--stream-processing--lambda--kappa)
- [Windowing / Watermark / Late Data Handling](#windowing--watermark--late-data-handling)
- [Index Table / Inverted Index](#index-table--inverted-index)
- [CDN / Edge Caching](#cdn--edge-caching)
- [Data Locality / Colocation](#data-locality--colocation)
-
  [Hot-key Mitigation / Salting / Hierarchical Aggregation](#hot-key-mitigation--salting--hierarchical-aggregation)
- [Geode / Active–Active Regions / Geo-routing](#geode--activeactive-regions--geo-routing)
- [Autoscaling / Scheduled Scaling / Target Tracking](#autoscaling--scheduled-scaling--target-tracking)
- [Multi-tenancy / Pool–Silo–Bridge](#multi-tenancy--poolsilobridge)

## Load Balancing / Stateless Service

**Intuicja:** Jedna instancja aplikacji może nie wystarczyć, a jej restart nie powinien zrywać całej usługi.
Load Balancing dzieli żądania między instancje. Stateless oznacza, że poprawna obsługa następnego żądania nie
wymaga ukrytego, niezastępowalnego stanu poprzedniej instancji; źródła danych nadal mogą być stanowe.

**Problem i mechanizm:** Ruch dzieli się między instancje, a stan sesji przechowuje poza lokalną pamięcią,
jeśli wymaga przetrwania failover.

**Role i współpraca:** Balancer wybiera endpoint, readiness ogranicza kandydatów, store utrzymuje trwały stan.
Sesja klienta ma jawny model niezależny od lokalnego stosu serwera.

**Przykład:** Trzy repliki API obsługują kolejne requesty; sesja jest podpisanym tokenem albo wpisem
współdzielonego store.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Endpoint endpoint = balancer.choose(readyInstances);
return client.call(endpoint, request, sessionToken);
// Następne żądanie może trafić do innej instancji.
```

**Konkretny przypadek użycia:** Trzy repliki API

- **Wejście:** Instancja B jest wycofywana podczas sesji klienta.
- **Przebieg:** Balancer wybiera gotowe A lub C, niezbędny stan jest poza pamięcią B.
- **Oczekiwany wynik:** Kolejny request zachowuje poprawne znaczenie sesji.
- **Błąd lub granica:** Repliki API nie zwiększają automatycznie wydajności przeciążonej wspólnej bazy.

**Kiedy zastosować:** Dla poziomego zwiększania liczby instancji i failover. Sama liczba replik API nie usuwa
wąskiego gardła wspólnej bazy.

**Koszt i pułapka:** Stateless API nadal używa stanowych zależności; round-robin może ignorować różnice czasu
żądań.

**Rozwinięcie i ślad wykonania:** Wyłączenie jednej repliki nie powinno usuwać danych potrzebnych kolejnemu
żądaniu. Przenieś konieczny stan do właściciela danych, a lokalne kopie traktuj jako odtwarzalne. Rozdziel
algorytm wyboru instancji od health check i connection draining, bo każdy odpowiada za inny moment obsługi
ruchu.

**Ćwiczenie sprawdzające:** Usuń jedną replikę i sprawdź brak utraty aktywnej sesji.

## Sharding / Horizontal Partitioning

**Intuicja:** Gdy cały zbiór danych lub zapisów jest zbyt duży dla jednego właściciela, można rozdzielić
rekordy na shardy. Klucz partycjonowania decyduje, co pozostaje razem. To pomaga lokalnym operacjom, ale
zapytanie globalne musi połączyć wyniki wielu właścicieli lub korzystać z osobnej projekcji.

**Problem i mechanizm:** Rekordy dzieli się między właścicieli według funkcji partycjonowania i planu
routingu.

**Role i współpraca:** Router wyznacza shard, shard przechowuje jego zakres, mapa własności wspiera relokację.
Transakcja lokalna nie obejmuje automatycznie kilku shardów.

**Przykład:** tenantId wybiera shard z zamówieniami; operacje jednego tenanta pozostają lokalne.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
ShardId shard = shardMap.ownerOf(tenantId);
Orders orders = shards.orders(shard);
return orders.findByTenant(tenantId, cursor);
```

**Konkretny przypadek użycia:** Podział tenantów

- **Wejście:** Dziesiątki milionów zamówień mają tenantId jako typowy zakres query.
- **Przebieg:** Shard map kieruje zapis i odczyt do właściciela zakresu.
- **Oczekiwany wynik:** Operacje jednego tenanta mogą pozostać lokalne.
- **Błąd lub granica:** Jeden ogromny tenant może nadal być hot shard i wymagać dalszego planu podziału.

**Kiedy zastosować:** Dla realnego limitu wielkości lub przepustowości pojedynczego store. Dobierz klucz do
operacji i rozkładu obciążenia, nie tylko równej liczby rekordów.

**Koszt i pułapka:** Cross-shard join, globalna unikalność i przenoszenie danych kosztują; hot tenant może
zdominować shard.

**Rozwinięcie i ślad wykonania:** Shard key powinien lokalizować typowe odczyty i niezmienniki zapisu. Podział
po tenantId upraszcza izolację, ale jeden duży tenant może wymagać dodatkowego podziału. Przy relokacji
wyznacz punkt przełączenia właściciela, żeby stary i nowy shard nie przyjmowały niezależnie zapisów tego
samego zakresu.

**Ćwiczenie sprawdzające:** Zaprojektuj shard key dla ruchu z jednym bardzo dużym tenantem.

## Consistent Hashing / Rendezvous Hashing

**Intuicja:** Proste hash(key) modulo N przemieszcza wiele kluczy po zmianie N. Consistent i Rendezvous
Hashing ograniczają tę zmianę innymi algorytmami: pierścieniem z pozycjami albo rankingiem par key–node.
Rozwiązują wybór właściciela, a nie fizyczne skopiowanie danych i zgodność podczas przełączenia.

**Problem i mechanizm:** Algorytm przypisuje klucze do węzłów, ograniczając zakres przemieszczeń po zmianie
składu.

**Role i współpraca:** Zbiór węzłów i stabilna funkcja wyniku wyznaczają właściciela. Wirtualne węzły lub wagi
mogą pomagać podziałowi, ale wymagają własnego kontraktu.

**Przykład:** Po dodaniu cache D tylko część kluczy zmienia właściciela; węzły wirtualne wyrównują podział
ringa.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
String owner = nodes.stream()
        .max(Comparator.comparing(node -> stableScore(key, node)))
        .orElseThrow();
// Nowy węzeł przejmie klucz tylko wtedy, gdy uzyska lepszy score.
```

**Konkretny przypadek użycia:** Dodanie D do routingu

- **Wejście:** Router zna A,B,C i 1000 kluczy.
- **Przebieg:** Rendezvous liczy stabilny wynik także dla nowego D.
- **Oczekiwany wynik:** Przenoszą się tylko klucze, dla których D staje się zwycięzcą.
- **Błąd lub granica:** Zmiana wyniku routera nie kopiuje jeszcze danych i nie blokuje podwójnego zapisu.

**Kiedy zastosować:** Dla dynamicznego składu cache i shardów. Sprawdź zachowanie hot key oraz migracji, nie
tylko odsetek równomiernie rozłożonych kluczy.

**Koszt i pułapka:** To algorytmy routingu, nie gwarancja trwałości ani spójności danych; ring i rendezvous
mają odmienne implementacje.

**Rozwinięcie i ślad wykonania:** RendezvousRouter nadaje każdej parze key–node stabilny wynik i wybiera
maksimum. Po dodaniu D zmieniają się tylko klucze, dla których D wygrywa; test sprawdza tę własność na wielu
kluczach. Zmiana właściciela w routerze nie przenosi jeszcze danych ani nie rozwiązuje podwójnego zapisu
podczas migracji.

**Ćwiczenie sprawdzające:** Porównaj liczbę przeniesionych kluczy z hash(key) modulo N.

## Read Replica / Leader–Follower Replication

**Intuicja:** Duża liczba odczytów może obciążać lidera przyjmującego zapisy. Read Replica utrzymuje jego
kopię i przejmuje wybrane zapytania. Replikacja potrzebuje czasu, więc po sukcesie zapisu różne repliki mogą
jeszcze pokazywać różne wersje; kontrakt odczytu musi to uwzględniać.

**Problem i mechanizm:** Zapisy prowadzi lider, a repliki obsługują część odczytów po otrzymaniu logu zmian.

**Role i współpraca:** Lider tworzy log zmian, replika stosuje go z własnym postępem, router dobiera źródło
według potrzeb świeżości. Failover ma osobną politykę.

**Przykład:** Raport czyta replikę z 2 s laga; potwierdzenie własnego zakupu idzie do lidera lub czeka na
wersję.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (request.requiresReadYourWrites()) {
    return leader.readAtLeast(id, request.minimumVersion());
}
return replicas.chooseHealthy().read(id);
```

**Konkretny przypadek użycia:** Raport na replice

- **Wejście:** Lider potwierdza wersję 42, replika ma lag 2 s.
- **Przebieg:** Niepilny raport czyta replikę, własny krytyczny odczyt czeka na wersję lub używa lidera.
- **Oczekiwany wynik:** Różne query mają świadomy kontrakt świeżości.
- **Błąd lub granica:** Health check repliki nie dowodzi, że zawiera właśnie zatwierdzony zapis.

**Kiedy zastosować:** Dla odczytów dopuszczających lag. Krytyczny odczyt własnego zapisu wymaga lidera,
minimalnej pozycji logu albo kontrolowanego czekania.

**Koszt i pułapka:** Lag może dawać stare lub cofające się odczyty; failover wymaga polityki utraty i
split-brain.

**Rozwinięcie i ślad wykonania:** Odczyt z repliki może nie widzieć przed chwilą zatwierdzonego zapisu, mimo
poprawnego połączenia i health check. Zapisana wersja albo pozycja logu pozwala określić minimalny postęp.
Przełączenie na lidera dla krytycznego odczytu podnosi koszt, ale może spełnić wymaganie sesji.

**Ćwiczenie sprawdzające:** Odczytaj natychmiast po zapisie i wyjaśnij read-your-writes.

## Multi-leader / Leaderless Replication

**Intuicja:** Kilka regionów może chcieć przyjmować zapisy bez odległego wspólnego lidera. Multi-leader
pozwala kilku liderom, leaderless opiera przyjęcie i odczyt na innym protokole replik. W obu przypadkach
równoczesne zmiany wymagają opisanej reguły wersjonowania, rozstrzygania albo łączenia.

**Problem i mechanizm:** Wiele węzłów przyjmuje zapisy, a protokół rozstrzyga ich łączenie i konflikty.

**Role i współpraca:** Repliki przechowują wersje, protokół określa odpowiedzialność za propagację, konflikt
jest wynikiem danych i ich historii. Reguła rozwiązania zależy od typu operacji.

**Przykład:** Dwa regiony zapisują niezależne aktualizacje dokumentu i później łączą je według reguł wersji.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
region A zapisuje address=A w wersji lokalnej
region B zapisuje address=B bez wiedzy o A
po wymianie:
    wykryj konkurencję
    zastosuj jawną regułę domenową albo zgłoś konflikt
```

**Konkretny przypadek użycia:** Równoczesna edycja regionów

- **Wejście:** A i B przyjmują różne adresy tego samego profilu podczas rozłączenia.
- **Przebieg:** Protokół wykrywa wersje i stosuje uzgodnioną regułę konfliktu.
- **Oczekiwany wynik:** Wiadomo, co znaczy wynik po połączeniu regionów.
- **Błąd lub granica:** Last-write-wins może zgubić zmianę, a zegar fizyczny nie jest pełnym porządkiem
  przyczynowym.

**Kiedy zastosować:** Dla danych, które dopuszczają taki model dostępności i konfliktu. Ścisły niezmiennik
wspólnego zasobu wymaga dodatkowego rozwiązania, nie samego uruchomienia zapisu w dwóch regionach.

**Koszt i pułapka:** Last-write-wins może gubić dane, a zegary nie oddają porządku przyczynowego; silne
niezmienniki wymagają dodatkowej koordynacji.

**Rozwinięcie i ślad wykonania:** Przyjmowanie zapisów w kilku miejscach wymaga reguły konfliktu dla
konkretnego typu danych. Dwie aktualizacje adresu mogą wymagać wyboru jednej wersji, licznik może mieć CRDT, a
ścisła liczba biletów koordynacji. Nie stosuj jednej ogólnej reguły last-write-wins do wszystkich
niezmienników biznesowych.

**Ćwiczenie sprawdzające:** Zapisz dwie równoczesne zmiany jednego pola i wybierz jawną politykę konfliktu.

## Partitioned Log / Partition per Key

**Intuicja:** Jedna globalna sekwencja ogranicza równoległość. Partitioned Log dzieli strumień na niezależne
ciągi, zachowując porządek w każdej partycji według kontraktu transportu. Dobór klucza decyduje, które
zdarzenia muszą pozostać razem i jaki gorący zakres nie da się dalej zrównoleglić bez zmiany reguł.

**Problem i mechanizm:** Uporządkowany log dzieli się na partycje tak, by operacje wymagające wspólnego
porządku trafiały do tej samej.

**Role i współpraca:** Klucz wybiera partycję, log przechowuje kolejność, konsument przetwarza przydział.
Zmiana liczby partycji może zmienić mapowanie klucza.

**Przykład:** accountId wyznacza partycję przelewów; różne konta przetwarza się równolegle.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
publisher.send("account-events", event.accountId(), event);
// Ten sam klucz kieruje powiązane fakty do ustalonego zakresu porządku.
```

**Konkretny przypadek użycia:** Log zdarzeń kont

- **Wejście:** AccountId kieruje zdarzenia na jedną z 3 partycji.
- **Przebieg:** Konsumenci przetwarzają niezależne partycje równolegle.
- **Oczekiwany wynik:** Powiązane fakty konta mają uzgodniony zakres porządku.
- **Błąd lub granica:** Zmiana liczby partycji wymaga planu historii klucza między starym i nowym
  właścicielem.

**Kiedy zastosować:** Gdy operacje różnych kont mogą działać niezależnie. Nie obiecuj globalnego porządku na
podstawie lokalnej kolejności w jednej partycji.

**Koszt i pułapka:** Porządek w partycji nie jest globalnym porządkiem; zmiana liczby partycji wymaga planu
zachowania kluczy.

**Rozwinięcie i ślad wykonania:** Porządek jest gwarantowany dla kluczy trafiających do tej samej partycji
zgodnie z kontraktem transportu. Zmiana liczby partycji może przenieść klucz i rozdzielić jego historię
pomiędzy starego oraz nowego właściciela. Plan migracji musi uwzględniać wiadomości będące jeszcze w locie.

**Ćwiczenie sprawdzające:** Wskaż skutek losowego partycjonowania eventów jednego zamówienia.

## Fan-out on Write / Fan-out on Read / Hybrid

**Intuicja:** Feed można przygotować z góry dla obserwujących albo składać dopiero przy odczycie. Fan-out on
Write płaci za każdego adresata podczas publikacji, Fan-out on Read za aktywny odczyt. Hybrid wybiera różne
warianty dla różnych klas autorów, aby ograniczyć koszt celebrytów i zwykłych kont.

**Problem i mechanizm:** Widok wielu odbiorców powstaje przy publikacji, przy odczycie albo w modelu
mieszanym.

**Role i współpraca:** Autor tworzy post, dystrybutor zasila inboxy lub query łączy źródła. Deduplikacja usuwa
nakładanie ścieżek podczas zmian polityki.

**Przykład:** Zwykłe posty trafiają do inboxów obserwujących, a posty celebryty są dołączane przy odczycie
feedu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (author.followerCount() < fanoutThreshold) {
    feedInboxes.appendToFollowers(post);
} else {
    celebrityStream.append(post);
}
// Odczyt łączy inbox z odpowiednimi źródłami read-time.
```

**Konkretny przypadek użycia:** Feed zwykłego autora i celebryty

- **Wejście:** Autor ma 100 obserwujących albo milion.
- **Przebieg:** Mały fan-out zapisuje inboxy, duży może być dołączany przy odczycie.
- **Oczekiwany wynik:** Koszt publikacji i odczytu jest dobrany do rozkładu odbiorców.
- **Błąd lub granica:** Zmiana klasyfikacji wymaga deduplikacji nakładających się ścieżek.

**Kiedy zastosować:** Dla systemów dystrybucji treści z różnymi proporcjami czytelników i publikacji. Oblicz
koszt rzeczywistego fan-out, zanim wybierzesz próg wariantu mieszanego.

**Koszt i pułapka:** Fan-out on write mnoży zapisy; fan-out on read zwiększa koszt odczytu i zależność od
dostępności źródeł.

**Rozwinięcie i ślad wykonania:** Fan-out on write płaci koszt przy publikacji, nawet jeśli wielu odbiorców
nie odczyta feedu. Fan-out on read przesuwa koszt na aktywnych czytelników. Wariant mieszany powinien określić
próg dużego autora i deduplikację, gdy post mógł trafić do obu ścieżek podczas zmiany klasyfikacji.

**Ćwiczenie sprawdzające:** Policz zapisy dla autora z milionem obserwujących i tysiącem czytelników dziennie.

## MapReduce / Scatter–Reduce

**Intuicja:** Duży zbiór można rozbić na części, obliczyć lokalne wyniki i połączyć je według klucza.
MapReduce porządkuje te fazy, w tym kosztowne grupowanie danych pomiędzy map a reduce. Powtórzenie zadania po
awarii wymaga powtarzalnej kalkulacji i kontrolowanych skutków ubocznych.

**Problem i mechanizm:** Zadanie dzieli się na lokalne mapowanie, grupowanie po kluczu i łączenie wyników.

**Role i współpraca:** Map emituje pary, shuffle grupuje klucze, reduce agreguje. Lokalny combiner może
ograniczyć transfer, jeśli operacja zachowuje potrzebne własności.

**Przykład:** Map emituje (productId,1) dla sprzedaży, shuffle grupuje klucze, reduce sumuje liczniki.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
map(order): dla pozycji emituj (sku, quantity)
shuffle: zbierz wszystkie wartości tego samego sku
reduce(sku, quantities): emituj (sku, suma)

Powtórzenie map nie może samo naliczać nieodwracalnych opłat.
```

**Konkretny przypadek użycia:** Agregacja sprzedaży po SKU

- **Wejście:** Miliony pozycji mają być policzone per produkt.
- **Przebieg:** Map emituje pary, shuffle grupuje, reduce sumuje, combiner może ograniczyć transfer.
- **Oczekiwany wynik:** Wynik jest niezależny od podziału na legalne porcje.
- **Błąd lub granica:** Skew jednego SKU może przeciążyć reduce mimo równych plików wejściowych.

**Kiedy zastosować:** Dla dużych, podzielnych obliczeń. Skew jednego klucza albo intensywne shuffle mogą
zdominować koszt mimo wielu workerów.

**Koszt i pułapka:** Skew kluczy, retry zadań i duże shuffle wpływają na koszt; skutki uboczne map nie są
automatycznie jednokrotne.

**Rozwinięcie i ślad wykonania:** Mapowanie powinno być powtarzalne, bo worker może zostać uruchomiony
ponownie. Lokalne częściowe agregacje redukują transfer przed shuffle, jeśli operacja łączenia na to pozwala.
Testuj skew jednego popularnego klucza; równomierna liczba plików wejściowych nie oznacza równomiernego kosztu
reduce.

**Ćwiczenie sprawdzające:** Wskaż hot key i pokaż lokalne częściowe agregowanie przed shuffle.

## Batch / Stream Processing / Lambda / Kappa

**Intuicja:** Batch obrabia skończone porcje, stream dane napływające z czasem. Lambda łączy osobną ścieżkę
batch i szybką, Kappa stawia na przetwarzanie oraz replay jednego logu. Wybór dotyczy sposobu odtwarzania i
aktualności, a nie automatycznej wyższości nowszej nazwy.

**Problem i mechanizm:** Przetwarzanie pełnych porcji lub bieżącego logu dobiera się do opóźnienia i
odtwarzalności.

**Role i współpraca:** Źródło prawdy zasila obliczenie, checkpoint wyznacza postęp, wynik ma kontrakt czasu.
Przy dwóch ścieżkach trzeba zapewnić zgodną semantykę ich łączenia.

**Przykład:** Kappa odbudowuje widok przez replay logu; Lambda ma osobną ścieżkę batch i speed layer z regułą
łączenia.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
batch: snapshot całego dnia -> obliczenie -> wynik dnia
stream: log -> aktualizowana projekcja
Lambda: batch + speed layer z regułą połączenia
Kappa: jedna ścieżka logu, odtwarzana także podczas rebuild
```

**Konkretny przypadek użycia:** Przebudowa analityki

- **Wejście:** Potrzebny jest świeży wynik i odtworzenie ostatniego miesiąca.
- **Przebieg:** Warianty Batch/Stream oraz Lambda/Kappa wybierają ścieżkę obliczeń i replay.
- **Oczekiwany wynik:** Źródło prawdy oraz sposób rebuild są jawne.
- **Błąd lub granica:** Dwie ścieżki Lambda mogą mieć inną semantykę, a zbyt krótki log Kappa nie umożliwi
  replay.

**Kiedy zastosować:** Według wymaganego latency i możliwości odbudowy. Krótka retencja logu może uniemożliwić
pełny replay, nawet jeśli pipeline nazywa się Kappa.

**Koszt i pułapka:** Dwie ścieżki Lambda mogą rozjechać semantykę; sam streaming nie daje poprawności dla
spóźnionych danych.

**Rozwinięcie i ślad wykonania:** Najpierw określ źródło prawdy i możliwość odtworzenia. Kappa wymaga
wystarczająco długiej historii albo snapshotów startowych, Lambda zgodnego wyniku obu ścieżek. Niezależnie od
nazwy trzeba rozstrzygnąć duplikaty, spóźnione dane oraz granicę momentu, dla którego raport jest kompletny.

**Ćwiczenie sprawdzające:** Zdefiniuj jedno źródło prawdy oraz sposób odtworzenia miesiąca danych.

## Windowing / Watermark / Late Data Handling

**Intuicja:** Zdarzenie o sprzedaży o 12:03 może przyjść dopiero o 12:10. Okna według czasu zdarzenia muszą
rozstrzygnąć, jak długo czekać i co zrobić z jeszcze późniejszymi danymi. Watermark opisuje zakładany postęp
strumienia; nie jest dowodem, że starszy event już nigdy nie nadejdzie.

**Problem i mechanizm:** Zdarzenia grupuje się w okna według czasu zdarzenia z jawną tolerancją spóźnienia.

**Role i współpraca:** Event time określa okno, watermark pozwala je domykać, allowed lateness i korekty
określają wynik po terminie. Processing time jest innym zegarem.

**Przykład:** Sprzedaż 12:00–12:05 domyka się po watermark 12:07; późniejszy event idzie do korekty lub side
output.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
okno: [12:00, 12:05)
eventTime 12:03 -> należy do tego okna
watermark 12:07 -> finalizacja według polityki
event z 12:02 po finalizacji -> korekta albo late-events
```

**Konkretny przypadek użycia:** Spóźniona sprzedaż

- **Wejście:** Event time 12:03 przychodzi o 12:10 dla okna 12:00–12:05.
- **Przebieg:** Watermark i polityka late data określają finalizację oraz korektę.
- **Oczekiwany wynik:** Wynik raportu ma zrozumiałe znaczenie kompletności.
- **Błąd lub granica:** Watermark nie dowodzi, że starszy event już nigdy nie nadejdzie.

**Kiedy zastosować:** Dla raportowania i agregacji strumieni. Jawna korekta lub osobne wyjście są lepsze niż
przypadkowe zgubienie spóźnionego eventu.

**Koszt i pułapka:** Watermark jest założeniem o postępie danych, nie dowodem, że nic starszego nie nadejdzie.

**Rozwinięcie i ślad wykonania:** Czas przyjścia i czas zdarzenia są różne. Event z 12:03 może nadejść o 12:10
po zamknięciu okna. Wybierz korektę, osobne wyjście lub kontrolowane pominięcie i zachowaj tę decyzję w
kontrakcie wyniku; watermark nie może sprawić, że późne dane przestaną istnieć.

**Ćwiczenie sprawdzające:** Dostarcz zdarzenie sprzed 10 minut i określ korektę raportu.

## Index Table / Inverted Index

**Intuicja:** Główny klucz danych nie zawsze odpowiada pytaniu użytkownika. Index Table lub Inverted Index
tworzy odwzorowanie od innej cechy do identyfikatorów. Wyszukiwarka znajduje więc dokumenty po tokenie, a nie
skanuje wszystkich treści; indeks musi być utrzymywany i odbudowywany jako kopia pochodna.

**Problem i mechanizm:** Osobny indeks odwzorowuje alternatywny klucz wyszukiwania na identyfikatory danych.

**Role i współpraca:** Indeks przechowuje token–IDs, store źródłowy dokumenty, aktualizator propaguje zmiany i
usunięcia. Czytelnik może potrzebować kontroli aktualnej wersji.

**Przykład:** token "java" wskazuje listę documentId; właściwe dokumenty pobiera się z podstawowego store.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Set<DocumentId> candidates = invertedIndex.find("java");
List<Document> visible = documents.loadVisible(candidates, tenantId);
// Kontrola widoczności nie może polegać wyłącznie na starym indeksie.
```

**Konkretny przypadek użycia:** Wyszukiwanie tokenu java

- **Wejście:** Indeks wskazuje dokumenty d1,d2, ale d2 zostało usunięte.
- **Przebieg:** Query bierze kandydatów i respektuje aktualną widoczność źródła.
- **Oczekiwany wynik:** Usunięty lub cudzy dokument nie jest ujawniony przez stary wpis.
- **Błąd lub granica:** Indeks jest kopią z lagiem, nie samodzielnym źródłem uprawnień.

**Kiedy zastosować:** Dla alternatywnych ścieżek wyszukiwania. Koszt szybkiego odczytu obejmuje write
amplification oraz opóźnienie aktualizacji indeksu.

**Koszt i pułapka:** Utrzymanie indeksu zwiększa write amplification i może mieć lag; usunięcia wymagają
propagacji.

**Rozwinięcie i ślad wykonania:** Indeks jest kopią pochodną: dokument i jego wpis tokenowy mogą chwilowo się
rozjechać. Przy odczycie można dodatkowo sprawdzić status dokumentu albo wersję, zwłaszcza po usunięciu.
Odbudowa indeksu powinna mieć checkpoint i sposób przejęcia zmian zachodzących podczas pełnego skanu.

**Ćwiczenie sprawdzające:** Usuń dokument i sprawdź brak zwracania go przez stary wpis indeksu.

## CDN / Edge Caching

**Intuicja:** Treść pobierana daleko od źródła ma koszt sieci i obciąża centralny serwer. CDN utrzymuje kopie
bliżej odbiorców. Poprawność zależy od klucza i polityki cache: dwie różne odpowiedzi użytkowników nie mogą
nieświadomie trafić pod ten sam publiczny klucz.

**Problem i mechanizm:** Kopie treści są dostarczane blisko odbiorcy według klucza cache, TTL i reguł
autoryzacji.

**Role i współpraca:** Origin jest źródłem, edge kopią, cache key wybiera wariant, TTL i invalidacja sterują
wiekiem. Hash w nazwie statycznego pliku pozwala publikować nową wersję pod nowym URL.

**Przykład:** Statyczne pliki mają nazwę z hashem i długi TTL; HTML wskazuje aktualny hash wersji.

**Mechanizm w kodzie:** Przykładowy kontrakt HTTP: pokazuje żądanie i odpowiedź z objaśnionymi danymi. W
module nie uruchamiamy tego endpointu.

```http
GET /assets/app.8f31c2.js HTTP/1.1

HTTP/1.1 200 OK
Cache-Control: public, max-age=31536000, immutable
Content-Type: application/javascript
```

**Konkretny przypadek użycia:** Statyczny bundle aplikacji

- **Wejście:** Plik app.8f31.js jest identyfikowany treścią.
- **Przebieg:** CDN przechowuje go długo, nowa treść dostaje nowy URL.
- **Oczekiwany wynik:** Klienci pobierają bliską kopię bez natychmiastowej invalidacji starego pliku.
- **Błąd lub granica:** Prywatnego API nie można cacheować tak samo pod wspólnym kluczem.

**Kiedy zastosować:** Dla publicznych, często odczytywanych zasobów i kontrolowanych wariantów. Prywatne dane
wymagają świadomego zakresu i autoryzacji, nie kopiowania ustawień obrazków na całe API.

**Koszt i pułapka:** Prywatne dane i błędny cache key mogą ujawnić cudze odpowiedzi; invalidacja nie zawsze
jest natychmiastowa.

**Rozwinięcie i ślad wykonania:** Klucz cache musi odróżniać wszystkie warianty wpływające na odpowiedź, w tym
język i uzasadniony zakres użytkownika. Plik z hashem w nazwie może mieć długi TTL, bo nowa treść dostaje nowy
adres. Dynamiczne prywatne odpowiedzi wymagają innej polityki niż publiczne zasoby statyczne.

**Ćwiczenie sprawdzające:** Zaprojektuj cache key zawierający wariant języka i właściwy zakres prywatności.

## Data Locality / Colocation

**Intuicja:** Jeżeli jedno obliczenie potrzebuje dziesięciu zdalnych odczytów, koszt komunikacji może
przewyższyć sam algorytm. Data Locality umieszcza powiązane dane i obliczenie blisko siebie. Kolokacja powinna
odpowiadać typowym operacjom, bo lokalność jednego zapytania może utrudnić inne globalne odczyty.

**Problem i mechanizm:** Obliczenia i powiązane dane są umieszczane blisko siebie, zmniejszając transfer i
liczbę zdalnych operacji.

**Role i współpraca:** Właściciel zakresu przechowuje potrzebny podzbiór, operacja wykonuje się lokalnie,
wynik przekracza sieć zamiast całej surowej historii.

**Przykład:** Wszystkie pozycje zamówienia są na shardzie orderId, więc obliczenie sumy jest lokalne.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Shard shard = shards.forOrder(orderId);
Money total = shard.orders().calculateTotalLocally(orderId);
// Pozycje nie są pobierane osobnym zdalnym requestem każda.
```

**Konkretny przypadek użycia:** Lokalne podsumowanie zamówienia

- **Wejście:** Suma wymaga wszystkich 20 pozycji jednego orderId.
- **Przebieg:** Pozycje są przy tym samym właścicielu, obliczenie wykonuje się lokalnie.
- **Oczekiwany wynik:** Do klienta wraca wynik zamiast 20 zdalnych odczytów.
- **Błąd lub granica:** Globalny raport może potrzebować innego modelu niż lokalność pojedynczego zamówienia.

**Kiedy zastosować:** Gdy transfer i round-tripy są wąskim gardłem. Oceń też przenoszenie danych, równoważenie
i koszt większej jednostki własności.

**Koszt i pułapka:** Denormalizacja i relokacja mogą podnieść koszt zapisu i utrudnić równoważenie.

**Rozwinięcie i ślad wykonania:** Przeniesienie obliczenia do właściciela danych zmniejsza liczbę zdalnych
odczytów, ale może utrudnić rozkład obciążenia. Dla jednego zamówienia kolokacja pozycji zwykle pomaga, dla
globalnego raportu potrzebny jest inny model. Oceń lokalność pod rzeczywiste operacje, nie samą podobną nazwę
rekordów.

**Ćwiczenie sprawdzające:** Porównaj lokalny odczyt zamówienia z dziesięcioma wywołaniami do shardów.

## Hot-key Mitigation / Salting / Hierarchical Aggregation

**Intuicja:** Dobry rozkład milionów kluczy nie chroni przed jednym bardzo popularnym filmem. Hot-key
Mitigation rozbija jego pracę na części, przenosi odczyt do kopii lub agreguje hierarchicznie. Trzeba zachować
sens operacji: przyrosty licznika łatwo zsumować, ale ścisłego limitu zasobu nie można dowolnie powielić.

**Problem i mechanizm:** Popularny klucz dzieli się na częściowe klucze lub buforuje bliżej odbiorcy z
późniejszym złożeniem wyniku.

**Role i współpraca:** Salt wybiera część, częściowy store przyjmuje zmianę, agregator składa wynik. Kontrakt
określa aktualność i ewentualny budżet praw per część.

**Przykład:** Licznik odsłon popularnego filmu ma 16 shardów licznikowych, a odczyt sumuje wartości.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
int part = random.nextInt(16);
counters.increment(videoId + ":" + part);
// Odczyt sumuje 16 części; nie tworzymy 16 niezależnych limitów biletów.
```

**Konkretny przypadek użycia:** Popularny film

- **Wejście:** Jeden videoId ma 80% aktualizacji licznika.
- **Przebieg:** Przyrosty dzielą się na 16 części, odczyt sumuje wynik.
- **Oczekiwany wynik:** Zmniejsza się contention jednego klucza.
- **Błąd lub granica:** Ten sam trik bez podziału praw nie zapewni ścisłego limitu 100 biletów.

**Kiedy zastosować:** Dla nierównego rozkładu ruchu. Najpierw rozpoznaj hot key, bo dodanie kolejnych zwykłych
shardów może go wcale nie rozdzielić.

**Koszt i pułapka:** Salting psuje prosty pojedynczy odczyt i może utrudnić ścisły limit globalny.

**Rozwinięcie i ślad wykonania:** Solenie licznika rozprasza aktualizacje, a odczyt sumuje częściowe wyniki.
Jeśli jednak limit 100 biletów jest ścisły, niezależne lokalne limity muszą razem zachować przydzielony
budżet. Losowy shard bez podziału praw rezerwacji może dopuścić więcej niż 100 sprzedaży.

**Ćwiczenie sprawdzające:** Wyjaśnij, dlaczego ten sam podział nie wystarcza do ścisłego limitu 100 biletów.

## Geode / Active–Active Regions / Geo-routing

**Intuicja:** Ruch może być obsługiwany w wielu regionach blisko użytkownika, lecz dane wymagają polityki
własności. Active–Active opisuje aktywne lokalizacje obsługi, nie automatycznie konfliktowo bezpieczny zapis
każdego rekordu. Geo-routing i replikacja muszą wspólnie określić zachowanie podczas odcięcia regionu.

**Problem i mechanizm:** Wiele regionów obsługuje ruch z lokalnymi kopiami danych i określoną polityką
własności.

**Role i współpraca:** Router wybiera lokalizację, region obsługuje ruch, protokół danych wyznacza autora i
propagację. Plan failover obejmuje również powrót starej lokalizacji.

**Przykład:** Użytkownik jest kierowany do najbliższego regionu, a krytyczny zapis zamówienia nadal ma
wyznaczonego właściciela.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
klient -> najbliższy region dla odczytu
krytyczny zapis -> wyznaczony właściciel zasobu
replikacja -> pozostałe regiony według kontraktu
failover -> uzgodnienie nowego właściciela i blokada starego
```

**Konkretny przypadek użycia:** Awaria regionu

- **Wejście:** Region A przyjął zapis, ale B nie otrzymało go przed utratą A.
- **Przebieg:** Plan geo-routing i failover określa nowego właściciela oraz dostępne dane.
- **Oczekiwany wynik:** RPO i prawo zapisu są jawne.
- **Błąd lub granica:** Dwa aktywne regiony same nie zapewniają RPO=0 ani braku split-brain po powrocie A.

**Kiedy zastosować:** Dla wymagań geograficznych i recovery uzasadniających koszt. Nie wywodź RPO=0 z samej
obecności dwóch działających regionów.

**Koszt i pułapka:** Opóźnienie replikacji i failover mogą powodować konflikty; active-active nie zapewnia
samoczynnie RPO=0.

**Rozwinięcie i ślad wykonania:** Najbliższy region nie musi być właścicielem zapisu konkretnego zasobu.
Oddziel routing od semantyki replikacji i rozwiązywania konfliktów. Failover powinien wskazywać, czy
potwierdzony, lecz jeszcze niezreplikowany zapis może zginąć oraz kto blokuje starego właściciela po jego
powrocie.

**Ćwiczenie sprawdzające:** Zaprojektuj utratę regionu między przyjęciem zapisu a replikacją.

## Autoscaling / Scheduled Scaling / Target Tracking

**Intuicja:** Zapotrzebowanie zmienia się w czasie, więc stała liczba instancji bywa droga albo zbyt mała.
Autoscaling dostosowuje zasoby według mierzonego celu lub planu. Musi uwzględniać czas startu, limit
zależności i to, czy wybrana metryka naprawdę opisuje niewykonaną pracę.

**Problem i mechanizm:** Liczba zasobów dostosowuje się do metryk i prognoz przy ograniczeniach czasu startu.

**Role i współpraca:** Metryka opisuje obciążenie, kontroler wylicza liczbę instancji, platforma realizuje
zmianę, limity blokują przekroczenie bezpiecznej pojemności downstreamu.

**Przykład:** Workerów przybywa według wieku najstarszej wiadomości i bezpiecznego limitu połączeń DB.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
cel: wiek najstarszego zadania < 5 s
gdy wiek rośnie: rozważ dodatkowe workery
limit: liczba workerów * połączenia per worker <= budżet DB
recovery: zmniejszaj stopniowo z kontrolą backlogu
```

**Konkretny przypadek użycia:** Skalowanie workerów backlogu

- **Wejście:** Wiek najstarszego zadania rośnie, DB dopuszcza 200 połączeń.
- **Przebieg:** Kontroler ocenia metrykę i limit połączeń także dla surge.
- **Oczekiwany wynik:** Dodatkowe workery nie przekraczają bezpiecznej pojemności downstreamu.
- **Błąd lub granica:** Samo CPU może nie opisywać kolejki, a startup opóźnia realny wzrost wydajności.

**Kiedy zastosować:** Dla zmiennego workloadu o znanym bottleneck. Więcej workerów nie pomoże, jeśli jedynym
ograniczeniem jest już przeciążona baza.

**Koszt i pułapka:** Więcej replik może przeciążyć bazę; CPU nie zawsze opisuje backlog i opóźnienie.

**Rozwinięcie i ślad wykonania:** Nowa replika zwiększa równoległość dopiero po gotowości i podłączeniu
zasobów. Jeśli każda tworzy pięć połączeń DB, limit 200 wyznacza twardą granicę także dla surge podczas
rollout. Metryka backlogu powinna uwzględniać wiek pracy i wydajność konsumentów, nie wyłącznie długość
kolejki.

**Ćwiczenie sprawdzające:** Wylicz limit workerów przy puli 200 połączeń i 5 połączeniach per worker.

## Multi-tenancy / Pool–Silo–Bridge

**Intuicja:** Wielu klientów może współdzielić aplikację, lecz nie prawo do cudzych danych. Multi-tenancy
wybiera stopień izolacji: wspólne tabele, osobne schematy, bazy lub całe komórki. Pool, Silo i Bridge
porządkują te warianty; każdy ma inny koszt operacji i ryzyko noisy neighbor.

**Problem i mechanizm:** Dane i zasoby tenantów są współdzielone albo izolowane na poziomie tabel, schematów,
baz lub komórek.

**Role i współpraca:** Zweryfikowany tenant wybiera zakres kluczy i zasobów. Baza, cache, kolejka i procesy w
tle muszą zachować tę samą granicę.

**Przykład:** Małe tenanty współdzielą pool z tenantId, duży klient ma osobną bazę i limit zasobów.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
record DocumentKey(String tenantId, String documentId) { }
DocumentKey key = new DocumentKey(verifiedTenant, documentId);
return store.load(key);
// Sam documentId nie jest pełnym kluczem dostępu.
```

**Konkretny przypadek użycia:** Izolacja klientów

- **Wejście:** Tenant A i B używają tego samego documentId d1.
- **Przebieg:** Klucz DB, cache i wiadomości obejmuje zweryfikowany tenant.
- **Oczekiwany wynik:** Oba d1 są odrębne i mają odrębny zakres dostępu.
- **Błąd lub granica:** Pominięcie tenanta w jednej ścieżce cache może obejść poprawny filtr kontrolera.

**Kiedy zastosować:** Gdy jeden produkt obsługuje wiele organizacji. Filtr w kontrolerze nie wystarcza, jeżeli
boczna ścieżka odczytu lub cache pomija tenantId.

**Koszt i pułapka:** Każde zapytanie i cache key muszą zachować zakres tenanta; izolacja logiczna nie usuwa
noisy neighbor.

**Rozwinięcie i ślad wykonania:** Scope tenanta musi przechodzić przez klucz bazy, cache, komunikat i wynik
odczytu. Test ConditionalDocuments używa tego samego id d1 w dwóch tenantach i sprawdza niezależne wartości.
Sam filtr na wejściu HTTP nie ochroni bocznej ścieżki konsumenta albo zadania okresowego.

**Ćwiczenie sprawdzające:** Wskaż wszystkie miejsca propagacji tenantId od HTTP do kolejki i cache.
