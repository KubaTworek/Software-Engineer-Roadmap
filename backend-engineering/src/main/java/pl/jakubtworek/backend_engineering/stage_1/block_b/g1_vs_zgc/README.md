# Case 6 — G1 vs ZGC

## Wprowadzenie

Dobór Garbage Collectora w nowoczesnym JVM nie jest wyłącznie decyzją techniczną dotyczącą „wydajności GC”.

To przede wszystkim:
## decyzja architektoniczna

która wpływa na:
- latency systemu,
- throughput,
- koszt CPU,
- rozmiar heapu,
- stabilność SLA,
- zachowanie aplikacji pod loadem.

Najczęstszy błąd polega na myśleniu:

> „który GC jest szybszy?”

To niewłaściwe pytanie.

Poprawne pytanie brzmi:

> „jaki kompromis wybieramy?”

Ponieważ każdy collector optymalizuje inne właściwości systemu.

---

# Fundamentalny problem Garbage Collection

GC musi rozwiązać bardzo trudny problem:

- aplikacja stale alokuje obiekty,
- część obiektów szybko umiera,
- część żyje długo,
- heap ma ograniczony rozmiar,
- pamięć trzeba odzyskiwać,
- aplikacja nie może zatrzymać się na zbyt długo.

Największe napięcie występuje między:
- throughput,
- a latency.

---

# Throughput vs Latency

To najważniejszy kompromis.

## Throughput-oriented collector

stara się:
- minimalizować całkowity koszt GC,
- maksymalizować ilość wykonanej pracy aplikacji,
- zmniejszać overhead concurrent work.

Kosztem bywają:
- dłuższe pause times,
- większe jittery latency.

---

## Latency-oriented collector

stara się:
- minimalizować stop-the-world pauses,
- utrzymywać stabilne tail latency,
- redukować długie zatrzymania JVM.

Kosztem bywają:
- większy narzut CPU,
- większe zużycie pamięci,
- niższy throughput całkowity.

---

# G1 — kompromisowy collector general-purpose

## G1 GC

powstał jako:
- nowoczesny collector serwerowy,
- następca CMS,
- rozwiązanie „general-purpose”.

G1:
- dzieli heap na regiony,
- wykonuje kolekcję inkrementalnie,
- próbuje kontrolować pause times,
- równoważy throughput i latency.

To bardzo ważne:
> G1 nie jest stricte low-latency collector.

To collector kompromisowy.

---

# Jak działa G1

G1:
- dzieli heap na regiony,
- monitoruje ilość garbage w regionach,
- wybiera najbardziej opłacalne regiony do odzyskania.

Stąd nazwa:
## Garbage First

G1 próbuje:
- odzyskać jak najwięcej pamięci przy ograniczonym czasie pauzy.

To znacznie bardziej zaawansowane podejście niż klasyczne generacyjne kolektory.

---

# Pause times w G1

G1 wykonuje dużą część pracy współbieżnie, ale nadal posiada:
- stop-the-world young collections,
- evacuation pauses,
- remark phases,
- cleanup phases.

To oznacza:
- pause times nadal istnieją,
- mogą rosnąć wraz z heapem,
- mogą być niestabilne pod dużym loadem.

Dla wielu systemów:
- to nadal całkowicie akceptowalne.

Ale dla ultra-low-latency:
- może być niewystarczające.

---

# ZGC — collector low-latency

## ZGC

został zaprojektowany z zupełnie innym priorytetem.

Najważniejszy cel:
> ekstremalnie niskie pause times niezależnie od rozmiaru heapu.

ZGC:
- wykonuje niemal całą pracę współbieżnie,
- minimalizuje stop-the-world phases,
- utrzymuje bardzo małe pauzy nawet przy ogromnych heapach.

To fundamentalnie inna filozofia niż klasyczne throughput collectors.

---

# Colored pointers i load barriers

ZGC używa bardzo zaawansowanych mechanizmów:
- colored pointers,
- load barriers,
- concurrent relocation.

To pozwala:
- przenosić obiekty współbieżnie,
- bez długich stop-the-world pauses.

Ale ceną jest:
- bardziej złożona praca runtime,
- większy overhead CPU,
- dodatkowe bariery pamięciowe.

---

# Dlaczego ZGC potrzebuje więcej CPU

To częste zaskoczenie.

ZGC osiąga niskie latency dlatego, że:
- dużo pracy wykonuje współbieżnie,
- stale monitoruje heap,
- stale utrzymuje poprawność referencji,
- wykonuje concurrent relocation.

To oznacza:
- większy koszt runtime,
- większe zużycie CPU,
- czasami niższy throughput.

Czyli:
> ZGC nie „usuwa kosztu GC”.

On:
- przenosi koszt z długich pauz
- na ciągłą pracę współbieżną.

---

# Heap size a wybór collectora

Rozmiar heapu ma ogromne znaczenie.

Przy:
- małych heapach,
- umiarkowanym SLA,
- klasycznych workloadach backendowych,

G1 często działa bardzo dobrze.

Natomiast przy:
- ogromnych heapach,
- dziesiątkach / setkach GB RAM,
- systemach low-latency,
- wymaganiach tail latency,

ZGC zaczyna mieć ogromną przewagę.

Dlaczego?

Ponieważ klasyczne pause times zwykle rosną wraz z:
- ilością live data,
- kosztem relocation,
- długością marking phases.

ZGC został zaprojektowany właśnie po to, by ten problem ograniczyć.

---

# Tail latency — prawdziwy problem produkcyjny

Średni czas odpowiedzi często nie ma dużego znaczenia.

Kluczowy jest:
## tail latency

czyli:
- p99,
- p99.9,
- najgorsze przypadki opóźnień.

GC pauses bardzo często ujawniają się właśnie tam.

Aplikacja może mieć:
- świetnie wyglądające średnie latency,
- a jednocześnie fatalne p99 przez sporadyczne długie GC pauses.

To dlatego:
- systemy tradingowe,
- realtime analytics,
- gaming backends,
- ultra-low-latency APIs

często preferują collectory typu ZGC.

---

# Dlaczego „niższe pause time” nie oznacza „szybszej aplikacji”

To bardzo ważne.

ZGC może mieć:
- znacznie lepsze latency,
- ale gorszy throughput.

G1 może mieć:
- dłuższe pause times,
- ale lepszą całkowitą przepustowość.

To zależy od workloadu.

Przykład:
- batch processing,
- analytics,
- duże ETL,
- throughput-heavy workloads

często bardziej korzystają z:
- wysokiego throughput,
- niż ultra-niskiego latency.

---

# Allocation rate nadal ma ogromne znaczenie

Collector nie usuwa problemu nadmiernych alokacji.

Jeżeli aplikacja:
- produkuje ogromny allocation churn,
- stale zalewa young generation,
- tworzy miliony krótkotrwałych obiektów,

to nawet nowoczesny collector:
- nadal wykonuje ogromną pracę.

To bardzo ważna intuicja:
> Zmiana collectora nie zastępuje poprawy allocation profile aplikacji.

---

# Co pokazuje to case study

Ten eksperyment celowo symuluje:
- allocation pressure,
- medium-lived objects,
- stabilny live set,
- latency-sensitive thread.

Dzięki temu można obserwować:
- throughput,
- pause times,
- CPU overhead,
- heap occupancy,
- allocation rate,
- tail latency.

I właśnie wtedy różnice między:
- G1,
- a ZGC

stają się wyraźnie widoczne.

---

# Dlaczego JFR i GC logs są kluczowe

GC należy analizować empirycznie.

Nie wystarczy:
- przeczytać benchmark z internetu,
- spojrzeć na średni response time,
- porównać jeden wykres.

Potrzebne są:
- GC logs,
- JFR,
- allocation profiling,
- latency histograms,
- analiza live set,
- analiza CPU overhead.

Dopiero wtedy można podejmować sensowne decyzje architektoniczne.

---

# Najczęstszy błąd tuningowy

Najczęstszy błąd wygląda tak:

> „Mamy GC pauses, zmieńmy collector”

Podczas gdy prawdziwy problem to:
- gigantyczny allocation rate,
- niepotrzebne obiekty,
- błędny cache design,
- nadmierny churn danych,
- źle dobrany heap sizing.

Collector nie naprawia złego modelu pamięci aplikacji.

---

# Najważniejsza intuicja praktyczna

Najbardziej praktyczny wniosek brzmi:

> wybór GC to wybór kompromisu między latency, throughput, CPU overhead i kosztami pamięci.

Nie istnieje:
- „najlepszy GC”.

Istnieje tylko:
- collector najlepiej dopasowany do konkretnego SLA i workloadu.

---

# Najważniejsze wnioski

Najbardziej użyteczny model mentalny wygląda tak:

- GC jest kompromisem między throughput i latency,
- G1 jest collectorem general-purpose balansującym oba światy,
- ZGC jest collectorem skoncentrowanym na ultra-niskich pause times,
- ZGC osiąga niski latency kosztem większego overheadu współbieżnego,
- duże heapy wzmacniają znaczenie nowoczesnych low-latency collectorów,
- tail latency jest często ważniejsze niż średni response time,
- allocation rate nadal pozostaje krytyczną metryką niezależnie od collectora,
- zmiana GC nie naprawia problemów allocation churn,
- decyzje GC powinny wynikać z SLA i charakterystyki workloadu,
- GC tuning wymaga analizy JFR, GC logs i rzeczywistego zachowania systemu.