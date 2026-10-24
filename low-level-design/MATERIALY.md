# Książki i źródła do Low Level Design

[Ścieżka ćwiczeń](README.md)

Tutaj punktem wyjścia są operacje, struktury danych, niezmienniki i koszt ich
utrzymania w czystej Javie. Lektura ma pomóc uzasadnić rozwiązanie oraz znaleźć
kontrprzykład. Nie wymaga dodawania frameworka ani infrastruktury. Materiały
są głównie po angielsku; jedna książka przewodnia i dokumentacja JDK wystarczą
do rozpoczęcia. Lista nie jest obowiązkowym zestawem zakupów.

## Książki i kolejność pracy

| ID | Źródło i dostęp | Zastosowanie |
| --- | --- | --- |
| L1 | Joshua Bloch, [Effective Java, wyd. 3](https://www.informit.com/store/effective-java-9780134685991), komercyjna | Kontrakty equals/hashCode/compareTo, kompozycja, kopie defensywne, niezmienność i projektowanie API. Książka obejmuje Javę do 9; record, sealed i nowsze API sprawdzaj w JDK 21. |
| L2 | Pat Morin, [Open Data Structures — Java Edition](https://opendatastructures.org/), pełny tekst i kod bezpłatne | Dobry pierwszy podręcznik do tablic, list, kolejek, map, drzew, trie i kopców. Czytaj reprezentację oraz analizę kosztu, potem porównaj z własnym kodem. |
| L3 | Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein, [Introduction to Algorithms, wyd. 4](https://mitpress.mit.edu/9780262046305/introduction-to-algorithms/), komercyjna | Pogłębienie: niezmienniki, amortyzacja, rozszerzanie drzew o dodatkowe dane, DP i grafy. Pseudokod trzeba przełożyć na model pamięci i typy Javy. |
| L4 | Robert Sedgewick, Kevin Wayne, [Algorithms, wyd. 4](https://www.informit.com/store/algorithms-9780132762588), komercyjna | Alternatywa dla szerokiej lektury L3: implementacje w Javie, kolejki priorytetowe, wyszukiwanie i grafy. Starszy kod podręcznika nie zastępuje kontraktu obecnego JDK. |
| L5 | Chris Okasaki, [Purely Functional Data Structures](https://www.cambridge.org/core/books/purely-functional-data-structures/0409255DA1B48FA731859AC72E34D494), komercyjna; [rozprawa doktorska z 1996 r.](https://www.cs.cmu.edu/~rwh/students/okasaki.pdf) bezpłatna | Opcjonalne pogłębienie struktur trwałych, współdzielenia i kolejek. Przykłady ML/Haskell wymagają uwzględnienia eager evaluation w Javie. Rozprawa nie jest identycznym wydaniem książki. |
| L6 | John Ousterhout, [A Philosophy of Software Design, wyd. 2](https://web.stanford.edu/~ouster/cgi-bin/book.php), komercyjna; wybrane fragmenty na stronie autora | Projekt kontraktu i ukrywanie złożoności. Oceń, czy interfejs ułatwia poprawne użycie; nie zwiększaj liczby klas dla samego podziału. |

Bezpłatnym uzupełnieniem części algorytmicznej jest Jeff Erickson,
[Algorithms](https://jeffe.cs.illinois.edu/teaching/algorithms/): rekurencja, DP,
zachłanność i grafy z uzasadnieniem poprawności. L2 dotyczy przede wszystkim
struktur, Erickson — projektowania algorytmów; nie trzeba czytać obu od deski do deski.

Najpierw wypisz operacje i prostą wyrocznię, potem przeczytaj właściwy fragment
L2/L3/L4, a następnie zaimplementuj oraz porównaj zachowanie. L1 pomaga dopracować
publiczny kontrakt. L5 jest szczególnie przydatne przy snapshotach i historii.

## Kółko i krzyżyk

[Ćwiczenie](tic-tac-toe/README.md). **Lektura:** L2, tablice i drzewa; L5, współdzielenie wersji.
**Dokumentacja:** [BitSet w JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html),
punkt odniesienia dla reprezentacji bitowej, nie opis implementacji planszy.

**Sprawdź rozumienie:** uzasadnij sprawdzanie linii przez ostatni ruch.
Pokaż granicę słowa bitowego i dlaczego zmiana jednej wersji nie zmienia poprzedniej.

## Parking

[Ćwiczenie](parking-lot/README.md). **Lektura:** L1, równość i porządek; L2/L4, uporządkowane zbiory.
**Dokumentacja:** [TreeSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html).

**Sprawdź rozumienie:** co stanie się z dwoma różnymi miejscami, jeśli Comparator
zwróci zero? Wyjaśnij wspólną granicę aktualizacji biletu i indeksu wolnych miejsc.

## Automat i bankomat

[Ćwiczenie i wariant bankomatu](vending-machine/README.md). **Lektura:** L3 lub
[Erickson — Algorithms](https://jeffe.cs.illinois.edu/teaching/algorithms/), części o DP;
L2, kolejki. **Dokumentacja:** [Math — arytmetyka exact](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html).

**Sprawdź rozumienie:** znajdź kontrprzykład zachłannego wydawania reszty.
Wyjaśnij stan DP, limit liczby monet, rekonstrukcję planu i minimum w przesuwanym
oknie. Sam podręcznik DP nie dowodzi poprawności optymalizacji kolejką monotoniczną.

## Biblioteka

[Ćwiczenie](library/README.md). **Lektura:** L3, rozszerzanie drzew o rozmiar poddrzewa;
L2, zrównoważone drzewa. **Dokumentacja:** [TreeSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html),
do porównania z własną kolejką z rangą.

**Sprawdź rozumienie:** prześledź rotację i usunięcie węzła z dwojgiem dzieci.
Jak zachowujesz rozmiary poddrzew i mapę czytelnik → węzeł? Dlaczego zwykłe FIFO
nie odpowiada równie szybko na pytanie o pozycję dowolnej osoby?

## Kalendarz zasobów

[Ćwiczenie i warianty sal/aut](resource-calendar/README.md). **Lektura:** L2/L3,
uporządkowane indeksy i wyszukiwanie zakresowe. **Dokumentacja:**
[NavigableMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableMap.html)
oraz [java.time](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html).

**Sprawdź rozumienie:** uzasadnij wyszukiwanie przez poprzednika dla rozłącznych
przedziałów. Pokaż, co przestaje działać po dopuszczeniu nakładania; pełne drzewo
przedziałowe jest innym rozwiązaniem niż obecny indeks po początku.

## Rozliczenia grupowe

[Ćwiczenie](splitwise/README.md). **Lektura:** L1, niemutowalność i kontrakty;
L3, niezmienniki. **Dokumentacja:** [Math](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Math.html)
oraz [BigInteger](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigInteger.html).

**Sprawdź rozumienie:** udowodnij sumę delt równą zero, rozdział reszty
i brak częściowej zmiany po overflow. Oddziel koszt dopisania dziennika od
kosztu materializacji historii. To ćwiczenie arytmetyki, nie system księgowy.

## Windy

[Ćwiczenie](elevator-system/README.md). **Lektura:** L5, trwałe kolejki; L2, trie i amortyzacja.
**Dokumentacja:** [ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html),
do porównania z własnym niemutowalnym FIFO.

**Sprawdź rozumienie:** prześledź odwrócenie listy i indeks przynależności.
Zbadaj, czy koszt amortyzowany dla liniowej historii można przenieść na wielokrotne
rozgałęzianie starych wersji. Wyjaśnij deterministyczny tick bez zegara ściennego.

## Szachy

[Ćwiczenie](chess/README.md). **Lektura:** L2, tablice i mapy; L1, kopie stanu.
**Źródła:** [FIDE Laws of Chess — wersja obowiązująca od 1.01.2023](https://handbook.fide.com/chapter/E012023)
oraz [BitSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html), jako odniesienie techniczne.

**Sprawdź rozumienie:** oddziel atak pola od legalnego ruchu, uzasadnij tożsamość
pozycji dla powtórzeń i porównaj perft z niezależnymi pozycjami referencyjnymi.
Zakres reguł aplikacji określa README; cytowanie FIDE nie oznacza pełnej obsługi turnieju.

## Promocje

[Ćwiczenie](promotion-engine/README.md). **Lektura:** L3 lub Erickson, grafy
i granice zachłanności; L1, kontrakty wyniku. **Dokumentacja:**
[BitSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html).

**Sprawdź rozumienie:** wyjaśnij maskę konfliktów i deterministyczne rozdzielenie
groszy. Czy wybór według priorytetów maksymalizuje rabat? Nie przypisuj algorytmowi
celu optymalizacyjnego, którego kontrakt nie obiecuje.

## Historia edytora

[Ćwiczenie](editor-history/README.md). **Lektura:** L5, struktury trwałe.
**Źródła:** Hans-Juergen Boehm, Russell R. Atkinson, Michael F. Plass,
[Ropes: An Alternative to Strings — opis publikacji](https://research.google/pubs/ropes-an-alternative-to-strings/)
oraz [String w JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html).

**Sprawdź rozumienie:** porównaj kopiowanie całego tekstu z kopiowaniem ścieżki
rope. Uzasadnij granice UTF-16 i koszt materializacji; zachowana gałąź historii
utrzymuje usunięty tekst w pamięci. Publikacja o rope nie specyfikuje naszego wariantu AVL.

## System plików w pamięci

[Ćwiczenie](in-memory-file-system/README.md). **Lektura:** L5, path copying;
L2/L3, drzewa. **Dokumentacja:** [kolekcje JDK 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/package-summary.html),
szczególnie kopia, widok i kolejność iteracji.

**Sprawdź rozumienie:** narysuj współdzielone poddrzewa przed i po move.
Dlaczego niezmienny widok może utrzymywać dane po delete i dlaczego ten model
nie daje crash consistency prawdziwego systemu plików?

## Cache w pamięci

[Ćwiczenie](in-memory-cache/README.md). **Lektura:** L2/L4, listy, mapy i kopce;
L1, własność stanu. **Dokumentacja:** [LinkedHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html)
oraz [PriorityQueue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html).

**Sprawdź rozumienie:** porównaj usuwanie dowolnego terminu w PriorityQueue
z kopcem mającym indeks pozycji. Wyjaśnij, które odczyty zmieniają LRU/LFU i jak
utrzymujesz zgodność wszystkich indeksów po expire, update i resize.

## Logger

[Ćwiczenie](logging-library/README.md). **Lektura:** L2, buforowanie tablicą;
L1, odpowiedzialność za zasoby. **Dokumentacja:** [ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html)
do porównania z buforem o stałej pojemności oraz [RFC 8259 — JSON](https://www.rfc-editor.org/rfc/rfc8259).

**Sprawdź rozumienie:** prześledź przepełnienie bufora dla obu polityk i częściowy
błąd jednego odbiorcy. Odróżnij poprawne escaping od redakcji sekretu oraz flush
od trwałości zapisu na dysku.

## Weryfikacja i wersje

- [JUnit 5.12.2 User Guide](https://docs.junit.org/5.12.2/user-guide/): parametryzacja,
  asercje i testy sekwencji. Ziarno losowania i prosta wyrocznia czynią porównanie odtwarzalnym.
- [JMH](https://github.com/openjdk/jmh): osobny eksperyment wydajnościowy po ustaleniu
  hipotezy; rozgrzewka, forki i eliminacja martwego kodu. Licznik odwiedzonych
  elementów pokazuje koszt algorytmiczny, nie czas działania JVM.
- [JOL](https://github.com/openjdk/jol): badanie układu i grafu obiektów na wskazanej
  JVM. Wynik zależy od środowiska; rozmiar obiektu z jednego pomiaru nie jest gwarancją Javy.
- [JLS 21, rozdział 17](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html):
  widoczność, publikacja i happens-before. Wyjaśniaj granicę jednowątkowego
  kontraktu, bez dopisywania niezaimplementowanej synchronizacji.

Dobór i adresy źródeł sprawdzono 25.10.2026. Publiczne gwarancje JDK 21 odróżniaj
od kodu konkretnej implementacji i wyników pomiaru. Książka ani zielony test
próbkowany nie zastępują uzasadnienia niezmiennika oraz granic złożoności.
Archiwum pozostaje opcjonalne i nie wydłuża głównej ścieżki.
