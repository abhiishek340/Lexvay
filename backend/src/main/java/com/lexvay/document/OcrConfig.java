package com.lexvay.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the OCR engine from {@code LEXVAY_OCR}: {@code off} (default) or {@code tesseract}. */
@Configuration
public class OcrConfig {

    @Bean
    OcrEngine ocrEngine(@Value("${lexvay.ocr.engine:off}") String engine,
                        @Value("${lexvay.ocr.tesseract-path:tesseract}") String tesseractPath,
                        @Value("${lexvay.ocr.language:eng}") String language) {
        return switch (engine) {
            case "off" -> new NoOcrEngine();
            case "tesseract" -> new TesseractCliOcrEngine(tesseractPath, language);
            default -> throw new IllegalArgumentException("Unknown LEXVAY_OCR value: " + engine);
        };
    }
}
