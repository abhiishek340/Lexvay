package com.lexvay.eval;

import com.lexvay.common.YamlFile;
import com.lexvay.document.DocumentService;
import com.lexvay.llm.JsonSchemaValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Finds, validates and selects the answer keys in {@code <eval dir>/keys}. A key is checked against {@code keys/schema.json}
 * and then for internal consistency ({@link AnswerKey#problems()}) before it is used; a key that fails either is refused, not repaired.
 *
 * <p>The split is the leakage guard. A dev run reads only the keys that declare {@code split: dev} and are not named in
 * {@code holdout.lock}; it never binds the content of a holdout key. A holdout run reads only keys that declare
 * {@code split: holdout}, and only when each one's file SHA-256 equals the one the lock records, so a held-out key cannot be
 * edited, swapped or added without the lock changing in a commit of its own.
 */
public final class AnswerKeys {

    /** A key that was read and passed every check, with the id (its file name without {@code .yml}) and the hash of its file. */
    public record Loaded(String id, String sha256, AnswerKey key) {
    }

    private static final String SCHEMA = "schema.json";
    private static final Pattern LOCK_LINE = Pattern.compile("([0-9a-f]{64})\\s+(\\S+\\.yml)");

    private final Path keysDir;
    private final Path lockFile;
    private final JsonSchemaValidator schema;

    public AnswerKeys(ObjectMapper json, Path evalDir) {
        this.keysDir = evalDir.resolve("keys");
        this.lockFile = evalDir.resolve("holdout.lock");
        this.schema = new JsonSchemaValidator(json, json.readTree(read(keysDir.resolve(SCHEMA))));
    }

    /** Every dev key, in file-name order. Holdout keys are not opened beyond the one field that says what they are. */
    public List<Loaded> dev() {
        Map<String, String> locked = lock();
        List<Loaded> keys = new ArrayList<>();
        for (Path file : keyFiles()) {
            if (!locked.containsKey(file.getFileName().toString()) && split(file) == AnswerKey.Split.dev) {
                keys.add(load(file));
            }
        }
        return keys;
    }

    /**
     * Every holdout key, after checking the lock against the files on disk. Refuses when a holdout key is not in the lock,
     * when its hash differs, when the lock names a file that is missing or is not a holdout key.
     */
    public List<Loaded> holdout() {
        Map<String, String> locked = lock();
        List<String> refusals = new ArrayList<>();
        List<Path> holdoutFiles = new ArrayList<>();
        for (Path file : keyFiles()) {
            String name = file.getFileName().toString();
            boolean declaredHoldout = split(file) == AnswerKey.Split.holdout;
            if (declaredHoldout) {
                holdoutFiles.add(file);
            }
            if (declaredHoldout && !locked.containsKey(name)) {
                refusals.add(name + " declares split holdout but is not in holdout.lock");
            } else if (!declaredHoldout && locked.containsKey(name)) {
                refusals.add(name + " is in holdout.lock but does not declare split holdout");
            } else if (declaredHoldout && !locked.get(name).equals(fileHash(file))) {
                refusals.add(name + " differs from holdout.lock (locked " + locked.get(name) + ", file " + fileHash(file) + ")");
            }
        }
        locked.keySet().stream().filter(name -> !Files.exists(keysDir.resolve(name)))
                .forEach(name -> refusals.add("holdout.lock names " + name + ", which does not exist"));
        if (!refusals.isEmpty()) {
            throw new IllegalStateException("The holdout is not run, because the lock does not match the keys: " + String.join("; ", refusals)
                    + ". Updating holdout.lock is a deliberate commit of its own.");
        }
        return holdoutFiles.stream().map(this::load).toList();
    }

    private Loaded load(Path file) {
        String name = file.getFileName().toString();
        JsonNode tree = YamlFile.readTree(file);
        JsonSchemaValidator.Result checked = schema.validate(tree);
        if (!checked.valid()) {
            throw new IllegalStateException("Answer key " + name + " does not match keys/" + SCHEMA + ": " + String.join("; ", checked.errors()));
        }
        AnswerKey key = YamlFile.read(file, AnswerKey.class);
        List<String> problems = key.problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Answer key " + name + " is inconsistent: " + String.join("; ", problems));
        }
        return new Loaded(name.substring(0, name.length() - ".yml".length()), fileHash(file), key);
    }

    private static AnswerKey.Split split(Path file) {
        String declared = YamlFile.readTree(file).path("split").asString("");
        try {
            return AnswerKey.Split.valueOf(declared);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Answer key " + file.getFileName() + " must declare split: dev or split: holdout");
        }
    }

    private List<Path> keyFiles() {
        try (Stream<Path> files = Files.list(keysDir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".yml")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + keysDir, e);
        }
    }

    /** The lock: SHA-256 and file name per line, {@code #} starting a comment. Absent means nothing is locked. */
    private Map<String, String> lock() {
        Map<String, String> entries = new LinkedHashMap<>();
        if (!Files.exists(lockFile)) {
            return entries;
        }
        for (String line : read(lockFile).lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList()) {
            Matcher m = LOCK_LINE.matcher(line);
            if (!m.matches()) {
                throw new IllegalStateException("holdout.lock has a line that is not '<sha256>  <key file>': " + line);
            }
            if (entries.put(m.group(2), m.group(1)) != null) {
                throw new IllegalStateException("holdout.lock names " + m.group(2) + " twice");
            }
        }
        return entries;
    }

    private static String fileHash(Path file) {
        try {
            return DocumentService.sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }
}
