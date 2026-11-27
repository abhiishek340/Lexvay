package com.lexvay.document;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the Tesseract command line: {@code tesseract <png> stdout -l <language> --psm 3 tsv}. The command is an
 * argument array, never a shell string. The page image goes into a private temporary directory that is deleted
 * afterwards. Page text and recognised words are never logged; failure messages carry the exit code and at most
 * the first line of Tesseract's standard error, which reports configuration problems, not page content.
 */
public final class TesseractCliOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractCliOcrEngine.class);
    private static final Duration RECOGNIZE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern LANGUAGE = Pattern.compile("[A-Za-z0-9_]+(\\+[A-Za-z0-9_]+)*");
    private static final int MAX_STDERR_CHARS = 200;

    private final String binary;
    private final String language;
    private volatile Boolean available;

    public TesseractCliOcrEngine(String binary, String language) {
        if (binary == null || binary.isBlank()) {
            throw new IllegalArgumentException("LEXVAY_OCR_TESSERACT_PATH must not be empty");
        }
        if (language == null || !LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("LEXVAY_OCR_LANGUAGE must be Tesseract language codes such as eng or eng+deu");
        }
        this.binary = binary;
        this.language = language;
    }

    @Override
    public boolean available() {
        Boolean known = available;
        if (known == null) {
            known = probe();
            available = known;
        }
        return known;
    }

    @Override
    public Optional<OcrPage> recognize(BufferedImage page) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("lexvay-ocr-");
            Path png = directory.resolve("page.png");
            if (!ImageIO.write(page, "png", png.toFile())) {
                throw new OcrFailedException("Page image could not be written as PNG");
            }
            String tsv = run(List.of(binary, png.toString(), "stdout", "-l", language, "--psm", "3", "tsv"),
                    directory, RECOGNIZE_TIMEOUT);
            return TesseractTsv.parse(tsv);
        } catch (IOException e) {
            throw new OcrFailedException("OCR could not prepare the page image", e);
        } finally {
            deleteQuietly(directory);
        }
    }

    private boolean probe() {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("lexvay-ocr-");
            String version = run(List.of(binary, "--version"), directory, VERSION_TIMEOUT);
            log.info("OCR enabled with {} {}", binary, firstLine(version));
            return true;
        } catch (IOException | OcrFailedException e) {
            log.warn("OCR is configured but the Tesseract binary '{}' cannot be run: {}", binary, e.getMessage());
            return false;
        } finally {
            deleteQuietly(directory);
        }
    }

    /** Runs the command with a timeout and returns its standard output. Standard error goes to a file in the private directory. */
    private String run(List<String> command, Path directory, Duration timeout) throws IOException {
        Path stderr = directory.resolve("stderr.txt");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectError(stderr.toFile());
        builder.environment().put("OMP_THREAD_LIMIT", "1");
        Process process = builder.start();
        process.getOutputStream().close();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new OcrFailedException("Tesseract did not finish within " + timeout.toSeconds() + " seconds");
            }
            if (process.exitValue() != 0) {
                throw new OcrFailedException("Tesseract exited with code " + process.exitValue() + stderrHint(stderr));
            }
            return stdout.join();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new OcrFailedException("OCR was interrupted", e);
        }
    }

    private static String stderrHint(Path stderr) {
        try {
            String line = firstLine(Files.readString(stderr, StandardCharsets.UTF_8));
            return line.isEmpty() ? "" : ": " + line.substring(0, Math.min(line.length(), MAX_STDERR_CHARS));
        } catch (IOException e) {
            return "";
        }
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse("").strip();
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            log.warn("Could not delete OCR temporary directory {}", directory);
        }
    }
}
