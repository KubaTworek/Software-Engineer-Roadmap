package pl.jakubtworek.backend_engineering.stage_3.block_b.alerts;

/** Długie okno mierzy zużycie budżetu, krótkie potwierdza, że incydent nadal trwa. */
public record BurnRatePolicy(double objective,double threshold) {
    public record Window(long eligible,long bad) {
        public Window {if(eligible<0||bad<0||bad>eligible)throw new IllegalArgumentException("Invalid SLI sample");}
    }
    public enum Decision { PAGE, HEALTHY, UNKNOWN }
    public BurnRatePolicy {
        if(!Double.isFinite(objective)||objective<=0||objective>=1||!Double.isFinite(threshold)||threshold<=0)throw new IllegalArgumentException("Invalid budget policy");
    }
    public Decision evaluate(Window longWindow,Window shortWindow) {
        if(longWindow.eligible()==0||shortWindow.eligible()==0)return Decision.UNKNOWN;
        double longBurn=((double)longWindow.bad()/longWindow.eligible())/(1-objective);
        double shortBurn=((double)shortWindow.bad()/shortWindow.eligible())/(1-objective);
        return longBurn>threshold&&shortBurn>threshold?Decision.PAGE:Decision.HEALTHY;
    }
}
