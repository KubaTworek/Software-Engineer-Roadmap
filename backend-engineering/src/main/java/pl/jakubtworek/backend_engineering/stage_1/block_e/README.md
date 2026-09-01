# Stage 1E — refaktoryzacja i bezpieczna zmiana

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Stage 1E — refaktoryzacja i bezpieczna zmiana.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Stage 1E — refaktoryzacja i bezpieczna zmiana” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=VersionedInvoiceExportContractTest,LegacyInvoiceBatchGoldenMasterTest,LegacyUserServiceCharacterizationTest" test`
> - **Role klas:** `LegacyEmailService` = `naive`, `LegacyInvoiceBatchService` = `naive`, `LegacyInvoiceRow` = `naive` (+6); `LegacyUserController` = `production-boundary`, `RegisterUserController` = `production-boundary`, `RegistrationConfiguration` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Refaktoryzacja kodu legacy – podejście krok po kroku



## Wprowadzenie

Refaktoryzacja systemów legacy nie polega wyłącznie na „sprzątaniu kodu”. W praktyce jest to proces odzyskiwania kontroli nad aplikacją, która przez lata rozwijała się bez wyraźnych granic architektonicznych, odpowiednich testów i spójnych zasad projektowych. Typowy kod legacy miesza logikę biznesową z frameworkiem, bazą danych oraz integracjami zewnętrznymi, przez co każda zmiana staje się kosztowna i ryzykowna.

Kluczowym problemem takich systemów jest brak możliwości bezpiecznego wprowadzania zmian. Michael Feathers definiuje kod legacy bardzo prosto — jest to kod bez testów. Oznacza to, że głównym celem refaktoryzacji nie jest od razu poprawa architektury, ale stworzenie środowiska, w którym zmiany można wykonywać przewidywalnie i bez ryzyka regresji.

W tym laboratorium katalogi `legacy` i `refactored` są celowo utrzymywane obok
siebie. Nie chodzi o pokazanie, że każda klasa musi otrzymać interfejs, lecz o
porównanie kosztu zmiany tego samego przypadku użycia przed i po utworzeniu seamów.

| Problem w wersji legacy | Ruch refaktoryzacyjny | Uzyskana własność |
|---|---|---|
| logika zależy bezpośrednio od JPA | port repozytorium i adapter Spring Data | domenę można uruchomić bez bazy |
| czas pobierany statycznie | wstrzyknięty `Clock` | deterministyczne zachowanie |
| wysłanie e-maila ukryte w serwisie | jawny port efektu zewnętrznego | można kontrolować kolejność i awarie |
| kontroler zna szczegóły zapisu | use case z modelem wejścia i wyniku | HTTP nie steruje regułami biznesowymi |
| błędy reprezentowane przypadkowo | wyjątki domenowe na granicy aplikacji | spójna semantyka niepowodzeń |

Refaktoryzacja zachowuje obserwowalne zachowanie, ale może świadomie zmienić
strukturę. Jeżeli zachowanie legacy jest błędem biznesowym, najpierw zabezpiecz je
testem charakteryzującym i udokumentuj, a poprawę wprowadź jako osobną zmianę.

## Mapa wykonywalnych laboratoriów

Stage 1E zawiera teraz dwa różne poziomy modernizacji:

| Laboratorium | Ryzyko | Technika |
|---|---|---|
| `legacy` → `refactored` | pojedynczy use case rejestracji | seamy, porty, `Clock`, testy jednostkowe |
| `legacy_batch` | zmienny czas, losowość, statyczne reguły i zewnętrzny format tekstowy | characterization test i golden master |
| `migration` | wymiana większej implementacji bez długowiecznego feature brancha | branch by abstraction, shadow comparison i stopniowe przełączenie |
| `contract` | nowy model odpowiedzi przy istniejących konsumentach | równoległe V1/V2 i kompatybilny adapter |
| `semantic_trap` | uproszczenie algorytmu finansowego | test granicy zaokrąglenia wykrywający zmianę semantyki |

Eksport faktur jest celowo bardziej kłopotliwy niż rejestracja użytkownika. Jego wynik jest kontraktem bajtowym dla zewnętrznego systemu, kolejność wierszy ma znaczenie, a podatek i zaokrąglenie wpływają na pieniądze. W takim miejscu „równoważny biznesowo” output może nie być równoważny operacyjnie.

---

## Characterization test czasu, losowości i statycznych zależności

`LegacyInvoiceBatchService` pobiera datę i identyfikator przez statyczny `LegacyRuntime`, a stawkę podatku przez statyczny `LegacyTaxRules`. Nie jest to docelowa architektura, lecz realistyczny punkt startowy.

`LegacyInvoiceBatchGoldenMasterTest` tworzy tymczasowy seam przez static mocking:

- zamraża datę biznesową,
- stabilizuje losowy identyfikator batcha,
- obserwuje wywołania statycznej tabeli podatkowej,
- porównuje kompletny output z `src/test/resources/stage_1/block_e/invoice-batch.golden`.

Static mocking jest tutaj rusztowaniem migracyjnym. Pozwala rozpocząć bez zmiany kodu produkcyjnego, ale nie powinien stać się preferowanym stylem nowych testów. Docelowa implementacja zastępuje go przez `BusinessDateProvider`, `BatchIdGenerator` i `TaxPolicy`.

Characterization test odpowiada na pytanie „co kod robi teraz?”, nie „co powinien robić?”. Jeżeli golden master zawiera błąd, najpierw należy świadomie zaakceptować stan bazowy, a poprawkę wprowadzić w osobnej zmianie z nowym wymaganiem.

## Golden master — wartość i ograniczenia

Golden master jest przydatny, gdy output jest duży, nieregularny albo słabo poznany. Chroni jednocześnie nagłówki, separatory, kolejność, format pieniędzy, podsumowanie i końcowy znak nowej linii. Ręczne asercje każdego pola byłyby trudniejsze do utworzenia na początku migracji.

Nie należy jednak automatycznie aktualizować pliku golden po nieudanym teście. Poprawny proces wygląda tak:

1. obejrzyj diff outputu,
2. ustal, czy to zamierzona zmiana kontraktu,
3. sprawdź konsumentów i wymagania biznesowe,
4. dopiero wtedy zaakceptuj nowy master wraz z opisem zmiany.

Golden master nie zastępuje precyzyjnych testów reguł. Po zrozumieniu kodu warto dodać mniejsze testy podatku, zaokrągleń i błędów, pozostawiając golden jako test kontraktu na granicy.

---

## Branch by abstraction zamiast rewrite'u

`InvoiceBatchGenerator` jest stabilną abstrakcją obejmującą istniejący generator. Nie powstała dlatego, że „każda klasa musi mieć interfejs”, ale dlatego, że przez pewien czas muszą współistnieć dwie implementacje.

Bezpieczna sekwencja migracji pokazana w `BranchByAbstractionInvoiceServiceTest`:

1. obejmij legacy interfejsem bez zmiany zachowania,
2. dodaj `RefactoredInvoiceBatchService` z jawnymi zależnościami,
3. uruchamiaj `ParityCheckingInvoiceBatchGenerator`, który zwraca wynik legacy i porównuje kandydata w cieniu,
4. zatrzymaj migrację, jeśli różni się choć jeden obserwowalny bajt,
5. przełącz `BranchByAbstractionInvoiceService` na replacement,
6. obserwuj produkcję i zachowaj szybki powrót na legacy,
7. usuń starą gałąź dopiero po ustalonym okresie stabilności.

Shadow comparison jest bezpieczne tylko dla obliczeń bez efektów ubocznych albo po odcięciu efektów w gałęzi cienia. Nie wolno podwójnie obciążać karty, wysyłać wiadomości czy zapisywać danych tylko po to, aby porównać implementacje.

Branch by abstraction ogranicza czas życia gałęzi Git i pozwala integrować małe kroki. Nie oznacza jednak pozostawienia dwóch implementacji na zawsze — data usunięcia legacy jest częścią planu.

---

## Bezpieczna zmiana kontraktu

Zmiana typu zwracanego istniejącej metody z `String` na bogaty obiekt byłaby source-breaking. `VersionedInvoiceExportContract` zachowuje więc V1 i dodaje V2:

- `exportV1` nadal zwraca identyczne bajty,
- `exportV2` opakowuje ten sam content w odpowiedź z media type i opcjonalną wersją schematu,
- konsument sam wybiera moment przejścia,
- wycofanie V1 może nastąpić dopiero po pomiarze użycia i ogłoszonym okresie deprecacji.

W API HTTP analogicznymi technikami są nowy endpoint, wersjonowany media type albo pole addytywne. Pole nie jest bezpiecznie addytywne, jeśli starszy klient odrzuca nieznane dane, generuje podpis całego payloadu lub ma zamknięty parser — kompatybilność trzeba potwierdzić testem konsumenta, nie założyć.

---

## „Czystszy” kod, który zmienia semantykę

`LegacyLineByLineDiscountCalculator` zaokrągla rabat każdej pozycji, a następnie sumuje wartości. `CleanButSemanticallyDifferentDiscountCalculator` ma krótszy pipeline stream: najpierw sumuje, potem nalicza rabat i zaokrągla raz.

Dla trzech pozycji po `0.05` i rabatu 10%:

- kontrakt legacy zwraca `0.15`,
- krótszy wariant zwraca `0.14`.

Obie formuły mogą wyglądać rozsądnie matematycznie, ale tylko jedna zachowuje istniejącą granicę zaokrąglenia. Przeniesienie kolejności, deduplikacja kolekcji, zamiana stabilnego sortowania, zmiana obsługi `null`, strefy czasowej albo typu kolekcji to potencjalne zmiany zachowania, nawet gdy metody stają się krótsze.

Refaktoryzacja zachowuje semantykę. Zmiana reguły zaokrągleń może być wartościowa, lecz jest zmianą biznesową wymagającą decyzji, migracji i osobnego testu akceptacyjnego.

---

## Characterization tests jako pierwszy krok

Najważniejszym etapem rozpoczęcia refaktoryzacji jest zabezpieczenie aktualnego zachowania systemu. W praktyce oznacza to tworzenie characterization tests, czyli testów opisujących istniejące działanie aplikacji — nawet jeśli obecne zachowanie nie jest idealne.

Podejście to ma istotne znaczenie psychologiczne i techniczne. Bez testów programista działa „na ślepo”, obawiając się, że każda modyfikacja może zepsuć fragment systemu, którego nie rozumie. Testy tworzą warstwę bezpieczeństwa umożliwiającą wykonywanie małych, kontrolowanych zmian.

Na tym etapie nie poprawia się jeszcze architektury. Priorytetem jest stabilizacja systemu.

---

## Seamy i rozbijanie zależności

Po zabezpieczeniu zachowania można zacząć stopniowo rozdzielać zależności. Feathers nazywa miejsca umożliwiające podmianę zachowania seamami. Są to punkty pozwalające testować kod w izolacji i odcinać go od infrastruktury.

Najczęściej oznacza to:

- przejście z bezpośredniego tworzenia obiektów (`new`) do dependency injection,
- wyodrębnienie interfejsów,
- parametryzację elementów trudnych do testowania (np. czasu),
- oddzielenie logiki biznesowej od frameworka.

Dzięki temu kod przestaje być „przyklejony” do Springa, JPA czy zewnętrznych API. Zależności zaczynają być jawne i możliwe do kontrolowania w testach.

Bardzo istotnym przykładem jest wstrzykiwanie `Clock` zamiast używania `LocalDate.now()`. Taka zmiana wydaje się drobna, ale eliminuje niedeterministyczne zachowanie testów i pozwala precyzyjnie kontrolować scenariusze biznesowe.

---

## Oddzielenie domeny od infrastruktury

Jednym z najważniejszych efektów refaktoryzacji jest rozdzielenie warstw aplikacji.

W systemach legacy logika biznesowa często zależy bezpośrednio od ORM, frameworka HTTP lub implementacji repozytorium. Powoduje to silne sprzężenie i utrudnia rozwój systemu.

Refaktoryzacja prowadzi do architektury, w której:

- domena zawiera wyłącznie logikę biznesową,
- przypadki użycia definiują scenariusze aplikacyjne,
- adaptery obsługują komunikację ze światem zewnętrznym,
- infrastruktura implementuje szczegóły techniczne.

To podejście jest zgodne z zasadą Dependency Rule z Clean Architecture — zależności powinny wskazywać do wnętrza systemu, nigdy odwrotnie.

Dzięki temu logika biznesowa może być rozwijana niezależnie od technologii. Framework staje się szczegółem implementacyjnym, a nie fundamentem systemu.

---

## Use Case jako centralny element logiki

W dobrze zrefaktoryzowanym systemie logika biznesowa nie znajduje się ani w kontrolerach HTTP, ani w klasach infrastrukturalnych. Centralnym punktem stają się use case’y.

Use case reprezentuje konkretny scenariusz biznesowy, np. rejestrację użytkownika. Przyjmuje dane wejściowe, wykonuje reguły biznesowe i zwraca wynik operacji.

Najważniejsze jest to, że use case:

- nie zna Springa,
- nie zna HTTP,
- nie zależy od bazy danych,
- nie zawiera szczegółów infrastrukturalnych.

Takie podejście znacząco upraszcza testowanie i zwiększa czytelność systemu. Programista może analizować logikę biznesową bez konieczności rozumienia całego frameworka.

---

## Clean Code i uproszczenie logiki

Refaktoryzacja nie kończy się na architekturze. Równie istotna jest poprawa samego kodu.

Systemy legacy bardzo często zawierają:

- zbyt długie metody,
- flag arguments,
- warunki biznesowe rozproszone po systemie,
- nieczytelne nazwy,
- klasy typu God Object.

Proces refaktoryzacji prowadzi do uproszczenia przepływu logiki i podziału odpowiedzialności zgodnie z zasadą SRP (Single Responsibility Principle).

Metody stają się mniejsze i bardziej intencyjne. Nazwy zaczynają opisywać zachowanie biznesowe zamiast technicznych szczegółów. Znika logika sterowana flagami, a rozbudowane instrukcje warunkowe są zastępowane polimorfizmem lub strategią.

Efektem nie jest „ładniejszy kod”, ale system łatwiejszy do rozwijania i mniej podatny na błędy.

---

## Obsługa błędów i fail-fast

Kod legacy bardzo często ukrywa błędy poprzez:

- zwracanie `null`,
- ignorowanie wyjątków,
- ciche fallbacki.

Takie podejście utrudnia diagnozowanie problemów i prowadzi do nieprzewidywalnego zachowania systemu.

Refaktoryzacja wprowadza podejście fail-fast. Problemy są sygnalizowane jawnie za pomocą wyjątków domenowych lub kontrolowanych rezultatów biznesowych.

Dzięki temu:

- ścieżka sukcesu pozostaje czytelna,
- błędy są obsługiwane na odpowiednim poziomie,
- system staje się bardziej przewidywalny.

---

## Testy jednostkowe i integracyjne

Po rozdzieleniu warstw możliwe staje się sensowne testowanie systemu.

Testy jednostkowe koncentrują się na logice biznesowej i działają bez infrastruktury. Są szybkie, deterministyczne i łatwe w utrzymaniu.

Testy integracyjne sprawdzają współpracę warstw, bazę danych i konfigurację frameworka.

Dobrze zrefaktoryzowany system posiada zdecydowanie więcej testów jednostkowych niż integracyjnych. Jest to zgodne z ideą piramidy testów — większość logiki powinna być możliwa do zweryfikowania bez uruchamiania całej aplikacji.

W przykładzie rejestracji zapis użytkownika następuje przed wysłaniem wiadomości powitalnej. Test jednostkowy pilnuje, aby błąd zapisu nie uruchamiał efektu zewnętrznego. Nie należy jednak interpretować zwykłego wywołania adaptera e-mail jako atomowej części transakcji bazodanowej. W systemie produkcyjnym niezawodne wysłanie wiadomości powinno być modelowane przez lokalny outbox i idempotentnego konsumenta, a nie przez próbę obejmowania SMTP transakcją JPA.

```shell
mvn --batch-mode --no-transfer-progress -Dtest=LegacyUserServiceCharacterizationTest,RegisterUserUseCaseTest,RegisterUserIntegrationTest test
mvn --batch-mode --no-transfer-progress -Dtest=LegacyInvoiceBatchGoldenMasterTest,BranchByAbstractionInvoiceServiceTest,VersionedInvoiceExportContractTest,RoundingSemanticsCharacterizationTest test
```

---

## Refaktoryzacja jako proces małych kroków

Najważniejszym aspektem całego procesu jest sposób pracy. Refaktoryzacja legacy nie polega na jednorazowym „przepisaniu systemu”. Takie podejście zazwyczaj kończy się destabilizacją projektu.

Skuteczna refaktoryzacja opiera się na:

1. zabezpieczeniu zachowania testami,
2. wykonywaniu małych zmian,
3. ciągłym uruchamianiu testów,
4. stopniowym poprawianiu architektury,
5. ograniczaniu ryzyka regresji.

Każda zmiana powinna być niewielka, łatwa do cofnięcia i możliwa do zweryfikowania.

W większej migracji „mały krok” nie musi oznaczać małego diffu całego przedsięwzięcia. Oznacza zmianę jednej własności naraz: najpierw seam, potem test parity, później nowa implementacja, przełączenie niewielkiej części ruchu i dopiero usunięcie starej ścieżki. Po każdym kroku system pozostaje możliwy do wdrożenia.

---

## Podsumowanie

Refaktoryzacja systemu legacy jest przede wszystkim procesem odzyskiwania kontroli nad kodem. Jej celem nie jest idealna architektura ani „czysty kod” sam w sobie, ale możliwość bezpiecznego rozwijania systemu.

Najważniejsze elementy tego podejścia to:

- characterization tests,
- seam’y i dependency injection,
- oddzielenie domeny od infrastruktury,
- use case’y jako centrum logiki,
- małe i bezpieczne kroki refaktoryzacyjne,
- przewidywalne testy,
- jasne granice odpowiedzialności.

Dobrze przeprowadzona refaktoryzacja prowadzi do systemu, który jest łatwiejszy do zrozumienia, testowania i rozwijania — bez konieczności przepisywania całej aplikacji od zera.
