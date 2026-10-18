# Kontrakty API i protokołów

Granice są wykonywalne: GraphQL liczy AST łącznie z aliasami i fragmentami; gRPC
kończy pracę po deadline/cancellation, w profilu secure używa JWT, a komplet
cert/key/client CA włącza mTLS; WebSocket ogranicza subskrypcję do `orderId` i
wznawia krótki strumień przez parametr `after`. Trwały stan pozostaje w query API.

| Granica | Kiedy użyć | Główne ryzyko | Wykonywalna ochrona |
|---|---|---|---|
| REST | zasoby, cache HTTP, publiczne API | lost update, niejednolity błąd | ETag/If-Match, Problem Details, Idempotency-Key |
| GraphQL | elastyczne grafy odczytowe dla UI | N+1, koszt i auth pól | `QueryComplexityBudget`, DataLoader, field auth |
| gRPC | wewnętrzne RPC o silnym schemacie | zerwanie kompatybilności i deadline | reserved field numbers, `DeadlineBudget` |
| WebSocket | niskolatencyjny sygnał zmiany | wolny klient i wygasłe auth | `ConnectionSession`, bounded queue, re-auth |

`schema.graphqls` jest wykonywany przez Spring GraphQL, `ordering.proto` generuje
stub i serwis gRPC, a `/ws/orders` jest rzeczywistym endpointem Spring WebSocket.
Nie oznacza to, że jeden use case należy wystawić wszystkimi protokołami. Dobór
wynika z potrzeb klienta; prostszy REST pozostaje domyślny.
