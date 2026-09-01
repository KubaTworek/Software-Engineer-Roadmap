package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

public class BehaviorChangedException extends RuntimeException {

    private final String legacyOutput;
    private final String candidateOutput;

    public BehaviorChangedException(String legacyOutput, String candidateOutput) {
        super("candidate output differs from characterized legacy behavior");
        this.legacyOutput = legacyOutput;
        this.candidateOutput = candidateOutput;
    }

    public String legacyOutput() {
        return legacyOutput;
    }

    public String candidateOutput() {
        return candidateOutput;
    }
}
