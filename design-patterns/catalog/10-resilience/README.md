# Odporność, przeciążenie i współbieżność

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#resilience)

Wzorce sterowania przepływem i awarią. **16 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [retry z budżetem, backoff i jitter](../../labs/03-resilience-and-cache.md).
Studium w Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Odporność zaczyna się od określenia, co ma zostać ograniczone: czas, tempo,
współbieżność, liczba dodatkowych prób czy pamięć kolejki. Te wzorce uzupełniają się, ale nie są zamienne.
Timeout nie zatrzymuje automatycznie skutku, Circuit Breaker nie ogranicza liczby równoległych wywołań, a
kolejka nie dodaje zdolności wykonania. Czytaj szkice jako współpracujące polityki jednego budżetu i
właściciela pracy.

## Spis kart

- [Timeout / Deadline Propagation](#timeout--deadline-propagation)
- [Retry / Exponential Backoff / Jitter](#retry--exponential-backoff--jitter)
- [Circuit Breaker](#circuit-breaker)
- [Bulkhead / Concurrency Limit](#bulkhead--concurrency-limit)
-
  [Rate Limiter / Token Bucket / Leaky Bucket / Sliding Window](#rate-limiter--token-bucket--leaky-bucket--sliding-window)
- [Backpressure / Demand-driven Flow](#backpressure--demand-driven-flow)
-
  [Load Shedding / Admission Control / Queue-based Load Leveling](#load-shedding--admission-control--queue-based-load-leveling)
-
  [Fallback / Graceful Degradation / Static Content Hosting](#fallback--graceful-degradation--static-content-hosting)
- [Hedged Request / Speculative Execution](#hedged-request--speculative-execution)
- [Single Flight / Request Coalescing](#single-flight--request-coalescing)
- [Cache Stampede Prevention / TTL Jitter](#cache-stampede-prevention--ttl-jitter)
- [Cooperative Cancellation / Interruptible Task](#cooperative-cancellation--interruptible-task)
- [Leader–Followers / Half-Sync Half-Async](#leaderfollowers--half-sync-half-async)
- [Reactor / Proactor](#reactor--proactor)
- [Retry Budget / Adaptive Concurrency](#retry-budget--adaptive-concurrency)
- [Priority Queue / Fair Scheduling / Aging](#priority-queue--fair-scheduling--aging)

## Timeout / Deadline Propagation

**Intuicja:** Bez granicy czasu jeden request może trzymać wątek lub połączenie bez końca. Timeout ogranicza
konkretne oczekiwanie, deadline cały proces. Po każdym etapie pozostaje mniej czasu, więc kolejny hop nie
powinien zaczynać nowego pełnego budżetu, ignorując już zużyte opóźnienie.

**Problem i mechanizm:** Każdy etap ma ograniczony czas, a kolejne hop-y dzielą jeden malejący budżet całej
operacji.

**Role i współpraca:** Właściciel requestu ustala budżet, kolejne operacje dostają jego pozostałą część.
Klient I/O musi stosować własne ograniczenia połączenia, odczytu i puli.

**Przykład:** Request ma 500 ms; po 150 ms odczytu zostaje maksymalnie 350 ms na downstream, a nie nowe 500
ms.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Deadline deadline = Deadline.after(Duration.ofMillis(500), monotonicClock);
Customer customer = customers.load(id, deadline.remaining());
Quote quote = prices.quote(customer, deadline.remaining());
return payments.authorize(quote, deadline.remaining());
```

**Konkretny przypadek użycia:** Budżet requestu 500 ms

- **Wejście:** Pierwszy odczyt zużywa 150 ms.
- **Przebieg:** Kolejny hop dostaje najwyżej pozostałe 350 ms i własne limity I/O.
- **Oczekiwany wynik:** Cały proces nie resetuje czasu na każdym etapie.
- **Błąd lub granica:** Timeout płatności oznacza nieznany wynik, nie automatyczny brak obciążenia.

**Kiedy zastosować:** Dla każdej zależności o niepewnym czasie wykonania. Odróżnij koniec czekania od
anulowania i od informacji, czy zewnętrzny efekt nastąpił.

**Koszt i pułapka:** Timeout nie dowodzi braku skutku ubocznego i nie gwarantuje przerwania pracy; potrzebna
idempotencja i anulowanie kooperatywne.

**Rozwinięcie i ślad wykonania:** Deadline całej operacji maleje wraz z czasem zużytym przez wcześniejsze
kroki. Osobny timeout połączenia, odczytu i oczekiwania na pulę nadal jest potrzebny, ale nie może stale
odtwarzać całego budżetu. Wynik „przestałem czekać” nie rozstrzyga, czy zewnętrzna płatność została wykonana.

**Ćwiczenie sprawdzające:** Policz budżet trzech hopów i odpowiedz na timeout po pobraniu płatności.

## Retry / Exponential Backoff / Jitter

**Intuicja:** Chwilowa awaria może zniknąć przed kolejną próbą, lecz natychmiastowe ponowienia zwiększają
obciążenie właśnie wtedy, kiedy system ma problem. Retry potrzebuje klasyfikacji, limitu prób i czasu. Backoff
rozsuwa próby, a jitter zapobiega jednoczesnemu powrotowi wszystkich klientów w tej samej chwili.

**Problem i mechanizm:** Ponawia się tylko przejściowe i bezpieczne operacje w ograniczonej liczbie prób oraz
czasie.

**Role i współpraca:** Polityka określa błędy ponawialne, zegar budżet, scheduler oczekiwanie, funkcja
losująca rozproszenie. Operacja musi być bezpieczna do ponowienia albo mieć klucz idempotencji.

**Przykład:** Demo retry kończy po trzech błędach i nie ponawia walidacji; wariant sieciowy dodaje full jitter
w ograniczonym wykładniczo oknie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
for (int attempt = 1; attempt <= maxAttempts; attempt++) {
    deadline.requireRemaining();
    try { return client.call(idempotencyKey, deadline.remaining()); }
    catch (TransientFailure error) {
        if (attempt == maxAttempts) throw error;
        sleeper.waitFor(jitter(backoffFor(attempt)), deadline);
    }
}
```

**Konkretny przypadek użycia:** Cztery próby z backoff

- **Wejście:** Początkowy delay 10, cap 25, wspólny budżet 100 jednostek.
- **Przebieg:** Próby mogą czekać 10,20,25 z wstrzykniętym jitter i kontrolą pozostałego czasu.
- **Oczekiwany wynik:** Czas wywołań i snu zużywa jeden budżet.
- **Błąd lub granica:** Nieponawialny błąd kończy od razu; opóźnienie ponad deadline blokuje kolejną próbę.

**Kiedy zastosować:** Dla przejściowych awarii z przewidywalnym sposobem powtórzenia. Błąd walidacji i
nieznany wynik nieidempotentnej płatności wymagają innej decyzji niż ślepy retry.

**Koszt i pułapka:** Retry mnoży obciążenie i duplikaty; kod lokalny celowo nie implementuje czasu, backoff
ani jitter. Nie stosuj ciasnej pętli do sieci.

**Rozwinięcie i ślad wykonania:** RetryExecutor ma maksymalną liczbę prób, klasyfikator, ograniczony
exponential backoff i wstrzyknięty jitter. Czas każdej próby oraz snu odejmuje się od jednego budżetu. Test z
nadmiernym opóźnieniem schedulera dowodzi, że kolejna próba nie rusza po deadline, mimo zaplanowanego
wcześniej krótszego snu.

**Ćwiczenie sprawdzające:** Rozdziel 400, 429, 503 i timeout po zapisie; zaprojektuj budżet retry.

## Circuit Breaker

**Intuicja:** Jeśli partner przez dłuższy czas odrzuca każde wywołanie, ciągłe próby marnują zasoby i mogą
opóźniać jego recovery. Circuit Breaker czasowo odcina te wywołania na podstawie obserwowanej historii.
Później dopuszcza kontrolną próbę, aby sprawdzić, czy zależność znów działa.

**Problem i mechanizm:** Po serii awarii wywołania są odrzucane, po cooldown dopuszcza się ograniczoną próbę
odzyskania.

**Role i współpraca:** CLOSED normalnie przepuszcza, OPEN odrzuca, HALF_OPEN ogranicza próby odzyskania.
Klasyfikacja określa, które błędy świadczą o stanie zależności.

**Przykład:** Dwa błędy otwierają breaker, przed 10 jednostkami próby nie trafiają do celu; sukces HALF_OPEN
zamyka obwód.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (breaker.isOpenAndCoolingDown(now)) {
    throw new DependencyUnavailable();
}
try {
    Result result = client.call(request);
    breaker.recordSuccess();
    return result;
} catch (DependencyFailure failure) {
    breaker.recordFailure(now);
    throw failure;
}
```

**Konkretny przypadek użycia:** Awaria partnera

- **Wejście:** Dwa kwalifikujące się błędy otwierają obwód na 10 jednostek.
- **Przebieg:** OPEN odrzuca bez downstream, HALF_OPEN próbuje recovery.
- **Oczekiwany wynik:** Udana próba zamyka breaker, nieudana rozpoczyna nowe cooldown.
- **Błąd lub granica:** Błędu walidacji klienta nie należy utożsamiać z awarią całej zależności.

**Kiedy zastosować:** Dla awarii trwających dłużej niż pojedyncza próba. Breaker nie zastępuje timeoutu ani
limitu równoległości i nie cofa już wykonanych skutków.

**Koszt i pułapka:** Breaker nie ogranicza równoległości i nie cofa skutków. Demo jest jednowątkowe, liczy
kolejne RuntimeException i ma wstrzyknięty czas.

**Rozwinięcie i ślad wykonania:** W CLOSED błędy budują próg, OPEN odrzuca bez wywołania celu, HALF_OPEN
dopuszcza kontrolną próbę. Błąd tej próby rozpoczyna nowe cooldown. Parametry mają sens tylko z właściwą
klasyfikacją: błąd walidacji klienta nie powinien automatycznie dowodzić awarii całej usługi.

**Ćwiczenie sprawdzające:** Sprawdź brak wywołania downstream w OPEN i ponowne otwarcie po błędzie próby.

## Bulkhead / Concurrency Limit

**Intuicja:** Wolna integracja może zająć wszystkie wątki albo połączenia i zablokować niepowiązane operacje.
Bulkhead ogranicza zasoby przydzielone określonej klasie pracy, podobnie jak przegroda ogranicza
rozprzestrzenianie awarii. Przy wyczerpaniu limitu trzeba odrzucić lub ograniczenie kolejkować nową pracę.

**Problem i mechanizm:** Oddzielne limity zasobów izolują przeciążenie jednego rodzaju pracy od pozostałych.

**Role i współpraca:** Permit reprezentuje slot, semafor lub osobna pula pilnuje liczby, finally oddaje slot.
Izolacja jest skuteczna tylko wtedy, gdy inne wspólne zasoby nie niwelują granicy.

**Przykład:** Bulkhead(1) odrzuca drugą pracę bez kolejki i zwalnia permit w finally także po wyjątku.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (!permits.tryAcquire()) {
    throw new RejectedExecutionException("Dependency bulkhead full");
}
try { return partner.call(request); }
finally { permits.release(); }
```

**Konkretny przypadek użycia:** Izolacja wolnego dostawcy

- **Wejście:** Integracja A dostaje 5 slotów, B własny limit.
- **Przebieg:** Permit obejmuje cały czas działania i wraca w finally.
- **Oczekiwany wynik:** A nie zużywa wszystkich przydzielonych zasobów B.
- **Błąd lub granica:** Wspólna pula DB może nadal połączyć awarie mimo osobnych semaforów.

**Kiedy zastosować:** Gdy jedna zależność nie może zużyć całej zdolności aplikacji. Osobne limity powinny
odpowiadać faktycznym wąskim gardłom, nie przypadkowej liczbie klas klientów.

**Koszt i pułapka:** Wspólna pula DB może nadal połączyć awarie; limit należy powiązać z najwęższym zasobem.

**Rozwinięcie i ślad wykonania:** Permit jest pozyskiwany przed wejściem i oddawany w finally. Wariant bez
kolejki odmawia natychmiast, więc nie ukrywa przeciążenia jako oczekiwania. Osobne bulkhead dla dwóch
partnerów dają izolację tylko wtedy, gdy nie wyczerpią wspólnej puli DB, wątków albo limitu pamięci.

**Ćwiczenie sprawdzające:** Wywołaj awarię wewnątrz sekcji i sprawdź możliwość wykonania następnej pracy.

## Rate Limiter / Token Bucket / Leaky Bucket / Sliding Window

**Intuicja:** Limit tempa odpowiada na pytanie „ile żądań można dopuścić w czasie”, a nie „ile trwa
jednocześnie”. Token Bucket pozwala wykorzystać zgromadzony krótki burst, Leaky Bucket wygładza wypływ, a okna
liczą wejścia w określonym zakresie. Każda polityka inaczej traktuje granice i nagłe skoki ruchu.

**Problem i mechanizm:** Kontroluje tempo dopuszczania żądań według jawnej polityki burstów, okna i klucza.

**Role i współpraca:** Klucz wybiera zakres klienta, licznik lub wiadro utrzymuje budżet, atomowa decyzja
akceptuje albo odrzuca. Limit klastra wymaga wspólnego mechanizmu lub podziału praw.

**Przykład:** TokenBucket(2) dopuszcza dwa żądania, potem odrzuca do odnowienia tokenu; demo odnawia jeden
token na jednostkę zegara.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
LimiterKey key = new LimiterKey(tenantId, "create-order");
Decision decision = limiter.tryAcquire(key, now);
if (!decision.allowed()) {
    return tooManyRequests(decision.retryAfter());
}
return handle(request);
```

**Konkretny przypadek użycia:** Burst dwóch żądań

- **Wejście:** Token Bucket ma capacity=2 i odnawia 1 token na jednostkę.
- **Przebieg:** Dwa wejścia przechodzą, trzecie jest odrzucone do odnowienia.
- **Oczekiwany wynik:** Krótki burst ma określony limit i tempo powrotu.
- **Błąd lub granica:** Lokalna JVM nie zapewnia globalnego limitu pięciu innych instancji.

**Kiedy zastosować:** Dla ochrony zależności i sprawiedliwego przydziału tempa. Długie requesty nadal mogą
wymagać osobnego limitu in-flight.

**Koszt i pułapka:** Limit tempa różni się od limitu równoległości; model lokalny nie zapewnia wspólnego
limitu wielu JVM.

**Rozwinięcie i ślad wykonania:** Token bucket dopuszcza krótki burst do capacity i odnawia prawo wejścia w
czasie. Długo działające żądania mogą jednak narastać mimo poprawnego limitu tempa, dlatego często potrzebny
jest również bulkhead. Globalny limit wymaga atomowego współdzielonego stanu lub podziału budżetu między
właścicieli.

**Ćwiczenie sprawdzające:** Porównaj token bucket, stałe okno i sliding window na granicy dwóch okien.

## Backpressure / Demand-driven Flow

**Intuicja:** Szybszy producent może stale dokładać dane, których odbiorca nie nadąża przetwarzać.
Backpressure przekazuje informację o zdolności przyjęcia w drugą stronę przepływu. Dzięki temu produkcja może
zwolnić albo wejście wybrać jawną politykę przeciążenia, zamiast ukrywać backlog w pamięci.

**Problem i mechanizm:** Odbiorca sygnalizuje zdolność przyjęcia danych albo wymusza ograniczenie produkcji.

**Role i współpraca:** Odbiorca zgłasza demand, producent nie przekracza uzgodnionego zakresu, bufor ma limit.
Zewnętrzne źródło bez kontroli wymaga osobnej decyzji odrzucenia lub buforowania.

**Przykład:** Subscriber żąda 10 elementów, przetwarza je, dopiero potem zgłasza kolejny demand.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
void onSubscribe(Subscription subscription) {
    this.subscription = subscription;
    subscription.request(10);
}
void onBatchCompleted() {
    subscription.request(10); // Dopiero gdy odbiorca ma miejsce.
}
```

**Konkretny przypadek użycia:** Wolny odbiorca strumienia

- **Wejście:** Producent potrafi 1000 elementów/s, odbiorca 100.
- **Przebieg:** Odbiorca zgłasza demand porcjami, bufor ma limit.
- **Oczekiwany wynik:** Nie gromadzimy bez końca nieodebranej pracy.
- **Błąd lub granica:** Źródło, którego nie można zwolnić, wymaga jawnego odrzucenia, dysku lub utraty.

**Kiedy zastosować:** Dla strumieni o nierównym tempie. Async i większy bufor nie są same w sobie
backpressure.

**Koszt i pułapka:** Samo async nie daje backpressure; buforowanie bez limitu ukrywa przeciążenie. W sieci
trzeba wybrać odrzucenie lub blokowanie.

**Rozwinięcie i ślad wykonania:** Demand informuje, ile elementów odbiorca jest gotów przyjąć, a bufor ma
skończony rozmiar. Jeśli zewnętrzny producent nie potrafi zwolnić, granica musi wybrać odrzucenie, dysk albo
utratę według jawnej polityki. „Dodajmy async” przenosi kolejkę, lecz nie usuwa problemu.

**Ćwiczenie sprawdzające:** Produkuj dziesięć razy szybciej i wskaż maksymalną liczbę danych w pamięci.

## Load Shedding / Admission Control / Queue-based Load Leveling

**Intuicja:** Gdy więcej pracy przychodzi niż może zostać wykonane, system musi zdecydować, co jeszcze
przyjmie. Admission Control odrzuca nadmiar przed kosztownym rozpoczęciem, Load Shedding zrzuca wybraną pracę,
a ograniczona kolejka wygładza krótkie skoki. Długi backlog zwiększa czas, nie realną wydajność.

**Problem i mechanizm:** System odrzuca nadmiar pracy albo przyjmuje ją do ograniczonej kolejki z jawnym
czasem oczekiwania.

**Role i współpraca:** Polityka obserwuje pojemność i wiek pracy, granica przyjęcia daje jednoznaczny wynik,
kolejka przechowuje tylko zaakceptowany zakres.

**Przykład:** Przy pełnej kolejce 100 zadań API zwraca 429/503 zamiast zwiększać pamięć bez limitu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (queue.oldestAge().compareTo(maxQueueAge) > 0
        || !queue.offer(job)) {
    return serviceUnavailable("Capacity exhausted");
}
return accepted(job.id());
```

**Konkretny przypadek użycia:** Przeciążony worker

- **Wejście:** Kolejka mieści 100 zadań, wydajność to 20/s.
- **Przebieg:** Admission control uwzględnia pojemność i wiek przed przyjęciem następnej pracy.
- **Oczekiwany wynik:** Zaakceptowane zadania mają realny plan i wynik.
- **Błąd lub granica:** 100 oczekujących to około 5 s opróżniania bez nowego ruchu, nie natychmiastowa
  obsługa.

**Kiedy zastosować:** Przy przeciążeniu i kosztownych requestach. Nie akceptuj bez końca pracy, której termin
minie przed rozpoczęciem.

**Koszt i pułapka:** Kolejka wygładza burst, ale zwiększa latency; zaakceptowane zadanie musi mieć wynik i
lifecycle.

**Rozwinięcie i ślad wykonania:** Kolejka 100 zadań przy 20 wykonaniach na sekundę dodaje około pięciu sekund
opróżniania, nawet bez nowego ruchu. Przyjmowanie dalej niż deadline klienta może być marnowaniem pracy.
Admission control powinno uwzględniać wiek backlogu i koszt żądania, nie tylko wolne miejsce w pamięci.

**Ćwiczenie sprawdzające:** Wylicz czas opróżniania backlogu 100 przy wydajności 20 zadań/s.

## Fallback / Graceful Degradation / Static Content Hosting

**Intuicja:** Nie wszystkie funkcje mają taką samą krytyczność. Podczas awarii rekomendacji katalog może nadal
pokazać produkty bez dodatku. Fallback definiuje taki słabszy wynik, a graceful degradation szerszy sposób
utrzymania użyteczności. Ważne, by nie nazwać sukcesem operacji, której kluczowego warunku nie spełniono.

**Problem i mechanizm:** Przy awarii system przechodzi do ograniczonego, jawnie słabszego zachowania.

**Role i współpraca:** Podstawowy kontrakt określa minimum, alternatywna ścieżka dopuszczalne dane, wynik
ujawnia partial lub stale. Polityka wybiera, gdzie degradacja ma sens.

**Przykład:** Katalog pokazuje ostatni snapshot i ukrywa rekomendacje; checkout nie udaje sukcesu płatności.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
try {
    return recommendations.fresh(customerId, timeout);
} catch (DependencyUnavailable failure) {
    return RecommendationResult.degraded(List.of());
}
```

**Konkretny przypadek użycia:** Opcjonalne rekomendacje

- **Wejście:** Katalog działa, dostawca rekomendacji jest niedostępny.
- **Przebieg:** Fallback zwraca jawnie zdegradowany pusty dodatek lub dozwoloną kopię.
- **Oczekiwany wynik:** Klient nadal ogląda produkty.
- **Błąd lub granica:** Nie stosujemy podobnego fałszywego sukcesu do potwierdzenia płatności.

**Kiedy zastosować:** Dla opcjonalnych funkcji i danych o ustalonej tolerancji starości. Potwierdzenia
płatności nie wolno zastępować udawanym sukcesem.

**Koszt i pułapka:** Fallback może podawać stare dane lub zamaskować awarię; nie każdy niezmiennik dopuszcza
degradację.

**Rozwinięcie i ślad wykonania:** Fallback powinien mieć jawny rodzaj wyniku: stale, partial albo unavailable.
Wyszukiwarka może pokazać poprzedni indeks, ale potwierdzenie płatności nie może zostać zastąpione fałszywym
sukcesem. Testuj fallback także podczas recovery, gdy system przestaje korzystać ze starej kopii.

**Ćwiczenie sprawdzające:** Określ, które pola mogą być stare i jak klient rozpozna degradację.

## Hedged Request / Speculative Execution

**Intuicja:** Większość odczytów jest szybka, ale mały odsetek trwa bardzo długo. Hedging po krótkim
opóźnieniu zleca dodatkowy bezpieczny odczyt i bierze pierwszy poprawny wynik. To próba skrócenia ogona
latency kosztem zwiększonej pracy, nie bezwarunkowe przyspieszenie każdego requestu.

**Problem i mechanizm:** Po krótkim opóźnieniu wysyła się dodatkową bezpieczną próbę do innej repliki i używa
pierwszego poprawnego wyniku.

**Role i współpraca:** Pierwsza próba rusza od razu, scheduler ewentualnie uruchamia drugą, zwycięski wynik
kończy oczekiwanie. Anulowanie przegranej jest best-effort.

**Przykład:** Odczyt do repliki B rusza po 50 ms oczekiwania na A; przegrana próba jest anulowana best-effort.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
0 ms:   wyślij odczyt do A
50 ms:  jeśli brak wyniku, uruchom bezpieczny odczyt do B
pierwszy poprawny wynik: zakończ request
pozostałe próby: anuluj best-effort w uzgodnionym budżecie
```

**Konkretny przypadek użycia:** Ogon opóźnienia odczytu

- **Wejście:** A nie odpowiedziało po 50 ms, dostępna jest niezależna replika B.
- **Przebieg:** W granicach polityki uruchamiamy drugi bezpieczny odczyt.
- **Oczekiwany wynik:** Pierwszy poprawny wynik kończy oczekiwanie.
- **Błąd lub granica:** Hedge zwiększa RPS i przy przeciążeniu wszystkich replik może pogorszyć awarię.

**Kiedy zastosować:** Dla bezpiecznych odczytów i replik o niejednakowym opóźnieniu. Przy globalnym
przeciążeniu dodatkowe próby mogą pogłębić awarię.

**Koszt i pułapka:** Podwaja część obciążenia i nie pasuje do nieidempotentnych zapisów; przy przeciążeniu
może pogorszyć awarię.

**Rozwinięcie i ślad wykonania:** Pierwszy wolny odczyt może uruchomić drugi do innej repliki po określonym
opóźnieniu. Sukces jednego kończy oczekiwanie klienta, ale anulowanie drugiego jest best-effort. Ogranicz
liczbę hedge i sprawdź rzeczywisty dodatkowy ruch; przy przeciążeniu wszystkich replik próby spekulacyjne mogą
pogłębić problem.

**Ćwiczenie sprawdzające:** Policz dodatkowe RPS, jeśli 10% odczytów uruchamia hedge.

## Single Flight / Request Coalescing

**Intuicja:** Sto identycznych missów cache nie musi powodować stu równoczesnych odczytów bazy. Single Flight
łączy pracę w toku dla tego samego klucza. Pierwszy klient uruchamia load, pozostali współdzielą jego wynik;
po zakończeniu nowy request może rozpocząć świeżą pracę.

**Problem i mechanizm:** Równoczesne identyczne operacje współdzielą jedną pracę w locie.

**Role i współpraca:** Mapa in-flight wskazuje Future, zwycięzca rezerwacji uruchamia loader, klienci dostają
odrębne uchwyty wyniku. Cleanup po błędzie umożliwia recovery.

**Przykład:** Sto missów cache dla p1 czeka na ten sam CompletableFuture, a wpis in-flight usuwa się również
po błędzie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
CompletableFuture<Product> first = flights.execute(id, () -> loadAsync(id));
CompletableFuture<Product> second = flights.execute(id, () -> loadAsync(id));
// Dla równoczesnego tego samego id działa jeden load.
// Każdy klient ma własny uchwyt oczekiwania.
```

**Konkretny przypadek użycia:** Wspólny miss p1

- **Wejście:** 100 requestów równocześnie potrzebuje tego samego produktu.
- **Przebieg:** Zwycięzca putIfAbsent uruchamia load, reszta czeka na kopiach Future.
- **Oczekiwany wynik:** W toku jest jeden loader dla klucza.
- **Błąd lub granica:** Cancel jednego klienta nie powinien zatrzymywać pracy pozostałych; brak zakończenia
  wymaga deadline.

**Kiedy zastosować:** Dla kosztownych, identycznych operacji. To nie trwała deduplikacja między JVM i nie
cache ukończonych wyników.

**Koszt i pułapka:** Nie usuwa potrzeby TTL i limitu kluczy; anulowanie jednego klienta nie powinno przerwać
pracy dla wszystkich.

**Rozwinięcie i ślad wykonania:** SingleFlight rezerwuje jeden CompletableFuture dla klucza przez putIfAbsent,
a loader uruchamia tylko zwycięzca. Inni dostają zależne kopie wyniku; cancel jednego nie przerywa pracy
pozostałych. Wpis znika po sukcesie i błędzie, więc kolejne żądanie może wykonać świeżą próbę.

**Ćwiczenie sprawdzające:** Sprawdź błąd loadera i możliwość nowej próby po usunięciu wpisu.

## Cache Stampede Prevention / TTL Jitter

**Intuicja:** Jednoczesne wygaśnięcie wielu kopii może przerzucić cały ruch na bazę. TTL Jitter rozkłada
momenty wygaśnięcia między kluczami, a jeden odświeżający ogranicza konkurencję dla tego samego klucza. Są to
różne części problemu lawiny missów i często trzeba połączyć obie.

**Problem i mechanizm:** Wygaśnięcia i odbudowę popularnych wpisów rozkłada się w czasie lub ogranicza jednego
odbudowującego.

**Role i współpraca:** Polityka TTL losuje przedział, Single Flight lub lock ogranicza odbudowę, opcjonalny
stale wynik podtrzymuje odczyt. Każda kopia ma maksymalny dopuszczalny wiek.

**Przykład:** TTL wynosi 60 s plus losowe 0–10 s; jeden worker odnawia popularny klucz z dopuszczeniem starego
wyniku.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Duration ttl = baseTtl.plus(randomDuration(maxJitter));
return flights.execute(key, () -> loadAndCache(key, ttl));
// Rozproszenie TTL i współdzielenie load rozwiązują różne przeploty.
```

**Konkretny przypadek użycia:** Masowe wygaśnięcie

- **Wejście:** Milion kluczy dostało podobny TTL przy rozgrzaniu.
- **Przebieg:** Jitter rozprasza expiry między kluczami, single-flight ogranicza jeden hot key.
- **Oczekiwany wynik:** Źródło nie dostaje jednej zsynchronizowanej lawiny.
- **Błąd lub granica:** Sam jitter nie deduplikuje stu missów tego samego klucza.

**Kiedy zastosować:** Dla popularnych cache i masowych restartów. Sam jitter nie połączy stu jednoczesnych
żądań jednego brakującego klucza.

**Koszt i pułapka:** Losowy TTL rozprasza różne klucze, ale nie deduplikuje jednego hot key; trzeba dobrać
mechanizmy razem.

**Rozwinięcie i ślad wykonania:** TTL jitter rozprasza jednoczesne wygaśnięcie różnych kluczy. Single-flight
łączy natomiast równoczesne odczyty tego samego klucza. Są to dwa odmienne rozkłady obciążenia. Rozwiązanie
powinno wyjaśniać oba, jeśli restart lub migracja powodują jednoczesny miss dużej części cache.

**Ćwiczenie sprawdzające:** Odróżnij lawinę po jednym kluczu od wspólnego expiry miliona kluczy.

## Cooperative Cancellation / Interruptible Task

**Intuicja:** Klient, który już nie czeka, nie zawsze chce dalej zużywać CPU i połączenia. Cooperative
Cancellation przekazuje sygnał zakończenia, a sama praca sprawdza go w bezpiecznych miejscach. To współpraca,
nie przymusowe cofnięcie wszystkich instrukcji i commitów, które zdążyły się wykonać.

**Problem i mechanizm:** Praca sprawdza anulowanie i zwalnia zasoby; właściciel zadania kontroluje lifecycle.

**Role i współpraca:** Właściciel zgłasza anulowanie, zadanie respektuje interrupt lub token, zakres zasobów
gwarantuje cleanup. Jednostki już zatwierdzone zachowują swoje skutki.

**Przykład:** Pętla importu sprawdza interrupt między porcjami, a blokujące API propaguje
InterruptedException.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
for (Batch batch : batches) {
    if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedException("Import cancelled");
    }
    transaction.run(() -> importOneBatch(batch));
}
```

**Konkretny przypadek użycia:** Anulowany import

- **Wejście:** Import zatwierdził 3 porcje z 10, klient anuluje.
- **Przebieg:** Zadanie sprawdza sygnał między porcjami i zamyka zasoby.
- **Oczekiwany wynik:** Pozostała praca może się nie rozpocząć, zakończone commity są jawne.
- **Błąd lub granica:** Cancel nie cofa pierwszych 3 porcji ani efektu w zewnętrznym API.

**Kiedy zastosować:** Dla długiej pracy i zadań potomnych. Nie interpretuj cancel(true) jako dowodu, że
partner zewnętrzny nie wykonał operacji.

**Koszt i pułapka:** Future.cancel(true) to żądanie przerwania, nie gwarancja zatrzymania. Zapis już
zatwierdzony pozostaje skutkiem.

**Rozwinięcie i ślad wykonania:** Kod powinien sprawdzać przerwanie między bezpiecznymi jednostkami pracy i
zachować stan zakończonych commitów. Przerwanie podczas blokującego oczekiwania wymaga zwolnienia zasobów i
przekazania sygnału wyżej. Nie połykaj InterruptedException i nie deklaruj, że anulowanie Future dowodzi braku
skutku po stronie partnera.

**Ćwiczenie sprawdzające:** Anuluj import między dwoma commitami i opisz stan częściowy.

## Leader–Followers / Half-Sync Half-Async

**Intuicja:** Obsługa zdarzeń wejściowych i kosztowne wykonanie mają inne potrzeby. Leader–Followers
organizuje role wątków przejmujących oczekiwanie na zdarzenie, a Half-Sync Half-Async oddziela stronę
asynchronicznych zdarzeń od synchronicznego wykonania pracy. Są to wzorce lokalnego modelu wykonania, nie
wybór lidera klastra.

**Problem i mechanizm:** Wątki rozdzielają odbiór zdarzeń i wykonanie pracy, kontrolując koszt przełączania i
blokowania.

**Role i współpraca:** Dispatcher lub leader odbiera gotową pracę, kolejka ją przekazuje, worker wykonuje
blokujące kroki. Limit kolejki chroni przekazanie przed nieograniczonym wzrostem.

**Przykład:** Jeden odbiorca pętli sieciowej przekazuje blokującą pracę ograniczonej puli workerów.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
zdarzenia sieciowe -> nieblokujący dispatcher
                      -> ograniczona kolejka
                          -> workery dla blokującej pracy

Nie przenosimy wolnego DB call na sam dispatcher.
```

**Konkretny przypadek użycia:** Dispatcher i blokujące DB

- **Wejście:** Jedna pętla obsługuje wiele kanałów sieciowych.
- **Przebieg:** Gotowe zdarzenia trafiają do ograniczonej puli dla blokującej pracy.
- **Oczekiwany wynik:** Wolne zapytanie nie zatrzymuje samej obsługi wszystkich kanałów.
- **Błąd lub granica:** Nieograniczona kolejka między pętlą a workerami tylko ukrywa przeciążenie.

**Kiedy zastosować:** Gdy event loop nie powinien blokować na pracy klienta. Dobierz model do rzeczywistego
API I/O i kosztu zadań.

**Koszt i pułapka:** Wzorce zarządzania wątkami nie są wyborem lidera w klastrze; zablokowanie event loop
zatrzymuje wiele połączeń.

**Rozwinięcie i ślad wykonania:** Nazwy opisują organizację lokalnych wątków: odbiór gotowych zdarzeń,
przekazanie pracy i zmianę roli odbierającego. Nie służą uzgadnianiu lidera bazy w klastrze. Blokujące
przetwarzanie należy odsunąć od pętli przyjęć, zachowując limit przekazywanej pracy.

**Ćwiczenie sprawdzające:** Wskaż granicę, na której wolno wykonać blokujące zapytanie DB.

## Reactor / Proactor

**Intuicja:** Powiadomienie „kanał jest gotowy” różni się od „operacja już się zakończyła”. Reactor reaguje na
gotowość i dopiero próbuje I/O, Proactor otrzymuje zakończenie wcześniej zleconej operacji. Oba modele
pozwalają obsługiwać wiele operacji, ale callback i bufor muszą mieć jasno określoną własność.

**Problem i mechanizm:** Reactor reaguje na gotowość I/O, a Proactor na zakończenie operacji asynchronicznej.

**Role i współpraca:** Reactor używa readiness i handlera, Proactor zlecenia oraz completion handlera.
Częściowy odczyt i koszt callbacku nadal wymagają poprawnej obsługi.

**Przykład:** Selector Javy zgłasza gotowy kanał do read; CompletionHandler w asynchronicznym API otrzymuje
wynik zakończenia.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
Reactor:  gotowość do odczytu -> handler próbuje read
Proactor: zleć read -> wykonanie I/O -> callback z wynikiem

W obu przypadkach długi callback może opóźniać pozostałe zdarzenia.
```

**Konkretny przypadek użycia:** Gotowość a zakończenie I/O

- **Wejście:** Kanał zgłasza readiness albo API oddaje completion odczytu.
- **Przebieg:** Reactor próbuje read po gotowości, Proactor odbiera wynik zleconego I/O.
- **Oczekiwany wynik:** Kod interpretuje właściwy rodzaj sygnału.
- **Błąd lub granica:** Długi callback może blokować dispatcher w obu modelach; sama nazwa Future nie
  rozstrzyga implementacji.

**Kiedy zastosować:** Dla systemów korzystających z odpowiedniego API asynchronicznego. Sama metoda zwracająca
Future nie dowodzi konkretnego modelu realizacji w kernelu.

**Koszt i pułapka:** Nie można wnioskować o modelu kernela wyłącznie z nazwy frameworka; callback nie powinien
blokować dispatchera.

**Rozwinięcie i ślad wykonania:** W Reactor gotowość sygnalizuje, że warto spróbować operacji I/O; wynik nadal
może być częściowy. W Proactor callback otrzymuje zakończenie zleconej operacji. W obu modelach długi callback
potrafi zatrzymać obsługę wielu innych kanałów, więc koszt CPU i blokowania pozostaje istotny.

**Ćwiczenie sprawdzające:** Porównaj read po readiness z callbackiem po completion.

## Retry Budget / Adaptive Concurrency

**Intuicja:** Limity ustawione osobno w każdym kliencie mogą razem wygenerować zbyt dużo dodatkowej pracy.
Retry Budget ogranicza łączny koszt ponowień względem normalnego ruchu. Adaptive Concurrency zmienia liczbę
dopuszczonych wywołań według obserwowanej kondycji; to pokrewne sterowanie obciążeniem, ale inną wielkością.

**Problem i mechanizm:** Dopuszczalna liczba dodatkowych prób lub równoległość zmieniają się z kondycją
systemu i bazowym ruchem.

**Role i współpraca:** Budżet zlicza dodatkowe próby, kontroler obserwuje latency i błędy, limiter stosuje
uzgodniony limit. Wzrost po recovery powinien być ostrożny.

**Przykład:** Na 1000 bazowych requestów wolno wykonać najwyżej 100 retry, a wzrost latency zmniejsza limit
in-flight.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (retryable(error) && retryBudget.trySpend()) {
    scheduleRetryWithin(deadline);
} else {
    finishWith(error);
}
```

**Konkretny przypadek użycia:** Budżet dodatkowej pracy

- **Wejście:** Na 1000 bazowych requestów wolno wykonać 100 retry.
- **Przebieg:** Każde ponowienie wydaje część wspólnego budżetu, limiter obserwuje kondycję.
- **Oczekiwany wynik:** Retry nie rośnie nieograniczenie podczas awarii.
- **Błąd lub granica:** Liczenie retry jako nowego ruchu bazowego mogłoby sztucznie powiększać budżet.

**Kiedy zastosować:** Dla wielu klientów i zależności wrażliwych na przeciążenie. Układ sterowania wymaga
stabilności i pomiaru, nie tylko automatycznego podnoszenia liczby workerów.

**Koszt i pułapka:** Źle dobrana pętla sterowania oscyluje; brak ruchu bazowego nie powinien dać
nieskończonych prób.

**Rozwinięcie i ślad wykonania:** Budżet retry liczy dodatkową pracę względem ruchu bazowego, nie wobec liczby
wcześniejszych retry. Pętla adaptacyjna obserwuje czas oraz błędy i zmienia limit stopniowo. Dodaj histerezę
lub ostrożny wzrost po recovery, aby limit nie oscylował gwałtownie wraz z krótkimi zmianami ruchu.

**Ćwiczenie sprawdzające:** Zaprojektuj zachowanie podczas nagłej awarii i stopniowego recovery.

## Priority Queue / Fair Scheduling / Aging

**Intuicja:** Praca interaktywna i eksport mogą mieć różną pilność, ale nieskończony priorytet dla jednej
klasy zagłodzi drugą. Fair Scheduling ustala podział zasobów, a Aging podnosi znaczenie długo czekającej
pracy. Struktura kolejki ma realizować konkretną regułę sprawiedliwości, nie tylko sortowanie po liczbie.

**Problem i mechanizm:** Praca jest porządkowana według priorytetu z mechanizmem ograniczającym zagłodzenie.

**Role i współpraca:** Zadanie ma klasę i czas wejścia, scheduler wybiera według wag lub wieku, limity
tenantów chronią przed zajęciem wszystkich slotów.

**Przykład:** Szybkie zapytania interaktywne mają pierwszeństwo, ale długo czekający eksport dostaje
podwyższony priorytet.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
priorytet efektywny = priorytet bazowy + funkcja czasu czekania
przy wyborze:
    uwzględnij limit tenanta
    wybierz pracę według uzgodnionej polityki
    aktualizuj wiek bez tworzenia nieaktualnego indeksu kolejki
```

**Konkretny przypadek użycia:** Interakcje i eksporty

- **Wejście:** Premium napływa stale, eksport czeka minutę.
- **Przebieg:** Wagi i aging zapewniają postęp także niższej klasie z limitami tenantów.
- **Oczekiwany wynik:** Eksport dostaje skończony udział zasobów według kontraktu.
- **Błąd lub granica:** Zwykła kolejka priorytetowa bez ochrony może nigdy go nie dopuścić.

**Kiedy zastosować:** Dla mieszanych workloadów. Sprawdź kontrprzykład stałego napływu premium, aby zwykła
praca miała realną gwarancję postępu.

**Koszt i pułapka:** Priorytet może pozwolić jednemu tenantowi zagłodzić innych; potrzebne są limity per klasa
i tenant.

**Rozwinięcie i ślad wykonania:** Najwyższy priorytet nie powinien mieć nieograniczonego dostępu do wszystkich
workerów. Aging zwiększa wagę czekającej pracy, a limity per tenant chronią sprawiedliwość. Test z ciągłym
strumieniem premium powinien nadal wykazać skończone oczekiwanie zwykłego eksportu według wybranego kontraktu.

**Ćwiczenie sprawdzające:** Podaj strumień pracy, który bez aging nigdy nie dopuści eksportu.
