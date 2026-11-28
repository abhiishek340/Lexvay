package com.lexvay.tenant;

import com.lexvay.common.TenantContext;
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

    public void insert(UUID id, String name, DataMode dataMode, UUID createdBy) {
        jdbc.sql("INSERT INTO tenant (id, tenant_id, name, data_mode, created_by) VALUES (:id, :id, :name, :dataMode, :createdBy)")
                .param("id", id).param("name", name).param("dataMode", dataMode.name()).param("createdBy", createdBy)
                .update();
    }

    public Optional<Tenant> findById(UUID id) {
        return jdbc.sql("SELECT id, name, created_at, data_mode FROM tenant WHERE id = :id")
                .param("id", id)
                .query((rs, i) -> new Tenant(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class), DataMode.valueOf(rs.getString("data_mode"))))
                .optional();
    }

    /** The mode of the current tenant. Row-level security hides every other tenant, so a missing row means no tenant context worked. */
    public DataMode currentDataMode() {
        return jdbc.sql("SELECT data_mode FROM tenant WHERE id = :id").param("id", TenantContext.require())
                .query((rs, i) -> DataMode.valueOf(rs.getString("data_mode")))
                .optional()
                .orElseThrow(() -> new IllegalStateException("The current tenant is not visible"));
    }
}
