package com.lexvay.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/** Default file store: a directory on the local filesystem. */
public class LocalFileStore implements FileStore {

    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)*$");

    private final Path root;

    public LocalFileStore(Path root) {
        try {
            this.root = Files.createDirectories(root).toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create file store directory " + root, e);
        }
    }

    /** Writes into a temporary file beside the target and moves it into place, so a reader never sees half an object. */
    private interface Fill {
        void into(Path temp) throws IOException;
    }

    @Override
    public void put(String key, byte[] content) {
        store(key, temp -> Files.write(temp, content));
    }

    @Override
    public void put(String key, Path file) {
        store(key, temp -> Files.copy(file, temp, StandardCopyOption.REPLACE_EXISTING));
    }

    private void store(String key, Fill fill) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                fill.into(temp);
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + key, e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    Path resolve(String key) {
        if (key == null || !SAFE_KEY.matcher(key).matches() || key.contains("..")) {
            throw new IllegalArgumentException("Invalid file store key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid file store key");
        }
        return resolved;
    }
}
