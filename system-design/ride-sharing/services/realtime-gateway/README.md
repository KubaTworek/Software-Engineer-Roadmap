# Realtime Gateway

## Sens laboratorium

Gateway zamienia trwały strumień zdarzeń na nietrwałe aktualizacje WebSocket. Najważniejszy niezmiennik: **zdarzenie przejazdu może trafić wyłącznie do jego uczestników**, a awaria socketu nie może wpływać na stan domenowy.

Krytyczny przepływ: outbox → Kafka → każdy węzeł gatewaya ma własną grupę konsumencką → envelope zostaje odpakowany → wiadomość trafia do prywatnej kolejki użytkownika. REST pozostaje źródłem prawdy po reconnect.

## Najważniejsze decyzje

- Wspólna grupa Kafka rozdzielałaby eventy pomiędzy węzły i gubiła lokalnych subskrybentów, dlatego node ma własną grupę.
- JWT jest weryfikowany podczas STOMP `CONNECT`, a subskrypcje są autoryzowane niezależnie.
- Publiczne topiki ride nie są używane; city broadcast jest zastrzeżony dla roli administracyjnej.
- WebSocket zapewnia UX, nie durability ani exactly-once delivery.

Rekomendowany test: `RealtimeFanoutServiceTest`.

## Granica produkcyjna

Laboratorium nie implementuje replayu sesji, backpressure per socket, limitów połączeń ani zewnętrznego brokera STOMP. Produkcyjnie potrzebne są limity bufora, heartbeat, metryki slow consumers i strategia resynchronizacji przez REST.
