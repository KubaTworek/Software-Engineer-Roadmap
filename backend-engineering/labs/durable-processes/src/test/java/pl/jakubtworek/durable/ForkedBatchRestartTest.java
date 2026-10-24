package pl.jakubtworek.durable;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.*;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ForkedBatchRestartTest {
    @Test @Timeout(60)
    void checkpointSurvivesActualJvmExitAndNewJvmResumes(@TempDir Path directory) throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("repository").toString().replace('\\','/');
        run(directory, url, "fail");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertEquals(200, jdbc.queryForObject("select count(*) from imported", Integer.class));
        run(directory, url, "resume");
        assertEquals(1000, jdbc.queryForObject("select count(*) from imported", Integer.class));
        assertEquals(500500L, jdbc.queryForObject("select sum(id) from imported", Long.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from batch_job_instance", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from batch_job_execution", Integer.class));
    }
    private void run(Path directory, String url, String phase) throws Exception {
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path arguments = directory.resolve(phase + ".args");
        // Argfile omija limit długości polecenia Windows i poprawnie zachowuje spacje.
        Files.writeString(arguments, "-cp\n\"" + cp.replace('\\','/') + "\"\n" + Worker.class.getName()
                + "\n\"" + url + "\"\n" + phase + "\n");
        Path log = directory.resolve(phase + ".log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "@" + arguments).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(25, TimeUnit.SECONDS), "Child JVM deadline");
            assertEquals(0, process.exitValue(), () -> read(log));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
    private String read(Path path) { try { return Files.readString(path); } catch (Exception e) { return e.toString(); } }
    public static class Worker {
        public static void main(String[] args) throws Exception {
            var source = new DriverManagerDataSource(args[0], "sa", "");
            boolean fail = args[1].equals("fail");
            if (fail) {
                new ResourceDatabasePopulator(new ClassPathResource("org/springframework/batch/core/schema-h2.sql")).execute(source);
                var jdbc = new JdbcTemplate(source);
                jdbc.execute("create table source_rows(id int primary key)");
                jdbc.execute("create table imported(id int primary key)");
                jdbc.execute("insert into source_rows select x from system_range(1,1000)");
            }
            var tx = new DataSourceTransactionManager(source);
            var factory = new JobRepositoryFactoryBean();
            factory.setDataSource(source); factory.setTransactionManager(tx); factory.afterPropertiesSet();
            var repository = factory.getObject();
            var launcher = new TaskExecutorJobLauncher(); launcher.setJobRepository(repository); launcher.afterPropertiesSet();
            var execution = launcher.run(RestartableImportJob.create(source, repository, tx, id -> fail && id == 251),
                    new JobParametersBuilder().addString("input", "immutable-v1").toJobParameters());
            if (execution.getStatus() != (fail ? BatchStatus.FAILED : BatchStatus.COMPLETED))
                throw new IllegalStateException("Unexpected batch status: " + execution.getStatus());
        }
    }
}
