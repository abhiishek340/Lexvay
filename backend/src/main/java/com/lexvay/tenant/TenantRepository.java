package com.lexvay.tenant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TenantRepository {

    private final JdbcClient jdbc;

    public TenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, String name, UUID createdBy) {
        jdbc.sql("INSERT INTO tenant (id, tenant_id, name, created_by) VALUES (:id, :id, :name, :createdBy)")
                .param("id", id).param("name", name).param("createdBy", createdBy)
                .update();
    }

    public Optional<Tenant> findById(UUID id) {
        return jdbc.sql("SELECT id, name, created_at FROM tenant WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new Tenant(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)))
                .optional();
    }
}
