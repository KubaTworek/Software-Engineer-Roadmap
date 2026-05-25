# Blok C – Architektura Cloud na Google Cloud Platform

## Wprowadzenie

Ten dokument stanowi teoretyczne omówienie podejścia do projektowania nowoczesnego backendu działającego w środowisku Google Cloud Platform. Głównym celem architektury cloud-native jest tworzenie systemów skalowalnych, odpornych na awarie, łatwych w obserwowaniu i możliwie efektywnych kosztowo. W praktyce oznacza to odejście od projektowania infrastruktury jako stałego, ręcznie utrzymywanego zaplecza serwerowego na rzecz usług zarządzanych, automatycznego skalowania oraz świadomego przenoszenia odpowiedzialności operacyjnej na platformę chmurową.

Architektura opisana w tym bloku bazuje na założeniach zgodnych z dobrymi praktykami Google Cloud, wzorcami cloud-native oraz zasadami znanymi z Well-Architected Framework. Chociaż raport odnosi się do konkretnych usług GCP, takich jak Cloud Run, Cloud SQL, Memorystore, Pub/Sub czy Secret Manager, jego główna wartość polega na pokazaniu sposobu myślenia o systemie. Backend nie powinien być traktowany jako pojedyncza aplikacja uruchomiona na serwerze, ale jako zestaw współpracujących komponentów, z których każdy pełni jasno określoną rolę i może być skalowany, monitorowany oraz zabezpieczany niezależnie.

## Założenia architektury cloud-native

Podstawową zasadą architektury cloud-native jest bezstanowość warstwy aplikacyjnej. Oznacza to, że pojedyncza instancja aplikacji nie powinna przechowywać lokalnie informacji wymaganych do obsłużenia kolejnych żądań użytkownika. Stan powinien zostać przeniesiony do wyspecjalizowanych usług zewnętrznych, takich jak relacyjna baza danych, cache, kolejka komunikatów lub magazyn obiektowy. Dzięki temu instancje aplikacji można swobodnie tworzyć i usuwać, a system może reagować na zmiany obciążenia bez ryzyka utraty danych.

W praktyce bezstanowość jest jednym z warunków skutecznego autoskalowania. Jeżeli aplikacja nie jest powiązana z lokalnym stanem konkretnej maszyny lub kontenera, platforma może uruchamiać dodatkowe instancje wtedy, gdy ruch rośnie, oraz usuwać je, gdy zapotrzebowanie spada. Taki model jest szczególnie dobrze dopasowany do Cloud Run, gdzie kontenery są skalowane automatycznie na podstawie liczby żądań i konfiguracji współbieżności. Architekt nie musi projektować infrastruktury pod maksymalny przewidywany ruch przez cały czas, lecz może pozwolić platformie reagować dynamicznie.

Drugim ważnym założeniem jest projektowanie systemu z myślą o awariach. W środowisku rozproszonym nie należy zakładać, że wszystkie komponenty zawsze będą dostępne. Baza danych może chwilowo odpowiadać wolniej, usługa zewnętrzna może przekroczyć limit czasu, a kolejka może dostarczyć komunikat ponownie. Dlatego aplikacja powinna obsługiwać timeouty, ponowienia, idempotencję i kontrolowane błędy. Odporność systemu nie wynika wyłącznie z infrastruktury, lecz z połączenia właściwego kodu aplikacyjnego, konfiguracji platformy i obserwowalności.

## Warstwa aplikacyjna i usługi stateless

Warstwa aplikacyjna powinna być projektowana jako zbiór usług stateless. Każda usługa przyjmuje żądanie, przetwarza je, komunikuje się z zależnościami zewnętrznymi i zwraca odpowiedź, ale nie zakłada, że kolejne żądanie trafi do tej samej instancji. Taki model upraszcza skalowanie poziome, ponieważ zwiększenie przepustowości systemu polega na uruchomieniu większej liczby identycznych instancji.

Istotnym elementem takiej architektury są mechanizmy health check i readiness check. Health check odpowiada na pytanie, czy aplikacja jako proces działa poprawnie, natomiast readiness check określa, czy instancja jest gotowa do przyjmowania ruchu. Różnica jest ważna, ponieważ aplikacja może być uruchomiona, ale jeszcze niegotowa, na przykład z powodu braku połączenia z bazą danych, niewczytanych sekretów lub trwającej inicjalizacji. Poprawne rozdzielenie tych mechanizmów pozwala platformie kierować ruch wyłącznie do instancji faktycznie zdolnych do obsługi żądań.

Konteneryzacja aplikacji, najczęściej przez Dockerfile, pełni w tym modelu rolę standaryzacji środowiska uruchomieniowego. Obraz kontenera powinien być możliwie mały, deterministyczny i bezpieczny. W praktyce oznacza to unikanie zbędnych pakietów, korzystanie z wieloetapowego budowania obrazu oraz uruchamianie procesu aplikacji w sposób przewidywalny. Kontener nie powinien być traktowany jako mała maszyna wirtualna, lecz jako przenośny artefakt aplikacyjny.

## Cloud Run jako środowisko uruchomieniowe

Cloud Run jest naturalnym wyborem dla wielu backendów HTTP, ponieważ łączy prostotę wdrażania kontenerów z automatycznym skalowaniem i modelem płatności zależnym od użycia. W architekturze opartej na Cloud Run szczególne znaczenie mają parametry autoskalowania: minimalna liczba instancji, maksymalna liczba instancji oraz współbieżność, czyli liczba żądań obsługiwanych równocześnie przez jedną instancję.

Minimalna liczba instancji wpływa głównie na ograniczenie cold startów i zapewnienie podstawowej gotowości usługi. Zbyt wysoka wartość może jednak generować niepotrzebny koszt, zwłaszcza gdy ruch jest nieregularny. Maksymalna liczba instancji pełni funkcję bezpiecznika kosztowego i technicznego. Chroni system przed niekontrolowanym wzrostem liczby kontenerów, ale jednocześnie może stać się ograniczeniem przepustowości. Współbieżność wymaga szczególnie świadomego dobrania, ponieważ zbyt wysoka wartość może przeciążać pojedynczą instancję, a zbyt niska prowadzić do nadmiernego skalowania i wyższych kosztów.

Cloud Run dobrze wspiera model usług stateless, ale nie rozwiązuje automatycznie wszystkich problemów architektonicznych. Aplikacja nadal musi prawidłowo obsługiwać limity czasu, sygnały zakończenia procesu, bezpieczne zamykanie połączeń i błędy zależności. Platforma może uruchomić więcej instancji, lecz jeżeli baza danych, cache lub zewnętrzne API nie są przygotowane na większy ruch, autoskalowanie warstwy aplikacyjnej może jedynie przenieść problem w inne miejsce.

## Warstwa danych: Cloud SQL, pooling i repliki odczytowe

Relacyjna baza danych pozostaje jednym z najważniejszych elementów backendu. W GCP typowym wyborem jest Cloud SQL lub, przy większych wymaganiach skalowalności i dostępności, AlloyDB. Ponieważ baza danych jest komponentem stanowym, jej skalowanie i optymalizacja wymagają większej ostrożności niż w przypadku warstwy aplikacyjnej.

Jednym z najczęstszych problemów w architekturach kontenerowych jest nadmierna liczba połączeń do bazy danych. Gdy Cloud Run dynamicznie zwiększa liczbę instancji, każda z nich może otwierać własną pulę połączeń. Bez kontroli prowadzi to do szybkiego wyczerpania limitów bazy danych. Dlatego pooling połączeń powinien być traktowany jako element architektury, a nie jedynie detal implementacyjny. Rozmiar puli musi uwzględniać maksymalną liczbę instancji, współbieżność oraz faktyczne zapotrzebowanie aplikacji na operacje bazodanowe.

Read-replicas są przydatne, gdy system generuje dużo zapytań odczytowych, które nie muszą zawsze trafiać do instancji głównej. Pozwalają one odciążyć bazę primary i poprawić skalowalność odczytów. Nie są jednak rozwiązaniem uniwersalnym. Repliki mogą mieć opóźnienie względem głównej bazy, dlatego nie powinny być używane w miejscach, gdzie wymagana jest natychmiastowa spójność po zapisie. Dobre wykorzystanie replik wymaga rozumienia wzorców dostępu do danych i świadomego rozdzielenia zapytań odczytowych od zapisów.

Optymalizacja bazy danych zaczyna się zwykle od indeksów, analizy zapytań i obserwacji realnego obciążenia. Skalowanie pionowe bazy, dokładanie replik lub przejście na droższy wariant usługi powinno być poprzedzone sprawdzeniem, czy problem nie wynika z nieefektywnych zapytań, braku indeksów lub nadmiernej liczby operacji wykonywanych synchronicznie w ścieżce żądania użytkownika.

## Cache-aside i Memorystore

Cache jest jednym z najważniejszych narzędzi ograniczania opóźnień i kosztów, ale wymaga dyscypliny projektowej. Wzorzec cache-aside polega na tym, że aplikacja najpierw próbuje odczytać dane z cache, a dopiero w przypadku braku trafienia pobiera je z bazy danych i zapisuje w cache na przyszłość. W GCP taką rolę może pełnić Memorystore, na przykład w wariancie Redis.

Zaletą cache-aside jest prostota i kontrola po stronie aplikacji. Programista jasno określa, które dane są cache'owane, przez jaki czas i w jaki sposób są odświeżane. Wadą jest konieczność poprawnego zarządzania inwalidacją. Jeżeli dane w bazie zostaną zmienione, a cache nadal przechowuje starą wartość, użytkownik może otrzymać nieaktualną odpowiedź. Dlatego TTL, usuwanie kluczy po zapisie oraz projektowanie kluczy cache powinny być traktowane jako część modelu danych.

Cache nie powinien być używany bezrefleksyjnie. Największą wartość daje tam, gdzie dane są często odczytywane, stosunkowo rzadko zmieniane i kosztowne do pobrania lub przeliczenia. W przypadku danych silnie dynamicznych źle zaprojektowany cache może zwiększyć złożoność systemu bardziej, niż poprawić jego wydajność. Trzeba też pamiętać, że cache jest zależnością zewnętrzną. Aplikacja powinna zachować kontrolowane działanie również wtedy, gdy cache jest chwilowo niedostępny.

## Przetwarzanie asynchroniczne i Pub/Sub

Nie wszystkie operacje powinny być wykonywane synchronicznie w czasie obsługi żądania użytkownika. Wysyłka wiadomości e-mail, generowanie raportów, aktualizacja indeksów wyszukiwania, przetwarzanie obrazów lub integracje z wolniejszymi systemami zewnętrznymi często lepiej nadają się do przetwarzania asynchronicznego. W GCP typowym mechanizmem komunikacji zdarzeniowej jest Pub/Sub.

Pub/Sub pozwala oddzielić producenta zdarzenia od konsumenta. Usługa obsługująca żądanie może opublikować komunikat i szybko zwrócić odpowiedź użytkownikowi, a dalsze przetwarzanie wykona osobny worker. Taki podział zwiększa odporność systemu, ponieważ chwilowe spowolnienie jednego komponentu nie musi blokować całej ścieżki użytkownika. Ułatwia też skalowanie, ponieważ konsumenci komunikatów mogą być skalowani niezależnie od API.

Architektura asynchroniczna wymaga jednak poprawnej obsługi powtórzeń i idempotencji. Komunikat może zostać dostarczony więcej niż raz, a konsument może przerwać działanie w połowie operacji. Dlatego operacje wykonywane przez workery powinny być projektowane tak, aby ich ponowne uruchomienie nie prowadziło do błędnego stanu, podwójnych płatności, wielokrotnych e-maili lub niespójnych zapisów. Klucze idempotencyjne i jawne śledzenie statusu przetwarzania są w takim modelu nie dodatkiem, lecz wymogiem poprawności.

## Idempotencja, limity i kontrola przeciążenia

Idempotencja oznacza, że wielokrotne wykonanie tej samej operacji daje taki sam efekt jak wykonanie jej raz. W systemach chmurowych jest to szczególnie ważne, ponieważ ponowienia żądań, timeouty i dostarczanie komunikatów więcej niż raz są normalnym elementem działania infrastruktury rozproszonej. Dla operacji zmieniających stan, takich jak tworzenie zamówienia, płatność lub rezerwacja zasobu, warto stosować klucze idempotencyjne przekazywane przez klienta albo generowane na poziomie systemu.

Rate limiting chroni system przed nadmiernym ruchem, nadużyciami oraz przypadkowymi pętlami po stronie klientów. Nie chodzi wyłącznie o bezpieczeństwo, ale także o stabilność i koszty. Bez limitów pojedynczy klient lub błędna integracja może doprowadzić do gwałtownego wzrostu liczby instancji, zapytań do bazy i kosztów infrastruktury. Dobrze zaprojektowany limit powinien być zależny od typu użytkownika, rodzaju operacji i kosztu technicznego danego żądania.

Kontrola przeciążenia powinna być projektowana warstwowo. Cloud Run może ograniczyć maksymalną liczbę instancji, API Gateway lub load balancer mogą egzekwować reguły ruchu, aplikacja może stosować własne limity biznesowe, a baza danych i cache powinny mieć jasno określone granice obciążenia. Brak takich ograniczeń często ujawnia się dopiero podczas awarii lub nagłego wzrostu popularności usługi.

## Obserwowalność: logi, metryki i latency

System cloud-native powinien być projektowany tak, aby jego działanie można było zrozumieć na podstawie danych operacyjnych. Obserwowalność nie polega jedynie na zapisywaniu logów, lecz na możliwości odpowiedzi na pytania: co się dzieje, gdzie występuje opóźnienie, która zależność zawodzi, jaki jest wpływ błędu na użytkowników i ile kosztuje obsługa danego ruchu.

Logowanie strukturalne jest znacznie bardziej użyteczne niż zwykłe komunikaty tekstowe. Log powinien zawierać informacje takie jak identyfikator żądania, użytkownik lub tenant, nazwa operacji, kod odpowiedzi, czas trwania, identyfikator komunikatu lub klucz idempotencyjny. Dzięki temu logi można filtrować, agregować i korelować z metrykami. W środowisku rozproszonym szczególnie ważne jest śledzenie przepływu jednego żądania przez wiele komponentów.

Metryki latency powinny być analizowane nie tylko przez średnią, ale także przez percentyle, na przykład p95 lub p99. Średnia potrafi ukrywać problemy dotykające mniejszą, ale istotną grupę użytkowników. Oprócz opóźnień warto monitorować liczbę błędów, nasycenie zasobów, wykorzystanie połączeń do bazy danych, trafienia cache, liczbę komunikatów w kolejce oraz koszt. Dopiero połączenie tych danych pozwala ocenić, czy system jest technicznie zdrowy i ekonomicznie uzasadniony.

## Bezpieczeństwo i zarządzanie sekretami

Bezpieczeństwo w architekturze GCP powinno opierać się na zasadzie najmniejszych uprawnień. Usługi powinny korzystać z dedykowanych kont serwisowych, które mają tylko te role, które są niezbędne do wykonania konkretnej pracy. Nadawanie szerokich uprawnień na wszelki wypadek jest wygodne w krótkim terminie, ale zwiększa ryzyko naruszenia bezpieczeństwa i utrudnia audyt.

Sekrety, takie jak hasła, tokeny API czy dane dostępowe, nie powinny być przechowywane w kodzie źródłowym, obrazach kontenerów ani zwykłych zmiennych konfiguracyjnych bez kontroli dostępu. Do tego celu należy stosować usługę Secret Manager. Aplikacja powinna odczytywać sekrety w sposób kontrolowany, a dostęp do nich powinien być ograniczony do konkretnych usług. Rotacja sekretów powinna być możliwa bez przebudowy całego systemu.

Ważnym elementem bezpieczeństwa jest również konfiguracja sieciowa. Prywatny dostęp do Cloud SQL, użycie VPC, ograniczanie publicznej ekspozycji usług oraz odpowiednie reguły ingress i egress zmniejszają powierzchnię ataku. CDN może dodatkowo poprawić wydajność i ograniczyć obciążenie backendu przy treściach statycznych lub często odczytywanych zasobach, ale nie zastępuje kontroli dostępu ani walidacji po stronie aplikacji.

## FinOps i optymalizacja kosztów

Architektura chmurowa powinna być oceniana nie tylko przez pryzmat dostępności i wydajności, ale także kosztu dostarczenia wartości biznesowej. Model płatności za użycie jest korzystny wtedy, gdy system rzeczywiście skaluje się zgodnie z ruchem i nie utrzymuje niepotrzebnego zapasu. Źle dobrane minimalne instancje, nadmierne pule połączeń, zbyt duże bazy danych, nieużywane repliki lub brak limitów autoskalowania mogą prowadzić do kosztów nieproporcjonalnych do korzyści.

FinOps nie oznacza prostego cięcia kosztów. Chodzi o świadome zarządzanie relacją między kosztem, wydajnością, niezawodnością i wartością biznesową. Tani system, który nie obsługuje ruchu lub często zawodzi, nie jest dobrze zoptymalizowany. Podobnie system technicznie poprawny, ale stale utrzymujący nadmiarowe zasoby bez uzasadnienia, wymaga dostrojenia.

W praktyce należy regularnie analizować wykorzystanie zasobów, trendy kosztowe i miejsca marnotrawstwa. Szczególną uwagę warto zwracać na zasoby działające bez ruchu, usługi przewymiarowane, dane przechowywane bez polityki retencji, niewykorzystywane adresy IP, nadmiarowe środowiska testowe i zbyt agresywne ustawienia skalowania minimalnego. Architektura powinna być projektowana tak, aby koszt rósł proporcjonalnie do realnego użycia, a nie do pesymistycznych założeń projektowych.

## Typowy przepływ żądania w proponowanej architekturze

W typowym scenariuszu użytkownik wysyła żądanie do usługi HTTP działającej na Cloud Run. Platforma kieruje ruch do dostępnej instancji albo uruchamia nową, jeżeli aktualna przepustowość jest niewystarczająca. Aplikacja wykonuje walidację, sprawdza limity, odczytuje potrzebne dane z cache lub bazy danych, a następnie zwraca odpowiedź. Jeżeli operacja wymaga dłuższego przetwarzania, aplikacja publikuje komunikat do Pub/Sub, a osobny worker realizuje zadanie asynchronicznie.

W tym przepływie każda warstwa ma jasno określoną odpowiedzialność. Cloud Run odpowiada za uruchamianie i skalowanie kontenerów, Cloud SQL za trwałe dane relacyjne, Memorystore za szybki dostęp do często odczytywanych danych, Pub/Sub za komunikację asynchroniczną, Secret Manager za sekrety, a narzędzia obserwowalności za wgląd w działanie systemu. Dobrze zaprojektowana aplikacja nie ukrywa tych zależności, lecz świadomie je kontroluje.

## Najważniejsze kryteria poprawnej architektury

Poprawna architektura cloud na GCP powinna być skalowalna, ale skalowalność nie może oznaczać nieograniczonego wzrostu kosztów. Powinna być odporna, ale odporność nie może polegać wyłącznie na nadmiarowości. Powinna być szybka, ale optymalizacja wydajności nie powinna prowadzić do niekontrolowanej złożoności. Powinna być bezpieczna, ale bezpieczeństwo musi być częścią codziennego procesu, a nie osobną warstwą dodaną na końcu.

W praktyce dobrą architekturę można rozpoznać po tym, że jej komponenty są jasno rozdzielone, usługi są stateless, zależności są monitorowane, błędy są obsługiwane jawnie, koszty są mierzone, a skalowanie odbywa się na podstawie realnego ruchu. Jeżeli system działa poprawnie technicznie, ale stale generuje koszt bez proporcjonalnej wartości, nie jest jeszcze dobrze zaprojektowany. Jeżeli system jest tani, ale nie ma obserwowalności, idempotencji i odporności na awarie, również nie spełnia standardów architektury produkcyjnej.

## Podsumowanie

Nowoczesny backend na Google Cloud Platform powinien być budowany jako system rozproszony oparty na usługach zarządzanych, automatycznym skalowaniu i świadomym zarządzaniu stanem. Cloud Run pozwala uprościć uruchamianie usług kontenerowych, ale wymaga poprawnego projektowania aplikacji stateless. Cloud SQL zapewnia wygodną bazę relacyjną, ale wymaga kontroli połączeń, indeksów i świadomego użycia replik. Memorystore może znacząco poprawić wydajność, ale tylko wtedy, gdy cache jest poprawnie inwalidowany. Pub/Sub ułatwia przetwarzanie asynchroniczne, lecz wymaga idempotencji i kontroli ponowień.

Najważniejszy wniosek jest praktyczny: dobra architektura cloud nie polega na użyciu jak największej liczby usług chmurowych, ale na właściwym dobraniu odpowiedzialności między aplikacją a platformą. Usługi zarządzane zmniejszają ciężar operacyjny, autoskalowanie pozwala dopasować zasoby do ruchu, a obserwowalność i FinOps pozwalają utrzymać system pod kontrolą. Projektując backend w GCP, należy stale równoważyć niezawodność, wydajność, bezpieczeństwo i koszt, ponieważ dopiero ich połączenie tworzy architekturę gotową do realnego użycia produkcyjnego.
