package pl.jakubtworek.backend_engineering.stage_1.block_a.race_condition;

public interface TicketStore {
    void buy();
    int getAvailable();
    int getInitial();
    int getSold();
    String name();
}