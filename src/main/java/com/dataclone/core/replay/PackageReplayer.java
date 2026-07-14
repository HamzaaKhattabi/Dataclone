package com.dataclone.core.replay;

import com.dataclone.core.domain.ColumnMetadata;
import com.dataclone.core.domain.DataSourceConfig;
import com.dataclone.core.domain.ExtractedRow;
import com.dataclone.core.domain.ExtractionPackage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays an ExtractionPackage into the destination database.
 * Per ADR 0006: uses deferred constraints and preserves original PKs.
 */
public class PackageReplayer {

    private static final Logger log = LoggerFactory.getLogger(PackageReplayer.class);

    private final DataSourceConfig destinationConfig;

    public PackageReplayer(DataSourceConfig destinationConfig) {
        this.destinationConfig = destinationConfig;
    }

    /**
     * Replays the extraction package into the destination database.
     * Inserts all rows with their original PKs, using deferred constraints.
     *
     * @param pkg the extraction package to replay
     * @return the number of rows inserted
     */
    public int replay(ExtractionPackage pkg) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                destinationConfig.jdbcUrl(), destinationConfig.username(), destinationConfig.password())) {

            conn.setAutoCommit(false);

            // Set constraints to deferred mode for this transaction
            try (PreparedStatement stmt = conn.prepareStatement("SET CONSTRAINTS ALL DEFERRED")) {
                stmt.execute();
            }

            int insertedCount = 0;

            for (String tableName : pkg.replayOrder()) {
                List<ExtractedRow> tableRows =
                        pkg.rows().stream().filter(r -> r.table().equals(tableName)).toList();

                for (ExtractedRow row : tableRows) {
                    insertRow(conn, row);
                    insertedCount++;
                }
            }

            conn.commit();
            log.info(
                    "Replayed package {} with {} row(s) into destination",
                    pkg.id(),
                    insertedCount);

            return insertedCount;
        }
    }

    private void insertRow(Connection conn, ExtractedRow row) throws SQLException {
        String sql = buildInsertSql(row);
        log.debug("Executing: {}", sql);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (ColumnMetadata col : row.columns()) {
                Object value = row.values().get(col.name());
                stmt.setObject(paramIndex++, value, col.jdbcType());
            }

            int affected = stmt.executeUpdate();
            if (affected != 1) {
                throw new SQLException("Expected 1 row inserted, got " + affected + " for table " + row.table());
            }
        }
    }

    private String buildInsertSql(ExtractedRow row) {
        List<String> columnNames = row.columns().stream().map(ColumnMetadata::name).toList();

        String columns = String.join(", ", columnNames);
        String placeholders = columnNames.stream().map(c -> "?").collect(Collectors.joining(", "));

        return "INSERT INTO " + row.table() + " (" + columns + ") VALUES (" + placeholders + ")";
    }
}
