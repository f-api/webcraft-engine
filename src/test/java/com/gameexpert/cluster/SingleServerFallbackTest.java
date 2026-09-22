package com.gameexpert.cluster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

/** Without trigger privilege (RDS with binary logging) one server runs; a second one refuses to start. */
class SingleServerFallbackTest {
    private static Object call(Object target, String name, Class<?> type, Object arg) throws Throwable {
        Method m = WorldAuthority.class.getDeclaredMethod(name, type);
        m.setAccessible(true);
        try { return m.invoke(target, arg); } catch (InvocationTargetException e) { throw e.getCause(); }
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = WorldAuthority.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    @Test
    void recognisesTheBinaryLoggingTriggerRefusalOnly() throws Throwable {
        SQLException refusal = new SQLException("You do not have the SUPER privilege and binary logging is enabled", "HY000", 1419);
        assertEquals(true, call(null, "fenceTriggersNeedPrivilege", Throwable.class, refusal));
        assertEquals(true, call(null, "fenceTriggersNeedPrivilege", Throwable.class, new IllegalStateException(refusal)));
        assertEquals(false, call(null, "fenceTriggersNeedPrivilege", Throwable.class, new SQLException("deadlock", "40001", 1213)));
    }

    private static DataSource lockSource(int getLockResult) throws SQLException {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getInt(1)).thenReturn(getLockResult);
        return source;
    }

    @Test
    void firstServerHoldsTheDatabaseLock() throws Throwable {
        WorldAuthority authority = new WorldAuthority(lockSource(1), mock(Environment.class));
        try {
            call(authority, "holdSingleServer", String.class, "webcraft:game:v1:0123456789abcdef01234567");
            assertNotNull(field(authority, "singleServerLock"));
            assertEquals("webcraft:single:0123456789abcdef01234567", field(authority, "singleServerLockName"));
            assertFalse(authority.enabled());
        } finally {
            authority.close();
        }
        assertNull(field(authority, "singleServerLock"));
    }

    @Test
    void secondServerRefusesToStart() throws Throwable {
        WorldAuthority authority = new WorldAuthority(lockSource(0), mock(Environment.class));
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> call(authority, "holdSingleServer", String.class, "webcraft:game:v1:0123456789abcdef01234567"));
            assertTrue(refused.getMessage().contains("Another server is already running"));
            assertNull(field(authority, "singleServerLock"));
        } finally {
            authority.close();
        }
    }
}
