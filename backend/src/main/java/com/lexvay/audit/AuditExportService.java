package com.lexvay.audit;

import com.lexvay.audit.AuditRepository.ExportRow;
import com.lexvay.common.CsvWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * The audit log as a CSV file for an auditor (F5a). Rows go from the database cursor through {@link CsvWriter} to the
 * response as they arrive, so the log's size does not matter to the heap (D-377). Handing the log out is an action
 * someone is accountable for, so it is audited like the register export (D-274), after the last row. The last four
 * columns let an auditor recompute the hash chain from the file (SEC-3, D-535); an entry not yet sealed has no Seq or Row hash.
 */
@Service
public class AuditExportService {

    static final List<String> HEADER = List.of("Time (UTC)", "Actor", "Action", "Record type", "Record ID", "Details",
            "Seq", "Row hash", "Entry ID", "Actor ID");

    private final AuditRepository repository;
    private final AuditService audit;

    public AuditExportService(AuditRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /**
     * Writes the matching entries, newest first.
     *
     * @param destination opens the output; called once, when the first line is ready, so a request that fails before
     *        any row (a value the database refuses) still gets an ordinary error response and no file headers
     * @throws IOException when the destination cannot be written, for example because the client left
     */
    public void export(AuditFilter filter, Supplier<Writer> destination) throws IOException {
        Streaming streaming = new Streaming(destination);
        boolean complete = false;
        try {
            repository.streamForExport(filter, streaming::write);
            streaming.finish();
            complete = true;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } finally {
            if (streaming.started()) {
                Map<String, Object> detail = new LinkedHashMap<>(filter.auditDetail());
                detail.put("rowCount", streaming.rows());
                detail.put("complete", complete);
                audit.record("AUDIT_EXPORTED", "audit_log", null, detail);
            }
        }
    }

    /** The CSV being written; opens its destination and writes the header with the first line. */
    private static final class Streaming {

        private final Supplier<Writer> destination;
        private Writer out;
        private CsvWriter csv;
        private long rows;

        Streaming(Supplier<Writer> destination) {
            this.destination = destination;
        }

        void write(ExportRow row) {
            start();
            csv.row(Arrays.asList(row.time().toString(), row.actor(), row.action(), row.entityType(),
                    Objects.toString(row.entityId(), null), row.detail(), Objects.toString(row.seq(), null), row.rowHash(),
                    row.entryId().toString(), Objects.toString(row.actorId(), null)));
            rows++;
        }

        void finish() throws IOException {
            start();
            out.flush();
        }

        boolean started() {
            return csv != null;
        }

        long rows() {
            return rows;
        }

        private void start() {
            if (csv == null) {
                out = destination.get();
                csv = CsvWriter.to(out).row(HEADER);
            }
        }
    }
}
