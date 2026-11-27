package com.lexvay.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

/**
 * The one CSV writer: RFC 4180 text. Every field is quoted, quotes inside a field are doubled, records end with CRLF,
 * and the result is encoded as UTF-8. A field that a spreadsheet would read as a formula (it starts with {@code =},
 * {@code +}, {@code -}, {@code @}, a tab or a carriage return) is prefixed with {@code '}, so opening an export never
 * evaluates anything. Null is written as an empty field.
 *
 * <p>The no-argument constructor collects the text in memory ({@link #toBytes()}); {@link #to(Appendable)} writes each
 * record to its destination as it is added, for exports too large to hold (the audit log, D-377).
 */
public final class CsvWriter {

    /** The media type of every CSV the API serves. */
    public static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private static final String CRLF = "\r\n";

    private final StringBuilder buffer;
    private final Appendable out;

    public CsvWriter() {
        this.buffer = new StringBuilder();
        this.out = buffer;
    }

    private CsvWriter(Appendable out) {
        this.buffer = null;
        this.out = out;
    }

    public static CsvWriter to(Appendable out) {
        return new CsvWriter(out);
    }

    /** The {@code Content-Disposition} of a download named {@code <prefix>-<today in UTC>.csv}. */
    public static String attachment(String filenamePrefix) {
        return ContentDisposition.attachment().filename(filenamePrefix + "-" + LocalDate.now(ZoneOffset.UTC) + ".csv")
                .build().toString();
    }

    public CsvWriter row(List<String> fields) {
        try {
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append('"').append(protect(fields.get(i)).replace("\"", "\"\"")).append('"');
            }
            out.append(CRLF);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** The text so far; only for a writer created without a destination. */
    public byte[] toBytes() {
        return toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        if (buffer == null) {
            throw new IllegalStateException("This writer streams to its destination and keeps nothing");
        }
        return buffer.toString();
    }

    private static String protect(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> "'" + value;
            default -> value;
        };
    }
}
