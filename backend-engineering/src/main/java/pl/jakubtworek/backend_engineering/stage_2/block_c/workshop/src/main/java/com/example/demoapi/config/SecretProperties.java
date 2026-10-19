package com.example.demoapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secret-backed configuration kept separate so diagnostics cannot expose it accidentally. */
@ConfigurationProperties(prefix = "secret")
public class SecretProperties {

    private String dbDsn = "";

    public String getDbDsn() {
        return dbDsn;
    }

    public void setDbDsn(String dbDsn) {
        this.dbDsn = dbDsn;
    }

    public String maskedDatabaseDsn() {
        return dbDsn == null || dbDsn.isBlank() ? "<missing>" : "<configured>";
    }
}
