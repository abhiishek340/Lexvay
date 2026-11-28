package com.lexvay.audit;

import com.lexvay.audit.AuditRepository.ExportRow;
import com.lexvay.common.CsvWriter;
import java.io.Writer;
import java.util.List;
import java.util.Objects;

/**
 * The columns of an audit trail as CSV (F5a), shared by the audit export and the evidence package so they cannot differ.
 * The last four let a reader recompute the hash chain from the file (SEC-3, D-535); {@code Seq} and {@code Row hash} are
 * empty while an entry waits to be sealed.
 */
public final class AuditCsv {

    static final List<String> HEADER = List.of("Time (UTC)", "Actor", "Action", "Record type", "Record ID", "Details",
            "Seq", "Row hash", "Entry ID", "Actor ID");

    private AuditCsv() {
    }

    /** A writer on {@code out} that has written the header line. */
    public static CsvWriter start(Writer out) {
        return CsvWriter.to(out).row(HEADER);
    }

    public static void write(CsvWriter csv, ExportRow row) {
        csv.row(List.of(row.time().toString(), row.actor(), row.action(), row.entityType(),
                Objects.toString(row.entityId(), ""), row.detail(), Objects.toString(row.seq(), ""),
                Objects.toString(row.rowHash(), ""), row.entryId().toString(), Objects.toString(row.actorId(), "")));
    }
}
