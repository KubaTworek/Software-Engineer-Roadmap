package pl.jakubtworek.backend_systems_lab_stage_1.block_a.thread_confinement;

public class Order {
    private final int id;

    public Order(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}