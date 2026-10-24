# Low Level Design

[Spis rozdziałów](README.md) · [Książki i źródła](MATERIALY.md#16-low-level-design)

Opisujemy model, odpowiedzialności i rdzeń rozwiązania z przykładami struktur, przejść i kodu. Fragmenty nie są kompletnymi aplikacjami; wyjaśniają konkretny niezmiennik i ograniczenia. SOLID oraz dobór Strategy/Factory/Observer opisano w [podstawach Javy](01-java.md), LRU/LFU i scheduler priorytetowy w [algorytmach](15-algorytmy.md), a rozproszoną odmianę limitera w [system design](13-system-design.md).

<!-- question-toc:start -->
## Spis pytań

- [LLD-01. Jak zaprojektować parking?](#lld-01-jak-zaprojektować-parking)
- [LLD-02. Jak zaprojektować bankomat z kontami, transakcjami i transferami?](#lld-02-jak-zaprojektować-bankomat-z-kontami-transakcjami-i-transferami)
- [LLD-03. Jak zaprojektować order book z kojarzeniem kupna i sprzedaży?](#lld-03-jak-zaprojektować-order-book-z-kojarzeniem-kupna-i-sprzedaży)
- [LLD-04. Jak zbudować limiter od zera i obronić strukturę danych?](#lld-04-jak-zbudować-limiter-od-zera-i-obronić-strukturę-danych)
- [LLD-05. Jak zaprojektować in-memory key-value store z TTL?](#lld-05-jak-zaprojektować-in-memory-key-value-store-z-ttl)
- [LLD-06. Jak zaprojektować rozliczanie wydatków w stylu Splitwise?](#lld-06-jak-zaprojektować-rozliczanie-wydatków-w-stylu-splitwise)
- [LLD-07. Jak zaprojektować framework logowania z poziomami i appenderami?](#lld-07-jak-zaprojektować-framework-logowania-z-poziomami-i-appenderami)
- [LLD-08. Jak uczynić projekt rozszerzalnym i poprawnym przy współbieżności?](#lld-08-jak-uczynić-projekt-rozszerzalnym-i-poprawnym-przy-współbieżności)
<!-- question-toc:end -->

## LLD-01. Jak zaprojektować parking?

Rozdzielam pojazd, typ miejsca, aktywny bilet i politykę ceny. Rdzeń utrzymuje wolne miejsca według zgodności typu oraz indeks bilet → zajęte miejsce. Przydział i aktualizacja obu indeksów muszą być jedną operacją; samo sprawdzenie available i późniejszy zapis ma wyścig.

Dla prostej polityki kolejki wolnych ID i mapy dają średnie O(1); „najbliższe miejsce” wymaga uporządkowanej struktury z kosztem O(log n). Wyjazd jest idempotentny, opłata liczona według jawnego czasu. Testuję pełny parking, podwójny wyjazd i dwa równoczesne przydziały.

Dane mogą obejmować mapę ticketId→Allocation i osobne kolejki wolnych miejsc według typu. Gdy większy pojazd może użyć tylko wybranych miejsc, polityka przydziału powinna chronić rzadki zasób, a nie brać dowolnego pierwszego ID. PricePolicy jest wymiennym zachowaniem, ale stan zajętości ma jednego właściciela.

<!-- example:LLD-01-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
allocate(vehicle) atomowo:
  wybierz zgodne wolne miejsce
  usuń je z indeksu wolnych
  zapisz bilet i allocation
~~~

Jeżeli zapis biletu zawiedzie, miejsce musi wrócić do indeksu albo cała operacja nie może być widoczna. Test dwóch jednoczesnych przydziałów sprawdza brak wspólnego spotId.

## LLD-02. Jak zaprojektować bankomat z kontami, transakcjami i transferami?

Oddzielam sesję/stan urządzenia, autoryzację, serwis kont, kasety banknotów i algorytm doboru nominałów. Wydanie gotówki to sekwencja rezerwacji środków, potwierdzenia urządzenia i rozliczenia. Dobór nominałów z ograniczonym stanem kaset może wymagać bounded change DP, a nie greedy.

W wersji in-memory transfer blokuje konta w stałej kolejności lub przechodzi przez jednego właściciela stanu. Błąd urządzenia po fizycznym wydaniu jest wynikiem niepewnym wymagającym journal/reconciliation, nie prostego retry. Pełny system bankowy to osobny poziom od tego ćwiczenia LLD.

Urządzenie i system kont mają różne źródła prawdy: bank zna księgowanie, bankomat wie o potwierdzonym wydaniu banknotów. Timeout czujnika nie dowodzi, że gotówka nie wyszła. Journal operacji wiąże te dwa światy stabilnym ID.

Dla kaset z nominałami 10,20,50 greedy może zawieść przy ograniczonej dostępności; trzeba znaleźć wykonalny zestaw, nie tylko minimalną liczbę w abstrakcyjnym nieskończonym zapasie. Model stanu odróżnia wybór nominałów, zarezerwowanie ich, polecenie wydania i potwierdzenie. Retry fizycznego wydania nie może być domyślną reakcją na każdy błąd komunikacji.

## LLD-03. Jak zaprojektować order book z kojarzeniem kupna i sprzedaży?

Utrzymuję uporządkowane poziomy ceny: najlepszy bid malejąco, ask rosnąco, a w każdym FIFO według czasu. TreeMap lokalizuje poziom, lista dwukierunkowa i indeks order ID → węzeł umożliwiają szybkie anulowanie. Matching działa, dopóki ceny się przecinają i ilości są dodatnie.

Dodanie poziomu kosztuje O(log p), usunięcie znanego zlecenia O(1) plus ewentualne usunięcie pustego poziomu. Trade obejmuje atomową zmianę obu stron i częściowe wykonanie. Jeden wątek właścicielski upraszcza kolejność; pieniądze/ceny mają ustaloną skalę bez double.

Dwa poziomy ceny zawierają FIFO zleceń, a indeks orderId umożliwia szybkie dotarcie do węzła. Sam TreeMap<Price, Queue<Order>> nie daje O(1) anulowania dowolnego elementu ArrayDeque; potrzebna jest lista z uchwytem albo świadome O(n) wyszukiwania.

Przykład: ask 100×5 i 101×10, nowe buy limit 101×8 wykonuje 5 po pierwszej cenie i 3 po drugiej zgodnie z ustaloną regułą ceny. Zostaje ask 101×7. Partial fill aktualizuje ilość bez zmiany priorytetu pozostałej części, jeśli tak mówi kontrakt. Jeden event loop upraszcza linearyzację i deterministyczny replay.

## LLD-04. Jak zbudować limiter od zera i obronić strukturę danych?

Dla token bucket przechowuję liczbę tokenów oraz ostatni czas uzupełnienia. Przy próbie atomowo obliczam przyrost z czasu monotonicznego, ograniczam do pojemności i zużywam koszt operacji. Każdy klucz ma stan, więc potrzebna jest polityka sprzątania nieaktywnych klientów.

Decyzja jest O(1), pamięć O(aktywnych kluczy). Chronię overflow, cofnięcie zegara i dwie równoczesne próby. Sliding window z deque daje inną semantykę i koszt pamięci zależny od liczby zdarzeń. Wybór struktury wynika z dopuszczalnego burst, nie z popularności wzorca.

Jednowątkowy protokół token bucket można ochronić jednym monitorem; poniżej szkic rdzenia z jawnym zegarem i jednostką tokenów:

<!-- example:LLD-04-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static final class Bucket {
    private final double capacity, tokensPerSecond;
    private double tokens;
    private long last;
    Bucket(int capacity, double tokensPerSecond, long now) {
        if (capacity <= 0 || !Double.isFinite(tokensPerSecond) || tokensPerSecond <= 0)
            throw new IllegalArgumentException();
        this.capacity = capacity;
        this.tokensPerSecond = tokensPerSecond;
        this.tokens = capacity;
        this.last = now;
    }
    synchronized boolean allow(long now) {
        long elapsed = now - last;
        if (elapsed < 0) throw new IllegalArgumentException("Zegar cofnął się");
        tokens = Math.min(capacity, tokens + elapsed / 1_000_000_000.0 * tokensPerSecond);
        last = now;
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }
}
~~~

Szkic zakłada monotoniczny czas i ograniczony zakres odstępów; double nie nadaje się bez analizy do rozliczeń pieniężnych.

<!-- follow-up:LLD-04 -->
**Sprawdź rozumienie:** Limiter dopuszcza 100 operacji/s, a każda trwa 5 s. Czy chroni limit 20 jednoczesnych wywołań?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Tempo i concurrency są innymi wielkościami. W stabilnych warunkach taki napływ oznaczałby około 500 operacji w toku. Potrzebny jest osobny bulkhead/semafor i limit oczekujących.

</details>

## LLD-05. Jak zaprojektować in-memory key-value store z TTL?

Mapa trzyma wartość, termin i wersję wpisu. Get sprawdza ważność, a min-heap terminów lub timing wheel wspiera aktywne sprzątanie. Heap może zawierać stare wpisy po update: usunięcie porównuje wersję, żeby nie skasować nowej wartości pod tym samym kluczem.

Get jest średnio O(1), put z heapem O(log n), pamięć zależy też od liczby nieusuniętych rekordów wygaśnięcia. Ograniczam jej narastanie przez kompakcję lub aktualizowalny indeks. TTL nie zastępuje limitu rozmiaru, a restart usuwa cały stan zgodnie z kontraktem in-memory.

Każdy wpis heapu expiry powinien zawierać klucz i generację. Aktualizacja klucza K z TTL=5 s na TTL=60 s zostawia starszy rekord sprzątania; po pięciu sekundach nie wolno usunąć nowej wartości.

<!-- example:LLD-05-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
map[K] = (value, expiresAt, version=2)
heap item = (K, oldExpiresAt, version=1)
cleanup: usuń tylko, gdy wersja nadal odpowiada map[K]
~~~

Lazy expiration w get zapewnia logiczną ważność nawet wtedy, gdy cleanup worker się opóźnia. Heap może rosnąć przy częstych odświeżeniach jednego klucza, dlatego liczę również stare rekordy i planuję compaction. Test korzysta ze sterowanego czasu, bez Thread.sleep.

<!-- follow-up:LLD-05 -->
**Sprawdź rozumienie:** Stary wpis heapu ma version=1, a mapa zawiera już version=2. Co zrobi cleanup?

<details>
<summary>Kierunek odpowiedzi</summary>

Pominie nieaktualny rekord zamiast usunąć nową wartość. Konieczne jest też ograniczenie wzrostu starych rekordów heapu. Lazy get nadal sam sprawdza ważność.

</details>

## LLD-06. Jak zaprojektować rozliczanie wydatków w stylu Splitwise?

Modeluję użytkownika, grupę, wydatek, udziały i settlement. Dla wydatku suma udziałów jest równa kwocie, a aktualizacja sald bilansuje się do zera w danej walucie. Podział reszty jednostek minimalnych ma deterministyczną regułę.

Indeks sald per użytkownik/para przyspiesza odczyt kosztem aktualizacji. Korekta wydatku powinna odwrócić poprzedni wpływ i zastosować nowy atomowo. Uproszczenie liczby przelewów może zmienić wierzycieli i nie zawsze daje minimum globalne; określam dozwoloną semantykę oraz złożoność algorytmu.

Dla wydatku 100 jednostek minimalnych dzielonego równo na trzy osoby wynikiem nie są trzy nieskończone ułamki. Można przydzielić 34,33,33 według stabilnej kolejności, zachowując sumę. Wartości powinny być w jednostkach minimalnych z jawną walutą.

<!-- example:LLD-06-1 kind:java-members -->
**Kontekst:** Java 21 — elementy klasy; kompilowane przez skrypt po osadzeniu w klasie.

~~~java
static long share(long total, int participants, int position) {
    if (total < 0 || participants <= 0 || position < 0 || position >= participants)
        throw new IllegalArgumentException();
    return total / participants + (position < total % participants ? 1 : 0);
}
~~~

To tylko równy podział. Aktualizacja sald i zapis wydatku muszą być atomowe, a powtórne dodanie tego samego expense ID nie może naliczyć kolejnego długu.

## LLD-07. Jak zaprojektować framework logowania z poziomami i appenderami?

Logger tworzy zdarzenie z czasem, poziomem i kontekstem, filtr decyduje o przyjęciu, formatter koduje, appender zapisuje do celu. Kosztownego formatowania unikam przed sprawdzeniem poziomu. Konfiguracja może być niezmiennym snapshotem atomowo podmienianym przy zmianie.

Async appender potrzebuje bounded queue, polityki overflow, kolejności i flush przy zamknięciu. Błąd appendera nie może rekursywnie logować do siebie. Redaguję sekrety i określam, czy zgubienie debug loga jest dopuszczalne; wymagający audyt nie powinien polegać wyłącznie na tej samej ulotnej kolejce.

Logger nie powinien formatować drogiego payloadu, zanim sprawdzi, czy poziom zostanie użyty. Przy async appender tworzę bezpieczny snapshot potrzebnych pól: zachowanie referencji do mutowalnego argumentu może spowodować zapis innej wartości niż w momencie logowania.

Gdy kolejka jest pełna, polityka może blokować, odrzucać mniej ważne logi lub użyć awaryjnej ścieżki; każda ma koszt. Flush przy shutdown ma ograniczony czas i raportuje utracone rekordy. Formatter powinien kodować nowe linie/znaki specjalne w strukturze, aby dane klienta nie udawały dodatkowych wpisów i nie zniekształcały audytu.

## LLD-08. Jak uczynić projekt rozszerzalnym i poprawnym przy współbieżności?

Najpierw opisuję operacje i niezmienniki, następnie właściciela stanu i koszty struktur. Wymienne polityki, np. taryfa parkingowa, dostają mały kontrakt. Rzadkie hipotetyczne rozszerzenia nie uzasadniają dziesiątek fabryk i warstw.

Dla mutacji wielu indeksów wybieram jedną sekcję krytyczną lub jednego właściciela, zamiast kilku niezależnych concurrent maps. Snapshoty i kopie defensywne chronią odczyt. Testy pokazują kontrprzykład: równoczesna rezerwacja, utrata aktualizacji, timeout lub błąd w środku zmiany.

Rozszerzenie powinno przechodzić ten sam test kontraktowy co wcześniejsza implementacja. Nowa PricePolicy nie może zmienić currency ani wyprodukować ujemnej kwoty, jeśli kontrakt tego zabrania. Interfejs nie chroni przed złamaniem tych warunków samą składnią.

Współbieżna klasa dokumentuje punkt linearyzacji: np. atomowy przydział miejsca kończy się dopiero po aktualizacji obu indeksów. Zwracanie referencji do wewnętrznej listy omija tę ochronę, dlatego wynik jest snapshotem/value object. Przy doborze struktur zapisuję koszty każdego indeksu: szybszy odczyt zwykle oznacza więcej pamięci i dodatkową pracę utrzymania.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [Parking](../low-level-design/parking-lot/README.md)
- [Automat sprzedażowy i bankomat](../low-level-design/vending-machine/README.md)
- [Rozliczenia grupowe](../low-level-design/splitwise/README.md)
- [Biblioteka logowania](../low-level-design/logging-library/README.md)
<!-- labs:end -->
