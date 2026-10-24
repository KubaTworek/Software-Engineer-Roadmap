# Weryfikacja przykładów z dokumentacji

[Moduł i rozdziały](../README.md) · [Skrypt weryfikacji](../scripts/check-examples.ps1) · [Testy zachowania](InterviewSmoke.java)

To mały zestaw kontroli materiału edukacyjnego, bez aplikacji, Maven/Gradle i bibliotek zewnętrznych. Wymaga PowerShell 7, a dla kompilacji JDK 21 lub nowszego. Narzędzia są brane z parametru JdkHome, JAVA_HOME albo PATH. Skrypt wypisuje faktyczną wersję runtime.

## Uruchomienie

Z katalogu interview-preparation uruchom `pwsh -File scripts/check-examples.ps1`. Jeżeli JDK nie jest wskazany poprawnie, podaj `-JdkHome` z katalogiem zawierającym bin/java i bin/javac. Parametr jest ścieżką lokalnego narzędzia, nie ustawieniem wersji repozytorium.

Kontrola bez narzędzi Java: `pwsh -File scripts/check-examples.ps1 -DocumentsOnly`. Ten wariant jawnie pomija kompilację oraz testy zachowania i nie może być raportowany jako pełna weryfikacja.

Spisy pytań odświeża `pwsh -File scripts/update-navigation.ps1`. Wariant `-Check` tylko sprawdza aktualność i zgłasza błąd, zamiast zmieniać pliki.

Z katalogu głównego repozytorium dodatkowo uruchom `pwsh -File scripts/check-documentation.ps1`, aby sprawdzić lokalne pliki, odsyłacze i kotwice również poza tym modułem.

## Co jest sprawdzane

- Kanoniczne identyfikatory i ciągła numeracja pytań, liczności w README oraz aktualność indeksu/spisów.
- Jawny kontekst każdego bloku w rozdziałach i materiale diagnostycznym; brakujące, powtórzone albo osierocone oznaczenie jest błędem.
- Zgodność opisu kontekstu z kategorią, językiem i odpowiedzią, do której należy przykład; zamknięcie bloków i brak błędnych znaczników tekstu.
- Regresje parsera: poprawny blok, brak kontekstu, niezamknięty blok, zły opis/język oraz nagłówek wewnątrz kodu.
- Składnia kontraktów JSON. Nie oznacza to walidacji ich biznesowego znaczenia.
- Kompilacja oznaczonych fragmentów JDK wyodrębnionych z Markdown, z parametrem javac --release 21. Elementy klasy są otaczane klasą, instrukcje — metodą pomocniczą throws Exception. Nie poprawiamy kodu przykładu podczas ekstrakcji.
- Testy zachowania na rzeczywistych wyodrębnionych metodach: małe losowe wejścia porównywane z prostymi oracle, granice, LRU, równy podział jednostek, snapshoty/kopie defensywne, tracker offsetów i współbieżne CAS.

Testy nie kopiują implementacji z Markdown do osobnej „poprawnej” wersji. Wywołują klasy z tych samych bloków, więc zmiana kodu w odpowiedzi zmienia weryfikowany program. Proste oracle enumerują małe przypadki, zamiast powtarzać optymalny algorytm. Seed jest stały, a oczekiwanie na taski i procesy ma limity czasu.

## Kategorie kontekstu

Każdy blok ma stabilne oznaczenie example:ID-PYTANIA-N i widoczny opis. Reguły opisów są w [DocumentTools.ps1](DocumentTools.ps1).

| Kategoria | Zakres dowodu |
| --- | --- |
| java-members / java-statements | kompilacja z API/docelowym bytecode Java 21; wybrane kontrakty także testowane |
| java-members-negative | błędna logika może się kompilować; test może odtworzyć jej błąd, nie uznaje jej za poprawne rozwiązanie |
| java-sketch | fragment wymaga pól/metod otoczenia; bez automatycznej kompilacji |
| spring-sketch / jpa-sketch / jdbc-sketch | wymaga frameworka, domeny, mappingu lub DB; nie ma testu integracyjnego tego szkicu |
| sql-template / jpql-template | wymaga schematu/encji i parametrów; nie jest wykonywany na bazie przez ten skrypt |
| json-contract | parsowanie składni JSON |
| http-contract / properties-fragment / yaml-fragment | ilustracja kontraktu albo konfiguracji; bez wywołania API i wdrożenia |
| shell-command | polecenia nie są wykonywane, więc przykładowe PID/ścieżki nie dotykają systemu |
| text-model / mermaid-diagram / synthetic-diagnostic | materiał opisowy; brak twierdzenia o pomiarze produkcji lub wykonaniu modelu |

Dodając przykład, wybierz kategorię, nadaj kolejny identyfikator w obrębie pytania i skopiuj aktualny opis kontekstu z reguł. Skrypt odrzuci niepełną deklarację. Nowy fragment JDK o nowym ID zostanie automatycznie dodany do kompilacji. Jeśli test zachowania odwołuje się do zmienionego kontraktu/nazwy metody, wymaga świadomej aktualizacji.

## Granice i artefakty

Kompilacja --release 21 nie oznacza wykonania na runtime JDK 21: test działa na wskazanym JDK, którego wersja jest wypisana. Przenośność runtime i zachowanie frameworka mogą wymagać osobnych testów w odpowiednim laboratorium. Dwa poprawne uruchomienia stress testu nie dowodzą braku data race; gwarancje JMM uzasadnia protokół happens-before.

Źródła pomocnicze, argumenty javac i class files trafiają do unikalnego katalogu systemowego TEMP. Skrypt usuwa tylko własny katalog po sprawdzeniu jego położenia i nazwy. Nie zapisuje tych artefaktów w repozytorium, nie uruchamia kontenerów, klientów HTTP, poleceń diagnostycznych ani migracji.
