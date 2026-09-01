# exception

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** exception.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „exception” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ExceptionApiContractTest" test`
> - **Role klas:** `UserController` = `production-boundary`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Walidacja i jeden kontrakt błędów REST



Klient powinien móc obsłużyć błąd na podstawie stabilnego statusu HTTP i maszynowego `code`, bez parsowania tekstu przeznaczonego dla człowieka. Laboratorium używa wyłącznie `ProblemDetail`, zgodnego z aktualnym standardem Problem Details (RFC 9457), zamiast mieszać własny `ApiError` z drugim formatem.

## Kształt odpowiedzi

```json
{
  "type": "https://api.example.com/problems/validation_failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request body validation failed",
  "code": "VALIDATION_FAILED",
  "fields": {
    "email": "Email must be valid"
  }
}
```

`type` identyfikuje klasę problemu, `code` jest stabilnym kodem aplikacyjnym, a `detail` może zostać zmieniony lub zlokalizowany. Nie zwracamy stack trace, SQL, nazw klas ani sekretów. Pełny wyjątek trafia do logu wraz z correlation ID.

## Mapowanie znaczeń

| Sytuacja | Status | Uzasadnienie |
|---|---:|---|
| niepoprawny request lub parametr | 400 | klient nie spełnił składni kontraktu |
| brak uwierzytelnienia | 401 | nie ustalono tożsamości |
| brak uprawnienia | 403 | tożsamość jest znana, ale operacja zabroniona |
| brak zasobu | 404 | wskazany zasób nie istnieje lub nie może być ujawniony |
| konflikt z aktualnym stanem | 409 | request jest poprawny, lecz koliduje ze stanem domeny |
| stary `If-Match` | 412 | warunek zapisany przez klienta nie jest już prawdziwy |
| brak wymaganego `If-Match` | 428 | serwer wymaga operacji warunkowej |
| limit ruchu | 429 | klient powinien respektować `Retry-After` |
| nieoczekiwany błąd | 500 | szczegóły pozostają po stronie serwera |

Nie każdy wyjątek biznesowy jest `400`. Próba utworzenia duplikatu może być `409`, a brak precondition ma własną semantykę. Status jest częścią kontraktu i powinien pomagać klientowi zdecydować: popraw dane, uwierzytelnij się, ponów później czy odśwież zasób.

## Dwie ścieżki walidacji

`@Valid @RequestBody` prowadzi do `MethodArgumentNotValidException`; błędy mają nazwę pola i komunikat. Walidacja `@PathVariable` oraz `@RequestParam` działa na metodzie kontrolera i prowadzi do błędu constraintu. Obie ścieżki muszą ostatecznie zwrócić ten sam format Problem Details.

Metoda `manual-validation` pozostaje celowym przykładem alternatywy z `BindingResult`. Pokazuje dodatkowy boilerplate; standardowa ścieżka z globalnym handlerem jest preferowana.

## Zakres advice

`GlobalExceptionHandler` jest przypisany do kontrolera tego laboratorium. Repozytorium zawiera niezależne przykłady z podobnymi wyjątkami, dlatego globalny catch-all dla całej aplikacji zacierałby ich granice i mógłby przechwytywać błędy obcego modułu. W jednej rzeczywistej aplikacji zwykle istnieje jeden świadomie zaprojektowany kontrakt błędów.
