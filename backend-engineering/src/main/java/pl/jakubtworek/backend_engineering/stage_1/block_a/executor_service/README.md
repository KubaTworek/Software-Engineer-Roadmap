Współbieżne przetwarzanie zadań w Javie -- notatka techniczna
==================================================

Wprowadzenie
------------

Współczesne systemy backendowe są projektowane z założeniem równoległego przetwarzania wielu zadań. Obsługa zapytań HTTP, komunikacja z bazą danych, przetwarzanie wiadomości z kolejek czy wykonywanie operacji asynchronicznych wymaga wykorzystania wielu wątków. Kluczowym problemem nie jest jednak samo uruchamianie równoległych operacji, lecz **kontrolowanie sposobu, w jaki są one wykonywane**.

W praktyce oznacza to, że architektura systemu powinna oddzielać **logikę zadania od strategii jego wykonania**. Logika zadania opisuje *co* ma zostać wykonane, natomiast warstwa infrastrukturalna decyduje *jak* i *kiedy* zostanie ono uruchomione. Takie podejście pozwala zachować modularność systemu oraz umożliwia zmianę modelu wykonania bez ingerowania w logikę biznesową.

W ekosystemie Javy podstawowym narzędziem realizującym tę koncepcję jest **model executorów**, który umożliwia delegowanie zadań do zarządzanej puli wątków.

* * * * *

Zarządzanie wątkami i rola puli wątków
======================================

Tworzenie nowego wątku w JVM nie jest operacją trywialną. Każdy wątek wymaga przydzielenia pamięci stosu, rejestracji w systemie operacyjnym oraz ponosi koszt przełączania kontekstu podczas planowania pracy procesora. Jeśli aplikacja tworzy wątki bez kontroli, szybko prowadzi to do nadmiernego zużycia pamięci oraz spadku wydajności.

Z tego powodu w profesjonalnych systemach zamiast tworzyć nowe wątki dla każdego zadania stosuje się **pule wątków**. Pula wątków utrzymuje zestaw wcześniej utworzonych workerów, którzy pobierają zadania z kolejki i wykonują je w miarę dostępności zasobów. Dzięki temu liczba aktywnych wątków pozostaje kontrolowana, a system może obsługiwać duże ilości pracy bez kosztownego tworzenia nowych struktur w systemie operacyjnym.

Mechanizm ten wprowadza również naturalną warstwę buforującą. Zadania, które nie mogą być wykonane natychmiast, trafiają do kolejki i oczekują na dostępność wolnego wątku. Odpowiednia konfiguracja tej kolejki ma kluczowe znaczenie dla stabilności systemu.

* * * * *

Kolejki zadań i ich wpływ na zachowanie systemu
===============================================

Jednym z najważniejszych elementów konfiguracji mechanizmu przetwarzania zadań jest typ i rozmiar kolejki. Kolejka stanowi bufor pomiędzy producentem zadań a wątkami wykonującymi pracę.

Kolejki mogą działać w różnych trybach. W modelu bezpośredniego przekazania zadanie musi zostać natychmiast przejęte przez wolny wątek. Jeśli taki wątek nie istnieje, system musi utworzyć nowy lub odrzucić zadanie. Tego typu strategia minimalizuje opóźnienia w kolejce, ale może prowadzić do dynamicznego wzrostu liczby wątków.

Innym podejściem jest stosowanie kolejek nieograniczonych. Zadania mogą wówczas oczekiwać w kolejce dowolnie długo, a liczba wątków pozostaje stabilna. Rozwiązanie to jest jednak ryzykowne w systemach produkcyjnych, ponieważ w sytuacji zwiększonego obciążenia kolejka może rosnąć bez limitu i doprowadzić do wyczerpania pamięci.

Najbardziej przewidywalnym rozwiązaniem jest **kolejka o ograniczonej pojemności**. Pozwala ona kontrolować maksymalną liczbę oczekujących zadań i wymusza określone zachowanie systemu w sytuacji przeciążenia.

* * * * *

Zachowanie systemu pod przeciążeniem
====================================

Każdy system ostatecznie napotka moment, w którym liczba napływających zadań przekroczy jego zdolność przetwarzania. Kluczowym aspektem projektowania systemów współbieżnych jest zatem określenie, co powinno wydarzyć się w takiej sytuacji.

Najprostszą strategią jest natychmiastowe odrzucenie nowych zadań. Taki model określany jest jako **fail-fast**. System sygnalizuje przeciążenie poprzez zgłoszenie błędu, dzięki czemu wyższe warstwy aplikacji mogą zdecydować o ponowieniu operacji, ograniczeniu ruchu lub zastosowaniu mechanizmów retry.

Alternatywną strategią jest wykonywanie zadań przez wątek, który je zgłasza. Powoduje to spowolnienie producenta zadań i wprowadza mechanizm regulacji przepływu pracy. Takie podejście często określa się mianem **backpressure**, ponieważ zmniejsza tempo generowania nowych zadań, gdy system znajduje się pod dużym obciążeniem.

W systemach produkcyjnych decyzja o wyborze strategii przeciążenia ma ogromne znaczenie dla stabilności aplikacji.

* * * * *

Backpressure jako mechanizm stabilizacji systemu
================================================

Backpressure jest jednym z kluczowych mechanizmów stosowanych w nowoczesnych systemach rozproszonych. Polega on na tym, że komponent produkujący zadania zostaje spowolniony, gdy komponent przetwarzający nie nadąża z ich obsługą.

Brak takiego mechanizmu prowadzi często do tzw. spirali degradacji wydajności. Gdy liczba zadań rośnie, system próbuje zwiększyć liczbę wątków, aby nadążyć z przetwarzaniem. Większa liczba wątków generuje jednak więcej przełączeń kontekstu, co z kolei obniża wydajność procesora. W efekcie zadania są wykonywane jeszcze wolniej, co powoduje dalszy wzrost kolejki.

Backpressure przerywa ten mechanizm poprzez ograniczenie szybkości generowania nowych zadań.

* * * * *

Charakter pracy: CPU-bound vs IO-bound
======================================

Konfiguracja systemu przetwarzania zadań powinna uwzględniać charakter wykonywanej pracy. Zadania obliczeniowe, które intensywnie wykorzystują procesor, wymagają innej strategii niż operacje oczekujące na zasoby zewnętrzne.

W przypadku operacji obliczeniowych liczba wątków powinna być zbliżona do liczby dostępnych rdzeni procesora. Większa liczba wątków nie zwiększa wydajności, ponieważ procesor i tak może wykonywać ograniczoną liczbę operacji jednocześnie.

Z kolei zadania związane z operacjami wejścia/wyjścia często spędzają znaczną część czasu w stanie oczekiwania. W takich przypadkach większa liczba wątków może zwiększyć przepustowość systemu, ponieważ inne zadania mogą być wykonywane w czasie, gdy część wątków czeka na odpowiedź z zewnętrznego systemu.

* * * * *

Monitoring i obserwowalność
===========================

Systemy wykorzystujące współbieżność powinny być stale monitorowane. Bez odpowiednich metryk trudno zrozumieć, jak system zachowuje się pod obciążeniem.

Szczególnie istotne są informacje dotyczące liczby aktywnych wątków, maksymalnej wielkości puli, liczby oczekujących zadań oraz ogólnego zużycia pamięci. Analiza tych danych pozwala wykryć sytuacje, w których system zaczyna zbliżać się do granic swojej wydajności.

Istotne jest również monitorowanie opóźnień przetwarzania zadań. Długie czasy oczekiwania w kolejce często wskazują na niewłaściwą konfigurację puli wątków lub na problem z wydajnością poszczególnych operacji.

* * * * *

Najczęstsze problemy projektowe
===============================

Jednym z najczęstszych błędów w projektowaniu systemów współbieżnych jest stosowanie nieograniczonych struktur danych. Nieograniczone kolejki lub dynamiczne tworzenie wątków mogą działać poprawnie w środowisku testowym, lecz w produkcji prowadzą do niekontrolowanego wzrostu zużycia zasobów.

Innym częstym problemem jest mieszanie różnych typów zadań w jednej puli wątków. Zadania blokujące mogą wówczas uniemożliwić wykonywanie operacji wymagających niskiego opóźnienia. Z tego powodu w większych systemach często stosuje się separację zasobów i dedykowane pule dla różnych typów pracy.

* * * * *

Podsumowanie
============

Efektywne zarządzanie współbieżnością w Javie polega przede wszystkim na świadomej kontroli zasobów. Należy ograniczać liczbę wątków, kontrolować rozmiar kolejek oraz jasno określać strategię zachowania systemu w sytuacji przeciążenia.

Dobrze zaprojektowany system nie próbuje wykonać nieskończonej liczby zadań jednocześnie. Zamiast tego stawia na przewidywalność, stabilność oraz mechanizmy regulujące przepływ pracy. Dzięki temu może zachować wysoką wydajność nawet w warunkach dużego obciążenia.