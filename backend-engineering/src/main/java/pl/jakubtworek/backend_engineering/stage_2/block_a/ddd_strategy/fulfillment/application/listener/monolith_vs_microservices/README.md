# Monolit vs. mikroserwisy – kryteria wyboru i koszty

## Wprowadzenie

Wybór między monolitem, modularnym monolitem a mikroserwisami jest jedną z najważniejszych decyzji architektonicznych przy projektowaniu systemu e-commerce. Nie jest to decyzja wyłącznie techniczna. W praktyce dotyczy ona również sposobu pracy zespołu, kosztów utrzymania, tempa dostarczania zmian, poziomu dojrzałości organizacji oraz przewidywanej skali systemu.

Częstym błędem jest traktowanie mikroserwisów jako naturalnie lepszej lub nowocześniejszej alternatywy dla monolitu. W rzeczywistości mikroserwisy nie eliminują złożoności, lecz przenoszą ją z poziomu kodu do poziomu infrastruktury, komunikacji sieciowej, monitoringu, deploymentu i zarządzania danymi. Podobnie monolit nie musi oznaczać złej architektury. Źle zaprojektowany monolit może stać się trudnym w utrzymaniu „big ball of mud”, ale dobrze zaprojektowany modularny monolit może być bardzo stabilną, tanią i efektywną architekturą przez długi czas.

W kontekście systemu e-commerce wybór architektury powinien wynikać przede wszystkim z realnych potrzeb biznesowych. Mały sklep, MVP, system budowany przez jeden zespół lub produkt na wczesnym etapie rozwoju zwykle nie potrzebuje mikroserwisów. Z kolei duża platforma sprzedażowa, rozwijana przez wiele niezależnych zespołów, z różnymi wymaganiami skalowania dla płatności, wysyłki, katalogu produktów i koszyka, może z czasem uzasadniać przejście do architektury mikroserwisowej.

## Monolit

Monolit to aplikacja, w której wszystkie główne funkcje systemu znajdują się w jednym wdrażalnym artefakcie. W najprostszym wariancie monolit korzysta z jednej bazy danych, jednego procesu aplikacyjnego i jednego pipeline’u deploymentowego. Takie podejście jest szczególnie atrakcyjne na początku projektu, ponieważ ogranicza liczbę decyzji infrastrukturalnych i pozwala szybko dostarczać funkcjonalność.

Największą zaletą monolitu jest prostota. Programiści pracują w jednym repozytorium, uruchamiają jeden system lokalnie, debugują kod w jednym procesie i zwykle korzystają z jednej bazy danych. Testowanie przepływów biznesowych jest łatwiejsze, ponieważ nie trzeba symulować wielu usług, brokerów wiadomości ani komunikacji sieciowej. Wdrożenie również jest prostsze, ponieważ cały system jest publikowany jako jeden artefakt.

Monolit bardzo dobrze sprawdza się w małych zespołach. Jeżeli nad systemem pracuje kilka osób, koszt podziału na wiele usług zwykle przewyższa korzyści. W takim przypadku najważniejsze jest szybkie dostarczanie wartości biznesowej, a nie budowanie złożonej platformy infrastrukturalnej. Monolit umożliwia krótszy time-to-market, prostszą konfigurację środowisk oraz mniejsze koszty początkowe.

Problem pojawia się wtedy, gdy monolit rośnie bez kontroli. Jeżeli w kodzie nie istnieją wyraźne granice modułów, poszczególne obszary biznesowe zaczynają się mieszać. Logika koszyka zaczyna zależeć od logiki płatności, moduł wysyłki bezpośrednio odczytuje tabele zamówień, a zmiana w jednym fragmencie systemu wymaga testowania dużej części aplikacji. W takim momencie monolit przestaje być prosty, a zaczyna być strukturą trudną do zmiany.

Duży monolit utrudnia również równoległą pracę wielu zespołów. Jeżeli wszyscy pracują na tym samym kodzie, tym samym modelu danych i tym samym pipeline’ie, pojawiają się konflikty, długie buildy, trudne merge’e oraz ryzyko regresji. Skalowanie także jest ograniczone, ponieważ najczęściej skaluje się całą aplikację, nawet jeżeli obciążony jest tylko jeden jej fragment. Jeżeli najwięcej ruchu generuje katalog produktów, a płatności są używane znacznie rzadziej, monolit i tak wymaga powielania całej aplikacji.

Monolit ma również słabszą izolację awarii. Błąd w jednym module może przeciążyć cały proces, zablokować wspólną bazę danych albo doprowadzić do niedostępności całego systemu. Nie oznacza to jednak, że każdy monolit jest zły. Oznacza to jedynie, że monolit wymaga dyscypliny architektonicznej, świadomego podziału domeny i kontroli zależności.

## Mikroserwisy

Mikroserwisy to podejście, w którym system jest podzielony na wiele niezależnie wdrażalnych usług. Każda usługa odpowiada za konkretny fragment domeny biznesowej, posiada własną logikę, własne API i najczęściej własną bazę danych. W systemie e-commerce osobnymi mikroserwisami mogą być na przykład katalog produktów, koszyk, zamówienia, płatności, wysyłka, fakturowanie, promocje i obsługa klientów.

Największą zaletą mikroserwisów jest niezależność. Poszczególne usługi mogą być rozwijane, testowane, wdrażane i skalowane osobno. Jeżeli katalog produktów obsługuje bardzo duży ruch, można skalować tylko ten serwis, bez konieczności powielania całej aplikacji. Jeżeli moduł płatności wymaga szczególnej niezawodności i bezpieczeństwa, może mieć własne procedury wdrożeniowe, własne mechanizmy monitorowania i bardziej restrykcyjne reguły dostępu.

Mikroserwisy dobrze wspierają duże organizacje. Jeżeli nad systemem pracuje wiele zespołów, każdy zespół może odpowiadać za własny serwis lub grupę serwisów. Dzięki temu łatwiej przypisać odpowiedzialność, ograniczyć konflikty w kodzie i umożliwić niezależne tempo rozwoju różnych części systemu. Mikroserwisy pozwalają również na większą swobodę technologiczną. Jeden serwis może być napisany w Javie, inny w Go, a jeszcze inny w Node.js, o ile organizacja potrafi kontrolować taki poziom różnorodności.

Ta niezależność ma jednak wysoką cenę. Mikroserwisy znacząco zwiększają złożoność operacyjną. Każda usługa wymaga osobnego deploymentu, konfiguracji, monitoringu, logowania, alertów, pipeline’u CI/CD, testów kontraktowych i mechanizmów obserwowalności. Debugowanie przestaje być lokalnym problemem jednego procesu, a staje się analizą rozproszonego przepływu przez wiele usług, baz danych i kolejek komunikatów.

W mikroserwisach pojawia się również złożoność komunikacyjna. Wywołanie metody w monolicie jest szybkie, lokalne i stosunkowo przewidywalne. Wywołanie innego mikroserwisu odbywa się przez sieć, a sieć jest zawodna. Usługa może być chwilowo niedostępna, odpowiedź może się opóźnić, komunikat może zostać przetworzony więcej niż raz, a częściowy błąd może doprowadzić do niespójności danych. Dlatego mikroserwisy wymagają mechanizmów takich jak retry, timeouty, circuit breaker, idempotentność, distributed tracing i centralne logowanie.

Jeszcze większym wyzwaniem jest zarządzanie danymi. W monolicie można użyć jednej transakcji bazodanowej, aby zapisać zamówienie, płatność i rezerwację magazynu. W mikroserwisach każdy z tych obszarów może mieć własną bazę danych. Nie można wtedy polegać na jednej transakcji ACID obejmującej cały proces. Zamiast tego trzeba stosować eventual consistency, zdarzenia domenowe, wzorzec Saga, outbox pattern i mechanizmy kompensacji.

Mikroserwisy są więc uzasadnione wtedy, gdy organizacja faktycznie potrzebuje niezależnego skalowania, niezależnych deploymentów i autonomii zespołów. Nie powinny być wybierane wyłącznie dlatego, że są popularne. Przedwczesne przejście na mikroserwisy często prowadzi do systemu trudniejszego, droższego i wolniejszego w rozwoju niż dobrze zaprojektowany monolit.

## Modular monolith

Modular monolith jest kompromisem między klasycznym monolitem a mikroserwisami. System nadal jest wdrażany jako jedna aplikacja, ale wewnątrz posiada wyraźnie wydzielone moduły odpowiadające za konkretne obszary domenowe. W systemie e-commerce takimi modułami mogą być sprzedaż, płatności, wysyłka, fakturowanie, katalog produktów i magazyn.

Najważniejszą cechą modularnego monolitu jest separacja logiczna. Moduły nie powinny bezpośrednio korzystać ze swoich wewnętrznych klas, tabel ani implementacji. Komunikacja powinna odbywać się przez jawne interfejsy, zdarzenia wewnętrzne albo dobrze zdefiniowane kontrakty. Dzięki temu system zachowuje prostotę wdrożenia monolitu, ale jednocześnie przygotowuje się na przyszłą dekompozycję.

Modular monolith pozwala uniknąć najczęstszego błędu mikroserwisów, czyli zbyt wczesnego podziału systemu. Na początku projektu granice domenowe często nie są jeszcze dobrze znane. Wymagania się zmieniają, pojęcia biznesowe są doprecyzowywane, a zespoły dopiero odkrywają rzeczywiste procesy. W takiej sytuacji fizyczny podział na mikroserwisy może utrwalić błędne granice i utrudnić późniejsze zmiany. Modularny monolit pozwala eksperymentować z granicami taniej, ponieważ zmiana struktury modułów wewnątrz jednej aplikacji jest prostsza niż migracja wielu usług produkcyjnych.

To podejście dobrze współgra z Domain-Driven Design. Każdy moduł może odpowiadać jednemu bounded contextowi, posiadać własny model domenowy i ukrywać szczegóły implementacyjne przed resztą systemu. Dzięki temu architektura pozostaje uporządkowana, nawet jeśli system jest nadal wdrażany jako jeden artefakt.

Modularny monolit nie rozwiązuje wszystkich problemów. Nadal skaluje się najczęściej jako całość i nadal awaria procesu może wpłynąć na cały system. Nie daje też pełnej autonomii deploymentu. Mimo to w wielu przypadkach jest najlepszym punktem startowym, ponieważ łączy prostotę operacyjną z dobrą strukturą kodu. Jeżeli w przyszłości jakiś moduł rzeczywiście wymaga niezależnego skalowania albo osobnego lifecycle’u, można go wydzielić jako mikroserwis z dużo mniejszym ryzykiem.

## Porównanie podejść

| Cecha / aspekt | Monolit | Modular monolith | Mikroserwisy |
|---|---|---|---|
| Model wdrożenia | Jedna aplikacja wdrażana jako jeden artefakt | Jedna aplikacja z logicznie wydzielonymi modułami | Wiele niezależnie wdrażalnych usług |
| Baza danych | Najczęściej jedna wspólna baza | Może być jedna baza z separacją schematów lub logicznych obszarów | Najczęściej osobna baza dla każdego serwisu |
| Skalowanie | Skalowanie całej aplikacji | Skalowanie całej aplikacji, choć z lepszą organizacją wewnętrzną | Skalowanie wybranych usług |
| Koszt początkowy | Niski | Niski lub umiarkowany | Wysoki |
| Złożoność operacyjna | Niska | Niska lub umiarkowana | Wysoka |
| Debugowanie | Najłatwiejsze | Relatywnie łatwe | Trudne, wymaga tracingu i centralnych logów |
| Autonomia zespołów | Ograniczona | Częściowa | Wysoka |
| Izolacja awarii | Niska | Umiarkowana logicznie, niska procesowo | Wyższa, jeśli system jest dobrze zaprojektowany |
| Szybkość startu | Bardzo wysoka | Wysoka | Niższa |
| Elastyczność technologiczna | Niska | Niska lub umiarkowana | Wysoka |
| Ryzyko nadmiernej złożoności | Niskie na początku, wysokie przy dużym wzroście | Umiarkowane | Wysokie od początku |

## Kryteria wyboru architektury

Wybór architektury powinien zaczynać się od pytania o aktualny etap systemu. Jeżeli produkt jest na etapie MVP, zespół jest mały, a domena nie została jeszcze dobrze poznana, monolit lub modularny monolit będzie zwykle lepszym wyborem. Pozwala szybciej dostarczać funkcjonalność, taniej zmieniać założenia i unikać przedwczesnych kosztów infrastrukturalnych.

Jeżeli system zaczyna rosnąć, ale nadal jest rozwijany przez jeden lub dwa zespoły, najlepszym kierunkiem jest zwykle modularny monolit. Pozwala on uporządkować kod, wydzielić bounded contexts i ograniczyć coupling bez wprowadzania komunikacji sieciowej między modułami. Jest to etap, w którym organizacja może uczyć się domeny i budować stabilne granice architektoniczne.

Mikroserwisy warto rozważać dopiero wtedy, gdy pojawiają się realne symptomy potrzeby dekompozycji. Takimi symptomami mogą być różne wymagania skalowania, częste konflikty deploymentowe, zbyt długi czas wydawania zmian, potrzeba niezależności zespołów albo duża różnica w wymaganiach niezawodności poszczególnych części systemu. Mikroserwis powinien być odpowiedzią na konkretny problem, a nie domyślnym wyborem architektonicznym.

Istotne jest również doświadczenie zespołu. Mikroserwisy wymagają dojrzałości w obszarze DevOps, automatyzacji, obserwowalności, testów kontraktowych i projektowania integracji. Jeżeli organizacja nie potrafi niezawodnie wdrażać jednej aplikacji, podział jej na wiele usług zwykle pogorszy sytuację.

## Koszty mikroserwisów

Koszty mikroserwisów są często niedoszacowane. Najbardziej widoczny jest koszt infrastruktury: więcej procesów, więcej baz danych, więcej pipeline’ów, więcej konfiguracji, więcej środowisk i więcej narzędzi monitorujących. Jednak równie istotny jest koszt poznawczy. Programista musi rozumieć nie tylko kod własnego serwisu, ale także kontrakty, zdarzenia, zależności, wersje API i skutki uboczne komunikacji z innymi usługami.

Mikroserwisy zwiększają również koszt testowania. Test jednostkowy pojedynczego serwisu nie wystarcza, ponieważ wiele błędów pojawia się dopiero na granicach między usługami. Potrzebne są testy kontraktowe, testy integracyjne, środowiska testowe z wieloma komponentami oraz strategia obsługi wersji API. Bez tego łatwo doprowadzić do sytuacji, w której każdy serwis działa osobno, ale cały system nie działa poprawnie.

Kolejny koszt dotyczy spójności danych. W monolicie wiele operacji można zamknąć w jednej transakcji. W mikroserwisach trzeba zaakceptować, że system przez pewien czas może być niespójny. Zamówienie może zostać utworzone, ale płatność może być jeszcze w trakcie autoryzacji. Produkt może zostać dodany do koszyka, ale jego dostępność może zmienić się chwilę później. Takie przypadki wymagają świadomego modelowania procesów biznesowych, komunikacji zdarzeniowej i mechanizmów kompensacyjnych.

Nie można też pominąć kosztu obserwowalności. W monolicie log błędu często wystarcza do zdiagnozowania problemu. W mikroserwisach konieczne jest śledzenie całej ścieżki żądania przez wiele usług. Bez distributed tracingu, korelacji logów, metryk i alertów mikroserwisy szybko stają się trudne do utrzymania.

## Typowe błędy decyzyjne

Najczęstszym błędem jest wybór mikroserwisów zbyt wcześnie. Jeżeli domena nie jest jeszcze dobrze poznana, podział systemu na usługi będzie oparty na przypuszczeniach. Błędne granice mikroserwisów są kosztowne, ponieważ każda zmiana wymaga migracji danych, zmiany kontraktów, aktualizacji pipeline’ów i koordynacji wielu deploymentów.

Drugim błędem jest mylenie mikroserwisów z porządkiem architektonicznym. System może mieć wiele mikroserwisów i nadal być źle zaprojektowany. Jeżeli serwisy są silnie sprzężone, współdzielą bazę danych, wymagają synchronicznych łańcuchów wywołań i nie mają jasno określonych odpowiedzialności, to w praktyce powstaje rozproszony monolit. Jest on zwykle trudniejszy w utrzymaniu niż klasyczny monolit.

Trzecim błędem jest ignorowanie kosztów operacyjnych. Mikroserwisy wymagają platformy, automatyzacji, monitoringu i kompetencji. Bez tego zespół zaczyna tracić czas na problemy infrastrukturalne zamiast na rozwój funkcji biznesowych.

Czwartym błędem jest brak strategii danych. Samo wydzielenie serwisów bez przemyślenia własności danych prowadzi do współdzielonych baz, ukrytych zależności i trudnych migracji. Jeżeli mikroserwis ma być autonomiczny, powinien posiadać własny model danych i jasno określone kontrakty wymiany informacji.

## Rekomendowane podejście ewolucyjne

Najbezpieczniejszą strategią jest podejście ewolucyjne. System można rozpocząć jako monolit, ale od początku projektować go z myślą o modularności. W praktyce oznacza to podział kodu według obszarów domenowych, unikanie współdzielonych modeli, ograniczanie zależności między modułami i stosowanie jawnych kontraktów komunikacji.

W kolejnym kroku warto rozwijać system jako modularny monolit. To pozwala stabilizować granice bounded contexts bez ponoszenia kosztów mikroserwisów. Moduły mogą komunikować się przez interfejsy aplikacyjne lub zdarzenia wewnętrzne, a ich modele danych mogą być logicznie separowane. Dzięki temu zespół zyskuje lepszą strukturę kodu i przygotowuje system do potencjalnego wydzielenia wybranych części.

Dopiero gdy pojawi się konkretny powód biznesowy lub techniczny, wybrany moduł można wydzielić jako mikroserwis. Dobrym kandydatem jest moduł, który ma stabilne granice, własny model danych, niezależny cykl zmian i inne wymagania skalowania niż reszta systemu. W systemie e-commerce takim kandydatem może być na przykład katalog produktów, wyszukiwarka, płatności albo moduł powiadomień.

Najważniejsze jest to, aby granice logiczne istniały przed granicami fizycznymi. Jeżeli moduł nie jest dobrze odseparowany wewnątrz monolitu, wydzielenie go do osobnego serwisu nie rozwiąże problemu. Zwykle jedynie przeniesie coupling z poziomu kodu na poziom sieci.

## Podsumowanie

Monolit, modularny monolit i mikroserwisy nie są konkurującymi modami, lecz różnymi odpowiedziami na różne problemy organizacyjne i techniczne. Monolit jest najlepszy wtedy, gdy liczy się prostota, niski koszt i szybkie tempo rozwoju. Mikroserwisy są uzasadnione wtedy, gdy system jest duży, zespoły potrzebują autonomii, a poszczególne obszary wymagają niezależnego skalowania i wdrażania. Modularny monolit jest najczęściej najlepszym kompromisem, ponieważ pozwala zachować prostotę operacyjną, a jednocześnie wprowadza porządek domenowy i przygotowuje system do przyszłej dekompozycji.

W praktyce najlepsza architektura to nie ta, która używa najbardziej zaawansowanych technologii, lecz ta, która odpowiada aktualnym potrzebom biznesu i poziomowi dojrzałości zespołu. Mikroserwisy są potężnym narzędziem, ale mają sens dopiero wtedy, gdy organizacja jest gotowa zapłacić ich koszt. W wielu przypadkach dobrze zaprojektowany modularny monolit będzie rozwiązaniem bardziej pragmatycznym, tańszym i bezpieczniejszym.
