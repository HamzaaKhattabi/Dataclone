package com.dataclone.core.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Reference to a seed row by table name and primary key values.
 * The primary key may be composite (multiple columns).
 */
public record SeedReference(
        @JsonProperty("table") String table, @JsonProperty("primaryKey") Map<String, Object> primaryKey) {

    @JsonCreator
    public SeedReference {}

    public static SeedReference of(String table, String pkColumn, Object pkValue) {
        return new SeedReference(table, Map.of(pkColumn, pkValue));
    }
}
