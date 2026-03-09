package pl.jakubtworek.backend_systems_lab_stage_1.block_a.java_effective.collections;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedList;

class ListPerformanceTest {

    @Test
    void compareRandomAccessPerformance() {

        int size = 100_000;

        long arrayListTime =
                ListPerformanceExample.testRandomAccess(
                        new ArrayList<>(),
                        size
                );

        long linkedListTime =
                ListPerformanceExample.testRandomAccess(
                        new LinkedList<>(),
                        size
                );

        System.out.println("ArrayList random access: " + arrayListTime);
        System.out.println("LinkedList random access: " + linkedListTime);
    }

    @Test
    void compareInsertBeginningPerformance() {

        int size = 50_000;

        long arrayListTime =
                ListPerformanceExample.testInsertBeginning(
                        new ArrayList<>(),
                        size
                );

        long linkedListTime =
                ListPerformanceExample.testInsertBeginning(
                        new LinkedList<>(),
                        size
                );

        System.out.println("ArrayList insert beginning: " + arrayListTime);
        System.out.println("LinkedList insert beginning: " + linkedListTime);
    }
}