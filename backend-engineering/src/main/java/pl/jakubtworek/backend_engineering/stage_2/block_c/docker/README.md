# Kubernetes-friendly Java application — README teoretyczny

## Cel dokumentu

Ten dokument opisuje teoretyczny model aplikacji javowej przygotowanej do działania w Kubernetesie. Nie jest to klasyczny poradnik „krok po kroku”, ani lista komend do uruchomienia projektu. Celem jest pokazanie, jakie założenia powinien spełniać kod aplikacji, aby dobrze współpracował z Kubernetesem: jak sygnalizować gotowość, jak odróżniać gotowość od żywotności procesu, jak bezpiecznie kończyć działanie po `SIGTERM`, jak traktować konfigurację, jak myśleć o stateless design i dlaczego aplikacja nie powinna zakładać trwałości pojedynczej instancji.

Przykładowy projekt jest oparty o Javę i Spring Boot. Nie chodzi jednak o sam Spring Boot. Framework jest tu tylko wygodnym sposobem pokazania szerszych zasad: aplikacja uruchomiona w Kubernetesie powinna być przewidywalna, jawnie konfigurowalna, możliwa do uruchomienia w wielu replikach, odporna na restarty oraz zdolna do komunikowania swojego stanu platformie.

Najważniejsza myśl jest taka: aplikacja nie działa już jako pojedynczy, ręcznie uruchomiony proces na trwałym serwerze. Działa jako jedna z wielu potencjalnie wymienialnych instancji. Kubernetes może ją zrestartować, przenieść, zastąpić nową wersją, skalować w górę lub w dół i kierować do niej ruch tylko wtedy, gdy sama zadeklaruje gotowość.

## Aplikacja w Kubernetesie jako nietrwała instancja procesu

W tradycyjnym modelu często myśli się o aplikacji jako o procesie działającym na konkretnym serwerze. Ten serwer ma nazwę, adres IP, lokalny system plików i długi czas życia. W Kubernetesie takie założenie jest niebezpieczne. Pod jest efemeryczny. Może zostać usunięty podczas aktualizacji, awarii noda, skalowania w dół albo zwykłego rollout’u. Nowy Pod może mieć inny adres IP i inną historię lokalnego systemu plików.

Z tego wynika podstawowy wymóg projektowy: aplikacja nie powinna przywiązywać krytycznego stanu do konkretnej instancji procesu. Sesje użytkowników, zadania oczekujące, pliki wymagające trwałości i dane biznesowe powinny znajdować się w zewnętrznych systemach przeznaczonych do przechowywania stanu, takich jak baza danych, cache, kolejka, storage obiektowy albo trwały wolumen, jeżeli rzeczywiście jest potrzebny. Pamięć procesu i lokalny dysk kontenera mogą być użyteczne, ale nie powinny być jedynym źródłem prawdy.

Wersja javowa tego konceptu pokazuje aplikację, którą można uruchomić w wielu replikach. To oznacza, że każda instancja powinna być traktowana jako równorzędna. Żadna replika nie powinna zakładać, że jest jedyną aktywną kopią aplikacji. Ma to znaczenie dla zadań cyklicznych, przetwarzania kolejek, cache lokalnego, migracji bazy danych i operacji, które nie są idempotentne.

## Dlaczego health endpointy są częścią kontraktu aplikacji

W Kubernetesie endpointy zdrowia nie są ozdobnikiem. Są kontraktem między aplikacją a platformą. Kubernetes nie zna wewnętrznej logiki aplikacji. Nie wie, czy zakończyła inicjalizację, czy może już przyjmować ruch, czy właśnie kończy działanie, czy utknęła w stanie wymagającym restartu. Aplikacja musi te informacje wystawić w sposób jawny.

W projekcie rozdzielone są trzy endpointy: `/startupz`, `/readyz` i `/livez`. To rozdzielenie jest ważniejsze niż same nazwy. Każdy z tych endpointów odpowiada na inne pytanie i powinien mieć inną semantykę.

`/startupz` odpowiada na pytanie, czy aplikacja zakończyła fazę startu. W Javie, szczególnie przy większych aplikacjach Spring Boot, start może obejmować inicjalizację kontekstu, załadowanie konfiguracji, przygotowanie połączeń, migracje zależne od architektury, rozgrzanie cache’a albo inne czynności startowe. Kubernetes powinien dać aplikacji czas na start i nie traktować długiego startu jako awarii procesu.

`/readyz` odpowiada na pytanie, czy Pod powinien dostawać ruch. Aplikacja może być uruchomiona, ale jeszcze niegotowa. Może też być w trakcie zamykania, czyli w trybie drain. Wtedy proces nadal istnieje, ale nie powinien przyjmować nowych żądań. Readiness jest więc mechanizmem sterowania ruchem. Jeżeli readiness nie przechodzi, Pod powinien zostać wyjęty z backendów Service.

`/livez` odpowiada na pytanie, czy proces jest wewnętrznie zdrowy i czy restart może pomóc. To powinien być najbardziej konserwatywny z tych endpointów. Liveness nie powinien padać tylko dlatego, że chwilowo niedostępna jest baza danych, cache albo zewnętrzne API. Restart aplikacji zwykle nie naprawia awarii bazy danych. Jeżeli wiele Podów zacznie się restartować z powodu tej samej zależności, awaria może się pogłębić.

Najczęstszy błąd polega na traktowaniu readiness i liveness jako tego samego testu. To jest zły model. Readiness decyduje o przyjmowaniu ruchu. Liveness decyduje o restarcie procesu. Jeżeli te dwa pojęcia zostaną pomylone, Kubernetes może albo wysyłać ruch do aplikacji, która nie jest gotowa, albo restartować aplikację, której restart niczego nie naprawi.

## Startup jako osobny etap życia aplikacji

Aplikacje javowe często mają wyraźną fazę startu. Spring Boot potrafi szybko wystartować w małych projektach, ale w realnych systemach start może obejmować wiele zależności, skanowanie klas, inicjalizację beanów, połączenia do zewnętrznych usług i przygotowanie lokalnego stanu. Kubernetes powinien rozumieć, że wolny start nie jest tym samym co martwy proces.

Dlatego w projekcie występuje osobny komponent odpowiedzialny za start aplikacji, reprezentowany przez `StartupCoordinator`. Jego rolą nie jest wykonanie biznesowej logiki, lecz pokazanie, że aplikacja może mieć kontrolowany moment przejścia ze stanu „proces działa” do stanu „aplikacja zakończyła inicjalizację”. Dopiero po tym przejściu endpoint startup może zwracać sukces.

Teoretycznie jest to bardzo istotne. Proces systemowy może istnieć, port HTTP może już nasłuchiwać, ale aplikacja nadal może nie być gotowa do obsługi prawdziwego ruchu. W Kubernetesie ten niuans ma znaczenie, bo Service może zacząć kierować ruch do Poda wtedy, gdy readiness uzna go za gotowy. Startup probe pozwala chronić aplikację przed przedwczesnym działaniem liveness probe w czasie długiego startu.

## Readiness jako decyzja o ruchu

Readiness powinien być projektowany z perspektywy pytania: „czy ta konkretna instancja powinna w tej chwili przyjmować nowe żądania?”. Odpowiedź może być negatywna z wielu powodów. Aplikacja może jeszcze startować. Może kończyć działanie po `SIGTERM`. Może utracić krytyczną zależność, bez której nie potrafi obsługiwać żądań. Może być przeciążona lub znajdować się w trybie konserwacyjnym.

W projekcie stan gotowości jest trzymany w komponencie `ApplicationHealthState`. To on wie, czy aplikacja zakończyła startup, czy weszła w drain mode oraz czy zależność symulowana przez `DependencyHealth` jest dostępna. Dzięki temu readiness nie jest przypadkowym testem HTTP, tylko wynikiem świadomego modelu stanu aplikacji.

W praktyce readiness powinien być szybki i stabilny. Nie powinien wykonywać kosztownych operacji przy każdym wywołaniu. Jeżeli gotowość zależy od bazy danych lub innej usługi, często lepszym wzorcem jest odświeżanie statusu zależności w tle i trzymanie ostatniego znanego stanu w pamięci. W przeciwnym razie same probe’y mogą stać się dodatkowym obciążeniem dla zależności.

Readiness może zależeć od zewnętrznych usług, ale trzeba robić to ostrożnie. Jeżeli aplikacja może obsłużyć część ruchu bez danej zależności, nie zawsze powinna całkowicie wypadać z Service. Jeżeli natomiast bez tej zależności każde żądanie kończy się błędem, readiness powinien zwrócić brak gotowości. To jest decyzja architektoniczna, nie automatyczna reguła.

## Liveness jako decyzja o restarcie

Liveness powinien odpowiadać na pytanie, czy proces jest w stanie, z którego restart może go wyprowadzić. Przykładem może być deadlock, całkowite zablokowanie wewnętrznego workera, utrata krytycznego stanu procesu albo sytuacja, w której aplikacja nie jest już zdolna do obsługi żadnych żądań z powodu własnej awarii.

Nie powinno się używać liveness jako prostego testu „czy wszystkie zależności działają”. To prowadzi do fałszywych restartów. Jeżeli baza danych jest chwilowo niedostępna, wszystkie repliki aplikacji mogą zacząć zwracać błąd liveness i Kubernetes zacznie je restartować. Po restarcie aplikacje ponownie spróbują połączyć się z bazą, zwiększając presję na i tak uszkodzony system. Efekt może być gorszy niż sama awaria zależności.

W projekcie ten model widać w metodzie `isLive()`. Liveness zależy od wewnętrznej flagi symulującej deadlock, a nie od stanu zależności zewnętrznej. To celowe uproszczenie pokazujące właściwy kierunek: liveness ma mierzyć zdrowie procesu, readiness ma mierzyć zdolność do przyjmowania ruchu.

## Graceful shutdown i drain mode

Kubernetes kończy Pod przez wysłanie sygnału `SIGTERM`, a po upływie okresu łaski może zakończyć proces siłowo. Aplikacja powinna traktować `SIGTERM` jako normalną część cyklu życia, a nie jako rzadką sytuację awaryjną. Rolling update, skalowanie w dół i przenoszenie obciążenia stale korzystają z tego mechanizmu.

W Javie i Spring Boot podstawą jest skonfigurowanie łagodnego shutdownu przez `server.shutdown: graceful` oraz kontrolowany timeout dla fazy zamykania. W projekcie dodatkowo występuje `GracefulShutdownService`, który oznacza aplikację jako będącą w trybie drain. To oznacza, że aplikacja nadal działa, ale readiness powinien przestać przechodzić. Dzięki temu Kubernetes może przestać kierować do niej nowy ruch, zanim proces ostatecznie się zakończy.

To zachowanie jest bardzo ważne podczas aktualizacji. Rolling update zakłada, że stare Pody będą stopniowo usuwane, a nowe Pody będą stopniowo dodawane. Jeżeli stary Pod przyjmuje nowe żądania aż do ostatniej chwili i zostaje nagle ubity, użytkownicy mogą zobaczyć błędy. Jeżeli natomiast Pod najpierw przestaje być ready, kończy obsługę bieżących żądań i dopiero potem wychodzi, aktualizacja jest znacznie bezpieczniejsza.

Graceful shutdown nie jest tylko konfiguracją Kubernetesa. Kod aplikacji musi współpracować. Workery powinny kończyć lub bezpiecznie przerywać pracę. Konsumenci kolejek powinni przestać pobierać nowe wiadomości. Długie żądania powinny mieć rozsądne timeouty. Połączenia powinny zostać zamknięte. Aplikacja nie powinna ignorować sygnału zakończenia.

## Konfiguracja z zewnątrz

Aplikacja gotowa na Kubernetes nie powinna mieć konfiguracji zaszytej w obrazie. Obraz kontenera powinien być tym samym artefaktem niezależnie od środowiska. Różnice między developmentem, stagingiem i produkcją powinny wynikać z konfiguracji dostarczonej przez zmienne środowiskowe, ConfigMapy, Secrety albo inne mechanizmy platformy.

W projekcie konfiguracja jest reprezentowana przez `AppProperties` oraz plik `application.yml`. Parametry takie jak port, opóźnienie startupu i timeout shutdownu mogą pochodzić ze zmiennych środowiskowych. To prosty przykład większej zasady: aplikacja powinna jawnie deklarować, czego potrzebuje, a środowisko powinno dostarczać wartości.

Konfiguracja powinna być walidowana. Jeżeli aplikacja wymaga konkretnego parametru, lepiej zakończyć start z jasnym błędem niż uruchomić się w stanie częściowo błędnym. W środowisku Kubernetes błędna konfiguracja często objawia się pętlą restartów, dlatego komunikaty błędów przy starcie powinny być czytelne i widoczne w logach.

Sekrety wymagają dodatkowej ostrożności. Samo użycie obiektu Secret nie oznacza, że dane są całkowicie bezpieczne. Trzeba uważać, aby nie wypisywać sekretów do logów, nie eksponować ich w endpointach diagnostycznych i nie przyznawać aplikacji szerszych uprawnień niż potrzebuje.

## Obraz kontenera i uruchomienie aplikacji

Dockerfile w tym koncepcie używa builda wieloetapowego. Pierwszy etap buduje aplikację przy pomocy Mavena, a drugi uruchamia gotowy artefakt na obrazie JRE. Sens tego podejścia jest prosty: środowisko runtime nie powinno zawierać narzędzi potrzebnych wyłącznie do kompilacji. Im mniejszy i prostszy obraz runtime, tym mniejsza powierzchnia ataku i zwykle szybsze pobieranie obrazu.

W przypadku Javy trzeba jednak uważać na szczegóły. Obraz oparty o Alpine jest mały, ale nie zawsze jest najlepszym wyborem dla każdej aplikacji i każdej zależności natywnej. Obrazy distroless mogą być jeszcze bardziej restrykcyjne, ale utrudniają debugowanie, ponieważ nie zawierają shella ani typowych narzędzi systemowych. W produkcji wybór obrazu bazowego powinien być świadomą decyzją, nie automatycznym wyborem najmniejszego taga.

Ważny jest również sposób uruchamiania JVM. Parametry takie jak `MaxRAMPercentage` pomagają dostosować zachowanie JVM do limitów pamięci kontenera. `ExitOnOutOfMemoryError` sprawia, że proces kończy się przy krytycznym błędzie pamięci, co pozwala Kubernetesowi go zastąpić. Nie jest to lekarstwo na wycieki pamięci, ale jest lepsze niż pozostawienie procesu w nieprzewidywalnym stanie.

## Deployment jako deklaracja życia aplikacji

Manifest Deployment opisuje, jak aplikacja ma żyć w klastrze. Nie jest to tylko opakowanie wokół obrazu. Deployment określa liczbę replik, strategię aktualizacji, probe’y, zasoby, konfigurację środowiska i kontekst bezpieczeństwa.

Rolling update wymaga, aby wersja stara i nowa mogły przez pewien czas działać równolegle. To ma konsekwencje dla kodu. Zmiany w API, schemacie bazy danych, komunikatach kolejkowych i konfiguracji powinny być kompatybilne przynajmniej przez okres wdrożenia. Kubernetes może pomóc w kontrolowanej wymianie Podów, ale nie rozwiąże problemu niekompatybilnych wersji aplikacji.

Parametry `maxUnavailable` i `maxSurge` wpływają na sposób aktualizacji. Przy `maxUnavailable: 0` Kubernetes nie powinien celowo zmniejszać liczby dostępnych replik podczas rollout’u. `maxSurge: 1` pozwala utworzyć jedną dodatkową replikę ponad zakładaną liczbę. To zwiększa bezpieczeństwo dostępności, ale wymaga dodatkowych zasobów. Nie są to więc parametry kosmetyczne, tylko część strategii niezawodności.

## Service jako stabilny punkt dostępu

Service oddziela stabilną tożsamość sieciową aplikacji od efemerycznych Podów. Pody mogą znikać i pojawiać się z nowymi adresami IP, ale Service pozostaje logicznym punktem dostępu. Inne aplikacje powinny komunikować się z usługą przez nazwę Service, a nie przez adres konkretnego Poda.

To rozróżnienie jest kluczowe. Aplikacja nie powinna zapamiętywać IP backendów ani zakładać, że konkretna instancja będzie istnieć przez długi czas. Kubernetes utrzymuje aktualny zestaw endpointów pasujących do selektora Service. Jeżeli Pod przestaje być ready, powinien wypaść z obsługi ruchu. Jeżeli pojawia się nowy gotowy Pod, może zostać dodany jako backend.

Z punktu widzenia kodu klienta nadal trzeba obsługiwać błędy sieciowe. Service nie gwarantuje, że każde połączenie się powiedzie. Pod może zostać zakończony, połączenie może zostać zerwane, a zależność może być przeciążona. Aplikacja powinna mieć sensowne timeouty, retry i obsługę błędów.

## HPA i skalowanie poziome

Horizontal Pod Autoscaler ma sens tylko wtedy, gdy aplikacja rzeczywiście nadaje się do skalowania poziomego. Jeżeli każda replika jest niezależna, nie trzyma krytycznego stanu lokalnie i może równolegle obsługiwać ruch, HPA może zwiększać liczbę Podów na podstawie metryk. Jeżeli aplikacja ma globalne blokady, lokalny stan albo niekontrolowane zadania cykliczne, samo dodanie HPA nie rozwiąże problemu.

HPA silnie zależy od poprawnych `resources.requests`. Jeżeli requesty CPU są źle dobrane albo ich brakuje, metryki procentowego użycia CPU mogą być mylące. Limity również wymagają ostrożności. Zbyt niski limit pamięci może powodować restarty. Zbyt niski limit CPU może prowadzić do throttlingu i wzrostu latencji. Zbyt wysokie wartości mogą z kolei utrudniać efektywne planowanie zasobów w klastrze.

Skalowanie poziome nie zastępuje dobrej architektury. Jeżeli wąskim gardłem jest baza danych, zwiększanie liczby replik aplikacji może zwiększyć liczbę połączeń i zapytań, pogarszając problem. HPA jest narzędziem, ale nie gwarancją skalowalności.

## Security baseline

Manifest Deployment zawiera kilka podstawowych ustawień bezpieczeństwa: uruchamianie jako non-root, wyłączenie privilege escalation, usunięcie capabilities, użycie profilu seccomp oraz wyłączenie automatycznego montowania tokena ServiceAccount. Te ustawienia nie czynią aplikacji automatycznie bezpieczną, ale tworzą rozsądny punkt wyjścia.

`automountServiceAccountToken: false` jest szczególnie ważne dla aplikacji, które nie muszą komunikować się z API Kubernetesa. Jeżeli aplikacja nie potrzebuje tego tokena, nie powinna go mieć. W razie podatności w aplikacji ogranicza to możliwości atakującego.

Uruchamianie procesu jako non-root ogranicza skutki błędów i podatności. Blokada `allowPrivilegeEscalation` zmniejsza ryzyko podniesienia uprawnień. Usunięcie capabilities ogranicza specjalne uprawnienia jądra Linuksa dostępne dla procesu. Są to mechanizmy obrony warstwowej, a nie zamiennik bezpiecznego kodu.

## Debugowanie w Kubernetesie

Debugowanie aplikacji w Kubernetesie powinno zaczynać się od modelu: obraz, konfiguracja, probe’y, endpointy, logi, zdarzenia, zasoby. Najpierw warto sprawdzić, czy uruchomiono właściwy obraz i właściwą wersję. Potem czy aplikacja dostała oczekiwaną konfigurację. Następnie czy startup, readiness i liveness zachowują się zgodnie z intencją. Dalej czy Service ma endpointy i czy selektory pasują do etykiet Podów. Dopiero potem warto głębiej analizować kod, logi i metryki.

W Kubernetesie nie powinno się polegać na SSH jako podstawowym sposobie diagnozy. Pody są nietrwałe, a obrazy produkcyjne często nie mają narzędzi diagnostycznych. Informacje potrzebne do diagnozy powinny być dostępne przez logi, eventy, metryki, statusy obiektów i jawne endpointy diagnostyczne. To wymaga przygotowania aplikacji wcześniej, a nie dopiero w momencie awarii.

W aplikacji javowej szczególnie ważne są czytelne logi startowe, jawne błędy konfiguracji i kontrolowane zachowanie przy braku zależności. Jeżeli aplikacja nie potrafi wystartować, powinna jasno powiedzieć dlaczego. Jeżeli zależność zewnętrzna jest niedostępna, readiness powinien to odzwierciedlać, ale liveness nie powinien bezmyślnie wymuszać restartów.

## Najważniejszy model mentalny

Ten projekt pokazuje aplikację jako proces, który współpracuje z platformą. Kubernetes nie zna intencji kodu, więc aplikacja musi je wyrazić. Startup mówi, kiedy aplikacja zakończyła inicjalizację. Readiness mówi, kiedy może dostawać ruch. Liveness mówi, kiedy restart ma sens. Graceful shutdown mówi, jak aplikacja kończy pracę bez zrywania ruchu. Konfiguracja zewnętrzna mówi, że obraz jest artefaktem niezależnym od środowiska. Security context mówi, że kontener powinien działać z minimalnymi uprawnieniami.

Najważniejsze nie są konkretne nazwy klas ani endpointów. Najważniejsze jest rozdzielenie odpowiedzialności. Stan zdrowia aplikacji powinien być jawny. Gotowość do ruchu powinna być czymś innym niż żywotność procesu. Shutdown powinien być częścią projektu, a nie przypadkowym efektem zamknięcia JVM. Konfiguracja powinna pochodzić ze środowiska. Aplikacja powinna działać poprawnie w wielu replikach i nie zakładać trwałości pojedynczego Poda.

## Podsumowanie

Aplikacja javowa gotowa na Kubernetes to nie jest tylko plik JAR w obrazie Dockera. To aplikacja zaprojektowana pod środowisko, w którym instancje są nietrwałe, ruch jest kierowany przez Service, aktualizacje odbywają się stopniowo, konfiguracja pochodzi z zewnątrz, a platforma podejmuje decyzje na podstawie jawnych sygnałów zdrowia.

Największa wartość tego konceptu polega na tym, że pokazuje granicę między odpowiedzialnością aplikacji a odpowiedzialnością Kubernetesa. Kubernetes może uruchomić nowe Pody, zatrzymać stare, sprawdzać probe’y, kierować ruch i skalować repliki. Nie naprawi jednak aplikacji, która trzyma stan lokalnie, myli readiness z liveness, ignoruje SIGTERM, ma niejawne zależności konfiguracyjne albo nie potrafi działać w wielu instancjach.

Dlatego projektując aplikację pod Kubernetes, trzeba myśleć nie tylko o kodzie biznesowym, ale też o cyklu życia procesu. Dobrze zaprojektowana aplikacja jasno komunikuje, kiedy startuje, kiedy jest gotowa, kiedy jest żywa, kiedy kończy działanie i jakich zasobów potrzebuje. To jest fundament stabilnego działania w klastrze.
