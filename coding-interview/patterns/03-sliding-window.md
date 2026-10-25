# 03. Sliding window — utrzymuj poprawny spójny fragment

[Katalog](../README.md) · [Prefix sum](04-prefix-sum.md)

## Problem i kontrakt

Zwróć długość najdłuższego spójnego fragmentu `String text` bez powtarzających
się znaków. W tej karcie wejście zawiera wyłącznie ASCII: wartości 0–127.
Pusty napis daje 0. Nie kopiujemy podnapisów i nie zmieniamy wejścia.

## Od brute force do niezmiennika

Dla każdego początku można rozszerzać koniec i budować zbiór znaków od nowa:
O(n²) czasu. Okno pozwala przenosić granice tylko do przodu, zachowując stan.

**Niezmiennik:** po obsłudze `right` zakres `[left, right]` nie ma powtórzeń,
a `left` jest najmniejszym dopuszczalnym początkiem dla tego końca. Jeżeli znak
był już w oknie, nowy początek musi znaleźć się za jego poprzednim wystąpieniem.
Maksimum chroni przed cofnięciem `left`, gdy poprzednie wystąpienie było poza oknem.

## Przykład krok po kroku

Dla `abba`: `a` daje długość 1, `ab` długość 2; drugie `b` przesuwa `left` z 0
na 2. Ostatnie `a` miało indeks 0, więc `left` zostaje 2. Okno `ba` ma długość 2.
Cofnięcie początku na 1 dopuściłoby niepoprawne `bba` długości 3.

## Kod Java

Kontekst: składowa klasy, `java.util.*`. Metoda jawnie odrzuca wejście poza ASCII.

```java
static int longestUniqueAscii(String text) {
    int[] lastSeen = new int[128];
    Arrays.fill(lastSeen, -1);
    int left = 0;
    int best = 0;
    for (int right = 0; right < text.length(); right++) {
        char symbol = text.charAt(right);
        if (symbol >= lastSeen.length) {
            throw new IllegalArgumentException("Wymagane ASCII");
        }
        // Poprzednie wystąpienie poza oknem nie może cofnąć left.
        left = Math.max(left, lastSeen[symbol] + 1);
        lastSeen[symbol] = right;
        best = Math.max(best, right - left + 1);
    }
    return best;
}
```

## Koszt i granice techniki

Czas O(n), pamięć O(1) dla stałego alfabetu 128 znaków, wynik O(1).
Dla innego alfabetu dobierz inną reprezentację i zdefiniuj jednostkę znaku.
Okno stałej długości ma inny warunek niż okno naprawiane przez przesuwanie lewej
granicy. Dla sum nie można automatycznie założyć, że usuwanie elementu zmniejsza
sumę: liczby ujemne łamią taką monotoniczność.

## Kontrprzykład i scenariusze

`abba` obala przypisanie `left = lastSeen[symbol] + 1` bez maksimum.
To zadanie o spójnym fragmencie; usunięcie dowolnych znaków rozwiązuje inny problem.

Kontekst: wykonywane asercje.

```java
static void verify() {
    assert longestUniqueAscii("abba") == 2;
    assert longestUniqueAscii("abcabcbb") == 3;
    assert longestUniqueAscii("aaaa") == 1;
    assert longestUniqueAscii("") == 0;
    assert longestUniqueAscii("a b") == 3;
    boolean rejected = false;
    try { longestUniqueAscii("ą"); }
    catch (IllegalArgumentException expected) { rejected = true; }
    assert rejected;
}
```

## Ćwiczenie

Znajdź najdłuższy fragment z najwyżej k różnymi znakami. Zastąp ostatnie indeksy
licznikami i wyjaśnij, kiedy usuwać klucz o liczności zero.
