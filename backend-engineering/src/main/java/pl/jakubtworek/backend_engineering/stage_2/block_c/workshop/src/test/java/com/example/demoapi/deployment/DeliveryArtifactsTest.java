package com.example.demoapi.deployment;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Treats delivery artifacts as one contract with the reference application.
 * A YAML file that parses correctly may still be unusable when names, ports,
 * timeouts or configuration keys drift between layers.
 */
class DeliveryArtifactsTest {

    private static final Path CONFIGURATION = Path.of("..", "configuration", "k8s");
    private static final Path KUBERNETES = Path.of("..", "kubernetes", "k8s");
    private static final Path DOCKERFILE = Path.of("..", "docker", "Dockerfile");

    private final Yaml yaml = new Yaml();

    @Test
    void workshopIsTheOnlyApplicationModuleInStage2c() throws IOException {
        assertThat(Path.of("pom.xml")).exists();
        assertThat(Path.of("src", "main", "java")).isDirectory();

        for (String artifactLayer : List.of("configuration", "docker", "kubernetes")) {
            Path layer = Path.of("..", artifactLayer);
            assertThat(layer.resolve("pom.xml")).doesNotExist();
            try (var files = Files.walk(layer)) {
                assertThat(files.filter(path -> path.toString().endsWith(".java"))).isEmpty();
            }
        }
    }

    @Test
    void runtimeSourcesProvideEveryKeyReferencedByDeployment() throws IOException {
        Map<String, Object> deployment = manifest(KUBERNETES.resolve("deployment.yaml"));
        Map<String, Object> configMap = manifest(CONFIGURATION.resolve("configmap.yaml"));
        Map<String, Object> secret = manifest(CONFIGURATION.resolve("secret.yaml"));

        Set<String> configKeys = referencedKeys(deployment, "configMapKeyRef");
        Set<String> secretKeys = referencedKeys(deployment, "secretKeyRef");

        assertThat(map(configMap.get("data")).keySet()).containsAll(configKeys);
        assertThat(map(secret.get("stringData")).keySet()).containsAll(secretKeys);
        assertThat(map(configMap.get("data"))).containsKey("app.yaml");
    }

    @Test
    void serviceSelectsPodsAndTargetsTheirNamedPort() throws IOException {
        Map<String, Object> deployment = manifest(KUBERNETES.resolve("deployment.yaml"));
        Map<String, Object> service = manifest(KUBERNETES.resolve("service.yaml"));
        Map<String, Object> template = map(map(deployment.get("spec")).get("template"));
        Map<String, Object> serviceSpec = map(service.get("spec"));

        assertThat(serviceSpec.get("selector"))
                .isEqualTo(map(template.get("metadata")).get("labels"));
        assertThat(list(serviceSpec.get("ports")).getFirst().get("targetPort")).isEqualTo("http");
        assertThat(container(deployment).get("ports").toString()).contains("http", "8080");
    }

    @Test
    void probesAndShutdownDescribeTheApplicationLifecycle() throws IOException {
        Map<String, Object> deployment = manifest(KUBERNETES.resolve("deployment.yaml"));
        Map<String, Object> podSpec = podSpec(deployment);
        Map<String, Object> container = container(deployment);

        assertProbe(container, "startupProbe", "/startupz");
        assertProbe(container, "readinessProbe", "/readyz");
        assertProbe(container, "livenessProbe", "/livez");
        assertThat(podSpec.get("terminationGracePeriodSeconds")).isEqualTo(30);
        assertThat(map(manifest(CONFIGURATION.resolve("configmap.yaml")).get("data")))
                .containsEntry("SHUTDOWN_TIMEOUT", "25s");
    }

    @Test
    void deploymentHasSafeRolloutResourcesAndContainerSecurity() throws IOException {
        Map<String, Object> deployment = manifest(KUBERNETES.resolve("deployment.yaml"));
        Map<String, Object> spec = map(deployment.get("spec"));
        Map<String, Object> podSpec = podSpec(deployment);
        Map<String, Object> container = container(deployment);
        Map<String, Object> rollingUpdate = map(map(spec.get("strategy")).get("rollingUpdate"));
        Map<String, Object> resources = map(container.get("resources"));
        Map<String, Object> security = map(container.get("securityContext"));

        assertThat(rollingUpdate).containsEntry("maxUnavailable", 0).containsEntry("maxSurge", 1);
        assertThat(map(resources.get("requests"))).containsKeys("cpu", "memory");
        assertThat(map(resources.get("limits"))).containsKeys("cpu", "memory");
        assertThat(podSpec).containsEntry("automountServiceAccountToken", false);
        assertThat(security)
                .containsEntry("allowPrivilegeEscalation", false)
                .containsEntry("readOnlyRootFilesystem", true);
        assertThat(map(security.get("capabilities")).get("drop")).isEqualTo(List.of("ALL"));
    }

    @Test
    void writablePathsUseVolumesAndAutoscalerHasCpuRequestToScaleAgainst() throws IOException {
        Map<String, Object> deployment = manifest(KUBERNETES.resolve("deployment.yaml"));
        Map<String, Object> hpa = manifest(KUBERNETES.resolve("hpa-cpu.yaml"));
        String volumes = podSpec(deployment).get("volumes").toString();
        String mounts = container(deployment).get("volumeMounts").toString();

        assertThat(volumes).contains("demo-api-config", "emptyDir", "demo-api-data");
        assertThat(mounts).contains("/etc/app", "/tmp/demo-api", "/data/demo-api");
        assertThat(map(map(hpa.get("spec")).get("scaleTargetRef")))
                .containsEntry("kind", "Deployment")
                .containsEntry("name", "demo-api");
        assertThat(map(map(container(deployment).get("resources")).get("requests")))
                .containsKey("cpu");
    }

    @Test
    void imageRunsCanonicalJarAsNonRootExecFormProcess() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE);

        assertThat(dockerfile)
                .contains("ARG JAVA_VERSION=21")
                .contains("demo-api-workshop-1.0.0.jar")
                .contains("USER app")
                .contains("ENTRYPOINT [\"java\", \"-jar\", \"/app/app.jar\"]");
    }

    private Set<String> referencedKeys(Map<String, Object> deployment, String referenceType) {
        return list(container(deployment).get("env")).stream()
                .filter(entry -> entry.containsKey("valueFrom"))
                .map(entry -> map(entry.get("valueFrom")))
                .filter(valueFrom -> valueFrom.containsKey(referenceType))
                .map(valueFrom -> map(valueFrom.get(referenceType)))
                .map(reference -> (String) reference.get("key"))
                .collect(Collectors.toSet());
    }

    private void assertProbe(Map<String, Object> container, String name, String path) {
        assertThat(map(map(container.get(name)).get("httpGet")))
                .containsEntry("path", path)
                .containsEntry("port", "http");
    }

    private Map<String, Object> manifest(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            return yaml.load(reader);
        }
    }

    private Map<String, Object> podSpec(Map<String, Object> deployment) {
        return map(map(map(deployment.get("spec")).get("template")).get("spec"));
    }

    private Map<String, Object> container(Map<String, Object> deployment) {
        return list(podSpec(deployment).get("containers")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
