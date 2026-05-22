# Demo API w Javie dla Kubernetes — README teoretyczny

## Cel dokumentu

Ten dokument opisuje teoretyczny sens przykładowej aplikacji `demo-api` napisanej w Javie i uruchamianej w Kubernetesie. Nie jest to instrukcja krok po kroku ani pełna dokumentacja wszystkich klas. Celem jest pokazanie, dlaczego aplikacja została podzielona na konkretne elementy, jaki kontrakt zawiera z Kubernetesem oraz jak należy myśleć o Podach, Deploymentach, Service, probe’ach, ConfigMapach, Secretach, storage, security context i cyklu życia procesu.

Przykładowy projekt pokazuje aplikację typu stateless API. Oznacza to, że pojedyncza instancja aplikacji nie powinna być traktowana jako trwałe źródło stanu. Kubernetes może uruchomić kilka replik, zakończyć jedną z nich, przenieść ruch na inną, zrestartować kontener albo wymienić starą wersję aplikacji na nową podczas rolling update. Kod aplikacji musi być napisany tak, aby te zdarzenia były normalną częścią życia systemu, a nie sytuacją wyjątkową.

Najważniejszy model mentalny jest prosty: w Kubernetesie nie uruchamia się „jednego serwera aplikacyjnego”, tylko deklaruje oczekiwany stan systemu. Deployment mówi, ile replik powinno działać i jaki obraz mają uruchamiać. ReplicaSet pilnuje liczby Podów. Scheduler przypisuje Pody do Node’ów. Kubelet uruchamia kontenery. Probe’y mówią, czy aplikacja wystartowała, czy jest gotowa na ruch i czy proces nadaje się do dalszego działania. Service udostępnia stabilny punkt dostępu do grupy gotowych Podów.

## Od manifestu do działającego Poda

Po wykonaniu `kubectl apply -f k8s/` nie dzieje się magiczne „uruchomienie kontenera”. Zachodzi sekwencja zdarzeń, którą programista aplikacji powinien rozumieć, ponieważ większość problemów wdrożeniowych znajduje się właśnie na tej ścieżce.

Najpierw `kubectl` wysyła manifesty do API Servera. API Server odpowiada za uwierzytelnienie, autoryzację, walidację i admission. Jeżeli obiekt jest poprawny, jego stan docelowy trafia do `etcd`. To nadal nie znaczy, że kontener już działa. Oznacza tylko, że klaster zapamiętał deklarację pożądanego stanu.

Następnie kontrolery reagują na zapisany stan. Deployment controller tworzy lub aktualizuje ReplicaSet. ReplicaSet controller tworzy Pody. Scheduler znajduje dla nich odpowiedni Node. Kubelet działający na tym Node pobiera obraz, montuje wolumeny, wstrzykuje konfigurację, uruchamia kontener i monitoruje jego stan. Dopiero potem zaczynają mieć znaczenie probe’y: startup, readiness i liveness.

Jeżeli Pod przechodzi readiness, może zostać uznany za gotowy backend Service. EndpointSlice controller aktualizuje listę backendów, a Service zaczyna kierować ruch do gotowych Podów. Jeżeli readiness nie przechodzi, proces może nadal działać, ale Pod nie powinien dostawać ruchu. To rozróżnienie jest podstawą poprawnego działania aplikacji w Kubernetesie.

## Pod jako jednostka wdrożeniowa

Pod jest najmniejszą jednostką wdrożeniową Kubernetesa. To nie pojedynczy kontener jest bezpośrednim centrum modelu aplikacyjnego, lecz Pod. Pod może zawierać jeden albo kilka kontenerów, które współdzielą sieć i mogą współdzielić storage. W typowym API javowym najczęściej jeden Pod zawiera jeden główny kontener aplikacyjny. Dodatkowe kontenery mają sens tylko wtedy, gdy są naprawdę ściśle powiązane z głównym procesem, na przykład jako sidecar proxy, agent telemetryczny albo lokalny komponent pomocniczy.

Pod jest efemeryczny. Może zostać usunięty i zastąpiony innym. Jego adres IP nie jest trwałą tożsamością aplikacji. Nie należy więc projektować aplikacji tak, aby inne systemy komunikowały się bezpośrednio z konkretnym Podem. Właściwym poziomem abstrakcji dla komunikacji jest Service.

W projekcie `demo-api` aplikacja nie zakłada trwałości Poda. Stan życia procesu jest jawnie trzymany w klasie odpowiedzialnej za health state, ale nie jest to stan biznesowy. Jest to tylko lokalna informacja o tym, czy dana instancja zakończyła startup, czy jest gotowa, czy jest w trybie drain i czy proces jest wewnętrznie zdrowy. Gdy Pod zniknie, ten stan znika razem z nim, co jest poprawne, ponieważ nie jest to trwały stan domenowy.

## Deployment jako deklaracja życia aplikacji

Deployment opisuje, jak aplikacja ma funkcjonować w klastrze. W projekcie `demo-api` Deployment określa liczbę replik, obraz kontenera, port HTTP, zmienne środowiskowe, wolumeny, probe’y, zasoby, strategię rolling update i ustawienia bezpieczeństwa.

To jest ważne, ponieważ Deployment nie jest tylko „plikiem do uruchomienia aplikacji”. Jest deklaracją cyklu życia aplikacji. Mówi Kubernetesowi, ile replik ma istnieć, jak je aktualizować i kiedy uznać, że dana replika jest gotowa do obsługi ruchu. Jeżeli jedna replika zniknie, kontrolery spróbują odtworzyć oczekiwany stan. Jeżeli zmieni się obraz, Deployment rozpocznie rollout i będzie stopniowo wymieniał stare Pody na nowe.

Rolling update wymaga, aby stara i nowa wersja aplikacji mogły przez pewien czas działać równolegle. To ma praktyczne konsekwencje dla kodu. Zmiany w API, schemacie bazy danych, komunikatach kolejkowych i konfiguracji powinny być kompatybilne przynajmniej na czas wdrożenia. Kubernetes potrafi stopniowo wymienić Pody, ale nie naprawi niekompatybilności między wersją N i N+1.

Parametry `maxUnavailable` i `maxSurge` określają, jak agresywnie ma przebiegać rollout. `maxUnavailable: 0` oznacza, że Deployment nie powinien celowo obniżać liczby dostępnych replik podczas aktualizacji. `maxSurge: 1` pozwala utworzyć jedną dodatkową replikę ponad deklarowaną liczbę. Te ustawienia są częścią strategii dostępności, a nie tylko detalem YAML.

## Service jako stabilny punkt dostępu

Service daje stabilny endpoint dla grupy Podów. Ponieważ Pody mają efemeryczne adresy IP, aplikacje nie powinny komunikować się z konkretnym Pod IP. Zamiast tego powinny odwoływać się do nazwy Service. Service wybiera Pody przez etykiety i kieruje ruch do tych, które są gotowe.

W projekcie `demo-api` Service wybiera Pody z etykietą `app: demo-api`. To oznacza, że jeżeli Deployment utworzy nowe Pody z tą etykietą i Pody przejdą readiness, mogą stać się backendami Service. Jeżeli Pod przestanie być ready, powinien wypaść z listy backendów. Ten mechanizm jest utrzymywany przez EndpointSlices.

Service nie oznacza jednak, że komunikacja sieciowa zawsze będzie bezbłędna. Połączenia mogą zostać zerwane podczas rollout’u, Pod może zostać zakończony, a backend może chwilowo nie odpowiadać. Kod aplikacji klienckiej nadal powinien mieć timeouty, retry i kontrolowaną obsługę błędów. Kubernetes daje stabilną abstrakcję routingu, ale nie usuwa natury systemów rozproszonych.

## Startup, readiness i liveness jako trzy różne pytania

W projekcie są trzy osobne endpointy: `/startupz`, `/readyz` i `/livez`. Nie jest to przypadkowe. Każdy z nich odpowiada na inne pytanie i powoduje inną decyzję po stronie Kubernetesa.

Startup probe pyta, czy aplikacja zakończyła fazę startu. W Javie start może być relatywnie długi, szczególnie w większych aplikacjach Spring Boot. Proces może już istnieć, port HTTP może już nasłuchiwać, ale aplikacja nadal może wykonywać inicjalizację. Dlatego samo istnienie procesu nie powinno automatycznie oznaczać gotowości.

Readiness probe pyta, czy Pod powinien dostawać ruch. To jest pytanie o zdolność obsługi żądań w tej chwili. Aplikacja może nie być ready, ponieważ jeszcze startuje, kończy działanie, utraciła krytyczną zależność albo świadomie weszła w tryb drain. Readiness steruje obecnością Poda za Service.

Liveness probe pyta, czy proces powinien zostać zrestartowany. To zupełnie inna semantyka. Liveness powinien wykrywać sytuacje, w których aplikacja jest wewnętrznie zepsuta, na przykład utknęła w stanie, z którego sama nie wróci. Nie powinien zależeć bezpośrednio od chwilowej niedostępności bazy danych albo zewnętrznego API. Restart aplikacji nie naprawi bazy, a masowe restarty Podów mogą pogorszyć awarię.

Klasa `ApplicationHealthState` reprezentuje ten podział w kodzie. Przechowuje osobno informację o starcie, trybie drain, wewnętrznej awarii i stanie zależności. Dzięki temu endpointy probe’ów nie są losowymi testami, tylko czytelnym kontraktem między aplikacją a Kubernetesem.

## Graceful shutdown i tryb drain

Kubernetes kończy Pod przez wysłanie sygnału `SIGTERM`. Po określonym czasie, jeżeli proces nie zakończy się sam, może zostać zabity siłowo. Aplikacja powinna traktować `SIGTERM` jako normalny element cyklu życia. Pody są kończone podczas rolling update, skalowania w dół, przenoszenia obciążenia i prac na Node’ach.

W projekcie za ten aspekt odpowiada `ShutdownCoordinator`. Jego zadaniem jest oznaczenie aplikacji jako znajdującej się w trybie drain. Gdy aplikacja wchodzi w drain, readiness powinien zacząć zwracać brak gotowości. Dzięki temu Pod przestaje być kandydatem do obsługi nowego ruchu przez Service, ale proces może jeszcze przez chwilę działać, aby zakończyć bieżące żądania.

W Spring Boot ważna jest również konfiguracja `server.shutdown: graceful` oraz limit czasu dla fazy shutdown. Graceful shutdown nie powinien trwać nieskończenie długo. Jeżeli aplikacja ma workerów, konsumentów kolejek albo długie żądania HTTP, każdy z tych elementów powinien mieć własną, ograniczoną czasowo logikę kończenia pracy.

To jest jedna z najważniejszych różnic między prostym przykładem aplikacji a aplikacją gotową na Kubernetes. W prostym przykładzie proces po prostu działa, dopóki ktoś go nie zatrzyma. W aplikacji produkcyjnej zakończenie procesu musi być zaprojektowane.

## ConfigMap i Secret jako konfiguracja zewnętrzna

Aplikacja kontenerowa nie powinna mieć konfiguracji zaszytej w obrazie. Obraz powinien być możliwie niezmiennym artefaktem, który można uruchomić w różnych środowiskach. Różnice między środowiskami powinny pochodzić z konfiguracji dostarczanej przez Kubernetes.

ConfigMap służy do zwykłej konfiguracji, takiej jak port, nazwa aplikacji, timeouty, ścieżki plików konfiguracyjnych czy flagi funkcjonalne. Secret służy do danych wrażliwych, takich jak hasła, tokeny i connection stringi. W projekcie `demo-api` ConfigMap dostarcza parametry środowiskowe oraz plik `app.yaml`, a Secret dostarcza przykładowy `DB_DSN`.

Warto jednak zachować sceptycyzm wobec Secretów. Sam obiekt Secret nie oznacza automatycznie, że dane są bezpieczne w każdym sensie. Trzeba kontrolować RBAC, szyfrowanie at rest, dostęp do namespace’a, logowanie wartości i sposób przekazywania sekretów do procesu. W przykładach edukacyjnych Secret bywa zapisany w plaintext, ale w realnym repozytorium produkcyjnym zwykle nie powinno się tak robić.

## Storage i emptyDir

W projekcie pojawia się `emptyDir`, montowany jako tymczasowy katalog. To dobry sposób pokazania różnicy między scratch space a trwałym storage. `emptyDir` istnieje razem z Podem i znika razem z nim. Nadaje się na pliki tymczasowe, cache lokalny albo dane pośrednie, które można odtworzyć. Nie nadaje się jako trwałe miejsce przechowywania danych biznesowych.

Klasa `TmpStorageService` celowo zapisuje tylko do katalogu tymczasowego. Jej sens polega na pokazaniu, że lokalny zapis w kontenerze może być użyteczny, ale tylko wtedy, gdy aplikacja nie oczekuje trwałości tych danych. Jeżeli dane mają przetrwać restart Poda, powinny trafić do zewnętrznego systemu albo do trwałego wolumenu zarządzanego przez PersistentVolumeClaim.

Dla aplikacji stateless lokalny storage powinien być dodatkiem, a nie fundamentem działania. Jeżeli restart Poda niszczy krytyczne dane, aplikacja nie jest poprawnie przygotowana do działania w Kubernetesie.

## SecurityContext i minimalne uprawnienia

Manifest Deployment zawiera podstawowy security baseline. Aplikacja działa jako non-root, ma wyłączone privilege escalation, usunięte capabilities, profil seccomp `RuntimeDefault`, a token ServiceAccount nie jest automatycznie montowany. Te ustawienia nie gwarantują pełnego bezpieczeństwa, ale są rozsądnym punktem wyjścia.

`automountServiceAccountToken: false` jest szczególnie ważne. Jeżeli aplikacja nie musi rozmawiać z API Kubernetesa, nie powinna mieć tokena pozwalającego jej na jakąkolwiek interakcję z API. W przeciwnym razie podatność w aplikacji może dać atakującemu dostęp do dodatkowej powierzchni ataku.

`readOnlyRootFilesystem: true` wymusza, aby aplikacja nie zapisywała przypadkowo po systemie plików obrazu. Jeżeli potrzebuje miejsca na zapis tymczasowy, powinno być ono jawnie zamontowane, na przykład przez `emptyDir`. To zwiększa przewidywalność i ogranicza skutki błędów.

## Resources i przewidywalność działania

Deployment deklaruje `resources.requests` i `resources.limits`. Requesty mówią schedulerowi, ile zasobów należy zarezerwować dla Poda. Limity ograniczają maksymalne użycie zasobów. Bez requestów scheduler ma gorszą informację o tym, gdzie umieścić Poda, a mechanizmy autoskalowania mogą działać mniej przewidywalnie.

W Javie szczególnie istotna jest pamięć. JVM musi być skonfigurowana tak, aby respektowała realia kontenera. Parametry takie jak `MaxRAMPercentage` pomagają dopasować heap do limitu pamięci. `ExitOnOutOfMemoryError` sprawia, że w krytycznej sytuacji proces kończy się jednoznacznie, co pozwala Kubernetesowi go zastąpić. Nie zastępuje to profilowania pamięci ani naprawiania wycieków, ale daje bardziej przewidywalne zachowanie przy awarii.

Resources nie powinny być zgadywane raz na zawsze. Powinny wynikać z obserwacji aplikacji, testów obciążeniowych i produkcyjnych metryk. Zbyt niskie limity powodują restarty i throttling. Zbyt wysokie requesty marnują pojemność klastra.

## Obraz kontenera i Dockerfile

Dockerfile jest wieloetapowy. Etap build używa Mavena do zbudowania artefaktu, a etap runtime zawiera tylko JRE i gotowy plik JAR. To zmniejsza obraz uruchomieniowy i oddziela narzędzia potrzebne do budowy od środowiska potrzebnego do działania.

Taki podział ma znaczenie nie tylko estetyczne. Mniejszy runtime to mniejsza powierzchnia ataku, mniej zależności, szybsze pobieranie obrazu i zwykle prostsza analiza bezpieczeństwa. W realnym środowisku warto dodatkowo pinować obrazy bazowe, skanować podatności, tagować obrazy numerem commita i budować je w CI.

Trzeba jednak pamiętać, że minimalizm ma koszt. Obraz distroless lub bardzo mały obraz runtime może utrudnić debugowanie, ponieważ nie zawiera shella ani narzędzi systemowych. To nie jest argument przeciwko minimalnym obrazom, ale powód, aby świadomie przygotować logowanie, metryki, endpointy diagnostyczne i procedury debugowania.

## Debugowanie po apply

Jeżeli po `kubectl apply` aplikacja nie działa, nie należy od razu zgadywać. Trzeba przejść przez łańcuch wykonania. Najpierw sprawdzić, czy manifest został przyjęty przez API Server i czy obiekty istnieją. Potem sprawdzić Deployment i ReplicaSet. Następnie Pody, ich status, eventy i przypisanie do Node. Dalej obraz kontenera, pull errors, konfigurację, wolumeny i sekrety. Potem probe’y, logi i endpointy Service.

Szczególnie często problemy leżą w selektorach i etykietach. Service może istnieć, ale nie mieć backendów, jeżeli selector nie pasuje do etykiet Podów. Deployment może istnieć, ale Pody mogą nie startować z powodu błędnego obrazu. Pody mogą działać, ale nie być ready z powodu źle ustawionej readiness probe. Aplikacja może odpowiadać lokalnie, ale Service może nie kierować ruchu, bo EndpointSlices nie zawierają gotowych backendów.

Dobra aplikacja ułatwia debugowanie. Powinna mieć czytelne logi startowe, jasne błędy konfiguracji, szybkie endpointy probe’ów i przewidywalne zachowanie przy braku zależności. Kubernetes daje narzędzia do obserwacji stanu, ale aplikacja musi wystawiać sensowne sygnały.

## Najważniejszy wniosek

Ten koncept pokazuje, że aplikacja javowa gotowa na Kubernetes to nie tylko Spring Boot zapakowany do Dockera. To aplikacja, która rozumie swój cykl życia w klastrze. Umie wystartować, zgłosić gotowość, odróżnić awarię procesu od braku zależności, wejść w tryb drain, zakończyć się łagodnie, działać w wielu replikach i korzystać z konfiguracji dostarczanej przez środowisko.

Kubernetes przejmuje odpowiedzialność za utrzymywanie zadeklarowanego stanu, ale nie przejmuje odpowiedzialności za poprawny projekt aplikacji. Jeżeli aplikacja trzyma stan lokalnie, ignoruje SIGTERM, myli readiness z liveness, wymaga pojedynczej repliki albo zapisuje trwałe dane do `emptyDir`, to Kubernetes nie naprawi tych decyzji. Co najwyżej szybciej pokaże ich konsekwencje.

Dlatego najważniejsze jest nie zapamiętanie wszystkich pól YAML, ale zrozumienie relacji między aplikacją a platformą. Manifesty opisują, jak aplikacja ma żyć w klastrze. Kod musi być napisany tak, aby ten opis był prawdziwy.
