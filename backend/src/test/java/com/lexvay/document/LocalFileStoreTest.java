package com.lexvay.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.nio.file.Path;
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
}
