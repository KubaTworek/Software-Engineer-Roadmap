package pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class InsertionBenchmark {

    @Param({"1000", "10000", "100000"})
    private int size;

    private ArrayList<Integer> baseArrayList;
    private LinkedList<Integer> baseLinkedList;

    @Setup
    public void setup() {
        // Base collections are prepared outside measured methods.
        // Each benchmark method copies them before mutation to keep runs independent.
        baseArrayList = new ArrayList<>(size);
        baseLinkedList = new LinkedList<>();

        for (int i = 0; i < size; i++) {
            baseArrayList.add(i);
            baseLinkedList.add(i);
        }
    }

    @Benchmark
    public int insertMiddleArrayList() {
        // Inserting into the middle of ArrayList requires shifting elements.
        // This is O(n), but the memory movement is over a compact backing array.
        ArrayList<Integer> copy = new ArrayList<>(baseArrayList);

        copy.add(copy.size() / 2, 42);

        return copy.size();
    }

    @Benchmark
    public int insertMiddleLinkedListWithIndex() {
        // This looks like the classic case where LinkedList should win,
        // but add(index, value) first has to walk to the target node.
        //
        // Node traversal is pointer chasing and often cache-unfriendly.
        LinkedList<Integer> copy = new LinkedList<>(baseLinkedList);

        copy.add(copy.size() / 2, 42);

        return copy.size();
    }

    @Benchmark
    public int insertMiddleLinkedListWithIterator() {
        // This version uses a ListIterator positioned manually.
        // The actual insertion after reaching the node is cheap,
        // but reaching the middle still requires traversal.
        LinkedList<Integer> copy = new LinkedList<>(baseLinkedList);

        ListIterator<Integer> iterator = copy.listIterator(copy.size() / 2);
        iterator.add(42);

        return copy.size();
    }
}