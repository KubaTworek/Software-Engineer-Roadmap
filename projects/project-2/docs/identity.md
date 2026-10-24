# Zewnętrzna tożsamość marketplace

Secure HMAC pozostaje prostym wariantem lokalnym. Tryb OIDC nie wymaga sekretu
JWT w aplikacji i korzysta z discovery zaufanego issuera oraz jego JWKS.

```powershell
$env:SPRING_PROFILES_ACTIVE='postgres,kafka,secure'
$env:MARKETPLACE_SECURITY_JWT_MODE='oidc'
$env:JWT_ISSUER='http://localhost:8089/realms/roadmap'
$env:JWT_AUDIENCE='ticketing-api'
.\gradlew.bat bootRun
```

Issuer jest z konfiguracji, nigdy z tokenowego jku/iss jako dowolnego URL.
Decoder wymaga podpisu, exp, poprawnego czasu, issuer i audience. Discovery i
JWKS mają connect/read timeout 500 ms; timeout pojedynczego odczytu nie jest
deadline wszystkich prób. Zimny start wymaga dostępnego discovery. Znany klucz
w cache pozwala walidować lokalnie, ale nie odwołuje wydanych JWT natychmiast.

OrderOwnership używa zweryfikowanego customer_id, a gdy brak claimu — UUID sub.
Fixture IdP ma UUID sub. Przy opaque sub innego providera należy wystawić jawne
mapowanie do CustomerId; nie parsuj emaila/username jako tożsamości.

OidcContactOwnershipIntegrationTest uruchamia prawdziwy Keycloak 26.6.2,
discovery, wydanie JWT oraz HTTP PUT/GET/DELETE kontaktu. Właściciel ma dostęp,
inny customer i anonim są odrzucani. Osobny test Keycloak w projekcie 3 sprawdza
rotację klucza oraz awarię IdP z ciepłym i zimnym cache.

Password grant występuje wyłącznie w kontrolowanej fixture testowej. Publiczny
klient produkcyjny powinien realizować Authorization Code + PKCE; backend API
jest Resource Serverem. Lokalny Keycloak start-dev/HTTP oraz jawne hasła fixture
nie są konfiguracją produkcyjną. TLS, własność klienta, mTLS/workload identity
i rotacja credentiali wymagają konfiguracji wybranego środowiska.

Źródła: [OIDC Discovery](https://openid.net/specs/openid-connect-discovery-1_0.html),
[Spring JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
