package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.lifecycle;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.config.AppProperties;
import pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.health.ApplicationHealthState;

@Component
public class StartupCoordinator {

    private final AppProperties properties;
    private final ApplicationHealthState healthState;

    public StartupCoordinator(AppProperties properties, ApplicationHealthState healthState) {
        this.properties = properties;
        this.healthState = healthState;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // The web server may already listen on a port, but the application can still be warming up.
        // This delay demonstrates why startupProbe is not the same as readinessProbe.
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(properties.getStartupDelay().toMillis());
                healthState.markStarted();
                System.out.println("Startup completed.");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                System.err.println("Startup coordinator interrupted.");
            }
        });

        thread.setName("startup-coordinator");
        thread.setDaemon(true);
        thread.start();
    }
}