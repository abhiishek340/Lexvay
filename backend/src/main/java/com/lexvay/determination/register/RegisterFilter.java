package com.lexvay.determination.register;

import com.lexvay.determination.Outcome;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Filters of the register and its CSV export; null means "any". {@code current} keeps only SIGNED determinations;
 * false includes those a rule or course change has REOPENED.
 */
public record RegisterFilter(UUID programId, String jurisdiction, Outcome outcome, boolean current) {

    /** The filters as the audit entry of an export records them; absent filters are left out. */
    Map<String, Object> auditDetail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        if (programId != null) {
            detail.put("programId", programId.toString());
        }
        if (jurisdiction != null) {
            detail.put("jurisdiction", jurisdiction);
        }
        if (outcome != null) {
            detail.put("outcome", outcome.name());
        }
        detail.put("current", current);
        return detail;
    }
}
