package pl.jakubtworek.backend_systems_lab_stage_1.race_condition;

public interface TicketStore {
    void buy();
    int getAvailable();
    int getInitial();
    int getSold();
    String name();
}