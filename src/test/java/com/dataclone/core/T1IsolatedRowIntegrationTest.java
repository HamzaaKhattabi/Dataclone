package com.dataclone.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dataclone.core.domain.DataSourceConfig;
import com.dataclone.core.domain.ExtractionPackage;
import com.dataclone.core.domain.ExtractionTrace;
import com.dataclone.core.domain.SeedReference;
import com.dataclone.core.extraction.IsolatedRowExtractor;
import com.dataclone.core.extraction.PackageSerializer;
import com.dataclone.core.guard.ProdExtractionBlockedException;
import com.dataclone.core.metastore.MetastoreService;
import com.dataclone.core.replay.PackageReplayer;
import com.dataclone.core.verification.ReplayVerifier;
import com.dataclone.core.verification.ReplayVerifier.VerificationResult;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration test for T1 tracer-bullet:
 * Extract isolated row -> Replay -> Verify
 */
@Testcontainers
class T1IsolatedRowIntegrationTest {

    @Container
    static OracleContainer sourceOracle =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart").withReuse(true);

    @Container
    static OracleContainer destOracle =
            new OracleContainer("gvenzl/oracle-xe:21-slim-faststart").withReuse(true);

    @Container
    static PostgreSQLContainer<?> metastore =
            new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    private static DataSourceConfig sourceConfig;
    private static DataSourceConfig destConfig;
    private static MetastoreService metastoreService;

    @BeforeAll
    static void setupContainers() throws Exception {
        sourceConfig = DataSourceConfig.nonProd(
                sourceOracle.getJdbcUrl(), sourceOracle.getUsername(), sourceOracle.getPassword());

        destConfig = DataSourceConfig.nonProd(
                destOracle.getJdbcUrl(), destOracle.getUsername(), destOracle.getPassword());

        metastoreService = new MetastoreService(
                metastore.getJdbcUrl(), metastore.getUsername(), metastore.getPassword());

        metastoreService.initializeSchema();
    }

    @BeforeEach
    void setupTables() throws Exception {
        // Create isolated table (no FK) in source with test data
        try (Connection conn = DriverManager.getConnection(
                sourceConfig.jdbcUrl(), sourceConfig.username(), sourceConfig.password())) {
            try (Statement stmt = conn.createStatement()) {
                dropTableIfExists(stmt, "ISOLATED_PRODUCT");
                stmt.execute(
                        """
                        CREATE TABLE ISOLATED_PRODUCT (
                            ID NUMBER PRIMARY KEY,
                            NAME VARCHAR2(100) NOT NULL,
                            PRICE NUMBER(10,2),
                            QUANTITY NUMBER
                        )
                        """);
                stmt.execute(
                        "INSERT INTO ISOLATED_PRODUCT (ID, NAME, PRICE, QUANTITY) VALUES (42, 'Widget', 19.99, 100)");
            }
        }

        // Create same table structure in destination (provisioned)
        try (Connection conn = DriverManager.getConnection(
                destConfig.jdbcUrl(), destConfig.username(), destConfig.password())) {
            try (Statement stmt = conn.createStatement()) {
                dropTableIfExists(stmt, "ISOLATED_PRODUCT");
                stmt.execute(
                        """
                        CREATE TABLE ISOLATED_PRODUCT (
                            ID NUMBER PRIMARY KEY,
                            NAME VARCHAR2(100) NOT NULL,
                            PRICE NUMBER(10,2),
                            QUANTITY NUMBER
                        )
                        """);
            }
        }
    }

    private void dropTableIfExists(Statement stmt, String tableName) {
        try {
            stmt.execute("DROP TABLE " + tableName + " CASCADE CONSTRAINTS PURGE");
        } catch (SQLException e) {
            // ORA-00942: table or view does not exist - this is expected
            if (!e.getMessage().contains("ORA-00942")) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void extractReplayVerify_isolatedRow_success() throws Exception {
        // Given: a seed reference to an isolated row
        SeedReference seed = SeedReference.of("ISOLATED_PRODUCT", "ID", 42);

        // When: extract the row
        IsolatedRowExtractor extractor = new IsolatedRowExtractor(sourceConfig, "test-app");
        ExtractionPackage pkg = extractor.extract(seed);

        // Then: package is valid
        assertThat(pkg.id()).isNotNull();
        assertThat(pkg.version()).isEqualTo("1.0");
        assertThat(pkg.applicationName()).isEqualTo("test-app");
        assertThat(pkg.seed()).isEqualTo(seed);
        assertThat(pkg.rows()).hasSize(1);
        assertThat(pkg.replayOrder()).containsExactly("ISOLATED_PRODUCT");

        // And: the row contains expected values
        var row = pkg.rows().get(0);
        assertThat(row.table()).isEqualTo("ISOLATED_PRODUCT");
        assertThat(row.values().get("ID")).isEqualTo(new BigDecimal(42));
        assertThat(row.values().get("NAME")).isEqualTo("Widget");

        // When: serialize and deserialize the package
        PackageSerializer serializer = new PackageSerializer();
        String json = serializer.toJson(pkg);
        assertThat(json).contains("\"id\"").contains("\"rows\"").contains("ISOLATED_PRODUCT");

        ExtractionPackage deserialized = serializer.fromJson(json);
        assertThat(deserialized.id()).isEqualTo(pkg.id());

        // When: record trace in metastore
        ExtractionTrace trace = metastoreService.recordTrace(pkg);
        assertThat(trace.extractionId()).isNotNull();
        assertThat(trace.applicationName()).isEqualTo("test-app");
        assertThat(trace.seedTable()).isEqualTo("ISOLATED_PRODUCT");
        assertThat(trace.rowCount()).isEqualTo(1);
        assertThat(trace.packageId()).isEqualTo(pkg.id());

        // When: replay into destination
        PackageReplayer replayer = new PackageReplayer(destConfig);
        int inserted = replayer.replay(pkg);
        assertThat(inserted).isEqualTo(1);

        // Then: verification passes
        ReplayVerifier verifier = new ReplayVerifier(destConfig);
        VerificationResult result = verifier.verify(pkg);
        assertThat(result.success()).isTrue();
        assertThat(result.rowsVerified()).isEqualTo(1);
        assertThat(result.discrepancies()).isEmpty();
    }

    @Test
    void extract_fromProdSource_blocked() {
        // Given: a production data source
        DataSourceConfig prodConfig =
                DataSourceConfig.prod(sourceOracle.getJdbcUrl(), sourceOracle.getUsername(), sourceOracle.getPassword());

        SeedReference seed = SeedReference.of("ISOLATED_PRODUCT", "ID", 42);
        IsolatedRowExtractor extractor = new IsolatedRowExtractor(prodConfig, "test-app");

        // When/Then: extraction is blocked
        assertThatThrownBy(() -> extractor.extract(seed))
                .isInstanceOf(ProdExtractionBlockedException.class)
                .hasMessageContaining("blocked")
                .hasMessageContaining("anonymization");
    }

    @Test
    void extract_nonExistentRow_fails() {
        SeedReference seed = SeedReference.of("ISOLATED_PRODUCT", "ID", 999);
        IsolatedRowExtractor extractor = new IsolatedRowExtractor(sourceConfig, "test-app");

        assertThatThrownBy(() -> extractor.extract(seed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }
}
