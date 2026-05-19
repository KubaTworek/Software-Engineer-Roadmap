package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.delegated_properties.java;

public class ConfigurationService {

    private String configuration;

    public String getConfiguration() {
        // Java requires manual lazy initialization.
        if (configuration == null) {
            System.out.println("Loading configuration...");
            configuration = "application-config";
        }

        return configuration;
    }

    public void printConfiguration() {
        System.out.println(getConfiguration());
    }
}