package com.lexvay.common;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Binds every connection to the current tenant on checkout. With no tenant set the session setting is
 * reset, so the policies see NULL and no tenant row is visible or writable. Binding happens on every
 * checkout and never on return: a pooled connection can carry the previous caller's tenant while it sits
 * idle, but nobody can use it before the next checkout has replaced that value, so the stale value is
 * unreachable. The flags {@code lexvay.worker} and {@code lexvay.auth_lookup} are transaction-local.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bind(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        try {
            var tenant = TenantContext.current();
            if (tenant.isPresent()) {
                try (var statement = connection.prepareStatement("SELECT set_config('lexvay.tenant_id', ?, false)")) {
                    statement.setString(1, tenant.get().toString());
                    statement.execute();
                }
            } else {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("RESET lexvay.tenant_id");
                }
            }
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }
}
