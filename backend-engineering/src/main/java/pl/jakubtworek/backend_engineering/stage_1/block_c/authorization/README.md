# authorization

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** authorization.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „authorization” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=AuthApiContractTest,AuthServiceTest,JwtSecurityContractTest" test`
> - **Role klas:** `AuthController` = `production-boundary`, `OrderController` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Bezpieczeństwo backendu — tożsamość, uprawnienia i cykl życia sesji



Bezpieczeństwo nie zaczyna się od `SecurityFilterChain`, lecz od aktywów, granic zaufania i sposobów nadużycia systemu. Framework pomaga zweryfikować token oraz wykonać politykę, ale nie zdecyduje, komu ufamy, które dane są wrażliwe ani jaki skutek ma przejęcie credentiala.

## Minimalny threat model

W tym laboratorium chronimy:

- hasło użytkownika i hash hasła;
- prywatny klucz podpisujący;
- access i refresh tokeny;
- zamówienia należące do konkretnych użytkowników;
- operacje administracyjne.

Zakładamy atakującego, który może wysyłać dowolne requesty, ponawiać przechwycony refresh token, modyfikować claims w tokenie, próbować cudzych identyfikatorów oraz analizować różnice w odpowiedziach logowania. Nie zakładamy, że dane po walidacji JSON są automatycznie bezpieczne ani że ruch wewnętrzny jest zaufany.

## Rozdziel pojęcia

Uwierzytelnianie odpowiada na pytanie „kim jest caller?”, a autoryzacja — „czy może wykonać tę operację na tych danych?”. OAuth 2.0 jest frameworkiem delegowanej autoryzacji, OpenID Connect dodaje warstwę tożsamości, a JWT jest formatem tokena. Access token nie musi być JWT-em; może być opaque tokenem sprawdzanym przez introspection.

Resource Server powinien zweryfikować co najmniej:

1. dozwolony algorytm i podpis,
2. czas `exp` i ewentualnie `nbf`,
3. zaufanego `iss`,
4. własne `aud`,
5. wymagane claims i ich typy.

Poprawny podpis nie oznacza jeszcze, że token został wystawiony dla tego API. Laboratorium wymusza issuer `demo-auth-server` oraz audience `backend-api`. `jti` nadaje tokenowi unikalną tożsamość potrzebną między innymi przy audycie lub denyliście.

## Mapa kodu

| Element | Odpowiedzialność |
|---|---|
| `JwtKeyConfig` | lokalny klucz RSA i walidacja podpisu, issuer oraz audience |
| `JwtTokenService` | krótko żyjący access token z `sub`, `jti`, `iss`, `aud` i authorities |
| `JwtAuthoritiesConverter` | jawny kontrakt claims → `GrantedAuthority` |
| `ConfiguredCredentialStore` | odczyt użytkownika i hasha z zewnętrznej konfiguracji |
| `RefreshTokenService` | hash tokena, rotacja, blokada rekordu i wykrywanie reuse |
| `OrderService` / `UserSecurity` | method security i ownership zasobu |
| `AuthExceptionHandler` | bezpieczne, nieróżnicujące błędy uwierzytelniania |

## Hasła i login

Hasło trafia do wolnego, odpornego na brute force algorytmu, na przykład Argon2id, scrypt lub bcrypt z parametrami dobranymi do sprzętu i budżetu latency. Hash nie jest sekretem równoważnym hasłu, ale nadal należy go chronić. Endpoint wymaga rate limitu, alertów na credential stuffing i — zależnie od ryzyka — MFA.

Nie zwracamy różnych komunikatów dla nieistniejącego użytkownika, złego hasła i wyłączonego konta. Wszystkie prowadzą do `INVALID_CREDENTIALS`. Dla nieznanego użytkownika wykonywane jest porównanie z losowym dummy hashem, co ogranicza prosty timing oracle. Nie daje to idealnie stałego czasu całego requestu, dlatego nadal potrzebne są rate limiting i monitoring. Limit długości requestu jest sprawdzany przed kosztownym hashowaniem.

Laboratorium nie zawiera hasła w repozytorium. Opcjonalnego użytkownika demonstracyjnego można dostarczyć z zewnątrz:

```text
APP_SECURITY_DEMO_USER_USERNAME=alice
APP_SECURITY_DEMO_USER_PASSWORD_HASH=<bcrypt-hash>
```

Bez tych wartości login celowo odrzuca wszystkich użytkowników. W produkcji adapter powinien korzystać z właściwego repozytorium tożsamości lub zewnętrznego Identity Providera.

## Authorities i zasada najmniejszych uprawnień

`hasRole('ADMIN')` szuka `ROLE_ADMIN`, natomiast `hasAuthority('ORDER_READ')` porównuje dokładną wartość. Claim, konwerter oraz wyrażenie metodowe tworzą jeden kontrakt. Samo umieszczenie beana konwertera w kontekście nie wystarcza — musi zostać podłączony do konfiguracji Resource Servera.

`@PreAuthorize` działa przez proxy. Test wywołujący `new OrderService(...)` sprawdza logikę metody, ale nie sprawdza autoryzacji. Dlatego osobny test korzysta z prawdziwego proxy i dowodzi, że odmowa następuje przed dostępem do repozytorium.

Ownership jest polityką zależną od danych. Dla krytycznego zapisu osobne „sprawdź właściciela, potem zapisz” może stworzyć TOCTOU. Warunek powinien należeć do tej samej transakcji, conditional update albo polityki domenowej operującej na tym samym załadowanym agregacie. Administrator musi być jawną alternatywą, a nie ukrytym wyjątkiem w kodzie persistence.

## Refresh token rotation i reuse detection

Refresh token jest długowiecznym credentialem wysokiej wartości. Surowa wartość wraca tylko raz, a baza przechowuje SHA-256 tokena o wysokiej entropii. Hash nie zastępuje entropii: krótki, przewidywalny token nadal byłby podatny na brute force.

Każda rotacja:

1. blokuje rekord tokena `PESSIMISTIC_WRITE`, aby dwa równoległe requesty nie wygrały;
2. oznacza stary token jako `ROTATED`;
3. tworzy następcę w tej samej rodzinie;
4. zwraca nową surową wartość.

Ponowne użycie starego tokena może oznaczać kradzież. Wtedy cała rodzina jest unieważniana jako `REUSE_DETECTED`, a klient musi zalogować się ponownie. Publiczna odpowiedź nadal brzmi tylko „invalid or expired”, natomiast dokładny powód powinien trafić do audytu i metryk bezpieczeństwa.

## Revocation i ograniczenia JWT

Krótki TTL access tokena zmniejsza okno nadużycia, ale nie daje natychmiastowego logoutu. Możliwe strategie to:

| Strategia | Zysk | Koszt |
|---|---|---|
| krótki TTL | prostota i ograniczone okno | token działa do wygaśnięcia |
| denylista `jti` | szybkie unieważnienie | stan i lookup na request |
| opaque token + introspection | centralna kontrola | zależność sieciowa i latency |
| wersja sesji/użytkownika | zbiorcze wylogowanie | lookup lub cache |
| rotacja klucza | reakcja na kompromitację klucza | unieważnia wiele tokenów naraz |

JWT nie czyni systemu bezstanowym. Nadal istnieją klucze, refresh tokeny, polityki revocation, użytkownicy, audyt i rate limiting.

## Security HTTP

Laboratorium używa bearer tokenów w nagłówku i bezstanowej sesji, dlatego CSRF jest wyłączone. Gdy credential jest automatycznie dołączany przez przeglądarkę jako cookie, ochrona CSRF ponownie staje się konieczna. CORS nie jest mechanizmem uwierzytelniania i nie chroni klientów innych niż przeglądarka.

`401` oznacza brak lub nieważne uwierzytelnienie, a `403` — rozpoznaną tożsamość bez uprawnienia. Walidacja i advice kontrolera nie obsłużą każdego błędu z filtra security; spójny format na tej granicy wymaga `AuthenticationEntryPoint` i `AccessDeniedHandler`.

## Sekrety i klucze

Klucze prywatne, hasła, API keys oraz surowe tokeny nie trafiają do Git, logów ani telemetrycznych tagów. `application.properties` korzysta z `EXTERNAL_API_KEY`, zamiast zawierać demonstracyjny sekret. Produkcyjny klucz podpisujący powinien pochodzić z KMS/HSM lub zarządzanego Authorization Servera.

`JwtKeyConfig` generuje klucz przy starcie wyłącznie po to, żeby laboratorium było samodzielne. Restart unieważnia wszystkie tokeny, wiele instancji nie ufa sobie wzajemnie, a bez `kid` nie ma bezpiecznej rotacji. To jawne ograniczenie, nie wzorzec wdrożeniowy.

## Testy negatywne

Najbardziej wartościowe testy bezpieczeństwa dowodzą odmowy:

- zły issuer, audience, podpis lub wygasły token;
- brak authority oraz próba dostępu do cudzego zasobu;
- nieistniejący, wyłączony użytkownik i błędne hasło dają ten sam błąd;
- ten sam refresh token nie może zostać obrócony dwa razy;
- reuse unieważnia następcę z tej samej rodziny;
- odmowa method security następuje przez rzeczywiste proxy przed efektem ubocznym;
- odpowiedź nie zawiera tokena, hasha, stack trace ani informacji o istnieniu konta.

H2 i test jednostkowy nie udowadniają jeszcze zachowania blokad produkcyjnej bazy. Wyścig rotacji trzeba dodatkowo sprawdzić testem integracyjnym na tym samym silniku i poziomie izolacji, których używa wdrożenie.
