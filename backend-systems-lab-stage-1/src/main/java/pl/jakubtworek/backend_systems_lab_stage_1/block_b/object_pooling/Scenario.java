package pl.jakubtworek.backend_systems_lab_stage_1.block_b.object_pooling;

public interface Scenario {

    // Runs one complete scenario and returns a checksum.
    //
    // The checksum makes the work observable.
    // Without it, the JIT could remove some computations as dead code.
    long run() throws InterruptedException;
}