package pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.lifecycle;

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
        // Startup is separated from readiness to model applications that need warm-up time.
        // Kubernetes startupProbe should pass only after this state becomes true.
        Thread startupThread = new Thread(() -> {
            try {
                Thread.sleep(properties.getStartupDelay().toMillis());
                healthState.markStarted();
                System.out.println("Application startup completed");
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                System.err.println("Startup coordinator interrupted");
            }
        });

        startupThread.setName("startup-coordinator");
        startupThread.setDaemon(true);
        startupThread.start();
    }
}