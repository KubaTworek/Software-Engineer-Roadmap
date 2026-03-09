package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.race_condition;

public class BrokenTicketStore implements TicketStore {

    private int available = 1;
    private final int initial = 1;
    private int sold = 0;

    @Override
    public void buy() {
        if (available > 0) {
            int tmp = available;

            Thread.yield();

            available = tmp - 1;
            sold++;
        }
    }

    @Override
    public int getAvailable() {
        return available;
    }

    @Override
    public int getSold() {
        return sold;
    }

    @Override
    public int getInitial() {
        return initial;
    }

    @Override
    public String name() {
        return "Broken (Lost Update)";
    }
}