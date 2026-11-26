package com.lexvay.program;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProgramRepository {

    private final JdbcClient jdbc;

    public ProgramRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, String name, String licenseType, String catalogYear, UUID createdBy) {
        jdbc.sql("INSERT INTO program (id, tenant_id, name, license_type, catalog_year, created_by) "
                        + "VALUES (:id, :tenantId, :name, :licenseType, :catalogYear, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("name", name)
                .param("licenseType", licenseType).param("catalogYear", catalogYear).param("createdBy", createdBy)
                .update();
    }

    public void insertJurisdiction(UUID programId, UUID jurisdictionId, UUID tenantId, UUID createdBy) {
        jdbc.sql("INSERT INTO program_jurisdiction (program_id, jurisdiction_id, tenant_id, created_by) "
                        + "VALUES (:programId, :jurisdictionId, :tenantId, :createdBy)")
                .param("programId", programId).param("jurisdictionId", jurisdictionId)
                .param("tenantId", tenantId).param("createdBy", createdBy)
                .update();
    }

    public Optional<Program> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE p.id = :id GROUP BY p.id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public List<Program> findAll() {
        return jdbc.sql(SELECT + " GROUP BY p.id ORDER BY p.created_at, p.id").query(this::map).list();
    }

    /** Used by isolation tests: how many rows does an update touch from another tenant's context. */
    public int rename(UUID id, String newName) {
        return jdbc.sql("UPDATE program SET name = :name WHERE id = :id")
                .param("name", newName).param("id", id).update();
    }

    private static final String SELECT = """
            SELECT p.id, p.name, p.license_type, p.catalog_year, p.created_at, p.created_by,
                   array_remove(array_agg(j.code ORDER BY j.code), NULL) AS jurisdictions
            FROM program p
            LEFT JOIN program_jurisdiction pj ON pj.program_id = p.id
            LEFT JOIN jurisdiction j ON j.id = pj.jurisdiction_id
            """;

    private Program map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String[] codes = (String[]) rs.getArray("jurisdictions").getArray();
        return new Program(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("license_type"),
                rs.getString("catalog_year"),
                List.of(codes),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("created_by", UUID.class));
    }
}
