package pl.jakubtworek.backend_engineering.stage_2.block_f;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CutoverSimulationTest {
    @Test void staleCopyCannotBePromotedAndWritesRemainFencedWhilePaused() {
        var migration = new CutoverSimulation();
        migration.write(CutoverSimulation.Owner.LEGACY);
        long watermark = migration.pauseAndGetWatermark();
        assertThrows(IllegalStateException.class, () -> migration.verify(watermark - 1, true));
        assertThrows(IllegalStateException.class, migration::promote);
        assertThrows(IllegalStateException.class, () -> migration.write(CutoverSimulation.Owner.LEGACY));
        migration.verify(watermark, true);
        migration.promote();
        assertThrows(IllegalStateException.class, () -> migration.write(CutoverSimulation.Owner.LEGACY));
        migration.write(CutoverSimulation.Owner.NEW);
        assertThrows(IllegalStateException.class, migration::rollback);
    }

    @Test void promotionWithoutNewWritesCanBeReverted() {
        var migration = new CutoverSimulation();
        migration.verify(migration.pauseAndGetWatermark(), true);
        migration.promote();
        migration.rollback();
        assertEquals(CutoverSimulation.Owner.LEGACY, migration.owner());
    }
}
