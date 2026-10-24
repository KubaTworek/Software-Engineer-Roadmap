# Silnik promocji

Projekt edukacyjny w czystej Javie: wyliczenie ceny koszyka przy wielu warunkach, priorytetach i wykluczeniach promocji. Implementacja pokazuje graf konfliktów w BitSet oraz dokładne rozdzielanie rabatów na pozycje. Wycena działa jednowątkowo, w pamięci.

Materiały do nauki: [lektury i źródła tego ćwiczenia](../MATERIALY.md#promocje).

## Rdzeń techniczny

[PromotionEngine](src/main/java/pl/jakubtworek/lld/promotion/domain/PromotionEngine.java) kompiluje uporządkowany katalog i symetryczny graf konfliktów do tablicy BitSet. Podczas wyceny przecięcie z maską zastosowanych promocji zastępuje szukanie konfliktów przez porównywanie wszystkich par.

Rabat jest rozdzielany proporcjonalnie do pozostałych kwot pozycji. Grosze po dzieleniu dostają największe reszty ułamkowe, przy remisie mniejsze SKU. Suma przydziałów jest dokładnie równa rabatowi, bez przekroczenia kwoty pozycji. Raport odróżnia rabat nieobliczony od obliczonego zera.

P oznacza liczbę promocji, L pozycji koszyka, K węzłów warunków jednej promocji.

| Etap | Koszt |
| --- | --- |
| Przygotowanie katalogu i grafu | O(P² + P log P), pamięć O(P² / 64 + P) |
| Sprawdzenia konfliktów w wycenie | O(P × ceil(P / 64)) |
| Ocena warunków | Do O(P × K × L), jeżeli warunki skanują pozycje |
| Alokacja jednego rabatu | O(L log L), w tym porządkowanie reszt |
| Pełny raport | Do O(PK + PL) elementów informacji |

Koszt warunków i alokacji jest oddzielny od kosztu grafu. Buforowanie samodzielnej wyceny nie uprawnia do użycia jej po zmianie podstawy rabatu przez wcześniejszą promocję.

### Graf konfliktów i dokładna alokacja

Indeks promocji w uporządkowanym katalogu jest numerem bitu. Maska konfliktów
promocji zawiera jej niedozwolone pary, a maska zastosowanych ofert obejmuje
wyłącznie dodatnie rabaty. Ich przecięcie wskazuje blokadę. Uporządkowanie
indeksów pozwala deterministycznie wskazać pierwszego blokującego.

Dla rabatu d i pozostałych kwot pozycji b[i] obliczamy proporcje d × b[i] / suma b.
Najpierw przydzielane są części całkowite, potem pozostałe grosze według reszt.
Przykład: 1 gr dzielony między dwie jednakowe pozycje daje 1 i 0 gr; remis
rozstrzyga SKU, a nie przypadkowa kolejność mapy. Mnożenie pośrednie jest dokładne.

Alokacja pomniejsza podstawy następnych rabatów. Dwa rabaty po 10% nie oznaczają
automatycznie 20% wartości początkowej: drugi działa na pozostałej kwocie,
z uwzględnieniem zaokrągleń i zakresu kategorii. Warunki kwalifikacji nadal
odnoszą się do pierwotnego koszyka.

Kwota nieobliczona jest inna niż obliczone zero. Jeżeli promocję zatrzymał
konflikt albo brak budżetu, raport nie może sugerować, że wykonano kalkulator
i otrzymano rabat 0. Ta różnica pozwala odtworzyć kolejność oceny.

## Mechanizmy Javy i model wątkowy

### BitSet jest mutowalny: dlaczego clone poprzedza and

BitSet reprezentuje wiele flag w słowach bitowych. and(other) zmienia
odbiorcę; nie zwraca niezależnego zbioru wyniku. Dlatego quote klonuje
conflicts[index], a dopiero kopię przecina z selected. Wykonanie and
bezpośrednio na konfiguracji usuwałoby z niej konflikty i psuło kolejne wyceny.

~~~java
var blocked = (BitSet) conflicts[index].clone();
blocked.and(selected);
int blocker = blocked.nextSetBit(0);
~~~

To fragment mechanizmu z PromotionEngine; index i selected należą do danej
wyceny. nextSetBit zwraca najmniejszy ustawiony indeks albo −1.
Porządek indeksów jest ustalony podczas kompilacji katalogu, więc wynik
nie zależy od przypadkowego porządku HashMap. Pamięć tablicy BitSet
i kopii podczas wyceny należy doliczyć do samych obliczeń warunków.

List.copyOf katalogu nie zamraża pól każdej Promotion ani dowolnego callbacku.
Wbudowane wartości chronią konfigurację, natomiast przekazana lambda może
przechwycić mutowalny obiekt. Interfejs funkcyjny nie oznacza automatycznie
funkcji czystej. Warunki AND/OR jawnie stosują krótkie spięcie; raport
SKIPPED nie może uruchomić predykatu tylko po to, żeby zbudować jego opis.

BigInteger i działania Money są niemutowalne, więc kolejne obliczenie
tworzy nową wartość. Ta dokładność ma koszt alokacji; BitSet optymalizuje
konflikty, ale nie usuwa kosztu alokacji groszy i tworzenia raportu.

### Lokalny stan wyceny i zależności

Mapy pozostałych kwot, maska selected i kopie konfliktów powstają lokalnie
w quote. Nie udostępniamy ich jako wspólnego, zmienianego koszyka.
Nie jest to jednak deklaracja bezpieczeństwa dowolnego Clock, predykatu
lub kalkulatora wywoływanego równolegle. Kontrakt demo pozostaje jednowątkowy;
niezmienna lista konfiguracji nie synchronizuje wnętrza callbacków.
API operacji bitowych: [BitSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/BitSet.html).

## Zakres, reguły i niezmienniki

Autor konfiguracji tworzy promocje. Klient wyceny przekazuje koszyk, segment
STANDARD/VIP i przedstawione kody kuponów. Silnik wycenia, nie zużywa kuponu
ani nie pobiera zapłaty. Dane są w pamięci, demo jednowątkowe. Wbudowane obiekty
są niemutowalne, a własne predykaty/polityki/polityki muszą być czyste.
Nie deklarujemy osobnych testów współbieżności implementacji rozszerzeń.

| ID | Wymaganie / kryterium |
| --- | --- |
| R1 | Nieujemne grosze PLN, dodatnia ilość, unikalne SKU, sprawdzana arytmetyka |
| R2 | Warunki na pierwotnych faktach, AND/OR z short-circuit i śladem SATISFIED/UNSATISFIED/SKIPPED |
| R3 | Rabat kwotowy lub procentowy HALF_UP od aktualnej kwalifikowanej podstawy |
| R4 | Priorytet malejąco, przy remisie ID rosnąco; wejściowa kolejność konfiguracji nie zmienia wyniku |
| R5 | STACKABLE/EXCLUSIVE i obustronnie respektowane wykluczenia ID; blokują tylko dodatnie zastosowania |
| R6 | Globalny kwotowy limit, saldo ≥ 0, brak kalkulacji różny od kalkulacji zero, powody przycięcia |
| R7 | Alokacja na linie: suma udziałów = rabat, udział ≤ saldo linii, deterministyczne reszty |
| R8 | Opcjonalny okres kampanii [od, do) na osi Instant, jeden odczyt Clock na quote |
| R9 | Wymienne PRIORITY i BEST_SINGLE, wspólne ocenianie i ograniczanie; bez optymalizacji kombinacji |
| R10 | Niemutowalny raport całego koszyka i linii, odmowa bez publikacji częściowego wyniku |

Poza zakresem: podatki, dostawa, inne waluty, zwroty, podział na pojedyncze
sztuki, magazyn kampanii, zużycie/rezerwacja kuponów, parser DSL, baza, REST,
płatności i optymalizacja najlepszej kombinacji. Alokacja na linię nie oznacza
jeszcze obsługi zwrotów ani rozliczeń podatkowych. Polityki są przyjętym modelem
ćwiczenia, a nie zaleceniem finansowym lub interpretacją wymagań prawnych.

- Money przechowuje grosze long; `0.10 + 0.20 = 0.30`. Przepełnienie jest
  błędem, nie zawinięciem. Cena może być zerowa, ilość musi być dodatnia.
  Duplikat SKU jest odmową, zamiast zgadywania sposobu scalenia cen/kategorii.
- Kwoty tekstowe i procenty mają najwyżej dwa miejsca po kropce, bez znaku,
  przecinka i notacji wykładniczej. Procent to 0..100%, lecz skonfigurowany
  PercentageDiscount wymaga wartości dodatniej. BigInteger chroni pośrednie mnożenia.
- Warunki czytają pierwotny subtotal; kolejne procenty czytają aktualne saldo
  kwalifikowanych linii. Dwa rabaty 10% od całego koszyka 100 PLN dają 81 PLN.
- `Conditions.category("books")` oznacza obecność książek, a nie zakres rabatu.
  Dopiero `scopedTo(DiscountScope.category("books"))` ogranicza podstawę do książek.
  Bez scopedTo rabat obejmuje cały koszyk.
- Limit pozostaje osobny od salda: przy braku limitu nominalny rabat 200 PLN
  z koszyka 100 PLN jest przycinany saldem, ale nie raportujemy fikcyjnego cap 100 PLN.
- Prawa wykluczenia są symetryczne nawet przy jednostronnej deklaracji.
  Duplikat ID, wykluczenie siebie i obcego ID są błędami konfiguracji.
  Priorytety są nieujemne; ID mają format `[A-Z][A-Z0-9_-]{0,39}`.
- EXCLUSIVE nie wypiera wcześniejszego rabatu. Nieaktywny, niekwalifikujący się,
  mający pustą podstawę lub zaokrąglony do zera kandydat nikogo nie blokuje.
- `allowed = min(requested, base, budget)`. Każda alokacja sumuje się do allowed.
  Zmiana salda linii następuje wyłącznie przy APPLIED. Alternatywa NOT_SELECTED
  może mieć dodatnie allowed, ale applied=0 i nie zmienia żadnej linii.
- Globalnie subtotal=discount+payable. Ten sam bilans obowiązuje dla każdej linii.
  Raport weryfikuje też znane SKU, ciągłość sald i brak debetu na pojedynczej linii.
- Kategorie/SKU rozróżniają wielkość liter. Kupony są normalizowane przez
  trim i Locale.ROOT; `welcome` = `WELCOME`. Kod nie jest dowodem autoryzacji.

## Reprezentacja stanu

Koszyk i kontekst są niemutowalnymi faktami. Silnik posiada uporządkowaną
konfigurację, Clock i politykę, lecz nie przechowuje roboczych sald ani cache
między wycenami. `Promotion.scopedTo` oraz `during` zwracają nowe wartości.
Stary konstruktor promocji nadal oznacza cały koszyk i brak ograniczenia dat.
Warunki i rabaty pozostają wymiennymi interfejsami. `final` referencji klienta
nie gwarantuje czystości jego własnej implementacji.

## Kontrakty i kolejność przepływu

`PromotionEngine(promotions, maximumDiscount)` zachowuje PRIORITY i Clock.systemUTC.
Wariant z czterema argumentami przyjmuje również Clock i SelectionPolicy.
Brak jawnego limitu oznacza Long.MAX_VALUE groszy; zero jako limit jest legalne.
Konfiguracja jest walidowana i sortowana raz, a quote używa lokalnego stanu.

Przebieg quote:

1. Jeden odczyt clock.instant. Dla każdej promocji najpierw sprawdzamy okres,
   a potem warunek. Nieaktywna kampania pokazuje drzewo warunku jako SKIPPED.
   Wszystkie kwalifikacje powstają przed wyborem i naliczaniem rabatów.
2. Polityka otrzymuje stabilnie uporządkowane kwalifikowane ID. PRIORITY dopuszcza
   wszystkie, BEST_SINGLE przez callback ocenia samodzielną korzyść po limitach.
3. BEST_SINGLE wybiera największą dodatnią korzyść; remis zachowuje priorytet/ID.
   Kalkulacje samodzielnych ofert są buforowane w tej wycenie. Nie wyliczamy
   zwycięskiej oferty drugi raz, gdy podstawy i budżet nie zmieniły się.
4. W kolejności priorytet/ID raportujemy nieaktywnych, niespełnione warunki i
   kandydatów niewybranych. Dla wybranych badamy zgodność z wcześniejszymi APPLIED:
   pierwszy blokujący w kolejności zastosowania; dla pary najpierw wyłączność,
   potem wykluczenie ID.
5. Badamy całe saldo, podstawę zakresu i limit, w tej kolejności. Dopiero wtedy
   wywołujemy politykę rabatu, ograniczamy kwotę i alokujemy ją na linie.
6. Dodatnia aplikacja odejmuje alokacje i budżet, zero niczego nie blokuje.
   Konstruktor PricingResult sprawdza globalny bilans i poszczególne linie.

| Status | Co oznacza kalkulacja |
| --- | --- |
| APPLIED | Obecna, dodatnie allowed zatwierdzono jako applied |
| NOT_SELECTED | Alternatywa BEST_SINGLE może mieć kalkulację i potencjalną alokację, ale applied=0; własna polityka może pominąć bez kalkulacji |
| NO_REDUCTION | Obecna kalkulacja z requested=0 i allowed=0 |
| NOT_ACTIVE / CONDITION_NOT_MET | Brak kalkulacji; niespełniony okres / warunek |
| BLOCKED_EXCLUSIVE / EXCLUDED | Brak kalkulacji, blocker wskazuje wcześniejsze zastosowanie |
| NO_REMAINING_BALANCE | Brak kalkulacji, koszyk bezpłatny |
| NO_ELIGIBLE_BALANCE | Brak kalkulacji, koszyk dodatni, lecz zakres nie ma dodatniego salda |
| LIMIT_REACHED | Brak kalkulacji, saldo i podstawa dodatnie, limit wyczerpany |

`requested()` zwraca Optional<Money>. Optional.empty nie oznacza rabatu zero. Kalkulacja przegranej oferty jest oceną
możliwości, nie zatwierdzeniem. Konsola oznacza jej alokację jako potencjalną.

`clippingReasons()` zawiera ELIGIBLE_BALANCE, gdy requested>base, i DISCOUNT_LIMIT,
gdy requested>budget. Oba powody mogą wystąpić razem: nominalna propozycja
przekroczyła obydwa ograniczenia, nawet jeśli jedno było ostatecznie ciaśniejsze.
Równość granicy nie oznacza przycięcia. Domyślny ogromny cap nie jest obniżany
do subtotal na potrzeby tej diagnozy.

Błędne dane/konfiguracje dają IllegalArgumentException, przepełnienia
ArithmeticException, null programistyczne NullPointerException. Błąd własnej
polityki lub polityki przerywa wycenę, nie udaje rabatu zero. Polityka nie może
wybierać ani przez callback oceniać ID spoza kwalifikowanej listy. Warunek oceniany
nie może zwrócić SKIPPED. Nie ma częściowego wyniku ani mutacji koszyka.

### Wyjaśnienie warunków

Przykład AND(próg 100 PLN, kupon WELCOME, OR(VIP, inny kupon)) przy koszyku
100 PLN bez WELCOME: próg jest SATISFIED z expected/actual, kupon UNSATISFIED,
całe ostatnie OR i jego dzieci SKIPPED. Nie wywołujemy ich predykatów.
`matches` nadal pozwala używać prostych lambd; domyślne evaluate wywołuje je raz
i zwraca liść CUSTOM. Wbudowane Conditions dodają nazwy oraz fakty. Opis pominięcia
nie uruchamia predykatu. Własna implementacja odpowiada za zgodność matches/evaluate
oraz czystość skipped. Raport nie jest serializowalnym językiem reguł ani DSL.

### Alokacja i przykład krytyczny

Koszyk: A books 40 PLN, B tech 60 PLN. Pierwsza promocja: 50% tylko books.
Podstawa 40, rabat 20 trafia wyłącznie do A; nowe salda A=20, B=60.
Druga promocja: 50% całego salda 80. Rabat 40 rozdziela się na A=10 i B=30,
więc końcowo A=10, B=30, razem 40 PLN. Nie odtwarzamy podstawy z cen pierwotnych.

Dla wag A=B=C=1 gr i rabatu 2 gr udziały idealne wynoszą po 2/3 gr.
Części całkowite dają 0; dwa grosze dostają A i B przez remis SKU. Nie zaokrąglamy
każdego udziału HALF_UP, co dałoby 3 gr. Ogólnie allocator dzieli iloczyny
amount×waga przez sumę wag, sortuje reszty malejąco i rozdziela brakujące grosze.
Wagi zerowe nie dostają udziału. Przy amount≤suma wag żaden udział nie przekracza
salda linii. BigInteger chroni pośrednie iloczyny nawet dla Long.MAX_VALUE.

### Czas i powtarzalność

CampaignPeriod przechowuje Instant, nie LocalDateTime. Początek jest włączony,
koniec wyłączony, a pusty/odwrócony przedział jest błędem. Zmiana strefy Clock
bez zmiany instant nie zmienia wyniku. Bieżąca chwila jest wspólna dla całej wyceny,
nawet jeśli fizycznie obliczenie przejdzie przez koniec kampanii.

Przy kampanii ślad zawiera odczytany instant. Identyczny raport gwarantujemy dla
tych samych faktów, konfiguracji i chwili; dwa odczyty systemowego zegara mogą
mieć inny ślad czasu. Testy kampanii używają Clock.fixed lub kontrolowanego Clock,
bez Thread.sleep. Bez kampanii czas nie jest częścią raportu; jego porównanie nie zależy wtedy od chwili wyceny.

## Przykład

Dla pozycji books = 1 PLN i tech = 9 PLN rabat 10% ograniczony do books wynosi 0,10 PLN. Gdy rabat obejmuje cały koszyk, daje 1 PLN. Jeżeli pozostaje tylko 0,05 PLN budżetu, zastosowany rabat nie może go przekroczyć, a raport pokazuje ograniczenie budżetem.

## Uruchomienie

Java 21; z katalogu low-level-design/promotion-engine:

~~~powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
& "$env:JAVA_HOME/bin/java.exe" -jar target/promotion-engine-1.0.0-SNAPSHOT.jar
~~~

Demo ma zegar 2026-10-21T12:00:00Z i limit rabatów 25 PLN. Polecenia: `add SKU CATEGORY PRICE QTY`, `remove SKU`, tier, coupon, uncoupon, `policy priority|best-single`, `at INSTANT`, basket, promotions, quote, new, help i q. new czyści koszyk, poziom klienta i kupony, zachowując czas i sposób wyceny.

## Testy i weryfikacja

[Testy](src/test/java/pl/jakubtworek/lld/promotion) sprawdzają priorytety, obustronne konflikty, zerowe rabaty, zakres kategorii, granice kampanii i ślady warunków. GeneratedPolicyTest używa niezależnego modelu wyboru; testy alokacji sprawdzają zachowanie groszy, limity pozycji i deterministyczne remisy.

| Gwarancja | Test zachowania | Granica |
| --- | --- | --- |
| R1: grosze/overflow | decimalMoneyIsExactAndArithmeticDoesNotWrap, percentageIntermediateDoesNotOverflowAtMaximumMoney | Wybrane skrajne liczby, bez dowodu wszystkich napisów wejściowych |
| R2: wyjaśnienia | andExplainsFirstFailureAndPreservesNestedSkippedBranches, orExplainsSuccessWithoutInvokingFollowingPredicate, customConditionIsEvaluatedOnceEvenForBestSingleReporting | Wbudowane kompozycje i kontrakt własnych lambd |
| R3/R7: zakres/alokacja | categoryBaseAndFollowingGlobalDiscountUseRemainingLineBalances, largestRemaindersUseSkuForTiesAndIgnoreInputOrder, categoryCapAllocatesOnlyToEligibleLinesAndDoesNotOverdraw | Kontrprzykłady oraz osobne wyczerpujące małe dane |
| R4/R5: zgodność | oneSidedExclusionBlocksInBothPriorityDirections, randomConflictGraphsMatchMaskOracleForBothPolicies | Próbka 1000 małych grafów do 6 promocji; nie wszystkie grafy |
| R6: brak/zero/clip | notCalculatedAndCalculatedZeroAreDifferentValues, bothConstraintsCanBeExceededAndUnlimitedCapIsNotMislabelled | Jawne stany i rozróżnienie niezależnych ograniczeń |
| R8: czas | campaignUsesHalfOpenInterval, clockIsReadOnceForMultipleCampaignsAndInactiveConditionsAreNotInvoked | Dokładne granice oraz kontrolowany zegar skaczący między odczytami |
| R9: wybór | bestSingleChoosesAfterClippingWithPriorityAndIdTieBreak, bestSingleDoesNotRunWinningDiscountTwiceAndRejectsInvalidPolicyIds | Najlepsza jedna oferta, bez optymalizacji zestawów |
| R10: raport | reportRejectsUnknownAllocationSkuAndKeepsItsCollectionsImmutable, reportRejectsAllocatedAmountWhichWouldOverdrawOneLine | Bilans globalny i liniowy, nie model zwrotów |

## Ograniczenia

Brak płatności, podatków, zwrotów, limitów użycia kuponów i trwałości. BEST_SINGLE nie gwarantuje najlepszego wyniku spośród kombinacji rabatów.
