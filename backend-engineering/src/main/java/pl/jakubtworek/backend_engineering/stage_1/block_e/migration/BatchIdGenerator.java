package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

@FunctionalInterface
public interface BatchIdGenerator {

    String nextId();
}
