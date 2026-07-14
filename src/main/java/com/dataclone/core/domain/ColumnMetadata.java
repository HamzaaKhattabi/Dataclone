package com.dataclone.core.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata for a single column, describing its name, Oracle type, and constraints.
 * Used in extraction packages to enable type-safe replay.
 */
public record ColumnMetadata(
        @JsonProperty("name") String name,
        @JsonProperty("oracleType") String oracleType,
        @JsonProperty("jdbcType") int jdbcType,
        @JsonProperty("nullable") boolean nullable,
        @JsonProperty("precision") int precision,
        @JsonProperty("scale") int scale) {

    @JsonCreator
    public ColumnMetadata {}

    public static ColumnMetadata of(String name, String oracleType, int jdbcType, boolean nullable) {
        return new ColumnMetadata(name, oracleType, jdbcType, nullable, 0, 0);
    }
}
