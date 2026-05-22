# Kubernetes: Up & Running — teoretyczne notatki dla programisty aplikacji

## Wprowadzenie

*Kubernetes: Up & Running* w 3. wydaniu pozostaje bardzo dobrą książką do zbudowania mentalnego modelu Kubernetesa. Jej największą wartością nie jest nauczenie pojedynczych komend ani dostarczenie gotowych manifestów YAML, lecz pokazanie sposobu myślenia o aplikacjach uruchamianych w środowisku rozproszonym, kontenerowym i deklaratywnym. Książka obejmuje kontenery i obrazy, pracę z `kubectl`, Pody, Service Discovery, Deploymenty, ConfigMapy i Secrety, RBAC, storage, rozszerzanie Kubernetesa oraz bezpieczeństwo. Z perspektywy programisty aplikacji szczególnie istotne są rozdziały dotyczące obrazów, Podów, Service, Deploymentów, konfiguracji, storage i security.

Wydanie 3. ukazało się w sierpniu 2022 roku, ma 328 stron, a jego autorami są Brendan Burns, Joe Beda, Kelsey Hightower i Lachlan Evenson. Trzeba jednak pamiętać, że Kubernetes rozwija się szybko. Książka dobrze tłumaczy model i wzorce, ale szczegóły wykonawcze należy weryfikować w aktualnej dokumentacji. Dotyczy to zwłaszcza pól YAML, semantyki probe’ów, bezpieczeństwa, HPA, debugowania i aktualnych zaleceń produkcyjnych. Oficjalna polska dokumentacja Kubernetesa istnieje i może być pomocna do nauki pojęć, ale do najświeższych szczegółów lepiej używać bieżącej dokumentacji angielskiej.

Ten dokument traktuje książkę jako przewodnik po teorii, a nie jako zamknięte źródło aktualnych rekomendacji operacyjnych. Celem jest uchwycenie tego, co programista aplikacji powinien naprawdę wynieść z lektury: jak myśleć o procesie, stanie, konfiguracji, sieci, wdrożeniach, dostępności, bezpieczeństwie i debugowaniu w Kubernetesie.

## Główna idea Kubernetesa

Kubernetes nie jest po prostu narzędziem do uruchamiania kontenerów. Jest platformą, która pozwala opisać pożądany stan systemu, a następnie próbuje ten stan utrzymać. Zamiast ręcznie uruchamiać proces i pilnować go samodzielnie, deklaruje się, co ma istnieć: ile instancji aplikacji, z jakiego obrazu, z jaką konfiguracją, z jakimi zasobami, z jakimi regułami zdrowia i w jaki sposób ma być dostępne w sieci. Kontrolery Kubernetesa obserwują rzeczywisty stan klastra i porównują go ze stanem zadeklarowanym. Jeżeli coś się rozjeżdża, próbują przywrócić system do oczekiwanej postaci.

To podejście wymaga zmiany myślenia. Programista nie powinien zakładać, że uruchomiony proces jest trwałym bytem. Pojedyncza instancja aplikacji może zostać ubita, zrestartowana, przeniesiona albo zastąpiona inną. Pod może mieć inny adres IP po restarcie. Node może zniknąć. Ruch może zostać przekierowany do innej repliki. Aktualizacja może sprawić, że przez pewien czas jednocześnie działają dwie wersje aplikacji. Kubernetes nie usuwa tych zjawisk, tylko daje mechanizmy, dzięki którym aplikacja może działać mimo nich.

Najważniejszy wniosek dla programisty jest prosty: aplikację należy pisać tak, jakby mogła zostać zakończona w dowolnym momencie, uruchomiona kilka razy równolegle, otrzymać ruch dopiero po osiągnięciu gotowości i działać w środowisku, gdzie adresy IP Podów są efemeryczne. To założenie wpływa na architekturę kodu bardziej niż same manifesty YAML.

## Model deklaratywny i pętle kontroli

Jednym z najważniejszych pojęć w Kubernetesie jest model deklaratywny. W tradycyjnym podejściu administrator lub skrypt wykonuje sekwencję kroków: uruchom proces, otwórz port, sprawdź status, zrestartuj w razie awarii. W Kubernetesie opisuje się stan docelowy, a system sam próbuje do niego dojść. Manifest nie jest listą poleceń, tylko deklaracją oczekiwanego świata.

Za tym modelem stoją pętle kontroli. Kontroler stale obserwuje obiekty API i stan rzeczywisty. Jeżeli Deployment mówi, że powinny działać trzy repliki aplikacji, a działają tylko dwie, kontroler podejmie próbę utworzenia brakującej repliki. Jeżeli Pod przestanie istnieć, zostanie zastąpiony nowym, o ile wynika to z zadeklarowanego stanu. To sprawia, że Kubernetes jest systemem samonaprawiającym się, ale tylko w granicach informacji, które mu dostarczymy.

Dla programisty oznacza to, że manifesty są częścią kontraktu aplikacji z platformą. Błędny manifest może być równie groźny jak błąd w kodzie. Źle ustawiona readiness probe może kierować ruch do aplikacji, która nie jest gotowa. Źle ustawiona liveness probe może restartować zdrową aplikację tylko dlatego, że chwilowo nie działa zależność zewnętrzna. Brak limitów i requestów zasobów może utrudnić planowanie Podów i autoskalowanie. Kubernetes realizuje zadeklarowany model, ale nie wie automatycznie, jakie intencje miał autor aplikacji.

## Kontenery i obrazy

Kontener jest izolowanym sposobem uruchomienia procesu wraz z jego zależnościami. Obraz kontenera powinien być powtarzalnym artefaktem aplikacji. Powinien zawierać kod, runtime i biblioteki potrzebne do uruchomienia, ale nie powinien zawierać konfiguracji środowiskowej ani sekretów. Ten sam obraz powinien dać się uruchomić w różnych środowiskach, a różnice między środowiskami powinny wynikać z konfiguracji dostarczanej z zewnątrz.

To rozróżnienie jest bardzo ważne. Jeżeli konfiguracja trafia do obrazu, obraz przestaje być uniwersalnym artefaktem. Trzeba wtedy budować osobne obrazy dla środowiska testowego, stagingowego i produkcyjnego, co utrudnia odtwarzalność. W poprawnym modelu obraz jest niezmienny, a środowisko dostarcza dane takie jak adresy usług, flagi funkcjonalne, nazwy bucketów, tryb logowania czy limity aplikacyjne.

Wersjonowanie obrazu powinno być jednoznaczne. Tag `latest` bywa wygodny lokalnie, ale w środowiskach kontrolowanych osłabia audytowalność i utrudnia rollback. Programista powinien myśleć o obrazie jako o konkretnym, niezmiennym artefakcie powiązanym z wersją kodu. Dzięki temu łatwiej ustalić, co faktycznie działa w klastrze, odtworzyć błąd i porównać zachowanie wersji N oraz N+1.

## Pod jako podstawowa jednostka uruchomieniowa

Pod jest najmniejszą jednostką, którą Kubernetes planuje i uruchamia. Nie kontener, lecz Pod jest podstawowym obiektem wdrożeniowym. Pod może zawierać jeden albo więcej kontenerów. Kontenery w tym samym Podzie współdzielą sieć, mogą komunikować się przez `localhost` i mogą współdzielić wolumeny. W typowej aplikacji webowej jeden Pod zawiera jeden główny kontener aplikacyjny, ale są przypadki, w których sens ma dodatkowy kontener pomocniczy.

Współlokowanie kontenerów w jednym Podzie powinno być stosowane ostrożnie. Sidecar ma sens wtedy, gdy procesy są naprawdę silnie sprzężone i powinny żyć razem. Przykładem może być lokalny agent, proxy, collector logów albo komponent pomocniczy, który musi współdzielić zasoby z aplikacją. Nie należy jednak wkładać do jednego Poda wielu niezależnych usług tylko dlatego, że komunikują się ze sobą. Pod jest jednostką skalowania i cyklu życia. Jeżeli dwa procesy mają skalować się niezależnie, zwykle nie powinny znajdować się w tym samym Podzie.

Pody są efemeryczne. Mogą zostać usunięte i zastąpione innymi. Ich adresy IP nie są trwałą tożsamością aplikacji. Z tego powodu lokalny stan Poda powinien być traktowany jako nietrwały, chyba że świadomie użyto odpowiedniego mechanizmu storage. Pliki zapisane lokalnie mogą zniknąć przy restarcie lub przeniesieniu Poda. Aplikacja, która wymaga trwałości, powinna używać bazy danych, systemu kolejkowego, zewnętrznego storage albo PersistentVolumeClaim.

## Stateless design i wielokrotne instancje aplikacji

Kubernetes szczególnie dobrze pasuje do aplikacji stateless, czyli takich, których pojedyncza instancja nie przechowuje trwałego stanu potrzebnego do obsługi przyszłych żądań. Stateless nie znaczy, że system nie ma stanu w ogóle. Znaczy, że stan nie jest przywiązany do konkretnego procesu aplikacyjnego. Stan może znajdować się w bazie danych, cache, kolejce, obiektowym storage albo innym systemie zewnętrznym.

Programista powinien zakładać, że jednocześnie może działać kilka replik tej samej aplikacji. To wpływa na sesje użytkowników, cache lokalny, zadania cykliczne, migracje baz danych, przetwarzanie kolejek i blokady. Jeżeli aplikacja zakłada, że istnieje tylko jeden proces, poziome skalowanie może spowodować błędy trudne do wykrycia w środowisku developerskim.

Szczególnie ryzykowne są zadania cykliczne uruchamiane wewnątrz każdej repliki aplikacji. Jeżeli Deployment ma trzy repliki, ten sam job może wykonać się trzy razy. Jeżeli zadanie musi wykonać się raz, trzeba użyć mechanizmu, który to gwarantuje lub przynajmniej czyni operację idempotentną. Podobnie operacje biznesowe powinny być projektowane tak, aby ponowienie żądania, restart procesu albo przejęcie pracy przez inną replikę nie prowadziły do niespójności.

## Service i Service Discovery

Service rozwiązuje problem nietrwałości Podów w sieci. Pody mają własne adresy IP, ale nie należy traktować tych adresów jako stabilnych punktów integracji. Service dostarcza stałą nazwę i stabilny punkt dostępu do grupy Podów wybranych przez etykiety. Dzięki temu klient nie musi znać aktualnych adresów IP instancji aplikacji.

To jest jedna z najważniejszych abstrakcji dla programisty. Aplikacja powinna komunikować się z inną usługą przez nazwę Service, a nie przez adres konkretnego Poda. Jeżeli Pod zostanie usunięty i zastąpiony nowym, Service nadal reprezentuje tę samą usługę logiczną. Kubernetes utrzymuje listę backendów, między innymi przez EndpointSlices, i kieruje ruch do aktualnie dostępnych instancji.

Service Discovery w Kubernetesie zwykle opiera się na DNS. Aplikacje odwołują się do innych usług po nazwach, a klaster rozwiązuje je na odpowiednie endpointy. Nie zwalnia to jednak programisty z obsługi błędów sieciowych. Połączenia mogą zostać zerwane, backend może zostać usunięty podczas rollout’u, a zależność może chwilowo nie odpowiadać. Kod klienta powinien mieć rozsądne timeouty, retry, circuit breaking tam, gdzie ma to sens, oraz dobrą obsługę błędów.

## Deployment i aktualizacje aplikacji

Deployment jest standardowym obiektem do uruchamiania aplikacji stateless. Opisuje obraz, liczbę replik, konfigurację Podów i strategię aktualizacji. W praktyce to właśnie Deployment najczęściej reprezentuje aplikację webową, API albo worker, który można uruchomić w wielu kopiach.

Deployment zarządza ReplicaSetami, a ReplicaSety pilnują liczby Podów. Podczas aktualizacji Deployment tworzy nowy ReplicaSet i stopniowo zastępuje stare Pody nowymi. Mechanizm rolling update pozwala wdrażać nową wersję bez całkowitego zatrzymania usługi. Warunkiem jest jednak to, że wersja N i N+1 potrafią przez pewien czas współistnieć. Dotyczy to kodu aplikacji, schematu bazy danych, kontraktów API, komunikatów w kolejkach i sposobu obsługi konfiguracji.

Parametry takie jak `maxUnavailable` i `maxSurge` decydują o tym, ile Podów może być niedostępnych oraz ile dodatkowych Podów może zostać utworzonych podczas aktualizacji. Nie są to wyłącznie ustawienia infrastrukturalne. Wpływają na realną dostępność aplikacji i obciążenie zależności. Jeżeli aplikacja startuje długo albo mocno obciąża bazę przy starcie, agresywny rollout może pogorszyć sytuację. Jeżeli readiness probe jest źle ustawiona, Kubernetes może uznać nową wersję za gotową zbyt wcześnie.

Rollback w Kubernetesie jest możliwy, ale nie powinien być traktowany jako magiczne cofnięcie całego systemu. Deployment może przywrócić poprzednią wersję Podów, ale nie cofnie automatycznie migracji bazy danych, zmian w danych ani efektów ubocznych. Dlatego aplikacje powinny być projektowane z myślą o kompatybilności wstecznej i bezpiecznych, stopniowych zmianach.

## Readiness, liveness i startup probe

Probe’y są częścią kontraktu między aplikacją a platformą. Nie są tylko dodatkiem do manifestu. To one mówią Kubernetesowi, czy aplikacja może dostawać ruch, czy wymaga restartu i czy nadal jest w fazie startu.

Readiness probe odpowiada na pytanie, czy Pod powinien otrzymywać ruch. Aplikacja może działać jako proces, ale nie być jeszcze gotowa do obsługi żądań. Może ładować konfigurację, inicjalizować zależności, rozgrzewać cache albo czekać na wewnętrzny stan. Dopóki readiness nie przechodzi, Pod nie powinien być traktowany jako gotowy backend Service.

Liveness probe odpowiada na pytanie, czy proces należy zrestartować. Powinna wykrywać sytuacje, w których aplikacja utknęła i nie potrafi sama wrócić do poprawnego działania. Nie powinna być zwykłym testem wszystkich zależności zewnętrznych. Jeżeli liveness zależy od chwilowej niedostępności bazy danych, cache albo zewnętrznego API, Kubernetes może zacząć restartować zdrowe procesy i pogłębić problem.

Startup probe pomaga przy aplikacjach, które startują długo. Pozwala dać aplikacji czas na start, zanim zaczną obowiązywać normalne reguły liveness. Bez tego wolno startująca aplikacja może być restartowana w pętli, zanim w ogóle osiągnie gotowość.

Najważniejsze rozróżnienie jest więc takie: readiness steruje ruchem, liveness steruje restartem, a startup chroni długi start. Pomylenie tych pojęć prowadzi do bardzo praktycznych awarii.

## Terminacja i graceful shutdown

W Kubernetesie zakończenie życia Poda jest normalnym zdarzeniem. Pod może zostać usunięty podczas rollout’u, skalowania w dół, drenażu noda albo awarii. Aplikacja powinna być napisana tak, aby poprawnie reagowała na SIGTERM i kończyła pracę w kontrolowany sposób.

Po otrzymaniu SIGTERM aplikacja powinna przestać przyjmować nowe żądania, dokończyć bieżące operacje albo bezpiecznie je przerwać, zamknąć połączenia i zakończyć proces przed upływem okresu łaski. Jeżeli proces ignoruje SIGTERM, Kubernetes po czasie użyje SIGKILL, który nie daje już możliwości sprzątania. To może prowadzić do przerwanych żądań, niedokończonych transakcji, porzuconych wiadomości w kolejce albo niespójnego stanu.

`terminationGracePeriodSeconds` nie jest tylko technicznym parametrem. Powinien być dobrany do realnego zachowania aplikacji. Jeżeli aplikacja obsługuje długie żądania albo przetwarza zadania z kolejki, potrzebuje czasu na bezpieczne zakończenie. Jednocześnie zbyt długi okres łaski może spowalniać rollout i skalowanie w dół. Poprawne zachowanie shutdown powinno być częścią projektu aplikacji, a nie dodatkiem na końcu.

## Konfiguracja przez ConfigMap i Secret

Konfiguracja powinna być dostarczana z zewnątrz, a nie zaszywana w obrazie. Kubernetes daje do tego ConfigMapy i Secrety. ConfigMap służy do zwykłej konfiguracji, takiej jak adresy usług, tryby działania czy wartości parametrów. Secret służy do danych wrażliwych, takich jak hasła, tokeny i klucze.

Ważne jest jednak realistyczne podejście do Secretów. Sam fakt użycia obiektu Secret nie oznacza automatycznie pełnego bezpieczeństwa. Trzeba rozumieć, kto ma do nich dostęp przez RBAC, czy są szyfrowane at rest, jak trafiają do Poda i czy nie wyciekają przez logi, zmienne środowiskowe, debugowanie albo błędną konfigurację aplikacji.

Konfigurację można przekazywać jako zmienne środowiskowe albo jako pliki montowane z wolumenu. Zmienne środowiskowe są proste, ale ich aktualizacja zwykle wymaga restartu procesu. Pliki mogą lepiej pasować do konfiguracji, która potencjalnie zmienia się w runtime, choć aplikacja i tak musi umieć ją przeładować. Nie należy zakładać, że każda zmiana ConfigMapy automatycznie i bezpiecznie zmieni zachowanie działającej aplikacji. To zależy od sposobu użycia i od kodu aplikacji.

Z punktu widzenia teorii najważniejsza zasada brzmi: obraz aplikacji powinien być niezależny od środowiska, a konfiguracja powinna być jawnie dostarczana przez platformę. Dzięki temu ten sam artefakt można przenosić między środowiskami, a różnice są widoczne w konfiguracji, nie ukryte w buildzie.

## Storage i trwałość danych

Kubernetes dobrze obsługuje aplikacje stateless, ale wiele systemów potrzebuje trwałych danych. Trzeba wtedy rozumieć różnicę między tymczasową przestrzenią a trwałym storage.

`emptyDir` jest przestrzenią tymczasową związaną z życiem Poda. Nadaje się na pliki robocze, cache lokalny albo dane pośrednie, które mogą zniknąć. Nie nadaje się na dane, które muszą przetrwać usunięcie Poda. Jeżeli Pod zniknie, dane w `emptyDir` również należy traktować jako utracone.

PersistentVolumeClaim reprezentuje żądanie trwałego storage. Aplikacja nie powinna zwykle znać szczegółów fizycznego wolumenu. Deklaruje potrzebę storage, a klaster dostarcza odpowiedni zasób zgodnie z klasą storage i konfiguracją infrastruktury. To oddziela aplikację od konkretnego dostawcy dysków, ale nie zwalnia z myślenia o lifecycle danych, backupie, odtwarzaniu i semantyce współdzielenia wolumenu.

Programista powinien szczególnie uważać na aplikacje stanowe. Baza danych uruchomiona w Kubernetesie to nie tylko kontener z procesem bazy. Potrzebuje trwałości, backupu, odtwarzania, aktualizacji, monitoringu i poprawnej obsługi awarii. Kubernetes może w tym pomóc, ale nie usuwa złożoności systemów stanowych.

## RBAC i tożsamość aplikacji

RBAC decyduje, kto może wykonywać jakie operacje na obiektach Kubernetesa. Dla programisty aplikacji najważniejsze jest zrozumienie, że Pod może działać z określoną tożsamością ServiceAccount. Ta tożsamość może mieć uprawnienia do API Kubernetesa. Jeżeli aplikacja nie potrzebuje dostępu do API klastra, nie powinna go otrzymywać.

Najbezpieczniejszym domyślnym podejściem jest minimalizacja uprawnień. Aplikacja powinna mieć tylko te uprawnienia, które są rzeczywiście potrzebne. Automatyczne montowanie tokena ServiceAccount warto wyłączać tam, gdzie aplikacja nie korzysta z API Kubernetesa. W przeciwnym razie podatność w aplikacji może dać atakującemu niepotrzebny punkt wejścia do klastra.

RBAC nie jest tylko sprawą administratorów. Jeżeli programista tworzy aplikację, która ma czytać obiekty Kubernetesa, tworzyć zasoby albo działać jako operator, musi świadomie projektować jej uprawnienia. Zbyt szerokie role są wygodne na początku, ale niebezpieczne produkcyjnie.

## Security baseline

Bezpieczeństwo aplikacji w Kubernetesie zaczyna się od prostych założeń. Kontener nie powinien działać jako root, jeżeli nie ma takiej konieczności. Warto używać `runAsNonRoot`, ustawiać `seccompProfile: RuntimeDefault`, blokować eskalację uprawnień przez `allowPrivilegeEscalation: false` i ograniczać capabilities. Aplikacja nie powinna mieć dostępu do tokenów, wolumenów, namespace’ów ani zasobów, których nie potrzebuje.

Warto też pamiętać, że bezpieczeństwo obrazu jest częścią bezpieczeństwa aplikacji. Mały, świadomie budowany obraz ma mniejszą powierzchnię ataku. Obraz powinien pochodzić z kontrolowanego procesu builda, być skanowany pod kątem podatności i nie zawierać narzędzi ani sekretów niepotrzebnych w runtime.

Sekrety nie powinny trafiać do logów, stack trace’ów, plików tymczasowych ani komunikatów diagnostycznych. W Kubernetesie łatwo zebrać logi z wielu Podów, co jest zaletą operacyjną, ale zwiększa konsekwencje przypadkowego wycieku. Aplikacja powinna traktować logowanie danych wrażliwych jako błąd bezpieczeństwa.

## Autoscaling i zasoby

Horizontal Pod Autoscaler ma sens wtedy, gdy aplikacja potrafi skalować się poziomo. Samo dodanie HPA nie sprawia, że system automatycznie staje się skalowalny. Aplikacja musi dobrze działać w wielu replikach, nie może trzymać krytycznego stanu lokalnie i powinna poprawnie obsługiwać równoległość.

HPA potrzebuje sensownych informacji o zasobach. `resources.requests` są szczególnie istotne, ponieważ pomagają schedulerowi umieścić Pody na nodach i są podstawą wielu mechanizmów skalowania. Jeżeli requesty są przypadkowe albo ich brakuje, autoskalowanie może działać słabo lub myląco. Limity również trzeba dobierać ostrożnie, bo zbyt niskie mogą powodować ubijanie procesów albo throttling, a zbyt wysokie mogą osłabiać izolację zasobów.

Autoscaling nie zastępuje projektowania wydajności. Jeżeli aplikacja ma globalny lock, wąskie gardło w bazie, nieidempotentne operacje albo ciężki start każdej repliki, zwiększanie liczby Podów może nie poprawić sytuacji. W skrajnym przypadku może ją pogorszyć, bo więcej replik zacznie mocniej obciążać wspólną zależność.

## Debugowanie bez SSH

W Kubernetesie typowy model debugowania różni się od klasycznego logowania przez SSH na serwer. Pojedynczy node i pojedynczy Pod nie powinny być traktowane jako trwałe miejsce pracy. Debugowanie powinno opierać się na obiektach Kubernetesa, logach, zdarzeniach i stanie deklarowanym.

Podstawowa ścieżka myślenia przy problemie powinna wyglądać następująco: obraz, konfiguracja, probe’y, endpointy, logi, zdarzenia, zasoby. Najpierw trzeba sprawdzić, czy uruchomiono właściwy obraz i właściwą wersję. Następnie czy aplikacja dostała poprawną konfigurację i sekrety. Potem czy readiness, liveness i startup probe nie powodują fałszywych problemów. Dalej czy Service rzeczywiście ma endpointy i czy selektory pasują do etykiet Podów. Dopiero potem warto głębiej analizować logi, eventy, zużycie zasobów i zachowanie samego procesu.

Narzędzia takie jak `describe`, `events`, `logs`, `exec`, `debug`, `rollout status` i analiza EndpointSlices zastępują wiele klasycznych nawyków administracyjnych. Nie chodzi o to, że wejście do kontenera jest zakazane, ale o to, że nie powinno być pierwszym i jedynym sposobem diagnozy. W dobrze zaprojektowanym systemie większość informacji potrzebnych do diagnozy powinna być dostępna z poziomu API Kubernetesa, logów i metryk.

## Zakres najważniejszy dla programisty

Jeżeli czytać książkę selektywnie z perspektywy programisty aplikacji, najważniejszy rdzeń to kontenery i obrazy, `kubectl`, Pody, Service Discovery, Deploymenty, ConfigMapy i Secrety, RBAC, storage, security oraz organizacja aplikacji. To właśnie te obszary realnie wpływają na sposób pisania i wdrażania aplikacji.

Ingress, Jobs, DaemonSets, Service Mesh, rozszerzanie Kubernetesa, custom resources i operatory są ważne, ale nie powinny być pierwszym priorytetem, jeżeli celem jest opanowanie podstaw Docker + Kubernetes + CI/CD + debug. Warto je znać koncepcyjnie, natomiast solidne zrozumienie Podów, Deploymentów, Service, konfiguracji, sekretów, RBAC i storage jest bardziej fundamentalne.

Najlepszy sposób czytania tej książki w 2026 roku jest więc selektywny. Należy czytać ją po to, aby zrozumieć, co jest obiektem API, który kontroler za co odpowiada, dlaczego Pod jest jednostką wdrożeniową, jak Service oddziela aplikację od efemerycznych IP, czemu Deployment wymaga zgodności wersji N i N+1 oraz jak konfiguracja, sekrety, storage i RBAC wpływają na kod aplikacji.

## Najważniejszy model mentalny

Kubernetes wymusza myślenie o aplikacji jako o zbiorze nietrwałych, zastępowalnych instancji. Pojedynczy proces nie jest centrum systemu. Centrum systemu jest deklaracja pożądanego stanu oraz zestaw kontrolerów, które próbują ten stan utrzymać.

Dobra aplikacja kubernetesowa jest stateless tam, gdzie to możliwe, poprawnie obsługuje wiele replik, ma jednoznacznie wersjonowany obraz, przyjmuje konfigurację z zewnątrz, nie zakłada trwałości lokalnego dysku, odróżnia gotowość od żywotności, poprawnie kończy pracę po SIGTERM, używa stabilnych nazw Service zamiast Pod IP, ogranicza swoje uprawnienia i daje się diagnozować przez logi, eventy, metryki i stan obiektów.

To jest ważniejsze niż zapamiętanie wszystkich pól manifestu. Szczegóły YAML można sprawdzić w dokumentacji. Trudniejsze i bardziej wartościowe jest przyjęcie właściwych założeń architektonicznych. Kubernetes nagradza aplikacje, które są jawnie konfigurowalne, odporne na restarty, gotowe do równoległego działania i przewidywalne w cyklu życia. Karze natomiast ukryty stan, zależność od lokalnej maszyny, zbyt szerokie uprawnienia, źle zaprojektowane health checki i założenie, że proces będzie działał bez przerwy.

## Podsumowanie

Najważniejsza lekcja z *Kubernetes: Up & Running* jest taka, że Kubernetes nie jest tylko warstwą wdrożeniową, którą można zostawić zespołowi platformowemu. Dla programisty aplikacji jest środowiskiem wykonawczym, które wpływa na projekt kodu. Aplikacja musi współpracować z tym środowiskiem: sygnalizować gotowość, kończyć się poprawnie, akceptować efemeryczność instancji, używać zewnętrznej konfiguracji, działać w wielu replikach i nie zakładać trwałości lokalnych zasobów.

Książka dobrze buduje ten model mentalny. Nie powinna być jednak traktowana jako jedyne źródło aktualnych szczegółów technicznych. Najrozsądniejsze podejście polega na tym, aby z książki wziąć teorię, pojęcia i sposób myślenia, a bieżące szczegóły sprawdzać w oficjalnej dokumentacji Kubernetesa. Wtedy lektura nie jest tylko nauką narzędzia, ale realnie zmienia sposób projektowania aplikacji pod środowisko produkcyjne.
