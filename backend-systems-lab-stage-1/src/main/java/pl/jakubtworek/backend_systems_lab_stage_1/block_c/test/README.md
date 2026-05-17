# Testowanie w Spring Boot

Spring Boot posiada bardzo rozbudowany ekosystem testowy, którego głównym celem jest umożliwienie testowania aplikacji na różnych poziomach izolacji. Framework wspiera zarówno szybkie testy jednostkowe bez uruchamiania Springa, jak i pełne testy integracyjne uruchamiające cały kontekst aplikacji wraz z bazą danych, warstwą HTTP czy bezpieczeństwem. Kluczową ideą Spring Boot Testing jest możliwość ładowania tylko tych fragmentów aplikacji, które są potrzebne do konkretnego testu. Dzięki temu testy pozostają szybkie, a jednocześnie dobrze odwzorowują rzeczywiste zachowanie aplikacji.

Najbardziej podstawowym poziomem są testy jednostkowe. Są to testy całkowicie niezależne od Springa, w których obiekty tworzone są ręcznie, a zależności mockowane przy pomocy Mockito. Testy jednostkowe są bardzo szybkie, ponieważ nie uruchamiają kontekstu Springa ani serwera aplikacyjnego. Najczęściej testuje się w ten sposób logikę biznesową serwisów. Repozytoria czy klienci zewnętrzni są zastępowani mockami, dzięki czemu test sprawdza wyłącznie zachowanie pojedynczej klasy.

Kolejnym poziomem są tzw. slice tests, czyli testy fragmentów aplikacji. Spring Boot pozwala ładować tylko wybrane części kontekstu aplikacji, np. wyłącznie warstwę MVC albo wyłącznie warstwę JPA. Dzięki temu testy są znacznie szybsze niż pełne testy integracyjne, ale jednocześnie pozwalają testować realną konfigurację Springa.

Najpopularniejszym slice testem jest `@WebMvcTest`. Adnotacja ta ładuje wyłącznie komponenty związane ze Spring MVC, takie jak kontrolery, konfiguracja serializacji JSON, walidacja czy `ControllerAdvice`. Nie są ładowane serwisy, repozytoria ani pełna infrastruktura aplikacji. Dzięki temu można bardzo szybko testować endpointy REST bez uruchamiania całego Spring Boota.

`@WebMvcTest` bardzo często współpracuje z `MockMvc`. `MockMvc` pozwala wykonywać żądania HTTP w pamięci, bez uruchamiania prawdziwego serwera HTTP. Można weryfikować statusy HTTP, nagłówki, body odpowiedzi czy serializację JSON. Jest to podstawowe narzędzie do testowania warstwy REST w Spring Boot.

Ponieważ `@WebMvcTest` nie ładuje warstwy serwisowej, zależności kontrolera muszą zostać zamockowane. W tym celu używa się `@MockBean`. `@MockBean` tworzy mock Mockito i jednocześnie rejestruje go jako bean w kontekście Springa, zastępując prawdziwą implementację. Dzięki temu kontroler działa w środowisku Springa, ale jego zależności pozostają w pełni kontrolowane przez test.

Drugim bardzo ważnym slice testem jest `@DataJpaTest`. Ta adnotacja ładuje wyłącznie komponenty związane z JPA i Hibernate. Spring konfiguruje encje, repozytoria, `EntityManager` oraz najczęściej wbudowaną bazę danych H2. Nie są ładowane kontrolery ani warstwa webowa. Dzięki temu można testować zapytania repozytoriów, mapowanie encji czy działanie Hibernate bez uruchamiania całej aplikacji.

`@DataJpaTest` uruchamia każdy test w transakcji i automatycznie wykonuje rollback po zakończeniu testu. Dzięki temu baza danych pozostaje czysta, a testy są od siebie niezależne. Jest to bardzo wygodne przy testowaniu repozytoriów i relacji JPA.

Najbardziej rozbudowanym typem testów są testy integracyjne wykorzystujące `@SpringBootTest`. Adnotacja ta uruchamia pełny kontekst aplikacji, dokładnie tak jak podczas normalnego działania systemu. Ładowane są wszystkie beany, konfiguracje, bezpieczeństwo, kontrolery, serwisy oraz repozytoria. Testy takie są najwolniejsze, ale najlepiej odwzorowują rzeczywiste działanie aplikacji.

`@SpringBootTest` może działać w różnych trybach web environment. W trybie `RANDOM_PORT` Spring uruchamia prawdziwy embedded server na losowym porcie. Dzięki temu możliwe jest wykonywanie prawdziwych żądań HTTP przy użyciu `TestRestTemplate` lub `WebTestClient`. Tego typu testy sprawdzają cały pipeline aplikacji, łącznie z routingiem, serializacją, bezpieczeństwem oraz integracją z bazą danych.

W testach integracyjnych często stosuje się `@Transactional`. Oznacza to, że każda metoda testowa wykonywana jest wewnątrz transakcji, która po zakończeniu testu zostaje automatycznie wycofana. Pozwala to utrzymać czystą bazę danych bez konieczności ręcznego usuwania danych testowych.

Spring Boot wspiera również inne wyspecjalizowane slice testy, np. `@RestClientTest` dla klientów REST czy `@JsonTest` do testowania serializacji JSON. Idea pozostaje taka sama — ładować wyłącznie tę część aplikacji, która jest potrzebna do konkretnego scenariusza testowego.

Bardzo ważną zasadą podczas projektowania testów jest unikanie niepotrzebnego uruchamiania pełnego kontekstu aplikacji. `@SpringBootTest` jest kosztowny czasowo, dlatego powinien być używany głównie tam, gdzie rzeczywiście potrzebna jest pełna integracja komponentów. W większości przypadków lepszym rozwiązaniem są węższe slice testy lub testy jednostkowe.

Testowanie w Springu jest silnie powiązane z mechanizmem dependency injection. Dzięki kontenerowi Spring można łatwo podmieniać zależności, mockować komponenty oraz kontrolować konfigurację środowiska testowego. Framework dostarcza również wiele mechanizmów wspierających testowanie bezpieczeństwa, transakcji czy komunikacji HTTP.

Dobrze zaprojektowana strategia testowania zwykle składa się z wielu poziomów. Testy jednostkowe pokrywają logikę biznesową, slice testy sprawdzają integrację wybranych warstw, a testy integracyjne weryfikują poprawność działania całej aplikacji. Takie podejście pozwala zachować równowagę pomiędzy szybkością wykonywania testów a poziomem pewności, że system działa poprawnie.