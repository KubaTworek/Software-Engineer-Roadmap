# Eksperymenty wydajnościowe

Skrypt k6 rozdziela trzy pytania: `load` mierzy oczekiwany ruch, `spike` reakcję na nagły burst, a `soak`
wycieki i narastanie backlogu. Ustawienie progu HTTP nie wystarcza: równolegle obserwuj
`notification_oldest_ready_job_age_seconds`, saturation puli DB, provider quota, retry oraz DLQ.

```powershell
k6 run -e PROFILE=load -e RATE=50 load-tests/notification-pipeline.js
k6 run -e PROFILE=spike load-tests/notification-pipeline.js
k6 run -e PROFILE=soak -e DURATION=2h load-tests/notification-pipeline.js
```

Przed testem zapisz oczekiwane `λ`, capacity providera, maksymalny inflight i kryterium przerwania. Przerwij
eksperyment, gdy oldest-job age stale rośnie, baza przekracza bezpieczną saturację albo retry wzmacnia awarię.
Open arrival model jest tu celowy: użytkownicy nie zwalniają automatycznie tylko dlatego, że system zwolnił.
