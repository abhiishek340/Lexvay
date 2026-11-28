package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Product rule 9 and the tamper-evident trail (SEC-3, D-530): every audit entry is queued for sealing by the statement
 * that writes it, so there must be exactly one place in the application that inserts into audit_log. Checked at the
 * source level, like the architecture rule for the rules engine.
 */
class Rule9_SingleAuditWritePathTest {

    static final Path MAIN = Path.of("src/main");
    static final Path THE_WRITE_PATH = Path.of("src/main/java/com/lexvay/audit/AuditRepository.java");

    @Test
    void onlyTheAuditRepositoryInsertsIntoTheAuditLog() throws IOException {
        List<String> inserts = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(Files::isRegularFile).filter(p -> !p.toString().contains("db/migration")).toList()) {
                // A statement may be written across concatenated lines, so compare the text with its white space collapsed.
                String text = Files.readString(file).replaceAll("\\s+", " ");
                for (int at = text.indexOf("INSERT INTO audit_log"); at >= 0; at = text.indexOf("INSERT INTO audit_log", at + 1)) {
                    inserts.add(file.toString());
                }
            }
        }

        assertThat(inserts).containsExactly(THE_WRITE_PATH.toString());
    }

    @Test
    void theStatementThatWritesAnEntryAlsoQueuesIt() throws IOException {
        String text = Files.readString(THE_WRITE_PATH);

        assertThat(text).contains("WITH entry AS (INSERT INTO audit_log").contains("INSERT INTO audit_unsealed");
    }
}
