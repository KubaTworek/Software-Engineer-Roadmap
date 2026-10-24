package pl.jakubtworek.backend.common.operations;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class UnitEconomicsTest {
    @Test void cheapFailuresAndUnknownDenominatorsCannotWin() {
        var cheap=new UnitEconomics.Window("cheap",3600,BigDecimal.ONE,"PLN",1000,900,900,4000);
        var steady=new UnitEconomics.Window("steady",3600,BigDecimal.TEN,"PLN",1000,1000,1000,1000);
        var retryHeavy=new UnitEconomics.Window("retries",3600,BigDecimal.TEN,"PLN",1000,100,100,1000);
        assertThrows(IllegalArgumentException.class,()->new UnitEconomics.Window("mixed_denominators",3600,BigDecimal.TEN,"PLN",2000,2000,100,1000));
        assertEquals("steady",UnitEconomics.cheapest(List.of(cheap,steady,retryHeavy),0.999,3000).orElseThrow().scenario());
        assertEquals(new BigDecimal("0.10000000"),retryHeavy.costPerCompletedOrder().orElseThrow());
        var empty=new UnitEconomics.Window("empty",3600,BigDecimal.ONE,"PLN",0,0,0,0);
        assertTrue(empty.costPerCompletedOrder().isEmpty());assertFalse(empty.meets(0.999,3000));
    }
}
