package com.lexvay.assessment;

/**
 * The D-066 "current mapping" filter as {@code MappingRepository.CURRENT} spelled it before FIX-3: a correlated
 * {@code NOT EXISTS} evaluated for every mapping. Kept only so tests can prove that the set-based
 * {@link MappingRepository#current} returns exactly the same rows.
 */
public final class LegacyCurrentFilter {

    public static final String SQL = """
             AND NOT EXISTS (
                SELECT 1 FROM rule_version nv
                JOIN requirement nr ON nr.rule_version_id = nv.id
                JOIN mapping nm ON nm.requirement_id = nr.id
                WHERE nv.supersedes_id = r.rule_version_id AND nm.assessment_id = m.assessment_id)
            """;

    private LegacyCurrentFilter() {
    }
}
