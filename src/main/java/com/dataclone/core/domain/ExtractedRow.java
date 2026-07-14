package com.dataclone.core.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * A single row extracted from the source database, with its column values
 * and metadata needed for replay.
 */
public record ExtractedRow(
        @JsonProperty("table") String table,
        @JsonProperty("columns") List<ColumnMetadata> columns,
        @JsonProperty("values") Map<String, Object> values,
        @JsonProperty("primaryKeyColumns") List<String> primaryKeyColumns) {

    @JsonCreator
    public ExtractedRow {}
}
