package com.lexvay.evidencepackage;

import com.lexvay.document.DocumentService;
import com.lexvay.job.PermanentJobFailure;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The bytes of the package file as they are written: counted, hashed, and refused beyond a limit. The limit makes
 * the build fail while it writes, so an oversized package is never finished, let alone stored.
 */
final class StoredFileOutput extends FilterOutputStream {

    private final MessageDigest digest = DocumentService.sha256Digest();
    private final long maxBytes;
    private long size;

    StoredFileOutput(OutputStream file, long maxBytes) {
        super(file);
        this.maxBytes = maxBytes;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (size + len > maxBytes) {
            throw new PermanentJobFailure("The package is larger than " + maxBytes + " bytes, the limit set by lexvay.package.max-bytes");
        }
        out.write(b, off, len);
        digest.update(b, off, len);
        size += len;
    }

    long size() {
        return size;
    }

    /** The SHA-256 of everything written; final once the ZIP is finished. */
    String sha256() {
        return HexFormat.of().formatHex(digest.digest());
    }
}
