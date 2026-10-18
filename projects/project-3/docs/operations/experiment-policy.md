# Polityka eksperymentów operacyjnych

## Niezmienniki i kryteria przerwania

- inventory nigdy nie spada poniżej zera;
- jeden `Idempotency-Key` daje jeden order i jeden payment;
- `http_req_failed >= 5%` lub `p99 >= 3 s` przez 30 sekund przerywa soak test;
- rosnące `dropped_iterations` oznacza brak capacity, nie „szybszy system”;
- po restarcie zależności health musi wrócić w 60 sekund, inaczej drill kończy się błędem;
- po broker lag porównujemy outbox, queue depth, inbox i efekt, nie tylko HTTP 2xx.

`open-vs-closed.js` pokazuje coordinated omission: closed model zmniejsza tempo, gdy odpowiedzi
zwalniają, natomiast arrival-rate zachowuje zaplanowany napływ i ujawnia kolejki. Wyników nie
porównuj między maszynami jako stałych liczb; porównuj zmianę mechanizmu na tym samym środowisku.
