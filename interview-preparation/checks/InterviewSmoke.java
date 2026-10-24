import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// Testy korzystają z klas wygenerowanych bezpośrednio z oznaczonych bloków Markdown.
// Kompilacja kontrprzykładu nie jest dowodem jego poprawności; nie uruchamiamy
// błędnego protokołu publikacji. JMM uzasadniamy relacją happens-before w tekście.
class InterviewSmoke {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) throws Exception {
        var random = new Random(713);
        int[][] original = {{8, 10}, {2, 6}, {1, 3}};
        check(Arrays.deepEquals(Snippet_ALG_02.mergeClosed(original), new int[][]{{1, 6}, {8, 10}}));
        check(original[0][0] == 8 && original[2][1] == 3);
        check(Snippet_ALG_02.mergeClosed(new int[0][]).length == 0);
        var lru = new Snippet_ALG_04.Lru<String, Integer>(2);
        lru.put("A", 1); lru.put("B", 2); lru.get("A"); lru.put("C", 3);
        check(lru.containsKey("A") && !lru.containsKey("B") && lru.size() == 2);
        var zero = new Snippet_ALG_04.Lru<Integer, Integer>(0); zero.put(1, 1); check(zero.isEmpty());
        check(Snippet_ALG_09.acyclic(3, new int[][]{{0, 1}, {1, 2}}));
        check(!Snippet_ALG_09.acyclic(3, new int[][]{{0, 1}, {1, 2}, {2, 0}}));
        check(!Snippet_ALG_09.acyclic(1, new int[][]{{0, 0}}));
        check(Snippet_ALG_09.acyclic(0, new int[0][]));
        for (int run = 0; run < 250; run++) {
            int n = 1 + random.nextInt(9);
            int[] values = random.ints(n, -10, 11).toArray();
            long max = Long.MIN_VALUE;
            int target = random.nextInt(21) - 10, shortest = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                long sum = 0;
                for (int j = i; j < n; j++) {
                    sum += values[j]; max = Math.max(max, sum);
                    if (sum == target) shortest = Math.min(shortest, j - i + 1);
                }
            }
            check(Snippet_ALG_12.maxSubarray(values) == max);
            check(Snippet_ALG_24.shortestExact(values, target) == (shortest == Integer.MAX_VALUE ? -1 : shortest));
            int longest = 0;
            for (int mask = 1; mask < (1 << n); mask++) {
                int length = 0, previous = Integer.MIN_VALUE; boolean valid = true;
                for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) {
                    if (values[i] <= previous) { valid = false; break; }
                    previous = values[i]; length++;
                }
                if (valid) longest = Math.max(longest, length);
            }
            check(Snippet_ALG_17.lisLength(values) == longest);
            int[] heights = random.ints(n, 0, 12).toArray();
            long water = 0;
            for (int i = 0; i < n; i++) {
                int left = 0, right = 0;
                for (int j = 0; j <= i; j++) left = Math.max(left, heights[j]);
                for (int j = i; j < n; j++) right = Math.max(right, heights[j]);
                water += Math.min(left, right) - heights[i];
            }
            check(Snippet_ALG_20.trapped(heights) == water);
            boolean pairExists = false;
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++)
                pairExists |= (long) values[i] + values[j] == target;
            int[] pair = Snippet_ALG_15.twoSum(values, target);
            check((pair.length == 2) == pairExists);
            if (pair.length == 2) check(pair[0] != pair[1] && (long)values[pair[0]] + values[pair[1]] == target);
            int[][] meetings = new int[n][2];
            for (int i = 0; i < n; i++) {
                meetings[i][0] = random.nextInt(10);
                meetings[i][1] = meetings[i][0] + 1 + random.nextInt(5);
            }
            int rooms = 0;
            for (int time = 0; time < 15; time++) {
                int active = 0;
                for (int[] meeting : meetings) if (meeting[0] <= time && time < meeting[1]) active++;
                rooms = Math.max(rooms, active);
            }
            check(Snippet_ALG_05.rooms(meetings) == rooms);
        }
        check(Snippet_ALG_20.trapped(new int[0]) == 0);
        check(Snippet_ALG_23.longestUnique("abba") == 2);
        check(Snippet_ALG_23.longestUnique("a\uD83D\uDE0Ab\uD83D\uDE0A") == 3);
        check(Snippet_ALG_23.longestUnique("") == 0);
        var head = new Snippet_ALG_27.Link(); head.next = head;
        check(Snippet_ALG_27.hasCycle(head)); check(!Snippet_ALG_27.hasCycle(null));
        var top = new PriorityQueue<Integer>();
        for (int value : new int[]{4, 5, 8, 2, 10}) Snippet_ALG_13.keepLargest(top, 3, value);
        check(top.size() == 3 && top.peek() == 5);
        var bucket = new Snippet_LLD_04.Bucket(2, 1.0, 0);
        check(bucket.allow(0)); check(bucket.allow(0)); check(!bucket.allow(0));
        check(!bucket.allow(500_000_000L)); check(bucket.allow(1_000_000_000L));
        for (int total = 0; total <= 100; total++) for (int people = 1; people <= 10; people++) {
            long sum = 0, min = Long.MAX_VALUE, max = 0;
            for (int i = 0; i < people; i++) {
                long share = Snippet_LLD_06.share(total, people, i);
                sum += share; min = Math.min(min, share); max = Math.max(max, share);
            }
            check(sum == total && max - min <= 1);
        }
        var initial = new ArrayList<String>(); initial.add("A");
        var view = new Snippet_JAVA_05.OrderView("id", initial); initial.add("B");
        check(view.items().size() == 1);
        try { view.items().add("C"); throw new AssertionError(); }
        catch (UnsupportedOperationException expected) { checks++; }
        var complete = new TreeSet<Long>(); complete.add(11L);
        check(Snippet_MSG_05.unsafePosition(complete) == 12); // odtworzony błędny skok przez 10
        check(Snippet_MSG_05_2.commitPosition(10, complete) == 10);
        complete.add(10L);
        check(Snippet_MSG_05_2.commitPosition(10, complete) == 12 && complete.isEmpty());
        complete.add(14L);
        check(Snippet_MSG_05_2.commitPosition(12, complete) == 12);
        complete.add(12L);
        check(Snippet_MSG_05_2.commitPosition(12, complete) == 13 && complete.contains(14L));
        var publication = new Snippet_CON_09_2.SafePublication();
        check(publication.read().equals("oczekuje"));
        publication.publish();
        check(publication.read().equals("gotowe"));
        byte[] bytes = {1, 2};
        var digest = new Snippet_CON_08.Digest(bytes);
        bytes[0] = 9;
        check(digest.bytes()[0] == 1);
        byte[] exposed = digest.bytes(); exposed[1] = 8;
        check(digest.bytes()[1] == 2);
        try { bucket.allow(999_999_999L); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        var balance = new AtomicInteger(1000); var accepted = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<?>>();
            for (int t = 0; t < 8; t++) futures.add(pool.submit(() -> {
                for (int i = 0; i < 250; i++) if (Snippet_CON_06.withdraw(balance, 1)) accepted.incrementAndGet();
            }));
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        }
        check(balance.get() == 0 && accepted.get() == 1000);
        System.out.println("behavior: " + checks + " checks; algorithms, boundaries, defensive copies, offset prefix and concurrent CAS; OK");
    }
}
