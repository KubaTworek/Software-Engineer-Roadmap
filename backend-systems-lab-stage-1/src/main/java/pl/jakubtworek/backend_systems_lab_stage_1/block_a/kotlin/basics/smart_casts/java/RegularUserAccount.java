package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.smart_casts.java;

public class RegularUserAccount implements Account {

    private final String name;

    public RegularUserAccount(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}