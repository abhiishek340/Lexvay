package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStoreTest {

    @TempDir Path dir;

    @Test
    void roundTrip() {
        var store = new LocalFileStore(dir.resolve("files"));
        assertThat(store.exists("t1/d1")).isFalse();
        store.put("t1/d1", new byte[] {1, 2, 3});
        assertThat(store.exists("t1/d1")).isTrue();
        assertThat(store.get("t1/d1")).containsExactly(1, 2, 3);
    }

    @Test
    void missingKeyFails() {
        var store = new LocalFileStore(dir);
        assertThatThrownBy(() -> store.get("t1/missing")).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void traversalKeysAreRejected() {
        var store = new LocalFileStore(dir);
        for (String bad : new String[] {"../etc/passwd", "/abs", "a/../../b", "", "a//b", ".hidden"}) {
            assertThatThrownBy(() -> store.put(bad, new byte[] {1})).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aFileIsStoredAndOpenedAsAStreamAndStaysWhereItWas() throws IOException {
        var store = new LocalFileStore(dir.resolve("files"));
        Path source = Files.write(dir.resolve("package.zip"), new byte[] {4, 5, 6});

        store.put("t1/package-1", source);

        assertThat(source).exists();
        try (InputStream in = store.open("t1/package-1")) {
            assertThat(in.readAllBytes()).containsExactly(4, 5, 6);
        }
        assertThat(store.get("t1/package-1")).containsExactly(4, 5, 6);
        try (Stream<Path> stored = Files.list(dir.resolve("files/t1"))) {
            assertThat(stored.map(p -> p.getFileName().toString())).containsExactly("package-1");
        }
    }

    @Test
    void aFileThatCannotBeReadLeavesNothingStored() throws IOException {
        var store = new LocalFileStore(dir.resolve("files"));

        assertThatThrownBy(() -> store.put("t1/package-2", dir.resolve("missing.zip"))).isInstanceOf(UncheckedIOException.class);

        assertThat(store.exists("t1/package-2")).isFalse();
        try (Stream<Path> stored = Files.list(dir.resolve("files/t1"))) {
            assertThat(stored).as("not even a temporary file").isEmpty();
        }
    }

    @Test
    void openingAMissingKeyFailsAndATraversalKeyIsRejected() {
        var store = new LocalFileStore(dir);
        assertThatThrownBy(() -> store.open("t1/missing")).isInstanceOf(UncheckedIOException.class);
        assertThatThrownBy(() -> store.open("../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("../x", dir)).isInstanceOf(IllegalArgumentException.class);
    }
}
