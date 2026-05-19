package pl.jakubtworek.backend_systems_lab_stage_1.block_b.escape_analysis;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class LockState {

    // This object is shared at benchmark scope.
    // It is intentionally visible outside a single benchmark invocation.
    public final Object lock = new Object();
}