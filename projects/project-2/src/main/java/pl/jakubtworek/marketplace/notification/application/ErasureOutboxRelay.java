package pl.jakubtworek.marketplace.notification.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.BiConsumer;

/** Port publikacji przyjmuje wyłącznie referencje. Potwierdzenie brokera poprzedza published_at. */
public final class ErasureOutboxRelay {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    public ErasureOutboxRelay(JdbcTemplate jdbc,PlatformTransactionManager transactions){this.jdbc=jdbc;this.tx=new TransactionTemplate(transactions);}
    public int publish(BiConsumer<UUID,UUID> broker) {
        return tx.execute(status -> {
            var rows=jdbc.query("select event_id,customer_id from notification.erasure_outbox where published_at is null order by created_at limit 100 for update skip locked",
                    (rs,n)->new UUID[]{rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)});
            for(UUID[] row:rows){broker.accept(row[0],row[1]);jdbc.update("update notification.erasure_outbox set published_at=now() where event_id=?",row[0]);}
            return rows.size();
        });
    }
}
