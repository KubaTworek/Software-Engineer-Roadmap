# Tożsamość na każdej granicy HTTP

Domyślny Compose pozostaje otwartym lokalnym labem. `app.security.enabled=true`
włącza JWT Resource Server w każdej usłudze: podpis RSA/JWKS, issuer, audience,
obowiązkowe exp i czas ważności. Osobne issuer/JWKS URI pozwalają użyć publicznego
issuera z wewnętrznym adresem kluczy, bez zależności startupu od discovery.
Adresy pochodzą z zaufanej konfiguracji. Timeout HTTP JWKS to 400 ms na próbę.

```powershell
$env:COMPOSE_PROJECT_NAME='roadmap-identity-lab'
docker compose -f docker-compose.yml -f docker-compose.identity.yml --profile apps up --detach --build --wait
.\scripts\identity-drill.ps1
docker compose -f docker-compose.yml -f docker-compose.identity.yml --profile '*' down --volumes --remove-orphans
```

Gateway przekazuje Bearer w jawnej allowliście; downstream sam go weryfikuje.
X-User-Id nie jest zaufany. Order/Reservation Controller wiąże userId z sub,
odrzuca podmienione body i sprawdza własność odczytu/cancel. Confirm, payment,
inventory i chaos wymagają scope internal. Actuator poza health wymaga ops.

Order i Reservation używają client credentials przy wywołaniach usługowych.
ServiceTokenProvider ma ograniczone timeouty, pojedyncze współdzielone pobranie
tokena w locie i odnowienie przed exp. I/O jest asynchroniczne; nie trzymamy
monitora na czas sieci. Worker ma własną tożsamość, nie utrwalony token klienta.
Nieznany/nieodświeżalny credential oznacza odmowę; brak tokenu nie jest fallbackiem
do lokalnego userId w trybie secure.

Fixture używa jednego technicznego klienta internal dla uproszczenia. Produkcja
potrzebuje odrębnych tożsamości usług, minimalnych scopes/audience i rotacji
credentiali. Znajomość wewnętrznego adresu nie powinna dawać uprawnień.
Monitoring secure wymaga osobnego poświadczenia ops; lokalny prometheus.yml
nie zawiera stałego tokenu administracyjnego. Nie uruchamiaj endurance w secure
z losowym userId; generator wymaga wtedy prawdziwych tożsamości workloadu.

KeycloakIdentityInfrastructureTest uruchamia prawdziwy IdP, sprawdza HTTP,
podmianę właściciela, rotację RSA i JWKS oraz cold/warm cache przy awarii.
identity-drill idzie przez gateway → reservation → order → payment → confirm,
wymaga PAID i stabilnego retry, a także sprawdza odmowę przez adres bezpośredni.

Cache kluczy nie jest natychmiastową revocation tokenów. Po kompromitacji
zaplanuj odwołanie credentiali/kluczy, czas życia JWT i odświeżenie cache zgodnie
z polityką IdP. Nie loguj access tokenu, client secret ani odpowiedzi token endpointu.
Testowy password grant, HTTP, start-dev i jawne credentiale służą wyłącznie labowi.

Fixture jawnie przypisuje scope basic z mapperem sub oraz internal/ops tylko
klientowi usługowemu. Zastąpienie domyślnych scopes realm bez basic może usunąć
sub z access tokenu; Resource Server wymaga niepustego sub i odmawia takiemu
tokenowi dostępu. Mapper dla przypiętej wersji opisuje
[źródło Keycloak 26.6.2](https://raw.githubusercontent.com/keycloak/keycloak/26.6.2/services/src/main/java/org/keycloak/protocol/oidc/mappers/SubMapper.java).

Źródła: [Keycloak containers](https://www.keycloak.org/server/containers),
[Spring Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
