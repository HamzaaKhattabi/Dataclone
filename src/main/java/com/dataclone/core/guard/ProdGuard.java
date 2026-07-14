package com.dataclone.core.guard;

import com.dataclone.core.domain.DataSourceConfig;

/**
 * Guard that prevents extraction from production databases until anonymization (T4) is delivered.
 * This is a transverse safety net: any attempt to extract from a prod source fails immediately.
 */
public final class ProdGuard {

    private static final String BLOCKED_MESSAGE =
            "Extraction from production databases is blocked until anonymization (T4) is delivered. "
                    + "Use a non-prod source or set isProd=false if this is not actually a production database.";

    private ProdGuard() {}

    /**
     * Throws if the source is marked as production.
     * @throws ProdExtractionBlockedException if source.isProd() is true
     */
    public static void assertNotProd(DataSourceConfig source) {
        if (source.isProd()) {
            throw new ProdExtractionBlockedException(BLOCKED_MESSAGE);
        }
    }
}
