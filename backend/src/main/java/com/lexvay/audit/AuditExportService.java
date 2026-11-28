package com.lexvay.audit;

import com.lexvay.audit.AuditRepository.ExportRow;
import com.lexvay.common.CsvWriter;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * The audit log as a CSV file for an auditor (F5a). Rows go from the database cursor through {@link CsvWriter} to the
 * response as they arrive, so the log's size does not matter to the heap (D-377). Handing the log out is an action
 * someone is accountable for, so it is audited like the register export (D-274), after the last row.
 */
@Service
public class AuditExportService {

    private final AuditRepository repository;
    private final AuditService audit;
    private final TenantRepository tenants;

    public AuditExportService(AuditRepository repository, AuditService audit, TenantRepository tenants) {
        this.repository = repository;
        this.audit = audit;
        this.tenants = tenants;
    }

    /**
     * Writes the matching entries, newest first.
     *
     * @param destination opens the output; called once, when the first line is ready, so a request that fails before
     *        any row (a value the database refuses) still gets an ordinary error response and no file headers
     * @throws IOException when the destination cannot be written, for example because the client left
     */
    public void export(AuditFilter filter, Supplier<Writer> destination) throws IOException {
        Streaming streaming = new Streaming(destination, tenants.currentDataMode());
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

    /** The CSV being written; opens its destination and writes the header, after the demo notice of a DEMO tenant, with the first line. */
    private static final class Streaming {

        private final Supplier<Writer> destination;
        private final DataMode mode;
        private Writer out;
        private CsvWriter csv;
        private long rows;

        Streaming(Supplier<Writer> destination, DataMode mode) {
            this.destination = destination;
            this.mode = mode;
        }

        void write(ExportRow row) {
            start();
            AuditCsv.write(csv, row);
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
                CsvWriter writer = CsvWriter.to(out);
                if (mode == DataMode.DEMO) {
                    writer.row(List.of(DataMode.DEMO_NOTICE));
                }
                csv = AuditCsv.header(writer);
            }
        }
    }
}
