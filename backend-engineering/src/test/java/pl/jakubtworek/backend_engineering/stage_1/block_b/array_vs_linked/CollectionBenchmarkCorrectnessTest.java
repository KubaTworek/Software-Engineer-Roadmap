package pl.jakubtworek.backend_engineering.stage_1.block_b.array_vs_linked;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionBenchmarkCorrectnessTest {

    @Test
    void iterationImplementationsProduceTheSameSumFromTheSameData() {
        ArrayListVsLinkedListBenchmark benchmark = new ArrayListVsLinkedListBenchmark();
        benchmark.size = 257;
        benchmark.setup();

        long expected = IntStream.range(0, benchmark.size).asLongStream().sum();

        assertThat(benchmark.iterateArrayList()).isEqualTo(expected);
        assertThat(benchmark.iterateLinkedList()).isEqualTo(expected);
    }

    @Test
    void indexedAccessImplementationsVisitTheSamePositions() {
        ArrayListVsLinkedListBenchmark benchmark = new ArrayListVsLinkedListBenchmark();
        benchmark.size = 257;
        benchmark.setup();

        assertThat(benchmark.indexAccessArrayList())
                .isEqualTo(benchmark.indexAccessLinkedList());
    }

    @Test
    void insertionVariantsStartFromEquivalentInputsAndPerformOneInsertion() {
        InsertionBenchmark benchmark = new InsertionBenchmark();
        benchmark.size = 31;
        benchmark.setup();

        assertThat(benchmark.insertMiddleArrayList()).isEqualTo(32);
        assertThat(benchmark.insertMiddleLinkedListWithIndex()).isEqualTo(32);
        assertThat(benchmark.insertMiddleLinkedListWithIterator()).isEqualTo(32);
    }

    @Test
    void primitiveAndBoxedIterationHaveEquivalentNumericResults() {
        PrimitiveArrayBaselineBenchmark benchmark = new PrimitiveArrayBaselineBenchmark();
        benchmark.size = 257;
        benchmark.setup();

        assertThat(benchmark.iteratePrimitiveArray())
                .isEqualTo(benchmark.iterateArrayListOfInteger());
    }

    @Test
    void allocationVariantsCreateCollectionsWithTheSameContents() {
        CollectionAllocationBenchmark benchmark = new CollectionAllocationBenchmark();
        benchmark.size = 257;

        ArrayList<Integer> arrayList = benchmark.createArrayList();
        LinkedList<Integer> linkedList = benchmark.createLinkedList();

        assertThat(arrayList).containsExactlyElementsOf(linkedList);
        assertThat(arrayList).hasSize(257);
    }
}
