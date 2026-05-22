package pl.jakubtworek.backend_engineering.stage_2.block_c.workshop.src.main.java.com.example.demoapi.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Application name visible in responses and logs.
     */
    @NotBlank
    private String name = "demo-api";

    /**
     * Artificial delay used to demonstrate startupProbe behavior.
     */
    private Duration startupDelay = Duration.ofSeconds(5);

    /**
     * HTTP port used by the embedded web server.
     */
    @Min(1)
    private int port = 8080;

    /**
     * Expected bind address.
     * In Kubernetes, applications should listen on 0.0.0.0, not only on 127.0.0.1.
     */
    @NotBlank
    private String bindAddress = "0.0.0.0";

    /**
     * Logical image tag injected by CI/CD.
     */
    private String imageTag = "dev";

    /**
     * Git commit SHA injected by CI/CD.
     */
    private String commitSha = "local";

    public String getName() {
        return name;
    }

    public Duration getStartupDelay() {
        return startupDelay;
    }

    public int getPort() {
        return port;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public String getImageTag() {
        return imageTag;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStartupDelay(Duration startupDelay) {
        this.startupDelay = startupDelay;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }
}