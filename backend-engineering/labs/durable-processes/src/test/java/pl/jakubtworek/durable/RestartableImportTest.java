package pl.jakubtworek.durable;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class RestartableImportTest {
    @Test void restartCommitsOnlyWholeChunksAndResumesFromPersistentCheckpoint() throws Exception {
        int rows = Integer.getInteger("import.rows", 1000);
        if (rows < 300) throw new IllegalArgumentException("Minimum 300 wierszy");
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("org/springframework/batch/core/schema-h2.sql")).execute(ds);
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table source_rows(id int primary key)");
        jdbc.execute("create table imported(id int primary key)");
        jdbc.update("insert into source_rows select x from system_range(1,?)", rows);
        var tx = new DataSourceTransactionManager(ds);
        var factory = new JobRepositoryFactoryBean();
        factory.setDataSource(ds); factory.setTransactionManager(tx); factory.afterPropertiesSet();
        var repository = factory.getObject();
        var launcher = new TaskExecutorJobLauncher(); launcher.setJobRepository(repository); launcher.afterPropertiesSet();
        var fail = new AtomicBoolean(true);
        var parameters = new JobParametersBuilder().addString("input", "immutable-v1").toJobParameters();
        java.util.function.Supplier<Job> definition = () -> RestartableImportJob.create(ds, repository, tx, item -> item == 251 && fail.get());
        assertEquals(BatchStatus.FAILED, launcher.run(definition.get(), parameters).getStatus());
        assertEquals(200, jdbc.queryForObject("select count(*) from imported", Integer.class));
        fail.set(false);
        // Nowy reader i nowy Job pobierają offset z JobRepository, nie z pamięci poprzedniej instancji.
        assertEquals(BatchStatus.COMPLETED, launcher.run(definition.get(), parameters).getStatus());
        assertEquals(rows, jdbc.queryForObject("select count(*) from imported", Integer.class));
        assertEquals((long) rows * (rows + 1) / 2, jdbc.queryForObject("select sum(id) from imported", Long.class));
    }
}
