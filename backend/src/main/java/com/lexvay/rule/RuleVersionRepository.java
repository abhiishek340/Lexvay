package com.lexvay.rule;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RuleVersionRepository {

    /**
     * Reads {@code rule_version_current}: the legal status and dates are those of the newest correction, or the
     * creation values while there is none.
     */
    private static final String COLUMNS = """
            rv.id, rv.jurisdiction_id, j.code AS jurisdiction_code, rv.license_type, rv.label, rv.source_url,
            rv.legal_status, rv.effective_from, rv.effective_to, rv.published_at, rv.adopted_at, rv.dates_basis,
            rv.observed_at, rv.supersedes_id, rv.document_id, rv.requirements_approved_by, rv.requirements_approved_at,
            rv.created_at, rv.created_by,
            (SELECT count(*) FROM requirement r WHERE r.rule_version_id = rv.id) AS requirement_count
            FROM rule_version_current rv JOIN jurisdiction j ON j.id = rv.jurisdiction_id
            """;

    private static final String SELECT = "SELECT " + COLUMNS;

    /** The first row per jurisdiction, in the order the selection that follows gives. */
    private static final String SELECT_FIRST_PER_JURISDICTION = "SELECT DISTINCT ON (rv.jurisdiction_id) " + COLUMNS;

    /** The approved versions of one license type in some jurisdictions that are legally in force on {@code :asOf} (D-556). */
    private static final String IN_FORCE = """
             WHERE rv.jurisdiction_id IN (:jurisdictionIds) AND rv.license_type = :licenseType
               AND rv.requirements_approved_at IS NOT NULL AND rv.legal_status IN ('IN_FORCE', 'FINAL_NOT_EFFECTIVE')
               AND rv.effective_from <= :asOf AND (rv.effective_to IS NULL OR rv.effective_to > :asOf)
             ORDER BY rv.jurisdiction_id, rv.effective_from DESC, rv.created_at DESC""";

    /** The final versions of one license type in some jurisdictions that are not yet effective on {@code :asOf}. */
    private static final String UPCOMING = """
             WHERE rv.jurisdiction_id IN (:jurisdictionIds) AND rv.license_type = :licenseType
               AND rv.requirements_approved_at IS NOT NULL AND rv.legal_status = 'FINAL_NOT_EFFECTIVE'
               AND rv.effective_from > :asOf
             ORDER BY rv.jurisdiction_id, rv.effective_from, rv.created_at""";

    private static final RowMapper<RuleVersion> ROW = (rs, i) -> new RuleVersion(
            rs.getObject("id", UUID.class), rs.getObject("jurisdiction_id", UUID.class), rs.getString("jurisdiction_code"),
            rs.getString("license_type"), rs.getString("label"), rs.getString("source_url"),
            new LegalPosition(LegalStatus.valueOf(rs.getString("legal_status")), rs.getObject("effective_from", LocalDate.class),
                    rs.getObject("effective_to", LocalDate.class), rs.getObject("published_at", LocalDate.class),
                    rs.getObject("adopted_at", LocalDate.class), rs.getString("dates_basis")),
            rs.getObject("observed_at", OffsetDateTime.class), rs.getObject("supersedes_id", UUID.class),
            rs.getObject("document_id", UUID.class), rs.getObject("requirements_approved_by", UUID.class),
            rs.getObject("requirements_approved_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class), rs.getObject("created_by", UUID.class),
            rs.getLong("requirement_count"));

    private static final RowMapper<Requirement> REQUIREMENT = (rs, i) -> new Requirement(
            rs.getObject("id", UUID.class), rs.getObject("rule_version_id", UUID.class), rs.getString("ref"),
            rs.getString("title"), rs.getString("text"), RequirementType.valueOf(rs.getString("type")),
            rs.getString("numeric_field") == null ? null : NumericField.valueOf(rs.getString("numeric_field")),
            rs.getString("numeric_operator"), rs.getBigDecimal("numeric_value"), rs.getInt("ordinal"),
            List.of((String[]) rs.getArray("search_terms").getArray()));

    private final JdbcClient jdbc;

    public RuleVersionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID jurisdictionId, String licenseType, String label, String sourceUrl,
                       LegalPosition legal, OffsetDateTime observedAt, UUID supersedesId, UUID documentId, UUID createdBy) {
        jdbc.sql("INSERT INTO rule_version (id, tenant_id, jurisdiction_id, license_type, label, source_url, legal_status, "
                        + "effective_from, effective_to, published_at, adopted_at, dates_basis, observed_at, supersedes_id, document_id, created_by) "
                        + "VALUES (:id, :tenantId, :jurisdictionId, :licenseType, :label, :sourceUrl, :legalStatus, "
                        + ":effectiveFrom, :effectiveTo, :publishedAt, :adoptedAt, :datesBasis, :observedAt, :supersedesId, :documentId, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("jurisdictionId", jurisdictionId)
                .param("licenseType", licenseType).param("label", label).param("sourceUrl", sourceUrl)
                .param("legalStatus", legal.status().name()).param("effectiveFrom", legal.effectiveFrom())
                .param("effectiveTo", legal.effectiveTo()).param("publishedAt", legal.publishedAt())
                .param("adoptedAt", legal.adoptedAt()).param("datesBasis", legal.datesBasis())
                .param("observedAt", observedAt).param("supersedesId", supersedesId)
                .param("documentId", documentId).param("createdBy", createdBy).update();
    }

    /** Appends a correction: the newest row of a version is its current status (D-552). Rows are never changed. */
    public void insertStatus(UUID tenantId, UUID ruleVersionId, LegalPosition legal, UUID recordedBy) {
        jdbc.sql("INSERT INTO rule_version_status (tenant_id, rule_version_id, legal_status, effective_from, effective_to, "
                        + "published_at, adopted_at, dates_basis, recorded_by) "
                        + "VALUES (:tenantId, :ruleVersionId, :legalStatus, :effectiveFrom, :effectiveTo, :publishedAt, :adoptedAt, :datesBasis, :recordedBy)")
                .param("tenantId", tenantId).param("ruleVersionId", ruleVersionId).param("legalStatus", legal.status().name())
                .param("effectiveFrom", legal.effectiveFrom()).param("effectiveTo", legal.effectiveTo())
                .param("publishedAt", legal.publishedAt()).param("adoptedAt", legal.adoptedAt())
                .param("datesBasis", legal.datesBasis()).param("recordedBy", recordedBy).update();
    }

    public Optional<RuleVersion> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE rv.id = :id").param("id", id).query(ROW).optional();
    }

    public List<RuleVersion> findAll() {
        return jdbc.sql(SELECT + " ORDER BY j.code, rv.license_type, rv.effective_from NULLS LAST, rv.created_at").query(ROW).list();
    }

    /**
     * The version each jurisdiction is assessed against on {@code asOf}: approved, in force or final and already
     * effective, and not ended. Never the newest version when none matches: a jurisdiction without one is absent (D-556).
     */
    public Map<UUID, RuleVersion> findInForce(Collection<UUID> jurisdictionIds, String licenseType, LocalDate asOf) {
        return byJurisdiction(jurisdictionIds, licenseType, asOf, IN_FORCE);
    }

    /** The next approved version of each jurisdiction that is final but not yet effective on {@code asOf}. */
    public Map<UUID, RuleVersion> findUpcoming(Collection<UUID> jurisdictionIds, String licenseType, LocalDate asOf) {
        return byJurisdiction(jurisdictionIds, licenseType, asOf, UPCOMING);
    }

    /** The first version per jurisdiction under {@code selection}, in one statement. */
    private Map<UUID, RuleVersion> byJurisdiction(Collection<UUID> jurisdictionIds, String licenseType, LocalDate asOf, String selection) {
        if (jurisdictionIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql(SELECT_FIRST_PER_JURISDICTION + selection).param("jurisdictionIds", jurisdictionIds)
                .param("licenseType", licenseType).param("asOf", asOf).query(ROW).list().stream()
                .collect(Collectors.toMap(RuleVersion::jurisdictionId, Function.identity()));
    }

    /** The approved version a new draft would supersede: approved, and not itself superseded by an approved version. */
    public Optional<RuleVersion> findCurrentApproved(UUID jurisdictionId, String licenseType) {
        return jdbc.sql(SELECT + " WHERE rv.jurisdiction_id = :jurisdictionId AND rv.license_type = :licenseType "
                        + "AND rv.requirements_approved_at IS NOT NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM rule_version s WHERE s.supersedes_id = rv.id AND s.requirements_approved_at IS NOT NULL) "
                        + "ORDER BY rv.effective_from DESC NULLS LAST, rv.created_at DESC LIMIT 1")
                .param("jurisdictionId", jurisdictionId).param("licenseType", licenseType)
                .query(ROW).optional();
    }

    /** An unapproved version that supersedes {@code supersedesId}, or, when that is null, one that supersedes nothing. */
    public Optional<RuleVersion> findDraft(UUID jurisdictionId, String licenseType, UUID supersedesId) {
        return jdbc.sql(SELECT + " WHERE rv.jurisdiction_id = :jurisdictionId AND rv.license_type = :licenseType "
                        + "AND rv.requirements_approved_at IS NULL AND rv.supersedes_id IS NOT DISTINCT FROM :supersedesId "
                        + "ORDER BY rv.created_at DESC LIMIT 1")
                .param("jurisdictionId", jurisdictionId).param("licenseType", licenseType).param("supersedesId", supersedesId)
                .query(ROW).optional();
    }

    public int approve(UUID id, UUID approvedBy) {
        return jdbc.sql("UPDATE rule_version SET requirements_approved_by = :by, requirements_approved_at = now() "
                        + "WHERE id = :id AND requirements_approved_at IS NULL")
                .param("by", approvedBy).param("id", id).update();
    }

    /** Stores the drafts in list order: {@code ordinal} is the position in the list, from 1. */
    public void insertRequirements(UUID tenantId, UUID ruleVersionId, List<RequirementDraft> drafts, UUID createdBy) {
        for (int i = 0; i < drafts.size(); i++) {
            RequirementDraft draft = drafts.get(i);
            jdbc.sql("INSERT INTO requirement (tenant_id, rule_version_id, ordinal, ref, title, text, type, numeric_field, numeric_operator, numeric_value, search_terms, created_by) "
                            + "VALUES (:tenantId, :ruleVersionId, :ordinal, :ref, :title, :text, :type, :numericField, :numericOperator, :numericValue, CAST(:searchTerms AS text[]), :createdBy)")
                    .param("tenantId", tenantId).param("ruleVersionId", ruleVersionId).param("ordinal", i + 1)
                    .param("ref", draft.ref()).param("title", draft.title()).param("text", draft.text())
                    .param("type", draft.type().name())
                    .param("numericField", draft.numericField() == null ? null : draft.numericField().name())
                    .param("numericOperator", draft.numericOperator()).param("numericValue", draft.numericValue())
                    .param("searchTerms", draft.searchTerms().toArray(String[]::new))
                    .param("createdBy", createdBy).update();
        }
    }

    public int deleteRequirements(UUID ruleVersionId) {
        return jdbc.sql("DELETE FROM requirement WHERE rule_version_id = :id").param("id", ruleVersionId).update();
    }

    public List<Requirement> requirements(UUID ruleVersionId) {
        return jdbc.sql("SELECT id, rule_version_id, ref, title, text, type, numeric_field, numeric_operator, numeric_value, ordinal, search_terms "
                        + "FROM requirement WHERE rule_version_id = :id ORDER BY ordinal")
                .param("id", ruleVersionId).query(REQUIREMENT).list();
    }

    public long countRequirements(UUID ruleVersionId) {
        return jdbc.sql("SELECT count(*) FROM requirement WHERE rule_version_id = :id").param("id", ruleVersionId)
                .query(Long.class).single();
    }
}
