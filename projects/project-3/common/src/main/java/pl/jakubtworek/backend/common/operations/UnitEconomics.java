package pl.jakubtworek.backend.common.operations;

import java.math.*;
import java.util.*;

/** Koszt udanego zamówienia ma mianownik biznesowy, a nie liczbę 2xx lub prób retry. */
public final class UnitEconomics {
    public record Window(String scenario,long seconds,BigDecimal cost,String currency,long eligible,long good,long completed,double p99Millis) {
        public Window {
            if(scenario==null||!scenario.matches("[A-Za-z0-9_-]+")||seconds<=0||cost==null||cost.signum()<0
                    ||currency==null||!currency.matches("[A-Z]{3}")||eligible<0||good<0||completed<0||good>completed||completed>eligible
                    ||!Double.isFinite(p99Millis)||p99Millis<0)throw new IllegalArgumentException("Invalid measurement window");
        }
        public Optional<BigDecimal> costPerCompletedOrder(){return completed==0?Optional.empty():Optional.of(cost.divide(BigDecimal.valueOf(completed),8,RoundingMode.HALF_UP));}
        public boolean meets(double availability,double latencyLimit){
            if(!Double.isFinite(availability)||availability<=0||availability>=1||!Double.isFinite(latencyLimit)||latencyLimit<=0)throw new IllegalArgumentException("Invalid SLO");
            return eligible>0&&completed>0&&BigDecimal.valueOf(good).compareTo(BigDecimal.valueOf(availability).multiply(BigDecimal.valueOf(eligible)))>=0&&p99Millis<=latencyLimit;
        }
    }
    public static Optional<Window> cheapest(List<Window> windows,double availability,double latencyLimit) {
        if(windows.stream().map(Window::currency).distinct().count()>1)throw new IllegalArgumentException("Cannot compare currencies without an explicit FX contract");
        return windows.stream().filter(w->w.meets(availability,latencyLimit))
                .min(Comparator.comparing(w->w.costPerCompletedOrder().orElseThrow()));
    }
    /** CSV ma jawny, wąski format bez quoting; ceny i źródło estymacji dostarcza operator. */
    public static void main(String[] args)throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("CSV availability latencyLimitMillis");
        var lines=java.nio.file.Files.readAllLines(java.nio.file.Path.of(args[0]));
        if(lines.isEmpty()||!lines.getFirst().equals("scenario,seconds,cost,currency,eligible,good,completed,p99Millis"))throw new IllegalArgumentException("Unknown CSV contract");
        var windows=new ArrayList<Window>();
        for(String line:lines.subList(1,lines.size()))if(!line.isBlank()){
            var c=line.split(",",-1);if(c.length!=8)throw new IllegalArgumentException("Expected 8 CSV fields");
            windows.add(new Window(c[0],Long.parseLong(c[1]),new BigDecimal(c[2]),c[3],Long.parseLong(c[4]),Long.parseLong(c[5]),Long.parseLong(c[6]),Double.parseDouble(c[7])));
        }
        double availability=Double.parseDouble(args[1]),limit=Double.parseDouble(args[2]);
        for(Window w:windows)System.out.println(w.scenario()+": costPerCompletedOrder="+w.costPerCompletedOrder().map(BigDecimal::toPlainString).orElse("UNKNOWN")+" "+w.currency()+", meetsSlo="+w.meets(availability,limit));
        System.out.println("selected="+cheapest(windows,availability,limit).map(Window::scenario).orElse("NONE"));
    }
}
