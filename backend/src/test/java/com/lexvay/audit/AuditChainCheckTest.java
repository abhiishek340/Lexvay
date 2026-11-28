package com.lexvay.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.audit.AuditCanonical.Entry;
import com.lexvay.audit.AuditChainCheck.Broken;
import com.lexvay.audit.AuditChainCheck.Sealed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The rules of the walk, without a database: what holds, and which seal is named when something does not. */
class AuditChainCheckTest {

    static final UUID TENANT = UUID.randomUUID();

    /** A correct chain of {@code length} seals, numbered from 1. */
    static List<Sealed> chain(int length) {
        List<Sealed> seals = new ArrayList<>();
        String prev = AuditCanonical.GENESIS_HASH;
        for (int seq = 1; seq <= length; seq++) {
            Entry entry = new Entry(UUID.randomUUID(), null, "ACTION_" + seq, "program", null, "{\"n\": " + seq + "}",
                    Instant.parse("2026-11-27T10:00:00Z").plusSeconds(seq));
            String hash = AuditCanonical.rowHash(TENANT, seq, entry, prev);
            seals.add(new Sealed(seq, prev, hash, entry));
            prev = hash;
        }
        return seals;
    }

    private static Optional<Broken> walk(List<Sealed> seals) {
        AuditChainCheck check = new AuditChainCheck(TENANT);
        for (Sealed seal : seals) {
            Optional<Broken> broken = check.next(seal);
            if (broken.isPresent()) {
                return broken;
            }
        }
        return Optional.empty();
    }

    @Test
    void aCorrectChainHolds() {
        assertThat(walk(chain(5))).isEmpty();
        assertThat(walk(List.of())).as("nothing sealed is not broken").isEmpty();
    }

    @Test
    void anEntryThatChangedNamesItsSeal() {
        List<Sealed> seals = new ArrayList<>(chain(5));
        Sealed third = seals.get(2);
        Entry changed = new Entry(third.entry().id(), null, "ACTION_3", "program", null, "{\"n\": 99}", third.entry().createdAt());
        seals.set(2, new Sealed(3, third.prevHash(), third.rowHash(), changed));

        assertThat(walk(seals)).hasValueSatisfying(broken -> {
            assertThat(broken.seq()).isEqualTo(3);
            assertThat(broken.reason()).contains("no longer matches its seal");
        });
    }

    @Test
    void aMissingSealInTheMiddleNamesTheNumberThatIsMissing() {
        List<Sealed> seals = new ArrayList<>(chain(5));
        seals.remove(2);

        assertThat(walk(seals)).hasValueSatisfying(broken -> {
            assertThat(broken.seq()).isEqualTo(3);
            assertThat(broken.reason()).contains("missing");
        });
    }

    @Test
    void aChainThatDoesNotStartAtOneIsMissingItsFirstSeal() {
        List<Sealed> seals = chain(4).subList(1, 4);

        assertThat(walk(seals)).hasValueSatisfying(broken -> assertThat(broken.seq()).isEqualTo(1));
    }

    @Test
    void aSealThatDoesNotLinkToItsPredecessorIsNamed() {
        List<Sealed> seals = new ArrayList<>(chain(4));
        Sealed third = seals.get(2);
        seals.set(2, new Sealed(3, "f".repeat(64), third.rowHash(), third.entry()));

        assertThat(walk(seals)).hasValueSatisfying(broken -> {
            assertThat(broken.seq()).isEqualTo(3);
            assertThat(broken.reason()).contains("does not follow");
        });
    }

    @Test
    void aRewrittenHashIsCaughtEvenWhenTheLinkIsRewrittenToo() {
        List<Sealed> seals = new ArrayList<>(chain(4));
        Sealed second = seals.get(1);
        String forged = "a".repeat(64);
        seals.set(1, new Sealed(2, second.prevHash(), forged, second.entry()));
        Sealed third = seals.get(2);
        seals.set(2, new Sealed(3, forged, third.rowHash(), third.entry()));

        assertThat(walk(seals)).hasValueSatisfying(broken -> assertThat(broken.seq()).isEqualTo(2));
    }

    @Test
    void theFirstBreakIsReportedAndNotALaterOne() {
        List<Sealed> seals = new ArrayList<>(chain(6));
        seals.set(4, new Sealed(5, "0".repeat(64), seals.get(4).rowHash(), seals.get(4).entry()));
        seals.set(1, new Sealed(2, seals.get(1).prevHash(), "b".repeat(64), seals.get(1).entry()));

        assertThat(walk(seals)).hasValueSatisfying(broken -> assertThat(broken.seq()).isEqualTo(2));
    }
}
