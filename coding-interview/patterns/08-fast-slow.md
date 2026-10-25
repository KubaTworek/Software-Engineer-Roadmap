# 08. Fast and slow pointers — wykryj cykl następników

[Katalog](../README.md) · [Grafy](11-graphs-bfs.md)

## Problem i kontrakt

Sprawdź, czy jednokierunkowa lista zawiera cykl. Węzeł ma tylko referencję `next`;
porównujemy tożsamość węzłów, nie ich wartości. `null` oznacza pustą listę lub
koniec. Metoda nie zmienia połączeń. Struktura pozostaje stała podczas przejścia.

## Od brute force do niezmiennika

Zapamiętanie odwiedzonych referencji wymaga O(n) pamięci i O(n) oczekiwanego czasu
przy zbiorze haszującym. Dwie prędkości pozwalają wykryć powtórzenie bez zbioru.

**Niezmiennik:** po t iteracjach slow wykonał t kroków, a fast 2t, o ile istnieją
następniki. Bez cyklu fast dojdzie do końca. W cyklu długości c odległość między
wskaźnikami zmienia się o jeden modulo c, więc po wejściu obu do cyklu muszą się
spotkać w ciągu najwyżej c kolejnych kroków.

## Przykład krok po kroku

Lista `A → B → C → B`: po pierwszym kroku slow jest w B, fast w C; po drugim
slow jest w C, fast znowu w C. Wykrywamy cykl. Dla `A → B → null` fast dociera
do null. Początkowa równość obu wskaźników nie jest dowodem cyklu.

## Kod Java

Kontekst: składowe klasy. Minimalny typ Node jest częścią dokumentu.

```java
static final class Node {
    Node next;
}

static boolean hasCycle(Node head) {
    Node slow = head;
    Node fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        // Porównujemy dopiero po ruchu, nie w stanie początkowym.
        if (slow == fast) {
            return true;
        }
    }
    return false;
}
```

## Koszt i granice techniki

Czas O(n), gdzie n to liczba różnych węzłów osiągalnych z head; pamięć i wynik
O(1). Technika wymaga jednoznacznego następnika. Nie zastępuje wykrywania cyklu
w dowolnym grafie z rozgałęzieniami ani w liście zmienianej współbieżnie.

## Kontrprzykład i scenariusze

Porównanie przed pierwszym ruchem zgłasza cykl nawet dla pojedynczego węzła
z `next = null`. Brak kontroli `fast.next` powoduje wyjątek przy końcu listy.

Kontekst: wykonywane asercje na tożsamości referencji.

```java
static void verify() {
    assert !hasCycle(null);
    Node a = new Node();
    Node b = new Node();
    Node c = new Node();
    assert !hasCycle(a);
    a.next = b;
    b.next = c;
    assert !hasCycle(a);
    c.next = b;
    assert hasCycle(a);
    Node self = new Node();
    self.next = self;
    assert hasCycle(self);
}
```

## Ćwiczenie

Znajdź pierwszy węzeł cyklu. Po spotkaniu przenieś jeden wskaźnik na head,
a oba przesuwaj po jednym kroku. Wyprowadź uzasadnienie z długości prefiksu
przed cyklem i odległości liczonych modulo jego długość.
