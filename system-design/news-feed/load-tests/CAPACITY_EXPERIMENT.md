# Eksperyment capacity

## Hipoteza i granica

Przed testem zapisz oczekiwany ruch, p95/p99, maksymalny error rate, fan-out lag,
consumer lag, wykorzystanie CPU, puli połączeń i Redis. Capacity to największy
trwały arrival rate spełniający wszystkie SLO, a nie najwyższy chwilowy pik.

## Profile

- `TEST_TYPE=load` — spodziewany ruch;
- `TEST_TYPE=stress` — znajdowanie punktu nasycenia;
- `TEST_TYPE=spike` — nagły skok i czas odzyskania;
- `TEST_TYPE=soak` — wycieki, wzrost storage i degradacja po czasie;
- `celebrity-spike.js` — write amplification i zachowanie ścieżki pull.

## Kryteria przerwania

Przerwij eksperyment przy naruszeniu bezpieczeństwa danych, error rate >5% przez
2 minuty, braku odzyskiwania consumer lag, wyczerpaniu puli DB lub ryzyku utraty
danych. Po teście zachowaj raport k6 oraz snapshot metryk i konfiguracji.
