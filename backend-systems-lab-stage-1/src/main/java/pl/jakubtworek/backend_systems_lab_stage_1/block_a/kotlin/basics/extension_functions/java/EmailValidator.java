package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.extension_functions.java;

public class EmailValidator {

    public boolean validate(String email) {
        // Java requires calling a static utility method.
        return StringUtils.isEmail(email);
    }
}