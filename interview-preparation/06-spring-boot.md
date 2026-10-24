# Spring i Spring Boot

[Spis rozdziałów](README.md)

<!-- question-toc:start -->
## Spis pytań

- [SPR-01. Po co Spring Boot i co oznacza convention over configuration?](#spr-01-po-co-spring-boot-i-co-oznacza-convention-over-configuration)
- [SPR-02. Czym różnią się IoC, DI, BeanFactory i ApplicationContext?](#spr-02-czym-różnią-się-ioc-di-beanfactory-i-applicationcontext)
- [SPR-03. Constructor injection, setter injection czy field injection?](#spr-03-constructor-injection-setter-injection-czy-field-injection)
- [SPR-04. Jak Spring wybiera bean przy wielu implementacjach? Co robią `@Autowired`, `@Qualifier` i `@Primary`?](#spr-04-jak-spring-wybiera-bean-przy-wielu-implementacjach-co-robią-autowired-qualifier-i-primary)
- [SPR-05. Czy dwa beany mogą mieć tę samą nazwę?](#spr-05-czy-dwa-beany-mogą-mieć-tę-samą-nazwę)
- [SPR-06. Jak wygląda lifecycle beana?](#spr-06-jak-wygląda-lifecycle-beana)
- [SPR-07. Co robią `@SpringBootApplication`, `@ComponentScan`, `@Configuration` i `@Bean`?](#spr-07-co-robią-springbootapplication-componentscan-configuration-i-bean)
- [SPR-08. Jak Boot decyduje, które autokonfiguracje zastosować? Czym jest SpringFactoriesLoader?](#spr-08-jak-boot-decyduje-które-autokonfiguracje-zastosować-czym-jest-springfactoriesloader)
- [SPR-09. Co się dzieje od uruchomienia `SpringApplication.run()` do gotowości HTTP?](#spr-09-co-się-dzieje-od-uruchomienia-springapplicationrun-do-gotowości-http)
- [SPR-10. Co daje starter web i jak uruchamia się embedded Tomcat?](#spr-10-co-daje-starter-web-i-jak-uruchamia-się-embedded-tomcat)
- [SPR-11. Czym różnią się `@Component`, `@Service` i `@Repository`? Co jeśli umieszczę wszystkie na jednej klasie?](#spr-11-czym-różnią-się-component-service-i-repository-co-jeśli-umieszczę-wszystkie-na-jednej-klasie)
- [SPR-12. Jak naprawić circular dependency, która ujawnia się po wdrożeniu?](#spr-12-jak-naprawić-circular-dependency-która-ujawnia-się-po-wdrożeniu)
- [SPR-13. Jak działają properties, profile i konfiguracja zewnętrzna?](#spr-13-jak-działają-properties-profile-i-konfiguracja-zewnętrzna)
- [SPR-14. Jak Boot zarządza wersjami zależności?](#spr-14-jak-boot-zarządza-wersjami-zależności)
- [SPR-15. Jak request przechodzi przez Spring MVC?](#spr-15-jak-request-przechodzi-przez-spring-mvc)
- [SPR-16. `@Controller` czy `@RestController`? `@ControllerAdvice` czy `@RestControllerAdvice`?](#spr-16-controller-czy-restcontroller-controlleradvice-czy-restcontrolleradvice)
- [SPR-17. Jak zaprojektować globalną obsługę wyjątków?](#spr-17-jak-zaprojektować-globalną-obsługę-wyjątków)
- [SPR-18. Filter czy Interceptor? Jak zmienić request przed kontrolerem?](#spr-18-filter-czy-interceptor-jak-zmienić-request-przed-kontrolerem)
- [SPR-19. Gdzie użyć `@Async` i co dzieje się z wyjątkiem?](#spr-19-gdzie-użyć-async-i-co-dzieje-się-z-wyjątkiem)
- [SPR-20. Co daje Spring Cache i jakie pułapki ma adnotacyjne cache'owanie?](#spr-20-co-daje-spring-cache-i-jakie-pułapki-ma-adnotacyjne-cacheowanie)
- [SPR-21. Dlaczego zadanie zaplanowane uruchamia się pięć razy zamiast raz?](#spr-21-dlaczego-zadanie-zaplanowane-uruchamia-się-pięć-razy-zamiast-raz)
- [SPR-22. Jak skonfigurować dwie bazy i znaczenie primary/secondary?](#spr-22-jak-skonfigurować-dwie-bazy-i-znaczenie-primarysecondary)
- [SPR-23. Jak wyjaśniać zastosowanie adnotacji Springa zamiast wyliczać „wszystkie”?](#spr-23-jak-wyjaśniać-zastosowanie-adnotacji-springa-zamiast-wyliczać-wszystkie)
<!-- question-toc:end -->

## SPR-01. Po co Spring Boot i co oznacza convention over configuration?

Spring dostarcza kontener i modele integracji, a Boot składa typowy runtime przez startery, autokonfigurację, zarządzanie zależnościami i narzędzia operacyjne. Starter dostarcza biblioteki; warunki autokonfiguracji decydują o rejestracji zwykłych beanów. Własny DataSource może wycofać default, jeśli tak stanowi konfiguracja.

Convention over configuration ogranicza powtarzane deklaracje, ale nie zastępuje decyzji o timeoutach, zasobach i bezpieczeństwie. Boot daje konfigurację Java/properties, nie usuwa możliwości XML.

Przykład: obecność sterownika DB spełnia warunek klasy, lecz utworzenie połączenia wymaga jeszcze URL, credential i sieci. Przy błędzie rozdzielam „autokonfiguracja nie pasuje” od „pasuje, ale bean nie dał się utworzyć”. Raport warunków i przyczyna wyjątku odpowiadają wtedy na różne pytania. Zamiast dodawać kolejne adnotacje, sprawdzam konkretny warunek i efektywną konfigurację.

## SPR-02. Czym różnią się IoC, DI, BeanFactory i ApplicationContext?

IoC oznacza odwrócenie sterowania, a DI jest sposobem dostarczania zależności z zewnątrz. BeanFactory jest podstawowym kontraktem tworzenia i wyszukiwania beanów. ApplicationContext dodaje m.in. zdarzenia, zasoby, environment i integrację typowych rozszerzeń kontenera.

Kontener rejestruje definicje, rozwiązuje zależności, tworzy instancje i stosuje lifecycle/post-processory. Nie czyni automatycznie domeny testowalną: konstruktor z jawnymi zależnościami pozwala używać klasy również bez kontekstu Springa.

W zwykłym Java zależność można dostarczyć bez kontenera; Spring automatyzuje składanie i lifecycle. To ważne, bo logika kalkulacji nie musi znać ApplicationContext ani wykonywać getBean wewnątrz metody domenowej. Taki service locator ukrywa zależności i utrudnia test.

ApplicationContext tworzy wiele singletonów podczas refresh, aby wykryć błędy wcześnie; lazy oraz factory beany mogą zmienić ten moment. BeanDefinition opisuje sposób utworzenia, a nie gotową instancję. Przy diagnostyce pytam więc, czy bean nie został zarejestrowany, czy nie został jeszcze utworzony, czy jego inicjalizacja zakończyła się błędem.

## SPR-03. Constructor injection, setter injection czy field injection?

Konstruktor wyraża wymagane zależności i umożliwia finalne pola oraz zwykły test jednostkowy. Setter pasuje do świadomie opcjonalnej lub wymiennej konfiguracji, choć dopuszcza stan częściowy. Field injection ukrywa kontrakt tworzenia obiektu i utrudnia testy bez refleksji.

Nie rozwiązuję nadmiernej liczby parametrów samym przejściem na pola: to często sygnał zbyt wielu odpowiedzialności. Dla jednego konstruktora współczesny Spring zwykle nie wymaga `@Autowired`.

Szkic wstrzykiwania przez konstruktor:

<!-- example:SPR-03-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@Service
class CheckoutService {
    private final PaymentGateway payments;
    CheckoutService(PaymentGateway payments) {
        this.payments = payments;
    }
}
~~~

PaymentGateway oznacza kontrakt domenowy, a adnotacja wymaga Springa. W teście mogę utworzyć serwis przez new z kontrolowaną implementacją bramki. Setter może być właściwy dla opcjonalnej polityki, jeśli obiekt ma bezpieczny default. Wielki konstruktor skłania do analizy granic przypadku użycia, lecz nie każda duża liczba zależności automatycznie uzasadnia „fasadę” ukrywającą ten sam chaos.

## SPR-04. Jak Spring wybiera bean przy wielu implementacjach? Co robią `@Autowired`, `@Qualifier` i `@Primary`?

Autowired uruchamia rozwiązywanie zależności, qualifier zawęża kandydatów, a primary wskazuje preferencję wśród pasujących beanów. Znaczenie mogą mieć też nazwa punktu wstrzyknięcia i szczegóły wersji. Brak jednoznaczności może zakończyć start błędem.

W domenie nazywam role, np. operator płatności krajowych, zamiast liczyć na przypadkową kolejność skanowania. Kolekcja implementacji jest dobrym rozwiązaniem, gdy rzeczywiście chcę wszystkie strategie. Nie mylę kwalifikatora z utworzeniem nowego scope.

Dwie implementacje powinny mieć zrozumiałe role:

<!-- example:SPR-04-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
CheckoutService(@Qualifier("domesticGateway") PaymentGateway payments) {
    this.payments = payments;
}
~~~

To fragment konstruktora Spring. Qualifier zawęża zbiór, a nie wyszukuje magicznie operatora według reguł biznesowych. Jeśli wybór zależy od kraju konkretnego zamówienia, potrzebna jest jawna strategia/routing w czasie wykonania, nie tylko konfiguracja DI przy starcie. Z kolei Primary jest sensowny jako default, ale nie powinien ukrywać przypadkowo zarejestrowanej drugiej implementacji. Sprawdzam również typy generyczne i nazwy konfiguracji.

## SPR-05. Czy dwa beany mogą mieć tę samą nazwę?

W jednym rejestrze nazwa identyfikuje definicję; konflikt może zakończyć się błędem lub zastąpieniem definicji, jeśli overriding jest włączony. To zależy od konfiguracji i hierarchii kontekstów. Dwa różne typy z taką samą nazwą nie tworzą automatycznie dwóch niezależnych wpisów.

Preferuję jawne unikalne nazwy i unikam nadpisywania jako przypadkowego skutku kolejności ładowania. Problem dwóch beanów tego samego typu z różnymi nazwami dotyczy wyboru zależności, a nie kolizji rejestracji.

Kolizja może powstać przez dwie klasy o tej samej prostej nazwie w różnych pakietach skanowanych domyślnym generatorem nazw. Inną przyczyną są dwie metody Bean o takim samym jawnym identyfikatorze. Zmiana kolejności classpath nie powinna rozstrzygać, który operator płatności działa.

W hierarchii kontekstów bean dziecka może przesłaniać nazwę rodzica według reguł lookup; to odmienny scenariusz od dwóch definicji w tym samym rejestrze. Diagnozuję pochodzenie definicji i cel override. Jeżeli zastępowanie jest potrzebne w teście, ograniczam je do jawnej konfiguracji testowej.

## SPR-06. Jak wygląda lifecycle beana?

W uproszczeniu: wybór definicji, utworzenie instancji, wstrzyknięcie zależności, callbacki Aware, post-processory przed inicjalizacją, `@PostConstruct`/init i post-processory po inicjalizacji, które mogą zwrócić proxy. Kolejność szczegółowa zależy od rozszerzeń.

Przy zamykaniu kontekstu Spring wywołuje właściwe callbacki niszczenia zarządzanych singletonów. Dla prototype nie zarządza automatycznie całym sprzątaniem. Nie polegam na `@Transactional` wywołanym na sobie w inicjalizacji; infrastruktura proxy może jeszcze nie działać zgodnie z oczekiwaniem.

Proxy zwrócone przez post-processor może mieć inną klasę runtime niż obiekt docelowy. Dlatego porównywanie getClass do konkretnej klasy albo ręczne wyciąganie targetu może zepsuć kontrakt. Klient powinien zależeć od publicznej roli beana.

Inicjalizacja zasobu i gotowość biznesowa też nie muszą być tym samym: bean może istnieć, a dopiero później zapełnić lokalny indeks. Stan gotowości powinien mieć sygnał dla readiness. Shutdown porządkuje się tak, aby najpierw zatrzymać nowe zadania, potem dokończyć aktywne i na końcu zamknąć klientów, których te zadania używają.

## SPR-07. Co robią `@SpringBootApplication`, `@ComponentScan`, `@Configuration` i `@Bean`?

SpringBootApplication łączy konfigurację Boota, włączenie autokonfiguracji i component scanning. Skanowanie odkrywa stereotypy w określonych pakietach. Configuration definiuje konfigurację, a Bean rejestruje wynik metody fabrykującej, także dla klas zewnętrznej biblioteki.

Umiejscowienie klasy startowej wpływa na domyślne pakiety. Zbyt szerokie skanowanie może wciągnąć cudze konfiguracje, zbyt wąskie zgubić zależności. Nie zakładam identycznego zachowania wywołania metody Bean przy pełnym proxy konfiguracji i `proxyBeanMethods=false`.

Różnica proxyBeanMethods jest istotna przy bezpośrednich wywołaniach metod konfiguracji. W pełnej konfiguracji wywołanie metody Bean może zostać przechwycone i zwrócić bean kontenera; w trybie bez takiego proxy zwykłe wywołanie metody tworzy zwykły obiekt.

Czytelniejszy wariant przekazuje zależność jako argument fabryki:

<!-- example:SPR-07-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@Bean
CheckoutService checkoutService(PaymentGateway gateway) {
    return new CheckoutService(gateway);
}
~~~

To szkic konfiguracji Spring. Domena nie musi wiedzieć, jak obiekty zostały złożone. Wybór jawnych importów konfiguracji zamiast szerokiego scan bywa korzystny dla izolowanych modułów i testów.

## SPR-08. Jak Boot decyduje, które autokonfiguracje zastosować? Czym jest SpringFactoriesLoader?

Kandydaci są odkrywani z metadanych, a warunki sprawdzają np. classpath, brak własnego beana, właściwości i rodzaj aplikacji. W aktualnych liniach Boota listę kandydatów dostarcza `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

SpringFactoriesLoader ładuje implementacje z metadanych `spring.factories`; nie jest jedynym współczesnym mechanizmem rejestracji autokonfiguracji. Przy problemie czytam condition evaluation report i exclusions. Starsze odpowiedzi o wyłącznym użyciu spring.factories trzeba opatrzyć wersją. [Autokonfiguracja Boota](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html).

Conditions są oceniane w odpowiednich fazach rejestracji, a kolejność autokonfiguracji pomaga, gdy jedna zależy od beanów innej. Nie należy polegać na arbitralnej kolejności plików JAR ani używać szerokiego ComponentScan jako mechanizmu ładowania całej autokonfiguracji biblioteki.

<!-- example:SPR-08-1 kind:properties-fragment -->
**Kontekst:** Fragment konfiguracji properties; wymaga zgodnej wersji biblioteki i otoczenia; bez testu uruchomienia.

~~~properties
debug=true
~~~

Wspierana konfiguracja Boota udostępnia wtedy raport warunków w logach. Szukam konkretnie: który warunek klasy/właściwości/braku beana nie został spełniony. Samo „auto-configuration enabled” nie mówi, że utworzono określony DataSource. Endpoint diagnostyczny może pomóc, ale jego ekspozycja musi być kontrolowana.

<!-- follow-up:SPR-08 -->
**Sprawdź rozumienie:** Po dodaniu własnego DataSource zniknęło domyślne połączenie Boota. Czy to musi być błąd?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie. Warunek braku własnego beana może świadomie wycofać autokonfigurację. Raport warunków wyjaśnia wybór, a błąd tworzenia własnego zasobu jest oddzielnym problemem.

</details>

## SPR-09. Co się dzieje od uruchomienia `SpringApplication.run()` do gotowości HTTP?

Boot przygotowuje environment, wybiera rodzaj kontekstu, ładuje definicje i odświeża kontekst, tworząc niezbędne beany oraz infrastrukturę web. W lifecycle uruchamiany jest serwer, wykonywane są callbacki i runnery, a aplikacja ogłasza odpowiednie zdarzenia gotowości.

Przy wolnym starcie mierzę kroki inicjalizacji, skanowanie, migracje, połączenia i własne callbacki. Lazy initialization może przesunąć błąd na pierwszy request. Nie mylę „port słucha” z gotowością biznesową do przyjęcia ruchu.

Start może zwalniać poza samym kontenerem: migracja schematu czeka na lock, DNS ma timeout, pobieranie sekretu jest wolne albo własny runner importuje duży słownik. ApplicationStartup/metryki startu pomagają oddzielić skanowanie i tworzenie beanów od tych zależności.

Przykład pomiaru: 5 s budowania kontekstu i 80 s migracji oznacza, że ograniczenie scan nie usunie głównego problemu. Lazy initialization może skrócić raportowany start, lecz przenieść błąd oraz koszt na pierwszego użytkownika. Readiness powinna obejmować minimalne dane potrzebne do bezpiecznej obsługi, a nie tylko ukończenie jednego callbacka.

## SPR-10. Co daje starter web i jak uruchamia się embedded Tomcat?

Starter agreguje zależności; dla typowej konfiguracji MVC zawiera wsparcie Spring MVC, JSON i domyślny serwer servletowy. Warunki autokonfiguracji wykrywają potrzebne klasy, a fabryka serwera tworzy embedded runtime z właściwościami aplikacji.

Sama zależność nie jest kodem obsługującym request. Inny starter, wyłączenie Tomcata, własna fabryka czy inna linia Boota zmieniają wynik. Sprawdzam dependency tree i raport warunków, zamiast zakładać, że każdy projekt Spring jest aplikacją Tomcat.

Starter zwykle dołącza zależności przechodnie, a wykluczenie serwera i dodanie innego zmienia wykrywaną implementację. Gdy serwis uruchamia się jako aplikacja nie-web, obecność przypadkowej biblioteki servletowej lub jawne ustawienie typu aplikacji może wpływać na wynik.

Przy błędzie „port zajęty” rozróżniam utworzenie fabryki serwera od faktycznego startu connectora. Przy złej odpowiedzi HTTP sprawdzam mapping i filtry, nie samą obecność Tomcata. Embedded oznacza lifecycle wewnątrz procesu aplikacji; nadal trzeba konfigurować connection limits, timeouty, graceful shutdown i zachowanie za proxy.

## SPR-11. Czym różnią się `@Component`, `@Service` i `@Repository`? Co jeśli umieszczę wszystkie na jednej klasie?

Service i Repository są wyspecjalizowanymi stereotypami komponentu. Service komunikuje rolę aplikacyjną; Repository może uczestniczyć w tłumaczeniu wyjątków persystencji, jeśli działa odpowiednia infrastruktura. Nie dodają automatycznie transakcji ani thread-safety.

Kilka stereotypów na tej samej klasie nie tworzy trzech warstw i zwykle jest redundantne; sprzeczne jawne nazwy mogą wywołać konflikt. Wybieram jedną odpowiedzialność. Kontroler zawierający SQL pozostaje złym podziałem, nawet jeżeli otrzyma wszystkie adnotacje.

Repository może być punktem tłumaczenia wyjątków providera na hierarchię DataAccessException, ale musi działać infrastruktura post-processora i właściwy przepływ wywołań. Nie jest to polecenie „zapewnij rollback każdej metody”. Service pozostaje wskazówką roli i miejscem organizowania przypadków użycia.

Dodanie Controller do serwisu może wciągnąć klasę w wykrywanie handlerów, lecz nie utworzy sensownego API bez mappingów. Jeśli jedna klasa ma trzy stereotypy, pytam, czy miesza transport, orkiestrację i SQL. Rozdział odpowiedzialności powinien wynikać z zachowania, a nie z liczby adnotacji.

## SPR-12. Jak naprawić circular dependency, która ujawnia się po wdrożeniu?

Najpierw porównuję profile i faktyczny graf beanów. Cykl konstruktorów nie pozwala zbudować poprawnej kolejności; dopuszczenie części cykli setterów nie czyni projektu dobrym. Deployment mógł aktywować dodatkową konfigurację lub implementację.

Wyodrębniam wspólną odpowiedzialność albo koordynator przypadku użycia; czasem właściwe jest zdarzenie. Lazy/provider może odroczyć rozwiązanie, ale ukrywa zależność i nie zawsze usuwa cykl wykonania. Nie włączam globalnie circular references jako pierwszej naprawy.

W cyklu OrderService → PaymentService → OrderService czasem brakuje trzeciej roli CheckoutCoordinator, która wywołuje oba serwisy. Innym razem oba potrzebują wspólnej małej polityki i nie powinny zależeć od siebie. Wyodrębnienie wspólnego „Utils” z całym stanem tylko przeniesie problem.

Event może usunąć zależność czasową, lecz zmieni moment wykonania, obsługę błędu i transakcję. Nie wprowadzam go wyłącznie w celu obejścia kontenera. Testuję graf uruchomienia dla rzeczywistych profili, bo lokalny mock może ukryć cykl obecny dopiero w produkcyjnej konfiguracji.

## SPR-13. Jak działają properties, profile i konfiguracja zewnętrzna?

Environment łączy źródła konfiguracji według określonego priorytetu. Profile aktywują warianty definicji i właściwości, np. pliki profilowe. `@Value` pasuje do małej wartości, a `@ConfigurationProperties` do typowanej, walidowanej konfiguracji grupy ustawień.

Profil można aktywować np. właściwością `spring.profiles.active`; środowisko uruchomienia dostarcza ją przez zmienną lub argument. Nie commituję sekretów do application.properties. Diagnozuję źródło nadpisania, a endpointy ujawniające konfigurację zabezpieczam. [Konfiguracja zewnętrzna](https://docs.spring.io/spring-boot/reference/features/external-config.html).

Typowana konfiguracja pozwala grupować i walidować kontrakt:

<!-- example:SPR-13-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@ConfigurationProperties(prefix = "payments")
record PaymentProperties(java.time.Duration timeout, int maxConcurrent) {}
~~~

Szkic wymaga rejestracji properties w Springu; walidacja dodatnich wartości wymaga odpowiednich constraints i infrastruktury. Wartość „200” bez jednostki jest mniej czytelna niż jawny Duration. Profile nie powinny być zbiorem niekontrolowanych wyjątków domenowych. Porównuję efektywną konfigurację i źródło wartości, ale nie wysyłam sekretów do logów podczas takiego porównania.

## SPR-14. Jak Boot zarządza wersjami zależności?

BOM/dependency management definiuje zestaw wersji sprawdzanych razem. Starter dodaje zależności funkcjonalne, a mechanizm budowania rozwiązuje ich graf. Maven parent może dodatkowo ustawiać pluginy i defaults; samo importowanie BOM nie jest pełnym odpowiednikiem parenta.

Jawne nadpisanie wersji może być potrzebne, ale wymaga sprawdzenia kompatybilności i testów. Przy konflikcie czytam dependency tree i effective POM. Nie wybieram niezależnie „najnowszego” Hibernate i Spring Data bez sprawdzenia wspieranej kombinacji.

Dwie wersje tej samej biblioteki mogą wejść przez różne zależności przechodnie. O wyniku decyduje mechanizm resolution narzędzia, a nie kolejność importów w kodzie. Raport dependency tree wyjaśnia, skąd pochodzi wybrana wersja; effective POM także odziedziczone ustawienia.

<!-- example:SPR-14-1 kind:shell-command -->
**Kontekst:** Przykładowe polecenia — PID, ścieżki i środowisko należy dopasować; skrypt ich nie wykonuje.

~~~shell
mvn dependency:tree
mvn help:effective-pom
~~~

To polecenia diagnostyczne dla modułu Maven. Po świadomym override testuję integrację używającą problematycznego API, nie tylko kompilację. NoSuchMethodError na produkcji często wskazuje rozjazd binarny między wersjami obecnymi w buildzie i runtime.

## SPR-15. Jak request przechodzi przez Spring MVC?

Żądanie przechodzi przez servlet filters, DispatcherServlet, wybór handlera, adapter, argument resolvers, binding i walidację. Kontroler wywołuje przypadek użycia, a wynik jest renderowany jako widok albo serializowany przez message converter. Wyjątek może zostać obsłużony przez resolver/advice.

Logika domeny nie należy do kontrolera. Czas odpowiedzi obejmuje też filtry, security, kolejki i serializację, więc szybki SQL nie dowodzi szybkiego requestu. Nie zwracam przypadkowo encji z lazy relacjami jako publicznego kontraktu JSON.

Binding może zakończyć request przed wejściem do metody: niepoprawny typ parametru, brak wymaganej wartości albo walidacja body daje inny punkt błędu niż wyjątek domenowy z serwisu. Security filter również może odrzucić żądanie jeszcze przed DispatcherServlet.

W trace warto odróżnić handler od całego czasu HTTP. Jeżeli serwis trwa 20 ms, a odpowiedź 2 s, winny może być duży JSON, lazy loading wywołany serializacją lub wolny odbiorca. DTO daje kontrolę nad kształtem danych i ogranicza przypadkowe ujawnienie relacji, pól wewnętrznych i zmian schematu encji.

## SPR-16. `@Controller` czy `@RestController`? `@ControllerAdvice` czy `@RestControllerAdvice`?

Controller może obsługiwać widoki; RestController łączy Controller z ResponseBody, więc wynik jest domyślnie treścią odpowiedzi. Analogicznie RestControllerAdvice dodaje semantykę ResponseBody do globalnych handlerów. ResponseEntity pozwala jawnie określić status, nagłówki i body.

Zwykły Controller też może zwracać JSON po użyciu ResponseBody. Advice nie gwarantuje obsłużenia błędów z każdego filtra, serwera czy background taska. Dla security i async trzeba zadbać o właściwe punkty obsługi.

Zwrócenie String z Controller może oznaczać nazwę widoku, a z RestController tekst odpowiedzi. Ten sam typ zwracany nie ma więc identycznej semantyki w obu konfiguracjach.

<!-- example:SPR-16-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@RestController
class StatusController {
    @GetMapping("/status")
    java.util.Map<String, String> status() {
        return java.util.Map.of("state", "ready");
    }
}
~~~

To szkic Spring MVC. Nie używam tego endpointu jako pełnej sondy bez ustalenia, co oznacza ready. Advice można ograniczyć do pakietu lub adnotacji, ale zbyt wiele nakładających się handlerów i niejawna kolejność utrudniają stabilny kontrakt błędów.

## SPR-17. Jak zaprojektować globalną obsługę wyjątków?

Mapuję błędy walidacji, brak zasobu, konflikt domenowy i nieoczekiwany błąd na spójne statusy i strukturę, np. Problem Details. Zwracam kod problemu i bezpieczny opis oraz identyfikator do korelacji. Przyczynę techniczną zachowuję w chronionych logach.

Nie zamieniam wszystkich wyjątków na HTTP 200 ani nie ujawniam SQL i sekretów. Osobno konfiguruję authentication entry point/access denied handler oraz obsługę błędów zadań. Testuję również brak efektu ubocznego i zgodność kontraktu błędu.

Szkic jawnej odpowiedzi dla konfliktu:

<!-- example:SPR-17-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(OrderConflict.class)
    ResponseEntity<ProblemDetail> conflict(OrderConflict failure) {
        var body = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "Zamówienie zostało już zmienione");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
~~~

OrderConflict jest typem domenowym; pozostałe typy pochodzą ze Springa. Nie wstawiam getMessage nieznanego wyjątku bezpośrednio do odpowiedzi, bo może zawierać SQL lub token. Test kontraktu obejmuje status, typ problemu, bezpieczny opis i brak częściowego skutku.

## SPR-18. Filter czy Interceptor? Jak zmienić request przed kontrolerem?

Filter działa na poziomie servletowym i może otoczyć request/response wrapperem; security zwykle opiera się na łańcuchu filtrów. HandlerInterceptor działa po wyborze handlera MVC, nadaje się np. do metryk związanych z endpointem.

RequestBodyAdvice lub argument resolver lepiej pasuje do części zmian deserializacji/argumentów. Body może być strumieniem jednokrotnego odczytu, więc logujący filtr musi uważać na buforowanie i limity. Nie ufam nagłówkowi użytkownika dopisanemu przez niezaufanego klienta.

Do ponownego odczytu body filtr może potrzebować wrappera, ale musi kontrolować rozmiar bufora i semantykę strumienia. Prosty filtr logujący całe uploady może wywołać OOM lub zapisać dane osobowe. Nagłówki również nie są nieskończone i mają własne ograniczenia.

Dla modyfikacji domenowego DTO lepszy bywa jawny mapper/argument resolver niż ukryte przepisywanie surowego JSON. Interceptor MVC nie chroni wszystkich innych endpointów serwera ani innych dispatcherów. Mechanizm wybieram według miejsca w cyklu żądania, na którym musi działać polityka, i testuję błędy przed oraz po kontrolerze.

## SPR-19. Gdzie użyć `@Async` i co dzieje się z wyjątkiem?

W typowym trybie proxy zewnętrzne wywołanie deleguje pracę do executora. Transakcja wywołującego nie przechodzi do nowego wątku. Wynik Future/CompletableFuture trzeba obserwować; dla void potrzebny jest m.in. AsyncUncaughtExceptionHandler i monitoring.

Self-invocation zwykle omija async proxy tak jak inne advice. Kolejka musi mieć limit. Dla pracy, która ma przetrwać restart, samo Async nie wystarcza — potrzebny jest trwały job lub broker i idempotentny handler.

Wywołanie Async zwraca sterowanie po przyjęciu taska przez executor, nie po jego wykonaniu. Rejection może wystąpić już przy zgłaszaniu, a wyjątek zadania później. Obie ścieżki wymagają obsługi i innych informacji dla klienta.

Jeśli API obiecuje „przyjęto do realizacji”, task w pamięci może zniknąć po restarcie, mimo poprawnego HTTP 202. Dla trwałej odpowiedzialności zapisuję job w DB/brokerze, a Async może jedynie przyspieszać jego lokalne podjęcie. Ustalam też propagację kontekstu i timeout, ponieważ transakcja oraz ThreadLocal wywołującego nie przechodzą automatycznie.

<!-- follow-up:SPR-19 -->
**Sprawdź rozumienie:** Metoda @Async void rzuciła wyjątek po odpowiedzi 202. Czy klient zna wynik i czy zadanie przetrwa restart?

<details>
<summary>Kierunek odpowiedzi</summary>

Nie wynika to z @Async. Błąd wymaga właściwego handlera/monitoringu, a trwałe przyjęcie i status zadania wymagają zapisu w DB lub brokerze. Kolejka executora jest ulotna.

</details>

## SPR-20. Co daje Spring Cache i jakie pułapki ma adnotacyjne cache'owanie?

Spring Cache jest abstrakcją nad providerem. `@Cacheable` może ominąć wywołanie przy trafieniu, `@CachePut` aktualizuje wynik, a `@CacheEvict` usuwa wpis. Klucze, TTL, serializacja i spójność zależą od konfiguracji/providerów.

W trybie proxy self-invocation może pominąć advice. Uwzględniam tenant i parametry wpływające na wynik, a invalidację koordynuję z commitem. Nie cachuję sekretów ani wyników różnych użytkowników pod wspólnym kluczem. Adnotacja nie ustala sama strategii awarii Redis.

Błędny klucz cache może naruszyć izolację danych, choć wynik jest świeży: getOrders(userId) cachowane tylko po nazwie metody zwróci listę innej osoby. Klucz obejmuje parametry semantyczne oraz tenant, a uprawnienia wymagają osobnej oceny.

Przy CacheEvict sprawdzam moment wywołania względem transakcji i kolejność advice. Usunięcie przed rollbackiem i odświeżenie przez stary odczyt tworzą race. Provider powinien mieć TTL, max size i metryki per rodzaj danych. W teście równoległy odczyt podczas aktualizacji daje więcej informacji niż dwa sekwencyjne wywołania metody z adnotacją.

## SPR-21. Dlaczego zadanie zaplanowane uruchamia się pięć razy zamiast raz?

Każda z pięciu instancji aplikacji ma własny scheduler. Cron opisuje czas, a nie globalną wyłączność. Rate limiting ogranicza tempo dopuszczania operacji; nie jest tym samym co scheduling i też nie zapewnia dokładnie jednego wykonania.

Dla jednego logicznego joba używam centralnego schedulera, atomowego claimu w bazie albo lease z fencingiem. Handler i tak powinien być idempotentny po crashu i przejęciu zadania. Ustalam strefę czasową, politykę spóźnień i zachowanie przy nakładaniu wykonań.

Sama rozproszona blokada harmonogramu też nie daje dokładnie jednego efektu. Jeśli proces zatrzyma się na długim GC, lease wygaśnie i druga instancja zacznie pracę, po czym pierwsza wróci. Zasób docelowy musi odrzucić stary fencing token albo rozpoznać ID wykonania.

Przykładowy klucz joba „raport:2026-10-24:tenant-7” identyfikuje logiczny wynik niezależnie od liczby prób. Checkpoint pozwala wznowić duży raport, a unique constraint blokuje podwójną publikację. Przy cron ważna jest także data lokalna i zmiana czasu: ten sam zapis godziny może nie wystąpić albo wystąpić dwa razy.

## SPR-22. Jak skonfigurować dwie bazy i znaczenie primary/secondary?

Definiuję osobne DataSource, EntityManagerFactory, transaction managery i zakresy repozytoriów, używając qualifierów. Primary rozwiązuje wybór beana w DI; nie ustala automatycznie roli primary/replica bazy ani routingu zapytań.

Transakcja jednego lokalnego managera nie obejmie automatycznie drugiej bazy. Dla wspólnej operacji rozważam zmianę granicy danych, sagę/outbox albo świadomie JTA przy zgodnych zasobach. Przy replice uwzględniam lag oraz wymaganie read-your-writes.

Dla dwóch niezależnych baz konfiguracja powinna jawnie przypisać repozytorium do EntityManagerFactory oraz transaction managera. Szkic granicy:

<!-- example:SPR-22-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
@Transactional(transactionManager = "ordersTransactionManager")
public void placeOrder(PlaceOrder command) {
    // operacje dotyczą bazy zamówień
}
~~~

Typ PlaceOrder i ciało są domenowe. Adnotacja nie obejmie automatycznie repozytorium z innej bazy. Przy primary/replica routing odczytu nie może opierać się wyłącznie na readOnly bez ustalenia, czy po wcześniejszym zapisie wymagane jest własne świeże saldo. „Primary bean” i primary DB opisują zupełnie inne role.

## SPR-23. Jak wyjaśniać zastosowanie adnotacji Springa zamiast wyliczać „wszystkie”?

Grupuję je według infrastruktury: stereotypy i Bean/Configuration rejestrują komponenty, Autowired/Qualifier opisują zależności, RequestMapping i warianty mapują HTTP, RequestBody/Valid wiążą i walidują wejście, ExceptionHandler obsługuje błędy, Transactional/Async/Cacheable wymagają odpowiedniego advice. Profile i ConfigurationProperties opisują konfigurację, a PreAuthorize politykę dostępu.

Nie ma krótkiej sensownej odpowiedzi obejmującej wszystkie adnotacje całego ekosystemu. Dla każdej istotnej wskazuję moduł, moment zastosowania, wymagane włączenie mechanizmu i granicę proxy. Sama metadana bez aktywnej infrastruktury może nie wywołać oczekiwanego działania.

Przy każdej adnotacji odpowiadam na cztery praktyczne pytania: kto ją odczytuje, kiedy to robi, jakie warunki muszą działać i co się stanie po ominięciu mechanizmu. Entity jest przetwarzana przez JPA, Valid przez walidację na właściwej granicy, a Transactional przez infrastrukturę transakcji.

Dzięki temu można wyjaśnić pozorną sprzeczność: metoda ma poprawną adnotację, lecz zwykłe new Service() albo this.method() nie przechodzi przez odpowiednie proxy. Test uruchomiony z kontenerem i test czystej klasy potwierdzają różne rzeczy. Kolekcja adnotacji w kodzie nie jest sama w sobie dowodem aktywnego zachowania.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [bean](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/bean/README.md)
- [configuration](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/configuration/README.md)
- [mvc](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/mvc/README.md)
<!-- labs:end -->
