package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Spec section 9: the words "compliant" and "meets requirements" never appear in the UI or in user-facing backend text. */
class ForbiddenWordsTest {

    static final List<String> FORBIDDEN = List.of("compliant", "meets requirements", "non-compliant", "noncompliant");

    @Test
    void frontendSourcesAndBackendPromptsContainNoForbiddenWords() throws IOException {
        List<String> offenders = new ArrayList<>();
        scan(Path.of("..", "frontend", "src"), List.of(".ts", ".html"), offenders);
        scan(Path.of("src", "main", "resources", "llm"), List.of(".txt", ".json"), offenders);
        scan(Path.of("src", "main", "java"), List.of(".java"), offenders);
        scan(Path.of("..", "seed"), List.of(".md", ".yml"), offenders);
        assertThat(offenders).isEmpty();
    }

    private static void scan(Path root, List<String> suffixes, List<String> offenders) throws IOException {
        assertThat(root).isDirectory();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> suffixes.stream().anyMatch(s -> p.toString().endsWith(s))).toList()) {
                if (file.toString().contains("node_modules") || file.getFileName().toString().equals("ForbiddenWordsTest.java")) {
                    continue;
                }
                String text = Files.readString(file).toLowerCase(Locale.ROOT);
                for (String word : FORBIDDEN) {
                    if (text.contains(word)) {
                        offenders.add(file + " contains \"" + word + "\"");
                    }
                }
            }
        }
    }
}
