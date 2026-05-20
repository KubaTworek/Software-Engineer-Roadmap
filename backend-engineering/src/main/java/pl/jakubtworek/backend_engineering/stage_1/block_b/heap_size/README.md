# Case 7 — Strojenie sterty: `-Xms` / `-Xmx` i przewidywalność GC

## Wprowadzenie

Jedną z najbardziej klasycznych rekomendacji tuningowych JVM jest ustawienie:

```text
-Xms == -Xmx
```

czyli:
- początkowy rozmiar heapu,
- oraz maksymalny rozmiar heapu

mają tę samą wartość.

Na pierwszy rzut oka może wydawać się to dziwne.

Przecież:
- dynamiczne rozszerzanie heapu
  wydaje się wygodne,
- JVM „sama dopasuje pamięć”,
- aplikacja „użyje tylko tyle RAM, ile potrzebuje”.

W praktyce jednak:
> dynamiczne resize’owanie heapu ma koszt.

I właśnie ten koszt wpływa na:
- przewidywalność GC,
- stabilność latency,
- zachowanie JVM pod loadem,
- czas reakcji aplikacji,
- jittery wydajnościowe.

---

# Heap JVM nie jest statyczny

To bardzo ważna obserwacja.

Heap JVM może:
- rosnąć,
- kurczyć się,
- zmieniać ilość committed memory,
- zmieniać rozkład regionów.

Jeżeli:

```text
-Xms256m
-Xmx2g
```

to JVM:
- startuje z małym heapem,
- a następnie rozszerza go w miarę potrzeb.

Teoretycznie brzmi to rozsądnie.

Ale w praktyce:
- każda zmiana rozmiaru heapu to dodatkowa praca runtime.

---

# Reserved memory vs committed memory

To kluczowe rozróżnienie.

JVM zwykle:
- rezerwuje przestrzeń adresową,
- ale commit pamięci następuje stopniowo.

Czyli:
- `-Xmx` oznacza maksymalny możliwy heap,
- ale nie cały heap musi być fizycznie zaalokowany od początku.

Natomiast:
- `-Xms`
  określa początkowy committed heap.

Dlatego:

```text
-Xms2g -Xmx2g
```

oznacza:
- JVM od razu commit’uje cały heap,
- nie musi go później dynamicznie rozszerzać.

---

# Dlaczego heap resizing ma koszt

Rozszerzanie heapu nie jest darmowe.

Może oznaczać:
- dodatkową synchronizację,
- commit nowych stron pamięci,
- aktualizację struktur GC,
- zmianę region layout,
- dodatkową pracę OS,
- większy jitter runtime.

Przy wysokim allocation pressure:
- takie operacje mogą pojawiać się w bardzo niepożądanych momentach.

W systemach low-latency:
- nawet sporadyczny resize heapu
  może powodować zauważalne spike’i latency.

---

# Problem przewidywalności

To najważniejszy aspekt tego case study.

Wydajność produkcyjna bardzo często zależy bardziej od:
- stabilności,
- przewidywalności,
- tail latency,

niż od średniego throughput.

Dynamiczny heap powoduje, że:
- zachowanie GC zmienia się runtime,
- zmienia się ilość dostępnej pamięci,
- zmienia się częstotliwość collection,
- zmienia się pressure na young/old generation.

To utrudnia:
- stabilne SLA,
- przewidywalność pause times,
- analizę wydajności.

---

# Dlaczego równe `-Xms` i `-Xmx` pomagają

Jeżeli:

```text
-Xms == -Xmx
```

to JVM:
- nie rozszerza heapu runtime,
- nie kurczy heapu runtime,
- pracuje na stabilnym rozmiarze pamięci.

To daje:
- bardziej przewidywalny GC,
- stabilniejszy allocation behavior,
- mniejsze jittery,
- bardziej powtarzalne pause times.

Dlatego:
> jest to klasyczna rekomendacja dla systemów produkcyjnych.

---

# To nie jest „optymalizacja throughput”

To ważne rozróżnienie.

Ustawienie:

```text
-Xms == -Xmx
```

bardzo często:
- nie zwiększa drastycznie throughput.

Największy efekt dotyczy:
## przewidywalności runtime.

To jest typowy tuning:
- stabilności systemu,
- a nie maksymalizacji benchmark score.

---

# Dlaczego mały `-Xms` bywa problematyczny

Przy:

```text
-Xms256m
-Xmx8g
```

JVM:
- startuje bardzo małym heapem,
- szybko doświadcza allocation pressure,
- musi wielokrotnie rozszerzać pamięć.

To może powodować:
- częstsze GC na początku życia aplikacji,
- niestabilny startup,
- większe pause variability,
- większe jittery latency.

Czasem wygląda to tak, jakby:
- aplikacja „rozgrzewała się pamięciowo”
  przez długi czas.

---

# Heap shrinking również ma koszt

Nie tylko rozszerzanie heapu jest problemem.

Niektóre collectory mogą:
- oddawać pamięć do systemu,
- zmniejszać committed heap,
- reorganizować regiony.

Jeżeli workload jest zmienny:
- heap może cyklicznie rosnąć i maleć.

To prowadzi do:
- niestabilnego runtime behavior,
- dodatkowej pracy GC,
- większej zmienności latency.

---

# Dlaczego problem ujawnia się głównie pod loadem

W małych aplikacjach:
- różnice mogą być prawie niewidoczne.

Ale pod:
- dużym allocation rate,
- dużą liczbą requestów,
- burst traffic,
- dużym live set,
- systemami low-latency,

heap resizing staje się dużo bardziej odczuwalny.

Właśnie dlatego:
- tuning heap sizing
  jest ważny głównie w systemach produkcyjnych.

---

# G1 i dynamiczne resize’owanie

G1 bardzo intensywnie operuje na regionach heapu.

Zmiany committed heap wpływają na:
- liczbę regionów,
- evacuation behavior,
- collection heuristics,
- mixed collections,
- reserve memory pressure.

To oznacza, że:
- dynamiczny heap może wpływać na decyzje G1 runtime.

Efekt często nie jest katastrofalny,
ale:
- przewidywalność GC maleje.

---

# ZGC i duże sterty

ZGC jest bardziej przygotowany na:
- ogromne heapy,
- dynamiczne memory management,
- concurrent memory handling.

Ale nawet tam:
- stabilny heap sizing
  ułatwia przewidywalne zachowanie systemu.

Szczególnie przy:
- dużych SLA latency,
- dużym allocation churn,
- środowiskach cloudowych.

---

# Koszt pamięci vs przewidywalność

Równe:

```text
-Xms == -Xmx
```

mają też koszt.

Aplikacja:
- rezerwuje więcej pamięci od początku,
- może zwiększać footprint kontenera,
- może utrudniać oversubscription hosta.

Dlatego nie zawsze jest to optymalne dla:
- małych mikroserwisów,
- środowisk cost-sensitive,
- serverless,
- workloadów burstowych.

To kolejny kompromis:
- przewidywalność vs footprint pamięci.

---

# Dlaczego eksperyment warto analizować z GC logami

Same czasy wykonania często niewiele pokazują.

Najciekawsze rzeczy widać dopiero w:
- GC logs,
- JFR,
- heap committed charts,
- allocation statistics.

W szczególności warto obserwować:
- committed heap growth,
- resize events,
- pause consistency,
- young GC frequency,
- allocation stalls.

---

# Najczęstszy błąd tuningowy

Bardzo częsty błąd wygląda tak:

> „Heap się rozszerza, więc JVM dobrze zarządza pamięcią”

Nie zawsze.

Czasem oznacza to:
- zbyt agresywny allocation rate,
- zbyt mały initial heap,
- niestabilny workload,
- nieprzewidywalny runtime behavior.

Dynamiczny heap nie jest automatycznie „lepszy”.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> równe `-Xms` i `-Xmx` nie służą głównie maksymalizacji wydajności, lecz stabilizacji zachowania JVM.

To tuning:
- przewidywalności,
- stabilności GC,
- redukcji jitterów,
- poprawy consistency systemu pod loadem.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- heap JVM może dynamicznie rosnąć i maleć,
- resize heapu ma realny koszt runtime,
- dynamiczne rozszerzanie pamięci pogarsza przewidywalność GC,
- równe `-Xms` i `-Xmx` stabilizują zachowanie JVM,
- poprawa dotyczy głównie consistency i latency stability,
- mały initial heap może powodować częste resize events,
- heap shrinking również może generować overhead,
- tuning heap sizing jest kompromisem między footprintem pamięci a przewidywalnością,
- GC należy analizować razem z JFR i GC logs,
- stabilność runtime często jest ważniejsza niż maksymalny throughput.