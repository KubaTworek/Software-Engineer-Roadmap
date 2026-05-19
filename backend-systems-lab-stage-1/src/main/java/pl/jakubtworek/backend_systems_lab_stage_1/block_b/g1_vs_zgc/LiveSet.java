package pl.jakubtworek.backend_systems_lab_stage_1.block_b.g1_vs_zgc;

import java.util.ArrayList;
import java.util.List;

public final class LiveSet {

    private final int targetMb;
    private final List<Payload> retainedObjects = new ArrayList<>();

    public LiveSet(int targetMb) {
        this.targetMb = targetMb;
    }

    public synchronized void growIfNeeded(int objectSizeBytes) {
        // This creates a stable live set.
        // A larger live set usually makes GC work harder.
        //
        // For G1, a large live set can increase marking and evacuation pressure.
        // For ZGC, a large live set is expected, but concurrent work and memory overhead still matter.
        long targetBytes = targetMb * 1024L * 1024L;
        long currentBytes = (long) retainedObjects.size() * objectSizeBytes;

        while (currentBytes < targetBytes) {
            retainedObjects.add(new Payload(objectSizeBytes));
            currentBytes += objectSizeBytes;
        }
    }

    public synchronized int touchSomeObjects() {
        // Touching retained objects simulates an application that keeps using old objects.
        // This makes the live set truly live, not just allocated and forgotten.
        int checksum = 0;

        for (int i = 0; i < retainedObjects.size(); i += 1024) {
            checksum += retainedObjects.get(i).touch();
        }

        return checksum;
    }

    public synchronized int size() {
        return retainedObjects.size();
    }
}