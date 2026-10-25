# Zadania: listy

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/21-linked-lists.md)

Listy są acykliczne, do 100 000 węzłów. Typ Node jest częścią szkieletu. Tożsamość węzłów ma znaczenie; brak elementu oznacza null.

## LIST-01 — odwróć listę

**Poziom:** L1
**Wymagania:** [listy](../patterns/21-linked-lists.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Odwróć wszystkie połączenia listy in-place i zwróć nową głowę. Użyj dokładnie istniejących węzłów, bez kopii i bez tablicy referencji. Pusta lista daje null. O(n) czasu, O(1) pamięci.

<details>
<summary>Wskazówka 1</summary>

Po przepięciu next tracisz dawny kierunek; co musisz zachować wcześniej?

</details>

<details>
<summary>Wskazówka 2</summary>

Rozdziel odwrócony prefiks od nieprzetworzonego sufiksu.

</details>

<details>
<summary>Wskazówka 3</summary>

W kroku zapisz dawny next, przepnij, przesuń obie granice.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:LIST-01 -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node reverseList(Node head) {
    throw new UnsupportedOperationException("TODO LIST-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:LIST-01 -->
```java
static void verify() {
    assert reverseList(null) == null;
    Node a = new Node(1), b = new Node(2), c = new Node(3); a.next=b; b.next=c;
    assert reverseList(a) == c;
    assert c.next == b && b.next == a && a.next == null;
    assert reverseList(c) == a && a.next == b && b.next == c && c.next == null;
    Node head = null;
    for (int i=0;i<100_000;i++) { Node item=new Node(i); item.next=head; head=item; }
    Node item=reverseList(head);
    for(int i=0;i<100_000;i++) { assert item!=null && item.value==i; item=item.next; }
    assert item==null;
}
```

## LIST-02 — stabilnie scal dwie listy

**Poziom:** L2
**Wymagania:** [listy](../patterns/21-linked-lists.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Dwie rozłączne listy są niemalejące. Scal je in-place w jedną niemalejącą listę z tych samych węzłów. Dla równych wartości najpierw pobieraj węzeł z pierwszej listy. Nie twórz kopii węzłów; pojedynczy pomocniczy węzeł-sentinel jest dozwolony i nie należy do wyniku. O(n+m) czasu, O(1) pamięci.

<details>
<summary>Wskazówka 1</summary>

Obie głowy są najmniejszymi pozostałymi wartościami swoich list.

</details>

<details>
<summary>Wskazówka 2</summary>

Utrzymuj ogon wyniku i dopinaj jeden istniejący węzeł.

</details>

<details>
<summary>Wskazówka 3</summary>

Równość rozstrzygaj na korzyść pierwszej listy; resztę niepustej listy podepnij na końcu.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:LIST-02 -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node mergeLists(Node first, Node second) {
    throw new UnsupportedOperationException("TODO LIST-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:LIST-02 -->
```java
static void verify() {
    assert mergeLists(null,null)==null;
    Node a=new Node(1), b=new Node(3), c=new Node(1), d=new Node(2);
    a.next=b; c.next=d;
    Node result=mergeLists(a,c);
    assert result==a && a.next==c && c.next==d && d.next==b && b.next==null;
    Node x=new Node(7); assert mergeLists(null,x)==x && x.next==null;
}
```

## LIST-03 — usuń n-ty od końca

**Poziom:** L2
**Wymagania:** [listy](../patterns/21-linked-lists.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Usuń n-ty węzeł liczony od końca (1 oznacza ostatni). Lista jest niepusta, 1≤n≤długość. Zwróć nową głowę, zachowaj pozostałe węzły i ich kolejność. Nie ma wymogu zmieniania next usuniętego węzła. Jedno przejście, O(n) czasu i O(1) pamięci.

<details>
<summary>Wskazówka 1</summary>

Jak utrzymać stałą odległość między dwoma wskaźnikami?

</details>

<details>
<summary>Wskazówka 2</summary>

Jeden wskaźnik ma wyprzedzać drugi o liczbę związaną z n.

</details>

<details>
<summary>Wskazówka 3</summary>

Sentinel przed głową ujednolica usunięcie pierwszego węzła i elementu w środku.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:LIST-03 -->
```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node removeNthFromEnd(Node head, int n) {
    throw new UnsupportedOperationException("TODO LIST-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:LIST-03 -->
```java
static void verify() {
    Node a=new Node(1), b=new Node(2), c=new Node(3); a.next=b; b.next=c;
    assert removeNthFromEnd(a,2)==a && a.next==c && c.next==null;
    assert removeNthFromEnd(a,2)==c;
    assert removeNthFromEnd(c,1)==null;
    Node x=new Node(4), y=new Node(5); x.next=y;
    assert removeNthFromEnd(x,1)==x && x.next==null;
}
```
