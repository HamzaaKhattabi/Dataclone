package com.dataclone.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A trace record stored in the metastore after each extraction.
 * Contains only non-sensitive metadata (ADR 0009): no row values, no substitution dictionary.
 */
public record ExtractionTrace(
        UUID extractionId,
        String applicationName,
        String seedTable,
        int rowCount,
        Instant extractedAt,
        UUID packageId) {

    public static ExtractionTrace from(ExtractionPackage pkg) {
        return new ExtractionTrace(
                UUID.randomUUID(),
                pkg.applicationName(),
                pkg.seed().table(),
                pkg.rows().size(),
                pkg.createdAt(),
                pkg.id());
    }
}
