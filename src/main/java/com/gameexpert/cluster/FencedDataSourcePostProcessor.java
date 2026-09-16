package com.gameexpert.cluster;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.stereotype.Component;

/** Tags all borrowed connections, including async writers, without changing student entities. */
@Component
public final class FencedDataSourcePostProcessor implements BeanPostProcessor, AutoCloseable {
    private Connection schemaConnection;
    private String schemaLock;

    private synchronized void awaitSchemaTurn(DataSource source) {
        if (schemaConnection != null) return;
        try {
            Connection connection = source.getConnection();
            try {
                if (!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")) { connection.close(); return; }
                String lock = "webcraft:schema:" + SqlWriteFences.digest(connection.getCatalog()).substring(0, 32);
                try (java.sql.PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?,180)")) {
                    statement.setString(1, lock);
                    try (java.sql.ResultSet result = statement.executeQuery()) {
                        if (!result.next() || result.getInt(1) != 1) throw new SQLException("Timed out waiting for shared schema initialization");
                    }
                }
                schemaConnection = connection; schemaLock = lock;
            } catch (Exception failure) { connection.close(); throw failure; }
        } catch (Exception failure) { throw new IllegalStateException("Cannot serialize shared schema initialization", failure); }
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void ready() { close(); }

    @Override public synchronized void close() {
        if (schemaConnection == null) return;
        try (Connection connection = schemaConnection;
                java.sql.PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, schemaLock); statement.execute();
        } catch (SQLException failure) { throw new IllegalStateException("Cannot release schema initialization lock", failure); }
        finally { schemaConnection = null; }
    }

    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (bean instanceof DataSource source && !(source instanceof TaggedDataSource)) {
            awaitSchemaTurn(source);
            return new TaggedDataSource(source);
        }
        return bean;
    }
    static final class TaggedDataSource extends DelegatingDataSource {
        TaggedDataSource(DataSource delegate) { super(delegate); }
        @Override public Connection getConnection() throws SQLException { return tag(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException {
            return tag(super.getConnection(user, password));
        }
        private Connection tag(Connection connection) throws SQLException {
            try {
                if (connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")) {
                    try (java.sql.PreparedStatement statement = connection.prepareStatement("SET @webcraft_node = ?")) {
                        statement.setString(1, ClusterIdentity.NODE);
                        statement.execute();
                    }
                }
                return connection;
            } catch (SQLException failure) {
                try { connection.close(); } catch (SQLException close) { failure.addSuppressed(close); }
                throw failure;
            }
        }
    }
}
