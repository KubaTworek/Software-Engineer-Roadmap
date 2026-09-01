# Stage 2D — Application Security i Secure SDLC

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `praktyka-produkcyjna`
> - **Uczy:** Stage 2D — Application Security i Secure SDLC.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Stage 2D — Application Security i Secure SDLC” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=BrowserAndLoggingSecurityTest,ReleaseSecurityGateTest,SafeCommandDecoderTest" test`
> - **Role klas:** `BoundedBodyReader` = `correct`, `SafeCommandDecoder` = `correct`, `SafeOutboundRequestPolicy` = `correct` (+1).
> - **Granica:** Laboratorium pokazuje kontrakt i failure modes; nie zastępuje pełnego testu end-to-end ani operacyjnej konfiguracji środowiska.
<!-- material-card:end -->

## Cel bloku

Bezpieczeństwo aplikacji nie kończy się na poprawnym JWT ani adnotacji
`@PreAuthorize`. To własność całego przepływu: projektu granic zaufania, wejść HTTP,
wywołań wychodzących, plików, serializacji, sekretów, telemetrii, artefaktu wdrożenia
i procesu aktualizowania zależności.

Ten blok jest wykonywalnym laboratorium polityk bezpieczeństwa. Przykłady są małe,
aby można było zobaczyć dokładny warunek odmowy. Nie stanowią frameworka WAF,
antywirusa, secret managera ani skanera podatności.

```text
threat model i trust boundaries
  → walidacja wejścia oraz egressu
  → bezpieczne przetwarzanie danych i sekretów
  → logowanie bez danych wrażliwych
  → SBOM, skan, podpis i provenance
  → fail-closed release gate
```

## Mapa kodu

| Element | Odpowiedzialność | Najważniejsza odmowa |
| --- | --- | --- |
| `SecurityDataFlow`, `ThreatModelValidator` | aktywa, trust zones i wymagane kontrole | privileged flow bez authz i audytu |
| `SafeOutboundRequestPolicy` | exact allowlist dla egressu HTTPS | metadata IP, userinfo, suffix hosta i redirect poza allowlistę |
| `SafeStoragePath` | pojedynczy plik pod storage root | `../`, separator, obce rozszerzenie |
| `BoundedBodyReader`, `UploadPolicy` | limit requestu i walidacja uploadu | chunked body ponad limit i fałszywy content type |
| `SafeCommandDecoder` | jawny discriminator i zamknięty schema | nazwa klasy, nieznany typ i mass assignment |
| `RotatingSecret` | current/previous podczas kontrolowanej rotacji | stara wersja po zakończeniu grace period |
| `DataProtectionBoundary` | właściciel TLS i klucza dla każdego hopu oraz storage | niezaszyfrowana kopia PII |
| `BrowserSecurityPolicy` | osobne decyzje HTTPS, CORS i CSRF | cookie + unsafe method bez tokena CSRF |
| `SecurityLogSanitizer` | klasyfikowane logowanie strukturalne | sekret oznaczony przez caller jako publiczny |
| `ReleaseSecurityGate` | polityka dowodów z pipeline’u | mutable image, brak SBOM/podpisu, podatność lub sekret |

Kod znajduje się w pakiecie [`security`](security), a testy negatywne w analogicznym
pakiecie `src/test/.../stage_2/block_d/security`.

## Threat modeling przed kodem

Threat model nie jest listą wszystkich możliwych ataków. Ma wskazać:

1. aktywa: credentiale, dane klientów, pieniądze, dostęp administracyjny;
2. aktorów i ich możliwości;
3. trust zones: internet, edge, aplikacja, data plane i third party;
4. przepływy przekraczające granice;
5. możliwe nadużycia, kontrolki, właściciela i sposób weryfikacji.

STRIDE może pomagać zadawać pytania o spoofing, tampering, repudiation, information
disclosure, denial of service i elevation of privilege, ale sama tabela STRIDE nie
jest wynikiem. Wynikiem są wymagania, testy i decyzje projektowe.

`ThreatModelValidator` realizuje minimalny, fail-closed review:

- internet wymaga input validation i abuse limit;
- flow niepubliczny wymaga authentication;
- operacja privileged wymaga authorization oraz audit;
- sensitive data store wymaga encryption at rest;
- third-party egress wymaga allowlisty;
- każdy opisany hop wskazuje ochronę transportu.

Model celowo rozróżnia endpoint publiczny od nieuwierzytelnionego błędu. Publiczny
health check może nie wymagać loginu, ale nadal wymaga ograniczenia informacji,
rate limitu i bezpiecznego transportu.

## SSRF i egress

SSRF pojawia się, gdy aplikacja pobiera URL kontrolowany przez użytkownika. Atakujący
może próbować dotrzeć do metadata service, panelu administracyjnego, Redis, loopbacku
albo usług widocznych wyłącznie z sieci aplikacji.

`SafeOutboundRequestPolicy` stosuje ścisłą politykę:

- tylko HTTPS i port domyślny;
- bez userinfo i fragmentu;
- bez IP literals;
- host musi dokładnie występować na allowliście — suffix matching nie wystarcza;
- każdy redirect przechodzi tę samą walidację.

Samo sprawdzenie tekstu URL nie zamyka tematu. Produkcyjny klient powinien dodatkowo:

- rozwiązać wszystkie rekordy A/AAAA i odrzucić loopback, private, link-local,
  multicast oraz adresy metadata;
- chronić się przed DNS rebindingiem i zmianą odpowiedzi między walidacją a connect;
- nie ufać systemowemu proxy ani automatycznym redirectom bez tej samej polityki;
- ograniczać response size, czas, liczbę redirectów i obsługiwane content types;
- używać osobnej sieciowej polityki egress jako defence in depth.

Allowlista jest silniejsza niż blocklista. Nie należy „naprawiać” SSRF przez samo
odrzucenie napisu `localhost`, ponieważ ten sam cel ma wiele reprezentacji.

## Path traversal i upload

`SafeStoragePath` traktuje nazwę klienta jako pojedynczy segment, normalizuje Unicode,
stosuje ścisły alfabet i dozwolone rozszerzenia, a na końcu sprawdza rodzica wyniku.
Nie skleja ścieżki przez string concatenation.

To nadal nie wystarcza przy współdzielonym filesystemie. Symlink utworzony między
walidacją a zapisem może stworzyć TOCTOU. Produkcyjnie warto generować własną nazwę
obiektu, otwierać pliki bez podążania za symlinkami, izolować katalog i uruchamiać
proces bez praw zapisu poza nim. Laboratorium Stage 2C niezależnie sprawdza tę granicę
w [aplikacji wdrożeniowej](../block_c/workshop/README.md).

Upload wymaga kilku limitów:

```text
reverse proxy / ingress
  → limit serwera HTTP
  → streaming limit aplikacji
  → limit po rozpakowaniu i przetworzeniu
```

`BoundedBodyReader` liczy rzeczywiście odczytane bajty, więc chroni również request bez
`Content-Length`. `UploadPolicy` porównuje media type, rozszerzenie i minimalną magic
signature dla PNG/PDF. Nazwa ani nagłówek klienta nie są dowodem zawartości.

Pełny pipeline uploadu powinien dodatkowo obejmować quarantine, malware scanning,
ochronę przed zip bomb i parser bomb, limity wymiarów obrazu/stron PDF, storage poza
web root, losową nazwę obiektu, bezpieczny `Content-Disposition`, retention i usunięcie.
Parser pliku powinien działać z najmniejszymi uprawnieniami, najlepiej w izolacji.

## Niebezpieczna deserializacja i mass assignment

Nie należy mapować pola `type` na `Class.forName`, włączać globalnego polymorphic
default typing ani używać natywnego `ObjectInputStream` dla niezaufanych danych.
Gadget chain może wykonać kod jeszcze przed walidacją domenową.

`SafeCommandDecoder` pokazuje prostszy kontrakt:

- zamknięty zestaw typów komend;
- jawna fabryka dla każdego typu;
- dokładny zestaw pól, bez ignorowania `isAdmin=true`;
- limity długości i walidacja semantyczna;
- nieznany typ kończy się odmową.

Schema JSON/Avro/Protobuf pomaga kontrolować strukturę, ale nadal potrzebne są limity
głębokości, rozmiaru, liczby elementów oraz walidacja uprawnień po dekodowaniu.

## Sekrety i rotacja

Sekret nie powinien trafiać do Git, obrazu, argumentów procesu, dashboardu ani dumpu
całego environment. Preferowany przepływ to workload identity → secret manager/KMS →
konkretna wersja sekretu z ograniczonym IAM i audytem odczytu.

`RotatingSecret` demonstruje rotację bez przebudowy obrazu:

1. aplikacja ładuje `v2` i tymczasowo akceptuje `v1` oraz `v2`;
2. producenci przechodzą na `v2`;
3. metryki potwierdzają brak użycia `v1`;
4. poprzednia wersja zostaje wycofana.

Okno dwóch wersji musi być krótkie i obserwowalne. Dla kompromitacji nie stosujemy
długiego grace period — natychmiast unieważniamy credential, ograniczamy blast radius,
rotujemy zależne sekrety i badamy audyt. `RotatingSecret` przechowuje wyłącznie digest
i nadpisuje tymczasowe bufory, ale JVM nie daje twardej gwarancji wymazania wszystkich
kopii z pamięci; jest to model protokołu rotacji, nie HSM.

## Encryption in transit i at rest

Hasło „TLS jest na load balancerze” nie opisuje całej ścieżki. Dla każdego hopu trzeba
wiedzieć: kto zestawia TLS/mTLS, kto weryfikuje nazwę oraz CA, kto rotuje certyfikat i
czy wewnętrzny hop pozostaje zaszyfrowany. Podobnie „dysk jest szyfrowany” nie mówi,
kto kontroluje klucz ani które eksporty, repliki i backupy są objęte polityką.

`DataProtectionBoundary` wymienia transport hops oraz storage copies, a walidator dla
PII i sekretów wymaga szyfrowania i jawnego właściciela certyfikatu/klucza.

Typowe granice odpowiedzialności:

| Warstwa | Odpowiedzialność platformy | Odpowiedzialność aplikacji/zespołu |
| --- | --- | --- |
| TLS | certyfikat, listener, polityka cipherów | poprawna weryfikacja klienta, brak downgrade i ochrona kolejnych hopów |
| storage encryption | zaszyfrowany dysk/usługa | klasyfikacja, IAM, wybór klucza, eksporty i retention |
| field encryption | biblioteka/KMS może dostarczyć primitive | kontekst AEAD, wersja klucza, wyszukiwanie i migracja ciphertextu |
| backup | mechanizm kopii | dostęp, key lifecycle, restore drill i ponowne zastosowanie tombstone’ów |

Nie implementujemy własnych algorytmów kryptograficznych. Używamy dojrzałych
primitive’ów, envelope encryption oraz KMS/HSM, a protokół i format ciphertextu muszą
obsługiwać rotację kluczy.

## CORS, CSRF i security headers

Te mechanizmy rozwiązują różne problemy:

- HTTPS chroni transport;
- CORS mówi przeglądarce, czy skrypt z innego originu może odczytać odpowiedź;
- CSRF chroni operację, gdy credential jest automatycznie dołączany, np. cookie;
- uwierzytelnianie i autoryzacja nadal decydują, kim jest caller i co może zrobić;
- security headers ograniczają zachowanie przeglądarki i cache.

`BrowserSecurityPolicy` wymaga CSRF dla unsafe cookie-authenticated requestu, ale nie
dla bearer tokena jawnie ustawianego w `Authorization`. Jednocześnie CORS denial nie
oznacza, że endpoint jest zabezpieczony przed klientem innym niż przeglądarka.

Model ustawia HSTS, `nosniff`, restrykcyjny CSP, `frame-ancestors`, `Referrer-Policy`
i `Cache-Control: no-store` dla odpowiedzi wrażliwej. Konkretna aplikacja webowa może
potrzebować bogatszego CSP; kopiowanie `default-src 'none'` bez sprawdzenia kontraktu
frontendu nie jest uniwersalnym rozwiązaniem.

## Logi bez tokenów, sekretów i PII

Nie logujemy pełnego request body, wszystkich nagłówków, JWT, cookies ani środowiska.
`SecurityLogSanitizer` wymaga klasyfikacji każdego pola:

- publiczne pole może pozostać jawne;
- stabilny identyfikator jest pseudonimizowany do korelacji;
- PII oraz secret są redagowane;
- nazwy takie jak `Authorization`, `cookie`, `password` i `token` są zawsze tajne,
  nawet jeśli caller błędnie oznaczy je jako publiczne.

Redaction po fakcie jest defence in depth. Najbezpieczniej nie przekazywać danych
wrażliwych do loggera. Dostęp do logów, retention, eksport, alerty i testy wycieku są
częścią modelu bezpieczeństwa. Hash emaila bez sekretnego klucza może być odgadnięty;
nie należy traktować prostego hasha PII jako anonimizacji.

## Supply chain i release gate

Secure build powinien rozdzielać wytworzenie dowodów od decyzji release:

```text
dependency/container/secret scanner
  + CycloneDX lub SPDX SBOM
  + podpis artefaktu
  + provenance builda
  → ReleaseArtifact
  → ReleaseSecurityGate
  → promote albo fail closed
```

`ReleaseSecurityGate` wymaga:

- obrazu przypiętego przez `@sha256:...`, a nie mutowalny tag;
- wygenerowanego SBOM;
- zweryfikowanego podpisu i provenance;
- zera critical vulnerabilities i jawnego budżetu high;
- braku znalezionych sekretów.

Gate nie skanuje artefaktu samodzielnie. Dane muszą pochodzić z realnych narzędzi,
np. dependency review/SCA, skanera obrazu, secret scannera, generatora CycloneDX/SPDX
oraz mechanizmu podpisu/provenance. Dzięki temu laboratorium nie udaje, że ręcznie
wpisane `0 vulnerabilities` jest dowodem.

Proces obsługi findings powinien określać ownership, exploitability, dostępność fixa,
termin naprawy i czasowo ograniczony risk acceptance. Suppression bez daty wygaśnięcia
i uzasadnienia zmienia skaner w dekorację. Skan przy pull requeście nie zastępuje
ciągłego rescanu już wdrożonych artefaktów, ponieważ nowe CVE pojawiają się później.

Aktualne centralne CI uruchamia testy `ReleaseSecurityGate` w zwykłym `verify`. Osobny
profil wykonuje realny Software Composition Analysis i generuje CycloneDX SBOM:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Psecurity-scan verify
```

Artefakty trafiają do `target/backend-engineering-sbom.json` oraz raportów
`target/dependency-check-report.*`. Skan pomija zależności testowe i provided, a build
kończy się błędem przy CVSS `>= 9.0` albo błędzie samego skanera. Próg jest przykładową
polityką release, nie definicją akceptowalnego ryzyka: zespół powinien uwzględniać
exploitability, ekspozycję i własne SLA także dla niższych wyników.

Dependency-Check korzysta z zewnętrznych źródeł podatności. Bez klucza pierwsze pobranie
danych NVD może być bardzo wolne. W CI należy przekazać klucz bezpiecznym kanałem
obsługiwanym przez plugin (zmienna środowiskowa wskazana przez
`nvdApiKeyEnvironmentVariable` albo zaszyfrowany Maven `server`) i cache'ować bazę;
nie wpisywać wartości klucza do POM-a, argumentów procesu ani logów. Profil celowo nie
należy do zwykłego szybkiego `verify`.
Powinien działać w osobnym security buildzie oraz cyklicznie, bo nowe CVE mogą zostać
opublikowane już po wdrożeniu artefaktu.

## Testy negatywne i fail closed

Najważniejsze testy znajdują się w pakiecie `stage_2.block_d.security`:

| Test | Atak / błąd, który ma zostać odrzucony |
| --- | --- |
| `ThreatModelValidatorTest` | privileged PII flow bez authn/authz/audytu i encryption |
| `SafeOutboundRequestPolicyTest` | metadata IP, userinfo, suffix hosta i zły redirect |
| `UploadBoundaryTest` | traversal, obce rozszerzenie, fałszywy PNG i body ponad limit |
| `SafeCommandDecoderTest` | nazwa klasy, nieznany typ i mass assignment |
| `SecretRotationAndEncryptionTest` | stary sekret po grace period i niechroniona kopia PII |
| `BrowserAndLoggingSecurityTest` | cookie bez CSRF, zły origin oraz PII/token w logu |
| `ReleaseSecurityGateTest` | latest tag, brak SBOM/podpisu/provenance, CVE i sekret |

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ThreatModelValidatorTest,SafeOutboundRequestPolicyTest,UploadBoundaryTest,SafeCommandDecoderTest,SecretRotationAndEncryptionTest,BrowserAndLoggingSecurityTest,ReleaseSecurityGateTest" test
```

Negatywny test powinien dodatkowo potwierdzić brak efektu ubocznego i bezpieczny,
nieróżnicujący komunikat publiczny. Szczegółowa przyczyna trafia do chronionego audytu
lub metryki, nie do odpowiedzi ujawniającej topologię czy dane klienta.

## Granice laboratorium

- Nie wykonujemy prawdziwego DNS lookup ani testu DNS rebinding.
- Magic bytes nie zastępują sandboxowanego parsera i malware scanningu.
- Polityka CORS/CSRF jest modelem, nie konfiguracją konkretnego reverse proxy.
- Digest sekretu nie zastępuje secret managera, KMS ani HSM.
- Release gate konsumuje wyniki skanerów; nie jest skanerem CVE ani sekretów.
- Test jednostkowy nie dowodzi poprawnej konfiguracji TLS, storage encryption ani IAM
  w działającym środowisku.

Powiązane materiały: [JWT/OAuth2 i autoryzacja](../../stage_1/block_c/authorization/README.md),
[bezpieczne wdrożenie Stage 2C](../block_c/README.md),
[logi i tracing](../../stage_3/block_b/README.md),
[multi-tenant data governance](../../stage_3/block_a/implementation/saas/README.md) oraz
[IAM, sekrety i DR](../../stage_3/block_c/README.md).
