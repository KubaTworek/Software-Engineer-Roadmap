# API i bezpieczeństwo

[Spis rozdziałów](README.md)

<!-- question-toc:start -->
## Spis pytań

- [API-01. Jakie są zasady REST i jak projektować zasoby?](#api-01-jakie-są-zasady-rest-i-jak-projektować-zasoby)
- [API-02. GET czy POST do złożonego wyszukiwania? Jeden endpoint czy wiele?](#api-02-get-czy-post-do-złożonego-wyszukiwania-jeden-endpoint-czy-wiele)
- [API-03. Czym różnią się PUT i PATCH?](#api-03-czym-różnią-się-put-i-patch)
- [API-04. `@RequestParam` czy `@PathVariable` i jak obsługiwać filtry?](#api-04-requestparam-czy-pathvariable-i-jak-obsługiwać-filtry)
- [API-05. Kiedy używać 200, 201, 202, 204, 400, 401, 403, 404, 409, 422, 429 i 503?](#api-05-kiedy-używać-200-201-202-204-400-401-403-404-409-422-429-i-503)
- [API-06. Jak rozwijać API bez psucia istniejących klientów?](#api-06-jak-rozwijać-api-bez-psucia-istniejących-klientów)
- [API-07. Jak zapobiec duplikatom płatności/zamówień po podwójnym kliknięciu lub timeoutcie?](#api-07-jak-zapobiec-duplikatom-płatnościzamówień-po-podwójnym-kliknięciu-lub-timeoutcie)
- [API-08. Authentication czy authorization? Jak działa Basic Authentication?](#api-08-authentication-czy-authorization-jak-działa-basic-authentication)
- [API-09. Czym różnią się OAuth 2.0, OpenID Connect i JWT?](#api-09-czym-różnią-się-oauth-20-openid-connect-i-jwt)
- [API-10. Jak bezpiecznie weryfikować JWT i co przy rotacji kluczy?](#api-10-jak-bezpiecznie-weryfikować-jwt-i-co-przy-rotacji-kluczy)
- [API-11. Stateless czy stateful authentication? Co z refresh tokenem?](#api-11-stateless-czy-stateful-authentication-co-z-refresh-tokenem)
- [API-12. Jak działa SecurityFilterChain w Spring Security?](#api-12-jak-działa-securityfilterchain-w-spring-security)
- [API-13. Czym jest CORS i czym różni się od CSRF oraz TLS?](#api-13-czym-jest-cors-i-czym-różni-się-od-csrf-oraz-tls)
- [API-14. Jak zabezpieczyć komunikację między mikroserwisami?](#api-14-jak-zabezpieczyć-komunikację-między-mikroserwisami)
- [API-15. Jak ograniczyć dostęp do usługi do 10 tys. użytkowników?](#api-15-jak-ograniczyć-dostęp-do-usługi-do-10-tys-użytkowników)
- [API-16. HttpClient, RestTemplate, RestClient, WebClient czy OpenFeign?](#api-16-httpclient-resttemplate-restclient-webclient-czy-openfeign)
- [API-17. REST, gRPC, WebSocket czy komunikacja przez broker?](#api-17-rest-grpc-websocket-czy-komunikacja-przez-broker)
- [API-18. Jak bezpiecznie przechowywać użytkowników i hasła w AWS?](#api-18-jak-bezpiecznie-przechowywać-użytkowników-i-hasła-w-aws)
<!-- question-toc:end -->

## API-01. Jakie są zasady REST i jak projektować zasoby?

REST zakłada m.in. interfejs jednolity, bezstanową komunikację, rozdzielenie klienta i serwera oraz świadome cache'owanie. HTTP API często wykorzystuje tylko część tych zasad. Modeluję zasoby i operacje zgodnie z semantyką metod, zamiast tworzyć zestaw dowolnych czasowników w URL.

Bezstanowość żądania nie oznacza braku trwałej bazy. Kontrakt obejmuje reprezentację, błędy, autoryzację, wersjonowanie i współbieżne aktualizacje. JSON po HTTP sam nie przesądza, że system spełnia pełny styl REST.

Dla zamówienia GET /orders/42 pobiera reprezentację, POST /orders tworzy nowy zasób, a osobny kontrakt operacji może opisywać anulowanie. Ważniejsze od czystej nazwy ścieżki jest to, czy klient rozumie skutki, retry i statusy. Prawdziwy styl REST obejmuje także sterowanie przez reprezentacje/hipermedia; nie każde JSON API je realizuje.

Kontrakt zasobu powinien zawierać wersję lub ETag do współbieżnej edycji. Stateless oznacza, że żądanie niesie potrzebny kontekst, nie że serwer nie ma trwałego stanu. Cacheability trzeba określić nagłówkami i polityką danych prywatnych.

## API-02. GET czy POST do złożonego wyszukiwania? Jeden endpoint czy wiele?

GET pasuje do bezpiecznego odczytu z filtrem w query string i dobrze współpracuje z cache. POST może przyjąć rozbudowane drzewo filtrów w body, gdy adres byłby niepraktyczny. Sam odczyt przez POST nie otrzymuje automatycznie semantyki bezpiecznego i idempotentnego GET.

Zwykle tworzę jeden endpoint wyszukiwania z walidowanymi filtrami, sortowaniem i limitem strony, zamiast wszystkich kombinacji osobno. Ograniczam koszt zapytania i pola sortowania. Body GET nie ma powszechnie uzgodnionej semantyki i bywa odrzucane.

Złożony filtr można przyjąć np. jako odrębne żądanie search:

<!-- example:API-02-1 kind:http-contract -->
**Kontekst:** Przykład kontraktu HTTP; wymaga odpowiadającego API, nie jest automatycznie wysyłanym żądaniem.

~~~http
POST /employees/search
Content-Type: application/json

{"departmentIds":[10,20],"minExperienceYears":5,"sort":["name","id"],"size":25}
~~~

To szkic kontraktu; nadal trzeba ograniczyć liczbę filtrów, zagnieżdżenie, sortowane pola i koszt wykonania. Dane w URL mogą trafiać do logów proxy, lecz body też nie jest automatycznie tajne. Dla powtarzalnego kosztownego wyszukiwania można utworzyć zasób zadania/raportu i pobierać wynik przez GET. Wybór POST nie usprawiedliwia ukrytych skutków w odczytowej operacji.

## API-03. Czym różnią się PUT i PATCH?

PUT tworzy lub zastępuje reprezentację wskazanego zasobu i jest idempotentny w swojej semantyce. PATCH opisuje częściową zmianę w określonym formacie; idempotencja zależy od operacji. „Ustaw status X” różni się od „zwiększ licznik o 1”.

Przy edycji współbieżnej używam wersji/ETag i If-Match. Ustalam znaczenie pominiętego pola oraz jawnego null. Idempotentny stan końcowy nie oznacza identycznego statusu HTTP za każdym razem ani prawa do powtarzania niekontrolowanych efektów ubocznych.

Warunkowa aktualizacja pomaga uniknąć lost update:

<!-- example:API-03-1 kind:http-contract -->
**Kontekst:** Przykład kontraktu HTTP; wymaga odpowiadającego API, nie jest automatycznie wysyłanym żądaniem.

~~~http
PUT /profiles/42
If-Match: "version-7"
Content-Type: application/json

{"displayName":"Ala","timezone":"Europe/Warsaw"}
~~~

Serwer sprawdza precondition atomowo z zapisem; niezgodna wersja może dać 412. Konflikt domenowy to inna przyczyna niż niespełniony warunek HTTP. Dla PATCH trzeba ustalić format: merge patch i JSON Patch mają inne zachowanie dla null, tablic i kolejnych operacji. Testuję dwa równoczesne zapisy na tym samym ETag, aby upewnić się, że jedna zmiana nie znika mimo dwóch sukcesów.

## API-04. `@RequestParam` czy `@PathVariable` i jak obsługiwać filtry?

PathVariable reprezentuje część ścieżki, zwykle identyfikującą zasób, np. zamówienie. RequestParam pasuje do filtrów, sortowania i paginacji. Oba wejścia są niezaufane: wymagają konwersji, walidacji i kontroli uprawnień.

Sortowanie mapuję na dozwolone pola, a limit strony ograniczam serwerowo. Posiadanie ID w ścieżce nie uprawnia do odczytu obiektu. Tenant ustalam z zaufanego kontekstu, a nie z dowolnego parametru klienta.

Parametr ścieżki bywa obowiązkowym identyfikatorem, a query opisuje opcjonalny wybór:

<!-- example:API-04-1 kind:http-contract -->
**Kontekst:** Przykład kontraktu HTTP; wymaga odpowiadającego API, nie jest automatycznie wysyłanym żądaniem.

~~~http
GET /tenants/7/orders?status=PENDING&size=20&sort=createdAt,desc
~~~

Serwer nie może uznać tenant_id=7 za dowód uprawnienia. To tylko żądanie wyboru, weryfikowane wobec tożsamości i polityki. Dla filtrów rozstrzygam brak parametru, pustą listę i jawny null, bo mogą oznaczać „wszystkie”, „żaden” albo błąd. Limity page size i zakresu dat chronią przed niezamierzonym kosztownym skanem, a stabilny tie-breaker zapewnia przewidywalny porządek.

## API-05. Kiedy używać 200, 201, 202, 204, 400, 401, 403, 404, 409, 422, 429 i 503?

200 oznacza udaną odpowiedź, 201 utworzenie zasobu, 202 przyjęcie do późniejszego przetwarzania, 204 sukces bez body. 400 dotyczy niepoprawnego żądania, 401 braku poprawnego uwierzytelnienia, 403 odmowy uprawnień, 404 braku zasobu, 409 konfliktu stanu, a 422 semantycznie nieprzetwarzalnej treści.

429 komunikuje limit klienta, 503 niedostępność/przeciążenie usługi. Dla 202 zwracam identyfikator i sposób sprawdzenia wyniku. Status dopasowuję do ustalonego kontraktu; błąd DB nie powinien odsłaniać technicznych szczegółów w body.

Status jest częścią protokołu działania klienta. Po 201 klient powinien znać utworzony zasób, zwykle przez Location; po 202 musi mieć sposób sprawdzenia wyniku, bo operacja może później upaść. 401 może uruchamiać odświeżenie tożsamości, ale 403 zwykle wymaga zmiany uprawnień.

<!-- example:API-05-1 kind:http-contract -->
**Kontekst:** Przykład kontraktu HTTP; wymaga odpowiadającego API, nie jest automatycznie wysyłanym żądaniem.

~~~http
HTTP/1.1 202 Accepted
Location: /operations/op-123
Content-Type: application/json

{"id":"op-123","status":"PENDING"}
~~~

To szkic odpowiedzi. Dla 429/503 Retry-After pomaga sterować ponowieniem, lecz klient nadal potrzebuje limitu prób i idempotencji operacji. Nie mapuję każdego konfliktu walidacji na ten sam status bez spójnej polityki.

## API-06. Jak rozwijać API bez psucia istniejących klientów?

Preferuję zmiany addytywne, zachowuję znaczenie i typ pól oraz testuję zachowanie rzeczywistych konsumentów. Nowa wartość enum może zepsuć klienta z wyczerpującym mapowaniem. Wersja endpointu pomaga przy zmianie niekompatybilnej, ale wymaga migracji i okresu utrzymania obu kontraktów.

Nie zmieniam niejawnie jednostki, waluty, sortowania ani interpretacji null. Stosuję consumer contracts i obserwuję użycie przed wycofaniem. Rollback musi być zgodny również z danymi zapisanymi przez nową wersję.

Zmiana pola amount z liczby jednostek minimalnych na liczbę dziesiętną może nie zmienić nazwy JSON, a jednak kompletnie złamać kontrakt. Podobnie dodanie obowiązkowego pola wejściowego albo nowego enum na wyjściu może zepsuć klientów mimo pozornie „addytywnej” zmiany.

Kompatybilność badam z perspektywy faktycznych konsumentów i ich tolerancji. Nową reprezentację wprowadzam równolegle lub przez jawny mechanizm wersjonowania, mierzę użycie i planuję wycofanie. Consumer contracts uzupełniam przykładami semantycznymi: jednostki, strefy czasu, brak danych, sortowanie i wartości graniczne. Schema validation nie wychwyci każdej zmiany znaczenia.

## API-07. Jak zapobiec duplikatom płatności/zamówień po podwójnym kliknięciu lub timeoutcie?

Klient przekazuje idempotency key identyfikujący zamiar. Serwer atomowo zapisuje klucz w zakresie klienta/tenanta, fingerprint żądania i wynik lub stan w toku, z unikalnym constraintem. Ten sam klucz z inną treścią jest konfliktem.

Retry zwraca istniejący wynik lub status; zewnętrzny operator też musi respektować tę tożsamość. Po timeoutcie wynik może być nieznany, więc sprawdzam status i rekoncyliuję. TTL klucza odpowiada oknu możliwych powtórzeń; blokada przycisku i lokalna mapa nie wystarczą.

Wyścig dwóch pierwszych prób rozstrzyga constraint, nie sekwencyjny check w aplikacji. Rekord idempotencji ma np. scope, key, fingerprint, status i result reference. Zapis klucza oraz lokalny efekt powinny należeć do tej samej granicy albo jawnego trwałego protokołu.

<!-- example:API-07-1 kind:sql-template -->
**Kontekst:** Szablon SQL — wymaga bazy, schematu i podstawienia parametrów; skrypt go nie wykonuje.

~~~sql
CREATE UNIQUE INDEX uq_idempotency
ON idempotency_request (tenant_id, operation_type, request_key);
~~~

Przy tym samym kluczu i innej kwocie odmawiam, zamiast zwrócić sukces dawnej operacji. Gdy stan jest PROCESSING, kontrakt opisuje oczekiwanie/status. Skończony TTL nie może być krótszy od realnego okna retry i rozliczeń zewnętrznych.

<!-- follow-up:API-07 -->
**Sprawdź rozumienie:** Dwa requesty mają ten sam klucz, ale różne kwoty. Czy zwrócenie starego sukcesu jest poprawne?

<details>
<summary>Kierunek odpowiedzi</summary>

To konflikt tożsamości zamiaru. Serwer porównuje fingerprint w zakresie klienta/operacji i odmawia sprzecznej treści. Sam unique key bez walidacji treści nie definiuje pełnej idempotencji.

</details>

## API-08. Authentication czy authorization? Jak działa Basic Authentication?

Authentication ustala tożsamość, authorization sprawdza prawo do konkretnego działania na danych. Basic przesyła login i hasło zakodowane base64, co nie jest szyfrowaniem; wymaga TLS i przemyślanego użycia. Każde żądanie może ponownie przenosić credential.

Rola to często za mało: sprawdzam właściciela, tenant i stan zasobu. Stosuję politykę odmowy domyślnej i testy dostępu do cudzych ID. Uwierzytelniony użytkownik nadal może nie mieć prawa do odczytu ani zmiany danego rekordu.

Basic umieszcza credential w nagłówku Authorization. Base64 można odwrócić bez klucza, więc odszyfrowanie transmisji lub zapis nagłówka w logu ujawnia dane. TLS chroni kanał, ale nie naprawia aplikacji, która loguje pełne nagłówki.

Authorization może zależeć od rekordu: użytkownik z rolą CUSTOMER nie ma prawa pobrać cudzego zamówienia. Kontrola powinna być związana z odczytem/zapisem, aby nie powstał wyścig między sprawdzeniem a zmianą ownershipu. Testy negatywne obejmują inne ID, tenant, wygaśnięty credential i pola, których dany użytkownik nie może zmieniać.

## API-09. Czym różnią się OAuth 2.0, OpenID Connect i JWT?

OAuth 2.0 opisuje delegowanie dostępu i uzyskiwanie access tokenów. OpenID Connect dodaje warstwę tożsamości i ID token. JWT jest formatem tokena/claims, nie pełnym protokołem logowania; access token może być JWT albo wartością opaque.

Nie wysyłam ID tokena jako zamiennika dowolnego access tokena do API. Wybieram sprawdzony authorization server, odpowiedni przepływ i bibliotekę walidacji. Samo zdekodowanie base64 JWT nie dowodzi zaufania do nadawcy.

Przeglądarkowa aplikacja zwykle korzysta z przepływu przekierowania i kodu autoryzacyjnego z PKCE według wspieranego profilu, a usługa działająca we własnym imieniu może użyć client credentials. To odmienne tożsamości i zakresy dostępu. JWT nie odpowiada sam, jak użytkownik się zalogował ani jak token dostał do klienta.

Resource server sprawdza token przeznaczony dla własnego audience. Authorization server odpowiada za jego wydanie, a klient za właściwe użycie i ochronę. Nie buduję własnego „OAuth” z endpointu zwracającego podpisany JSON, bo omijam wtedy reguły protokołu i liczne scenariusze awarii. [Aktualne praktyki bezpieczeństwa OAuth — RFC 9700](https://www.rfc-editor.org/rfc/rfc9700).

## API-10. Jak bezpiecznie weryfikować JWT i co przy rotacji kluczy?

Weryfikuję podpis z zaufanego klucza, dozwolony algorytm, issuer, audience, typ tokena i ograniczenia czasu. Claims służą dopiero potem do autoryzacji. Podpisany JWT nie jest automatycznie zaszyfrowany, więc nie umieszczam w nim sekretów.

Obsługuję rotację i ograniczony cache kluczy z kontrolowanego źródła. Ustalam tolerancję zegara i sposób odebrania dostępu: krótki TTL, sesja/introspection lub lista unieważnień według wymagań. [JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725).

Odczyt pola role bez weryfikacji podpisu pozwala klientowi wpisać sobie dowolne uprawnienia. Weryfikacja podpisu bez audience nadal może zaakceptować poprawny token wydany do innego systemu. Dlatego walidacja to zestaw warunków, nie jedna operacja decode.

Rotacja wymaga okresu współistnienia właściwych kluczy i ograniczonego cache. Nie pobieram klucza z dowolnego URL wskazanego w niezaufanym tokenie. Przy braku znanego kid ustalam kontrolowane odświeżenie oraz ochronę przed lawiną zapytań. Zegar ma niewielką jawną tolerancję, a błędy walidacji nie ujawniają pełnej treści tokena w logach.

## API-11. Stateless czy stateful authentication? Co z refresh tokenem?

Sesja utrzymuje stan po stronie serwera i ułatwia natychmiastowe unieważnienie, ale wymaga dostępnego magazynu sesji. Lokalnie weryfikowany access token ogranicza zależność od centralnego odczytu, kosztem trudniejszego wycofania przed wygaśnięciem.

Refresh token służy do uzyskania nowego access tokena i wymaga bezpiecznego przechowywania, rotacji oraz wykrywania powtórnego użycia zgodnie z kontraktem dostawcy. Token w cookie zmienia analizę CSRF; token dostępny dla JavaScript powiększa skutki XSS. Nie ma jednego rozwiązania bez kosztów.

Lokalnie weryfikowany JWT może działać podczas krótkiej awarii dostawcy kluczy, jeśli zaufane klucze są w cache i token nadal ważny. Opaque token z introspection ma z kolei koszt zależności od serwera, chyba że wynik jest świadomie cache'owany. Żaden wariant nie usuwa potrzeby polityki unieważnienia.

Refresh token powinien mieć inny lifecycle niż access token. Po wykryciu reuse rotowanego refresh rozstrzygam los całej rodziny sesji według kontraktu dostawcy. Dla logout określam, czy kończy tylko bieżącą sesję, wszystkie urządzenia, czy również ważność już wydanych access tokenów. Klient musi znać tę różnicę.

## API-12. Jak działa SecurityFilterChain w Spring Security?

DelegatingFilterProxy prowadzi do FilterChainProxy, który wybiera pasujący SecurityFilterChain. Filtry odtwarzają/weryfikują tożsamość, instalują SecurityContext i kontrolują dostęp przed przekazaniem requestu dalej. Przy wielu łańcuchach kolejność matcherów ma znaczenie.

Konfiguruję jawnie resource server lub logowanie i reguły zasobów, a następnie autoryzację metod/danych. Błędy authentication i authorization mają własne handlery. Nie zostawiam niepasujących ścieżek poza ochroną przypadkiem. [Architektura Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html).

Przy kilku SecurityFilterChain pierwszy pasujący łańcuch przejmuje request według kolejności. Zbyt szeroki matcher przed bardziej szczegółowym może więc wyłączyć oczekiwaną politykę. Wewnątrz łańcucha ustalenie tożsamości musi poprzedzać decyzję o dostępie.

Szkic fragmentu konfiguracji resource server:

<!-- example:API-12-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/public/**").permitAll()
        .anyRequest().authenticated())
    .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
~~~

To fragment Spring Security wymagający dodatkowej konfiguracji walidacji JWT. Sam authenticated nie sprawdza właściciela zamówienia. Testuję również ścieżki błędów i management, żeby nie powstała przypadkowa luka poza oczekiwanym matcherem. [Konfiguracja JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

<!-- follow-up:API-12 -->
**Sprawdź rozumienie:** Token jest poprawnie podpisany, a użytkownik authenticated. Dlaczego GET cudzego zamówienia nadal musi być odrzucony?

<details>
<summary>Kierunek odpowiedzi</summary>

Authentication ustala tożsamość, nie ownership zasobu. Sprawdź audience/scopes oraz politykę danych: właściciel, tenant, stan i dozwolona operacja. Gateway nie zastępuje tej kontroli w usłudze.

</details>

## API-13. Czym jest CORS i czym różni się od CSRF oraz TLS?

CORS to polityka przeglądarki dotycząca odczytu odpowiedzi między originami, a nie autoryzacja serwera. CSRF wykorzystuje automatycznie dołączane credentials ofiary; ochrona zależy od sposobu uwierzytelnienia i transportu. TLS chroni transmisję i tożsamość serwera zgodnie z walidacją certyfikatu.

API może być wywołane poza przeglądarką mimo CORS. Nie wyłączam CSRF mechanicznie tylko dlatego, że odpowiedź jest JSON. Ustalam dozwolone originy i credentials, SameSite oraz politykę cookies zgodnie z przepływem aplikacji.

Przeglądarka może wysłać request, choć zabroni skryptowi odczytać odpowiedź. To dlatego CORS nie może chronić przelewu przed wykonaniem. Atak CSRF wykorzystuje m.in. automatyczne dołączanie cookie; API z bearer w nagłówku ma inną analizę, ale cookie sesyjne/refresh nadal wymaga uwagi.

Preflight jest elementem protokołu przeglądarki, nie dowodem tożsamości. Dla credentials nie łączę bezrefleksyjnie dowolnego originu z pełnym zaufaniem. Test powinien objąć zaufany i obcy origin, nagłówki, metody oraz faktyczny skutek biznesowy. Nagłówek CORS „zablokowane” nie oznacza, że operacja nie zaszła.

## API-14. Jak zabezpieczyć komunikację między mikroserwisami?

Nadaję usługom tożsamość workloadu, ograniczone uprawnienia i szyfrowany kanał, np. mTLS lub zweryfikowany token serwisowy. Propagowanie kontekstu użytkownika wymaga rozróżnienia tożsamości wywołującej usługi i uprawnień delegowanych użytkownika.

Gateway nie zastępuje autoryzacji zasobów w serwisie. Nie ufam dowolnym nagłówkom z Internetu i nie kopiuję tokenów do logów. Rotuję klucze, ograniczam egress oraz sprawdzam scenariusz odmowy i wygaśnięcia credentiala.

Usługa A może być uprawniona do wywołania B, ale nie do wykonania każdej operacji w imieniu każdego użytkownika. Rozróżniam service identity, delegację użytkownika i tenant. Inaczej powstaje confused deputy: zaufana usługa wykonuje nieuprawnione żądanie pochodzące od klienta.

mTLS potwierdza peer zgodnie z polityką certyfikatów i chroni transport; nadal potrzebna jest autoryzacja zasobu. Tokeny mają audience i minimalne scope, a sieć ogranicza niepotrzebne kierunki. Przy rotacji testuję jednoczesną obsługę nowej i starej instancji, bez okresu powszechnego wyłączenia walidacji.

## API-15. Jak ograniczyć dostęp do usługi do 10 tys. użytkowników?

Najpierw rozróżniam uprawnienie dla konkretnego zbioru użytkowników, 10 tys. aktywnych sesji, równoczesnych operacji i 10 tys. żądań na jednostkę czasu. To odpowiednio entitlement/allowlist, zarządzanie sesjami, admission control i rate limiting.

Dla dozwolonej grupy stosuję autoryzację w zaufanym źródle. Dla globalnej współbieżności potrzebny jest koordynowany licznik/lease lub podział budżetów, z zachowaniem po awarii. Nie implementuję biznesowego dostępu samym limitem wątków Tomcata.

Dla „pierwszych 10 tys. zapisanych użytkowników programu” rozwiązaniem jest entitlement w trwałym źródle, z atomowym przydziałem miejsc. Dla „najwyżej 10 tys. operacji jednocześnie” potrzebna jest kontrola in-flight i odzyskiwanie pozwoleń po crashu. Są to różne dane i inne punkty awarii.

Dla globalnego licznika sesji trzeba określić, kiedy sesja wygasa i co przy partycji sieci. Prosty INCR bez bezpiecznego release pozostawia wiszący limit. Local limit na każdym z 20 podów mnoży dopuszczoną liczbę. Najpierw zapisuję dokładną jednostkę ograniczenia i zakres, potem dobieram mechanizm.

## API-16. HttpClient, RestTemplate, RestClient, WebClient czy OpenFeign?

JDK HttpClient daje bazowe synchroniczne i asynchroniczne HTTP. RestTemplate to starszy synchroniczny klient Springa; RestClient udostępnia fluent API synchroniczne, WebClient reaktywne. OpenFeign buduje klienta deklaratywnego na podstawie interfejsu i konfiguracji integracji.

Dobieram model do stosu, a następnie timeouty, pule, serializację i obsługę błędów. POST wymaga metody, URI, nagłówków i zakodowanego body; klient nie zapewnia idempotencji. W Spring Framework 7 RestTemplate jest deprecated na rzecz RestClient. [Klienci Spring](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html).

Klient HTTP jest zwykle współdzielonym zasobem z pulą połączeń, a nie obiektem tworzonym i porzucanym przy każdym żądaniu. Timeout connect, acquisition i odczytu rozwiązują różne problemy. WebClient nie oznacza bezpiecznego blokowania event loop przez block.

Szkic synchronicznego wywołania przez Spring RestClient:

<!-- example:API-16-1 kind:spring-sketch -->
**Kontekst:** Szkic Spring — wymaga zależności, konfiguracji i ewentualnych typów domenowych; poza automatyczną kompilacją.

~~~java
PaymentStatus response = restClient.post()
    .uri("/payments")
    .header("Idempotency-Key", operationId)
    .body(command)
    .retrieve()
    .body(PaymentStatus.class);
~~~

Typy są domenowe; trzeba osobno skonfigurować klienta, błędy i deadline. Deklaratywny Feign skraca zapis, lecz nie zwalnia z kontroli tych samych zasobów i semantyki retry.

## API-17. REST, gRPC, WebSocket czy komunikacja przez broker?

REST pasuje do szeroko dostępnego request/response, gRPC do kontraktów typowanych i streamingu między zgodnymi klientami, WebSocket do długiego dwukierunkowego kanału. Broker oddziela czas dostępności nadawcy i odbiorcy oraz pozwala trwale buforować pracę.

Async HTTP zwalnia wywołujący wątek, lecz nie zapewnia trwałości komunikatu. WebSocket wymaga heartbeatów, reconnect, limitu wolnego odbiorcy i wznowienia. Wybór wynika z potrzeb czasu odpowiedzi, utraty danych, kolejności i wsparcia klientów.

WebSocket daje aktywny kanał, lecz odłączenie klienta nie jest anulowaniem jego wcześniejszej transakcji. Wiadomości potrzebujące trwałości powinny mieć ID i potwierdzenie zapisu, a wznowienie po reconnect cursor/sekwencję. HTTP async także nie gwarantuje przejęcia odpowiedzialności po restarcie.

Dla gRPC rozważam deadline, streaming i kompatybilną ewolucję Protobuf. Broker jest dobry do pracy odroczonej i wielu odbiorców, ale klient potrzebuje wtedy statusu operacji zamiast natychmiastowego wyniku. Wybór protokołu łączę z oczekiwanym SLO i punktem trwałego przyjęcia, nie z samą popularnością technologii.

## API-18. Jak bezpiecznie przechowywać użytkowników i hasła w AWS?

Można użyć zarządzanego dostawcy tożsamości, np. Cognito, aby aplikacja nie zarządzała hasłami. Przy własnym magazynie przechowuję solony adaptacyjny hash hasła, a nie plaintext ani odwracalne szyfrowanie jako substytut hashowania. Dane profilu i sekrety aplikacji mają osobne role.

Dostęp nadaję przez IAM i workload identity, a sekrety integracji trzymam w secret managerze. Hasło nie trafia do logów, tokenów ani zwykłych properties. [User pools Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools.html).

Hasło i sekret integracji wymagają innych mechanizmów. Hasło weryfikujemy przez adaptacyjny hash z solą; nie musimy odzyskiwać oryginału. Sekret operatora płatności musi być dostępny klientowi integracji, więc trzymamy go w kontrolowanym secret store z rotacją i minimalnym dostępem.

Przy Cognito aplikacja przechowuje stabilny identyfikator użytkownika i własne dane profilu, a dostawca obsługuje credential zgodnie z usługą. Nie kopiuję hasła do tabeli profilu „dla wygody”. Procedura resetu, MFA, zmiana email i usunięcie konta również są częścią projektu, bo często tworzą ważniejsze ryzyko niż sam ekran logowania.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [api design](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_a/api_design/README.md)
- [authorization](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/authorization/README.md)
- [Stage 2D — Application Security i Secure SDLC](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_d/README.md)
<!-- labs:end -->
