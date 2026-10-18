package com.example.ecommerce.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimalny test kontraktu schematu. Test kontekstu nie wystarczał, ponieważ
 * Hibernate potrafił zalogować nieudaną komendę DDL i mimo to uruchomić aplikację.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
class SchemaContractTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void criticalTablesAndPromotionValueColumnExist() {
        Integer promotions = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'PROMOTIONS'",
                Integer.class);
        Integer promotionValue = jdbc.queryForObject(
                "select count(*) from information_schema.columns " +
                        "where table_name = 'PROMOTIONS' and column_name = 'PROMOTION_VALUE'",
                Integer.class);

        assertThat(promotions).isEqualTo(1);
        assertThat(promotionValue).isEqualTo(1);
    }
}
