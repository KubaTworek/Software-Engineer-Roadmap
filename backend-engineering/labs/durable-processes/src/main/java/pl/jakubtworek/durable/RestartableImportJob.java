package pl.jakubtworek.durable;

import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.function.IntPredicate;

/** Snapshot wejścia musi pozostać niezmienny między restartami tego samego JobInstance. */
public final class RestartableImportJob {
    public static Job create(DataSource source, JobRepository repository, PlatformTransactionManager tx,
                             IntPredicate failBeforeWrite) {
        var reader = new JdbcCursorItemReader<Integer>();
        reader.setName("orderedSource"); reader.setDataSource(source);
        reader.setSql("select id from source_rows order by id");
        reader.setRowMapper((rs, row) -> rs.getInt(1));
        var writer = new JdbcBatchItemWriterBuilder<Integer>().dataSource(source)
                .sql("insert into imported(id) values (?)")
                .itemPreparedStatementSetter((item, ps) -> ps.setInt(1, item)).build();
        var step = new StepBuilder("import", repository).<Integer,Integer>chunk(100, tx)
                .reader(reader).processor(item -> {
                    // Kontrolowana awaria służy zbadaniu rollbacku całej porcji, nie pojedynczego rekordu.
                    if (failBeforeWrite.test(item)) throw new IllegalStateException("Awaria porcji: " + item);
                    return item;
                }).writer(writer).build();
        return new JobBuilder("restartableImport", repository).start(step).build();
    }
}
