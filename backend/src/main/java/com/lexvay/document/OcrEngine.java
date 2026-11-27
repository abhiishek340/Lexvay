package com.lexvay.document;

import java.awt.image.BufferedImage;
import java.util.Optional;

/** The seam for optical character recognition of a rendered page. Off unless the server is configured for it. */
public interface OcrEngine {

    boolean available();

    /** The recognised page, or empty when the engine found no words. A failing engine throws {@link OcrFailedException}. */
    Optional<OcrPage> recognize(BufferedImage page);
}
