# Procedura realizacji ćwiczenia LLD

Aktualna ścieżka ma **12 głównych tematów i osobną rozgrzewkę**. Bankomat
jest wariantem automatu, a auta i sale należą do jednego kalendarza zasobów.
Historię edytora rozwijamy wokół rope i undo/redo; workflow ról pozostaje
archiwalnym materiałem opcjonalnym. Nowy główny temat musi wnosić odrębny
problem techniczny; najpierw rozważ wariant w istniejącym module.

Procedura dotyczy każdego zadania z [planu ćwiczeń](README.md). Pracujemy nad
jednym projektem naraz; kolejne punkty opisują przyrosty pracy, a nie obowiązek
uzyskiwania osobnego zatwierdzenia po każdym kroku.

Każdy rezultat jest **projektem edukacyjnym**. Stosuj
[standard edukacyjny](EDUCATIONAL_STANDARD.md): zaplanuj, czego czytelnik ma
się nauczyć, jak prześledzi decyzje i jak sam sprawdzi ich konsekwencje.

## 1. Ustal problem i granice

Przeczytaj główny README repozytorium, [README LLD](README.md),
[AGENTS.md](AGENTS.md) oraz dokumentację wybranego projektu, jeśli istnieje.
Sprawdź stan Git i zachowaj istniejące zmiany użytkownika.

W README projektu zapisz aktorów, scenariusz podstawowy, wymagania funkcjonalne,
out-of-scope oraz konkretne kryteria akceptacji. Rozstrzygnij niejasności mające
wpływ na poprawność; rutynowe założenia zapisz i kontynuuj pracę.
Określ, czy model jest jednowątkowy, jak interpretuje czas i jak reprezentuje pieniądze.
Utrzymuj README jako samodzielny, konkretny opis tego projektu: bez porównań
z innymi zadaniami, omawiania wzorców projektowych i powtarzania reguł.

## 2. Zapisz reguły i kontrakty

Wypisz niezmienniki, np. „jedno miejsce nie ma dwóch aktywnych biletów”.
Każdej istotnej operacji przypisz warunki wejściowe, efekt, wynik i zachowanie
przy odmowie. Zdefiniuj, czy odrzucona operacja pozostawia stan bez zmian.
Dla cyklu życia opisz dozwolone i zabronione przejścia stanów.

Przygotuj przykłady: happy path, granicę zakresu i niedozwoloną operację.
Połącz je z testami zachowania; nie rozbijaj opisu na katalog drobnych punktów.

## 3. Zaprojektuj model obiektowy

Najpierw zaprojektuj reprezentację danych i ścieżki krytyczne: operacje,
częstotliwość, docelowy koszt, struktury, indeksy i koszt ich aktualizacji.
Porównaj z prostym skanem jako wyrocznią. Potem ustal minimalny podział na klasy.
Sekcja „Rdzeń techniczny” w README i testy różnicowe są częścią kryterium ukończenia.

W README wyjaśnij także konkretne mechanizmy Javy użyte na tych ścieżkach:
kontrakty kolekcji, równość i porządek, referencje, kopie, boxing, iteratory,
alokacje oraz model wątkowy. Nie poprzestawaj na tabeli nazw i złożoności.
Umieść część techniczną przed szczegółami domeny. Sprawdź, czy opis braku
synchronizacji, leniwych pól i niezmiennych widoków odpowiada kodowi.

Wskaż właścicieli stanu i odpowiedzialność za reguły. Publiczne operacje
i krytyczny przepływ opisz przy ich kontraktach. Diagram dodaj tylko wtedy,
gdy ułatwia zrozumienie; po implementacji musi odpowiadać rzeczywistemu kodowi.
Uzasadnienie interfejsów, kompozycji i reprezentacji umieszczaj przy mechanizmie,
bez osobnego katalogu wzorców.

## 4. Utwórz minimalny samodzielny projekt

Utwórz katalog dopiero dla realizowanego ćwiczenia. Skopiuj
[szablon README](templates/PROJECT_README.md) i uzupełnij go treścią domenową.
Dodaj własny build Maven, wrapper, źródła i testy. Użyj Java 21 i JUnit 5,
przypnij wersje narzędzi i zależności, a wymagania wpisz do README.

Zastosuj standardowy układ:

```text
low-level-design/<nazwa-zadania>/
  README.md
  pom.xml
  mvnw
  mvnw.cmd
  .mvn/wrapper/maven-wrapper.properties
  src/main/java/...
  src/test/java/...
```

Pokaż mały wykonywalny przepływ przez test scenariuszowy; konsolowe demo jest
opcjonalne. Unikaj dodawania frameworka do uruchomienia kilku obiektów.

## 5. Implementuj zachowanie i sprawdzaj reguły

Zacznij od jednego pełnego scenariusza. Dodawaj kolejne operacje z testami
niezmienników, błędów, granic i sekwencji przejść stanów. Testy mają sprawdzać
wynik i obserwowalny stan, nie prywatny układ metod ani wywołania getterów.

Czas kontroluj przez `Clock`, gdy wpływa na wynik. Dla pieniędzy ustal walutę,
skalę i zaokrąglenia; użyj jednostek najmniejszych albo `BigDecimal`.
Współbieżność testuj tylko przy zadeklarowanym kontrakcie wielowątkowym.

Równolegle z implementacją objaśniaj ją po polsku. W komentarzu klasy pokaż
jej odpowiedzialność i granicę; przy trudnej operacji podaj niezmiennik,
przyczynę kolejności kroków oraz skutek błędnej alternatywy. W teście wyjaśnij
znaczenie danych. Po zmianie algorytmu przeczytaj ponownie wszystkie komentarze
zależne od jego założeń, złożoności i kontraktu.

## 6. Zmień jedno wymaganie

Po działającym wariancie podstawowym wybierz rozszerzenie z planu lub uzgodnionego
zakresu. Dodaj scenariusz akceptacyjny i wprowadź zmianę. Opisz, które klasy
wymagały modyfikacji, które pozostały stabilne i jaki koszt ma nowa abstrakcja.
Nie obiecuj obsługi przyszłych wariantów, których nie pokazuje kod.

## 7. Zweryfikuj i zamknij przyrost

Z katalogu ćwiczenia uruchom:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Sprawdź raporty testów: brak wykonanych testów nie potwierdza zachowania modelu.
Z katalogu głównego repozytorium uruchom:

```powershell
pwsh -File scripts/check-documentation.ps1
git diff --check
git status --short
```

Porównaj README z kodem, sprawdź odnośniki do źródeł i testów,
zaktualizuj status w indeksie LLD i dodaj odnośnik do istniejącego projektu.
Sprawdź również nowe, nieśledzone pliki; wyników buildu i plików IDE nie dodawaj do Git.

Przeczytaj README tak, jakbyś wracał do niego po kilku miesiącach. Sprawdź,
czy da się z niego odtworzyć reguły, reprezentację danych, przebieg algorytmu,
kolejność zmian i koszty. Zachowaj przykłady, kontrprzykłady, komendy, powiązania
z testami i ograniczenia. Usuń powtórzenia, porównania z innymi projektami
i odniesienia zależne od rozmowy. Połącz drobne sekcje bez utraty ich istotnych
odpowiedzi. Dokumentacja i komentarze mają uzupełniać się; nie przenoś całego
wyjaśnienia z README do kodu pod pretekstem kompaktowania.

W podsumowaniu podaj wykonany zakres, komendy i wyniki, wnioski dydaktyczne,
ograniczenia oraz konkretną przyczynę niewykonanych kontroli. Dla zmian wyłącznie dokumentacyjnych
uruchom kontrolę dokumentacji i diffu; build i testy Javy nie mają zastosowania.
Commit i push wymagają wyraźnego polecenia użytkownika.
