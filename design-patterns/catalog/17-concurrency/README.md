# Wzorce współbieżności w kodzie

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#java)

Wzorce aktywnych obiektów i współdzielenia stanu. **9 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [pula zasobów, Active Object i Single-flight](../../labs/05-concurrency.md).
Studium w Markdown rozwija wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Współbieżność nie polega tylko na wyborze locka. Najpierw ustal właściciela
mutowalnego stanu, granicę operacji, sposób oczekiwania i lifecycle pracy. Jedne wzorce usuwają
współdzielenie, inne dopuszczają konflikt i ponowienie, jeszcze inne organizują zadania. Szkice nie dowodzą
bezpieczeństwa dowolnego callbacku: wyniesiona referencja albo ukryte I/O może złamać założenia poprawnej
struktury.

## Spis kart

- [Active Object / Actor / Mailbox](#active-object--actor--mailbox)
- [Balking](#balking)
- [Read–Write Lock / Read-mostly State](#readwrite-lock--read-mostly-state)
- [Copy-on-Write / Snapshot Publication](#copy-on-write--snapshot-publication)
- [Double Buffer / Versioned Snapshot Swap](#double-buffer--versioned-snapshot-swap)
- [Lock Striping / Partitioned Ownership](#lock-striping--partitioned-ownership)
- [CAS Loop / Optimistic Concurrency](#cas-loop--optimistic-concurrency)
- [Fork–Join / Work Stealing](#forkjoin--work-stealing)
- [Structured Concurrency / Scoped Task Lifetime](#structured-concurrency--scoped-task-lifetime)

## Active Object / Actor / Mailbox

**Intuicja:** Wywołujący może chcieć zlecić zmianę, nie wykonując jej na swoim wątku. Active Object oddziela
moment wywołania od wykonania przez kolejkę i własnego wykonawcę. Actor opiera współpracę na wiadomościach i
określonym modelu izolacji; te koncepcje są pokrewne, ale nie są identycznym frameworkiem ani kontraktem.

**Problem i mechanizm:** Obiekt przetwarza polecenia przez własną kolejkę, oddzielając wywołanie od wykonania
i serializując zmiany stanu.

**Role i współpraca:** Mailbox ogranicza backlog, worker posiada stan, komenda opisuje zmianę, Future przenosi
wynik. Serializacja działa tylko wtedy, gdy mutowalne referencje nie uciekają poza właściciela.

**Przykład:** Jednowątkowy executor konta wykonuje Deposit i Withdraw z mailbox; klient dostaje Future wyniku.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
var mailbox = new BoundedMailbox<>(100, ArrayList<String>::new);
CompletableFuture<Integer> added = mailbox.submit(state -> {
    state.add("task");
    return state.size();
});
// Oddajemy liczbę, nie mutowalną listę należącą do workera.
```

**Konkretny przypadek użycia:** Szeregowanie zmian stanu gracza

- **Wejście:** Dwie sesje wysyłają move i spend dla jednego gracza.
- **Przebieg:** Mailbox przekazuje komunikaty jednemu właścicielowi stanu; actor stosuje je kolejno.
- **Oczekiwany wynik:** Operacje nie modyfikują jednocześnie salda i pozycji przez współdzielone referencje.
- **Błąd lub granica:** Przepełnienie mailbox wymaga jawnej polityki; restart aktora nie daje trwałości
  wiadomości bez osobnego mechanizmu.

**Kiedy zastosować:** Dla sekwencyjnych zmian jednego zasobu i asynchronicznego wejścia. Nieblokujące submit
nie oznacza nieograniczonej pojemności ani anulowania efektu przez cancel Future.

**Koszt i pułapka:** Active Object i Actor to pokrewne modele, nie identyczne API; mailbox wymaga pojemności,
obsługi błędów i shutdown.

**Rozwinięcie i ślad wykonania:** BoundedMailbox tworzy stan na swoim workerze i przekazuje go kolejnym
komendom w FIFO. Future wyniku oddziela czas wywołania od wykonania. Pełna kolejka daje obserwowalne
odrzucenie, a wyjątek komendy nie blokuje następnej. Klient nie może zachować i mutować otrzymanej referencji
stanu poza workerem.

**Ćwiczenie sprawdzające:** Określ wynik anulowania klienta po pobraniu komendy z kolejki.

## Balking

**Intuicja:** Nie każda operacja powinna czekać na możliwość wykonania. Jeśli eksport już trwa, następne
żądanie może natychmiast otrzymać AlreadyRunning. Balking podejmuje taką decyzję na podstawie stanu i od razu
odmawia, zamiast blokować albo kolejkując tworzyć drugi identyczny proces.

**Problem i mechanizm:** Operacja nie czeka na warunek, tylko od razu odmawia działania, gdy stan nie pozwala
jej wykonać.

**Role i współpraca:** Guard i zmiana stanu muszą być jedną atomową operacją. Właściciel pracy przywraca
gotowość po zakończeniu, również gdy wykonanie kończy się błędem.

**Przykład:** startExport() zwraca AlreadyRunning, jeśli eksport już trwa; nie uruchamia drugiego i nie
blokuje wątku.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (!running.compareAndSet(false, true)) {
    return ALREADY_RUNNING;
}
try { return exportNow(); }
finally { running.set(false); }
```

**Konkretny przypadek użycia:** Pominięcie równoległego odświeżenia

- **Wejście:** Odświeżanie indeksu już trwa, przychodzi drugie żądanie refresh.
- **Przebieg:** Atomowe sprawdzenie stanu odmawia drugiego uruchomienia, pierwsze czyści stan w finally.
- **Oczekiwany wynik:** W danym momencie pracuje jeden refresh, a klient dostaje wynik skipped albo
  already-running.
- **Błąd lub granica:** Pominięcie nie nadaje się do operacji, która musi zostać wykonana dla każdego żądania;
  stan trzeba zmieniać bez wyścigu.

**Kiedy zastosować:** Gdy ponowne rozpoczęcie jest bezcelowe lub niedozwolone. Jeśli klient ma legalnie
poczekać na warunek, Guarded Suspension opisuje inną politykę.

**Koszt i pułapka:** Sprawdzenie stanu i jego zmiana muszą być atomowe; inaczej dwa wątki jednocześnie przejdą
guard.

**Rozwinięcie i ślad wykonania:** Warunek READY i przejście do RUNNING muszą być jedną atomową decyzją. Dwa
niezależne if i set pozwolą wystartować dwa eksporty. Po zakończeniu trzeba przywrócić gotowość zgodnie z
kontraktem również po błędzie; samo odrzucenie drugiego startu nie rozwiązuje lifecycle pierwszego.

**Ćwiczenie sprawdzające:** Zaprojektuj compare-and-set READY→RUNNING i test dwóch startów.

## Read–Write Lock / Read-mostly State

**Intuicja:** Wielu czytelników może bezpiecznie obserwować ten sam stan, o ile żaden w tym czasie go nie
zmienia. Read–Write Lock dopuszcza równoległe odczyty i wyłączny zapis. Jest użyteczny tylko wtedy, gdy
chroniony zakres faktycznie obejmuje wszystkie zależne pola i koszt blokady się opłaca.

**Problem i mechanizm:** Wielu czytelników może wejść równolegle, ale zapis wymaga wyłączności na chronionym
stanie.

**Role i współpraca:** Read lock chroni odczyt, write lock zmianę, finally zwalnia prawo. Zwrócenie żywego
mutowalnego obiektu może wynieść dalsze użycie poza ochronę.

**Przykład:** Konfiguracja ma readLock przy pobraniu snapshotu i writeLock przy zastąpieniu indeksu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
lock.readLock().lock();
try {
    return new View(state.first(), state.second());
} finally {
    lock.readLock().unlock();
}
```

**Konkretny przypadek użycia:** Współdzielona mapa konfiguracji

- **Wejście:** Tysiąc odczytów przypada na jedną aktualizację kilku powiązanych wartości.
- **Przebieg:** Read lock pozwala na równoległe odczyty, write lock obejmuje całą spójną zmianę.
- **Oczekiwany wynik:** Czytelnik widzi zgodny zestaw wartości, bez połowy aktualizacji.
- **Błąd lub granica:** Przekazanie mutowalnej referencji poza blokadę łamie ochronę; częste zapisy mogą
  zniwelować korzyść.

**Kiedy zastosować:** Dla dłuższych odczytów i rzadkich zmian. Dla krótkiego dostępu lub niezmiennego
snapshotu prostszy monitor czy volatile może być lepszy.

**Koszt i pułapka:** Lock bywa droższy niż krótki synchronized; upgrade read→write i zagłodzenie wymagają
ostrożności.

**Rozwinięcie i ślad wykonania:** Read lock chroni cały odczyt zależnych pól, a nie tylko pobranie jednej
referencji. Jeśli wynik jest niezmiennym snapshotem, można opublikować go przez volatile i skrócić sekcję
krytyczną. Nigdy nie zakładaj, że read lock pozwala bezpiecznie modyfikować obiekt zwrócony czytelnikowi.

**Ćwiczenie sprawdzające:** Porównaj rozwiązanie z niezmiennym snapshotem publikowanym przez volatile.

## Copy-on-Write / Snapshot Publication

**Intuicja:** Czytelnik iterujący po liście może zostać zaskoczony jej zmianą przez inny kod. Copy-on-Write
tworzy nowy kontener przy zmianie, a dotychczasowy czytelnik kończy na starym snapshotcie. Zamienia częstsze i
tańsze odczyty na droższe zapisy i większą liczbę kopii.

**Problem i mechanizm:** Zmiana tworzy nową wersję struktury, a czytelnicy widzą spójny wcześniejszy snapshot.

**Role i współpraca:** Writer publikuje nową strukturę, reader zachowuje starą, polityka określa moment
widoczności. Nie oznacza to automatycznie niezmienności samych elementów.

**Przykład:** Lista listenerów jest kopiowana przy rejestracji; iteracja po snapshotcie nie widzi nowego
listenera w połowie przebiegu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
for (Listener listener : listeners) {
    listener.onEvent(event);
}
// Iteracja widzi swój snapshot składu, nie późniejsze rejestracje.
```

**Konkretny przypadek użycia:** Lista listenerów zmieniana rzadko

- **Wejście:** Emitowanych jest 10000 zdarzeń, w tym czasie dodano jednego listenera.
- **Przebieg:** Writer tworzy nową niezmienną kopię listy i publikuje ją atomowo.
- **Oczekiwany wynik:** Bieżąca iteracja kończy się na swoim snapshot, następna widzi nową listę.
- **Błąd lub granica:** Duża lista przy częstych zapisach powoduje koszt kopiowania i GC; elementy snapshot
  też muszą mieć właściwy kontrakt.

**Kiedy zastosować:** Dla struktur często czytanych i rzadko zmienianych, np. list listenerów. Przy częstych
dużych aktualizacjach kopiowanie może dominować koszt.

**Koszt i pułapka:** Zapis kosztuje O(n) i alokacje; mutowalne elementy nadal mogą być współdzielone.

**Rozwinięcie i ślad wykonania:** Zmiana kopiuje strukturę i publikuje nową wersję; istniejący czytelnik
kończy na poprzedniej. To eliminuje modyfikację kontenera pod iteratorem, lecz nie mutowalnych elementów
wewnątrz. W kolekcji listenerów opisz, czy unsubscribe ma zatrzymać bieżący callback, czy dopiero przyszłe
publikacje.

**Ćwiczenie sprawdzające:** Określ zachowanie wypisania listenera w trakcie publikacji eventu.

## Double Buffer / Versioned Snapshot Swap

**Intuicja:** Aktualizacja wielu pól po kolei pozwoliłaby czytelnikowi zobaczyć mieszankę starej i nowej
konfiguracji. Double Buffer lub Snapshot Swap przygotowuje nową spójną wersję poza widokiem i publikuje ją
jednym przełączeniem. Trzeba jednak przejąć zmiany, które zaszły w źródle podczas budowy.

**Problem i mechanizm:** Nowy spójny stan powstaje poza widokiem czytelników i jest publikowany jednym
przełączeniem referencji.

**Role i współpraca:** Builder tworzy niezmienny snapshot, walidator go sprawdza, AtomicReference lub volatile
publikuje referencję. Czytelnik powinien pobrać ją raz dla spójnego odczytu.

**Przykład:** Nowy indeks produktów jest budowany w pamięci, walidowany i zastępuje AtomicReference starego
indeksu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Index next = builder.from(sourceSnapshot);
validator.check(next);
current.set(next);

Index visible = current.get();
return visible.lookup(query);
```

**Konkretny przypadek użycia:** Jednoczesna publikacja tablicy kursów

- **Wejście:** Dane dla EUR i USD muszą należeć do tej samej wersji.
- **Przebieg:** Writer buduje drugi bufor poza widokiem czytelników i zamienia referencję na gotowy snapshot.
- **Oczekiwany wynik:** Czytelnik odczytujący referencję raz widzi obie wartości z jednej wersji.
- **Błąd lub granica:** Nie wolno od razu nadpisywać starego bufora, jeśli czytelnicy nadal z niego
  korzystają; potrzebna jest kontrola jego ponownego użycia.

**Kiedy zastosować:** Dla indeksów i konfiguracji odświeżanych całościowo. Oceń pamięć dwóch wersji i zasady
zwalniania starej, jeśli zawiera zewnętrzne zasoby.

**Koszt i pułapka:** Chwilowo potrzeba pamięci na dwie wersje; przy współbieżnych zapisach potrzebny plan
przeniesienia zmian.

**Rozwinięcie i ślad wykonania:** Nowy indeks budowany jest obok aktualnego, a przełączenie powinno objąć
jedną spójną referencję. Zmiany danych podczas budowy wymagają logu przyrostowego, blokady albo kontrolowanego
punktu odcięcia. Bez tego atomowy swap publikuje spójny, ale już nieaktualny snapshot z utraconymi
aktualizacjami.

**Ćwiczenie sprawdzające:** Zaktualizuj produkt podczas budowy indeksu i wyjaśnij, jak nie zgubić zmiany.

## Lock Striping / Partitioned Ownership

**Intuicja:** Jeden lock dla całej mapy może blokować niezależne klucze, a lock per klucz ma koszt zarządzania
i pamięci. Lock Striping dzieli klucze między ograniczoną liczbę blokad. Dwa różne klucze mogą trafić na ten
sam pasek, więc kolizja ogranicza równoległość, ale nie musi psuć poprawności.

**Problem i mechanizm:** Wiele niezależnych kluczy dzieli się między ograniczoną liczbę locków lub
właścicieli.

**Role i współpraca:** Funkcja klucza wybiera pasek, lock chroni cały read-modify-write, operacja
wielokluczowa ustala stałą kolejność i usuwa powtórzone paski.

**Przykład:** hash(accountId) wybiera jeden z 64 locków; operacje jednego konta są serializowane.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
int stripe = Math.floorMod(key.hashCode(), locks.length);
Lock chosen = locks[stripe];
chosen.lock();
try { updateWholeInvariant(key); }
finally { chosen.unlock(); }
```

**Konkretny przypadek użycia:** Równoległe aktualizacje kont

- **Wejście:** Konta są przypisane do 64 pasków blokad przez stabilny hash id.
- **Przebieg:** Zmiana jednego konta blokuje jego pasek, przelew blokuje dwa paski w ustalonej kolejności.
- **Oczekiwany wynik:** Różne paski pracują równolegle, a stała kolejność ogranicza ryzyko deadlock.
- **Błąd lub granica:** Kolizje ograniczają równoległość, a rozszerzanie zestawu pasków wymaga planu migracji
  przypisania.

**Kiedy zastosować:** Dla wielu niezależnych zakresów stanu. Przy przeniesieniu między dwoma kluczami nie
zajmuj blokad w przypadkowej kolejności.

**Koszt i pułapka:** Kolizje zmniejszają równoległość; operacja na dwóch kluczach wymaga stałej kolejności
locków.

**Rozwinięcie i ślad wykonania:** Klucz mapuje się na pasek, więc dwa różne klucze mogą legalnie współdzielić
lock. Operacja na dwóch paskach powinna posortować ich numery i zablokować każdy najwyżej raz. Stała kolejność
eliminuje konkretną klasę deadlock, ale nadal trzeba ograniczyć czas pracy pod blokadą.

**Ćwiczenie sprawdzające:** Wykryj sytuację, gdy oba konta trafiają na ten sam pasek, i uniknij podwójnego
lockowania.

## CAS Loop / Optimistic Concurrency

**Intuicja:** Zamiast blokować wszystkich z góry, można obliczyć nowy stan i spróbować podmienić go tylko,
jeśli stary nadal jest aktualny. CAS Loop powtarza kalkulację po konflikcie. Dlatego funkcja aktualizacji nie
może bezwarunkowo wykonywać maila czy płatności, które powtórzą się razem z nieudaną próbą.

**Problem i mechanizm:** Wątek oblicza zmianę na snapshotcie i publikuje ją tylko, jeśli nikt wcześniej nie
zmienił wersji.

**Role i współpraca:** AtomicReference przechowuje snapshot, CAS porównuje i publikuje, pętla ponawia czyste
obliczenie. Wersja może być potrzebna do wykrycia ABA.

**Przykład:** AtomicReference.compareAndSet(old, new) w pętli powtarza czystą kalkulację po konflikcie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
while (true) {
    State before = current.get();
    State after = calculateWithoutSideEffects(before, command);
    if (current.compareAndSet(before, after)) {
        return after;
    }
}
```

**Konkretny przypadek użycia:** Licznik z konkurencyjnymi aktualizacjami

- **Wejście:** Dwa wątki odczytują 7 i oba chcą zwiększyć licznik.
- **Przebieg:** CAS pozwala jednemu zmienić 7 na 8; drugi ponawia obliczenie od aktualnej wartości.
- **Oczekiwany wynik:** Po dwóch udanych operacjach licznik ma 9.
- **Błąd lub granica:** Funkcja wyliczenia może wykonać się wiele razy, więc nie może przy każdej próbie
  wysyłać emaila; CAS nie gwarantuje braku głodzenia.

**Kiedy zastosować:** Dla krótkich aktualizacji z dobrze zdefiniowanym niezmiennym stanem. Lock-free nie
oznacza braku konfliktów ani gwarancji sprawiedliwości każdego wątku.

**Koszt i pułapka:** Funkcja aktualizująca może wykonać się wielokrotnie, więc nie powinna wysyłać maili. ABA
wymaga wersji lub odpowiedniej reprezentacji.

**Rozwinięcie i ślad wykonania:** CAS może nie powieść się po obliczeniu nowej wartości, więc funkcja
aktualizująca musi nadawać się do ponowienia bez dodatkowych skutków. Wyślij event dopiero po udanym przejęciu
odpowiedniego commitu, z osobnym mechanizmem trwałości. Wersja pomaga odróżnić powrót tej samej wartości w
scenariuszu ABA.

**Ćwiczenie sprawdzające:** Pokaż podwójny skutek uboczny w updateAndGet z nieczystą lambdą.

## Fork–Join / Work Stealing

**Intuicja:** Duże obliczenie można podzielić na mniejsze niezależne fragmenty i połączyć ich wyniki.
Fork–Join organizuje takie dzielenie, a Work Stealing pozwala workerowi przejąć oczekującą pracę od innego.
Sens zależy od kosztu fragmentu i tego, czy agregacja pozwala zmienić kolejność wykonania.

**Problem i mechanizm:** Problem dzieli się na podzadania, a workery przejmują pracę z kolejek innych
workerów.

**Role i współpraca:** Task dzieli zakres, worker wykonuje, join zbiera wynik. Próg przejścia do obliczenia
sekwencyjnego ogranicza narzut tworzenia bardzo małych zadań.

**Przykład:** Duża tablica jest dzielona na zakresy, lokalne sumy łączą się po join; próg ogranicza koszt
drobnych zadań.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
if (range.size() <= threshold) return sumSequentially(range);
SumTask left = new SumTask(range.left());
SumTask right = new SumTask(range.right());
left.fork();
long rightValue = right.compute();
return Math.addExact(left.join(), rightValue);
```

**Konkretny przypadek użycia:** Równoległe sumowanie dużej tablicy

- **Wejście:** Tablica ma milion liczb; zadanie CPU można dzielić na niezależne zakresy.
- **Przebieg:** Fork dzieli duże zakresy, małe liczy lokalnie, join łączy wyniki, work stealing wyrównuje
  obciążenie.
- **Oczekiwany wynik:** Wynik zgadza się z sekwencyjną sumą przy odpowiednim typie i regułach arytmetyki.
- **Błąd lub granica:** Zbyt małe zadania zwiększają narzut; blokujące I/O w tej samej puli może odebrać
  przepustowość pracy CPU.

**Kiedy zastosować:** Dla podzielnej pracy CPU. Blokujące I/O i nierówny koszt zakresów wymagają osobnej oceny
zamiast założenia, że więcej zadań zawsze przyspiesza.

**Koszt i pułapka:** Blokujące I/O w puli CPU może ograniczyć postęp; zbyt małe zadania zwiększają narzut.

**Rozwinięcie i ślad wykonania:** Podzadania dzielą zakres, a join zbiera wyniki. Wynik powinien być
niezależny od kolejności wykonania, o ile kontrakt agregacji na to pozwala. Koszt utworzenia zadań i
nierównowaga zakresów mogą zniwelować równoległość; oceniaj próg na pomiarze właściwego workloadu, nie na
samej liczbie rdzeni.

**Ćwiczenie sprawdzające:** Dobierz próg i porównaj z prostą pętlą bez deklaracji przyspieszenia bez pomiaru.

## Structured Concurrency / Scoped Task Lifetime

**Intuicja:** Fire-and-forget może zostawić pracę, której właściciel już nie istnieje. Structured Concurrency
wiąże zadania potomne z zakresem rodzica: start, oczekiwanie, propagacja błędu i koniec mają określony punkt.
Konkretny framework musi jeszcze określić politykę pierwszej awarii lub sukcesu i reakcję na anulowanie.

**Problem i mechanizm:** Zadania potomne mają zakres życia związany z rodzicem, który odpowiada za ich wynik,
anulowanie i zakończenie.

**Role i współpraca:** Scope jest właścicielem dzieci, rodzic odbiera ich wyniki przed wyjściem, cancellation
dociera według kontraktu. Blokujące API nadal musi współpracować.

**Przykład:** Pobranie profilu i uprawnień kończy się przed wyjściem z use case; błąd jednego zadania anuluje
pozostałe zgodnie z polityką.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
wejdź do zakresu zadań
    uruchom child: pobierz profil
    uruchom child: pobierz uprawnienia
    po błędzie zastosuj uzgodnioną politykę anulowania
    odbierz wyniki lub błąd
wyjdź dopiero zgodnie z kontraktem zakończenia dzieci
```

**Konkretny przypadek użycia:** Jedno żądanie i dwa odczyty zależne

- **Wejście:** Odpowiedź potrzebuje równolegle profilu i limitów, cały request ma deadline 200 ms.
- **Przebieg:** Zakres jest właścicielem obu zadań, łączy wyniki i przekazuje anulowanie po błędzie lub
  terminie.
- **Oczekiwany wynik:** Zadania nie powinny pozostawać osierocone po wyjściu z zakresu.
- **Błąd lub granica:** Przerwanie jest kooperacyjne; wywołanie ignorujące anulowanie nadal wymaga własnego
  timeout, a szczegóły API zależą od wersji Javy.

**Kiedy zastosować:** Dla równoległych podzadań jednego procesu, których wynik i czas życia kontroluje wspólny
rodzic. Pseudokod opisuje model niezależnie od konkretnej wersji API.

**Koszt i pułapka:** W JDK 21 StructuredTaskScope jest API preview. Przeniesienie modelu do konkretnego
projektu wymaga sprawdzenia wersji API, polityki błędu i współpracy operacji z anulowaniem.

**Rozwinięcie i ślad wykonania:** Zakres rodzica obejmuje utworzenie, oczekiwanie, propagację błędu i
zakończenie potomków. To ogranicza osierocone zadania, lecz blokujące operacje nadal muszą reagować na
anulowanie. Jeśli wywołanie I/O ignoruje przerwanie, zakres nie zamienia go automatycznie w operację
odwoływalną. Oddziel deadline rodzica, timeout zależności i zasady zamknięcia zakresu.

**Ćwiczenie sprawdzające:** Porównaj kontrolę zadań potomnych z fire-and-forget CompletableFuture.
