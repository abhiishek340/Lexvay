package com.lexvay.change;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChangeEventRepository {

    private static final RowMapper<ChangeEvent> ROW = (rs, i) -> new ChangeEvent(rs.getObject("id", UUID.class),
            ChangeKind.valueOf(rs.getString("kind")), rs.getObject("old_id", UUID.class), rs.getObject("new_id", UUID.class),
            ChangeStatus.valueOf(rs.getString("status")), rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("created_by", UUID.class));

    private final JdbcClient jdbc;

    public ChangeEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChangeEvent> findAll() {
        return jdbc.sql("SELECT id, kind, old_id, new_id, status, created_at, created_by FROM change_event ORDER BY created_at DESC, id DESC")
                .query(ROW).list();
    }
}
