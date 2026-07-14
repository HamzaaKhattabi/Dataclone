package com.dataclone.core.extraction;

import com.dataclone.core.domain.ExtractionPackage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes and deserializes ExtractionPackage to/from JSON format.
 */
public class PackageSerializer {

    private final ObjectMapper mapper;

    public PackageSerializer() {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    public String toJson(ExtractionPackage pkg) throws IOException {
        return mapper.writeValueAsString(pkg);
    }

    public void toFile(ExtractionPackage pkg, Path path) throws IOException {
        mapper.writeValue(path.toFile(), pkg);
    }

    public ExtractionPackage fromJson(String json) throws IOException {
        return mapper.readValue(json, ExtractionPackage.class);
    }

    public ExtractionPackage fromFile(Path path) throws IOException {
        return mapper.readValue(Files.readString(path), ExtractionPackage.class);
    }
}
