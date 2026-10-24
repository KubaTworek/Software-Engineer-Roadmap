# Rozliczenia grupowe

Projekt edukacyjny w czystej Javie: dzielenie wydatków, obliczanie sald, spłaty i korekty. Implementacja aktualizuje salda przyrostowo, zachowując dziennik operacji i obsługę ponowień. Stan pozostaje w pamięci, operacje są jednowątkowe.

Materiały do nauki: [lektury i źródła tego ćwiczenia](../MATERIALY.md#rozliczenia-grupowe).

## Rdzeń techniczny

[ExpenseGroup](src/main/java/pl/jakubtworek/lld/splitwise/ExpenseGroup.java) łączy long[] sald z mapą członek → indeks, dopisywaną listą historii, indeksem wpisów, kluczy i cofniętych wydatków. Przed zapisem oblicza wszystkie zmieniane salda z kontrolą przepełnienia.

G to liczba członków grupy, N uczestników wydatku, H wpisów historii, K suma zapisanych udziałów.

| Operacja | Koszt |
| --- | --- |
| Podział równy / procentowy | O(N log N), w tym deterministyczne porządkowanie |
| Zastosowanie wydatku / jego korekty | O(N), osobno obliczenie podziału; dopisanie historii amortyzowane O(1) |
| Spłata / odszukanie ponowienia | Średnio O(1) dla indeksów |
| Snapshot | O(G + H + K) po zmianie, O(1) bez zmiany |

Procenty używają BigInteger dla wyników pośrednich, aby uniknąć przepełnienia mnożenia przed dzieleniem. Salda pozostają long z jawną kontrolą arytmetyki. Snapshot odtwarza niezależny widok z dziennika; pobieranie go po każdym dopisaniu długiej historii może dać łączny koszt kwadratowy. Pamięć historii i udziałów rośnie liniowo z H i K.

### Aktualizacja sald i trwałość znaczenia wpisu

Dla kwoty T opłaconej przez płatnika p i udziałów s[i] zmiana salda wynosi
T − s[p] dla płatnika i −s[i] dla pozostałych uczestników. Suma delt jest
zerowa, ponieważ suma udziałów wynosi T. Płatnik niebędący uczestnikiem
otrzymuje pełne +T.

Historia zapisuje ostateczne udziały. Nie uruchamia ponownie algorytmu podziału
przy każdym odczycie, więc zmiana konfiguracji przyszłych wydatków nie zmienia
znaczenia wcześniejszych wpisów. Bieżące salda są aktualizowane tylko dla
członków objętych operacją; pełny odczyt historii ma osobny koszt.

Identyczne żądanie z kluczem oznacza identyczną treść, a nie tę samą klasę
algorytmu. Równy podział 100 gr na dwie osoby i jawne 50/50 gr mogą opisywać
ten sam wydatek. Obliczenie udziałów potrzebne do porównania żądania nadal
musi być deterministyczne.

Korekta po spłacie nie usuwa przelewu z historii. Jeżeli U2 oddał U1 koszt
wydatku, a U1 następnie wycofał ten wydatek, powstaje saldo wymagające zwrotu
w przeciwnym kierunku. Zachowanie obu wpisów pozwala odtworzyć to rozliczenie.

## Mechanizmy Javy i model wątkowy

### long[], boxing i koszt listy historii

Bieżące salda są w long[], a mapa MemberId → Integer tłumaczy ID na indeks.
Tablica zawiera wartości prymitywne bez osobnego Long dla każdego salda.
Indeks w HashMap nadal podlega boxingowi. Generyki `Map<MemberId, Long>`
nie przechowywałyby long bezpośrednio; nie można też użyć long jako
parametru typu kolekcji.

ArrayList historii zapewnia indeksowy odczyt O(1) i dopisywanie
amortyzowane O(1). Powiększenie tablicy kopiuje referencje, więc pojedyncze
dopisanie może być liniowe. HashSet cofniętych ID i HashMap operacji
odpowiadają za inne pytania; przegląd całej listy nie jest wymagany
przy sprawdzaniu klucza lub prawa do ponownej korekty.

Operatory + i * na long nie zgłaszają zwykłego przepełnienia.
Math.addExact/subtractExact i kontrolowane działania Money zamieniają
ten błąd w wyjątek przed zapisem do tablicy. BigInteger dla iloczynów
procentów tworzy obiekty i ma koszt zależny od liczby cyfr, ale nie traci
dokładności przed dzieleniem. To świadomy koszt poprawności arytmetyki.

Map.copyOf udziałów zabezpiecza strukturę mapy. Własne podziały nie mogą
przekazać mutowalnej mapy, którą po przyjęciu wydatku zmienią przez alias.
Porównanie retry obejmuje wartości żądania, a nie sam hashCode — kolizja
skrótu nie oznacza równości.

### Wątki: walidacja kandydata i publikacja

Bufor delt pozwala obliczyć wszystkie nowe salda przed modyfikacją long[].
Nie daje izolacji między wątkami. Dwa wywołania mogłyby odczytać te same
stare salda, a następnie wzajemnie nadpisać wynik lub powielić klucz.
Ani AtomicLong dla pojedynczego salda, ani ConcurrentHashMap rejestru
nie zapewniłyby wspólnej zmiany tablicy, dziennika i indeksów. Kod wymaga
jednego wątku; testy modelowe sprawdzają sekwencje, nie liniowalność.

## Zakres, reguły i niezmienniki

Aktor zgłasza wydatek lub spłatę w imieniu uczestnika. MemberId nie jest
uwierzytelnieniem. Lista członków grupy jest stała; jedna osoba może samodzielnie
tworzyć grupę i przypisywać sobie cały koszt. Taki zapis ma zerową zmianę salda,
ale nadal jest wydatkiem w dzienniku.

| ID | Gwarancja | Przykład odmowy |
| --- | --- | --- |
| R1 | Grupa niepusta, kody unikalne po normalizacji, nazwy niepuste | Duplikat U1/u1 → IllegalArgumentException |
| R2 | Płatnik i wszystkie osoby w podziale należą do grupy | UNKNOWN_MEMBER |
| R3 | Wydatek i spłata mają dodatnią kwotę Money | Zero → INVALID_AMOUNT; ujemne Money → IllegalArgumentException |
| R4 | Udziały nieujemne, niepuste i sumują się dokładnie do kwoty | Wadliwa polityka → błąd przed zapisem |
| R5 | Remisy podziału są deterministyczne, bez zależności od kolejności map/list | Przy 1 gr równym dla U1/U2 grosz dostaje U1 |
| R6 | Suma sald jest zero; wydatek dodaje zmianę netto płatnika i odejmuje koszty innych | Błąd projekcji nie może wejść do snapshotu |
| R7 | Spłata idzie od ujemnego do dodatniego salda, między różnymi osobami | SELF_SETTLEMENT lub INVALID_SETTLEMENT_DIRECTION |
| R8 | Spłata nie przekracza długu nadawcy ani należności odbiorcy | OVERPAYMENT |
| R9 | Błąd podziału, wyjątek polityki i przepełnienie salda zachowują tę samą publikację | Brak częściowego wpisu lub salda |
| R10 | Odczyty i stare snapshoty są niemutowalne, także mapa udziałów Expense | Zmiana mapy/listy → UnsupportedOperationException |
| R11 | Ten sam jawny klucz i treść zwracają pierwotny wynik, inne dane są konfliktem | IDEMPOTENCY_CONFLICT; bez nowego wpisu |
| R12 | Korekta dotyczy istniejącego wydatku, zgłasza ją jego płatnik, najwyżej raz | UNKNOWN_ENTRY, NOT_EXPENSE, NOT_PAYER, ALREADY_REVERSED |

Money ma zakres 0..Long.MAX_VALUE, Balance pełny zakres long ze znakiem.
Saldo pojedynczej osoby musi się w nim zmieścić. Suma wielu należności może
przekraczać Long.MAX_VALUE, dlatego kontrola sumy używa BigInteger. Nie oznacza
to nieograniczonego salda pojedynczego członka.

### Ważne rozstrzygnięcie: saldo grupy, a długi par

Jeżeli Ala zapłaciła 1 PLN za Bartka, a Bartek 1 PLN za Celinę, salda wynoszą
Ala +1 PLN, Bartek 0, Celina −1 PLN. Celina może spłacić Alę, chociaż w pierwszym
wydatku nie była uczestnikiem. To przyjęty model rozliczenia NETTO grupy.
Nie udajemy, że zapisujemy zobowiązania każdej pary lub zasady rzeczywistej
cesji długu. Osobny graf długów wymagałby innych kontraktów i walidacji spłat.

## Reprezentacja stanu i przepływ

Każda operacja
tworzy nowy, kompletny wpis; nie ma edycji lub usuwania historii. Record Expense
kopiuje udziały, więc późniejsza zmiana mapy polityki nie zmieni dawnych kosztów.
Snapshot kopiuje listę wpisów i mapy, których wartości również są niemutowalne.

| Komenda | Kolejność sprawdzania | Sukces |
| --- | --- | --- |
| addExpense | null, wykonanie i kopia podziału oraz normalizacja opisu, kontrola klucza, znany płatnik, dodatnia kwota, znane osoby, struktura/suma udziałów, kandydackie delty sald | Expense i aktualizacja indeksów albo dawny wynik przy retry |
| settle | null, kontrola klucza, znane osoby, dodatnia kwota, różne osoby, kierunek sald, granice kwoty, kandydackie delty sald | Settlement i aktualizacja indeksów albo dawny wynik |
| reverseExpense | null i normalizacja powodu, kontrola klucza, znany aktor, istniejący wpis, typ Expense, aktor jest płatnikiem, brak wcześniejszej korekty, kandydackie delty sald | ExpenseReversal i aktualizacja indeksów albo dawny wynik |
| balanceOf | null, znany kod | Balance, bez zmiany stanu |

Null jest błędem programisty (NullPointerException). Konfiguracja polityki,
pusty opis, błędne wartości lub podział naruszający kontrakt dają
IllegalArgumentException; suma udziałów przekraczająca long może dać
ArithmeticException. Odmowy biznesowe mają SplitException.reason(). Przy
kilku naruszeniach wygrywa pierwszy warunek z tabeli. Inne wyjątki dodatkowej
polityki propagują, ale nadal bez publikacji wpisu.

Przepływ wydatku 100 gr opłaconego przez U1 dla U1/U2/U3:

1. Równy podział 100 gr dla U1/U2/U3 daje 34/33/33.
2. Weryfikujemy udziały i tworzymy Expense.
3. Bufor delt oblicza kandydackie salda +66/−33/−33 z kontrolą przepełnień.
4. Aktualizujemy trzy pola long[], dopisujemy wpis i klucz do indeksów.
5. Snapshot odtwarza dziennik dopiero na żądanie i zachowuje niezależny dowód projekcji.
6. Dwie spłaty po 33 gr zerują salda, zachowując trzy wpisy w historii.

Dla wydatku zmiana płatnika wynosi `total − jego_udział`; dla innych `−udział`.
Suma zmian to `total − suma_udziałów = 0`. Dla spłaty zmiany wynoszą `+amount`
u dłużnika i `−amount` u wierzyciela, więc także sumują się do zera.
To pozwala wyprowadzić niezmiennik całej historii przez kolejne wpisy.

### Ponowienie operacji: tożsamość i treść

Przeciążenia `addExpense(OperationKey, ...)`, `settle(OperationKey, ...)` i
`reverseExpense(OperationKey, ...)` pamiętają przyjęty wynik. Klucz jest
rozróżniającym wielkość liter napisem 1..64 znaków `[A-Za-z0-9._:-]`, po `strip()`.
Jego zakres to jedna instancja grupy. `EntryId` identyfikuje zapisany wynik,
a `OperationKey` intencję wywołującego, np. konkretny rachunek lub transfer.

Treść porównujemy wartościowo, bez polegania wyłącznie na skrócie:

- wydatek: płatnik, kwota, finalna mapa udziałów i opis po `strip()`;
- spłata: nadawca, odbiorca i kwota;
- korekta: aktor, ID wydatku i powód po `strip()`.

Kolejność mapy i klasa polityki nie definiują intencji. Równy podział 100 gr
na U1/U2 daje tę samą treść co jawne 50/50 gr przy tym samym płatniku i opisie.
Polityka wydatku jest wykonywana także przy retry, żeby wyznaczyć porównywane
udziały; musi być czysta i deterministyczna. Wadliwa polityka może więc zgłosić
błąd przed kontrolą klucza. Inny rodzaj operacji pod zajętym kluczem jest konfliktem.

Ten sam klucz z tą samą treścią zwraca ten sam obiekt wyniku bez publikacji
nowego snapshotu. Dlatego retry spłaty działa także po wyzerowaniu sald,
a retry odwróconego wydatku zwraca dawny wydatek i nie przywraca jego kosztu.
Nowy klucz oznacza nową operację. API bez klucza oraz komendy bez `-key`
generują świeży klucz przy każdym wywołaniu i nie deduplikują powtórek.

Nieudane żądania nie rezerwują klucza. Po błędzie można go użyć ponownie,
również z poprawioną treścią. Dziennik, salda i rejestr przyjętych operacji
wchodzą do jednego kandydata przed publikacją. Publiczne `operationEntries()`
pokazuje niemutowalne powiązanie kluczy z ID wpisów. Gwarancja jest jednowątkowa
i wyłącznie w pamięci. Z samego dziennika można odtworzyć salda, ale nie rejestr
kluczy i treści żądań: po dodaniu trwałości należałoby zapisywać także te metadane.

### Korekta po rozliczeniu

`reverseExpense` dopisuje `ExpenseReversal` wskazujący pierwotny wydatek,
aktora i powód. Nie usuwa ani nie przepisuje historii. Zmiany sald są przeciwne
do zmian oryginału: płatnik `−(total−ownShare)`, pozostali `+udział`.
Nie odwracamy korekt ani spłat. Tylko pierwotny płatnik może zgłosić korektę;
to reguła domenowa, a nie mechanizm uwierzytelniania.

Przykład: U1 płaci 100 gr za U2, U2 spłaca U1 w całości. Salda wynoszą zero.
Korekta wydatku pozostawia spłatę w historii: U1 ma teraz −100 gr, U2 +100 gr.
Można zapisać zwrot U1→U2 100 gr. Korekta nie wykonuje ani nie cofa przelewu
bankowego. Ponowienie z pierwotnym kluczem zwraca tę samą korektę;
nowy klucz dla tego samego wydatku daje `ALREADY_REVERSED`.

Nawet korekta może przekroczyć zakres bieżącego salda `long`, jeżeli między
wydatkiem a korektą zaszły inne operacje. Wtedy cały kandydat jest odrzucany,
wraz z kluczem; po zmianie sald można spróbować ponownie.

## Algorytmy podziału i granice arytmetyki

### Podział równy

Dla kwoty A i N osób: podstawa to A/N, a R=A%N osób dostaje dodatkowy grosz.
Sortujemy kody leksykograficznie, żeby reszty nie zależały od listy wejściowej.
U10 jest przed U2. Udziały różnią się najwyżej o grosz i sumują do A.
Przy A<N część osób dostanie zero; to poprawny wynik, nie brak osoby w podziale.
Samodzielny split dla A=0 zwraca wszystkich z zerowymi kwotami.

### Podział procentowy

Punkty każdej osoby są dodatnie, a ich suma wynosi 10000. Udział 0% pomijamy
w konfiguracji; obliczony udział 0 gr przy małej kwocie jest nadal dozwolony.

1. Oblicz dokładny iloczyn A×punkty przez BigInteger.
2. Dziel przez 10000, zapisując kwotę całkowitą i resztę ułamkową.
3. Zsumuj przydzielone kwoty całkowite.
4. Pozostałe grosze przydziel po jednym według malejącej reszty, a przy remisie
   rosnącego kodu uczestnika.

Każda odrzucona część jest mniejsza niż grosz, więc pozostałych groszy jest
mniej niż osób. Nie ma potrzeby rozdzielać ich w nieograniczonej pętli.
Dla 100 gr i 33.33%,33.33%,33.34% otrzymamy 33,33,34 gr. Dla jednego grosza
przy tym samym podziale otrzymamy 0,0,1 — U3 ma największą resztę, mimo że jego
kod nie jest pierwszy. Przy remisie 50%/50% grosz dostaje mniejszy kod.

Zapisujemy wynik, a nie politykę do późniejszego ponownego uruchamiania.
Deterministyczny remis nie obiecuje naprzemiennej sprawiedliwości między wieloma
wydatkami. Zmiana reguły remisów wymagałaby osobnej decyzji i ewentualnego stanu.

### Podział kwotowy

`ExactSplit(Map<MemberId, Money>)` kopiuje niepustą mapę i sprawdza sumę
przy każdym `split(total)`. Dla 100 PLN można wskazać 20/35/45 PLN; system
nie próbuje wyrównywać udziałów. Brakujący lub nadmiarowy grosz to błąd danych,
a nie polecenie automatycznego rozdzielenia reszty. Udział 0 gr jest dozwolony.
Polityka może samodzielnie podzielić zero na zerowe udziały, lecz grupa nadal
odrzuca wydatek zerowy. Sumowanie używa dokładnej arytmetyki `Money.plus`;
przekroczenie `Long.MAX_VALUE` powoduje odmowę konfiguracji.

### Dlaczego zmiana netto i BigInteger?

Jeżeli U1 ma już saldo Long.MAX_VALUE i kupi coś za 1 gr wyłącznie dla siebie,
jego zmiana to zero. Tymczasowe dodanie 1, a potem odjęcie 1 błędnie zgłosiłoby
przepełnienie. Dlatego płatnika aktualizujemy raz, różnicą total−ownShare.

Jeżeli U1 zapłacił Long.MAX_VALUE gr za U3, a U2 dodatkowo 1 gr za U3,
saldo U3 wynosi Long.MIN_VALUE. Można spłacić U2 jednym groszem, a potem U1
kwotą Long.MAX_VALUE. Nie można jednak bezpiecznie obliczyć `abs(saldoU3)`
w long. Granice spłaty badamy przez kierunek zmian do zera, bez negowania długu.
BigInteger służy również do kontroli sumy i iloczynu procentowego, ale nie
ukrywa faktycznego przepełnienia indywidualnego salda: taki wpis jest odrzucany.

## Uruchomienie

Java 21; z katalogu low-level-design/splitwise:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/splitwise-1.0.0-SNAPSHOT.jar
~~~

Polecenia obejmują equal, percent, exact, settle, reverse oraz warianty z kluczem; `help` podaje składnię. Przykład: `equal U1 100 obiad U1 U2 U3`. status i history pokazują wynik, q kończy demo.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/splitwise) obejmują reszty groszowe, zerowe udziały, granice long, ponowienia i korekty po spłatach. GeneratedLedgerTest porównuje salda z modelem opartym o BigInteger; testy podziału sprawdzają sumę udziałów i deterministyczne rozstrzyganie remisów.

| Reguła | Test / plik | Granica dowodu |
| --- | --- | --- |
| R1, R3: wartości i zakresy | [ValueObjectsTest](src/test/java/pl/jakubtworek/lld/splitwise/ValueObjectsTest.java) | Kody, formatowanie, niepoprawne wpisy i grupa jednoosobowa |
| R4–R5: równy podział | [SplitPolicyTest](src/test/java/pl/jakubtworek/lld/splitwise/SplitPolicyTest.java): equalSplitPreservesSumFor1505SmallCases | 5 liczności × kwoty 0..300, także udziały zerowe |
| R4–R5: procenty i remisy | ten sam plik: all244SmallPercentageCasesMatchIndependentEnumeration, percentageSplitUsesLargestFractionAndCodeForTies | 4 zestawy procentów × 61 kwot, niezależna enumeracja wszystkich podziałów |
| R4: granica iloczynu | ten sam plik: longMaximumAmountDoesNotOverflowIntermediatePercentageProduct | Long.MAX_VALUE jako kwota obu polityk |
| R2, R6: wydatek i własny udział | ExpenseGroupTest: expenseWithPayerShareCreatesExactlyBalancedProjection, payerNeedNotParticipateInCostAndPartialSettlementsReduceBothSides | Płatnik w podziale i poza podziałem |
| R7–R8: spłaty | ExpenseGroupTest: netGroupSettlementCanBypassOriginalPairOfExpenseParticipants, overpaymentIsRejectedAgainstDebtorAndCreditorIndependently | Salda netto, częściowe spłaty i obie granice kwoty |
| R9: błędy i zakres salda | ExpenseGroupTest: invalidOrFailingPolicyCannotPublishIncompleteExpense, overflowKeepsJournalWhileNetZeroPayerChangeDoesNotFalselyOverflow, minimumSignedBalanceCanBeSettledWithoutAbsoluteValueOverflow | Wadliwa polityka, dodatnie i ujemne przepełnienie, MIN_VALUE |
| R10: własność danych | ExpenseGroupTest: mutableSourcesCannotChangeMembersHistoryOrRecordedShares | Kopie wejścia, mapy udziałów i listy historii |
| R6–R10: próbka historii | [GeneratedLedgerTest](src/test/java/pl/jakubtworek/lld/splitwise/GeneratedLedgerTest.java) | 4 ziarna × 300 poleceń, model BigInteger i stare widoki |
| R4: jawne kwoty | [ExactSplitTest](src/test/java/pl/jakubtworek/lld/splitwise/ExactSplitTest.java) | 20/35/45 PLN, zero, zła suma, obcy uczestnik, kopie i MAX_VALUE |
| R11: treść i klucz | [IdempotencyTest](src/test/java/pl/jakubtworek/lld/splitwise/IdempotencyTest.java) | Ten sam wynik po pełnej spłacie, konflikty wszystkich pól, różne polityki, brak rezerwacji po odmowie |
| R12: korekta | [ExpenseReversalTest](src/test/java/pl/jakubtworek/lld/splitwise/ExpenseReversalTest.java) | Przed i po spłacie, zły aktor/cel, podwójna korekta, przepełnienie i retry po zmianie salda |
| Adapter i zakończenie rozmowy | [ExpenseConsoleTest](src/test/java/pl/jakubtworek/lld/splitwise/console/ExpenseConsoleTest.java) | Strumienie w pamięci, procenty, spłaty, błędy i EOF |

## Ograniczenia

Brak rzeczywistych przelewów, wymiany walut, zmiany członkostwa, trwałości i synchronizacji. Dziennik oraz klucze operacji pozostają w pamięci przez cały czas życia grupy.
