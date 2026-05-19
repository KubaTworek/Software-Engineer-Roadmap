package pl.jakubtworek.backend_systems_lab_stage_1.block_b.escape_analysis;

import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class EscapeState {

    // This field intentionally makes the referenced object escape.
    // If a benchmark stores a newly created object here,
    // the object becomes visible outside the local method scope.
    public Point point;
}