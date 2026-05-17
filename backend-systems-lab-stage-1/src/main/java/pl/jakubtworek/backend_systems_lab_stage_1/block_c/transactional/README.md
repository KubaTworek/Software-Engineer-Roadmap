# Konfiguracja i profile w Spring Boot

Konfiguracja aplikacji jest jednym z najważniejszych elementów architektury Spring Boot. Framework został zaprojektowany w taki sposób, aby oddzielić konfigurację od kodu biznesowego i umożliwić łatwe zarządzanie ustawieniami dla różnych środowisk. W nowoczesnych aplikacjach Spring praktycznie całkowicie odchodzi się od konfiguracji XML na rzecz Java Config, autokonfiguracji oraz externalized configuration.

Historycznie Spring opierał się głównie na plikach XML definiujących beany oraz zależności pomiędzy nimi. Współcześnie standardem jest Java Config, czyli klasy oznaczone `@Configuration`, zawierające metody `@Bean`. Dzięki temu konfiguracja staje się typowana, łatwiejsza do refaktoryzacji oraz bardziej zintegrowana z IDE. W praktyce jednak Spring Boot znacząco ogranicza potrzebę ręcznego definiowania beanów, ponieważ większość infrastruktury tworzona jest automatycznie przez autokonfigurację starterów.

Autokonfiguracja jest jednym z fundamentów Spring Boot. Framework analizuje zależności znajdujące się w classpath oraz dostępne właściwości konfiguracyjne i na tej podstawie automatycznie tworzy odpowiednie beany. Dzięki temu dodanie startera, np. `spring-boot-starter-data-jpa`, wystarcza do skonfigurowania Hibernate, DataSource i transakcji bez konieczności ręcznego definiowania całej infrastruktury.

Spring Boot automatycznie skanuje komponenty oznaczone adnotacjami takimi jak `@Component`, `@Service`, `@Repository` czy `@Controller`. Mechanizm ten nazywa się component scanning i jest domyślnie aktywowany przez `@SpringBootApplication`. Dzięki temu większość beanów tworzona jest automatycznie bez potrzeby rejestracji w konfiguracji.

Bardzo istotnym mechanizmem są profile środowiskowe. Spring Profiles pozwalają definiować konfigurację aktywną wyłącznie dla określonego środowiska, np. development, test lub production. Klasy oznaczone `@Profile("dev")` zostaną załadowane tylko wtedy, gdy aktywny jest profil `dev`. Dzięki temu można utrzymywać osobne konfiguracje dla różnych środowisk bez modyfikowania kodu aplikacji.

Profile aktywuje się najczęściej poprzez właściwość `spring.profiles.active`, zmienne środowiskowe lub argumenty JVM. Jeśli profil nie zostanie ustawiony, Spring użyje profilu `default`. Mechanizm profili jest bardzo ważny w systemach enterprise, ponieważ pozwala oddzielić konfigurację developerską od produkcyjnej, np. różne bazy danych, endpointy API czy feature flagi.

Spring Boot wspiera zarówno pliki `.properties`, jak i `.yml`. Oba formaty są równoważne funkcjonalnie, jednak YAML jest często preferowany ze względu na większą czytelność przy bardziej rozbudowanej konfiguracji. Spring automatycznie ładuje pliki takie jak `application.yml`, `application-dev.yml` czy `application-prod.yml` w zależności od aktywnego profilu. Konfiguracje profili są nadpisywane warstwowo, co pozwala utrzymywać wspólną konfigurację bazową oraz różnice specyficzne dla środowiska.

Do odczytywania pojedynczych wartości konfiguracyjnych można używać `@Value`. Mechanizm ten sprawdza się przy prostych przypadkach, np. odczycie jednej właściwości. W większych projektach znacznie lepszym rozwiązaniem jest jednak `@ConfigurationProperties`. Pozwala ono mapować grupy właściwości na typowane klasy Java. Dzięki temu konfiguracja staje się bardziej przejrzysta, łatwiejsza do walidacji oraz bardziej odporna na błędy.

`@ConfigurationProperties` jest szczególnie przydatne przy konfiguracji klientów zewnętrznych systemów, feature flag czy połączeń sieciowych. Zamiast wielu pojedynczych `@Value`, aplikacja posiada jedną spójną klasę reprezentującą konfigurację danego modułu. Spring Boot automatycznie mapuje właściwości z plików konfiguracyjnych na pola klasy.

Jednym z najważniejszych założeń Spring Boot jest externalized configuration, czyli przechowywanie konfiguracji poza kodem aplikacji. W praktyce oznacza to możliwość dostarczania konfiguracji z wielu źródeł: plików `application.yml`, zmiennych środowiskowych, parametrów JVM, argumentów uruchomieniowych czy zewnętrznych serwerów konfiguracji. Dzięki temu ten sam artefakt aplikacji może działać w różnych środowiskach jedynie poprzez zmianę konfiguracji.

Spring Boot bardzo dobrze integruje się ze zmiennymi środowiskowymi. W środowiskach kontenerowych i Kubernetes jest to szczególnie istotne, ponieważ konfiguracja często przekazywana jest właśnie przez env variables. Spring automatycznie mapuje zmienne takie jak `SPRING_DATASOURCE_URL` na odpowiednie właściwości konfiguracyjne aplikacji.

W bardziej rozbudowanych systemach mikroserwisowych często wykorzystuje się Spring Cloud Config. Jest to centralny serwer konfiguracji przechowujący ustawienia aplikacji np. w repozytorium Git. Klient podczas uruchamiania pobiera konfigurację z Config Server przez HTTP. Dzięki temu wszystkie mikroserwisy mogą korzystać ze wspólnego źródła konfiguracji.

Spring Cloud Config wspiera również dynamiczne odświeżanie konfiguracji przy użyciu `@RefreshScope`. Po wywołaniu endpointu `/actuator/refresh` wybrane beany są rekonstruowane z nowymi wartościami konfiguracyjnymi bez restartu aplikacji. Mechanizm ten jest szczególnie przydatny dla feature flag oraz konfiguracji zewnętrznych integracji.

Dobrą praktyką jest minimalizowanie liczby hardcoded values w kodzie aplikacji. Parametry takie jak URL-e, timeouty, klucze API, feature flagi czy konfiguracja baz danych powinny znajdować się w zewnętrznej konfiguracji. Dzięki temu aplikacja pozostaje elastyczna i łatwiejsza do wdrażania w różnych środowiskach.

Konfiguracja w Spring Boot jest bardzo silnie związana z filozofią convention over configuration. Framework dostarcza sensowne wartości domyślne i autokonfigurację, ale jednocześnie pozwala łatwo nadpisywać ustawienia w razie potrzeby. Zrozumienie mechanizmów profilów, externalized configuration oraz autokonfiguracji jest kluczowe przy budowaniu większych aplikacji enterprise oraz systemów mikroserwisowych.