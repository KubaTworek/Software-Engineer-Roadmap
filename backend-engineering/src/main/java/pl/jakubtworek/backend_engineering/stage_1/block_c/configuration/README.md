# configuration

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** configuration.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „configuration” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ValidatedExternalApiPropertiesTest" test`
> - **Role klas:** `ValidatedExternalApiProperties` = `correct`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Konfiguracja Spring Boot — źródła, typy i walidacja



Konfiguracja jest wejściem do programu. Błędny URL, timeout albo limit puli jest
tak samo realnym błędem jak niepoprawny argument metody, dlatego powinien zostać
związany z typem, zwalidowany i odrzucony podczas startu aplikacji.

## Mapa przykładów

| Przykład | Rola |
| --- | --- |
| `AppConfig` | jawna rejestracja beanów i `proxyBeanMethods=false` |
| `ValidatedExternalApiProperties` | immutable binding do `URI` i `Duration`, fail-fast |
| `FeatureFlagsProperties` | grupa powiązanych flag |
| `ValueBasedService` | lokalne użycie pojedynczego `@Value` |
| `DevConfig`, `ProdConfig`, `DefaultConfig` | warunkowa topologia beanów przez profile |
| `CloudConfigClient` | wartość pochodząca z zewnętrznego property source |

## Precedence property sources

Spring rozpatruje wiele źródeł, a źródło o wyższym priorytecie przesłania niższe.
Praktyczny, uproszczony porządek od niższego do wyższego priorytetu to:

1. wartości domyślne zapisane w kodzie,
2. `application.properties` lub `application.yml`,
3. pliki wariantu profilu i importowane config data,
4. zmienne środowiskowe,
5. system properties JVM (`-D...`),
6. argumenty command line (`--...`),
7. właściwości dostarczone przez test.

Pełna lista Spring Boot zawiera dodatkowe źródła, np. `SPRING_APPLICATION_JSON`,
JNDI i servlet init parameters. W diagnozie sprawdzaj `Environment` oraz actuator
`env` z zachowaniem ochrony sekretów; nie zgaduj, który plik „powinien wygrać”.
`ValidatedExternalApiPropertiesTest` dodaje dwa property sources i potwierdza,
że pierwsze źródło w `MutablePropertySources` dostarcza wartość efektywną.

Relaxed binding mapuje przykładowo `APP_EXTERNAL_API_BASE_URL` na
`app.external-api.base-url`. Nazwa zmiennej środowiskowej nie zmienia kontraktu
konfiguracji — jest tylko inną reprezentacją tego samego klucza.

## `@Value` czy `@ConfigurationProperties`

| Sytuacja | Wybór |
| --- | --- |
| jedna lokalna, nieskomplikowana wartość | `@Value` może wystarczyć |
| grupa ustawień jednego klienta/modułu | `@ConfigurationProperties` |
| potrzebne `Duration`, `URI`, lista lub zagnieżdżona struktura | `@ConfigurationProperties` |
| potrzebna walidacja i metadata dla IDE | `@ConfigurationProperties` |
| wyrażenie SpEL | `@Value`, ale najpierw oceń, czy logika należy do konfiguracji |

`ValidatedExternalApiProperties` używa typów `URI` i `Duration`. Konstruktor
odrzuca schemat inny niż HTTP(S) oraz timeout poza zakresem `100ms..30s`, a Bean
Validation pilnuje wartości wymaganych. Niepoprawna konfiguracja zatrzymuje start
zamiast ujawniać się przy pierwszym ruchu produkcyjnym.

Sekret może być powiązany z properties, ale nie powinien mieć bezpiecznej
wartości domyślnej zapisanej w Git. Powinien pochodzić z secret managera lub
kontrolowanego środowiska, nie być logowany i mieć procedurę rotacji.

## Profile nie są systemem konfiguracji biznesowej

Profil zmienia zestaw beanów. Jest właściwy, gdy środowiska naprawdę potrzebują
innej implementacji, np. emulatora zamiast zewnętrznego adaptera. Nie używaj
profili do każdej różnicy wartości ani jako zamiennika feature flags.

Ryzyka nadmiaru profili:

- kombinacje `dev,cloud,region-a,feature-x` tworzą trudną do przewidzenia topologię,
- test może uruchomić inny graf beanów niż produkcja,
- negacje typu `@Profile("!prod")` mogą przypadkowo aktywować kod demonstracyjny,
- sekret lub URL nadal powinien być wartością, nie osobną klasą konfiguracyjną.

Profil `default` jest aktywny tylko wtedy, gdy nie wskazano żadnego profilu. Nie
jest profilem bazowym dokładanym do każdego środowiska.

## Java Config i autokonfiguracja

`@Configuration(proxyBeanMethods=false)` unika proxy klasy konfiguracyjnej. Jest
poprawne, gdy metody `@Bean` przyjmują zależności jako parametry i nie wywołują
się wzajemnie. Przy `proxyBeanMethods=true` bezpośrednie wywołanie innej metody
`@Bean` jest przechwytywane, aby zachować semantykę singletona; koszt i ukryte
powiązanie zwykle nie są potrzebne.

Autokonfiguracja działa warunkowo na podstawie classpath, properties i istniejących
beanów. Gdy wynik zaskakuje, sprawdź condition evaluation report. Ręczne dodanie
beana może celowo wyłączyć konfigurację oznaczoną `@ConditionalOnMissingBean`.

## Dynamiczne odświeżanie

`@RefreshScope` rekonstruuje wybrane beany, ale nie daje atomowej zmiany całej
aplikacji. Dwa beany mogą przez chwilę widzieć różne wersje ustawień, istniejące
requesty mogą używać starej instancji, a zmiana rozmiaru puli czy schematu danych
może wymagać kontrolowanego restartu. Każdą dynamiczną właściwość trzeba ocenić
pod kątem spójności i możliwości rollbacku.

## Jak testować konfigurację

`ApplicationContextRunner` uruchamia mały kontekst tylko z badaną konfiguracją.
Test powinien potwierdzić:

- poprawne mapowanie do typów domenowych,
- fail-fast dla brakującej lub niepoprawnej wartości,
- wartość efektywną przy kilku property sources,
- obecność lub brak warunkowego beana.

Pełny `@SpringBootTest` jest potrzebny dopiero, gdy sprawdzamy współdziałanie wielu
autokonfiguracji, a nie sam rekord properties.
