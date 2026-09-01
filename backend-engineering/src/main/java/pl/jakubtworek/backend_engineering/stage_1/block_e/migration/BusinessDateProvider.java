package pl.jakubtworek.backend_engineering.stage_1.block_e.migration;

import java.time.LocalDate;

@FunctionalInterface
public interface BusinessDateProvider {

    LocalDate currentDate();
}
