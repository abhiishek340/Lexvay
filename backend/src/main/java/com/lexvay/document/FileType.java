package com.lexvay.document;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.MediaType;

/** The three accepted upload formats, detected from the file name and the leading bytes. */
public enum FileType {
    PDF(MediaType.APPLICATION_PDF),
    DOCX(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
    /** Text uploads are accepted only as valid UTF-8, and rule text is stored as UTF-8. */
    TXT(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));

    private final MediaType mediaType;

    FileType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    /** The media type the original file is served with. */
    public MediaType mediaType() {
        return mediaType;
    }

    public static FileType detect(String filename, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") && startsWith(bytes, "%PDF-")) {
            return PDF;
        }
        if (name.endsWith(".docx") && bytes.length > 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4) {
            return DOCX;
        }
        if (name.endsWith(".txt") && isUtf8Text(bytes)) {
            return TXT;
        }
        throw new IllegalArgumentException("Unsupported file type. Upload a text-based PDF, a DOCX or a UTF-8 TXT file");
    }

    public static FileType fromFilename(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return PDF;
        }
        if (name.endsWith(".docx")) {
            return DOCX;
        }
        if (name.endsWith(".txt")) {
            return TXT;
        }
        throw new IllegalArgumentException("Unsupported file type: " + filename);
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < p.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (bytes[i] != p[i]) {
                return false;
            }
        }
        return true;
    }

    static boolean isUtf8Text(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
