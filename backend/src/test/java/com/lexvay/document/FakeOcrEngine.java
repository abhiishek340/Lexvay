package com.lexvay.document;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The OCR engine of every database test. Unavailable (like the production default {@code LEXVAY_OCR=off}) until a
 * test calls {@link #enable()}; results are handed out in the order pages are recognised. Tests call {@link #reset()}.
 */
@Primary
@Component
public class FakeOcrEngine implements OcrEngine {

    private boolean available;
    private final Deque<Optional<OcrPage>> results = new ArrayDeque<>();
    private final List<BufferedImage> seen = new ArrayList<>();

    public synchronized void reset() {
        available = false;
        results.clear();
        seen.clear();
    }

    public synchronized FakeOcrEngine enable() {
        available = true;
        return this;
    }

    /** The next page that is recognised reads as this text. */
    public synchronized FakeOcrEngine page(String text, double confidence) {
        results.add(Optional.of(new OcrPage(text, confidence)));
        return this;
    }

    /** The next page that is recognised yields no words. */
    public synchronized FakeOcrEngine blankPage() {
        results.add(Optional.empty());
        return this;
    }

    public synchronized List<BufferedImage> imagesSeen() {
        return List.copyOf(seen);
    }

    @Override
    public synchronized boolean available() {
        return available;
    }

    @Override
    public synchronized Optional<OcrPage> recognize(BufferedImage page) {
        seen.add(page);
        Optional<OcrPage> next = results.poll();
        return next == null ? Optional.empty() : next;
    }
}
