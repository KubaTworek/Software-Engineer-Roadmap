package pl.jakubtworek.backend_engineering.stage_2.block_c.configuration.src.main.java.com.example.demoapi.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Application name injected from ConfigMap or environment.
     */
    @NotBlank
    private String name = "demo-api";

    /**
     * Artificial startup delay used to demonstrate startupProbe behavior.
     */
    private Duration startupDelay = Duration.ofSeconds(5);

    /**
     * Maximum graceful shutdown duration.
     */
    private Duration shutdownTimeout = Duration.ofSeconds(25);

    /**
     * HTTP server port.
     */
    @Min(1)
    private int port = 8080;

    /**
     * Mounted ConfigMap file path.
     * Values loaded from mounted files may be refreshed at runtime.
     */
    private String mountedConfigPath = "/etc/app/app.yaml";

    /**
     * Directory backed by emptyDir.
     * Data here is disposable and follows the Pod lifecycle.
     */
    private String scratchDirectory = "/tmp/demo-api";

    /**
     * Directory backed by PVC.
     * Data here has a lifecycle independent from the Pod.
     */
    private String persistentDirectory = "/data/demo-api";

    public String getName() {
        return name;
    }

    public Duration getStartupDelay() {
        return startupDelay;
    }

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    public int getPort() {
        return port;
    }

    public String getMountedConfigPath() {
        return mountedConfigPath;
    }

    public String getScratchDirectory() {
        return scratchDirectory;
    }

    public String getPersistentDirectory() {
        return persistentDirectory;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStartupDelay(Duration startupDelay) {
        this.startupDelay = startupDelay;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setMountedConfigPath(String mountedConfigPath) {
        this.mountedConfigPath = mountedConfigPath;
    }

    public void setScratchDirectory(String scratchDirectory) {
        this.scratchDirectory = scratchDirectory;
    }

    public void setPersistentDirectory(String persistentDirectory) {
        this.persistentDirectory = persistentDirectory;
    }
}