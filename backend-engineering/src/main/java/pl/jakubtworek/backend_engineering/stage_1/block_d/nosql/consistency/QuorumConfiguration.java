package pl.jakubtworek.backend_engineering.stage_1.block_d.nosql.consistency;

/**
 * Model obliczeniowy quorum dla N replik, R potwierdzeń odczytu i W zapisu.
 * Nierówności opisują przecięcie zbiorów, ale nie zastępują wersjonowania,
 * naprawy replik ani rozwiązywania konfliktów konkretnego silnika.
 */
public record QuorumConfiguration(int replicas, int readAcks, int writeAcks) {

    public QuorumConfiguration {
        if (replicas <= 0) {
            throw new IllegalArgumentException("replicas must be positive");
        }
        if (readAcks <= 0 || readAcks > replicas) {
            throw new IllegalArgumentException("readAcks must be in range 1..replicas");
        }
        if (writeAcks <= 0 || writeAcks > replicas) {
            throw new IllegalArgumentException("writeAcks must be in range 1..replicas");
        }
    }

    public boolean readAndWriteQuorumsOverlap() {
        return readAcks + writeAcks > replicas;
    }

    public boolean concurrentWriteQuorumsOverlap() {
        return 2 * writeAcks > replicas;
    }

    public int toleratedReadReplicaFailures() {
        return replicas - readAcks;
    }

    public int toleratedWriteReplicaFailures() {
        return replicas - writeAcks;
    }
}
