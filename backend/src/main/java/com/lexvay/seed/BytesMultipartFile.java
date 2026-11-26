package com.lexvay.seed;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/** A MultipartFile backed by bytes, so the seed can go through the same upload path as the API. */
final class BytesMultipartFile implements MultipartFile {

    private final String filename;
    private final String contentType;
    private final byte[] bytes;

    BytesMultipartFile(String filename, String contentType, byte[] bytes) {
        this.filename = filename;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return filename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return bytes.length == 0; }
    @Override public long getSize() { return bytes.length; }
    @Override public byte[] getBytes() { return bytes.clone(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), bytes); }
}
