package com.lexvay.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Binds every connection to the current tenant on checkout and clears the binding on return. With no
 * tenant set the session setting is reset, so the policies see NULL and no tenant row is visible or
 * writable. The flags {@code lexvay.worker} and {@code lexvay.auth_lookup} are transaction-local and
 * are also reset here as a precaution.
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
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        clearQuietly(connection);
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void clearQuietly(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            if (!connection.isClosed()) {
                statement.execute("RESET lexvay.tenant_id");
            }
        } catch (SQLException ignored) {
            // The connection may be inside an aborted transaction; the next checkout re-binds anyway.
        }
    }
}
