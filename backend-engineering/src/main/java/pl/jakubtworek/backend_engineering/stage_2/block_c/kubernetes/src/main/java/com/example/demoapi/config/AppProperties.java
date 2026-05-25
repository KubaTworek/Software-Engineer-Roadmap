package pl.jakubtworek.backend_engineering.stage_2.block_c.kubernetes.src.main.java.com.example.demoapi.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Application name visible in responses and logs.
     */
    @NotBlank
    private String name = "demo-api";

    /**
     * Artificial startup delay used to demonstrate startupProbe behavior.
     */
    private Duration startupDelay = Duration.ofSeconds(5);

    /**
     * Maximum time isAllowed for graceful shutdown.
     */
    private Duration shutdownTimeout = Duration.ofSeconds(25);

    /**
     * HTTP port used by the embedded server.
     */
    @Min(1)
    private int port = 8080;

    /**
     * Path to the mounted ConfigMap file.
     */
    private String configFilePath = "/etc/app/app.yaml";

    /**
     * Path to temporary scratch space backed by emptyDir.
     */
    private String tmpDirectory = "/tmp/demo-api";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public String getConfigFilePath() {
        return configFilePath;
    }

    public void setConfigFilePath(String configFilePath) {
        this.configFilePath = configFilePath;
    }

    public String getTmpDirectory() {
        return tmpDirectory;
    }

    public void setTmpDirectory(String tmpDirectory) {
        this.tmpDirectory = tmpDirectory;
    }
}