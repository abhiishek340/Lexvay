package com.lexvay.audit;

import com.lexvay.audit.AuditRepository.ExportRow;
import com.lexvay.common.CsvWriter;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/** The columns of an audit trail as CSV (F5a), shared by the audit export and the evidence package so they cannot differ. */
public final class AuditCsv {

    static final List<String> HEADER = List.of("Time (UTC)", "Actor", "Action", "Record type", "Record ID", "Details");

    private AuditCsv() {
    }

    /** A writer on {@code out} that has written the header line. */
    public static CsvWriter start(Writer out) {
        return header(CsvWriter.to(out));
    }

    /** Writes the header line to a writer that may already hold lines above it, such as a demo notice. */
    public static CsvWriter header(CsvWriter csv) {
        return csv.row(HEADER);
    }

    public static void write(CsvWriter csv, ExportRow row) {
        csv.row(List.of(row.time().toString(), row.actor(), row.action(), row.entityType(),
                Objects.toString(row.entityId(), ""), row.detail()));
    }
}
