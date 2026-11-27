package com.lexvay.document;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Checkpoints kept in memory with the semantics of the table: the first save of a page wins, a DPI mismatch finds nothing. */
class InMemoryOcrCheckpoints implements OcrCheckpoints {

    record Stored(OcrPage page, int renderDpi) {
    }

    private final Map<Integer, Stored> stored = new TreeMap<>();

    @Override
    public Optional<OcrPage> find(int pageNumber, int renderDpi) {
        return Optional.ofNullable(stored.get(pageNumber)).filter(s -> s.renderDpi() == renderDpi).map(Stored::page);
    }

    @Override
    public void save(int pageNumber, OcrPage page, int renderDpi) {
        stored.putIfAbsent(pageNumber, new Stored(page, renderDpi));
    }

    Map<Integer, Stored> stored() {
        return Map.copyOf(stored);
    }
}
