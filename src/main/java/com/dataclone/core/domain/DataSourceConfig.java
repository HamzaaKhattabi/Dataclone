package com.dataclone.core.domain;

/**
 * Configuration for connecting to an Oracle database.
 * The isProd flag controls whether extraction is allowed (blocked for prod until T4).
 */
public record DataSourceConfig(String jdbcUrl, String username, String password, boolean isProd) {

    public static DataSourceConfig nonProd(String jdbcUrl, String username, String password) {
        return new DataSourceConfig(jdbcUrl, username, password, false);
    }

    public static DataSourceConfig prod(String jdbcUrl, String username, String password) {
        return new DataSourceConfig(jdbcUrl, username, password, true);
    }
}
