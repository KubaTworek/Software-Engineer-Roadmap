package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.smart_casts.java;

import java.util.List;

public class AdminUserAccount implements Account {

    private final String name;
    private final List<String> permissions;

    public AdminUserAccount(String name, List<String> permissions) {
        this.name = name;
        this.permissions = permissions;
    }

    public String getName() {
        return name;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}