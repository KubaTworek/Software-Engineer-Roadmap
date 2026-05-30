# Etap 7 — Security i autoryzacja oparta o dane

Ten etap celowo nie kończy się na `ROLE_ADMIN`. Role są tylko pierwszym filtrem. Właściwa autoryzacja sprawdza również właściciela danych, organizację i tenant boundary.

## Zakres implementacji

Dodane elementy:

- Spring Security jako konfiguracja stateless.
- Własny edukacyjny JWT access token podpisany HMAC SHA-256.
- Refresh token przechowywany w bazie jako SHA-256 hash.
- Rotacja refresh tokenów: użyty refresh token jest odwoływany, a użytkownik dostaje nowy.
- `@PreAuthorize` i SpEL.
- Beany autoryzacyjne:
  - `reservationSecurity`,
  - `eventSecurity`,
  - `organizationUserSecurity`,
  - `tenantSecurity`.
- Endpointy zabezpieczone w `/api/secure/**`.

Istniejące endpointy edukacyjne z poprzednich etapów pozostają publiczne, żeby nie rozbić testów MVP/concurrency/performance. Stage 7 pokazuje security na osobnym obszarze API.

## Role

- `CUSTOMER` — widzi tylko własne rezerwacje.
- `EVENT_MANAGER` — widzi eventy swojej organizacji.
- `ORG_ADMIN` — zarządza użytkownikami tylko w swojej organizacji.
- `HR` — widzi pracowników swojej organizacji.
- `SUPPORT` — widzi operacyjne podsumowanie, ale bez pełnych danych płatności.

## Authentication vs authorization

Authentication odpowiada na pytanie: kim jesteś?

W projekcie robią to:

- `/api/auth/login`,
- JWT access token,
- `JwtAuthenticationFilter`.

Authorization odpowiada na pytanie: czy możesz wykonać tę konkretną operację na tych konkretnych danych?

W projekcie robią to:

- `@PreAuthorize`,
- SpEL,
- sprawdzanie `organizationId`,
- sprawdzanie właściciela rezerwacji po emailu klienta,
- sprawdzanie roli i tenanta razem.

## Endpointy

### Login

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "manager@orga.com",
  "password": "secret"
}
```

Odpowiedź zawiera:

- `accessToken`,
- `refreshToken`,
- czasy wygaśnięcia.

### Refresh token rotation

```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "..."
}
```

Stary refresh token zostaje oznaczony jako revoked. Ponowne użycie starego tokena ma zakończyć się błędem.

### Rezerwacja widoczna zależnie od danych

```http
GET /api/secure/reservations/{reservationId}
Authorization: Bearer <accessToken>
```

Reguły:

- `CUSTOMER` widzi rezerwację tylko wtedy, gdy email użytkownika zgadza się z emailem klienta na rezerwacji.
- `EVENT_MANAGER`, `ORG_ADMIN`, `HR` widzą rezerwacje swojej organizacji.
- `SUPPORT` widzi rezerwację operacyjnie.

Przykład z kodu:

```java
@PreAuthorize("@reservationSecurity.canView(authentication, #reservationId)")
public ReservationResponse getReservation(UUID reservationId) { ... }
```

### Event manager

```http
GET /api/secure/events/{eventId}/manager-view
Authorization: Bearer <accessToken>
```

Reguły:

- `EVENT_MANAGER` i `ORG_ADMIN` mogą widzieć event tylko ze swojej organizacji.
- Manager z innej organizacji dostaje `403`.

### Admin organizacji

```http
GET /api/secure/organizations/{organizationId}/users
Authorization: Bearer <accessToken>
```

Reguły:

- tylko `ORG_ADMIN`,
- tylko w swoim tenantcie.

### HR

```http
GET /api/secure/hr/organizations/{organizationId}/employees
Authorization: Bearer <accessToken>
```

Reguły:

- tylko `HR`,
- tylko własna organizacja.

### Support i dane płatności

```http
GET /api/secure/support/reservations/{reservationId}/payment-summary
Authorization: Bearer <supportToken>
```

Support dostaje zamaskowane dane:

```json
{
  "cardNumber": "**** **** **** ****"
}
```

Ten endpoint ma pokazać, że rola `SUPPORT` może mieć dostęp operacyjny, ale nie powinna widzieć pełnych danych płatności.

## Testy

Nowe testy są w:

```text
SecurityStage7IntegrationTest
```

Uruchomienie:

```bash
mvn test -Dtest=SecurityStage7IntegrationTest
```

Testy sprawdzają:

- login zwraca access token i refresh token,
- refresh token jest rotowany,
- stary refresh token nie może zostać użyty ponownie,
- customer widzi tylko własną rezerwację,
- manager widzi tylko event swojej organizacji,
- admin organizacji zarządza użytkownikami tylko w swoim tenantcie,
- HR widzi tylko pracowników swojej organizacji,
- support widzi zamaskowane dane płatności,
- support nie widzi pełnych danych płatności,
- endpointy `/api/secure/**` wymagają uwierzytelnienia.

## Ważne ograniczenie

JWT w tym projekcie jest implementacją edukacyjną. W produkcji zwykle lepiej użyć gotowego resource servera OAuth2/OIDC, np. Keycloak, Auth0, Cognito albo Spring Authorization Server. Ten etap ma pokazać mechanikę i pułapki autoryzacji, nie zastępować pełnego identity providera.
