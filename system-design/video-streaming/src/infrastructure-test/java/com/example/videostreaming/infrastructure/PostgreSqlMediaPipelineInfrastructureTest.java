package com.example.videostreaming.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

/** Dowodzi semantyki migracji i constraintu, której H2 nie może gwarantować. */
@Testcontainers
class PostgreSqlMediaPipelineInfrastructureTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migrationsApplyAndDatabaseRejectsSecondJobForTheSameSourceObject() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();
        assertThat(flyway.migrate().success).isTrue();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into videos(id,title,status,visibility,created_at,updated_at,minimum_plan_code,
                                       drm_protected,license_policy,row_version)
                    values ('10000000-0000-0000-0000-000000000001','video','PROCESSING','PRIVATE',now(),now(),
                            'FREE',false,'STREAMING_ONLY',0)
                    """);
            statement.executeUpdate("""
                    insert into transcoding_jobs(id,video_id,source_object_key,status,attempts,created_at,updated_at)
                    values ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',
                            'raw/source.mp4','PENDING',0,now(),now())
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    insert into transcoding_jobs(id,video_id,source_object_key,status,attempts,created_at,updated_at)
                    values ('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000001',
                            'raw/source.mp4','PENDING',0,now(),now())
                    """))
                    .isInstanceOf(SQLException.class);
        }
    }
}
