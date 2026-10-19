package pl.jakubtworek.cloudarchitecture.operations.recovery;

import java.util.List;

public record RestoreDrillReport(boolean successful, List<String> failures) {

    public RestoreDrillReport {
        failures = List.copyOf(failures);
    }
}
