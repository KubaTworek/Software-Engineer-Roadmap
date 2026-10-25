# Zadania: fast and slow pointers

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/08-fast-slow.md)

Każdy szkielet ma własny minimalny typ Node. Wartość służy tylko do zbudowania
kontrprzykładów: równe wartości nie oznaczają tego samego węzła. Współbieżna
modyfikacja struktury nie występuje. Limit to 100 000 osiągalnych węzłów.

## FAST-01 — pierwszy węzeł cyklu

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/08-fast-slow.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Spotkanie dwóch prędkości nie musi być wejściem do cyklu.

</details>

<details>
<summary>Wskazówka 2</summary>

Porównaj drogę od głowy i od punktu spotkania.

</details>

<details>
<summary>Wskazówka 3</summary>

Po spotkaniu przesuwaj oba po jednym kroku; uzasadnij odległości modulo długości cyklu.

</details>

**Treść i kontrakt.** Zwróć referencję do pierwszego węzła cyklu osiągalnego
z head albo null, jeśli cyklu nie ma. Nie zmieniaj żadnego połączenia. Pusta
lista → null. Docelowo O(n) czasu, O(1) pamięci.

Kontekst: typ wejścia i nierozwiązana metoda.

<!-- exercise:FAST-01 -->
```java
static final class Node {
    final int value;
    Node next;
    Node(int value) { this.value = value; }
}

static Node cycleEntry(Node head) {
    throw new UnsupportedOperationException("TODO FAST-01");
}
```

Kontekst: wykonywalne testy tożsamości, nie wartości.

<!-- tests:FAST-01 -->
```java
static void verify() {
    assert cycleEntry(null) == null;
    Node a = new Node(7), b = new Node(7), c = new Node(7), d = new Node(7);
    a.next = b; b.next = c; c.next = d;
    assert cycleEntry(a) == null;
    d.next = b;
    assert cycleEntry(a) == b;
    assert a.next == b && b.next == c && c.next == d && d.next == b;
    d.next = a;
    assert cycleEntry(a) == a;
    Node self = new Node(1);
    self.next = self;
    assert cycleEntry(self) == self;
}
```

## FAST-02 — długość cyklu

**Poziom:** L2
**Wymagania:** [Karta techniki](../patterns/08-fast-slow.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Najpierw znajdź dowolny węzeł cyklu.

</details>

<details>
<summary>Wskazówka 2</summary>

Z niego wykonaj dokładnie jeden obieg.

</details>

<details>
<summary>Wskazówka 3</summary>

Nie doliczaj prefiksu; brak spotkania przed końcem daje zero.

</details>

**Treść i kontrakt.** Zwróć liczbę różnych węzłów w osiągalnym cyklu; prefiks
przed cyklem nie wlicza się. Lista bez cyklu i pusta lista dają 0. Bez mutacji
połączeń. Docelowo O(n) czasu, O(1) pamięci.

Kontekst: typ wejścia i nierozwiązana metoda.

<!-- exercise:FAST-02 -->
```java
static final class Node {
    Node next;
}

static int cycleLength(Node head) {
    throw new UnsupportedOperationException("TODO FAST-02");
}
```

Kontekst: wykonywalne testy.

<!-- tests:FAST-02 -->
```java
static void verify() {
    assert cycleLength(null) == 0;
    Node a = new Node(), b = new Node(), c = new Node(), d = new Node();
    a.next = b; b.next = c; c.next = d;
    assert cycleLength(a) == 0;
    d.next = b;
    assert cycleLength(a) == 3;
    d.next = a;
    assert cycleLength(a) == 4;
    d.next = d;
    assert cycleLength(a) == 1;
    assert a.next == b && b.next == c && c.next == d && d.next == d;
}
```

## FAST-03 — prawy środek listy

**Poziom:** L1
**Wymagania:** [Karta techniki](../patterns/08-fast-slow.md), [Java i koszty](../JAVA.md).

<details>
<summary>Wskazówka 1</summary>

Dwie prędkości zastępują osobne liczenie długości.

</details>

<details>
<summary>Wskazówka 2</summary>

Jeden wskaźnik idzie jeden krok, drugi dwa.

</details>

<details>
<summary>Wskazówka 3</summary>

Warunek zakończenia ma wskazać prawy środek przy parzystej długości.

</details>

**Treść i kontrakt.** Wejście jest listą bez cyklu. Zwróć referencję do środkowego
węzła; przy parzystej liczbie węzłów wybierz drugi z dwóch środkowych. Pusta lista
→ null. Nie licz długości w osobnym wstępnym przejściu i nie zmieniaj połączeń.
Docelowo O(n) czasu, O(1) pamięci.

Kontekst: typ wejścia i nierozwiązana metoda.

<!-- exercise:FAST-03 -->
```java
static final class Node {
    Node next;
}

static Node middleNode(Node head) {
    throw new UnsupportedOperationException("TODO FAST-03");
}
```

Kontekst: wykonywalne testy obu parzystości długości.

<!-- tests:FAST-03 -->
```java
static void verify() {
    assert middleNode(null) == null;
    Node a = new Node(), b = new Node(), c = new Node(), d = new Node(), e = new Node();
    assert middleNode(a) == a;
    a.next = b;
    assert middleNode(a) == b;
    b.next = c;
    assert middleNode(a) == b;
    c.next = d;
    assert middleNode(a) == c;
    d.next = e;
    assert middleNode(a) == c;
    assert a.next == b && b.next == c && c.next == d && d.next == e && e.next == null;
}
```
