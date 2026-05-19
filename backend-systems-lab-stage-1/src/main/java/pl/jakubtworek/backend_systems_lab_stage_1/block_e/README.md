# Refaktoryzacja kodu legacy – podejście krok po kroku

## Wprowadzenie

Refaktoryzacja systemów legacy nie polega wyłącznie na „sprzątaniu kodu”. W praktyce jest to proces odzyskiwania kontroli nad aplikacją, która przez lata rozwijała się bez wyraźnych granic architektonicznych, odpowiednich testów i spójnych zasad projektowych. Typowy kod legacy miesza logikę biznesową z frameworkiem, bazą danych oraz integracjami zewnętrznymi, przez co każda zmiana staje się kosztowna i ryzykowna.

Kluczowym problemem takich systemów jest brak możliwości bezpiecznego wprowadzania zmian. Michael Feathers definiuje kod legacy bardzo prosto — jest to kod bez testów. Oznacza to, że głównym celem refaktoryzacji nie jest od razu poprawa architektury, ale stworzenie środowiska, w którym zmiany można wykonywać przewidywalnie i bez ryzyka regresji.

---

# Charakterization tests jako pierwszy krok

Najważniejszym etapem rozpoczęcia refaktoryzacji jest zabezpieczenie aktualnego zachowania systemu. W praktyce oznacza to tworzenie characterization tests, czyli testów opisujących istniejące działanie aplikacji — nawet jeśli obecne zachowanie nie jest idealne.

Podejście to ma istotne znaczenie psychologiczne i techniczne. Bez testów programista działa „na ślepo”, obawiając się, że każda modyfikacja może zepsuć fragment systemu, którego nie rozumie. Testy tworzą warstwę bezpieczeństwa umożliwiającą wykonywanie małych, kontrolowanych zmian.

Na tym etapie nie poprawia się jeszcze architektury. Priorytetem jest stabilizacja systemu.

---

# Seamy i rozbijanie zależności

Po zabezpieczeniu zachowania można zacząć stopniowo rozdzielać zależności. Feathers nazywa miejsca umożliwiające podmianę zachowania seamami. Są to punkty pozwalające testować kod w izolacji i odcinać go od infrastruktury.

Najczęściej oznacza to:

- przejście z bezpośredniego tworzenia obiektów (`new`) do dependency injection,
- wyodrębnienie interfejsów,
- parametryzację elementów trudnych do testowania (np. czasu),
- oddzielenie logiki biznesowej od frameworka.

Dzięki temu kod przestaje być „przyklejony” do Springa, JPA czy zewnętrznych API. Zależności zaczynają być jawne i możliwe do kontrolowania w testach.

Bardzo istotnym przykładem jest wstrzykiwanie `Clock` zamiast używania `LocalDate.now()`. Taka zmiana wydaje się drobna, ale eliminuje niedeterministyczne zachowanie testów i pozwala precyzyjnie kontrolować scenariusze biznesowe.

---

# Oddzielenie domeny od infrastruktury

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

# Use Case jako centralny element logiki

W dobrze zrefaktoryzowanym systemie logika biznesowa nie znajduje się ani w kontrolerach HTTP, ani w klasach infrastrukturalnych. Centralnym punktem stają się use case’y.

Use case reprezentuje konkretny scenariusz biznesowy, np. rejestrację użytkownika. Przyjmuje dane wejściowe, wykonuje reguły biznesowe i zwraca wynik operacji.

Najważniejsze jest to, że use case:

- nie zna Springa,
- nie zna HTTP,
- nie zależy od bazy danych,
- nie zawiera szczegółów infrastrukturalnych.

Takie podejście znacząco upraszcza testowanie i zwiększa czytelność systemu. Programista może analizować logikę biznesową bez konieczności rozumienia całego frameworka.

---

# Clean Code i uproszczenie logiki

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

# Obsługa błędów i fail-fast

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

# Testy jednostkowe i integracyjne

Po rozdzieleniu warstw możliwe staje się sensowne testowanie systemu.

Testy jednostkowe koncentrują się na logice biznesowej i działają bez infrastruktury. Są szybkie, deterministyczne i łatwe w utrzymaniu.

Testy integracyjne sprawdzają współpracę warstw, bazę danych i konfigurację frameworka.

Dobrze zrefaktoryzowany system posiada zdecydowanie więcej testów jednostkowych niż integracyjnych. Jest to zgodne z ideą piramidy testów — większość logiki powinna być możliwa do zweryfikowania bez uruchamiania całej aplikacji.

---

# Refaktoryzacja jako proces małych kroków

Najważniejszym aspektem całego procesu jest sposób pracy. Refaktoryzacja legacy nie polega na jednorazowym „przepisaniu systemu”. Takie podejście zazwyczaj kończy się destabilizacją projektu.

Skuteczna refaktoryzacja opiera się na:

1. zabezpieczeniu zachowania testami,
2. wykonywaniu małych zmian,
3. ciągłym uruchamianiu testów,
4. stopniowym poprawianiu architektury,
5. ograniczaniu ryzyka regresji.

Każda zmiana powinna być niewielka, łatwa do cofnięcia i możliwa do zweryfikowania.

---

# Podsumowanie

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