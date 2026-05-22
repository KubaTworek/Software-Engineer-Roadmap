# Lokalny warsztat, CI/CD i debugowanie aplikacji w Kubernetes

## Cel dokumentu

Ten dokument opisuje teoretyczny model pracy z aplikacją uruchamianą w Kubernetesie z perspektywy programisty. Skupia się na trzech obszarach: lokalnym warsztacie developerskim, minimalnym CI/CD oraz debugowaniu problemów typu „działa lokalnie, ale nie działa na klastrze”. Nie jest to instrukcja kopiowania komend krok po kroku, lecz opis sposobu myślenia, który pozwala rozumieć, co dzieje się z aplikacją od momentu zbudowania obrazu, przez wdrożenie manifestów, aż po diagnozowanie problemów w Podach, Service i rolloutach.

Kubernetes wymusza inne podejście niż klasyczne uruchamianie aplikacji na serwerze. Programista nie powinien myśleć tylko o procesie aplikacji, ale o całym łańcuchu: kod, testy, obraz kontenera, tag obrazu, registry, manifest Deploymentu, rollout, probe’y, Service, EndpointSlices, logi, eventy i zasoby. Dopiero ten pełny obraz pozwala skutecznie diagnozować awarie.

Najważniejsza zasada brzmi: w Kubernetesie debugowanie zaczyna się od stanu obiektów API, a nie od wchodzenia na serwer przez SSH. Najpierw sprawdza się, co Kubernetes wie o obiekcie, jakie zdarzenia zarejestrował, jaki status ma Pod, czy obraz został pobrany, czy kontener wystartował, czy probe’y przechodzą i czy Service ma gotowe endpointy. Dopiero później przechodzi się do logów, `exec` albo ephemeral containers.

## Lokalny warsztat developerski

Lokalny warsztat Kubernetes powinien dawać szybki feedback. Programista powinien móc zbudować obraz, uruchomić go w lokalnym klastrze, zastosować manifesty i sprawdzić, czy aplikacja zachowuje się tak samo, jak będzie zachowywać się w środowisku zbliżonym do produkcyjnego. Do tego najczęściej używa się narzędzi takich jak `kind` albo `minikube`.

`kind` uruchamia lokalny klaster Kubernetes, w którym nody są kontenerami Dockera. To sprawia, że bardzo dobrze pasuje do workflow: zbuduj obraz lokalnie, załaduj go do klastra, zastosuj manifesty i przetestuj Service przez port-forward. Jest szczególnie wygodny w CI oraz w szybkich testach manifestów, ponieważ klaster można utworzyć i usunąć relatywnie łatwo.

`minikube` jest bardziej nastawiony na naukę i lokalny playground. Daje prosty start oraz wygodne mechanizmy wystawiania Service, takie jak `minikube service` i `minikube tunnel`. Jest dobrym wyborem, gdy ktoś chce ręcznie eksplorować Kubernetes i mieć bardziej „samouczkowe” doświadczenie. W praktyce programistycznej `kind` bywa jednak wygodniejszy, gdy priorytetem jest powtarzalny loop build → load image → apply.

Nie chodzi o to, że jedno narzędzie jest obiektywnie lepsze. Ważniejsze jest zrozumienie modelu. Lokalny klaster powinien pozwolić sprawdzić, czy manifesty są poprawne, czy obraz działa w Kubernetesie, czy probe’y są dobrze skonfigurowane, czy aplikacja słucha na właściwym adresie i czy Service rzeczywiście kieruje ruch do gotowych Podów.

## Obraz lokalny i imagePullPolicy

W lokalnym workflow bardzo częstym źródłem problemów jest obraz kontenera. Programista buduje obraz lokalnie, ale kubelet w klastrze próbuje pobrać go z registry. Jeżeli obraz nie istnieje w registry albo tag jest niezgodny, pojawia się `ImagePullBackOff`. W `kind` typowy model polega na zbudowaniu obrazu lokalnie i załadowaniu go do klastra przez `kind load docker-image`.

W takim scenariuszu istotne jest `imagePullPolicy`. Jeżeli manifest ma `imagePullPolicy: Always`, kubelet może próbować pobrać obraz z registry, zamiast użyć obrazu dostępnego lokalnie w nodach klastra. Dla lokalnego workflow często lepsze jest `IfNotPresent` albo czasem `Never`, zależnie od sposobu pracy. To nie jest drobiazg konfiguracyjny, tylko realna różnica między działającym a niedziałającym wdrożeniem lokalnym.

W CI/CD sytuacja jest inna. Tam obraz powinien zostać zbudowany, otagowany jednoznacznym identyfikatorem, wypchnięty do registry i dopiero potem użyty w Deploymentcie. Lokalny tag `dev` jest wygodny w warsztacie, ale nie jest dobrym tagiem dla środowisk współdzielonych. W pipeline lepiej używać tagów opartych o SHA commita, ponieważ jednoznacznie wskazują, jaka wersja kodu została zbudowana.

## Aplikacja musi słuchać na 0.0.0.0

Jednym z klasycznych błędów w kontenerach i Kubernetesie jest nasłuchiwanie aplikacji wyłącznie na `127.0.0.1`. Lokalnie może to wyglądać poprawnie, bo test uruchomiony wewnątrz tego samego środowiska trafia w loopback. W Kubernetesie jednak probe’y HTTP i Service kierują ruch do adresu IP Poda oraz portu kontenera. Jeżeli proces słucha tylko na loopbacku wewnątrz kontenera, ruch z zewnątrz procesu może nie trafić do właściwego socketu.

Dlatego aplikacja powinna słuchać na `0.0.0.0`. W aplikacji javowej ze Spring Boot odpowiada za to konfiguracja `server.address`. Warto traktować to jako element kontraktu kontenera: proces ma być osiągalny na interfejsie sieciowym kontenera, nie tylko na lokalnym loopbacku.

To jest szczególnie ważne w debugowaniu scenariusza „lokalnie działa, w klastrze nie”. Jeżeli logi pokazują, że aplikacja wystartowała, ale readiness failuje albo Service nie odpowiada, jedną z pierwszych rzeczy do sprawdzenia jest adres nasłuchu i port. Problem nie musi leżeć w Kubernetesie. Często leży w sposobie uruchomienia aplikacji w kontenerze.

## Minimalny pipeline CI/CD

Minimalny pipeline CI/CD dla aplikacji kontenerowej powinien mieć kilka jasnych etapów. Najpierw testy, potem budowa obrazu, następnie tagowanie, push do registry i ewentualnie deploy do środowiska testowego. Najważniejsze jest to, aby pipeline tworzył powtarzalny artefakt i aby Deployment wskazywał dokładnie tę wersję obrazu, która została zbudowana.

Testy powinny uruchamiać się przed budową obrazu albo przynajmniej przed jego publikacją. W aplikacji javowej będzie to najczęściej `mvn test`. Jeżeli testy nie przechodzą, obraz nie powinien być wypychany jako kandydat do wdrożenia. To prosta zasada, ale nadal ważna: pipeline ma chronić środowisko przed wdrażaniem znanych błędów.

Budowa obrazu powinna korzystać z cache, ale nie powinna opierać się na niejawnych założeniach. W CI warto używać jednoznacznych tagów. Tag `latest` jest wygodny, ale problematyczny, ponieważ nie mówi, jaki commit zawiera obraz. Tag oparty o SHA commita pozwala powiązać działający Pod z konkretną wersją kodu. W debugowaniu jest to bardzo ważne, bo pozwala odpowiedzieć na pytanie: „co dokładnie działa w klastrze?”.

Push do registry jest etapem publikacji artefaktu. W GitHub Actions dla GHCR często wystarcza `GITHUB_TOKEN` z odpowiednimi uprawnieniami. W GitLab CI używa się zwykle zmiennych takich jak `CI_REGISTRY_USER` i `CI_REGISTRY_PASSWORD`, a obraz taguje się przez `CI_COMMIT_SHA`. Szczegóły narzędzi są różne, ale model jest taki sam: pipeline buduje obraz i publikuje go w miejscu, z którego klaster może go pobrać.

Deploy do środowiska testowego może polegać na aktualizacji obrazu w Deploymentcie przez `kubectl set image`, a następnie oczekiwaniu na `kubectl rollout status`. Ważne jest to drugie polecenie. Samo ustawienie obrazu nie oznacza, że wdrożenie się powiodło. Dopiero rollout status mówi, czy Kubernetes zdołał utworzyć nowe Pody, czy przeszły probe’y i czy Deployment osiągnął oczekiwany stan.

## GitHub Actions i katalog .github

W repozytorium GitHub workflow umieszcza się w katalogu `.github/workflows`. To nie jest pakiet Javy ani część kodu źródłowego aplikacji. Jest to katalog konfiguracyjny platformy GitHub. Plik `ci.yaml` opisuje, kiedy pipeline ma się uruchomić i jakie joby ma wykonać.

Warto utrzymywać pipeline blisko kodu, ponieważ konfiguracja CI/CD jest częścią sposobu dostarczania aplikacji. Jeżeli aplikacja wymaga Javy 21, workflow powinien jawnie ustawiać Javę 21. Jeżeli obraz ma być publikowany do GHCR, workflow powinien jawnie logować się do registry i tagować obraz. Jeżeli deploy używa Kubernetes, workflow powinien jawnie wykonywać rollout status i kończyć się błędem, gdy rollout się nie powiedzie.

Katalog `.github` nie powinien być traktowany jako coś pobocznego. To część repozytorium, która opisuje automatyzację dostarczania. W praktyce błąd w workflow może zatrzymać dostarczanie tak samo skutecznie jak błąd w kodzie.

## Debugowanie zaczyna się od obiektów API

W Kubernetesie naturalną pokusą jest traktowanie Poda jak serwera i próba „wejścia do środka”. To jest często zły pierwszy krok. Lepszy model polega na rozpoczęciu od obiektów API i ich statusów. Kubernetes wie, czy Pod jest Pending, czy obraz się pobiera, czy kontener się restartuje, czy probe’y failują, czy Pod jest Ready oraz jakie eventy wystąpiły.

Pierwszym krokiem jest zwykle sprawdzenie listy Podów, Service i EndpointSlices. To pozwala odpowiedzieć na pytanie, czy obiekty istnieją i w jakim są stanie. Następnie używa się `kubectl describe pod`, bo tam widać szczegóły harmonogramowania, pullowania obrazu, restartów, probe’ów i eventów. Eventy są szczególnie ważne, ponieważ często zawierają bezpośrednią przyczynę problemu: brak obrazu, błąd autoryzacji do registry, niespełnione zasoby, błędny mount wolumenu albo failujące probe’y.

Dopiero potem przechodzi się do logów. `kubectl logs` pokazuje aktualne logi kontenera, a `kubectl logs -p` poprzednie logi, co jest kluczowe przy `CrashLoopBackOff`. Jeżeli proces startuje i szybko się kończy, aktualne logi mogą nie wystarczyć. Poprzednie logi często pokazują prawdziwy błąd startu.

`kubectl exec` ma sens wtedy, gdy kontener działa i zawiera narzędzia potrzebne do diagnozy. W minimalnych obrazach lub obrazach distroless często nie ma shella, `ls`, `cat` ani narzędzi sieciowych. Wtedy bardziej właściwym narzędziem jest `kubectl debug` i ephemeral container. To pozwala dodać kontener diagnostyczny do Poda bez dopakowywania narzędzi debugowych do obrazu runtime.

## Typowe statusy i ich znaczenie

`Pending` zwykle oznacza, że Pod nie został przypisany do Node. Przyczyną mogą być brak zasobów, niespełnione affinity, brak PVC albo inne ograniczenia schedulera. Pierwszym miejscem do sprawdzenia jest `describe pod`, ponieważ scheduler zapisuje tam powody niepowodzenia.

`ImagePullBackOff` oznacza, że kubelet nie może pobrać obrazu. Może to wynikać ze złej nazwy obrazu, braku taga, prywatnego registry bez imagePullSecret, błędnego `imagePullPolicy` albo próby pobrania lokalnego obrazu z registry. W lokalnym `kind` ten status często oznacza, że obraz nie został załadowany do klastra albo manifest wskazuje na inny tag niż lokalnie zbudowany obraz.

`CrashLoopBackOff` oznacza, że kontener startuje, kończy się błędem, a kubelet próbuje uruchamiać go ponownie z narastającym opóźnieniem. Przyczyną może być błąd konfiguracji, wyjątek przy starcie aplikacji, brak wymaganego sekretu, błąd połączenia inicjalizacyjnego albo zbyt agresywna probe’a. W takim przypadku bardzo ważne są poprzednie logi kontenera.

`OOMKilled` oznacza, że proces został zabity z powodu przekroczenia limitu pamięci. W Javie może to wynikać z niepoprawnie dobranych limitów, zbyt dużego heapu, wycieku pamięci albo braku dostosowania parametrów JVM do limitów kontenera. Sam restart nie rozwiązuje problemu, jeżeli aplikacja po prostu regularnie przekracza limit.

`Running, ale 0/1 Ready` oznacza, że proces działa, ale Pod nie jest gotowy do obsługi ruchu. To zwykle problem readiness, zależności, konfiguracji albo długiego startupu. Taki Pod nie powinien być traktowany jako backend Service. Warto wtedy sprawdzić `describe pod`, endpointy i logi aplikacji.

Jeżeli Service nie odpowiada, problem często nie leży w samym Service, tylko w selektorach, targetPort albo braku gotowych endpointów. Service może istnieć poprawnie, ale jeżeli jego selector nie pasuje do etykiet Podów, nie będzie miał backendów. Może też mieć backendy, ale kierować ruch na błędny port.

## EndpointSlices i Service

Service jest stabilnym punktem dostępu, ale ruch faktycznie trafia do backendów utrzymywanych przez mechanizmy takie jak EndpointSlices. Jeżeli Pod nie jest ready, nie powinien być regularnym backendem Service. Dlatego przy problemach z ruchem do aplikacji nie wystarczy sprawdzić samego Service. Trzeba sprawdzić również EndpointSlices.

To rozróżnienie jest ważne, bo Service może wyglądać poprawnie, a mimo to nie działać z perspektywy klienta. Może mieć poprawną nazwę, port i typ, ale nie mieć żadnych gotowych endpointów. Może też mieć endpointy, ale kierować na nieprawidłowy targetPort. Dlatego w debugowaniu ruchu należy zawsze sprawdzić selector, etykiety Podów, port kontenera, port Service i EndpointSlices.

Readiness ma bezpośredni wpływ na ten mechanizm. Jeżeli readiness failuje, Pod wypada z gotowych backendów. To jest dobre, gdy aplikacja naprawdę nie powinna przyjmować ruchu. Jest złe, gdy readiness jest błędnie zaprojektowana i niestabilna. Wtedy Service może mieć flapping backendów, a użytkownicy mogą obserwować losowe problemy.

## Minimalne obrazy i ephemeral containers

Dobre obrazy produkcyjne są możliwie małe i nie zawierają niepotrzebnych narzędzi. To ogranicza powierzchnię ataku i zmniejsza rozmiar artefaktu. Ma to jednak konsekwencję: w kontenerze może nie być shella ani narzędzi diagnostycznych. Wtedy klasyczny `kubectl exec` może nie wystarczyć.

Ephemeral containers rozwiązują część tego problemu. `kubectl debug` pozwala dodać do istniejącego Poda kontener diagnostyczny, na przykład z obrazem `busybox`. Dzięki temu można zbadać środowisko sieciowe, wolumeny albo podstawowe właściwości Poda bez zmieniania obrazu aplikacji. To jest lepszy model niż dopakowywanie narzędzi debugowych do obrazu produkcyjnego tylko po to, żeby czasem móc wejść do środka.

To podejście wspiera zasadę, że obraz runtime powinien być możliwie czysty, a debugowanie powinno korzystać z narzędzi platformy. Nie oznacza to, że logi i metryki są mniej ważne. Przeciwnie: im mniejszy obraz runtime, tym bardziej aplikacja powinna mieć dobre logowanie, metryki i jasne endpointy diagnostyczne.

## Endpointy diagnostyczne w aplikacji

W przykładowym projekcie pojawia się endpoint diagnostyczny, który pokazuje wybrane informacje: nazwę aplikacji, tag obrazu, SHA commita, port, adres bind, hostname i kilka nieszkodliwych wartości runtime. Taki endpoint może bardzo pomóc w warsztacie i środowiskach testowych, bo pozwala szybko sprawdzić, jaka wersja aplikacji faktycznie działa w Podzie.

Trzeba jednak uważać. Endpoint diagnostyczny nie powinien zwracać pełnego środowiska procesu, nagłówków żądań, sekretów, tokenów, connection stringów ani danych użytkowników. W Kubernetesie wiele wartości trafia do aplikacji przez env, a env może zawierać dane wrażliwe. Dlatego diagnostyka powinna być selektywna i świadomie ograniczona.

W produkcji taki endpoint powinien być usunięty, zabezpieczony albo ograniczony sieciowo. Nie każdy mechanizm przydatny w warsztacie nadaje się bezpośrednio do publicznego środowiska produkcyjnego.

## Makefile jako lokalna dokumentacja operacyjna

Makefile w takim projekcie pełni rolę lekkiej automatyzacji i dokumentacji operacyjnej. Zamiast pamiętać pełną sekwencję poleceń, programista może mieć cele takie jak build, kind-load, deploy, rollout, port-forward i debug-status. To nie zastępuje zrozumienia Kubernetesa, ale zmniejsza liczbę przypadkowych błędów w codziennym workflow.

Warto jednak pamiętać, że Makefile nie powinien ukrywać całkowicie modelu. Jeżeli cel `deploy` wykonuje `kubectl apply -f k8s/`, programista nadal powinien rozumieć, że manifest trafia do API Servera, jest zapisywany jako stan docelowy, a kontrolery dopiero później doprowadzają klaster do tego stanu. Automatyzacja ma wspierać zrozumienie, a nie je zastępować.

## Rola klas w przykładowej aplikacji

W przykładowym szkielecie `AppProperties` reprezentuje konfigurację aplikacji pochodzącą ze środowiska. Zawiera między innymi port, bind address, tag obrazu i SHA commita. Dzięki temu aplikacja może jawnie pokazać, z jaką konfiguracją działa i jaka wersja została wdrożona.

`ApplicationHealthState` przechowuje stan potrzebny do probe’ów. Nie jest to stan biznesowy, tylko techniczny stan instancji: czy aplikacja zakończyła startup, czy jest w trybie drain, czy readiness ma failować i czy proces jest live. `ProbeController` wystawia ten stan przez `/startupz`, `/readyz` i `/livez`.

`StartupCoordinator` modeluje sytuację, w której serwer HTTP może już działać, ale aplikacja nadal się rozgrzewa. `ShutdownCoordinator` odpowiada za wejście w tryb drain podczas zamykania. To pokazuje, że start i stop aplikacji są częścią projektu, a nie przypadkowym efektem uruchomienia JVM.

`DebugInfoService` i `RuntimeDiagnostics` pokazują bezpieczną diagnostykę. Ich zadaniem nie jest ujawnianie całego środowiska, ale pokazanie minimalnego zestawu informacji potrzebnych do triage’u: jaka wersja działa, na jakim porcie, z jakim bind address i pod jakim hostname. To pomaga odróżnić problem aplikacji od problemu obrazu, konfiguracji albo Deploymentu.

## Podsumowanie

Lokalny warsztat, CI/CD i debugowanie są częścią tego samego systemu dostarczania aplikacji. Lokalnie programista powinien móc szybko zbudować obraz, załadować go do klastra, zastosować manifesty i sprawdzić rollout. W CI/CD ten sam proces powinien być powtarzalny, oparty o testy, jednoznaczne tagi obrazów i kontrolę rollout status. W debugowaniu należy zaczynać od stanu obiektów Kubernetes, a dopiero później przechodzić do logów, exec i ephemeral containers.

Najważniejsze jest zachowanie ciągłości między lokalnym środowiskiem, pipeline i klastrem. Obraz, tag, manifest, probe’y i Service muszą opisywać tę samą rzeczywistość. Jeżeli lokalnie testujesz obraz `demo-api:dev`, a Deployment próbuje pobrać inną nazwę z registry, problem jest w łańcuchu dostarczania. Jeżeli aplikacja słucha na `127.0.0.1`, a Service kieruje ruch do Pod IP, problem jest w konfiguracji runtime. Jeżeli Pod działa, ale nie jest Ready, problem leży w semantyce gotowości, niekoniecznie w samym procesie.

Dobre debugowanie w Kubernetesie polega na spokojnym przejściu przez warstwy: manifest, Deployment, ReplicaSet, Pod, obraz, konfiguracja, probe’y, logi, EndpointSlices i Service. To podejście jest wolniejsze niż zgadywanie, ale znacznie skuteczniejsze.
