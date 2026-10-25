# 21. Listy — zmiana referencji bez utraty ogona

[Katalog](../README.md) · [Zadania](../exercises/21-linked-lists.md)

## Problem i kontrakt

Odwróć acykliczną listę jednokierunkową in-place. Zwróć nową głowę, użyj dokładnie
tych samych węzłów. null oznacza pustą listę. Nie twórz kopii węzłów ani tablicy wartości.

## Od brute force do niezmiennika

Zapisanie węzłów w tablicy pozwala odtworzyć kolejność w O(n) pamięci. Wystarczą
referencje do odwróconego prefiksu i niezmienionej reszty. **Niezmiennik:** previous
jest głową odwróconego prefiksu, current głową jeszcze nieprzetworzonego sufiksu.
Zanim zmienimy current.next, zapisujemy dawny następnik, aby nie zgubić sufiksu.

## Przykład krok po kroku

A→B→C: zapisz B, ustaw A.next=null; previous=A, current=B. Następnie zapisz C,
ustaw B.next=A. Ostatni krok daje C→B→A→null. Węzły są te same, kierunki inne.

## Kod Java

Kontekst: składowe klasy. Mutacja jest tu częścią kontraktu.

```java
static final class Node {
    final int value; Node next;
    Node(int value) { this.value = value; }
}
static Node reverse(Node head) {
    Node previous = null, current = head;
    while (current != null) {
        // Dawny następnik za chwilę przestanie być dostępny przez current.next.
        Node following = current.next;
        current.next = previous;
        previous = current;
        current = following;
    }
    return previous;
}
```

## Koszt i granice techniki

Czas O(n), pamięć O(1). Głęboka rekurencja nie jest potrzebna. W scalaniu dwóch
list analogicznie pilnuj ogona wyniku i nieprzetworzonych głów; przy równości
wybór z pierwszej listy może zapewnić stabilność. Listy muszą być rozłączne,
jeśli przepinasz ich węzły. Wspólny ogon może utworzyć cykl przy naiwnym scalaniu.

## Kontrprzykład i scenariusze

Po ustawieniu current.next=previous odczyt current.next nie zwraca już dawnego
następnika. Zapisanie go dopiero wtedy gubi pozostałe węzły.

Kontekst: wykonywane asercje tożsamości i dużego wejścia.

```java
static void verify() {
    Node a = new Node(1), b = new Node(2), c = new Node(3);
    a.next = b; b.next = c;
    assert reverse(a) == c && c.next == b && b.next == a && a.next == null;
    assert reverse(c) == a && a.next == b && b.next == c && c.next == null;
    assert reverse(null) == null;
    Node head = null;
    for (int i = 0; i < 100_000; i++) { Node node = new Node(i); node.next = head; head = node; }
    Node node = reverse(head);
    for (int i = 0; i < 100_000; i++) { assert node != null && node.value == i; node = node.next; }
    assert node == null;
}
```

## Ćwiczenie

Odwróć tylko zadany zakres indeksów. Wskaż cztery połączenia na granicy zakresu,
zanim zaczniesz przepinać referencje.
