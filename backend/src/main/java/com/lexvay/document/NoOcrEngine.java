package com.lexvay.document;

import java.awt.image.BufferedImage;
import java.util.Optional;

/** OCR switched off (the default): scanned pages stay empty. */
public final class NoOcrEngine implements OcrEngine {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public Optional<OcrPage> recognize(BufferedImage page) {
        throw new IllegalStateException("OCR is not enabled");
    }
}
