package com.gameexpert.cluster;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FencedDataSourceProtocolTest {
    @Test
    void everyBorrowedMysqlConnectionDeclaresNodeAndFenceProtocol() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.prepareStatement("SET @webcraft_node = ?, @webcraft_fence_protocol = 2"))
                .thenReturn(statement);
        FencedDataSourcePostProcessor.TaggedDataSource tagged =
                new FencedDataSourcePostProcessor.TaggedDataSource(source);
        assertSame(connection, tagged.getConnection());
        assertSame(connection, tagged.getConnection());
        verify(statement, times(2)).setString(1, ClusterIdentity.NODE);
        verify(statement, times(2)).execute();
    }

    @Test
    void failedProtocolTagNeverReturnsAnUntaggedConnection() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.prepareStatement("SET @webcraft_node = ?, @webcraft_fence_protocol = 2"))
                .thenReturn(statement);
        when(statement.execute()).thenThrow(new SQLException("protocol tag failed"));
        assertThrows(SQLException.class,
                () -> new FencedDataSourcePostProcessor.TaggedDataSource(source).getConnection());
        verify(connection).close();
    }
}
