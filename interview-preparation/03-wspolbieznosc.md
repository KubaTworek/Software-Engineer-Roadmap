# Współbieżność i model pamięci

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#03-współbieżność-i-model-pamięci)

<!-- question-toc:start -->
## Spis pytań

- [CON-01. Czym różnią się proces i wątek?](#con-01-czym-różnią-się-proces-i-wątek)
- [CON-02. Czym różni się data race od race condition? Czy wyścig może wystąpić bez data race?](#con-02-czym-różni-się-data-race-od-race-condition-czy-wyścig-może-wystąpić-bez-data-race)
- [CON-03. Co gwarantuje Java Memory Model i dlaczego wątki mogą widzieć różną kolejność operacji?](#con-03-co-gwarantuje-java-memory-model-i-dlaczego-wątki-mogą-widzieć-różną-kolejność-operacji)
- [CON-04. Czym różnią się volatile, synchronized i ReentrantLock?](#con-04-czym-różnią-się-volatile-synchronized-i-reentrantlock)
- [CON-05. Dlaczego volatile nie wystarcza dla `i++`, obiektu i dwóch powiązanych pól?](#con-05-dlaczego-volatile-nie-wystarcza-dla-i-obiektu-i-dwóch-powiązanych-pól)
- [CON-06. Kiedy AtomicInteger wystarczy, a kiedy potrzebna jest transakcja lub blokada?](#con-06-kiedy-atomicinteger-wystarczy-a-kiedy-potrzebna-jest-transakcja-lub-blokada)
- [CON-07. Czy klasa ze zmiennym lub prywatnym stanem może być thread-safe? Czy stateless wystarcza?](#con-07-czy-klasa-ze-zmiennym-lub-prywatnym-stanem-może-być-thread-safe-czy-stateless-wystarcza)
- [CON-08. Dlaczego final pomaga, ale nie zapewnia niezmienności i całkowitej thread-safety?](#con-08-dlaczego-final-pomaga-ale-nie-zapewnia-niezmienności-i-całkowitej-thread-safety)
- [CON-09. Czym różnią się bezpieczna inicjalizacja i bezpieczna publikacja?](#con-09-czym-różnią-się-bezpieczna-inicjalizacja-i-bezpieczna-publikacja)
- [CON-10. Dlaczego ucieczka `this` i wywołanie metody nadpisywalnej w konstruktorze są groźne?](#con-10-dlaczego-ucieczka-this-i-wywołanie-metody-nadpisywalnej-w-konstruktorze-są-groźne)
- [CON-11. Jak bezpiecznie implementować leniwą inicjalizację singletona?](#con-11-jak-bezpiecznie-implementować-leniwą-inicjalizację-singletona)
- [CON-12. Dlaczego dwie synchronizowane metody nadal mogą mieć race condition?](#con-12-dlaczego-dwie-synchronizowane-metody-nadal-mogą-mieć-race-condition)
- [CON-13. Czym jest deadlock i jak go wykryć oraz usunąć?](#con-13-czym-jest-deadlock-i-jak-go-wykryć-oraz-usunąć)
- [CON-14. Czym różnią się `wait()`, `sleep()` i `join()`?](#con-14-czym-różnią-się-wait-sleep-i-join)
- [CON-15. Jak działa problem producent–konsument i kiedy użyć BlockingQueue?](#con-15-jak-działa-problem-producentkonsument-i-kiedy-użyć-blockingqueue)
- [CON-16. Jak Semaphore ogranicza dostęp do zasobu i co przy zerowej liczbie pozwoleń?](#con-16-jak-semaphore-ogranicza-dostęp-do-zasobu-i-co-przy-zerowej-liczbie-pozwoleń)
- [CON-17. Jak ThreadLocal powoduje wyciek pamięci i mieszanie danych żądań?](#con-17-jak-threadlocal-powoduje-wyciek-pamięci-i-mieszanie-danych-żądań)
- [CON-18. Czym jest thread affinity i jak propagować kontekst przez CompletableFuture?](#con-18-czym-jest-thread-affinity-i-jak-propagować-kontekst-przez-completablefuture)
- [CON-19. Jakie gwarancje widoczności daje ExecutorService?](#con-19-jakie-gwarancje-widoczności-daje-executorservice)
- [CON-20. Jak udowodnić przyczynę sporadycznie błędnych danych przy dużej współbieżności?](#con-20-jak-udowodnić-przyczynę-sporadycznie-błędnych-danych-przy-dużej-współbieżności)
<!-- question-toc:end -->

## CON-01. Czym różnią się proces i wątek?

Proces ma własną przestrzeń adresową i zasoby systemowe. Wątki procesu współdzielą m.in. heap, ale mają własny kontekst wykonania i stosy. Współdzielenie upraszcza komunikację, a jednocześnie pozwala jednemu błędowi uszkodzić wspólny stan.

Osobne procesy też mogą mieć wyścigi, np. podczas aktualizacji tego samego rekordu w bazie. W projekcie wybór procesu, wątku lub komunikatu nie zwalnia z ustalenia właściciela danych, atomowości i sposobu obsługi awarii.

Dwa wątki mogą trzymać referencję do tego samego obiektu Customer, ale każdy ma własne zmienne lokalne. Lokalna referencja nie czyni wskazywanego obiektu lokalnym. Podobnie dwa procesy z odrębnymi heapami mogą aktualizować to samo konto w DB i naruszyć jego niezmiennik.

W diagnostyce odróżniam limit procesów/wątków systemowych od liczby zadań Java i virtual threads. Awaria procesu zabija jego wątki oraz stan w pamięci; retry innego procesu potrzebuje więc trwałej tożsamości operacji. To granica między bezpieczeństwem lokalnym a poprawnością rozproszonego przypadku użycia.

## CON-02. Czym różni się data race od race condition? Czy wyścig może wystąpić bez data race?

Data race dotyczy konfliktujących dostępów do tej samej zmiennej, z co najmniej jednym zapisem, bez właściwej relacji happens-before. Race condition to szerszy problem: poprawność zależy od przeplotu operacji. Może wystąpić mimo poprawnie synchronizowanych pojedynczych metod.

Przykład: `get` i `addAndGet` są atomowe, lecz „sprawdź saldo, potem odejmij” nie jest jedną atomową operacją. Najpierw zapisuję niezmiennik biznesowy, potem chronię całą sekwencję, zamiast tylko poszczególne pola.

Wyścig bez data race pokazuje bezpieczny pojedynczo AtomicInteger:

<!-- example:CON-02-1 kind:java-sketch -->
**Kontekst:** Szkic Java — wymaga wskazanych pól lub metod otoczenia; poza automatyczną kompilacją.

~~~java
// Błędny fragment operacji biznesowej:
if (balance.get() >= amount) {
    balance.addAndGet(-amount);
}
~~~

Dwa żądania mogą oba zobaczyć saldo 500 i zaakceptować pobranie 400. Wszystkie dostępy do balance są atomowe, ale warunek nie jest związany atomowo z aktualizacją. Gdy reguła obejmuje jeszcze rejestr transakcji, nawet poprawienie samego licznika nie wystarczy. Niezmiennik opisuję jako całość: saldo nieujemne oraz dokładnie jeden wpis dla zaakceptowanego pobrania.

## CON-03. Co gwarantuje Java Memory Model i dlaczego wątki mogą widzieć różną kolejność operacji?

JMM określa dopuszczalne obserwacje pamięci między wątkami. Kompilator, JIT i CPU mogą zmieniać wykonanie, zachowując wymagane reguły. Bez synchronizacji intuicyjna kolejność linii kodu nie jest kontraktem publikacji innemu wątkowi.

Happens-before łączy m.in. odblokowanie i późniejsze zablokowanie tego samego monitora, zapis volatile z późniejszym odczytem oraz start wątku z jego działaniem. Nie znaczy „zdarzyło się wcześniej według zegara”. [JLS: model pamięci](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html).

Ważne jest powiązanie operacji konkretnym mechanizmem. Jeżeli jeden wątek zwalnia monitor A, a drugi blokuje monitor B, nie dostają oczekiwanej relacji tylko dlatego, że oba użyły synchronized. Tak samo zapis zwykłego pola po publikacji nie staje się bezpieczny przez wcześniejszą synchronizację.

Happens-before jest przechodnie: przygotowanie danych przed submit, wykonanie taska i get wyniku tworzą łańcuch widoczności. Ta własność pozwala budować większe protokoły z prostych elementów. Nie trzeba opisywać wszystkiego jako „flush cache CPU”; JMM definiuje obserwowalne gwarancje, a konkretne bariery zależą od architektury i JVM.

## CON-04. Czym różnią się volatile, synchronized i ReentrantLock?

Volatile daje widoczność i reguły uporządkowania dostępu do pola, lecz nie wyłączność wykonywania kilku instrukcji. Synchronized chroni sekcję monitorem i ustanawia widoczność. ReentrantLock zapewnia podobną ochronę, dodatkowo np. `tryLock`, oczekiwanie przerywalne i wiele Condition.

Volatile nie zakazuje wszystkich przestawień instrukcji; zabrania obserwacji sprzecznych z jego gwarancjami. Blokady muszą być wspólne dla wszystkich ścieżek dostępu. ReentrantLock zwalniam w `finally`; synchronized zwalnia monitor przy wyjściu z bloku.

Synchronized jest reentrant: właściciel może wejść ponownie na ten sam monitor. Nie znaczy to, że obiekt pozostaje poprawny podczas każdego callbacka. Jeśli część pól zmieniono przed wywołaniem obcego kodu, ponowne wejście może zobaczyć stan przejściowy.

ReentrantLock pozwala przerwać oczekiwanie lub odmówić po czasie, ale wymaga dyscypliny zwalniania i nie chroni ścieżek, które go omijają. Fairness zmienia sposób przydzielania, zwykle kosztem throughput; nie gwarantuje czasu odpowiedzi. Wybieram najprostszy mechanizm spełniający wymaganie, a zmianę locka oceniam profilem contention.

## CON-05. Dlaczego volatile nie wystarcza dla `i++`, obiektu i dwóch powiązanych pól?

`i++` to odczyt, obliczenie i zapis; dwa wątki mogą nadpisać wynik. Volatile referencja publikuje wskazywany obiekt, lecz nie zmienia jego późniejszych aktualizacji w atomowe operacje. Dwa pola volatile nie tworzą jednego snapshotu: można zobaczyć nową wartość jednego i starą drugiego.

Dla licznika wystarczy operacja atomowa; dla relacji między polami stosuję wspólną blokadę albo niezmienny obiekt stanu podmieniany atomowo. Flaga zakończenia może być volatile, jeśli protokół nie wymaga dodatkowej atomowości.

Jedna volatile referencja do niezmiennego stanu może zachować spójność kilku wartości:

<!-- example:CON-05-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
record Limits(int maxRequests, int maxConcurrent) {}
private volatile Limits limits = new Limits(100, 10);
Limits snapshot() {
    return limits; // jeden odczyt pary
}
~~~

Czytelnik powinien zachować ten snapshot w zmiennej, zamiast odczytać limits osobno dla każdego pola. Podmiana całej pary nie czyni jednak atomową aktualizacji „odczytaj, zmień, zapisz” przez dwóch administratorów; tam potrzebny jest CAS lub blokada. Bez tego nowsza konfiguracja może nadpisać równoległą zmianę.

## CON-06. Kiedy AtomicInteger wystarczy, a kiedy potrzebna jest transakcja lub blokada?

Wystarcza dla niezależnego licznika lub aktualizacji wyrażalnej atomowym CAS. Dla warunkowego pobrania środków w pamięci potrzebna jest pętla CAS: odczytaj saldo, sprawdź warunek, spróbuj podmiany i powtórz po konflikcie. Funkcja aktualizacji nie powinna wykonywać efektów ubocznych, bo może zostać powtórzona.

AtomicInteger nie chroni dwóch kont ani kilku instancji usługi. Trwałe saldo wymaga niezmiennika w bazie, blokady/versioningu lub warunkowej aktualizacji. Atomowość zmiennej, operacji i transakcji biznesowej to różne granice.

Rdzeń warunkowego pobrania w pamięci może wyglądać tak:

<!-- example:CON-06-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static boolean withdraw(java.util.concurrent.atomic.AtomicInteger balance, int amount) {
    if (amount <= 0) throw new IllegalArgumentException();
    for (;;) {
        int current = balance.get();
        if (current < amount) return false;
        if (balance.compareAndSet(current, current - amount)) return true;
    }
}
~~~

Udane CAS jest punktem linearyzacji. Po konflikcie wątek ponownie sprawdza warunek na nowym saldzie. Ta metoda nie zapisuje historii, nie chroni wielu procesów i traci stan po restarcie. Przy dużym contention kolejne próby też zużywają CPU; lock lub pojedynczy właściciel może dać lepszą przewidywalność.

<!-- follow-up:CON-06 -->
**Sprawdź rozumienie:** Dlaczego poprawny CAS salda nie wystarcza do transferu między dwoma kontami i zapisu historii?

<details>
<summary>Kierunek odpowiedzi</summary>

CAS ma jeden punkt linearyzacji jednej zmiennej. Transfer ma niezmiennik kilku stanów, a historia musi być atomowa z nimi i trwała. Potrzebna jest szersza granica, np. transakcja DB lub pojedynczy właściciel całego stanu.

</details>

## CON-07. Czy klasa ze zmiennym lub prywatnym stanem może być thread-safe? Czy stateless wystarcza?

Zmienny stan może być bezpieczny przy izolacji wątku, wspólnej blokadzie albo poprawnym protokole atomowym. `private` ogranicza dostęp składniowy, nie równoczesne wywołania metod. Klasa bez własnych pól też może być niebezpieczna, jeśli modyfikuje współdzieloną zależność albo argument.

Bezpieczny singleton nie gwarantuje poprawności całego przypadku użycia. Jego metoda może składać kilka atomowych wywołań repozytorium w nieatomową sekwencję. Oceniam pełny graf współdzielenia i kontrakt operacji, a nie sam scope Springa.

Stateless PaymentService może wywołać niebezpieczny adapter przechowujący ostatni request w polu albo połączyć poprawne metody DAO w błędny protokół. Ocena bezpieczeństwa wymaga więc przejścia przez zależności i ustalenia, kto rzeczywiście posiada dane.

Dla klasy ze stanem wybieram jeden kontrakt: wszystkie mutacje pod wspólnym lockiem, izolacja do jednego właściciela lub poprawna maszyna atomowych przejść. Mieszanie tych modeli bez reguł utrudnia dowód. Przydatna jest krótka dokumentacja: „guarded by monitor X”, „nie współdzielić między zadaniami” albo „niemutowalny snapshot”. Sam singleton określa liczbę instancji, nie ten kontrakt.

## CON-08. Dlaczego final pomaga, ale nie zapewnia niezmienności i całkowitej thread-safety?

Poprawnie skonstruowane pola final mają specjalne gwarancje inicjalizacji. Finalna referencja nie pozwala na przypisanie innej listy, ale pozwala zmieniać jej zawartość. Same niezmienne wartości pól nie zabezpieczą efektów ubocznych w zewnętrznych serwisach.

Rzeczywista niezmienność wymaga nieujawniania mutowalnego wnętrza. Kopiuję wejściowe Date, listy lub tablice oraz wyniki wymagające ochrony; dla list sama kopia kontenera nie wystarczy, gdy elementy też są mutowalne. Preferuję niezmienne typy czasu.

Konstruktor i accessor muszą razem chronić tablicę:

<!-- example:CON-08-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static final class Digest {
    private final byte[] bytes;
    Digest(byte[] bytes) { this.bytes = bytes.clone(); }
    byte[] bytes() { return bytes.clone(); }
}
~~~

Gdyby konstruktor zachował wejściową tablicę, caller mógłby zmienić obiekt po utworzeniu. Gdyby getter zwracał wnętrze, mógłby zrobić to później. Obie kopie mają koszt proporcjonalny do rozmiaru danych. Dla dużych buforów rozważam ownership transfer lub API tylko do odczytu z realnym kontraktem lifecycle, a nie deklarację final jako substytut izolacji.

## CON-09. Czym różnią się bezpieczna inicjalizacja i bezpieczna publikacja?

Inicjalizacja tworzy poprawny stan obiektu, publikacja udostępnia go innym wątkom z wymaganymi gwarancjami widoczności. Odczyt „tylko do czytania” również potrzebuje poprawnego przekazania danych. Sleep i upływ czasu nie ustanawiają happens-before.

**Błędny protokół jednorazowego komunikatu:** flaga jest zwykłym polem, więc obserwacja jej zmiany nie gwarantuje widoczności wcześniejszego zapisu message.

<!-- example:CON-09-1 kind:java-members-negative -->
**Kontekst:** Java 21 — kompilowalny kontrprzykład błędnej logiki; skrypt nie uruchamia tego kodu jako poprawnego rozwiązania.

~~~java
static final class UnsafePublication {
    private String message;
    private boolean ready;
    void publish() { message = "gotowe"; ready = true; }
    String read() { return ready ? message : "oczekuje"; }
}
~~~

**Poprawny protokół przy jednym publish i braku późniejszych mutacji:**

<!-- example:CON-09-2 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static final class SafePublication {
    private String message;
    private volatile boolean ready;
    void publish() { message = "gotowe"; ready = true; }
    String read() { return ready ? message : "oczekuje"; }
}
~~~

Odczyt volatile, który zobaczy ready=true z publish, ma gwarancję widoczności wcześniejszej inicjalizacji message. Nie jest to protokół wielokrotnych równoległych aktualizacji. Dla konfiguracji zmienianej wielokrotnie lepszy bywa niezmienny snapshot podmieniany przez volatile/CAS. Kompilacja błędnego przykładu nie wykryje data race; brak obserwowanego błędu w stress teście także nie stanowi dowodu poprawności JMM.

<!-- follow-up:CON-09 -->
**Sprawdź rozumienie:** Czy flaga volatile z przykładu pozwala dowolnie zmieniać message także po pierwszym publish?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Pokazany protokół jest jednorazowy. Późniejsze mutacje wymagają osobnego uporządkowania; dla kolejnych konfiguracji stosuje się spójny snapshot albo wersjonowane przejście stanu.

</details>

## CON-10. Dlaczego ucieczka `this` i wywołanie metody nadpisywalnej w konstruktorze są groźne?

Rejestracja listenera lub start wątku z `this` może udostępnić obiekt przed zakończeniem konstruktora. Callback widzi stan częściowy; podklasa wywołana przez metodę nadpisaną może nie mieć jeszcze zainicjalizowanych pól. To podważa założenia bezpiecznej konstrukcji.

Oddzielam utworzenie obiektu od rejestracji/uruchomienia i publikuję go dopiero po gotowości. Nie wywołuję obcego kodu z konstruktora. Problem nie znika przez ustawienie pól jako final, jeżeli referencja uciekła za wcześnie.

Niebezpieczna konstrukcja ma często pośrednią postać: konstruktor wywołuje register(listener), a rejestr natychmiast odtwarza ostatnie zdarzenie. Callback odwołuje się do this, chociaż autor nie uruchomił jawnie nowego wątku. Metoda nadpisana może też odczytać pola podklasy przed ich inicjalizacją.

Bezpieczniej utworzyć kompletny obiekt, a dopiero potem wywołać start/register na warstwie koordynującej. Trzeba jednak zapewnić, że klient nie użyje obiektu przed startem, albo uczynić stan gotowości częścią kontraktu. Testuję natychmiastowy callback, a nie wyłącznie „normalne” opóźnione powiadomienie.

## CON-11. Jak bezpiecznie implementować leniwą inicjalizację singletona?

Synchronizowana metoda jest prosta, lecz synchronizuje każdy dostęp. Double-checked locking wymaga volatile na referencji i poprawnego drugiego sprawdzenia pod blokadą. Holder idiom opiera się na synchronizacji inicjalizacji klasy przez JVM i jest dobry dla prostego statycznego singletona.

Samo statyczne pole nie wystarczy, gdy jest później mutowane bez ochrony. Holder nie rozwiązuje lifecycle zasobów, zależności ani testowej izolacji. Statyczny cache może być bezpiecznie publikowany, a nadal tworzyć bottleneck przez globalną blokadę, hot key lub retencję danych. W Springu zwykle korzystam z kontenera zamiast budować drugi mechanizm singletonów.

Holder przenosi inicjalizację do klasy pomocniczej:

<!-- example:CON-11-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static final class Registry {
    private Registry() {}
    private static final class Holder {
        static final Registry INSTANCE = new Registry();
    }
    static Registry instance() { return Holder.INSTANCE; }
}
~~~

JVM synchronizuje inicjalizację Holder przy pierwszym aktywnym użyciu. Jeśli konstruktor rzuci wyjątek, nie jest to automatyczna pętla ponawiania jak w cache; inicjalizacja klasy ma własne reguły błędów. Dla połączenia wymagającego reconnect i close singleton może być złą abstrakcją: potrzebny jest jawny lifecycle zasobu oraz kontrolowane odzyskiwanie.

## CON-12. Dlaczego dwie synchronizowane metody nadal mogą mieć race condition?

Wywołanie pierwszej i drugiej metody zwalnia blokadę między nimi. Dodatkowo mogą używać różnych monitorów: innych instancji, `Class` dla metody statycznej albo różnych wrapperów. Callback może ponownie wejść do kodu w stanie przejściowym, mimo że blokada jest reentrant.

Przenoszę niezmiennik do jednej operacji i ustalam wspólną blokadę. Unikam wywoływania obcego kodu pod blokadą, a callbacki uruchamiam po przejściu do spójnego stanu. Logowanie samych wejść do metod nie pokazuje wszystkich przeplotów.

Dwie metody synchronized na dwóch różnych instancjach nie współdzielą monitora. Dwie synchronizowane metody tej samej instancji chronią z kolei każdą wizytę osobno: klient może wykonać available(), zwolnić monitor i dopiero potem reserve(), gdy zasobu już brak.

Dlatego projektuję reserveIfAvailable jako jedną metodę walidującą i zmieniającą stan. Przed wywołaniem callbacka przywracam spójność albo zbieram zdarzenie do wysłania po wyjściu z sekcji. Reentrancy zapobiega zakleszczeniu na tym samym locku tego samego wątku, lecz nie zapobiega logicznemu wyścigowi z ponownym wejściem.

## CON-13. Czym jest deadlock i jak go wykryć oraz usunąć?

Cykl oczekiwania może powstać, gdy wątki trzymają zasoby i czekają na kolejne bez możliwości odebrania. Pobieram serię thread dumpów, szukam właścicieli monitorów i cyklu; analizuję również blokady bazy i oczekiwanie na Future.

Zapobiegam przez stałą kolejność blokad, krótkie sekcje i brak zdalnego I/O pod blokadą. ReentrantLock pozwala ponownie wejść temu samemu wątkowi, ale obcy callback może wprowadzić drugą blokadę lub oczekiwanie na inny wątek. Restart łagodzi incydent, nie usuwa przyczyny.

Dla transferu między kontami kolejność blokowania można ustalić po niezmiennym ID: zawsze niższe, potem wyższe. Wszystkie ścieżki, także administracyjne i kompensacyjne, muszą stosować tę samą zasadę. W przeciwnym razie „naprawiony” główny endpoint nadal może uczestniczyć w cyklu.

Kilka dumpów pozwala sprawdzić brak postępu; pojedynczy BLOCKED nie oznacza deadlocka. Przy bazie szukam transakcji i kolejności SQL, bo monitor JVM nie pokaże wszystkich relacji. Timeout zdobycia locka ogranicza oczekiwanie, ale kod musi wycofać częściową zmianę i bezpiecznie ponowić całą operację.

## CON-14. Czym różnią się `wait()`, `sleep()` i `join()`?

`wait` wymaga posiadania monitora, zwalnia go na czas oczekiwania i wymaga ponownego sprawdzenia warunku w pętli. `sleep` usypia bieżący wątek, nie zwalniając posiadanych monitorów. `join` czeka na zakończenie innego wątku i zapewnia odpowiednie gwarancje widoczności.

Sleep pod blokadą zwiększa contention, a join przy trzymaniu zasobu potrzebnego kończonemu wątkowi może stworzyć deadlock. Nie używam sleep do koordynacji testów; wybieram latch, barierę lub jawny warunek.

Wzorzec oczekiwania na warunek obejmuje ponowne sprawdzenie:

<!-- example:CON-14-1 kind:java-sketch -->
**Kontekst:** Szkic Java — wymaga wskazanych pól lub metod otoczenia; poza automatyczną kompilacją.

~~~java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    // korzystamy ze stanu chronionego tym samym monitorem
}
~~~

To szkic z polami monitor i ready. Pętla obsługuje zarówno spurious wakeup, jak i sytuację, gdy inny wątek zużył warunek przed odzyskaniem monitora. Notify nie przekazuje automatycznie prawa do konkretnego zasobu. InterruptedException wymaga świadomej polityki zakończenia; połknięcie go może uniemożliwić shutdown zadania. Dla typowych kolejek lepiej użyć gotowego BlockingQueue.

## CON-15. Jak działa problem producent–konsument i kiedy użyć BlockingQueue?

Producent przekazuje pracę, konsument ją odbiera. Bounded BlockingQueue ogranicza liczbę oczekujących zadań: `put` może czekać na miejsce, `take` na element. W API częściej potrzebne są timeouty albo `offer` i jawne odrzucenie niż nieograniczone czekanie.

ConcurrentLinkedQueue jest nieblokująca i nieograniczona; nie zapewnia naturalnego backpressure ani oczekiwania na element. Jest dobra przy krótkim, kontrolowanym przekazaniu pracy, ale aktywne odpytywanie pustej kolejki może marnować CPU. Trzeba określić zakończenie i obsługę błędu konsumenta.

Pojemność kolejki określam przez akceptowany czas oczekiwania i możliwości konsumenta, nie liczbę „wyglądającą bezpiecznie”. Przy produkcji 1000/s i obsłudze 800/s backlog rośnie o około 200/s, więc każda skończona kolejka zapełni się bez zmiany warunków.

<!-- example:CON-15-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
var queue = new java.util.concurrent.ArrayBlockingQueue<String>(100);
boolean accepted = queue.offer("job");
~~~

False wymaga reakcji: odmowy, odroczenia w trwałym magazynie lub degradacji. Nie zwracam sukcesu po utracie zadania. Zamknięcie obejmuje koniec produkcji, opróżnienie albo odrzucenie oraz obsługę konsumenta, który przestał działać.

## CON-16. Jak Semaphore ogranicza dostęp do zasobu i co przy zerowej liczbie pozwoleń?

`acquire` czeka na pozwolenie, a `tryAcquire` może odmówić od razu albo po czasie. Zero pozwoleń nie oznacza błędu semafora, tylko brak wolnej przepustowości. Po udanym pobraniu zwalniam dokładnie jedno pozwolenie w `finally`.

Semafor może ograniczyć wywołania do downstreamu, lecz sam nie tworzy połączeń DB ani nie zastępuje puli. Limit musi uwzględniać wszystkie instancje; lokalny limit 50 przy 20 podach dopuszcza 1000 operacji. Ograniczam również liczbę oczekujących.

Pozwolenie zwalniam tylko po jego rzeczywistym pobraniu:

<!-- example:CON-16-1 kind:java-sketch -->
**Kontekst:** Szkic Java — wymaga wskazanych pól lub metod otoczenia; poza automatyczną kompilacją.

~~~java
if (!permits.tryAcquire()) {
    throw new java.util.concurrent.RejectedExecutionException();
}
try {
    callDownstream();
} finally {
    permits.release();
}
~~~

To szkic z semaforem permits i domenowym wywołaniem. Bezwarunkowe release w catch po nieudanym acquire sztucznie zwiększa dostępny limit. Semafor nie wymaga tego samego właściciela jak ReentrantLock, dlatego łatwo pomylić bilans. Mierzę liczbę aktywnych operacji i odrzuceń; tysiące czekających na pozwolenie nadal zajmuje pamięć oraz budżet czasu.

## CON-17. Jak ThreadLocal powoduje wyciek pamięci i mieszanie danych żądań?

Wątek puli żyje dłużej od żądania. Wartość może pozostać w jego mapie, zatrzymać duży graf obiektów lub zostać odczytana przez kolejnego użytkownika. Słabe referencje kluczy nie oznaczają natychmiastowego usunięcia silnie trzymanej wartości.

Ustawienie kontekstu otaczam `try/finally` z `remove`. Nie przenoszę przypadkowo EntityManager ani danych użytkownika do innych zadań. Przy virtual threads problemem jest też koszt jednej wartości na każdy liczny wątek, nawet bez wielokrotnego użycia wątku.

Bezpieczny zakres ustawienia kontekstu powinien przywrócić stan również po wyjątku. Jeśli kontekst może być zagnieżdżany, samo remove może nie wystarczyć: trzeba odtworzyć poprzednią wartość. Framework kontekstowy zwykle oferuje w tym celu scope.

ThreadLocalMap nie jest „globalną mapą per zmienna”, tylko strukturą powiązaną z wątkiem. Długowieczny wątek może utrzymywać wartość po zniknięciu klucza do czasu sprzątania odpowiednich wpisów. W heap dumpie szukam więc ścieżki od Thread do wartości i dalej do dużego grafu, a nie wyłącznie liczby aktywnych ThreadLocal.

## CON-18. Czym jest thread affinity i jak propagować kontekst przez CompletableFuture?

Nie można zakładać, że kolejne etapy async wykonają się na tym samym wątku. ThreadLocal nie przechodzi automatycznie do dowolnego executora. W virtual thread jego tożsamość Java pozostaje stała, chociaż carrier może się zmieniać; to inny poziom niż przełączenie zadania między wątkami Java.

Przechwytuję minimalny niezmienny kontekst i jawnie przekazuję albo używam instrumentowanego executora/context propagation. Kontekst instaluję na czas zadania i zawsze przywracam poprzedni. Nie przenoszę transakcji przez kopiowanie ThreadLocal.

Przekazanie jawnego kontekstu jest często łatwiejsze do przeanalizowania niż ukryty stan:

<!-- example:CON-18-1 kind:java-statements -->
**Kontekst:** Java 21 — instrukcje ciała metody; kompilowane przez skrypt w metodzie pomocniczej.

~~~java
record RequestContext(String requestId, long tenantId) {}
// RequestContext captured = context;
// executor.submit(() -> handle(captured, command));
~~~

To szkic granicy wykonania. Obiekt jest niezmienny, a handler dostaje tylko potrzebne dane; nie przechwytuje servlet requestu, sesji JPA ani całego kontrolera. Dla tracingu nadal można używać instrumentacji, ale trzeba sprawdzić executory i callbacki biblioteki. W testach uruchamiam następujące po sobie zadania różnych tenantów, aby wykryć pozostawiony kontekst.

## CON-19. Jakie gwarancje widoczności daje ExecutorService?

Działania przed przekazaniem zadania happen-before jego wykonaniem; działania zadania happen-before odczytem wyniku przez `Future.get`. To pozwala bezpiecznie przekazać przygotowane dane. Nie obejmuje dowolnych późniejszych równoległych modyfikacji tych samych obiektów.

Używam niezmiennych argumentów lub snapshotów. Jeżeli po submit zmieniam listę, zadanie i zgłaszający nadal mogą mieć wyścig. Publikacja przez executor nie jest też transakcją obejmującą bazę, inny task i zewnętrzne API.

Publikacja przez executor obejmuje stan przygotowany przed submit. Jeśli po nim caller zmieni ten sam ArrayList, zadanie może czytać w trakcie modyfikacji. Future.get dotyczy natomiast działań konkretnego zakończonego taska, nie wszystkich innych wątków utworzonych wewnątrz bez koordynacji.

Dobry test przekazuje snapshot, kończy task i sprawdza wynik po get zamiast po sleep. Przy wykonaniu wielu zadań należy poczekać na wszystkie wymagane wyniki i obsłużyć wyjątki każdego z nich. Widoczność pamięci nie oznacza jeszcze, że wynik kilku niezależnych tasków reprezentuje jeden atomowy stan biznesowy.

## CON-20. Jak udowodnić przyczynę sporadycznie błędnych danych przy dużej współbieżności?

Zapisuję niezmiennik i historię operacji z ID obiektu, wersją, początkiem/końcem i wynikiem. Odtwarzam przeplot przez bariery, sprawdzam publikację, wszystkie ścieżki zapisu, callbacki, cache i granice transakcji. Thread dump pokaże oczekiwanie, ale nie dowodzi braku data race.

Dla prostego problemu pamięciowego przydaje się jcstress, dla reguł domeny test modelowy/historii. Lost update mimo dwóch HTTP 200 weryfikuję również w SQL i `@Version`. Brak reprodukcji w tysiącu prób nie jest dowodem poprawności.

Dla znikającej aktualizacji zamówienia zapisuję oczekiwaną wersję wejściową i wersję zatwierdzoną przez DB. Gdy oba requesty pracowały na wersji 7 i obie odpowiedzi są sukcesem, sprawdzam, czy SQL miał warunek version=7 i czy wynik aktualizacji został skontrolowany. To szybciej zawęża problem niż samo zwiększanie obciążenia.

Dla podejrzenia JMM buduję minimalny test odtwarzający publikację, a dla cache porównuję wersję odpowiedzi z wersją źródła prawdy. Logi mogą zmieniać timing i przypadkowo maskować wyścig. Dowód obejmuje niezmiennik, konkretny przeplot oraz zabezpieczenie, które ten przeplot uniemożliwia.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [Widoczność pamięci i happens-before](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/visibility/README.md)
- [Race condition i utrata aktualizacji](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/race_condition/README.md)
- [Deterministyczne testowanie współbieżności](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_a/testing/README.md)
<!-- labs:end -->
