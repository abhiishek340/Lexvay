package com.lexvay.rule;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reference data shared by all tenants; read only. */
@Repository
public class JurisdictionRepository {

    private static final RowMapper<Jurisdiction> ROW = (rs, i) ->
            new Jurisdiction(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"));

    private final JdbcClient jdbc;

    public JurisdictionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Jurisdiction> findAll() {
        return jdbc.sql("SELECT id, code, name FROM jurisdiction ORDER BY code").query(ROW).list();
    }

    public List<Jurisdiction> findByCodes(Collection<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT id, code, name FROM jurisdiction WHERE code IN (:codes) ORDER BY code")
                .param("codes", List.copyOf(codes))
                .query(ROW).list();
    }
}
