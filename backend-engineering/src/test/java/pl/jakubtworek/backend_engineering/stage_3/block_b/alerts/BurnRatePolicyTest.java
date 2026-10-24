package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BurnRatePolicyTest {
    @Test void recoveryClearsPageWhileLongWindowStillContainsFailuresAndSilenceIsUnknown() {
        var policy=new BurnRatePolicy(0.999,14.4);
        assertEquals(BurnRatePolicy.Decision.PAGE,policy.evaluate(new BurnRatePolicy.Window(100000,2000),new BurnRatePolicy.Window(10000,200)));
        assertEquals(BurnRatePolicy.Decision.HEALTHY,policy.evaluate(new BurnRatePolicy.Window(100000,2000),new BurnRatePolicy.Window(10000,0)));
        assertEquals(BurnRatePolicy.Decision.UNKNOWN,policy.evaluate(new BurnRatePolicy.Window(0,0),new BurnRatePolicy.Window(0,0)));
    }
}
