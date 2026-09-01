package com.example.demoapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
     * Artificial delay used to demonstrate startupProbe behavior.
     */
    @NotNull
    private Duration startupDelay = Duration.ofSeconds(5);

    /** Maximum time granted to Spring's graceful shutdown phase. */
    @NotNull
    private Duration shutdownTimeout = Duration.ofSeconds(25);

    /**
     * HTTP port used by the embedded web server.
     */
    @Min(1)
    @Max(65535)
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
    @NotBlank
    private String imageTag = "dev";

    /**
     * Git commit SHA injected by CI/CD.
     */
    @NotBlank
    private String commitSha = "local";

    /** ConfigMap file mounted by the runtime configuration layer. */
    @NotBlank
    private String mountedConfigPath = "/etc/app/app.yaml";

    /** Disposable directory expected to be backed by emptyDir. */
    @NotBlank
    private String scratchDirectory = "/tmp/demo-api";

    /** Durable directory expected to be backed by a PVC. */
    @NotBlank
    private String persistentDirectory = "/data/demo-api";

    public String getName() {
        return name;
    }

    public Duration getStartupDelay() {
        return startupDelay;
    }

    public int getPort() {
        return port;
    }

    public Duration getShutdownTimeout() {
        return shutdownTimeout;
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

    public void setPort(int port) {
        this.port = port;
    }

    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
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
