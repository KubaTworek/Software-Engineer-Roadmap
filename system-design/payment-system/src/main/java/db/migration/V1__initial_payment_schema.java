package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Installs the readable SQL laboratory through a versioned Flyway boundary. */
public class V1__initial_payment_schema extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var dataSource = new SingleConnectionDataSource(context.getConnection(), true);
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("schema.sql"));
    }
}
