package com.lexvay.evidencepackage;

import com.lexvay.document.DocumentService;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A ZIP that is the same bytes for the same entries: entries in the order they are added, every one stamped with one
 * fixed time (read as UTC, written without the extended-timestamp field) and carrying no comment or extra field.
 * Each entry's size and SHA-256 are recorded as it is written, for the manifest.
 */
final class PackageZip implements Closeable {

    /** What the manifest says about one entry: the bytes written, not the compressed size. */
    record Entry(String path, long size, String sha256) {
    }

    @FunctionalInterface
    interface TextBody {
        void write(Writer out) throws IOException;
    }

    private final ZipOutputStream zip;
    private final LocalDateTime stamp;
    private final List<Entry> entries = new ArrayList<>();

    PackageZip(OutputStream out, Instant stamp) {
        this.zip = new ZipOutputStream(out);
        this.stamp = LocalDateTime.ofInstant(stamp, ZoneOffset.UTC);
    }

    Entry add(String path, byte[] content) throws IOException {
        try (EntryOutput entry = open(path)) {
            entry.write(content);
            return entry.finish();
        }
    }

    /** Copies {@code content} to a new entry; the caller closes the stream. */
    Entry add(String path, InputStream content) throws IOException {
        try (EntryOutput entry = open(path)) {
            content.transferTo(entry);
            return entry.finish();
        }
    }

    /** An entry of UTF-8 text, written by {@code body}. */
    Entry addText(String path, TextBody body) throws IOException {
        try (EntryOutput entry = open(path)) {
            Writer writer = new OutputStreamWriter(entry, StandardCharsets.UTF_8);
            body.write(writer);
            writer.flush();
            return entry.finish();
        }
    }

    /** The entries added so far, in order. */
    List<Entry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    private EntryOutput open(String path) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTimeLocal(stamp);
        zip.putNextEntry(entry);
        return new EntryOutput(path);
    }

    /** Counts and hashes what goes into one entry; closing it ends the entry, not the ZIP. */
    private final class EntryOutput extends OutputStream {

        private final String path;
        private final MessageDigest digest = DocumentService.sha256Digest();
        private long size;
        private Entry done;

        EntryOutput(String path) {
            this.path = path;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            zip.write(b, off, len);
            digest.update(b, off, len);
            size += len;
        }

        /** Ends the entry and records it. */
        Entry finish() throws IOException {
            if (done == null) {
                zip.closeEntry();
                done = new Entry(path, size, HexFormat.of().formatHex(digest.digest()));
                entries.add(done);
            }
            return done;
        }

        @Override
        public void close() throws IOException {
            finish();
        }
    }
}
