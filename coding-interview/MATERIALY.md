# Materiały do nauki

[Mapa modułu](README.md) · [Plan ćwiczeń](PLAN.md)

Wybierz jedno źródło do teorii i od razu rozwiąż zadanie z odpowiedniej karty.
Poniższe materiały uzupełniają własne wyjaśnienia modułu. Treści zadań i rozwiązania
opisujemy własnymi słowami; nie kopiujemy cudzych editoriali.

## Zadania i teoria

- [LeetCode — Top Interview 150](https://leetcode.com/studyplan/top-interview-150/)
  — oficjalny zestaw 150 zadań do przygotowania rozmów. Używaj go jako banku
  ćwiczeń po poznaniu techniki; kolejność naszego planu jest własną propozycją.
- [MIT OpenCourseWare — 6.006, Spring 2020](https://ocw.mit.edu/courses/6-006-introduction-to-algorithms-spring-2020/)
  — wykłady, notatki i zadania o strukturach danych, algorytmach oraz analizie.
  Sięgaj tu po uzasadnienie mechanizmu i kosztu. Przełóż poznany algorytm na Javę
  i własny kontrakt zamiast kopiować składnię innego języka.

To wystarcza na początek: karta → własna próba → konkretna część wykładu → ponowna
próba bez rozwiązania. Nie potrzebujesz równocześnie kilku płatnych kursów.

## Dokumentacja Javy 21

| Źródło | Co sprawdzić | Gdzie zastosować |
| --- | --- | --- |
| [HashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html) | założenia stałego kosztu i brak porządku | Two Sum, prefix sum |
| [ArrayDeque](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayDeque.html) | końce kolejki, stos, brak null, amortyzacja | monotonic stack, BFS, topological sort |
| [PriorityQueue](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/PriorityQueue.html) | najmniejszy element na szczycie, koszt operacji, kolejność iteratora | top-k |

## Jak sprawdzać, czy lektura pomogła

Po materiale wyjaśnij bez notatek: jaki stan przechowujesz, dlaczego wolno odrzucić
kandydata, kiedy technika zawodzi i co zmienia nowy warunek zadania. Następnie
zapisz minimalny kontrprzykład dla błędnego wariantu. Obejrzenie rozwiązania jest
pomocą w nauce; samodzielne rozwiązanie wariantu jest osobnym krokiem.
