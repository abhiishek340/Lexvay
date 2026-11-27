package com.lexvay.report;

import com.lexvay.determination.DeterminationStatus;
import com.lexvay.determination.Outcome;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The answer to "is this the file Lexvay produced, unchanged?" (F14): the SHA-256 of the checked bytes and, when a
 * report of the caller's tenant has exactly that content, the report and the determination it records. It states
 * facts about the file; it never says whether the determination is right.
 */
public record ReportCheck(boolean match, String sha256, Match matchDetails) {

    /**
     * The report with that content and the determination it was generated for, as it stands now: a determination
     * reopened since the report was generated says so, with the time it was reopened ({@code reopenedAt} is null
     * unless {@code determinationStatus} is {@code REOPENED}). {@code programId} and {@code outcome} are beyond the
     * fields F14 lists: the screen links to the program's report and draws the seal from them (D-454).
     */
    public record Match(UUID reportId, OffsetDateTime generatedAt, UUID determinationId, UUID programId, String programName,
                        String jurisdictionCode, String ruleVersionLabel, Outcome outcome, String signedByName, OffsetDateTime signedAt,
                        DeterminationStatus determinationStatus, OffsetDateTime reopenedAt, String versionsHash) {
    }

    static ReportCheck of(String sha256, Match match) {
        return new ReportCheck(match != null, sha256, match);
    }
}
