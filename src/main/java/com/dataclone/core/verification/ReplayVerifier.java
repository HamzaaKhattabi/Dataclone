package com.dataclone.core.verification;

import com.dataclone.core.domain.ColumnMetadata;
import com.dataclone.core.domain.DataSourceConfig;
import com.dataclone.core.domain.ExtractedRow;
import com.dataclone.core.domain.ExtractionPackage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that replayed rows match their source values column-by-column.
 */
public class ReplayVerifier {

    private static final Logger log = LoggerFactory.getLogger(ReplayVerifier.class);

    private final DataSourceConfig destinationConfig;

    public ReplayVerifier(DataSourceConfig destinationConfig) {
        this.destinationConfig = destinationConfig;
    }

    /**
     * Verifies all rows in the package exist in the destination with matching values.
     *
     * @param pkg the extraction package to verify
     * @return verification result with any discrepancies
     */
    public VerificationResult verify(ExtractionPackage pkg) throws SQLException {
        List<RowDiscrepancy> discrepancies = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(
                destinationConfig.jdbcUrl(), destinationConfig.username(), destinationConfig.password())) {

            for (ExtractedRow expectedRow : pkg.rows()) {
                verifyRow(conn, expectedRow, discrepancies);
            }
        }

        if (discrepancies.isEmpty()) {
            log.info(
                    "Verification passed for package {} ({} rows)",
                    pkg.id(),
                    pkg.rows().size());
            return VerificationResult.success(pkg.rows().size());
        } else {
            log.warn(
                    "Verification failed for package {} with {} discrepancies",
                    pkg.id(),
                    discrepancies.size());
            return VerificationResult.failure(discrepancies);
        }
    }

    private void verifyRow(Connection conn, ExtractedRow expected, List<RowDiscrepancy> discrepancies)
            throws SQLException {
        String sql = buildSelectSql(expected);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (String pkCol : expected.primaryKeyColumns()) {
                stmt.setObject(paramIndex++, expected.values().get(pkCol));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    discrepancies.add(new RowDiscrepancy(
                            expected.table(),
                            extractPkValues(expected),
                            "Row not found in destination"));
                    return;
                }

                for (ColumnMetadata col : expected.columns()) {
                    Object expectedValue = expected.values().get(col.name());
                    Object actualValue = rs.getObject(col.name());

                    if (!valuesEqual(expectedValue, actualValue)) {
                        discrepancies.add(new RowDiscrepancy(
                                expected.table(),
                                extractPkValues(expected),
                                "Column " + col.name() + " mismatch: expected=" + expectedValue + ", actual="
                                        + actualValue));
                    }
                }
            }
        }
    }

    private String buildSelectSql(ExtractedRow row) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(row.table()).append(" WHERE ");

        List<String> conditions = new ArrayList<>();
        for (String pkCol : row.primaryKeyColumns()) {
            conditions.add(pkCol + " = ?");
        }

        sql.append(String.join(" AND ", conditions));
        return sql.toString();
    }

    private Map<String, Object> extractPkValues(ExtractedRow row) {
        return row.primaryKeyColumns().stream()
                .collect(java.util.stream.Collectors.toMap(
                        col -> col, col -> row.values().get(col)));
    }

    private boolean valuesEqual(Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            return true;
        }

        // Handle numeric comparisons (different numeric types may be returned)
        if (expected instanceof Number && actual instanceof Number) {
            return ((Number) expected).doubleValue() == ((Number) actual).doubleValue();
        }

        return false;
    }

    public record RowDiscrepancy(String table, Map<String, Object> primaryKey, String message) {}

    public record VerificationResult(boolean success, int rowsVerified, List<RowDiscrepancy> discrepancies) {

        public static VerificationResult success(int rowsVerified) {
            return new VerificationResult(true, rowsVerified, List.of());
        }

        public static VerificationResult failure(List<RowDiscrepancy> discrepancies) {
            return new VerificationResult(false, 0, discrepancies);
        }
    }
}
