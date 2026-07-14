package com.dataclone.core.extraction;

import com.dataclone.core.domain.ColumnMetadata;
import com.dataclone.core.domain.DataSourceConfig;
import com.dataclone.core.domain.ExtractedRow;
import com.dataclone.core.domain.ExtractionPackage;
import com.dataclone.core.domain.SeedReference;
import com.dataclone.core.guard.ProdGuard;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts a single isolated row from a table without dependencies (T1 scope).
 * Produces an ExtractionPackage conforming to ADR 0004.
 */
public class IsolatedRowExtractor {

    private static final Logger log = LoggerFactory.getLogger(IsolatedRowExtractor.class);

    private final DataSourceConfig sourceConfig;
    private final String applicationName;

    public IsolatedRowExtractor(DataSourceConfig sourceConfig, String applicationName) {
        this.sourceConfig = sourceConfig;
        this.applicationName = applicationName;
    }

    /**
     * Extracts a single row identified by the seed reference.
     *
     * @param seed the table and primary key identifying the row to extract
     * @return an ExtractionPackage containing the row and metadata
     * @throws SQLException if database access fails
     * @throws IllegalArgumentException if the row is not found
     */
    public ExtractionPackage extract(SeedReference seed) throws SQLException {
        ProdGuard.assertNotProd(sourceConfig);

        try (Connection conn =
                DriverManager.getConnection(sourceConfig.jdbcUrl(), sourceConfig.username(), sourceConfig.password())) {

            validateNoDependencies(conn, seed.table());

            ExtractedRow row = fetchRow(conn, seed);
            List<ExtractedRow> rows = List.of(row);
            List<String> replayOrder = List.of(seed.table());

            ExtractionPackage pkg = ExtractionPackage.create(applicationName, seed, rows, replayOrder);
            log.info(
                    "Extracted package {} with {} row(s) from table {}",
                    pkg.id(),
                    rows.size(),
                    seed.table());

            return pkg;
        }
    }

    private void validateNoDependencies(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();

        // Check for outgoing FK (this table references others)
        try (ResultSet rs = meta.getImportedKeys(null, conn.getSchema(), tableName.toUpperCase())) {
            if (rs.next()) {
                throw new IllegalStateException("Table " + tableName
                        + " has outgoing foreign keys. T1 only supports tables without dependencies.");
            }
        }

        // Check for incoming FK (others reference this table)
        try (ResultSet rs = meta.getExportedKeys(null, conn.getSchema(), tableName.toUpperCase())) {
            if (rs.next()) {
                throw new IllegalStateException("Table " + tableName
                        + " has incoming foreign keys. T1 only supports tables without dependencies.");
            }
        }

        log.debug("Table {} has no foreign key dependencies", tableName);
    }

    private ExtractedRow fetchRow(Connection conn, SeedReference seed) throws SQLException {
        List<String> pkColumns = getPrimaryKeyColumns(conn, seed.table());

        String sql = buildSelectSql(seed.table(), seed.primaryKey());
        log.debug("Executing: {}", sql);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (String pkCol : seed.primaryKey().keySet()) {
                stmt.setObject(paramIndex++, seed.primaryKey().get(pkCol));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "Row not found in table " + seed.table() + " with PK " + seed.primaryKey());
                }

                ResultSetMetaData rsmd = rs.getMetaData();
                List<ColumnMetadata> columns = extractColumnMetadata(rsmd);
                Map<String, Object> values = extractValues(rs, rsmd);

                if (rs.next()) {
                    throw new IllegalStateException(
                            "Multiple rows found for PK " + seed.primaryKey() + " in table " + seed.table());
                }

                return new ExtractedRow(seed.table(), columns, values, pkColumns);
            }
        }
    }

    private List<String> getPrimaryKeyColumns(Connection conn, String tableName) throws SQLException {
        List<String> pkColumns = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();

        try (ResultSet rs = meta.getPrimaryKeys(null, conn.getSchema(), tableName.toUpperCase())) {
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
        }

        if (pkColumns.isEmpty()) {
            throw new IllegalStateException("Table " + tableName + " has no primary key defined");
        }

        return pkColumns;
    }

    private String buildSelectSql(String table, Map<String, Object> pkValues) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table).append(" WHERE ");

        List<String> conditions = new ArrayList<>();
        for (String col : pkValues.keySet()) {
            conditions.add(col + " = ?");
        }

        sql.append(String.join(" AND ", conditions));
        return sql.toString();
    }

    private List<ColumnMetadata> extractColumnMetadata(ResultSetMetaData rsmd) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            columns.add(new ColumnMetadata(
                    rsmd.getColumnName(i),
                    rsmd.getColumnTypeName(i),
                    rsmd.getColumnType(i),
                    rsmd.isNullable(i) == ResultSetMetaData.columnNullable,
                    rsmd.getPrecision(i),
                    rsmd.getScale(i)));
        }
        return columns;
    }

    private Map<String, Object> extractValues(ResultSet rs, ResultSetMetaData rsmd) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            String colName = rsmd.getColumnName(i);
            Object value = rs.getObject(i);
            values.put(colName, value);
        }
        return values;
    }
}
