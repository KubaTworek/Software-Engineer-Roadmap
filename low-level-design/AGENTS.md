# AGENTS.md

Aktualna ścieżka ma **12 głównych tematów i osobną rozgrzewkę**. Bankomat
jest wariantem automatu, a auta i sale należą do jednego kalendarza zasobów.
Historię edytora rozwijamy wokół rope i undo/redo; workflow ról pozostaje
archiwalnym materiałem opcjonalnym. Nowy główny temat musi wnosić odrębny
problem techniczny; najpierw rozważ wariant w istniejącym module.

## Cel i zakres

Ten katalog zawiera ćwiczenia Low Level Design w Javie. Priorytetem jest
zrozumienie reprezentacji danych, kolekcji JDK, algorytmów, pamięci i modelu
wątkowego. Podział na obiekty oraz reguły domeny służą sprawdzaniu tych mechanizmów.
Każde ćwiczenie jest niezależnym projektem edukacyjnym.
Obowiązuje [standard edukacyjny](EDUCATIONAL_STANDARD.md). Wartość dydaktyczna
komentarzy, README i testów jest częścią kryterium ukończenia zadania.

Obowiązują również instrukcje z głównego `AGENTS.md`. Przed zmianą przeczytaj
główny README repozytorium, [README tego obszaru](README.md),
[procedurę](PROCEDURE.md) i README modyfikowanego ćwiczenia.

## Standard pracy

- Realizuj zakres wskazany przez użytkownika i zachowuj jego lokalne zmiany.
- Twórz katalog i build wyłącznie dla aktualnie realizowanego zadania.
- Nie twórz zależności między ćwiczeniami ani wspólnej biblioteki domenowej.
- Domyślnie stosuj czystą Java 21, Maven, JUnit 5 i stan w pamięci.
- Przy tworzeniu projektu przypnij wersje narzędzi, pluginów i zależności; później nie zmieniaj ich bez powodu wynikającego z zadania.
- Nie dodawaj Springa, REST, bazy, brokera ani Dockera, jeśli wymagania ćwiczenia ich nie uzasadniają.
- Nie przenoś wymagań skali, SLO i infrastruktury z katalogu `system-design` do ćwiczeń obiektowych.

## Projektowanie i implementacja

- Priorytetem są struktury danych, algorytmy i efektywne użycie czystej Javy. Reguły domenowe są kontekstem testowania, nie celem rozbudowy.
- Przed podziałem na klasy wypisz operacje i ich docelową złożoność. Dobierz tablice/bitsety, HashMap, TreeMap/TreeSet, PriorityQueue, ArrayDeque lub listę wiązaną do tych operacji.
- Ogranicz skanowanie, sortowanie przy każdym zapytaniu i kopiowanie pełnego stanu na ścieżce zapisu. Oddziel koszt aktualizacji indeksu od kosztu snapshotu/raportu.
- Własna struktura jest uzasadniona konkretnym kosztem lub celem dydaktycznym, np. węzły LRU i indeksowany kopiec. Nie dodawaj struktur wyłącznie dla ich nazw.
- Wyjaśniaj złożoność pesymistyczną/amortyzowaną, lokalność pamięci, boxing, alokacje, aktualizację i unieważnianie indeksów. Zachowuj niezmienniki i testy odmów.
- Każda przebudowa ma mieć niezależną wyrocznię lub testy różnicowe i dowód kosztu (np. licznik odwiedzonych elementów); pomiar czasu bez rozgrzewki nie dowodzi przewagi.

- Najpierw opisz scenariusze, niezmienniki i kontrakty, potem projektuj klasy.
- Umieszczaj reguły przy obiektach odpowiedzialnych za dany stan. Unikaj obiektów z samymi setterami i jednej klasy zarządzającej całą domeną.
- Nie wymuszaj metod w encji, jeśli reguła naturalnie obejmuje kilka obiektów; jawnie opisz odpowiedzialność usługi domenowej.
- Preferuj kompozycję. Dziedziczenie wymaga zgodności kontraktu podtypu z typem bazowym.
- Dodawaj interfejs dla konkretnej wymienności lub granicy zależności; nie twórz interfejsu automatycznie dla każdej klasy.
- Nie narzucaj wzorców projektowych z góry. Wyjaśnij problem i koszt wzorca oraz prostszą alternatywę.
- Chroń kolekcje i stan przed modyfikacją z zewnątrz. Unikaj globalnego mutowalnego stanu.
- Zdefiniuj błędy domenowe oraz efekt nieudanej operacji; nie sygnalizuj sukcesu atrapą.
- Opisz kontrakt wątkowy. Synchronizację dodawaj wyłącznie przy wymaganiu współbieżności.
- Dla czasu zależnego od środowiska używaj wstrzykiwanego `Clock`; dla pieniędzy ustal walutę i reguły zaokrągleń, nie używaj `double`.
- Nazwy klas i metod zapisuj po angielsku, objaśnienia dydaktyczne i dokumentację po polsku, zgodnie z otaczającym kodem.
- Komentuj decyzje, niezmienniki i pułapki; nie opisuj oczywistej składni.
- Przejrzyj każdą klasę i każdy istotny algorytm: komentarz po polsku ma pozwolić odtworzyć odpowiedzialność, przyczynę decyzji i skutki jej naruszenia. Używaj konkretnych przykładów i istniejących testów.
- Wyjaśniaj pojęcia przy pierwszym użyciu. README ma przedstawiać rozumowanie algorytmu, a komentarze uzupełniać je szczegółami implementacji. Nie utożsamiaj `final` z głęboką niemutowalnością ani testu próbkowanego z pełnym dowodem.
- Komentarze testów opisują chronioną gwarancję, powód wyboru danych i granicę wniosku. Pomocniczy kod testu też wymaga objaśnienia, jeśli stosuje nieoczywisty algorytm.
- Zachowuj zwięzłe gettery bez komentarza powtarzającego ich nazwę. Obszerność komentarzy ma wynikać z trudności rozumowania, a nie liczby linii kodu.

## Dokumentacja ćwiczenia

Stosuj [szablon README](templates/PROJECT_README.md). README ma być konkretnym,
samodzielnym opisem projektu: edukacyjny charakter i zakres, działanie
i kontrakty, rdzeń techniczny, przykład, uruchomienie, testy i ograniczenia.
Nie odnoś go do innych projektów ani wzorców projektowych. Nie opisuj historii
przebudowy i przenoszenia wiedzy do kolejnych zadań.

Po krótkim wprowadzeniu umieszczaj rdzeń techniczny oraz mechanizmy Javy przed
szczegółowymi regułami domeny. Opisuj konkretne operacje użytych kolekcji, a nie
samą ich nazwę i O(...): equals/hashCode lub Comparator, porządek iteracji,
widok kontra kopia, boxing, powiększanie tablic, alokacje i własność referencji.
Dla własnej struktury wskaż różnicę względem najbliższego API JDK. Szczegóły
implementacji JDK oddzielaj od gwarancji publicznego kontraktu i sprawdzaj
w dokumentacji używanej wersji Javy; nie podawaj niezmierzonych rozmiarów obiektów.

Wyjaśnij rzeczywisty model wątkowy: co jest mutowalne, które odczyty zapisują
stan, co chroni final, a czego nie daje volatile, wersja lub flaga reentrancji.
Przy kontrakcie jednowątkowym pokaż konkretny wyścig na użytych strukturach
i granicę operacji wymagającą wspólnej ochrony. Nie dopisuj wątków ani pul tylko
po to, żeby opisać współbieżność; rozważania oddzielaj od zaimplementowanego kodu.
Reguły biznesowe zachowuj w zakresie potrzebnym do kontraktów i niezmienników.

Kompaktowanie oznacza usuwanie powtórzeń i łączenie rozdrobnionych sekcji,
nie usuwanie wiedzy. Nie ograniczaj liczby wierszy ani akapitów z góry. Zachowuj
pełne kontrakty, kolejność walidacji, niezmienniki, uzasadnienie struktur,
przebieg algorytmu, koszty i granice gwarancji. Czytelnik powinien móc odtworzyć
rozumowanie z samego README; link do kodu nie zastępuje wyjaśnienia.

Pokaż reprezentatywne przepływy z konkretnymi danymi: sukces, odmowę bez
częściowych zmian oraz istotną granicę. Wyjaśnij kontrprzykład błędnego podejścia
i wskaż test, który go wykrywa. Grupuj treści tematycznie; nie mnoż osobnych
sekcji dla każdego drobnego pytania. Diagram stosuj, gdy wyjaśnia stan lub
przepływ; musi odpowiadać rzeczywistej implementacji. Komentarze w kodzie
uzupełniają dokumentację, a nie przejmują całego uzasadnienia technicznego.

Linkuj do istniejących plików źródeł i testów. Warianty należące do jednego
modułu mogą mieć własne opisy i wspólny indeks. Nawigację między projektami
pozostaw w indeksie LLD; dawne lokalizacje mogą zawierać krótkie przekierowanie.
Po realizacji projektu aktualizuj indeks. Istotne decyzje opisuj przy ich
mechanizmie, bez osobnej rozprawy o abstrakcjach.

## Weryfikacja

Testuj scenariusze, niezmienniki, odrzucane operacje i przypadki brzegowe.
Dla maszyny stanów sprawdzaj również zabronione przejścia. Nie testuj samych
getterów ani szczegółów prywatnej implementacji. Testy mają działać bez usług
zewnętrznych, przypadkowego czasu i opóźnień opartych na `Thread.sleep`.

W katalogu projektu uruchom `mvnw.cmd --batch-mode --no-transfer-progress verify`
(na systemach Unix `./mvnw --batch-mode --no-transfer-progress verify`).
Sprawdź, czy testy rzeczywiście się wykonały. Z katalogu głównego uruchom
`pwsh -File scripts/check-documentation.ps1` oraz `git diff --check`.
Przejrzyj diff i nowe pliki pod kątem artefaktów, logów i plików IDE.

Dla samych zmian dokumentacyjnych kontrola dokumentacji i diffu wystarcza;
nie uruchamiaj niezwiązanych buildów. W podsumowaniu podaj komendy, wyniki
i powód niewykonanych kontroli. Nie wykonuj commitów ani pushowania bez
wyraźnego polecenia użytkownika.
