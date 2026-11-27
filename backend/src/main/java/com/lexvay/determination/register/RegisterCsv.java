package com.lexvay.determination.register;

import com.lexvay.common.CsvWriter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The register as CSV: the columns of {@link RegisterRow} in their order, under human labels. Outcomes and statuses
 * are written with the words of the signing dialog; times are ISO-8601 with their offset, as stored.
 */
final class RegisterCsv {

    private record Column(String label, Function<RegisterRow, Object> value) {
    }

    private static final List<Column> COLUMNS = List.of(
            new Column("Determination ID", RegisterRow::determinationId),
            new Column("Program ID", RegisterRow::programId),
            new Column("Program", RegisterRow::programName),
            new Column("License type", RegisterRow::licenseType),
            new Column("Catalog year", RegisterRow::catalogYear),
            new Column("Jurisdiction code", RegisterRow::jurisdictionCode),
            new Column("Jurisdiction", RegisterRow::jurisdictionName),
            new Column("Outcome", r -> r.outcome().label()),
            new Column("Status", r -> r.status().label()),
            new Column("Signed by (user ID)", RegisterRow::signedBy),
            new Column("Signed by", RegisterRow::signedByName),
            new Column("Signed at", RegisterRow::signedAt),
            new Column("Rule version ID", RegisterRow::ruleVersionId),
            new Column("Rule version", RegisterRow::ruleVersionLabel),
            new Column("Rule effective from", RegisterRow::ruleEffectiveFrom),
            new Column("Versions hash", RegisterRow::versionsHash),
            new Column("Assessment ID", RegisterRow::assessmentId),
            new Column("Course versions", RegisterRow::courseVersionCount));

    private RegisterCsv() {
    }

    static byte[] write(List<RegisterRow> rows) {
        CsvWriter csv = new CsvWriter().row(COLUMNS.stream().map(Column::label).toList());
        for (RegisterRow row : rows) {
            csv.row(COLUMNS.stream().map(c -> Objects.toString(c.value().apply(row), null)).toList());
        }
        return csv.toBytes();
    }
}
