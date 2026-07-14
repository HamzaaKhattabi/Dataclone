package com.dataclone.core.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The extraction package: a portable, already-anonymized artifact containing rows
 * and metadata needed for replay (ADR 0004).
 *
 * <p>For T1 (tracer-bullet), packages contain a single row from a table without
 * dependencies; anonymization is a no-op (table has no PII by assumption).
 */
public record ExtractionPackage(
        @JsonProperty("id") UUID id,
        @JsonProperty("version") String version,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("applicationName") String applicationName,
        @JsonProperty("seed") SeedReference seed,
        @JsonProperty("rows") List<ExtractedRow> rows,
        @JsonProperty("replayOrder") List<String> replayOrder) {

    @JsonCreator
    public ExtractionPackage {}

    public static final String FORMAT_VERSION = "1.0";

    public static ExtractionPackage create(
            String applicationName, SeedReference seed, List<ExtractedRow> rows, List<String> replayOrder) {
        return new ExtractionPackage(
                UUID.randomUUID(), FORMAT_VERSION, Instant.now(), applicationName, seed, rows, replayOrder);
    }
}
