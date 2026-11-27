package com.lexvay.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Reads a YAML file into a record. Unknown fields are rejected, so a typo in a data file fails at once. */
public final class YamlFile {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private YamlFile() {
    }

    public static <T> T read(Path path, Class<T> type) {
        try {
            return YAML.readValue(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
