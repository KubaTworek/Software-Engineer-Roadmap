# System plików w pamięci

Projekt edukacyjny w czystej Javie: hierarchia katalogów i plików tekstowych z niemutowalnymi widokami. Obsługuje tworzenie, zapis, usuwanie, przenoszenie, kopiowanie i wyszukiwanie. Stan znajduje się w pamięci, operacje wykonuje jeden wątek.

## Rdzeń techniczny

[DirectoryNode](src/main/java/pl/jakubtworek/lld/filesystem/domain/DirectoryNode.java) przechowuje dzieci w trwałym AVL po nazwie. [InMemoryFileSystem](src/main/java/pl/jakubtworek/lld/filesystem/domain/InMemoryFileSystem.java) kopiuje przy zmianie tylko drogę od korzenia i zmieniane ścieżki lokalnych indeksów. Pozostałe poddrzewa są współdzielone.

Przeniesienie w obrębie jednego rodzica buduje cel z aktualnego kandydata drzewa po usunięciu źródła; użycie starego rodzica mogłoby odtworzyć przenoszony wpis.

D oznacza głębokość ścieżki, Bi liczbę dzieci kolejnego katalogu, K liczbę odwiedzonych węzłów wyszukiwania, M liczbę wyników.

| Operacja | Koszt |
| --- | --- |
| Przejście ścieżki / aktualizacja | O(suma log(Bi + 1)), osobno długość i porównywanie nazw |
| Lista dzieci | O(B) przejścia indeksu, plus utworzenie ścieżek wynikowych |
| Publiczna mapa dzieci | Pierwsza materializacja TreeMap O(B log B), kolejne pobranie O(1) |
| Wyszukiwanie | O(K + M log M), osobno filtry, budowanie i porównywanie ścieżek |
| Zachowanie widoku | O(1) dla korzenia; pamięć późniejszych zmian zależy od kopiowanych ścieżek |

Przejście hierarchii jest iteracyjne, wyszukiwanie używa ArrayDeque i odcina dzieci po osiągnięciu limitu głębokości. Rekurencja dotyczy wyłącznie zrównoważonych indeksów AVL. Wyniki wyszukiwania są sortowane po pełnej ścieżce. Niemutowalność umożliwia tanie kopiowanie poddrzewa, ale utrzymywane widoki zachowują również usunięte dane.

### Kopiowanie ścieżki i współdzielenie poddrzew

Zmiana /a/b/info wymaga nowego wpisu pliku, nowych wersji katalogów b i a
oraz nowego korzenia. Nie wymaga kopiowania pozostałych katalogów. W każdym
zmienianym katalogu AVL kopiuje wyłącznie drogę do odpowiedniej nazwy dziecka.

Nazwę przechowuje rodzic jako klucz indeksu. Przeniesienie lub zmiana nazwy
nie wymaga przepisywania całego poddrzewa ani przechowywania absolutnej ścieżki
w każdym potomku. Copy może współdzielić to samo niemutowalne poddrzewo;
późniejszy zapis tworzy nowe węzły tylko po stronie zmienianej kopii.

Najpierw rozstrzygane są źródło, rodzic celu, kolizja nazwy i relacja przodka.
Dopiero wtedy powstaje kandydat nowego korzenia. W move z jednym wspólnym
rodzicem cel trzeba dodać do wersji rodzica po usunięciu źródła, inaczej
ponowny zapis starej mapy mógłby przywrócić przeniesiony wpis.

Ograniczenie głębokości wyszukiwania odcina dodawanie dzieci do stosu.
Filtrowanie już zebranej listy nie dawałoby tej samej oszczędności pracy.
Niedopasowanie nazwy rodzica nie odcina jednak jego poddrzewa.

## Mechanizmy Javy i model wątkowy

### Węzły, widoki kolekcji i jawny stos

DirectoryNode ma finalny korzeń własnego AVL, ale pole view z materializowaną
mapą dzieci jest leniwe i mutowalne. Logiczna niemutowalność katalogu oznacza,
że nie zmienia zestawu dzieci, a nie że każde jego pole jest final.

children() buduje uporządkowany widok mapowy na żądanie. Collections.unmodifiableNavigableMap
blokuje zapis przez zwrócony interfejs; nie tworzy głębokiej kopii.
Tutaj mapa bazowa jest prywatnie zbudowana i później niezmieniana. Owinięcie
żywej, dalej modyfikowanej mapy miałoby inne zachowanie i nie dałoby snapshotu.

Wewnętrzne przejścia AVL nie potrzebują materializować tej mapy. ArrayDeque
przechowuje referencje do oczekujących węzłów lub opisów wizyt. To jawny stos
na stercie, dzięki któremu głęboka hierarchia nie zużywa po jednej ramce
stosu wywołań na każdy katalog. Nadal potrzebna jest pamięć na oczekujące
wizyty; iteracyjność nie oznacza stałego zużycia pamięci całego wyszukiwania.

Porównania nazw String mogą odwiedzić wspólny prefiks znaków. O(log B)
opisuje liczbę porównań AVL, a nie stałą pracę niezależną od długości nazw.
Także equals/hashCode całego DirectoryNode może wymagać mapowego widoku
i zejścia w zawartość; nie jest zwykłym porównaniem referencji korzenia.

### Współdzielenie poddrzew a współdzielenie instancji

Copy zachowuje referencje do niemutowalnych poddrzew. Usunięcie ich z live
nie zwalnia pamięci, jeśli prowadzi do nich zachowany FileSystemView.
GC rozstrzyga osiągalność obiektów, nie to, czy nazwa jest jeszcze widoczna.

InMemoryFileSystem zmienia bieżący korzeń, a children() ma leniwy cache bez
synchronizacji. Projekt nie deklaruje przez to równoległych zapisów ani
bezpiecznego współbieżnego dostępu do wszystkich odczytów. Samo volatile
na bieżącym korzeniu nie zabezpieczyłoby utraconej aktualizacji move/write.
Kontrakt widoków: [Collections](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collections.html).

## Zakres, reguły i niezmienniki

Aktor to lokalny użytkownik API. Nie modelujemy kont ani praw dostępu.

| ID | Reguła i kryterium |
| --- | --- |
| R1 | Korzeń / istnieje zawsze; nie można go tworzyć, usuwać, przenosić ani kopiować jako źródła/celu |
| R2 | Rodzic nowego wpisu istnieje i jest katalogiem; nie tworzymy automatycznie przodków |
| R3 | Nazwy rodzeństwa są unikalne niezależnie od rodzaju wpisu; wielkość liter ma znaczenie |
| R4 | createFile tworzy, write zastępuje istniejący plik; pusty tekst jest poprawny |
| R5 | read wymaga pliku, list katalogu; przez plik nie można przejść |
| R6 | find obejmuje start i potomków, bada nazwy i typy, nie tekst plików |
| R7 | DeleteMode.EMPTY_ONLY odrzuca niepusty katalog; RECURSIVE usuwa całe poddrzewo |
| R8 | move zmienia pełną ścieżkę, zachowuje zawartość, nie nadpisuje celu |
| R9 | move/copy odrzucają tę samą ścieżkę oraz katalog jako cel u własnego potomka |
| R10 | Każda odmowa zachowuje ten sam widok; stare korzenie i wyniki są niemutowalne |
| R11 | FileSystemView ma read/stat/list/find zależne wyłącznie od zachowanego korzenia |
| R12 | copy zachowuje źródło; późniejsze mutacje obu gałęzi nie zmieniają się wzajemnie |
| R13 | AND/OR komponują kryteria; maxDepth=0 bada tylko start, 1 także dzieci |
| R14 | Odmowa domenowa zawiera reason, operation, path i problemPath |
| R15 | Parser kończy ocenę składni przed wykonaniem i obsługuje cytowane ścieżki |

FsPath dopuszcza / albo /segment/segment. Odrzuca ścieżki względne, powtórzone
separatory, separator końcowy poza /, . i .., backslash i znaki kontrolne.
Nie stosuje strip ani normalizacji Unicode. `/ A ` i `/A` są różne.
Nazwa całkowicie biała jest błędna. Nie przyjmujemy reguł POSIX ani Windows.

Nie ma mutowalnych linków. Po copy fizyczne obiekty wartości mogą tworzyć DAG
(współdzielone węzły), ale logiczny widok nadal jest drzewem wpisów adresowanych
ścieżkami. Węzeł nie ma ID ani parent, więc ta sama wartość nie wiąże mutacji
różnych ścieżek.

Poza zakresem: dysk, trwałość, pliki binarne, linki, cwd, uprawnienia, daty,
kwoty, strumienie, idempotencja, stabilne ID i współbieżność.
Kopiowanie katalogu do jego potomka jest świadomie zabronione także wtedy,
gdy niemutowalna reprezentacja nie wytworzyłaby cyklu referencji.

## Reprezentacja stanu

InMemoryFileSystem deleguje również odczyty do bieżącego FileSystemView.
Widok nie przechowuje odnośnika do systemu, więc nie może przypadkiem zobaczyć
nowszego korzenia. Nie ma metod mutacji. root() pozwala analizować strukturę,
ale mapa i wszystkie jej wartości pozostają niemutowalne.

FsNode opisuje wspólny typ wpisu, ale nie narzuca katalogowi operacji tekstowych. Wspólny interfejs sealed uniemożliwia dodanie mutowalnego podtypu.
Nazwa należy do mapy rodzica, co usuwa konieczność aktualizacji pola nazwy
w dziecku przy rename. Interfejs SearchCriteria ma konkretną wymienność;
nie tworzymy interfejsu automatycznie dla każdej klasy.

## Kontrakty i kolejność odmów

| Operacja | Kontrakt |
| --- | --- |
| mkdir/createFile | Rodzic istnieje, nazwa wolna; plik dopuszcza pusty tekst |
| write | Istniejący plik, zastąpienie całej treści; brak automatycznego create |
| delete(path, mode) | Jawny tryb usunięcia; null mode jest błędem programistycznym |
| move(source, target) | Target jest pełną nową ścieżką, nie katalogiem docelowym |
| copy(source, target) | Te same ograniczenia celu co move, ale źródło pozostaje |
| snapshot | O(1), zachowany FileSystemView; brak automatycznej historii w domenie |
| stat/read/list | Niemutowalne odczyty z danego korzenia; list zwraca bezpośrednie pełne ścieżki |
| find(start, fragment) | Skrót dla nameContains(fragment), bez limitu głębokości |
| find(start, query) | Posortowana, niemutowalna lista; pusty wynik to brak trafień |

Kolejność reguł:
create — ochrona korzenia → rodzic i typ → kolizja;
delete — ochrona korzenia → źródło → niepusty katalog;
move/copy — ochrona obu korzeni → źródło → ten sam adres/potomek →
rodzic celu i jego typ → kolizja → kandydat → publikacja.

Dlatego /a → /a/sub/new daje INVALID_MOVE/INVALID_COPY przed badaniem sub.
Brak źródła daje NOT_FOUND przed kolizją celu. Po odłączeniu move odczytuje
rodzica celu ponownie z kandydata, żeby rename nie przywrócił starej nazwy.

[FileSystemException](src/main/java/pl/jakubtworek/lld/filesystem/domain/FileSystemException.java)
ma NOT_FOUND, NOT_DIRECTORY, NOT_FILE, ALREADY_EXISTS, ROOT_PROTECTED,
DIRECTORY_NOT_EMPTY, INVALID_MOVE i INVALID_COPY.
operation jest enumem; path oznacza badany operand, problemPath miejsce problemu.
Przykład mkdir(/file/a/b): operation=MKDIR, path=/file/a/b, problemPath=/file
dla blokującego pliku. Dla copy(/a, /missing/new) brak rodzica opisuje operand
celu /missing/new i pierwszy brakujący prefiks /missing.
Przy zakazanym transferze path jest źródłem, problemPath celem.

Błędna składnia, pusty fragment, ujemna głębokość i błędne rozszerzenie dają
IllegalArgumentException. Null daje NullPointerException. createFile/write
walidują tekst przed strukturą. Te błędy danych nie mają domenowego kontekstu.

### Wyszukiwanie jako kompozycja

```java
var textFiles = SearchCriteria.kind(SearchCriteria.Kind.FILE)
        .and(SearchCriteria.extension("txt"))
        .and(SearchCriteria.nameContains("lesson"));
var results = fs.snapshot().find(FsPath.parse("/docs"), new SearchQuery(textFiles, 2));
```

nameContains jest case-sensitive i wymaga niepustego fragmentu.
extension przyjmuje nazwę bez kropek, np. txt, i sprawdza końcówkę .txt tylko
w plikach; plik o nazwie .txt również pasuje. Katalog kończący się na .txt nie pasuje.
all dopuszcza każdy wpis, and/or stosują krótkie spięcie.
Własne kryteria muszą być czyste, deterministyczne i bez efektów ubocznych.

Niepasujący katalog nie odcina potomków: kryterium określa wynik.
maxDepth ogranicza przejście, a start ma głębokość 0 także wtedy, gdy jest plikiem.

## Prześledź kopię, edycję i historyczny odczyt

1. Utwórz /docs/lesson.txt z tekstem „pierwsza”.
2. Zachowaj `var before = fs.snapshot();`.
3. copy(/docs, /copy) sprawdza oba adresy i dołącza niemutowalne poddrzewo pod
   nową nazwą. Źródłowe /docs pozostaje.
4. write(/copy/lesson.txt, „druga”) tworzy nowy TextFile i mapy przodków
   tylko na drodze kopii. read(/docs/lesson.txt) nadal daje „pierwsza”.
5. Usuń bieżące /docs. before.read(/docs/lesson.txt) nadal daje „pierwsza”,
   a bieżący system odmawia NOT_FOUND.
6. copy(/copy, /copy/nested) daje INVALID_COPY i zachowuje ten sam obiekt widoku.

Każda mutacja buduje cały kandydat przed publish. Błąd reguły lub konstrukcji
nie publikuje części odłączenia/przyłączenia. Nie deklarujemy odzyskiwania
procesu po wyczerpaniu pamięci JVM.

## Uruchomienie

Java 21; z katalogu low-level-design/in-memory-file-system:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/in-memory-file-system-1.0.0-SNAPSHOT.jar
~~~

Konsola udostępnia mkdir, create, write, read, ls, move, copy, rm, rm-tree, find i search. create/write oddzielają tekst od ścieżki przez `|`; ścieżki ze spacjami można cytować. save zapisuje nazwany widok, use wybiera widok do odczytu, live wraca do bieżącego. Wybranie starego widoku nie odtwarza stanu zapisywalnego; zapisy nadal dotyczą aktualnego systemu. help podaje pełną składnię, q kończy demo.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/filesystem) porównują operacje z niezależnym modelem drzewa. DirectoryIndexTest kontroluje 4096 uporządkowanych nazw, wysokość AVL, usuwanie względem TreeMap i stare korzenie. Pozostałe przypadki obejmują wspólnego rodzica move, kopie, limity głębokości, składnię ścieżek i brak częściowych zmian.

| Reguła | Dowód | Granica |
| --- | --- | --- |
| R1–R5 | rootCannotBeCreatedDeletedOrMoved, missingParentsAreNotCreatedAndFileCannotBeTraversed, siblingNamesAreUniqueAcrossBothTypesAndCaseSensitive | Korzeń, typy, istnienie i nazwy |
| R6/R13 | criteriaComposeAndDepthPrunesTraversalWithoutFilteringParents, depthIsRelativeToFileOrDirectoryStartAndResultsRemainImmutable | AND/OR, typ, rozszerzenie, poziomy 0/1 i brak limitu |
| R7–R9 | nonRecursiveDeleteRefusesNonEmptyDirectory, badMoveDestinationNeverDetachesSource, textualPrefixIsNotAncestry | Usunięcie, cel move, prefiks segmentów |
| R10/R11 | snapshotsAndInputMapsAreDeeplyImmutable, historicalViewReadsListsAndSearchesItsOwnTree | Odczyty dawnych korzeni i kolekcje |
| R12 | copyChangesIndependentlyInBothDirections, copyRefusalsPreserveSnapshotAndDescribeContext | Edycje obu stron, usuwanie, plik i poddrzewo, siedem odmów |
| R14 | errorLocatesFirstBlockingPrefixAndFullOperand, model | Operacja, pełny operand i pierwszy blokujący prefiks |
| R15 | invalidSyntaxIsRejected, malformedQuoteCannotDetachSourceAndErrorsShowBlockingPath | Składnia przed wykonaniem |
| Sekwencje | mixedSequencesAgreeWithFlatModelIncludingErrorsAndHistoricalReads | 80 historii po 120 poleceń, ziarno 13002342 |

## Ograniczenia

Brak dostępu do dysku, plików binarnych, dowiązań, uprawnień, limitu pamięci i synchronizacji. Ścieżki nie mają semantyki systemu operacyjnego hosta.
