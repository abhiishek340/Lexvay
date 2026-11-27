package com.lexvay.common;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The one CSV writer: RFC 4180 text built in memory. Every field is quoted, quotes inside a field are doubled,
 * records end with CRLF, and the result is encoded as UTF-8. A field that a spreadsheet would read as a formula
 * (it starts with {@code =}, {@code +}, {@code -}, {@code @}, a tab or a carriage return) is prefixed with
 * {@code '}, so opening an export never evaluates anything. Null is written as an empty field.
 */
public final class CsvWriter {

    private static final String CRLF = "\r\n";

    private final StringBuilder out = new StringBuilder();

    public CsvWriter row(List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(protect(fields.get(i)).replace("\"", "\"\"")).append('"');
        }
        out.append(CRLF);
        return this;
    }

    public byte[] toBytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return out.toString();
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
