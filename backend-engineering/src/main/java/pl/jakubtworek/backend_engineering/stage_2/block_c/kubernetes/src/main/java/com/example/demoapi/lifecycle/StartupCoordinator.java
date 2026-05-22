package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.lifecycle;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.config.AppProperties;
import pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.health.ApplicationHealthState;

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
        // The HTTP server may already be listening, but the application can still be warming up.
        // This separation models the difference between "process exists" and "application is ready".
        Thread startupThread = new Thread(() -> {
            try {
                Thread.sleep(properties.getStartupDelay().toMillis());
                healthState.markStarted();
                System.out.println("Startup completed; startupProbe can now pass.");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                System.err.println("Startup coordinator was interrupted.");
            }
        });

        startupThread.setName("startup-coordinator");
        startupThread.setDaemon(true);
        startupThread.start();
    }
}