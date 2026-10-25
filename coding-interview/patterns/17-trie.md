# 17. Trie — wspólne prefiksy zamiast całych kluczy

[Katalog](../README.md) · [Zadania](../exercises/17-trie.md)

## Problem i kontrakt

Słownik małych liter a–z obsługuje dodanie słowa, sprawdzenie całego słowa
i istnienia prefiksu. Duplikat dodania jest idempotentny. Puste słowo jest
dozwolone; pusty prefiks istnieje także w pustym słowniku jako ścieżka korzenia.

## Od brute force do niezmiennika

Przegląd wszystkich słów dla każdego prefiksu powtarza porównywanie tych samych
początków. Trie współdzieli ich ścieżki. **Niezmiennik:** po k literach bieżący
węzeł reprezentuje dokładnie prefiks długości k. Znacznik terminal odróżnia
istniejącą ścieżkę od końca dodanego słowa.

## Przykład krok po kroku

Dodaj „kot”: powstaje korzeń–k–o–t, terminal tylko w t. „ko” jest prefiksem,
ale jeszcze nie słowem. Dodanie „ko” ustawia terminal w o bez zmiany ścieżki „kot”.

## Kod Java

Kontekst: składowe klasy. Kontrakt alfabetu jest gwarancją wejścia.

```java
static final class Trie {
    static final class Node { Node[] children = new Node[26]; boolean terminal; }
    final Node root = new Node();
    void add(String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            int index = word.charAt(i) - 'a';
            if (node.children[index] == null) { node.children[index] = new Node(); }
            node = node.children[index];
        }
        // Ścieżka oznacza prefiks; dopiero ten znacznik oznacza całe słowo.
        node.terminal = true;
    }
    Node walk(String text) {
        Node node = root;
        for (int i = 0; i < text.length(); i++) {
            node = node.children[text.charAt(i) - 'a'];
            if (node == null) { return null; }
        }
        return node;
    }
    boolean contains(String word) { Node node = walk(word); return node != null && node.terminal; }
    boolean hasPrefix(String prefix) { return walk(prefix) != null; }
}
```

## Koszt i granice techniki

Operacja O(L), gdzie L jest długością argumentu; pamięć O(26·S) dla co najwyżej
S + 1 węzłów, S to suma długości dodawanych słów. Dla stałego alfabetu O(S).
Tablica w każdym węźle ma duży koszt stały. Mapa dzieci lepiej pasuje do rzadkiego,
dużego alfabetu. Trie nie rozwiązuje automatycznie rankingu autocomplete.

## Kontrprzykład i scenariusze

Samo istnienie węzła „ko” po dodaniu „kot” nie oznacza obecności słowa „ko”.
Usuwając słowo, nie wolno odciąć ścieżki potrzebnej dłuższemu słowu.

Kontekst: wykonywane asercje.

```java
static void verify() {
    Trie trie = new Trie();
    assert trie.hasPrefix("") && !trie.contains("");
    trie.add("kot");
    assert trie.hasPrefix("ko") && !trie.contains("ko");
    trie.add("ko"); trie.add("kot"); trie.add("");
    assert trie.contains("ko") && trie.contains("kot") && trie.contains("");
    assert !trie.hasPrefix("pies") && !trie.contains("kotek");
}
```

## Ćwiczenie

Policz różne słowa o danym prefiksie. Ustal, czy duplikaty dodania mają zmieniać licznik.
