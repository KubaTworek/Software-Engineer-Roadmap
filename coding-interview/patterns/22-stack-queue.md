# 22. Stos i kolejka — kolejność obsługi stanu

[Katalog](../README.md) · [Zadania](../exercises/22-stack-queue.md)

## Problem i kontrakt

Zaimplementuj kolejkę FIFO liczb int przez dwa stosy. add dopisuje, remove usuwa
najstarszy, peek go odczytuje. Odczyt pustej kolejki zgłasza NoSuchElementException.
To ćwiczenie sekwencyjne, bez współbieżności.

## Od brute force do niezmiennika

Usuwanie indeksu zero z ArrayList przesuwa O(n) wartości. Dwa stosy rozdzielają
nowe elementy od gotowych do odczytu. **Niezmiennik:** kolejność FIFO to stos out
od szczytu do dna, następnie stos in od dna do szczytu. Przenosimy in do out
tylko wtedy, gdy out jest pusty; odwrócenie przywraca kolejność najstarszy pierwszy.

## Przykład krok po kroku

add(1), add(2): in ma szczyt 2. remove przenosi do out, którego szczytem jest 1.
add(3) trafia do in. Następne remove bierze 2 ze starego out, dopiero kolejne
przenosi 3. Bez warunku pustego out nowszy element mógłby wyprzedzić starszy.

## Kod Java

Kontekst: składowa klasy, java.util.*. ArrayDeque służy jako stos.

```java
static final class IntQueue {
    final Deque<Integer> in = new ArrayDeque<>(), out = new ArrayDeque<>();
    void add(int value) { in.push(value); }
    void prepare() {
        // Nie przenosimy nowych elementów przed starsze czekające w out.
        if (out.isEmpty()) { while (!in.isEmpty()) { out.push(in.pop()); } }
    }
    int remove() { prepare(); return out.pop(); }
    int peek() { prepare(); return out.getFirst(); }
}
```

## Koszt i granice techniki

add jest amortyzowane O(1), remove i peek również w serii operacji; pojedyncze
przeniesienie może kosztować O(n). Każdy element przechodzi in→out najwyżej raz.
Pamięć O(n). Stos LIFO pasuje do domykania nawiasów i interpretacji wyrażeń;
kolejka FIFO do przetwarzania w kolejności zgłoszeń i BFS.

## Kontrprzykład i scenariusze

W wyrażeniu RPN odejmowanie wymaga kolejności: pierwszy zdjęty operand jest
prawy. Dla tokenów 3,2,- wynik to 1, a nie -1. FIFO nie zastępuje tutaj stosu.

Kontekst: wykonywane asercje kolejki.

```java
static void verify() {
    IntQueue queue = new IntQueue();
    queue.add(1); queue.add(2);
    assert queue.remove() == 1;
    queue.add(3);
    assert queue.peek() == 2 && queue.peek() == 2;
    assert queue.remove() == 2 && queue.remove() == 3;
    boolean rejected = false;
    try { queue.remove(); } catch (NoSuchElementException expected) { rejected = true; }
    assert rejected;
    for (int i = 0; i < 100_000; i++) { queue.add(i); }
    for (int i = 0; i < 100_000; i++) { assert queue.remove() == i; }
}
```

## Ćwiczenie

Obsłuż trzy rodzaje nawiasów. Nazwij, co oznacza szczyt stosu po przeczytaniu prefiksu.
