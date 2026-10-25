# Diagnozowanie błędów — osiem zadań

[Zasady pracy i uruchamianie](README.md)

Wszystkie implementacje początkowo kompilują się, ale naruszają kontrakt. Poniższe
verify() są testami wymaganej poprawnej wersji, więc stan początkowy daje FAIL.
Importy java.util.* zapewnia skrypt. Nie ma celowych błędów składni.

## BUG-01 — granica wystąpień

**Poziom:** L1
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Dla niemalejącego int[] zwróć pierwszy indeks z wartością co najmniej target albo długość tablicy. Puste wejście daje 0. Bez mutacji. Docelowo O(log(n+1)) czasu. Znajdź najmniejsze wejście ujawniające złą granicę.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-01 -->
```java
static int lowerBound(int[] sorted, int target) {
    int left = 0, right = sorted.length;
    while (left < right) { int mid = left+(right-left)/2; if (sorted[mid] <= target) { left = mid+1; } else { right = mid; } }
    return left;
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-01 -->
```java
static void verify() {
    assert lowerBound(new int[0],3)==0;
    assert lowerBound(new int[] {1,3},2)==1;
    assert lowerBound(new int[] {2},2)==0;
    assert lowerBound(new int[] {1,2,2,4},2)==1;
    assert lowerBound(new int[] {1,2},8)==2;
}
```

## BUG-02 — suma dwóch wartości

**Poziom:** L1
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Dla niemalejącej tablicy int sprawdź istnienie dwóch różnych indeksów o matematycznej sumie target typu int. Bez mutacji; O(n) czasu i O(1) pamięci. Przeanalizuj zakres obliczenia, nie tylko typ zwracany.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-02 -->
```java
static boolean hasPair(int[] values, int target) {
    int left=0,right=values.length-1;
    while(left<right) {
        int sum=values[left]+values[right];
        if(sum==target) { return true; }
        if(sum<target) { left++; } else { right--; }
    }
    return false;
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-02 -->
```java
static void verify() {
    assert hasPair(new int[] {1,2,4},6);
    assert !hasPair(new int[] {3},6);
    assert !hasPair(new int[0],0);
    assert !hasPair(new int[] {Integer.MAX_VALUE,Integer.MAX_VALUE},-2);
    assert hasPair(new int[] {Integer.MIN_VALUE,Integer.MAX_VALUE},-1);
}
```

## BUG-03 — liczba spójnych fragmentów

**Poziom:** L2
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Policz niepuste spójne fragmenty int[] o matematycznej sumie target typu int. Liczby ujemne i zera dozwolone, n≤100 000, wynik long, bez mutacji. Oczekiwane O(n) czasu. Wyjaśnij znaczenie stanu przed pierwszym elementem.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-03 -->
```java
static long countFragments(int[] values, int target) {
    Map<Long,Long> counts=new HashMap<>(); long prefix=0,result=0;
    for(int value:values) { prefix+=value; result+=counts.getOrDefault(prefix-target,0L); counts.merge(prefix,1L,Long::sum); }
    return result;
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-03 -->
```java
static void verify() {
    assert countFragments(new int[0],0)==0;
    assert countFragments(new int[] {2},2)==1;
    assert countFragments(new int[] {0,0},0)==3;
    assert countFragments(new int[] {1,-1,1},1)==3;
    assert countFragments(new int[100_000],0)==5_000_050_000L;
}
```

## BUG-04 — oczekiwanie na cieplejszy dzień

**Poziom:** L2
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Dla każdej temperatury int zwróć liczbę dni do pierwszej późniejszej ściśle wyższej temperatury albo 0. Puste wejście daje pusty wynik. Bez mutacji. O(n) czasu i O(n) pamięci. Zwróć uwagę na równość.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-04 -->
```java
static int[] warmer(int[] values) {
    int[] result=new int[values.length]; Deque<Integer> stack=new ArrayDeque<>();
    for(int i=0;i<values.length;i++) {
        while(!stack.isEmpty() && values[i]>=values[stack.peek()]) { int previous=stack.pop(); result[previous]=i-previous; }
        stack.push(i);
    }
    return result;
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-04 -->
```java
static void verify() {
    assert Arrays.equals(warmer(new int[] {1,2,3}),new int[] {1,1,0});
    assert Arrays.equals(warmer(new int[] {2,2}),new int[] {0,0});
    assert Arrays.equals(warmer(new int[] {3,2,1}),new int[] {0,0,0});
    assert Arrays.equals(warmer(new int[] {73,74,74,76}),new int[] {1,2,1,0});
    assert warmer(new int[0]).length==0;
}
```

## BUG-05 — zapis wyników rekurencji

**Poziom:** L2
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Zwróć wszystkie podzbiory różnych wartości int, każdy z zachowaną kolejnością wejścia. Kolejność wyników dowolna, puste wejście daje jeden pusty podzbiór. n≤15. Wyniki nie mogą współdzielić bufora ścieżki; bez mutacji wejścia. Wyjaśnij tożsamość zapisanych obiektów.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-05 -->
```java
static List<List<Integer>> subsets(int[] values) {
    List<List<Integer>> result=new ArrayList<>();
    class Search {
        void run(int start,List<Integer> path) {
            result.add(path);
            for(int i=start;i<values.length;i++) { path.add(values[i]); run(i+1,path); path.remove(path.size()-1); }
        }
    }
    new Search().run(0,new ArrayList<>()); return result;
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-05 -->
```java
static void verify() {
    assert subsets(new int[0]).equals(List.of(List.of()));
    List<List<Integer>> result=subsets(new int[] {1,2});
    assert result.size()==4;
    assert new HashSet<>(result).equals(Set.of(List.of(),List.of(1),List.of(2),List.of(1,2)));
    List<List<Integer>> single=subsets(new int[] {7});
    assert single.size()==2 && new HashSet<>(single).equals(Set.of(List.of(),List.of(7)));
}
```

## BUG-06 — najkrótsza liczba krawędzi

**Poziom:** L2
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Niepusty graf skierowany nieważony jest zapisany jako poprawne listy sąsiedztwa. Zwróć najmniejszą liczbę krawędzi source→target lub -1. Source=target daje 0. Bez mutacji, O(V+E) czasu. Sprawdź kolejność obsługi struktury pracy.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-06 -->
```java
static int distance(int[][] graph, int source, int target) {
    int[] distance=new int[graph.length]; Arrays.fill(distance,-1); distance[source]=0;
    Deque<Integer> queue=new ArrayDeque<>(); queue.addLast(source);
    while(!queue.isEmpty()) {
        int vertex=queue.removeLast();
        if(vertex==target) { return distance[vertex]; }
        for(int next:graph[vertex]) { if(distance[next]==-1) { distance[next]=distance[vertex]+1; queue.addLast(next); } }
    }
    return -1;
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-06 -->
```java
static void verify() {
    assert distance(new int[][] {{1},{}},0,1)==1;
    int[][] graph={{1,3},{2},{},{4},{5},{2}};
    assert distance(graph,0,2)==2;
    assert distance(graph,2,0)==-1;
    assert distance(graph,3,3)==0;
}
```

## BUG-07 — porządek poddrzewa

**Poziom:** L3
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Sprawdź ścisłe BST w acyklicznym drzewie bez współdzielonych poddrzew. Wartości int, duplikaty zabronione, pusty korzeń poprawny. Nie zmieniaj drzewa. O(n) czasu. Nazwij ograniczenia narzucone przez wszystkich przodków.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-07 -->
```java
static final class Node {
    final int value; Node left,right;
    Node(int value) { this.value=value; }
}
static boolean valid(Node root) {
    Deque<Node> stack=new ArrayDeque<>(); if(root!=null) { stack.push(root); }
    while(!stack.isEmpty()) {
        Node node=stack.pop();
        if(node.left!=null) { if(node.left.value>=node.value) { return false; } stack.push(node.left); }
        if(node.right!=null) { if(node.right.value<=node.value) { return false; } stack.push(node.right); }
    }
    return true;
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-07 -->
```java
static void verify() {
    assert valid(null);
    Node root=new Node(10); root.left=new Node(5); root.right=new Node(15);
    assert valid(root);
    root.right.left=new Node(6); assert !valid(root);
    root.right.left=new Node(10); assert !valid(root);
    Node extremes=new Node(Integer.MIN_VALUE); extremes.right=new Node(Integer.MAX_VALUE); assert valid(extremes);
}
```

## BUG-08 — kolejność operandów

**Poziom:** L1
**Wymagania:** prześledzenie kodu i sformułowanie niezmiennika; [metodyka](../METODYKA.md).

**Treść i kontrakt.** Oblicz poprawne wyrażenie RPN. Tokeny to int i +,-,*,/. Każdy wynik pośredni mieści się w int, brak dzielenia przez zero; dzielenie obcina do zera. Nie zmieniaj tokenów. O(n) czasu i O(n) pamięci. Ustal, który operand jest lewy.

Zadanie: zapisz minimalny kontrprzykład, opisz przyczynę, napraw kod oraz dopisz
przypadek regresyjny. Nie usuwaj istniejących asercji.

Kontekst: celowo błędna, kompilująca się implementacja do poprawienia.

<!-- exercise:BUG-08 -->
```java
static int evaluate(String[] tokens) {
    Deque<Integer> stack=new ArrayDeque<>();
    for(String token:tokens) {
        if(token.equals("+")||token.equals("-")||token.equals("*")||token.equals("/")) {
            int left=stack.pop(),right=stack.pop();
            stack.push(switch(token) { case "+" -> left+right; case "-" -> left-right; case "*" -> left*right; default -> left/right; });
        } else { stack.push(Integer.parseInt(token)); }
    }
    return stack.pop();
}
```

Kontekst: wykonywalne testy poprawnego kontraktu.

<!-- tests:BUG-08 -->
```java
static void verify() {
    assert evaluate(new String[] {"2","3","+"})==5;
    assert evaluate(new String[] {"3","2","-"})==1;
    assert evaluate(new String[] {"7","3","/"})==2;
    assert evaluate(new String[] {"-7","3","/"})==-2;
    assert evaluate(new String[] {"9"})==9;
}
```
