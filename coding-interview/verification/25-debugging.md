# Referencje i mutacje zadań diagnostycznych

[Zasady obszaru weryfikacji](README.md)

Pełne rozwiązania kontrolne — czytaj po własnej próbie.

## BUG-01

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-01 -->
```java
static int lowerBound(int[] sorted, int target) {
    int left = 0, right = sorted.length;
    while (left < right) { int mid = left+(right-left)/2; if (sorted[mid] < target) { left = mid+1; } else { right = mid; } }
    return left;
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-01:wersja-poczatkowa -->
```java
static int lowerBound(int[] sorted, int target) {
    int left = 0, right = sorted.length;
    while (left < right) { int mid = left+(right-left)/2; if (sorted[mid] <= target) { left = mid+1; } else { right = mid; } }
    return left;
}
```

## BUG-02

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-02 -->
```java
static boolean hasPair(int[] values, int target) {
    int left=0,right=values.length-1;
    while(left<right) {
        long sum=(long) values[left]+values[right];
        if(sum==target) { return true; }
        if(sum<target) { left++; } else { right--; }
    }
    return false;
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-02:wersja-poczatkowa -->
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

## BUG-03

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-03 -->
```java
static long countFragments(int[] values, int target) {
    Map<Long,Long> counts=new HashMap<>(); counts.put(0L,1L); long prefix=0,result=0;
    for(int value:values) { prefix+=value; result+=counts.getOrDefault(prefix-target,0L); counts.merge(prefix,1L,Long::sum); }
    return result;
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-03:wersja-poczatkowa -->
```java
static long countFragments(int[] values, int target) {
    Map<Long,Long> counts=new HashMap<>(); long prefix=0,result=0;
    for(int value:values) { prefix+=value; result+=counts.getOrDefault(prefix-target,0L); counts.merge(prefix,1L,Long::sum); }
    return result;
}
```

## BUG-04

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-04 -->
```java
static int[] warmer(int[] values) {
    int[] result=new int[values.length]; Deque<Integer> stack=new ArrayDeque<>();
    for(int i=0;i<values.length;i++) {
        while(!stack.isEmpty() && values[i]>values[stack.peek()]) { int previous=stack.pop(); result[previous]=i-previous; }
        stack.push(i);
    }
    return result;
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-04:wersja-poczatkowa -->
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

## BUG-05

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-05 -->
```java
static List<List<Integer>> subsets(int[] values) {
    List<List<Integer>> result=new ArrayList<>();
    class Search {
        void run(int start,List<Integer> path) {
            result.add(new ArrayList<>(path));
            for(int i=start;i<values.length;i++) { path.add(values[i]); run(i+1,path); path.remove(path.size()-1); }
        }
    }
    new Search().run(0,new ArrayList<>()); return result;
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-05:wersja-poczatkowa -->
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

## BUG-06

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-06 -->
```java
static int distance(int[][] graph, int source, int target) {
    int[] distance=new int[graph.length]; Arrays.fill(distance,-1); distance[source]=0;
    Deque<Integer> queue=new ArrayDeque<>(); queue.addLast(source);
    while(!queue.isEmpty()) {
        int vertex=queue.removeFirst();
        if(vertex==target) { return distance[vertex]; }
        for(int next:graph[vertex]) { if(distance[next]==-1) { distance[next]=distance[vertex]+1; queue.addLast(next); } }
    }
    return -1;
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-06:wersja-poczatkowa -->
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

## BUG-07

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-07 -->
```java
static final class Node {
    final int value; Node left,right;
    Node(int value) { this.value=value; }
}
static boolean valid(Node root) {
    record Frame(Node node, long lower, long upper) {}
    Deque<Frame> stack = new ArrayDeque<>(); if (root != null) { stack.push(new Frame(root,Long.MIN_VALUE,Long.MAX_VALUE)); }
    while (!stack.isEmpty()) {
        Frame frame = stack.pop(); Node node = frame.node();
        if (node.value <= frame.lower() || node.value >= frame.upper()) { return false; }
        if (node.left != null) { stack.push(new Frame(node.left,frame.lower(),node.value)); }
        if (node.right != null) { stack.push(new Frame(node.right,node.value,frame.upper())); }
    }
    return true;
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-07:wersja-poczatkowa -->
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

## BUG-08

Kontekst: składowe klasy weryfikowane oryginalnym verify().

<!-- reference:BUG-08 -->
```java
static int evaluate(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (String token : tokens) {
        if (token.equals("+") || token.equals("-") || token.equals("*") || token.equals("/")) {
            int right = stack.pop(), left = stack.pop();
            stack.push(switch (token) { case "+" -> left+right; case "-" -> left-right; case "*" -> left*right; default -> left/right; });
        } else { stack.push(Integer.parseInt(token)); }
    }
    return stack.pop();
}
```

Kontekst: początkowa błędna wersja jako mutacja; oczekiwany AssertionError.

<!-- mutant:BUG-08:wersja-poczatkowa -->
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
