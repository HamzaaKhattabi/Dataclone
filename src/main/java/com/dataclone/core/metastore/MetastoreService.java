package com.dataclone.core.metastore;

import com.dataclone.core.domain.ExtractionPackage;
import com.dataclone.core.domain.ExtractionTrace;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for recording extraction traces in the metastore (Supabase/Postgres).
 * Per ADR 0009: only non-sensitive metadata is stored; no row values, no substitution dictionary.
 */
public class MetastoreService {

    private static final Logger log = LoggerFactory.getLogger(MetastoreService.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MetastoreService(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Initializes the metastore schema if it doesn't exist.
     */
    public void initializeSchema() throws SQLException {
        try (Connection conn = getConnection()) {
            String ddl =
                    """
                    CREATE TABLE IF NOT EXISTS extraction_traces (
                        extraction_id UUID PRIMARY KEY,
                        application_name VARCHAR(255) NOT NULL,
                        seed_table VARCHAR(255) NOT NULL,
                        row_count INT NOT NULL,
                        extracted_at TIMESTAMP NOT NULL,
                        package_id UUID NOT NULL
                    )
                    """;

            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.execute();
            }

            log.info("Metastore schema initialized");
        }
    }

    /**
     * Records a trace of the extraction in the metastore.
     *
     * @param pkg the extraction package (metadata only, no row values stored)
     * @return the created trace record
     */
    public ExtractionTrace recordTrace(ExtractionPackage pkg) throws SQLException {
        ExtractionTrace trace = ExtractionTrace.from(pkg);

        try (Connection conn = getConnection()) {
            String sql =
                    """
                    INSERT INTO extraction_traces
                        (extraction_id, application_name, seed_table, row_count, extracted_at, package_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, trace.extractionId());
                stmt.setString(2, trace.applicationName());
                stmt.setString(3, trace.seedTable());
                stmt.setInt(4, trace.rowCount());
                stmt.setTimestamp(5, Timestamp.from(trace.extractedAt()));
                stmt.setObject(6, trace.packageId());
                stmt.executeUpdate();
            }

            log.info(
                    "Recorded extraction trace {} for package {} (app={}, table={}, rows={})",
                    trace.extractionId(),
                    trace.packageId(),
                    trace.applicationName(),
                    trace.seedTable(),
                    trace.rowCount());

            return trace;
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }
}
