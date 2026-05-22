# Konfiguracja, dane i niezawodność aplikacji w Kubernetes

## Cel dokumentu

Ten dokument opisuje teoretyczny model aplikacji uruchamianej w Kubernetesie, ze szczególnym naciskiem na konfigurację, dane i niezawodność. Punktem wyjścia jest aplikacja typu `demo-api`, ale najważniejsze nie są konkretne nazwy klas ani manifestów. Istotne jest zrozumienie, jak aplikacja powinna współpracować z Kubernetesem: skąd bierze konfigurację, jak obsługuje dane wrażliwe, kiedy używa `emptyDir`, kiedy potrzebuje PVC, jak rozróżnia readiness od liveness, jak przechodzi w tryb drain podczas terminacji oraz jakie warunki musi spełniać, aby autoscaling miał sens.

Kubernetes nie naprawia automatycznie błędów architektonicznych aplikacji. Jeżeli aplikacja trzyma krytyczny stan w pamięci procesu, myli gotowość z żywotnością, zapisuje trwałe dane do katalogu tymczasowego albo restartuje się przy każdej chwilowej niedostępności bazy danych, to Kubernetes nie zamieni jej w system niezawodny. Platforma daje mechanizmy, ale kod aplikacji musi być napisany zgodnie z ich semantyką.

Najważniejsza zasada dla tego obszaru brzmi: compute jest wymienialny, dane i konfiguracja muszą być traktowane świadomie. Pod może zniknąć, kontener może zostać zrestartowany, nowa replika może wystartować na innym Node, a Service może przestać kierować ruch do konkretnego backendu. Aplikacja powinna być przygotowana na te zdarzenia.

## ConfigMap i Secret

ConfigMap i Secret są podstawowymi mechanizmami dostarczania konfiguracji do aplikacji. ConfigMap służy do danych jawnych, takich jak port, nazwa aplikacji, poziom logowania, flagi funkcjonalne albo ścieżki plików. Secret służy do danych wrażliwych, takich jak hasła, tokeny, connection stringi i klucze. To rozróżnienie jest fundamentalne. ConfigMap nie zapewnia poufności i nie powinien być używany do sekretów.

Samo użycie Secretu również nie oznacza, że temat bezpieczeństwa jest zamknięty. Secret jest osobnym typem obiektu, ale nadal wymaga właściwego RBAC, kontroli dostępu do namespace’a, ostrożności w logowaniu i — po stronie administracji klastra — odpowiedniej konfiguracji szyfrowania w spoczynku. Sekret, który zostanie wypisany do logów albo zwrócony z endpointu diagnostycznego, przestaje być sekretem niezależnie od tego, że pochodził z obiektu Kubernetes Secret.

W aplikacji javowej warto jawnie oddzielić konfigurację zwykłą od wrażliwej. Przykładowo `AppProperties` może reprezentować zwykłe parametry działania aplikacji, a `SecretProperties` dane pochodzące z Secretu. Ważne jest, aby klasa obsługująca sekrety nie miała metod przypadkowo ujawniających pełną wartość. Jeżeli aplikacja musi pokazać, że sekret jest skonfigurowany, powinna użyć maskowania, na przykład zwracając informację „configured”, a nie faktyczny connection string.

## Env vs volume

Konfigurację można wstrzykiwać do Poda na dwa główne sposoby: jako zmienne środowiskowe albo jako pliki zamontowane z wolumenu. Te dwa sposoby mają inną semantykę, więc nie powinny być traktowane jako zamienne w każdej sytuacji.

Zmienne środowiskowe są proste i dobrze pasują do konfiguracji odczytywanej przy starcie procesu. Jeżeli aplikacja czyta port, nazwę aplikacji, timeouty albo connection string tylko podczas startu JVM, wstrzyknięcie przez `env` lub `envFrom` jest naturalne. Trzeba jednak pamiętać, że zmienne środowiskowe nie aktualizują się automatycznie w działającym procesie. Zmiana ConfigMapy albo Secretu nie zmieni wartości już obecnych w środowisku procesu. Potrzebny jest restart Poda albo mechanizm rollout’u.

Mount jako volume ma inną charakterystykę. ConfigMap lub Secret zamontowany jako plik może zostać odświeżony przez Kubernetes z opóźnieniem, w modelu eventually consistent. To oznacza, że aplikacja może zobaczyć nową zawartość pliku, ale nie natychmiast i nie przez zmienne środowiskowe. Jeżeli aplikacja ma przeładowywać konfigurację w runtime, powinna czytać plik ponownie albo obserwować jego zmiany. Sama obecność pliku nie wystarczy; kod musi umieć obsłużyć reload.

Właśnie dlatego w koncepcie pojawia się komponent typu `MountedConfigReader`. Jego sens polega na pokazaniu, że plik konfiguracyjny zamontowany z ConfigMapy jest czymś innym niż wartość z env-a. Env jest dobry dla konfiguracji startowej. Volume jest lepszy dla konfiguracji, którą aplikacja ma potencjalnie przeładować bez restartu. Mylenie tych dwóch modeli prowadzi do fałszywego oczekiwania, że zmiana ConfigMapy automatycznie zmieni zachowanie działającej aplikacji.

## Bezpieczne używanie Secretów

Secret powinien być traktowany jako materiał wrażliwy na całej ścieżce życia, nie tylko w manifeście. Aplikacja nie powinna wypisywać sekretów do logów, zwracać ich w odpowiedziach HTTP, dołączać do stack trace’ów ani pokazywać w endpointach diagnostycznych. To brzmi oczywiście, ale w praktyce wiele wycieków wynika z wygodnego debugowania albo z automatycznego wypisywania całych obiektów konfiguracyjnych.

Istotne jest również ograniczenie dostępu do Secretów po stronie Kubernetesa. Role powinny być minimalne. Domyślne lub szerokie uprawnienia do odczytu Secretów mogą prowadzić do eskalacji, zwłaszcza gdy w Secretach znajdują się tokeny lub dane umożliwiające dostęp do innych systemów. Jeżeli aplikacja nie musi rozmawiać z API Kubernetesa, warto wyłączyć automatyczne montowanie tokena ServiceAccount przez `automountServiceAccountToken: false`.

W kodzie dobrym wzorcem jest stworzenie osobnej warstwy obsługi sekretów, która nie udostępnia surowych wartości wszędzie w aplikacji. Przykładowo `SecretProperties` może mieć metodę sprawdzającą, czy connection string istnieje, oraz metodę zwracającą wartość zamaskowaną. Surowa wartość powinna trafiać tylko tam, gdzie faktycznie jest potrzebna do utworzenia połączenia z zależnością.

## emptyDir jako przestrzeń tymczasowa

`emptyDir` jest wolumenem tworzonym wtedy, gdy Pod zostaje przypisany do Node. Jest współdzielony przez kontenery w tym samym Podzie i istnieje tak długo, jak długo Pod istnieje na danym Node. Jeżeli kontener w Podzie się zrestartuje, dane w `emptyDir` mogą nadal istnieć. Jeżeli jednak Pod zostanie usunięty, przeniesiony lub odtworzony jako nowy Pod, dane z `emptyDir` znikają.

To czyni `emptyDir` dobrym miejscem na scratch space, cache lokalny, pliki tymczasowe i dane pośrednie. Nie jest to dobre miejsce na trwałe dane aplikacji. Jeżeli aplikacja zapisuje tam informacje, których utrata oznacza problem biznesowy, to aplikacja jest źle zaprojektowana dla Kubernetesa.

Komponent typu `ScratchStorageService` powinien być traktowany jako demonstracja tej zasady. Może zapisać plik do katalogu tymczasowego i później go odczytać, ale ten plik nie ma gwarancji trwałości poza życiem Poda. To jest poprawne dla danych tymczasowych, ale niedopuszczalne dla danych domenowych, których nie można utracić.

## PVC i trwałość danych

Dla danych trwałych używa się PersistentVolumeClaim. PVC jest żądaniem storage, które zostaje powiązane z PersistentVolume. Lifecycle takiego storage jest oddzielony od życia pojedynczego Poda. Pod może zniknąć, a wolumen może nadal istnieć zgodnie z polityką storage i reclaim policy.

To rozdzielenie jest fundamentalne: kod aplikacji jest disposable, storage nie. Compute można odtworzyć z obrazu i manifestu. Dane wymagają osobnego lifecycle: provisioningu, bindingu, backupu, retencji, odtwarzania i kontroli dostępu. PVC daje aplikacji trwały punkt montowania, ale nie zwalnia zespołu z zaprojektowania strategii backupu i recovery.

Komponent typu `PersistentDataService` pokazuje różnicę względem `ScratchStorageService`. Dane zapisywane na PVC mogą przetrwać odtworzenie Poda, o ile storage i polityki klastra są poprawnie skonfigurowane. Nie należy jednak przesadzać z wnioskiem. PVC nie oznacza automatycznie wysokiej dostępności, replikacji, backupu ani spójności aplikacyjnej. To tylko mechanizm dostarczenia trwałego wolumenu.

## Readiness, liveness i startup

Kubernetes rozróżnia trzy rodzaje probe’ów: startup, readiness i liveness. To rozróżnienie jest jednym z najważniejszych elementów niezawodności aplikacji w klastrze.

Startup probe odpowiada na pytanie, czy aplikacja zakończyła inicjalizację. Dopóki startup probe nie przechodzi, Kubernetes powinien dać aplikacji czas na start i nie traktować jej wolnego uruchamiania jako awarii. W aplikacjach javowych ma to szczególne znaczenie, bo większe aplikacje Spring Boot mogą potrzebować czasu na zbudowanie kontekstu, inicjalizację zależności, migracje albo rozgrzanie cache’a.

Readiness probe odpowiada na pytanie, czy Pod powinien otrzymywać ruch teraz. To pytanie dotyczy aktualnej zdolności do obsługi żądań. Pod może nie być ready, ponieważ jeszcze startuje, kończy działanie, utracił krytyczną zależność albo jest chwilowo przeciążony. Jeżeli readiness failuje, Pod powinien zostać usunięty z listy gotowych backendów Service.

Liveness probe odpowiada na zupełnie inne pytanie: czy proces należy zrestartować. To powinno być używane ostrożnie. Liveness powinien failować wtedy, gdy proces jest wewnętrznie uszkodzony, na przykład utknął w deadlocku albo nie robi postępu. Nie powinien failować tylko dlatego, że baza danych jest chwilowo niedostępna. Restart aplikacji nie naprawia bazy danych, a masowe restarty mogą pogorszyć awarię.

Najważniejszy wzorzec jest następujący: chwilowy problem zależności powinien zwykle powodować `readiness = fail` i `liveness = success`. Pod przestaje wtedy dostawać nowy ruch, ale nie jest bez sensu restartowany. Z kolei deadlock, zawieszenie event loopa albo trwała awaria procesu powinny prowadzić do `liveness = fail`, bo restart może być uzasadnioną strategią naprawy.

## Tryb drain i terminacja Poda

Kończenie Poda nie polega wyłącznie na zabiciu procesu. Kubernetes wysyła sygnał `SIGTERM`, daje aplikacji czas na zakończenie pracy, a dopiero później może wymusić zatrzymanie. Dobrze napisana aplikacja powinna po otrzymaniu sygnału zakończenia wejść w tryb drain.

Drain oznacza, że aplikacja nadal działa, ale nie chce już przyjmować nowego ruchu. Readiness powinna zacząć zwracać błąd, najczęściej 503. Dzięki temu Pod przestaje być traktowany jako gotowy backend Service. Jednocześnie proces może jeszcze zakończyć bieżące żądania, zamknąć połączenia, zatrzymać workery i bezpiecznie wyjść.

W Spring Boot pomaga konfiguracja `server.shutdown: graceful` oraz odpowiedni timeout fazy shutdown. Nie wystarczy jednak sama konfiguracja frameworka. Kod aplikacji musi współpracować: konsumenci kolejek powinni przestać pobierać nowe wiadomości, długie operacje powinny mieć timeouty, a cleanup nie powinien trwać bez końca.

W koncepcie za ten stan odpowiada klasa typu `ShutdownCoordinator`, która oznacza aplikację jako draining. Z kolei `ApplicationHealthState` sprawia, że readiness zaczyna failować, gdy aplikacja jest w trybie drain. To jest poprawny kontrakt z Kubernetesem: Pod istnieje, ale nie powinien już dostawać nowego ruchu.

## EndpointSlices i ruch do gotowych backendów

Service nie kieruje ruchu do abstrakcyjnej aplikacji, tylko do zestawu backendów reprezentowanych przez endpointy. W nowoczesnym Kubernetesie backendy Service są reprezentowane przez EndpointSlices. Kiedy Pod staje się ready, może pojawić się jako backend. Kiedy przestaje być ready albo jest usuwany, jego status w EndpointSlice się zmienia.

To ma praktyczne znaczenie podczas rolloutów i terminacji. Jeżeli Pod jest usuwany, endpoint może przejść w stan terminating, `ready` może stać się false, a informacja o serving może pozwolić klientom lub load balancerom na connection draining. Kod aplikacji powinien wspierać ten model, czyli po SIGTERM przestać przyjmować nowe żądania, ale nie zrywać natychmiast trwających operacji.

Readiness jest więc nie tylko lokalnym health checkiem. Jest sygnałem wpływającym na routing w klastrze. Błędna readiness oznacza błędne decyzje routingu. Jeżeli readiness przechodzi zbyt wcześnie, Service może wysłać ruch do aplikacji, która nie jest gotowa. Jeżeli readiness jest zbyt niestabilna, Pod może ciągle wypadać i wracać do backendów, powodując flapping.

## Autoscaling i HPA

Horizontal Pod Autoscaler jest kontrolerem, który okresowo pobiera metryki, oblicza pożądaną liczbę replik i aktualizuje obiekt docelowy, zwykle Deployment. Najprostszy wariant skaluje po CPU. Aby CPU-based HPA miało sens, kontenery muszą mieć ustawione `resources.requests.cpu`, ponieważ wykorzystanie CPU jest liczone względem requestu.

HPA nie jest magicznym rozwiązaniem problemów wydajnościowych. Jeżeli aplikacja nie skaluje się poziomo, zwiększanie liczby replik nie pomoże. Jeżeli trzyma sesje tylko w pamięci procesu, ma globalny mutex, używa lokalnego dysku jako źródła prawdy albo wykonuje zadania cykliczne w każdej replice bez koordynacji, autoscaling może wręcz zwiększyć chaos.

HPA uwzględnia gotowość Podów i nie powinien ślepo reagować na metryki świeżo startujących replik. To ważne, bo nowa replika często ma inny profil zużycia zasobów podczas startu niż po ustabilizowaniu. Długi cold start bez startup probe może zaburzać zachowanie systemu, a niestabilna readiness utrudnia podejmowanie decyzji skalujących.

Skalowanie po metrykach własnych jest możliwe przez `autoscaling/v2`, ale wymaga adaptera dostarczającego custom metrics albo external metrics. Sam YAML może wyglądać prosto, lecz operacyjnie to bardziej złożony tor niż CPU HPA. Trzeba mieć pipeline metryk, adapter, sensowną semantykę metryki i zrozumienie, czy metryka rzeczywiście koreluje z potrzebą skalowania.

## Antywzorce niezawodności i autoscalingu

Najczęstsze problemy nie wynikają z braku konkretnego pola YAML, lecz z błędnych założeń aplikacji. Singleton wymuszający jedną aktywną instancję sabotuje skalowanie. Globalny mutex ogranicza równoległość. Sesje przechowywane wyłącznie w pamięci procesu utrudniają routing i restarty. Brak `resources.requests.cpu` osłabia HPA. Długi cold start bez startup probe może prowadzić do fałszywych restartów. Readiness zależna od drogich lub niestabilnych checków może powodować flapping. Liveness reagująca na przeciążenie albo chwilową awarię zależności może wywołać lawinę restartów.

Wszystkie te problemy mają wspólny mianownik: aplikacja nie jest zaprojektowana zgodnie z modelem Kubernetesa. Kubernetes zakłada, że repliki mogą pojawiać się i znikać, że Pod jest wymienialny, że gotowość jest jawnie komunikowana, że storage trwały jest osobno zarządzany, a ruch trafia tylko do backendów gotowych na obsługę żądań. Jeżeli aplikacja łamie te założenia, platforma zaczyna ujawniać błędy zamiast je ukrywać.

## Rola klas w przykładowym projekcie

W przykładowym projekcie klasy są podzielone zgodnie z odpowiedzialnościami. `AppProperties` reprezentuje zwykłą konfigurację, którą można dostarczyć przez ConfigMapę lub env. `SecretProperties` reprezentuje dane wrażliwe i dba o to, aby nie ujawniać ich przypadkowo. `MountedConfigReader` pokazuje różnicę między konfiguracją startową a plikiem, który można ponownie odczytać w runtime.

`ApplicationHealthState` jest centralnym modelem stanu procesu. Nie przechowuje stanu biznesowego, tylko informacje potrzebne do komunikacji z Kubernetesem: czy aplikacja wystartowała, czy jest draining, czy proces jest live i czy może być ready. `ProbeController` wystawia te informacje przez endpointy zgodne z semantyką startup, readiness i liveness.

`StartupCoordinator` modeluje opóźniony start, czyli sytuację, w której proces już działa, ale aplikacja jeszcze się inicjalizuje. `ShutdownCoordinator` modeluje wejście w drain mode podczas terminacji. `ScratchStorageService` pokazuje pracę z `emptyDir`, czyli storage tymczasowym. `PersistentDataService` pokazuje pracę z PVC, czyli storage o lifecycle niezależnym od pojedynczego Poda.

Ten podział jest ważniejszy niż same nazwy klas. Chodzi o to, aby kod aplikacji jasno odzwierciedlał model działania w Kubernetesie. Health checki nie powinny być przypadkowymi endpointami. Sekrety nie powinny być zwykłymi stringami wypisywanymi w logach. Storage tymczasowy i trwały nie powinny być mylone. Shutdown nie powinien być zdarzeniem pozostawionym przypadkowi.

## Podsumowanie

Konfiguracja, dane i niezawodność w Kubernetesie wymagają świadomego projektu aplikacji. ConfigMap nadaje się do konfiguracji jawnej, Secret do danych wrażliwych, ale oba mechanizmy trzeba stosować zgodnie z ich semantyką. Env jest dobre dla konfiguracji startowej, volume dla plików, które aplikacja może ponownie odczytać. `emptyDir` jest dobre dla danych tymczasowych, PVC dla danych, które mają żyć niezależnie od Poda.

Readiness, liveness i startup to trzy różne pytania. Readiness steruje ruchem, liveness steruje restartem, startup chroni długi start aplikacji. Tryb drain pozwala kończyć Pody bez gwałtownego zrywania ruchu. HPA działa sensownie tylko wtedy, gdy aplikacja rzeczywiście skaluje się poziomo i ma poprawnie zadeklarowane zasoby.

Najkrótszy wniosek brzmi: Kubernetes daje mechanizmy niezawodności, ale aplikacja musi mówić prawdę o swoim stanie. Jeżeli kod poprawnie komunikuje startup, readiness, liveness, drain mode, konfigurację i potrzeby storage, platforma może podejmować dobre decyzje. Jeżeli te sygnały są błędne, Kubernetes będzie automatyzował błędne założenia.
