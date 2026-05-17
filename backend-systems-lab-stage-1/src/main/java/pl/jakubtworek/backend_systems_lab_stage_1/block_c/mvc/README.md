# Spring MVC Pipeline

Spring MVC jest oparty o wzorzec Front Controller, którego centralnym elementem jest `DispatcherServlet`. Każde żądanie HTTP trafiające do aplikacji przechodzi przez ten komponent, a następnie wykonywany jest cały pipeline odpowiedzialny za odnalezienie odpowiedniego kontrolera, przygotowanie argumentów metody, walidację danych, wykonanie logiki biznesowej oraz przygotowanie odpowiedzi HTTP.

Proces rozpoczyna się w momencie otrzymania żądania HTTP przez serwer servletów, np. Tomcat. Żądanie trafia najpierw do filtrów servletowych (`Filter`), które działają jeszcze przed Spring MVC. Filtry są częścią standardu Servlet API i służą do niskopoziomowego przetwarzania requestów, np. logowania, obsługi CORS, dodawania correlation id czy preprocessing bezpieczeństwa. Dopiero po przejściu przez filtry żądanie trafia do `DispatcherServlet`.

`DispatcherServlet` pełni rolę centralnego koordynatora całego Spring MVC. Jego pierwszym zadaniem jest odnalezienie odpowiedniego handlera dla żądania HTTP. Wykorzystywany jest do tego mechanizm `HandlerMapping`, który analizuje ścieżkę URL, metodę HTTP oraz adnotacje takie jak `@RequestMapping`, `@GetMapping`, `@PostMapping` itd. Na tej podstawie Spring wybiera odpowiednią metodę kontrolera.

Po odnalezieniu handlera Spring korzysta z `HandlerAdapter`, którego zadaniem jest faktyczne wywołanie metody kontrolera. Zanim jednak metoda zostanie wykonana, Spring musi przygotować jej argumenty. Odpowiadają za to `HandlerMethodArgumentResolver`.

Mechanizm argument resolverów jest jednym z najważniejszych elementów Spring MVC. Każdy resolver odpowiada za konkretny typ argumentu lub konkretną adnotację. `PathVariableMethodArgumentResolver` odczytuje wartości z URL, `RequestParamMethodArgumentResolver` pobiera parametry query string, natomiast `RequestResponseBodyMethodProcessor` odpowiada za obsługę `@RequestBody`. Jeśli metoda kontrolera przyjmuje obiekt DTO oznaczony `@RequestBody`, Spring używa `HttpMessageConverter` do deserializacji JSON-a na obiekt Java.

Domyślnie Spring Boot wykorzystuje `MappingJackson2HttpMessageConverter`, który używa biblioteki Jackson do konwersji JSON ↔ Java Object. Dla zwykłego tekstu wykorzystywany jest np. `StringHttpMessageConverter`. Mechanizm converterów jest rozszerzalny — można definiować własne formaty danych i własne konwertery.

Po utworzeniu argumentów Spring wykonuje walidację danych. Jeśli parametr oznaczony jest `@Valid`, uruchamiany jest Bean Validation. Reguły walidacji definiowane są za pomocą adnotacji takich jak `@NotBlank`, `@Email`, `@Min` czy `@Size`. Jeśli walidacja nie powiedzie się, Spring rzuca `MethodArgumentNotValidException`, które może zostać przechwycone globalnie przez `@RestControllerAdvice`.

Po przygotowaniu argumentów i zakończeniu walidacji wykonywana jest metoda kontrolera. Kontroler powinien być cienką warstwą odpowiedzialną jedynie za obsługę HTTP oraz delegowanie logiki do serwisów biznesowych. Po wykonaniu metody Spring przechodzi do etapu tworzenia odpowiedzi HTTP.

W przypadku `@RestController` zwracany obiekt nie jest traktowany jako nazwa widoku, lecz jako body odpowiedzi HTTP. Spring ponownie wykorzystuje `HttpMessageConverter`, tym razem do serializacji obiektu Java do JSON. Ostatecznie wygenerowana odpowiedź trafia z powrotem do klienta.

W klasycznym Spring MVC opartym o `@Controller` sytuacja wygląda inaczej. Metoda kontrolera zwykle zwraca logical view name, np. `"home"`. Wtedy `DispatcherServlet` używa `ViewResolver`, aby zamapować nazwę widoku na konkretny szablon HTML, np. Thymeleaf lub JSP. Następnie generowany jest widok HTML zwracany do przeglądarki.

Ważnym elementem pipeline są również `HandlerInterceptor`. Interceptory działają już wewnątrz Spring MVC, po wybraniu handlera przez `DispatcherServlet`. Mogą wykonywać logikę przed wywołaniem kontrolera (`preHandle`), po wykonaniu kontrolera (`postHandle`) oraz po zakończeniu całego requestu (`afterCompletion`). W praktyce używa się ich do logowania, monitoringu, audytu lub dodatkowej autoryzacji.

Spring MVC pozwala również definiować własne `HandlerMethodArgumentResolver`. Dzięki temu można automatycznie wstrzykiwać własne obiekty do metod kontrolera. Typowym przykładem jest wstrzykiwanie aktualnie zalogowanego użytkownika. Resolver pobiera dane np. z `SecurityContextHolder`, a następnie przekazuje gotowy obiekt jako parametr metody kontrolera. Dzięki temu kod kontrolera staje się znacznie czystszy.

Bardzo ważnym elementem pipeline jest także globalna obsługa wyjątków przez `@ControllerAdvice` lub `@RestControllerAdvice`. Jeśli podczas wykonywania requestu zostanie rzucony wyjątek, Spring może przechwycić go globalnie i zamapować na odpowiedni kod HTTP oraz ustandaryzowaną odpowiedź błędu. Dzięki temu aplikacja REST zachowuje spójność odpowiedzi niezależnie od miejsca wystąpienia błędu.

Spring MVC jest bardzo modularny i rozszerzalny. Framework pozwala dodawać własne filtry, interceptory, argument resolvery, message convertery czy bindery danych. Dzięki temu można dostosować pipeline HTTP praktycznie do dowolnych wymagań aplikacji.

Cały mechanizm Spring MVC opiera się na współpracy wielu wyspecjalizowanych komponentów, które razem tworzą kompletny pipeline obsługi requestów HTTP. Zrozumienie tego przepływu jest kluczowe podczas debugowania problemów związanych z walidacją, serializacją JSON, bezpieczeństwem, routingiem czy obsługą wyjątków w aplikacjach Spring Boot.