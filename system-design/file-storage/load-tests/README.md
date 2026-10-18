# Eksperymenty wydajnościowe

Skrypt k6 rozróżnia model otwarty (`arrival-rate`) od liczby wirtualnych użytkowników. Dzięki temu wolniejsza odpowiedź nie zmniejsza sztucznie oferowanego obciążenia i łatwiej zauważyć saturation.

```powershell
$env:TOKEN = "..."
$env:FILE_ID = "..."
$env:TEST_TYPE = "load" # load | stress | spike | soak
k6 run .\load-tests\file-storage.js
```

Domyślnie 20% iteracji wykonuje dodatkowo pobranie pierwszych 64 KiB przez
`Range`. Proporcję można zmienić przez `DOWNLOAD_PERCENT`; pozwala to osobno
obserwować lekki control plane oraz kosztowniejszy data plane. Test korzysta z
przygotowanych danych, aby nie mieszać kosztu setupu z badaną ścieżką.

Przed eksperymentem zapisz capacity instancji, pulę połączeń, limity MinIO, rozmiar danych i oczekiwany RPS. Obserwuj p95/p99, error rate, aktywne połączenia DB, backlog kwarantanny/outboxa, CPU, GC i przepustowość storage.

Przerwij test, jeżeli error rate przekracza 1%, p99 przekracza SLO przez ponad minutę, kolejka rośnie bez możliwości odrobienia albo zależność osiąga twardy limit. Soak ma wykrywać wycieki i powolną degradację; nie należy uruchamiać go przeciw produkcji bez osobnej zgody.
