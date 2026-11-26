package com.lexvay.determination;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Product rule 6: SHA-256 over the rule version id and the sorted course version ids (D-056). Anyone can
 * recompute it from the stored determination to prove which versions were signed.
 */
public final class VersionsHash {

    private VersionsHash() {
    }

    public static String compute(UUID ruleVersionId, Collection<UUID> courseVersionIds) {
        String material = "rule_version:" + ruleVersionId + "\n"
                + courseVersionIds.stream().map(UUID::toString).sorted()
                .map(id -> "course_version:" + id).collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
