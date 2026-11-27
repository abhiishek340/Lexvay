package com.lexvay.rule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RuleVersionRepository {

    private static final String SELECT = """
            SELECT rv.id, rv.jurisdiction_id, j.code AS jurisdiction_code, rv.license_type, rv.label, rv.source_url,
                   rv.effective_from, rv.supersedes_id, rv.document_id, rv.requirements_approved_by,
                   rv.requirements_approved_at, rv.created_at, rv.created_by,
                   (SELECT count(*) FROM requirement r WHERE r.rule_version_id = rv.id) AS requirement_count
            FROM rule_version rv JOIN jurisdiction j ON j.id = rv.jurisdiction_id
            """;

    private static final RowMapper<RuleVersion> ROW = (rs, i) -> new RuleVersion(
            rs.getObject("id", UUID.class), rs.getObject("jurisdiction_id", UUID.class), rs.getString("jurisdiction_code"),
            rs.getString("license_type"), rs.getString("label"), rs.getString("source_url"),
            rs.getObject("effective_from", LocalDate.class), rs.getObject("supersedes_id", UUID.class),
            rs.getObject("document_id", UUID.class), rs.getObject("requirements_approved_by", UUID.class),
            rs.getObject("requirements_approved_at", java.time.OffsetDateTime.class),
            rs.getObject("created_at", java.time.OffsetDateTime.class), rs.getObject("created_by", UUID.class),
            rs.getLong("requirement_count"));

    private static final RowMapper<Requirement> REQUIREMENT = (rs, i) -> new Requirement(
            rs.getObject("id", UUID.class), rs.getObject("rule_version_id", UUID.class), rs.getString("ref"),
            rs.getString("title"), rs.getString("text"), RequirementType.valueOf(rs.getString("type")),
            rs.getString("numeric_field") == null ? null : NumericField.valueOf(rs.getString("numeric_field")),
            rs.getString("numeric_operator"), rs.getBigDecimal("numeric_value"), rs.getInt("ordinal"));

    private final JdbcClient jdbc;

    public RuleVersionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID jurisdictionId, String licenseType, String label, String sourceUrl,
                       LocalDate effectiveFrom, UUID supersedesId, UUID documentId, UUID createdBy) {
        jdbc.sql("INSERT INTO rule_version (id, tenant_id, jurisdiction_id, license_type, label, source_url, effective_from, supersedes_id, document_id, created_by) "
                        + "VALUES (:id, :tenantId, :jurisdictionId, :licenseType, :label, :sourceUrl, :effectiveFrom, :supersedesId, :documentId, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("jurisdictionId", jurisdictionId)
                .param("licenseType", licenseType).param("label", label).param("sourceUrl", sourceUrl)
                .param("effectiveFrom", effectiveFrom).param("supersedesId", supersedesId)
                .param("documentId", documentId).param("createdBy", createdBy).update();
    }

    public Optional<RuleVersion> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE rv.id = :id").param("id", id).query(ROW).optional();
    }

    public List<RuleVersion> findAll() {
        return jdbc.sql(SELECT + " ORDER BY j.code, rv.license_type, rv.effective_from, rv.created_at").query(ROW).list();
    }

    /** The approved version a program is assessed against (D-010). */
    public Optional<RuleVersion> findApprovedVersion(UUID jurisdictionId, String licenseType, LocalDate asOf) {
        return jdbc.sql(SELECT + " WHERE rv.jurisdiction_id = :jurisdictionId AND rv.license_type = :licenseType "
                        + "AND rv.requirements_approved_at IS NOT NULL AND rv.effective_from <= :asOf "
                        + "ORDER BY rv.effective_from DESC, rv.created_at DESC LIMIT 1")
                .param("jurisdictionId", jurisdictionId).param("licenseType", licenseType).param("asOf", asOf)
                .query(ROW).optional();
    }

    /** The approved version a new draft would supersede: approved, and not itself superseded by an approved version. */
    public Optional<RuleVersion> findCurrentApproved(UUID jurisdictionId, String licenseType) {
        return jdbc.sql(SELECT + " WHERE rv.jurisdiction_id = :jurisdictionId AND rv.license_type = :licenseType "
                        + "AND rv.requirements_approved_at IS NOT NULL "
                        + "AND NOT EXISTS (SELECT 1 FROM rule_version s WHERE s.supersedes_id = rv.id AND s.requirements_approved_at IS NOT NULL) "
                        + "ORDER BY rv.effective_from DESC, rv.created_at DESC LIMIT 1")
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
            jdbc.sql("INSERT INTO requirement (tenant_id, rule_version_id, ordinal, ref, title, text, type, numeric_field, numeric_operator, numeric_value, created_by) "
                            + "VALUES (:tenantId, :ruleVersionId, :ordinal, :ref, :title, :text, :type, :numericField, :numericOperator, :numericValue, :createdBy)")
                    .param("tenantId", tenantId).param("ruleVersionId", ruleVersionId).param("ordinal", i + 1)
                    .param("ref", draft.ref()).param("title", draft.title()).param("text", draft.text())
                    .param("type", draft.type().name())
                    .param("numericField", draft.numericField() == null ? null : draft.numericField().name())
                    .param("numericOperator", draft.numericOperator()).param("numericValue", draft.numericValue())
                    .param("createdBy", createdBy).update();
        }
    }

    public int deleteRequirements(UUID ruleVersionId) {
        return jdbc.sql("DELETE FROM requirement WHERE rule_version_id = :id").param("id", ruleVersionId).update();
    }

    public List<Requirement> requirements(UUID ruleVersionId) {
        return jdbc.sql("SELECT id, rule_version_id, ref, title, text, type, numeric_field, numeric_operator, numeric_value, ordinal "
                        + "FROM requirement WHERE rule_version_id = :id ORDER BY ordinal")
                .param("id", ruleVersionId).query(REQUIREMENT).list();
    }

    public long countRequirements(UUID ruleVersionId) {
        return jdbc.sql("SELECT count(*) FROM requirement WHERE rule_version_id = :id").param("id", ruleVersionId)
                .query(Long.class).single();
    }
}
