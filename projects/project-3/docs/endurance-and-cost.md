# Eksploatacja, recovery i koszt udanego zamówienia

## Scenariusz i niezmienniki

Generator ma otwarty model constant-arrival-rate. Każda iteracja tworzy rezerwację
i opłaca zamówienie; co dziesiąta ponawia ten sam klucz. Licznik biznesowy nie
rośnie od retry. RabbitMQ jest wyłączany podczas ruchu; zapis sagi ma działać,
a outbox po restarcie ma dojść do inboxa. Inventory musi zachować równanie
total = available + suma PENDING/CONFIRMED. Wszystkie zamówienia tego run mają
zakończyć PAID. Nie dodajemy ruchu innych użytkowników do wyników tego eksperymentu.

```powershell
$env:COMPOSE_PROJECT_NAME='roadmap-endurance-lab'
docker compose --profile apps up --detach --build --wait
.\scripts\endurance-drill.ps1
```

Domyślnie: 15 min pracy + 15 s awarii + 15 min recovery przy 2 checkout/s.
Skrócony smoke mechanizmu: `-SteadySeconds 60 -OutageSeconds 10 -RecoverySeconds 60`.
Nie traktuj go jako dowodu braku wycieku przez wiele godzin. Zadbaj o wystarczające
inventory; wyczerpanie zasobu kończy test zamiast zaniżać denominator do udanych
prób. Dla długich przebiegów przygotuj osobne wydarzenie i kontrolowany reset
wyłącznie własnego środowiska. Nie zmieniaj produkcyjnego inventory na potrzeby testu.

Skrypt zapisuje w ignorowanym target/endurance: surowy summary k6, stdout/stderr,
próbki CPU/memory/network/block I/O z docker stats, wynik niezmienników i CSV.
Źródło CPU/RSS nie jest histogramem requestów ani dowodem retained heap.
Przy trendzie wzrostowym pobierz JFR/heap/native memory i porównaj stan po GC.
Po generatorze jest do 30 s cichego drain; pomiar kosztu obejmuje również recovery.
Maksymalny czas, rate, concurrency i liczba wierszy batch SQL są ograniczone.

## Metryki i alerty

CheckoutMetrics mierzy w Order Service fizyczne próby API i PAID<=3s.
Generator mierzy logiczną podróż obejmującą również utworzenie rezerwacji.
Definicje nie są identyczne. Liczba unikalnych PAID z bazy jest mianownikiem
kosztu biznesowego, a nie liczba 2xx w gateway. Runbook: [checkout SLO](runbooks/checkout-slo.md).

Sprawdzenie reguł:

```powershell
docker run --rm --entrypoint /bin/promtool --workdir /work/observability/prometheus/tests -v "${PWD}:/work:ro" prom/prometheus:v2.55.1 test rules checkout-slo.test.yml
```

## Ekonomia jednostkowa

UnitEconomics.Window oblicza koszt/unikalne zakończone zamówienie, zachowuje
UNKNOWN przy zerowym mianowniku i wybiera najtańszy wariant spełniający podany
empiryczny SLO/p99. Różne waluty wymagają osobnego kontraktu FX. Tani wariant,
który odrzuca pracę lub nie kończy zamówień, nie wygrywa porównania.

```powershell
java -cp common/target/classes pl.jakubtworek.backend.common.operations.UnitEconomics target/endurance/RUN_ID.csv 0.999 3000
```

CSV można połączyć z niezależnym przebiegiem wariantu po zachowaniu jednego
nagłówka: scenario,seconds,cost,currency,eligible,good,completed,p99Millis.
Porównuj tę samą mieszankę ruchu, dane, cache warmup, horyzont czasu i kontrakt
kosztu. completed obejmuje zamówienia utworzone przez dany run, a nie wszystkie
zamknięcia w zegarowym oknie. eligible to logiczne checkouty generatora.

Przykładowy wariant ograniczenia zasobów: `docker-compose.cost-candidate.yml`
ustawia Order Service na 0.75 CPU i 1 GiB. Uruchom go razem z podstawowym Compose,
powtórz dokładnie parametry baseline i zachowaj osobny CSV. Nie uruchamiaj go
równocześnie z identity overlay przy generatorze używającym lokalnych userId.
Manualny job CI wykonuje oba warianty z tymi samymi fazami i rate.
Przykładowy model ceny w jobie to 0.90 jednostki kosztu wspólnego i 0.10
przypisanego Order Service, z proporcjonalnym 0.075 w wariancie CPU. To jawna
hipoteza alokacji (1.00 vs 0.975 PLN/h), nie wynik odczytu rachunku ani gwarancja
liniowego billing/capacity. W produkcji zastąp ją rzeczywistym modelem kosztu.

EstimatedHourlyCost jest JAWNYM założeniem operatora, domyślnie przykładowe 1 PLN/h
całego przypisanego środowiska. Skrypt nie pobiera cennika chmury i nie generuje
faktury. Przy rzeczywistym rachunku uwzględnij instancje/CPU/pamięć, DB, storage,
broker, egress, telemetrię i opłaty providera oraz regułę podziału kosztów wspólnych.
Podaj walutę, źródło ceny, datę i proporcję alokacji. Minutowy test z 100% sukcesów
nie dowodzi statystycznie 99.9% w 30 dniach. Model filtruje obserwacje, nie wydaje
certyfikatu SLO. Źródło: [FinOps Unit Economics](https://www.finops.org/framework/capabilities/unit-economics/).
