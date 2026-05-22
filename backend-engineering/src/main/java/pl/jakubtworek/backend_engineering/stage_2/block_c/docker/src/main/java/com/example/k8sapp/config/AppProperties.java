package pl.jakubtworek.backend_engineering.stage_2.block_c.docker.src.main.java.com.example.k8sapp.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Artificial startup delay used to demonstrate startupProbe behavior.
     */
    private Duration startupDelay = Duration.ofSeconds(5);

    /**
     * Maximum time the application should spend during graceful shutdown.
     */
    private Duration shutdownTimeout = Duration.ofSeconds(25);

    /**
     * HTTP port is usually configured through server.port, but keeping this here
     * makes the application-level configuration explicit for the concept.
     */
    @Min(1)
    private int port = 8080;

    public Duration getStartupDelay() {
        return startupDelay;
    }

    public void setStartupDelay(Duration startupDelay) {
        this.startupDelay = startupDelay;
    }

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}