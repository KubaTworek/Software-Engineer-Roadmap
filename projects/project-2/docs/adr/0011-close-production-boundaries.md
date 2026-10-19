# ADR 0011: domknięcie granic Stage 2

## Kontekst

Kilka adapterów kończyło gwarancję przed granicą wielu replik lub prawdziwej
infrastruktury: idempotencja HTTP była lokalna, Inventory wykonywało read-modify-write
bez locka, timeout płatności był binarnym odrzuceniem, a replay DLQ omijał Kafkę.

## Decyzja

- `Idempotency-Key` przechowuje PostgreSQL, a advisory transaction lock serializuje
  ten sam klucz. Wpis, Order i outbox należą do jednej transakcji.
- Inventory blokuje wiersze `FOR UPDATE` w stabilnej kolejności i agreguje
  powtarzające się linie produktu przed walidacją.
- wynik providera ma `ACCEPTED`, `REJECTED` albo `UNKNOWN`; stan niepewny wolno
  rozstrzygnąć tylko przez lookup stabilnego `providerOperationId`.
- replay DLQ publikuje oryginalny envelope do Kafki.
- GraphQL analizuje AST, gRPC dziedziczy JWT i opcjonalne mTLS, WebSocket wymaga
  order scope i wspiera wznowienie od numeru sekwencji.
- CDC ma osobną suite PostgreSQL -> Debezium Connect -> Kafka.

## Konsekwencje

Klucze idempotencji potrzebują retencji, `UNKNOWN` wymaga workera i alertu,
WebSocket nadal nie jest źródłem prawdy, a replay nadal podlega at-least-once.
Status DLQ `REPLAYED` oznacza udany publish, nie udany efekt konsumenta.

## Odrzucone alternatywy

- lokalna mapa idempotencji — nie działa między replikami;
- drugi `reserve` po timeout — może podwójnie obciążyć klienta;
- replay przez lokalny event bus — omija semantykę konsumenta;
- globalna subskrypcja WebSocket — ujawnia cudze zamówienia.
